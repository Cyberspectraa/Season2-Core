package com.season2.townlife.runtime;

import com.season2.townlife.config.TownLifeConfig;
import com.season2.townlife.data.TownPathSavedData;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Server-side Path Wand editing and visualization. */
public final class TownPathService {
    private static final int INSPECT_RADIUS = 24;
    private static final int INSPECT_LIMIT = 180;
    private static final int[] STEP_Y = {0, 1, -1};

    private TownPathService() {}

    public static void registerConnected(ServerLevel level, ServerPlayer player, BlockPos clicked) {
        if (!level.hasChunkAt(clicked)) return;
        BlockState seedState = level.getBlockState(clicked);
        if (seedState.isAir() || !isWalkableSurface(level, clicked)) {
            player.displayClientMessage(Component.literal("That block is not a usable path surface; it needs clear walking space above it.")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }

        Block seedBlock = seedState.getBlock();
        int limit = TownLifeConfig.PATH_BULK_REGISTER_LIMIT.get();
        Queue<BlockPos> open = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        List<BlockPos> connected = new ArrayList<>();

        BlockPos seed = clicked.immutable();
        open.add(seed);
        visited.add(seed.asLong());

        while (!open.isEmpty() && connected.size() < limit) {
            BlockPos pos = open.remove();
            if (!level.hasChunkAt(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() != seedBlock || !isWalkableSurface(level, pos)) continue;
            connected.add(pos.immutable());

            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos beside = pos.relative(direction);
                for (int yOffset : STEP_Y) {
                    BlockPos candidate = beside.offset(0, yOffset, 0);
                    long packed = candidate.asLong();
                    if (visited.contains(packed) || !level.hasChunkAt(candidate)) continue;
                    BlockState candidateState = level.getBlockState(candidate);
                    if (candidateState.getBlock() != seedBlock || !isWalkableSurface(level, candidate)) continue;
                    visited.add(packed);
                    open.add(candidate.immutable());
                }
            }
        }

        TownPathSavedData data = TownPathSavedData.get(level);
        int added = data.addAll(connected);
        boolean truncated = !open.isEmpty();
        String suffix = truncated ? " (scan stopped at safety limit)" : "";
        player.displayClientMessage(Component.literal(
                "Town Path: registered " + added + " new block" + (added == 1 ? "" : "s")
                        + " • " + data.count() + " total" + suffix)
                .withStyle(truncated ? ChatFormatting.YELLOW : ChatFormatting.GREEN), true);
        inspectNearby(level, player);
    }

    public static void removeSingle(ServerLevel level, ServerPlayer player, BlockPos clicked) {
        TownPathSavedData data = TownPathSavedData.get(level);
        if (!data.remove(clicked)) {
            player.displayClientMessage(Component.literal("That block is not registered as a Town Path block.")
                    .withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        player.displayClientMessage(Component.literal(
                "Town Path: removed this block • " + data.count() + " total")
                .withStyle(ChatFormatting.AQUA), true);
        inspectNearby(level, player);
    }

    public static void inspectNearby(ServerLevel level, ServerPlayer player) {
        TownPathSavedData data = TownPathSavedData.get(level);
        List<BlockPos> nearby = data.nearby(player.blockPosition(), INSPECT_RADIUS, INSPECT_LIMIT);
        for (BlockPos pos : nearby) {
            level.sendParticles(ParticleTypes.END_ROD,
                    pos.getX() + 0.5D, pos.getY() + 1.08D, pos.getZ() + 0.5D,
                    1, 0.03D, 0.02D, 0.03D, 0D);
        }
        player.displayClientMessage(Component.literal(
                "Town Paths: " + data.count() + " registered • highlighting " + nearby.size()
                        + " nearby block" + (nearby.size() == 1 ? "" : "s"))
                .withStyle(ChatFormatting.AQUA), true);
    }

    private static boolean isWalkableSurface(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getCollisionShape(level, pos).isEmpty()) return false;
        BlockPos feet = pos.above();
        BlockPos head = feet.above();
        if (!level.getFluidState(feet).isEmpty() || !level.getFluidState(head).isEmpty()) return false;
        return level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(head).getCollisionShape(level, head).isEmpty();
    }
}
