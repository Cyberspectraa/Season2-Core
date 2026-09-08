package com.season2.townlife.runtime;

import com.season2.townlife.config.TownLifeConfig;
import com.season2.townlife.data.Resident;
import com.season2.townlife.data.Town;
import com.season2.townlife.data.TownLifeSavedData;
import com.season2.townlife.data.TownLocation;
import com.season2.townlife.logic.Activity;
import com.season2.townlife.logic.JobType;
import com.season2.townlife.logic.NeedType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Lightweight resident controller. There are no hand-built areas or markers:
 * a bed is home, a workstation is work, and vanilla navigation handles the route.
 */
public final class TownLifeManager {
    private static final Map<UUID, RuntimeState> RUNTIME = new HashMap<>();
    private static final Map<UUID, ProviderReservation> PROVIDER_RESERVATIONS = new HashMap<>();
    private static final int NATIVE_PATH_RANGE = 160;

    private TownLifeManager() {}

    public static void tick(ServerLevel level) {
        long gameTime = level.getGameTime();
        maintainSleepingResidents(level);
        cleanupProviderReservations(gameTime);
        if (gameTime % 20L != 0L) return;

        TownLifeSavedData data = TownLifeSavedData.get(level);
        boolean dirty = false;
        for (Resident resident : data.residents()) {
            Entity entity = level.getEntity(resident.entityUuid());
            if (entity == null) continue;
            Optional<Mob> mobOptional = EasyNpcCompat.asControllableMob(entity);
            if (mobOptional.isEmpty()) {
                resident.setReason("Loaded entity is not a controllable Easy NPC");
                continue;
            }
            Mob mob = mobOptional.get();
            if (Season2NpcProtection.isProtected(level, mob)) {
                resident.setReason("Protected Season 2 service NPC; Town Life scheduler skipped");
                forgetRuntime(resident.entityUuid());
                continue;
            }
            Town town = data.town(resident.townId()).orElse(null);
            if (town == null) continue;
            RuntimeState runtime = RUNTIME.computeIfAbsent(resident.entityUuid(), ignored -> new RuntimeState());
            dirty |= tickResident(level, data, town, resident, mob, runtime, gameTime, level.getDayTime());
        }
        if (dirty) data.setDirty();
    }

    public static void pauseForInteraction(ServerLevel level, UUID residentUuid, Mob mob, Entity player) {
        if (Season2NpcProtection.isProtected(level, mob)) return;
        if (TownLifeSavedData.get(level).resident(residentUuid).isEmpty()) return;
        RuntimeState runtime = RUNTIME.computeIfAbsent(residentUuid, ignored -> new RuntimeState());
        runtime.pausedUntil = level.getGameTime() + 160L;
        stopSleeping(mob, runtime);
        EasyNpcCompat.enterStationaryState(mob);
        runtime.mode = null;
        runtime.modeAnchor = null;
        mob.getNavigation().stop();
        mob.getLookControl().setLookAt(player, 30F, 30F);
    }

    public static String modeName(UUID uuid) {
        RuntimeState runtime = RUNTIME.get(uuid);
        if (runtime == null || runtime.mode == null) return "idle";
        return runtime.mode.name().toLowerCase().replace('_', ' ');
    }

    public static void forgetRuntime(UUID uuid) {
        RUNTIME.remove(uuid);
        PROVIDER_RESERVATIONS.entrySet().removeIf(entry -> entry.getKey().equals(uuid) || entry.getValue().customer().equals(uuid));
    }

    private static boolean tickResident(ServerLevel level, TownLifeSavedData data, Town town, Resident resident, Mob mob,
                                        RuntimeState runtime, long gameTime, long dayTime) {
        if (!runtime.presetConfigured) {
            runtime.presetConfigured = EasyNpcCompat.applyTownResidentPreset(mob);
        }
        updateNeeds(resident, runtime);

        if (runtime.pausedUntil > gameTime) {
            EasyNpcCompat.enterStationaryState(mob);
            runtime.mode = null;
            runtime.modeAnchor = null;
            mob.getNavigation().stop();
            resident.setReason("Paused for a conversation");
            return true;
        }

        boolean danger = hasNearbyDanger(level, mob);
        if (danger && !resident.homeLocationId().isBlank()) {
            if (runtime.serviceRequest != null || runtime.interacting) {
                cancelService(resident, mob, runtime, gameTime, "A hostile mob interrupted the errand");
            }
            stopSleeping(mob, runtime);
            TownLocation home = town.location(resident.homeLocationId()).orElse(null);
            if (home != null) {
                beginOrContinueLocation(level, resident, mob, runtime, home, Activity.SHELTER, gameTime, true);
                resident.setReason("Heading home because a hostile mob is nearby");
                return true;
            }
        }

        if (runtime.interacting) {
            return continueServiceInteraction(level, data, resident, mob, runtime, gameTime);
        }
        if (runtime.serviceRequest != null) {
            return continueServiceErrand(level, data, town, resident, mob, runtime, gameTime, dayTime);
        }

        // Before normal routine decisions, see whether the resident would visit a working service NPC.
        if (!resident.isNighttime(dayTime) && gameTime >= runtime.nextServiceRetryTick) {
            ServiceRequest request = neededService(resident, mob);
            if (request != null) {
                Optional<Provider> provider = findProvider(level, data, town, resident, mob, request, dayTime, gameTime);
                if (provider.isPresent()) {
                    startServiceErrand(resident, mob, runtime, request, provider.get(), gameTime);
                    return true;
                }
                runtime.nextServiceRetryTick = gameTime + 200L;
            }
        }

        TownLocation home = town.location(resident.homeLocationId()).orElse(null);
        TownLocation work = town.location(resident.workplaceLocationId()).orElse(null);
        if (home != null && !(level.getBlockState(home.anchor()).getBlock() instanceof net.minecraft.world.level.block.BedBlock)) {
            resident.setHomeLocationId("");
            resident.clearActivity("Assigned bed no longer exists", gameTime);
            home = null;
        }
        if (work != null && WorkstationClassifier.classify(level.getBlockState(work.anchor())).isEmpty()) {
            resident.setWorkplaceLocationId("");
            resident.setJobType(JobType.UNEMPLOYED);
            resident.clearActivity("Assigned workstation no longer exists", gameTime);
            work = null;
        }

        if (resident.isNighttime(dayTime) && home != null) {
            return sleepAtHome(level, resident, mob, runtime, home, gameTime);
        }
        if (runtime.sleeping) stopSleeping(mob, runtime);

        if (resident.needs().get(NeedType.ENERGY) < 14F && home != null) {
            return sleepAtHome(level, resident, mob, runtime, home, gameTime);
        }

        boolean workNow = resident.jobType() != JobType.UNEMPLOYED && work != null
                && resident.isWorkHours(dayTime) && work.isOpen(dayTime);
        if (workNow) {
            beginOrContinueLocation(level, resident, mob, runtime, work, Activity.WORK, gameTime, false);
            performWork(resident, mob, runtime, work, gameTime);
            return true;
        }

        if (resident.needs().get(NeedType.HUNGER) < 35F && home != null) {
            beginOrContinueLocation(level, resident, mob, runtime, home, Activity.EAT, gameTime, false);
            if (runtime.locationArrived) {
                resident.needs().add(NeedType.HUNGER, 5.2F);
                if (gameTime % 80L == 0L) mob.swing(InteractionHand.MAIN_HAND);
                if (resident.needs().get(NeedType.HUNGER) >= 82F) finishActivity(resident, mob, runtime, gameTime, "Finished eating at home");
            }
            return true;
        }

        if (home != null) {
            beginOrContinueLocation(level, resident, mob, runtime, home, Activity.RETURN_HOME, gameTime, false);
            if (runtime.locationArrived) {
                resident.needs().add(NeedType.ENERGY, 0.20F);
                resident.needs().add(NeedType.FUN, 0.22F);
                resident.setReason("At home • Easy NPC local stroll active");
            }
            return true;
        }

        EasyNpcCompat.enterStationaryState(mob);
        runtime.mode = null;
        runtime.modeAnchor = null;
        mob.getNavigation().stop();
        resident.clearActivity("No home or active workplace assigned", gameTime);
        return true;
    }

    private static boolean sleepAtHome(ServerLevel level, Resident resident, Mob mob, RuntimeState runtime,
                                       TownLocation home, long gameTime) {
        if (runtime.sleeping) {
            setResidentMode(mob, runtime, ResidentMode.SLEEPING, runtime.sleepBedPos, 0D, false);
            mob.getNavigation().stop();
            resident.needs().add(NeedType.ENERGY, 4.0F);
            resident.setReason("Sleeping in assigned bed");
            return true;
        }

        beginOrContinueLocation(level, resident, mob, runtime, home, Activity.SLEEP, gameTime, false);
        if (!runtime.locationArrived) return true;

        BlockPos approach = runtime.finalTarget;
        EasyNpcCompat.enterStationaryState(mob);
        SleepService.SleepStart start = SleepService.begin(level, mob, home.anchor(), approach);
        if (start.success()) {
            runtime.sleeping = true;
            runtime.sleepBedPos = start.bed();
            runtime.sleepWakePos = start.wakePos();
            runtime.sleepRetryAt = 0L;
            runtime.mode = ResidentMode.SLEEPING;
            runtime.modeAnchor = start.bed();
            resident.beginPerforming(Long.MAX_VALUE, start.reason());
            return true;
        }

        // Never fall back to a floor sleeping pose. Choose another final
        // bedside square, then let Easy NPC / Minecraft path there normally.
        resident.setReason(start.reason() + "; retrying bedside destination");
        if (gameTime >= runtime.sleepRetryAt) {
            runtime.sleepRetryAt = gameTime + 60L;
            runtime.locationArrived = false;
            runtime.finalTarget = findApproachTarget(
                    level, mob, home.anchor(), runtime.random(resident.entityUuid(), gameTime + 97L));
            runtime.nextPathRefreshTick = 0L;
            runtime.lastDistanceSqr = Double.MAX_VALUE;
            runtime.stuckSeconds = 0;
            runtime.mode = null;
            runtime.modeAnchor = null;
        }
        return true;
    }

    private static void beginOrContinueLocation(ServerLevel level, Resident resident, Mob mob, RuntimeState runtime,
                                                TownLocation location, Activity activity, long gameTime, boolean emergency) {
        boolean changed = resident.activity() != activity || !resident.targetLocationId().equals(location.id());
        if (changed) {
            stopSleeping(mob, runtime);
            resident.beginTravel(activity, location.id(), "Going to " + readable(activity), gameTime);
            runtime.resetMovement();
            runtime.anchor = location.anchor();
            runtime.finalTarget = findApproachTarget(
                    level, mob, location.anchor(), runtime.random(resident.entityUuid(), gameTime));
        }

        double speed = emergency ? TownLifeConfig.EMERGENCY_WALK_SPEED.get() : TownLifeConfig.WALK_SPEED.get();
        if (!runtime.locationArrived) {
            setResidentMode(mob, runtime, ResidentMode.COMMUTING, runtime.finalTarget, speed, false);
            followNativeNavigation(level, resident, mob, runtime, speed, gameTime);
        }
        if (!runtime.locationArrived) return;

        if (activity == Activity.WORK) {
            setResidentMode(mob, runtime, ResidentMode.WORK, runtime.finalTarget,
                    TownLifeConfig.INDOOR_WALK_SPEED.get(), false);
        } else if (activity != Activity.SLEEP) {
            setResidentMode(mob, runtime, ResidentMode.HOME, runtime.finalTarget,
                    TownLifeConfig.INDOOR_WALK_SPEED.get(), false);
        }

        if (!resident.performing() && activity != Activity.SLEEP) {
            resident.beginPerforming(gameTime + 600L, "At " + readable(activity) + " location");
        }
        if (activity != Activity.SLEEP && resident.performing() && gameTime >= resident.activityEndsAt()) {
            resident.beginPerforming(gameTime + 600L, "Continuing " + readable(activity));
        }
    }

    /**
     * Complete-route movement. Town Life never invents staircase, doorway or
     * balcony waypoints. Easy NPC owns the movement goal and Minecraft owns the
     * path. A wider single-path retry is used for commutes beyond Easy NPC's
     * built-in 48-block Move Back To Home search range.
     */
    private static void followNativeNavigation(ServerLevel level, Resident resident, Mob mob, RuntimeState runtime,
                                               double speed, long gameTime) {
        BlockPos target = runtime.finalTarget;
        if (target == null) {
            target = findApproachTarget(level, mob,
                    runtime.anchor == null ? mob.blockPosition() : runtime.anchor,
                    runtime.random(resident.entityUuid(), gameTime));
            runtime.finalTarget = target;
            runtime.mode = null;
        }

        double distanceSqr = mob.distanceToSqr(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D);
        if (distanceSqr <= 2.56D) {
            runtime.locationArrived = true;
            runtime.lastDistanceSqr = Double.MAX_VALUE;
            runtime.stuckSeconds = 0;
            mob.getNavigation().stop();
            resident.setReason("Arrived at " + readable(resident.activity()));
            return;
        }

        boolean progressed = runtime.lastDistanceSqr == Double.MAX_VALUE
                || distanceSqr < runtime.lastDistanceSqr - 0.15D;
        if (progressed) runtime.stuckSeconds = 0;
        else runtime.stuckSeconds++;
        runtime.lastDistanceSqr = distanceSqr;

        // Issue one full Minecraft path immediately, then only refresh if the
        // navigator finishes or the resident has genuinely stopped progressing.
        boolean shouldIssue = gameTime >= runtime.nextPathRefreshTick
                && (mob.getNavigation().isDone() || runtime.stuckSeconds >= 3);
        if (runtime.nextPathRefreshTick == 0L) shouldIssue = true;
        if (shouldIssue) {
            EasyNpcCompat.startWidePath(mob, target, speed, NATIVE_PATH_RANGE);
            runtime.nextPathRefreshTick = gameTime + 60L;
        }

        resident.setReason("Commuting to " + readable(resident.activity()) + " using Easy NPC navigation");

        // If the destination stand square itself is awkward, choose another
        // square beside the same bed/workstation. This is not an intermediate
        // route hop and never guesses where stairs or doors are.
        if (runtime.stuckSeconds >= 10) {
            BlockPos anchor = runtime.anchor == null ? target : runtime.anchor;
            runtime.finalTarget = findApproachTarget(
                    level, mob, anchor, runtime.random(resident.entityUuid(), gameTime + 131L));
            runtime.nextPathRefreshTick = 0L;
            runtime.lastDistanceSqr = Double.MAX_VALUE;
            runtime.stuckSeconds = 0;
            runtime.mode = null;
            runtime.modeAnchor = null;
            mob.getNavigation().stop();
            resident.setReason("Easy NPC is retrying the final destination square");
        }
    }

    private static void setResidentMode(Mob mob, RuntimeState runtime, ResidentMode mode,
                                        BlockPos anchor, double speed, boolean force) {
        BlockPos immutableAnchor = anchor == null ? null : anchor.immutable();
        if (!force && runtime.mode == mode
                && ((runtime.modeAnchor == null && immutableAnchor == null)
                || (runtime.modeAnchor != null && runtime.modeAnchor.equals(immutableAnchor)))) {
            return;
        }

        switch (mode) {
            case COMMUTING, ERRAND -> EasyNpcCompat.enterTravelState(mob, immutableAnchor, speed);
            case HOME, WORK -> EasyNpcCompat.enterLocalState(mob, immutableAnchor, speed);
            case SLEEPING -> EasyNpcCompat.enterStationaryState(mob);
        }
        runtime.mode = mode;
        runtime.modeAnchor = immutableAnchor;
        if (mode == ResidentMode.COMMUTING || mode == ResidentMode.ERRAND) {
            runtime.nextPathRefreshTick = 0L;
        }
    }

    private static void performWork(Resident resident, Mob mob, RuntimeState runtime, TownLocation workplace, long gameTime) {
        if (!runtime.locationArrived) return;
        setResidentMode(mob, runtime, ResidentMode.WORK, runtime.finalTarget,
                TownLifeConfig.INDOOR_WALK_SPEED.get(), false);
        if ((resident.jobType() == JobType.SMITH_APPRENTICE || resident.jobType() == JobType.GARDENER) && gameTime % 80L == 0L) {
            mob.swing(InteractionHand.MAIN_HAND);
        }
        if (resident.jobType() == JobType.MARKET_VENDOR || resident.jobType() == JobType.TAVERN_WORKER) {
            resident.needs().add(NeedType.SOCIAL, 0.16F);
        }
        resident.needs().add(NeedType.FUN, -0.05F);
        resident.setReason("Working around workplace • Easy NPC local stroll active");
    }

    private static ServiceRequest neededService(Resident resident, Mob mob) {
        if (resident.needs().get(NeedType.HUNGER) < 42F) return ServiceRequest.FOOD;
        if (needsTool(resident, mob)) return ServiceRequest.TOOL;
        if (needsArmor(resident, mob)) return ServiceRequest.ARMOR;
        return null;
    }

    private static boolean needsTool(Resident resident, Mob mob) {
        ItemStack held = mob.getItemBySlot(EquipmentSlot.MAINHAND);
        if (!held.isEmpty()) return false;
        return resident.jobType() == JobType.GARDENER || resident.jobType() == JobType.WATCHPERSON;
    }

    private static boolean needsArmor(Resident resident, Mob mob) {
        return resident.jobType() == JobType.WATCHPERSON
                && mob.getItemBySlot(EquipmentSlot.CHEST).isEmpty();
    }

    private static Optional<Provider> findProvider(ServerLevel level, TownLifeSavedData data, Town town, Resident customer,
                                                   Mob customerMob, ServiceRequest request, long dayTime, long gameTime) {
        List<Provider> providers = new ArrayList<>();
        for (Resident provider : data.residents()) {
            if (provider.entityUuid().equals(customer.entityUuid()) || !provider.townId().equals(town.id())) continue;
            if (!provides(provider.jobType(), request)) continue;
            ProviderReservation reservation = PROVIDER_RESERVATIONS.get(provider.entityUuid());
            if (reservation != null && reservation.expiresAt() > gameTime && !reservation.customer().equals(customer.entityUuid())) continue;
            TownLocation workplace = town.location(provider.workplaceLocationId()).orElse(null);
            if (workplace == null || !provider.isWorkHours(dayTime) || !workplace.isOpen(dayTime)) continue;
            if (provider.activity() != Activity.WORK || !provider.targetLocationId().equals(workplace.id())) continue;
            Entity entity = level.getEntity(provider.entityUuid());
            if (!(entity instanceof Mob providerMob) || !EasyNpcCompat.isEasyNpc(providerMob)) continue;
            if (Season2NpcProtection.isProtected(level, providerMob)) continue;
            if (providerMob.distanceToSqr(workplace.anchor().getX()+0.5D, workplace.anchor().getY(), workplace.anchor().getZ()+0.5D) > 144D) continue;
            providers.add(new Provider(provider, providerMob, workplace));
        }
        return providers.stream().min(Comparator.comparingDouble(provider -> provider.mob().distanceToSqr(customerMob)));
    }

    private static boolean provides(JobType job, ServiceRequest request) {
        return switch (request) {
            case FOOD -> job == JobType.MARKET_VENDOR || job == JobType.TAVERN_WORKER;
            case TOOL, ARMOR -> job == JobType.SMITH_APPRENTICE;
        };
    }

    private static void startServiceErrand(Resident resident, Mob mob, RuntimeState runtime, ServiceRequest request,
                                           Provider provider, long gameTime) {
        stopSleeping(mob, runtime);
        runtime.resetMovement();
        runtime.serviceRequest = request;
        runtime.providerUuid = provider.resident().entityUuid();
        runtime.serviceFailures = 0;
        runtime.lastDistanceSqr = Double.MAX_VALUE;
        PROVIDER_RESERVATIONS.put(runtime.providerUuid, new ProviderReservation(resident.entityUuid(), gameTime + 300L));
        resident.beginTravel(Activity.SOCIALISE, provider.workplace().id(),
                "Going to " + provider.resident().identityName() + " for " + request.displayName(), gameTime);
    }

    private static boolean continueServiceErrand(ServerLevel level, TownLifeSavedData data, Town town, Resident resident,
                                                  Mob mob, RuntimeState runtime, long gameTime, long dayTime) {
        UUID providerUuid = runtime.providerUuid;
        if (providerUuid == null) {
            cancelService(resident, mob, runtime, gameTime, "Service provider was lost");
            return true;
        }
        Resident providerResident = data.resident(providerUuid).orElse(null);
        Entity providerEntity = level.getEntity(providerUuid);
        if (providerResident == null || !(providerEntity instanceof Mob providerMob)) {
            cancelService(resident, mob, runtime, gameTime, "Service provider is not available right now");
            return true;
        }
        TownLocation workplace = town.location(providerResident.workplaceLocationId()).orElse(null);
        boolean providerWorking = workplace != null && providerResident.isWorkHours(dayTime) && workplace.isOpen(dayTime)
                && providerResident.activity() == Activity.WORK;
        if (!providerWorking) {
            cancelService(resident, mob, runtime, gameTime, providerResident.identityName() + " is not working right now");
            return true;
        }

        PROVIDER_RESERVATIONS.put(providerUuid, new ProviderReservation(resident.entityUuid(), gameTime + 120L));
        double distanceSqr = mob.distanceToSqr(providerMob);
        if (distanceSqr <= 9.0D) {
            EasyNpcCompat.enterStationaryState(mob);
            runtime.mode = null;
            runtime.modeAnchor = null;
            mob.getNavigation().stop();
            providerMob.getNavigation().stop();
            mob.getLookControl().setLookAt(providerMob, 30F, 30F);
            providerMob.getLookControl().setLookAt(mob, 30F, 30F);
            runtime.interacting = true;
            runtime.interactionEndsAt = gameTime + 60L;
            RuntimeState providerRuntime = RUNTIME.computeIfAbsent(providerUuid, ignored -> new RuntimeState());
            providerRuntime.pausedUntil = gameTime + 70L;
            resident.setReason("Talking to " + providerResident.identityName() + " about " + runtime.serviceRequest.displayName());
            return true;
        }

        BlockPos target = providerMob.blockPosition();
        if (runtime.mode != ResidentMode.ERRAND || runtime.modeAnchor == null
                || runtime.modeAnchor.distSqr(target) > 9.0D) {
            setResidentMode(mob, runtime, ResidentMode.ERRAND, target, TownLifeConfig.WALK_SPEED.get(), true);
            EasyNpcCompat.startWidePath(mob, target, TownLifeConfig.WALK_SPEED.get(), NATIVE_PATH_RANGE);
            runtime.nextPathRefreshTick = gameTime + 60L;
        }

        boolean progressed = runtime.lastDistanceSqr == Double.MAX_VALUE
                || distanceSqr < runtime.lastDistanceSqr - 0.15D;
        if (progressed) runtime.serviceFailures = Math.max(0, runtime.serviceFailures - 1);
        else runtime.serviceFailures++;
        runtime.lastDistanceSqr = distanceSqr;

        if (gameTime >= runtime.nextPathRefreshTick && (mob.getNavigation().isDone() || runtime.serviceFailures >= 3)) {
            EasyNpcCompat.startWidePath(mob, target, TownLifeConfig.WALK_SPEED.get(), NATIVE_PATH_RANGE);
            runtime.nextPathRefreshTick = gameTime + 60L;
        }

        if (runtime.serviceFailures >= 14) {
            cancelService(resident, mob, runtime, gameTime, "Could not reach " + providerResident.identityName());
        } else {
            resident.setReason("Errand: walking to " + providerResident.identityName()
                    + " for " + runtime.serviceRequest.displayName());
        }
        return true;
    }

    private static boolean continueServiceInteraction(ServerLevel level, TownLifeSavedData data, Resident resident,
                                                       Mob mob, RuntimeState runtime, long gameTime) {
        Resident providerResident = runtime.providerUuid == null ? null : data.resident(runtime.providerUuid).orElse(null);
        Entity providerEntity = runtime.providerUuid == null ? null : level.getEntity(runtime.providerUuid);
        if (providerResident == null || !(providerEntity instanceof Mob providerMob)) {
            cancelService(resident, mob, runtime, gameTime, "Conversation was interrupted");
            return true;
        }
        EasyNpcCompat.enterStationaryState(mob);
        runtime.mode = null;
        runtime.modeAnchor = null;
        mob.getNavigation().stop();
        providerMob.getNavigation().stop();
        mob.getLookControl().setLookAt(providerMob, 30F, 30F);
        providerMob.getLookControl().setLookAt(mob, 30F, 30F);
        if (gameTime < runtime.interactionEndsAt) {
            resident.setReason("Talking with " + providerResident.identityName());
            return true;
        }

        ServiceRequest completed = runtime.serviceRequest;
        fulfillService(completed, resident, mob, providerMob);
        providerMob.swing(InteractionHand.MAIN_HAND);
        resident.needs().add(NeedType.SOCIAL, 8F);
        resident.needs().add(NeedType.FUN, 2F);
        if (runtime.providerUuid != null) PROVIDER_RESERVATIONS.remove(runtime.providerUuid);
        runtime.clearService();
        resident.clearActivity("Got " + completed.displayName() + " from " + providerResident.identityName(), gameTime);
        runtime.nextServiceRetryTick = gameTime + 300L;
        return true;
    }

    private static void fulfillService(ServiceRequest request, Resident resident, Mob customer, Mob provider) {
        if (request == null) return;
        switch (request) {
            case FOOD -> {
                resident.needs().add(NeedType.HUNGER, 68F);
                customer.swing(InteractionHand.MAIN_HAND);
            }
            case TOOL -> {
                if (resident.jobType() == JobType.GARDENER) {
                    customer.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_HOE));
                } else if (resident.jobType() == JobType.WATCHPERSON) {
                    customer.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
                }
            }
            case ARMOR -> {
                if (customer.getItemBySlot(EquipmentSlot.CHEST).isEmpty()) {
                    customer.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
                }
                if (customer.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
                    customer.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
                }
            }
        }
    }

    private static void cancelService(Resident resident, Mob mob, RuntimeState runtime, long gameTime, String reason) {
        if (runtime.providerUuid != null) PROVIDER_RESERVATIONS.remove(runtime.providerUuid);
        runtime.clearService();
        EasyNpcCompat.enterStationaryState(mob);
        mob.getNavigation().stop();
        resident.clearActivity(reason + "; will try later", gameTime);
        runtime.nextServiceRetryTick = gameTime + 240L;
    }

    private static BlockPos findApproachTarget(ServerLevel level, Mob mob, BlockPos anchor, Random random) {
        List<BlockPos> candidates = new ArrayList<>();
        for (int yOffset : new int[]{0, 1, -1}) {
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                candidates.add(anchor.relative(direction).offset(0, yOffset, 0));
            }
        }
        candidates.add(anchor.above());
        candidates.sort(Comparator.comparingDouble(pos -> pos.distSqr(mob.blockPosition())));

        // Prefer a deterministic nearby square, but vary equally good choices
        // between retries so a fence post/counter edge cannot trap one resident.
        int rotate = candidates.isEmpty() ? 0 : random.nextInt(candidates.size());
        for (int i = 0; i < candidates.size(); i++) {
            BlockPos candidate = candidates.get((i + rotate) % candidates.size());
            if (isStandable(level, candidate)) return candidate.immutable();
        }

        BlockPos nearby = findStandableNear(level, anchor, 3, random);
        return nearby != null ? nearby : anchor.immutable();
    }

    private static BlockPos findStandableNear(ServerLevel level, BlockPos anchor, int radius, Random random) {
        for (int attempt = 0; attempt < 28; attempt++) {
            int x = anchor.getX() + random.nextInt(radius * 2 + 1) - radius;
            int z = anchor.getZ() + random.nextInt(radius * 2 + 1) - radius;
            for (int yOffset : new int[]{0, 1, -1, 2, -2}) {
                BlockPos candidate = new BlockPos(x, anchor.getY() + yOffset, z);
                if (isStandable(level, candidate)) return candidate.immutable();
            }
        }
        return null;
    }

    private static boolean isStandable(ServerLevel level, BlockPos pos) {
        if (!level.getFluidState(pos).isEmpty() || !level.getFluidState(pos.above()).isEmpty()) return false;
        if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) return false;
        if (!level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()) return false;
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    private static void updateNeeds(Resident resident, RuntimeState runtime) {
        resident.needs().add(NeedType.HUNGER, -TownLifeConfig.HUNGER_DECAY_PER_SECOND.get().floatValue());
        if (!runtime.sleeping) resident.needs().add(NeedType.ENERGY, -TownLifeConfig.ENERGY_DECAY_PER_SECOND.get().floatValue());
        resident.needs().add(NeedType.SOCIAL, -TownLifeConfig.SOCIAL_DECAY_PER_SECOND.get().floatValue());
        resident.needs().add(NeedType.FUN, -TownLifeConfig.FUN_DECAY_PER_SECOND.get().floatValue());
    }

    private static boolean hasNearbyDanger(ServerLevel level, Mob mob) {
        return !level.getEntitiesOfClass(Monster.class, mob.getBoundingBox().inflate(10D),
                monster -> monster.isAlive() && monster.distanceToSqr(mob) <= 100D).isEmpty();
    }

    private static void stopSleeping(Mob mob, RuntimeState runtime) {
        if (!runtime.sleeping) return;
        if (mob.level() instanceof ServerLevel level) {
            SleepService.wake(level, mob, runtime.sleepBedPos, runtime.sleepWakePos);
        } else {
            EasyNpcCompat.stopSleeping(mob);
        }
        runtime.sleeping = false;
        runtime.sleepBedPos = null;
        runtime.sleepWakePos = null;
        runtime.sleepRetryAt = 0L;
        runtime.mode = null;
        runtime.modeAnchor = null;
    }

    private static void maintainSleepingResidents(ServerLevel level) {
        if (RUNTIME.isEmpty()) return;
        for (Map.Entry<UUID, RuntimeState> entry : RUNTIME.entrySet()) {
            RuntimeState runtime = entry.getValue();
            if (!runtime.sleeping || runtime.sleepBedPos == null) continue;
            Entity entity = level.getEntity(entry.getKey());
            if (!(entity instanceof Mob mob) || !EasyNpcCompat.isEasyNpc(entity)) continue;
            if (Season2NpcProtection.isProtected(level, mob)) {
                runtime.sleeping = false;
                runtime.sleepBedPos = null;
                runtime.sleepWakePos = null;
                runtime.mode = null;
                runtime.modeAnchor = null;
                continue;
            }
            if (!SleepService.maintain(level, mob, runtime.sleepBedPos)) {
                runtime.sleeping = false;
                runtime.sleepBedPos = null;
                runtime.sleepWakePos = null;
                runtime.sleepRetryAt = level.getGameTime() + 40L;
                runtime.mode = null;
                runtime.modeAnchor = null;
            }
        }
    }

    private static void finishActivity(Resident resident, Mob mob, RuntimeState runtime, long gameTime, String reason) {
        stopSleeping(mob, runtime);
        EasyNpcCompat.enterStationaryState(mob);
        mob.getNavigation().stop();
        runtime.resetMovement();
        resident.clearActivity(reason, gameTime);
    }

    private static void cleanupProviderReservations(long gameTime) {
        PROVIDER_RESERVATIONS.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= gameTime);
    }

    private static String readable(Activity activity) {
        return activity.name().toLowerCase().replace('_', ' ');
    }

    private enum ResidentMode {
        HOME,
        COMMUTING,
        WORK,
        ERRAND,
        SLEEPING
    }

    private enum ServiceRequest {
        FOOD("food"), TOOL("a tool"), ARMOR("armour");
        private final String displayName;
        ServiceRequest(String displayName) { this.displayName = displayName; }
        public String displayName() { return displayName; }
    }

    private record Provider(Resident resident, Mob mob, TownLocation workplace) {}
    private record ProviderReservation(UUID customer, long expiresAt) {}

    private static final class RuntimeState {
        private BlockPos anchor;
        private BlockPos finalTarget;
        private boolean locationArrived;
        private long nextPathRefreshTick;
        private double lastDistanceSqr = Double.MAX_VALUE;
        private int stuckSeconds;
        private long pausedUntil;
        private boolean sleeping;
        private BlockPos sleepBedPos;
        private BlockPos sleepWakePos;
        private long sleepRetryAt;
        private ServiceRequest serviceRequest;
        private UUID providerUuid;
        private int serviceFailures;
        private boolean interacting;
        private long interactionEndsAt;
        private long nextServiceRetryTick;
        private ResidentMode mode;
        private BlockPos modeAnchor;
        private boolean presetConfigured;

        private Random random(UUID uuid, long salt) {
            return new Random(uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits() ^ salt);
        }

        private void resetMovement() {
            anchor = null;
            finalTarget = null;
            locationArrived = false;
            nextPathRefreshTick = 0L;
            lastDistanceSqr = Double.MAX_VALUE;
            stuckSeconds = 0;
            sleepRetryAt = 0L;
            mode = null;
            modeAnchor = null;
        }

        private void clearService() {
            serviceRequest = null;
            providerUuid = null;
            serviceFailures = 0;
            interacting = false;
            interactionEndsAt = 0L;
            resetMovement();
        }
    }
}
