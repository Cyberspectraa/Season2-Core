package com.season2.townlife.runtime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

/**
 * Handles the final transition between normal navigation and vanilla sleeping.
 *
 * <p>The resident must first walk to a valid bedside square. Only then do we
 * resolve the clicked bed to its canonical head block and call Minecraft's
 * real sleeping API. While sleeping we re-assert the bed attachment at the end
 * of each level tick if another Easy NPC goal moved the entity away.</p>
 */
public final class SleepService {
    private static final double APPROACH_DISTANCE = 2.10D;
    private static final double MAX_SLEEP_OFFSET_SQR = 2.25D;

    private SleepService() {}

    public static SleepStart begin(ServerLevel level, Mob mob, BlockPos assignedBed, BlockPos approach) {
        BlockPos bed = canonicalBed(level, assignedBed);
        if (bed == null) return SleepStart.failed("Assigned bed is missing");
        if (approach == null || !isStandable(level, approach)) {
            return SleepStart.failed("No safe bedside standing position");
        }
        if (!near(mob, approach, APPROACH_DISTANCE)) {
            return SleepStart.failed("Resident has not reached the bedside yet");
        }

        mob.getNavigation().stop();
        if (!EasyNpcCompat.startSleeping(mob, bed)) {
            EasyNpcCompat.stopSleeping(mob);
            return SleepStart.failed("Easy NPC could not enter vanilla bed sleep");
        }

        if (!isAttachedToBed(level, mob, bed)) {
            EasyNpcCompat.stopSleeping(mob);
            return SleepStart.failed("Sleep started but the NPC did not attach to the bed");
        }
        return new SleepStart(true, bed, approach.immutable(), "Sleeping in assigned bed");
    }

    /** Called every level tick while Town Life considers an NPC asleep. */
    public static boolean maintain(ServerLevel level, Mob mob, BlockPos canonicalBed) {
        BlockPos bed = canonicalBed(level, canonicalBed);
        if (bed == null) {
            EasyNpcCompat.stopSleeping(mob);
            return false;
        }

        mob.getNavigation().stop();
        if (!isAttachedToBed(level, mob, bed)) {
            // Easy NPC can run its own goals after Town Life begins sleeping.
            // Re-running vanilla startSleeping is intentionally used instead of
            // manually forcing Pose.SLEEPING, because vanilla also aligns the
            // entity's body and sleeping position to the bed orientation.
            EasyNpcCompat.stopSleeping(mob);
            if (!EasyNpcCompat.startSleeping(mob, bed)) return false;
        }
        return isAttachedToBed(level, mob, bed);
    }

    public static void wake(ServerLevel level, Mob mob, BlockPos bed, BlockPos preferredExit) {
        EasyNpcCompat.stopSleeping(mob);
        BlockPos exit = chooseWakePosition(level, preferredExit, bed);
        if (exit != null) {
            // This is only the tiny final bed -> bedside transition after the NPC
            // already walked to the bedroom. It is not a pathfinding teleport.
            mob.teleportTo(exit.getX() + 0.5D, exit.getY(), exit.getZ() + 0.5D);
        }
    }

    public static BlockPos canonicalBed(ServerLevel level, BlockPos assigned) {
        if (assigned == null) return null;
        BlockState state = level.getBlockState(assigned);
        if (!(state.getBlock() instanceof BedBlock)) return null;

        Direction facing = state.getValue(BedBlock.FACING);
        BedPart part = state.getValue(BedBlock.PART);
        if (part == BedPart.HEAD) return assigned.immutable();

        BlockPos expectedHead = assigned.relative(facing);
        BlockState headState = level.getBlockState(expectedHead);
        if (headState.getBlock() instanceof BedBlock
                && headState.getValue(BedBlock.PART) == BedPart.HEAD) {
            return expectedHead.immutable();
        }

        // Graceful recovery for edited/rotated beds: find an adjacent bed head.
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos nearbyPos = assigned.relative(direction);
            BlockState nearby = level.getBlockState(nearbyPos);
            if (nearby.getBlock() instanceof BedBlock
                    && nearby.getValue(BedBlock.PART) == BedPart.HEAD) {
                return nearbyPos.immutable();
            }
        }
        return null;
    }

    private static boolean isAttachedToBed(ServerLevel level, Mob mob, BlockPos bed) {
        if (!mob.isSleeping()) return false;
        if (mob.getSleepingPos().filter(bed::equals).isEmpty()) return false;
        double distanceSqr = mob.distanceToSqr(bed.getX() + 0.5D, bed.getY() + 0.5D, bed.getZ() + 0.5D);
        return distanceSqr <= MAX_SLEEP_OFFSET_SQR;
    }

    private static BlockPos chooseWakePosition(ServerLevel level, BlockPos preferred, BlockPos bed) {
        if (preferred != null && isStandable(level, preferred)) return preferred.immutable();
        if (bed == null) return null;
        List<BlockPos> candidates = standingCandidates(level, bed, 2);
        return candidates.stream().min(Comparator.comparingDouble(pos -> pos.distSqr(bed))).orElse(null);
    }

    private static List<BlockPos> standingCandidates(ServerLevel level, BlockPos anchor, int radius) {
        List<BlockPos> candidates = new ArrayList<>();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x == 0 && z == 0) continue;
                for (int y : new int[]{0, 1, -1}) {
                    BlockPos pos = anchor.offset(x, y, z);
                    if (isStandable(level, pos)) candidates.add(pos.immutable());
                }
            }
        }
        return candidates;
    }

    private static boolean isStandable(ServerLevel level, BlockPos pos) {
        if (!level.getFluidState(pos).isEmpty() || !level.getFluidState(pos.above()).isEmpty()) return false;
        if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) return false;
        if (!level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()) return false;
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    private static boolean near(Mob mob, BlockPos pos, double distance) {
        return mob.distanceToSqr(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D) <= distance * distance;
    }

    public record SleepStart(boolean success, BlockPos bed, BlockPos wakePos, String reason) {
        private static SleepStart failed(String reason) {
            return new SleepStart(false, null, null, reason);
        }
    }
}
