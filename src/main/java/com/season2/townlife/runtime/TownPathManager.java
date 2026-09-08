package com.season2.townlife.runtime;

import com.season2.townlife.config.TownLifeConfig;
import com.season2.townlife.data.Resident;
import com.season2.townlife.data.Town;
import com.season2.townlife.data.TownLifeSavedData;
import com.season2.townlife.data.TownLocation;
import com.season2.townlife.data.TownPathSavedData;
import com.season2.townlife.logic.Activity;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

/**
 * Gives Town Life residents road waypoints from the registered Town Path graph.
 * EasyNPC/Minecraft remains responsible for the actual path between waypoints.
 */
public final class TownPathManager {
    private static final Map<UUID, RouteState> ROUTES = new HashMap<>();
    private static final Map<UUID, Long> SUSPENDED_UNTIL = new HashMap<>();
    private static final int WAYPOINT_NATIVE_RANGE = 80;

    private TownPathManager() {}

    public static void suspend(UUID residentUuid, long untilGameTime) {
        SUSPENDED_UNTIL.merge(residentUuid, untilGameTime, Math::max);
    }

    public static void forget(UUID residentUuid) {
        ROUTES.remove(residentUuid);
        SUSPENDED_UNTIL.remove(residentUuid);
    }

    public static void tick(ServerLevel level) {
        long gameTime = level.getGameTime();
        if (gameTime % 5L != 0L) return;

        SUSPENDED_UNTIL.entrySet().removeIf(entry -> entry.getValue() <= gameTime);
        TownPathSavedData pathData = TownPathSavedData.get(level);
        String dimension = level.dimension().location().toString();
        if (pathData.count() < 2) {
            ROUTES.entrySet().removeIf(entry -> dimension.equals(entry.getValue().dimension));
            return;
        }

        TownLifeSavedData townData = TownLifeSavedData.get(level);
        Set<Long> packedPaths = pathData.packedPositions();
        for (Resident resident : townData.residents()) {
            UUID uuid = resident.entityUuid();
            Entity entity = level.getEntity(uuid);
            if (!(entity instanceof Mob mob) || !EasyNpcCompat.isEasyNpc(entity)) {
                ROUTES.remove(uuid);
                continue;
            }
            if (Season2NpcProtection.isProtected(level, mob)) {
                forget(uuid);
                continue;
            }
            if (SUSPENDED_UNTIL.getOrDefault(uuid, 0L) > gameTime) continue;
            if (!shouldUseTownPath(resident, mob)) {
                ROUTES.remove(uuid);
                continue;
            }

            Town town = townData.town(resident.townId()).orElse(null);
            TownLocation destination = town == null ? null : town.location(resident.targetLocationId()).orElse(null);
            if (destination == null) {
                ROUTES.remove(uuid);
                continue;
            }

            double destinationDistance = mob.distanceToSqr(
                    destination.anchor().getX() + 0.5D,
                    destination.anchor().getY(),
                    destination.anchor().getZ() + 0.5D);
            if (destinationDistance <= 64D) {
                RouteState previous = ROUTES.remove(uuid);
                if (previous != null && previous.engaged) releaseRoadControl(mob);
                continue;
            }

            String targetKey = resident.townId() + ":" + resident.targetLocationId();
            RouteState state = ROUTES.computeIfAbsent(uuid, ignored -> new RouteState());
            boolean contextChanged = !dimension.equals(state.dimension)
                    || !targetKey.equals(state.targetKey)
                    || state.pathRevision != pathData.revision();
            if (contextChanged) state.reset(dimension, targetKey, pathData.revision());

            if (state.completed) {
                if (destinationDistance <= 144D) continue;
                state.completed = false;
                state.waypoints = List.of();
                state.waypointIndex = 0;
            }

            if (state.waypoints.isEmpty()) {
                if (gameTime < state.retryAt) continue;
                state.waypoints = TownPathRouter.route(
                        packedPaths,
                        mob.blockPosition(),
                        destination.anchor(),
                        TownLifeConfig.PATH_ENTRY_RADIUS.get(),
                        TownLifeConfig.PATH_WAYPOINT_SPACING.get(),
                        TownLifeConfig.PATH_ROUTE_SEARCH_LIMIT.get());
                state.waypointIndex = 0;
                state.nextIssueAt = 0L;
                if (state.waypoints.isEmpty()) {
                    state.retryAt = gameTime + 100L;
                    continue;
                }
            }

            advanceReachedWaypoints(mob, state);
            if (state.waypointIndex >= state.waypoints.size()) {
                finishRoadSection(mob, state);
                continue;
            }

            BlockPos waypoint = state.waypoints.get(state.waypointIndex);
            double speed = TownLifeConfig.WALK_SPEED.get();
            boolean issueNow = gameTime % 20L == 0L
                    || gameTime >= state.nextIssueAt
                    || mob.getNavigation().isDone();
            if (issueNow) {
                EasyNpcCompat.enterTravelState(mob, waypoint, speed);
                EasyNpcCompat.startWidePath(mob, waypoint, speed, WAYPOINT_NATIVE_RANGE);
                state.nextIssueAt = gameTime + 20L;
                state.engaged = true;
            }
            resident.setReason("Following registered Town Path to " + readable(resident.activity()));
        }

        Iterator<Map.Entry<UUID, RouteState>> iterator = ROUTES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, RouteState> entry = iterator.next();
            if (!dimension.equals(entry.getValue().dimension)) continue;
            if (townData.resident(entry.getKey()).isEmpty()) iterator.remove();
        }
    }

    private static boolean shouldUseTownPath(Resident resident, Mob mob) {
        if (resident.targetLocationId().isBlank() || resident.performing() || mob.isSleeping()) return false;
        if (resident.activity() == Activity.IDLE || resident.activity() == Activity.SHELTER) return false;
        String reason = resident.reason();
        return !(reason.startsWith("Paused for a conversation")
                || reason.startsWith("Talking to ")
                || reason.startsWith("Talking with ")
                || reason.startsWith("Sleeping in "));
    }

    private static void advanceReachedWaypoints(Mob mob, RouteState state) {
        while (state.waypointIndex < state.waypoints.size()) {
            BlockPos waypoint = state.waypoints.get(state.waypointIndex);
            double distance = mob.distanceToSqr(
                    waypoint.getX() + 0.5D, waypoint.getY(), waypoint.getZ() + 0.5D);
            if (distance > 3.24D) break;
            state.waypointIndex++;
            state.nextIssueAt = 0L;
        }
    }

    private static void finishRoadSection(Mob mob, RouteState state) {
        if (state.completed) return;
        if (state.engaged) releaseRoadControl(mob);
        state.completed = true;
        state.engaged = false;
        state.waypoints = List.of();
        state.waypointIndex = 0;
        state.nextIssueAt = 0L;
    }

    private static void releaseRoadControl(Mob mob) {
        // Remove the temporary waypoint home/objective. TownLifeManager resumes
        // its normal short final approach to the actual bed/work/provider target.
        EasyNpcCompat.enterStationaryState(mob);
        mob.getNavigation().stop();
    }

    private static String readable(Activity activity) {
        return activity.name().toLowerCase().replace('_', ' ');
    }

    private static final class RouteState {
        private String dimension = "";
        private String targetKey = "";
        private int pathRevision = -1;
        private List<BlockPos> waypoints = List.of();
        private int waypointIndex;
        private long nextIssueAt;
        private long retryAt;
        private boolean completed;
        private boolean engaged;

        private void reset(String dimension, String targetKey, int pathRevision) {
            this.dimension = dimension;
            this.targetKey = targetKey;
            this.pathRevision = pathRevision;
            this.waypoints = List.of();
            this.waypointIndex = 0;
            this.nextIssueAt = 0L;
            this.retryAt = 0L;
            this.completed = false;
            this.engaged = false;
        }
    }
}
