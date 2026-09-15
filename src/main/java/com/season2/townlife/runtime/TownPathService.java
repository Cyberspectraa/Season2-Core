package com.season2.townlife.runtime;

import com.season2.townlife.config.TownLifeConfig;
import com.season2.townlife.data.TownPathSavedData;
import com.season2.townlife.data.TownPathType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
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
        registerConnected(level, player, clicked, TownPathType.NORMAL);
    }

    public static void registerConnected(ServerLevel level, ServerPlayer player, BlockPos clicked, TownPathType type) {
        if (!level.hasChunkAt(clicked)) return;
        BlockState seedState = level.getBlockState(clicked);
        if (seedState.isAir() || !isWalkableSurface(level, clicked)) {
            invalidSurface(player);
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
        int changed = data.setAll(connected, type);
        boolean truncated = !open.isEmpty();
        String suffix = truncated ? " (scan stopped at safety limit)" : "";
        player.displayClientMessage(Component.literal(
                "Town Path: updated " + changed + " block" + (changed == 1 ? "" : "s")
                        + " as " + safe(type).displayName() + " • " + data.count() + " total" + suffix)
                .withStyle(truncated ? ChatFormatting.YELLOW : ChatFormatting.GREEN), true);
        inspectNearby(level, player);
    }

    public static void registerSingle(ServerLevel level, ServerPlayer player, BlockPos clicked, TownPathType type) {
        if (!level.hasChunkAt(clicked) || !isWalkableSurface(level, clicked)) {
            invalidSurface(player);
            return;
        }
        TownPathSavedData data = TownPathSavedData.get(level);
        int changed = data.setAll(List.of(clicked.immutable()), type);
        player.displayClientMessage(Component.literal(
                "Town Path: " + (changed == 0 ? "already " : "set ") + safe(type).displayName()
                        + " • " + data.count() + " total")
                .withStyle(ChatFormatting.GREEN), true);
        highlight(level, clicked, safe(type), 4);
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

    /**
     * Removes the connected registered section that matches both the clicked path type and block material.
     * Matching the material makes the bulk delete safer at mixed-material intersections.
     */
    public static void removeConnected(ServerLevel level, ServerPlayer player, BlockPos clicked) {
        TownPathSavedData data = TownPathSavedData.get(level);
        TownPathType seedType = data.typeAt(clicked);
        if (seedType == null) {
            player.displayClientMessage(Component.literal("That block is not registered as a Town Path block.")
                    .withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        if (!level.hasChunkAt(clicked)) return;

        Block seedBlock = level.getBlockState(clicked).getBlock();
        int limit = TownLifeConfig.PATH_BULK_REGISTER_LIMIT.get();
        Queue<BlockPos> open = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        List<BlockPos> connected = new ArrayList<>();
        open.add(clicked.immutable());
        visited.add(clicked.asLong());

        while (!open.isEmpty() && connected.size() < limit) {
            BlockPos pos = open.remove();
            if (data.typeAt(pos) != seedType || level.getBlockState(pos).getBlock() != seedBlock) continue;
            connected.add(pos.immutable());
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos beside = pos.relative(direction);
                for (int yOffset : STEP_Y) {
                    BlockPos candidate = beside.offset(0, yOffset, 0);
                    long packed = candidate.asLong();
                    if (!visited.add(packed)) continue;
                    if (data.typeAt(candidate) == seedType
                            && level.hasChunkAt(candidate)
                            && level.getBlockState(candidate).getBlock() == seedBlock) {
                        open.add(candidate.immutable());
                    }
                }
            }
        }

        int removed = data.removeAll(connected);
        boolean truncated = !open.isEmpty();
        player.displayClientMessage(Component.literal(
                "Town Path: removed " + removed + " connected " + seedType.displayName()
                        + " block" + (removed == 1 ? "" : "s")
                        + (truncated ? " (stopped at safety limit)" : ""))
                .withStyle(truncated ? ChatFormatting.YELLOW : ChatFormatting.AQUA), true);
        inspectNearby(level, player);
    }

    public static void inspectBlock(ServerLevel level, ServerPlayer player, BlockPos clicked) {
        TownPathSavedData data = TownPathSavedData.get(level);
        TownPathType type = data.typeAt(clicked);
        if (type == null) {
            player.displayClientMessage(Component.literal("This block is not registered as a Town Path.")
                    .withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        player.displayClientMessage(Component.literal(
                "Town Path: " + type.displayName() + " • " + clicked.toShortString())
                .withStyle(ChatFormatting.AQUA), true);
        highlight(level, clicked, type, 8);
        inspectNearby(level, player);
    }

    public static void inspectNearby(ServerLevel level, ServerPlayer player) {
        TownPathSavedData data = TownPathSavedData.get(level);
        List<BlockPos> nearby = data.nearby(player.blockPosition(), INSPECT_RADIUS, INSPECT_LIMIT);
        Map<Long, TownPathType> typed = data.typedPositions();
        int main = 0;
        int normal = 0;
        int low = 0;
        int avoid = 0;
        for (BlockPos pos : nearby) {
            TownPathType type = typed.getOrDefault(pos.asLong(), TownPathType.NORMAL);
            highlight(level, pos, type, 1);
            switch (type) {
                case MAIN -> main++;
                case NORMAL -> normal++;
                case LOW -> low++;
                case AVOID -> avoid++;
            }
        }
        player.displayClientMessage(Component.literal(
                "Town Paths: " + data.count() + " total • nearby M:" + main
                        + " N:" + normal + " L:" + low + " A:" + avoid)
                .withStyle(ChatFormatting.AQUA), true);
    }

    private static void invalidSurface(ServerPlayer player) {
        player.displayClientMessage(Component.literal(
                "That block is not a usable path surface; it needs clear walking space above it.")
                .withStyle(ChatFormatting.RED), true);
    }

    private static TownPathType safe(TownPathType type) {
        return type == null ? TownPathType.NORMAL : type;
    }

    private static void highlight(ServerLevel level, BlockPos pos, TownPathType type, int count) {
        ParticleOptions particle = switch (safe(type)) {
            case MAIN -> ParticleTypes.HAPPY_VILLAGER;
            case NORMAL -> ParticleTypes.END_ROD;
            case LOW -> ParticleTypes.CRIT;
            case AVOID -> ParticleTypes.SMOKE;
        };
        level.sendParticles(particle,
                pos.getX() + 0.5D, pos.getY() + 1.08D, pos.getZ() + 0.5D,
                count, 0.05D, 0.03D, 0.05D, 0D);
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
