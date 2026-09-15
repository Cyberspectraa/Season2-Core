package com.season2.townlife.data;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Persistent registered Town Path surface blocks for one dimension. */
public final class TownPathSavedData extends SavedData {
    private static final String DATA_NAME = "townlife_paths";
    private static final int DATA_VERSION = 2;

    private final Map<Long, TownPathType> pathBlocks = new LinkedHashMap<>();
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
        return pos != null && pathBlocks.containsKey(pos.asLong());
    }

    public TownPathType typeAt(BlockPos pos) {
        return pos == null ? null : pathBlocks.get(pos.asLong());
    }

    public Set<Long> packedPositions() {
        return Set.copyOf(pathBlocks.keySet());
    }

    public Map<Long, TownPathType> typedPositions() {
        return Map.copyOf(pathBlocks);
    }

    /** Legacy-compatible helper. New registrations should use {@link #setAll(Iterable, TownPathType)}. */
    public int addAll(Iterable<BlockPos> positions) {
        int added = 0;
        for (BlockPos pos : positions) {
            if (pos != null && pathBlocks.putIfAbsent(pos.asLong(), TownPathType.NORMAL) == null) added++;
        }
        if (added > 0) changed();
        return added;
    }

    /** Registers or retypes every supplied path block. Returns how many entries changed. */
    public int setAll(Iterable<BlockPos> positions, TownPathType type) {
        TownPathType safeType = type == null ? TownPathType.NORMAL : type;
        int changed = 0;
        for (BlockPos pos : positions) {
            if (pos == null) continue;
            TownPathType previous = pathBlocks.put(pos.asLong(), safeType);
            if (previous != safeType) changed++;
        }
        if (changed > 0) changed();
        return changed;
    }

    public boolean remove(BlockPos pos) {
        if (pos == null || pathBlocks.remove(pos.asLong()) == null) return false;
        changed();
        return true;
    }

    public int removeAll(Iterable<BlockPos> positions) {
        int removed = 0;
        for (BlockPos pos : positions) {
            if (pos != null && pathBlocks.remove(pos.asLong()) != null) removed++;
        }
        if (removed > 0) changed();
        return removed;
    }

    public List<BlockPos> nearby(BlockPos center, int radius, int limit) {
        int radiusSqr = radius * radius;
        List<BlockPos> result = new ArrayList<>();
        for (long packed : pathBlocks.keySet()) {
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

        ListTag entries = new ListTag();
        long[] legacyPacked = new long[pathBlocks.size()];
        int index = 0;
        for (Map.Entry<Long, TownPathType> entry : pathBlocks.entrySet()) {
            CompoundTag path = new CompoundTag();
            path.putLong("Pos", entry.getKey());
            path.putString("Type", entry.getValue().name());
            entries.add(path);
            legacyPacked[index++] = entry.getKey();
        }
        tag.put("PathEntries", entries);

        // Keep the old list as a downgrade-friendly mirror. Version 1 loads these as normal paths.
        tag.putLongArray("PathBlocks", legacyPacked);
        return tag;
    }

    public static TownPathSavedData load(CompoundTag tag) {
        TownPathSavedData data = new TownPathSavedData();
        ListTag entries = tag.getList("PathEntries", Tag.TAG_COMPOUND);
        if (!entries.isEmpty()) {
            for (int i = 0; i < entries.size(); i++) {
                CompoundTag path = entries.getCompound(i);
                if (!path.contains("Pos", Tag.TAG_LONG)) continue;
                data.pathBlocks.put(path.getLong("Pos"), TownPathType.fromSavedName(path.getString("Type")));
            }
            return data;
        }

        // Automatic v1 migration: every previously registered path becomes Normal Path.
        for (long packed : tag.getLongArray("PathBlocks")) {
            data.pathBlocks.put(packed, TownPathType.NORMAL);
        }
        return data;
    }

    private void changed() {
        revision++;
        setDirty();
    }
}
