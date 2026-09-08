package com.season2.townlife.data;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Persistent registered Town Path surface blocks for one dimension. */
public final class TownPathSavedData extends SavedData {
    private static final String DATA_NAME = "townlife_paths";
    private static final int DATA_VERSION = 1;

    private final Set<Long> pathBlocks = new LinkedHashSet<>();
    private int revision;

    public TownPathSavedData() {}

    public static TownPathSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TownPathSavedData::load, TownPathSavedData::new, DATA_NAME);
    }

    public int count() {
        return pathBlocks.size();
    }

    public int revision() {
        return revision;
    }

    public boolean contains(BlockPos pos) {
        return pos != null && pathBlocks.contains(pos.asLong());
    }

    public Set<Long> packedPositions() {
        return Set.copyOf(pathBlocks);
    }

    public int addAll(Iterable<BlockPos> positions) {
        int added = 0;
        for (BlockPos pos : positions) {
            if (pos != null && pathBlocks.add(pos.asLong())) added++;
        }
        if (added > 0) {
            revision++;
            setDirty();
        }
        return added;
    }

    public boolean remove(BlockPos pos) {
        if (pos == null || !pathBlocks.remove(pos.asLong())) return false;
        revision++;
        setDirty();
        return true;
    }

    public List<BlockPos> nearby(BlockPos center, int radius, int limit) {
        int radiusSqr = radius * radius;
        List<BlockPos> result = new ArrayList<>();
        for (long packed : pathBlocks) {
            BlockPos pos = BlockPos.of(packed);
            int dx = pos.getX() - center.getX();
            int dz = pos.getZ() - center.getZ();
            if (dx * dx + dz * dz <= radiusSqr && Math.abs(pos.getY() - center.getY()) <= 6) {
                result.add(pos);
            }
        }
        result.sort(Comparator.comparingDouble(center::distSqr));
        if (result.size() > limit) return List.copyOf(result.subList(0, limit));
        return List.copyOf(result);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("DataVersion", DATA_VERSION);
        long[] packed = new long[pathBlocks.size()];
        int index = 0;
        for (long value : pathBlocks) packed[index++] = value;
        tag.putLongArray("PathBlocks", packed);
        return tag;
    }

    public static TownPathSavedData load(CompoundTag tag) {
        TownPathSavedData data = new TownPathSavedData();
        for (long packed : tag.getLongArray("PathBlocks")) data.pathBlocks.add(packed);
        return data;
    }
}
