package com.season2.townlife.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Lightweight route planner over explicitly registered road blocks.
 * This chooses high-level road waypoints only; Minecraft/EasyNPC still owns
 * collision-aware physical pathfinding between those waypoints.
 */
public final class TownPathRouter {
    private static final int[] STEP_Y = {0, 1, -1};

    private TownPathRouter() {}

    public static List<BlockPos> route(Set<Long> pathBlocks, BlockPos start, BlockPos destination,
                                       int entryRadius, int waypointSpacing, int maxVisited) {
        if (pathBlocks == null || pathBlocks.size() < 2) return List.of();
        Optional<Long> startPath = nearest(pathBlocks, start, entryRadius);
        Optional<Long> endPath = nearest(pathBlocks, destination, entryRadius);
        if (startPath.isEmpty() || endPath.isEmpty()) return List.of();

        List<BlockPos> raw = findPath(pathBlocks, startPath.get(), endPath.get(), maxVisited);
        if (raw.isEmpty()) return List.of();
        return simplify(raw, Math.max(2, waypointSpacing));
    }

    private static Optional<Long> nearest(Set<Long> pathBlocks, BlockPos origin, int radius) {
        long best = 0L;
        double bestScore = Double.MAX_VALUE;
        int radiusSqr = radius * radius;
        boolean found = false;
        for (long packed : pathBlocks) {
            BlockPos pos = BlockPos.of(packed);
            int dx = pos.getX() - origin.getX();
            int dz = pos.getZ() - origin.getZ();
            int dy = pos.getY() - origin.getY();
            int horizontal = dx * dx + dz * dz;
            if (horizontal > radiusSqr || Math.abs(dy) > 4) continue;
            double score = horizontal + dy * dy * 1.5D;
            if (score < bestScore) {
                bestScore = score;
                best = packed;
                found = true;
            }
        }
        return found ? Optional.of(best) : Optional.empty();
    }

    private static List<BlockPos> findPath(Set<Long> pathBlocks, long start, long goal, int maxVisited) {
        if (start == goal) return List.of(BlockPos.of(start));

        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(Node::fScore));
        Map<Long, Double> gScore = new HashMap<>();
        Map<Long, Long> cameFrom = new HashMap<>();
        Set<Long> closed = new HashSet<>();

        gScore.put(start, 0D);
        open.add(new Node(start, heuristic(BlockPos.of(start), BlockPos.of(goal))));

        while (!open.isEmpty() && closed.size() < maxVisited) {
            Node node = open.poll();
            long current = node.packed();
            if (!closed.add(current)) continue;
            if (current == goal) return reconstruct(cameFrom, current);

            BlockPos currentPos = BlockPos.of(current);
            double currentG = gScore.getOrDefault(current, Double.MAX_VALUE);
            for (long neighbor : neighbors(pathBlocks, currentPos)) {
                if (closed.contains(neighbor)) continue;
                BlockPos neighborPos = BlockPos.of(neighbor);
                double tentative = currentG + 1D + Math.abs(neighborPos.getY() - currentPos.getY()) * 0.35D;
                if (tentative >= gScore.getOrDefault(neighbor, Double.MAX_VALUE)) continue;
                cameFrom.put(neighbor, current);
                gScore.put(neighbor, tentative);
                double f = tentative + heuristic(neighborPos, BlockPos.of(goal));
                open.add(new Node(neighbor, f));
            }
        }
        return List.of();
    }

    private static List<Long> neighbors(Set<Long> pathBlocks, BlockPos pos) {
        List<Long> result = new ArrayList<>(12);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos beside = pos.relative(direction);
            for (int yOffset : STEP_Y) {
                long packed = beside.offset(0, yOffset, 0).asLong();
                if (pathBlocks.contains(packed)) result.add(packed);
            }
        }
        return result;
    }

    private static double heuristic(BlockPos from, BlockPos to) {
        return Math.abs(from.getX() - to.getX())
                + Math.abs(from.getZ() - to.getZ())
                + Math.abs(from.getY() - to.getY()) * 0.25D;
    }

    private static List<BlockPos> reconstruct(Map<Long, Long> cameFrom, long current) {
        List<BlockPos> result = new ArrayList<>();
        result.add(BlockPos.of(current));
        while (cameFrom.containsKey(current)) {
            current = cameFrom.get(current);
            result.add(BlockPos.of(current));
        }
        Collections.reverse(result);
        return result;
    }

    private static List<BlockPos> simplify(List<BlockPos> raw, int spacing) {
        if (raw.isEmpty()) return List.of();
        if (raw.size() == 1) return List.of(raw.get(0).above());

        List<BlockPos> waypoints = new ArrayList<>();
        addDistinct(waypoints, raw.get(0).above());
        Step previousStep = step(raw.get(0), raw.get(1));
        int lastAddedIndex = 0;

        for (int i = 2; i < raw.size(); i++) {
            Step nextStep = step(raw.get(i - 1), raw.get(i));
            if (!nextStep.equals(previousStep)) {
                addDistinct(waypoints, raw.get(i - 1).above());
                lastAddedIndex = i - 1;
            } else if (i - lastAddedIndex >= spacing) {
                addDistinct(waypoints, raw.get(i).above());
                lastAddedIndex = i;
            }
            previousStep = nextStep;
        }

        addDistinct(waypoints, raw.get(raw.size() - 1).above());
        return List.copyOf(waypoints);
    }

    private static Step step(BlockPos from, BlockPos to) {
        return new Step(
                Integer.signum(to.getX() - from.getX()),
                Integer.signum(to.getY() - from.getY()),
                Integer.signum(to.getZ() - from.getZ()));
    }

    private static void addDistinct(List<BlockPos> list, BlockPos pos) {
        BlockPos immutable = pos.immutable();
        if (list.isEmpty() || !list.get(list.size() - 1).equals(immutable)) list.add(immutable);
    }

    private record Node(long packed, double fScore) {}
    private record Step(int x, int y, int z) {}
}
