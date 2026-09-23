package com.season2.townlife.runtime;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/** Validates a player-selected floor square, without loading distant chunks or changing NPC navigation. */
public final class WorkPositionService {
    private WorkPositionService() {}

    public static boolean isStandable(ServerLevel level, BlockPos feet) {
        if (feet == null || !level.hasChunkAt(feet) || !level.hasChunkAt(feet.below())) return false;
        if (!level.getFluidState(feet).isEmpty() || !level.getFluidState(feet.above()).isEmpty()) return false;
        if (!level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()) return false;
        if (!level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) return false;
        BlockPos floor = feet.below();
        return level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP);
    }
}
