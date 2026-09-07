package uk.co.cyberspectra.spectralmail;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * One-at-a-time EasyNPC courier state machine. Alpha.3 adds real Drop Box collection and Letter Box
 * fallback while preserving the no-force-load, no-recipient-teleport and home-only recovery rules.
 */
public final class CourierManager {
    private enum Phase { PICKUP_DROP_BOX, OUTBOUND_PLAYER, OUTBOUND_LETTER_BOX, WAITING, RETURNING }

    private static final Queue<String> QUEUE = new ArrayDeque<>();
    private static final Set<String> QUEUED_IDS = new HashSet<>();
    private static Trip active;
    private static long serverTicks;

    private CourierManager() {}

    public static void enqueue(String mailId) {
        if (mailId == null || mailId.isBlank()) return;
        synchronized (QUEUE) {
            if (active != null && mailId.equals(active.mailId)) return;
            if (QUEUED_IDS.add(mailId)) QUEUE.add(mailId);
        }
    }

    public static void queuePersistent(MinecraftServer server, MailSavedData data) {
        if (server == null || data == null || !data.hasCourier()) return;
        for (MailRecord record : data.allRecords()) {
            if (record.state != MailRecord.PENDING) continue;
            if (data.dropBoxForMail(record.id) != null
                    || data.hasLetterBox(record.recipientUuid)
                    || MinecraftRuntime.findOnlinePlayer(server, record.recipientUuid) != null) {
                enqueue(record.id);
            }
        }
    }

    public static String bindNearest(MinecraftServer server, ServerPlayer player) {
        if (server == null || player == null) return "No player was available for courier binding.";
        Object npc = MinecraftRuntime.findNearestEasyNpc(player, 8.0D);
        if (npc == null) return "No loaded EasyNPC was found within 8 blocks.";
        return bindEntity(server, npc);
    }

    /** Used by the EasyNPC courier preset so its setup button binds that exact NPC, not a neighbour. */
    public static String bindEntity(MinecraftServer server, Object npc) {
        if (server == null || npc == null || !MinecraftRuntime.isEasyNpc(npc)) {
            return "The command source was not a loaded EasyNPC courier.";
        }
        UUID uuid = MinecraftRuntime.uuidOf(npc);
        if (uuid == null) return "That EasyNPC did not expose a usable UUID.";
        double x = MinecraftRuntime.x(npc), y = MinecraftRuntime.y(npc), z = MinecraftRuntime.z(npc);
        if (!finite(x, y, z)) return "Could not read that EasyNPC's position.";

        MailSavedData data = MailSavedData.get(server);
        data.bindCourier(uuid, x, y, z);
        resetActive(true);
        queuePersistent(server, data);
        return "Bound this EasyNPC as the postman and recorded this position as home.";
    }

    public static String setHome(MinecraftServer server) {
        MailSavedData data = MailSavedData.get(server);
        if (!data.hasCourier()) return "No courier is bound.";
        Object courier = MinecraftRuntime.findLoadedEntity(server, data.courierUuid());
        if (courier == null) return "The bound courier is not currently loaded.";
        double x = MinecraftRuntime.x(courier), y = MinecraftRuntime.y(courier), z = MinecraftRuntime.z(courier);
        if (!finite(x, y, z)) return "Could not read the courier's current position.";
        data.setCourierHome(x, y, z);
        return "Courier home updated to its current position.";
    }

    public static String forceReturn(MinecraftServer server) {
        MailSavedData data = MailSavedData.get(server);
        if (!data.hasCourier()) return "No courier is bound.";
        if (data.courierHome() == null) return "The courier has no recorded home position.";
        if (active == null) active = new Trip(null, null, Phase.RETURNING, null, serverTicks, serverTicks);
        else {
            active.requeueAfterReturn = active.mailId != null;
            active.phase = Phase.RETURNING;
            active.phaseStarted = serverTicks;
        }
        return "Courier return requested.";
    }

    public static String clear(MinecraftServer server) {
        MailSavedData data = MailSavedData.get(server);
        Object courier = data.hasCourier() ? MinecraftRuntime.findLoadedEntity(server, data.courierUuid()) : null;
        if (courier != null) MinecraftRuntime.clearMainHand(courier);
        data.clearCourier();
        synchronized (QUEUE) {
            QUEUE.clear();
            QUEUED_IDS.clear();
        }
        active = null;
        return "Courier binding cleared. Pending letters remain safe in the mailbox database and postal boxes.";
    }

    public static String status(MinecraftServer server) {
        MailSavedData data = MailSavedData.get(server);
        if (!data.hasCourier()) return "Courier: not bound.";
        Object courier = MinecraftRuntime.findLoadedEntity(server, data.courierUuid());
        int queued;
        synchronized (QUEUE) { queued = QUEUE.size(); }
        String state = active == null ? "idle" : active.phase.name().toLowerCase();
        return "Courier: bound (" + (courier == null ? "not loaded" : "loaded") + "), state=" + state + ", queued=" + queued + ", routing=letter_box_first, service_pathing=front_approach.";
    }

    public static void tick(MinecraftServer server) {
        if (server == null) return;
        serverTicks++;
        if (serverTicks % 5L != 0L) return; // four arrival/route checks per second for smoother stopping

        SpectralMailConfig config = SpectralMailConfig.get();
        MailSavedData data = MailSavedData.get(server);
        if (!config.courierEnabled || !data.hasCourier()) return;

        if (active == null) startNext(server, data, config);
        if (active == null) return;

        Object courier = MinecraftRuntime.findLoadedEntity(server, data.courierUuid());
        if (courier == null) {
            if (serverTicks - active.phaseStarted > config.courierReturnTimeoutSeconds * 20L) finishActive();
            return;
        }

        switch (active.phase) {
            case PICKUP_DROP_BOX -> tickPickup(server, data, config, courier);
            case OUTBOUND_PLAYER -> tickOutboundPlayer(server, data, config, courier);
            case OUTBOUND_LETTER_BOX -> tickOutboundLetterBox(server, data, config, courier);
            case WAITING -> tickWaiting(config, courier);
            case RETURNING -> tickReturning(data, config, courier);
        }
    }

    private static void startNext(MinecraftServer server, MailSavedData data, SpectralMailConfig config) {
        while (true) {
            String id;
            synchronized (QUEUE) { id = QUEUE.poll(); }
            if (id == null) return;

            MailRecord record = data.getRecord(id);
            if (record == null || record.state != MailRecord.PENDING) {
                forgetQueued(id);
                continue;
            }
            Object courier = MinecraftRuntime.findLoadedEntity(server, data.courierUuid());
            if (courier == null) {
                synchronized (QUEUE) { QUEUE.add(id); }
                return;
            }

            DeliveryPlan plan = planDelivery(server, data, config, courier, record);
            MailSavedData.PostalAddress pickup = data.dropBoxForMail(id);
            if (pickup != null) {
                if (plan == null || !validPostalTarget(server, courier, pickup, SpectralMail.DROP_BOX.get(), config.courierMaxDistance)) {
                    forgetQueued(id); // remains physically/logically in the Drop Box until a future trigger
                    continue;
                }
                active = new Trip(id, record.recipientUuid, Phase.PICKUP_DROP_BOX, pickup, serverTicks, serverTicks);
                MinecraftRuntime.clearMainHand(courier);
                startPostalApproach(server, courier, pickup, config, 0);
                return;
            }

            if (plan == null) {
                forgetQueued(id);
                continue;
            }
            startDeliveryPlan(server, courier, record, plan, config);
            return;
        }
    }

    private static void tickPickup(MinecraftServer server, MailSavedData data, SpectralMailConfig config, Object courier) {
        MailRecord record = currentPending(data);
        if (record == null) {
            beginReturn(courier, false);
            return;
        }
        MailSavedData.PostalAddress origin = data.dropBoxForMail(record.id);
        if (origin == null || !origin.equals(active.postalTarget)) {
            beginReturn(courier, true); // e.g. the box was broken while the courier was walking to it
            return;
        }
        if (!validPostalTarget(server, courier, origin, SpectralMail.DROP_BOX.get(), config.courierMaxDistance)) {
            beginReturn(courier, false);
            return;
        }
        PostalRuntime.ApproachPoint approach = ensurePostalApproach(server, courier, origin, config);
        if (approach != null
                && PostalRuntime.distanceSq(courier, approach) <= config.courierPostalArrivalRadius * config.courierPostalArrivalRadius) {
            MinecraftRuntime.stopNavigation(courier);
            data.pickupFromDropBox(record.id);
            DeliveryPlan plan = planDelivery(server, data, config, courier, record);
            if (plan == null) {
                beginReturn(courier, false);
                return;
            }
            startDeliveryPlan(server, courier, record, plan, config);
            return;
        }
        if (timedOut(config.courierTimeoutSeconds)) {
            beginReturn(courier, false); // mail stays in the Drop Box
            return;
        }
        maintainPostalApproach(server, courier, origin, config);
    }

    private static void tickOutboundPlayer(MinecraftServer server, MailSavedData data, SpectralMailConfig config, Object courier) {
        MailRecord record = currentPending(data);
        if (record == null) {
            beginReturn(courier, false);
            return;
        }
        ServerPlayer recipient = MinecraftRuntime.findOnlinePlayer(server, record.recipientUuid);
        if (!validPlayerTarget(courier, recipient, config.courierMaxDistance)) {
            if (!switchToLetterBox(server, data, config, courier, record)) beginReturn(courier, false);
            return;
        }

        double distanceSq = MinecraftRuntime.distanceSq(courier, recipient);
        if (distanceSq <= config.courierArrivalRadius * config.courierArrivalRadius) {
            MinecraftRuntime.stopNavigation(courier);
            MinecraftRuntime.clearMainHand(courier);
            if (MailDelivery.deliver(data, record, recipient)) {
                active.phase = Phase.WAITING;
                active.phaseStarted = serverTicks;
            } else if (!switchToLetterBox(server, data, config, courier, record)) {
                recipient.m_5661_(Component.m_237113_("Your inventory is full. The postman will keep the letter safe until it can be delivered."), true);
                beginReturn(courier, false);
            }
            return;
        }

        if (timedOut(config.courierTimeoutSeconds)) {
            if (!switchToLetterBox(server, data, config, courier, record)) {
                recipient.m_5661_(Component.m_237113_("The postman could not reach you. Your letter remains safe."), true);
                beginReturn(courier, false);
            }
            return;
        }
        MinecraftRuntime.moveToEntity(courier, recipient, config.courierSpeed);
    }

    private static void tickOutboundLetterBox(MinecraftServer server, MailSavedData data, SpectralMailConfig config, Object courier) {
        MailRecord record = currentPending(data);
        MailSavedData.PostalAddress target = active.postalTarget;
        if (record == null) {
            beginReturn(courier, false);
            return;
        }
        if (target == null
                || !data.isActiveLetterBox(record.recipientUuid, target)
                || !data.letterBoxHasSpace(record.recipientUuid, config.letterBoxCapacity)
                || !validPostalTarget(server, courier, target, SpectralMail.LETTER_BOX.get(), config.courierMaxDistance)) {
            if (!switchToPlayer(server, config, courier, record)) beginReturn(courier, false);
            return;
        }

        PostalRuntime.ApproachPoint approach = ensurePostalApproach(server, courier, target, config);
        if (approach != null
                && PostalRuntime.distanceSq(courier, approach) <= config.courierPostalArrivalRadius * config.courierPostalArrivalRadius) {
            MinecraftRuntime.stopNavigation(courier);
            MinecraftRuntime.clearMainHand(courier);
            if (data.depositToLetterBox(record, config.letterBoxCapacity)) {
                PostalFeedback.deliveredToLetterBox(server, target);
                ServerPlayer online = MinecraftRuntime.findOnlinePlayer(server, record.recipientUuid);
                if (online != null) online.m_5661_(Component.m_237113_("The postman left a sealed letter in your Letter Box."), true);
                active.phase = Phase.WAITING;
                active.phaseStarted = serverTicks;
                clearPostalApproach();
            } else if (!switchToPlayer(server, config, courier, record)) {
                beginReturn(courier, false);
            }
            return;
        }
        if (timedOut(config.courierTimeoutSeconds)) {
            if (!switchToPlayer(server, config, courier, record)) beginReturn(courier, false);
            return;
        }
        maintainPostalApproach(server, courier, target, config);
    }

    private static DeliveryPlan planDelivery(MinecraftServer server, MailSavedData data, SpectralMailConfig config,
                                             Object courier, MailRecord record) {
        MailSavedData.PostalAddress box = data.letterBox(record.recipientUuid);
        if (box != null
                && data.letterBoxHasSpace(record.recipientUuid, config.letterBoxCapacity)
                && validPostalTarget(server, courier, box, SpectralMail.LETTER_BOX.get(), config.courierMaxDistance)) {
            return new DeliveryPlan(Phase.OUTBOUND_LETTER_BOX, box, null);
        }
        ServerPlayer recipient = MinecraftRuntime.findOnlinePlayer(server, record.recipientUuid);
        if (validPlayerTarget(courier, recipient, config.courierMaxDistance)) {
            return new DeliveryPlan(Phase.OUTBOUND_PLAYER, null, recipient);
        }
        return null;
    }

    private static boolean switchToLetterBox(MinecraftServer server, MailSavedData data, SpectralMailConfig config,
                                             Object courier, MailRecord record) {
        MailSavedData.PostalAddress box = data.letterBox(record.recipientUuid);
        if (box == null || !data.letterBoxHasSpace(record.recipientUuid, config.letterBoxCapacity)
                || !validPostalTarget(server, courier, box, SpectralMail.LETTER_BOX.get(), config.courierMaxDistance)) {
            return false;
        }
        active.phase = Phase.OUTBOUND_LETTER_BOX;
        active.postalTarget = box;
        active.phaseStarted = serverTicks;
        MinecraftRuntime.setMainHand(courier, MailItemData.courierVisual(record));
        clearPostalApproach();
        startPostalApproach(server, courier, box, config, 0);
        return true;
    }


    private static boolean switchToPlayer(MinecraftServer server, SpectralMailConfig config,
                                          Object courier, MailRecord record) {
        ServerPlayer recipient = MinecraftRuntime.findOnlinePlayer(server, record.recipientUuid);
        if (!validPlayerTarget(courier, recipient, config.courierMaxDistance)) return false;
        active.phase = Phase.OUTBOUND_PLAYER;
        active.postalTarget = null;
        active.phaseStarted = serverTicks;
        clearPostalApproach();
        MinecraftRuntime.setMainHand(courier, MailItemData.courierVisual(record));
        MinecraftRuntime.moveToEntity(courier, recipient, config.courierSpeed);
        return true;
    }

    private static void startDeliveryPlan(MinecraftServer server, Object courier, MailRecord record, DeliveryPlan plan, SpectralMailConfig config) {
        if (active == null || !record.id.equals(active.mailId)) {
            active = new Trip(record.id, record.recipientUuid, plan.phase, plan.postalTarget, serverTicks, serverTicks);
        } else {
            active.phase = plan.phase;
            active.postalTarget = plan.postalTarget;
            active.phaseStarted = serverTicks;
        }
        clearPostalApproach();
        MinecraftRuntime.setMainHand(courier, MailItemData.courierVisual(record));
        if (plan.phase == Phase.OUTBOUND_PLAYER && plan.player != null) {
            MinecraftRuntime.moveToEntity(courier, plan.player, config.courierSpeed);
        } else if (plan.postalTarget != null) {
            startPostalApproach(server, courier, plan.postalTarget, config, 0);
        }
    }

    private static PostalRuntime.ApproachPoint ensurePostalApproach(MinecraftServer server, Object courier,
                                                                    MailSavedData.PostalAddress address,
                                                                    SpectralMailConfig config) {
        if (active == null) return null;
        if (active.approachTarget == null) startPostalApproach(server, courier, address, config, 0);
        return active.approachTarget;
    }

    private static boolean startPostalApproach(MinecraftServer server, Object courier, MailSavedData.PostalAddress address,
                                               SpectralMailConfig config, int startIndex) {
        if (active == null) return false;
        PostalRuntime.ApproachPoint[] points = PostalRuntime.servicePoints(server, address);
        if (points.length == 0) {
            clearPostalApproach();
            return false;
        }
        int normalized = Math.floorMod(startIndex, points.length);
        for (int offset = 0; offset < points.length; offset++) {
            int index = (normalized + offset) % points.length;
            PostalRuntime.ApproachPoint point = points[index];
            if (PostalRuntime.moveTo(courier, point, config.courierPostalSpeed)) {
                active.approachTarget = point;
                active.approachIndex = index;
                active.bestApproachDistanceSq = PostalRuntime.distanceSq(courier, point);
                active.lastApproachProgressTick = serverTicks;
                return true;
            }
        }
        active.approachTarget = points[normalized];
        active.approachIndex = normalized;
        active.bestApproachDistanceSq = PostalRuntime.distanceSq(courier, active.approachTarget);
        active.lastApproachProgressTick = serverTicks;
        return false;
    }

    private static void maintainPostalApproach(MinecraftServer server, Object courier, MailSavedData.PostalAddress address,
                                               SpectralMailConfig config) {
        if (active == null) return;
        PostalRuntime.ApproachPoint point = ensurePostalApproach(server, courier, address, config);
        if (point == null) return;
        double distanceSq = PostalRuntime.distanceSq(courier, point);
        if (distanceSq + 0.04D < active.bestApproachDistanceSq) {
            active.bestApproachDistanceSq = distanceSq;
            active.lastApproachProgressTick = serverTicks;
        }
        if (serverTicks - active.lastApproachProgressTick >= config.courierApproachRetrySeconds * 20L) {
            startPostalApproach(server, courier, address, config, active.approachIndex + 1);
            return;
        }
        if (serverTicks % 10L == 0L && !PostalRuntime.moveTo(courier, point, config.courierPostalSpeed)) {
            startPostalApproach(server, courier, address, config, active.approachIndex + 1);
        }
    }

    private static void clearPostalApproach() {
        if (active == null) return;
        active.approachTarget = null;
        active.approachIndex = 0;
        active.bestApproachDistanceSq = Double.POSITIVE_INFINITY;
        active.lastApproachProgressTick = serverTicks;
    }

    private static boolean validPlayerTarget(Object courier, ServerPlayer recipient, double maxDistance) {
        if (recipient == null || !MinecraftRuntime.sameLevel(courier, recipient)) return false;
        double distanceSq = MinecraftRuntime.distanceSq(courier, recipient);
        return Double.isFinite(distanceSq) && distanceSq <= maxDistance * maxDistance;
    }

    private static boolean validPostalTarget(MinecraftServer server, Object courier, MailSavedData.PostalAddress address,
                                             net.minecraft.world.level.block.Block expected, double maxDistance) {
        if (!PostalRuntime.sameDimension(courier, address)) return false;
        double distanceSq = PostalRuntime.distanceSq(courier, address);
        return Double.isFinite(distanceSq) && distanceSq <= maxDistance * maxDistance
                && PostalRuntime.loadedAndMatches(server, address, expected);
    }

    private static MailRecord currentPending(MailSavedData data) {
        MailRecord record = active == null || active.mailId == null ? null : data.getRecord(active.mailId);
        return record != null && record.state == MailRecord.PENDING ? record : null;
    }

    private static boolean timedOut(int seconds) {
        return active != null && serverTicks - active.phaseStarted >= seconds * 20L;
    }

    private static void tickWaiting(SpectralMailConfig config, Object courier) {
        if (serverTicks - active.phaseStarted >= config.courierWaitAfterDeliveryTicks) beginReturn(courier, false);
    }

    private static void beginReturn(Object courier, boolean requeue) {
        MinecraftRuntime.clearMainHand(courier);
        if (active == null) return;
        active.requeueAfterReturn |= requeue;
        active.phase = Phase.RETURNING;
        active.postalTarget = null;
        clearPostalApproach();
        active.phaseStarted = serverTicks;
    }

    private static void tickReturning(MailSavedData data, SpectralMailConfig config, Object courier) {
        MailSavedData.CourierHome home = data.courierHome();
        if (home == null) {
            finishActive();
            return;
        }
        double dx = MinecraftRuntime.x(courier) - home.x();
        double dy = MinecraftRuntime.y(courier) - home.y();
        double dz = MinecraftRuntime.z(courier) - home.z();
        double distSq = dx * dx + dy * dy + dz * dz;
        if (distSq <= 2.25D) {
            MinecraftRuntime.stopNavigation(courier);
            MinecraftRuntime.clearMainHand(courier);
            finishActive();
            return;
        }

        if (serverTicks - active.phaseStarted >= config.courierReturnTimeoutSeconds * 20L) {
            if (config.courierTeleportHomeOnReturnTimeout) MinecraftRuntime.teleport(courier, home.x(), home.y(), home.z());
            MinecraftRuntime.stopNavigation(courier);
            MinecraftRuntime.clearMainHand(courier);
            finishActive();
            return;
        }
        MinecraftRuntime.moveToPosition(courier, home.x(), home.y(), home.z(), config.courierSpeed);
    }

    private static void finishActive() {
        if (active == null) return;
        String id = active.mailId;
        boolean requeue = active.requeueAfterReturn;
        if (id != null) forgetQueued(id);
        active = null;
        if (requeue && id != null) enqueue(id);
    }

    private static void forgetQueued(String id) {
        synchronized (QUEUE) { QUEUED_IDS.remove(id); }
    }

    private static void resetActive(boolean clearQueue) {
        active = null;
        if (clearQueue) {
            synchronized (QUEUE) {
                QUEUE.clear();
                QUEUED_IDS.clear();
            }
        }
    }

    public static void reset() {
        resetActive(true);
        serverTicks = 0L;
    }

    private static boolean finite(double x, double y, double z) {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
    }

    private record DeliveryPlan(Phase phase, MailSavedData.PostalAddress postalTarget, ServerPlayer player) {}

    private static final class Trip {
        final String mailId;
        final UUID recipientUuid;
        Phase phase;
        MailSavedData.PostalAddress postalTarget;
        final long started;
        long phaseStarted;
        boolean requeueAfterReturn;
        PostalRuntime.ApproachPoint approachTarget;
        int approachIndex;
        double bestApproachDistanceSq = Double.POSITIVE_INFINITY;
        long lastApproachProgressTick;

        Trip(String mailId, UUID recipientUuid, Phase phase, MailSavedData.PostalAddress postalTarget,
             long started, long phaseStarted) {
            this.mailId = mailId;
            this.recipientUuid = recipientUuid;
            this.phase = phase;
            this.postalTarget = postalTarget;
            this.started = started;
            this.phaseStarted = phaseStarted;
            this.lastApproachProgressTick = phaseStarted;
        }
    }
}
