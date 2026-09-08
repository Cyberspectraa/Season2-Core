package com.season2.townlife.data;

import com.season2.townlife.logic.JobType;
import com.season2.townlife.logic.LocationType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public final class Town {
    private final String id;
    private String displayName;
    private BlockPos center;
    private int radius;
    private final Map<String, TownLocation> locations = new LinkedHashMap<>();

    public Town(String id, BlockPos center, int radius) {
        this.id = normalizeId(id);
        this.displayName = prettyName(id);
        this.center = center.immutable();
        this.radius = Math.max(8, radius);
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public BlockPos center() { return center; }
    public int radius() { return radius; }
    public Collection<TownLocation> locations() { return List.copyOf(locations.values()); }

    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public void setCenter(BlockPos center) { this.center = center.immutable(); }
    public void setRadius(int radius) { this.radius = Math.max(8, radius); }

    public boolean isInside(BlockPos pos) {
        long dx = (long) pos.getX() - center.getX();
        long dz = (long) pos.getZ() - center.getZ();
        return dx * dx + dz * dz <= (long) radius * radius;
    }

    public boolean addLocation(TownLocation location) {
        if (locations.containsKey(location.id())) return false;
        locations.put(location.id(), location);
        return true;
    }

    public TownLocation putLocation(TownLocation location) {
        return locations.put(location.id(), location);
    }

    public Optional<TownLocation> location(String id) {
        return Optional.ofNullable(locations.get(normalizeId(id)));
    }

    public boolean removeLocation(String id) {
        return locations.remove(normalizeId(id)) != null;
    }

    public List<TownLocation> locationsOfType(LocationType type) {
        return locations.values().stream().filter(location -> location.type() == type).toList();
    }

    public List<TownLocation> workplacesFor(JobType jobType) {
        return locations.values().stream()
                .filter(location -> location.type() == LocationType.WORKPLACE)
                .filter(location -> location.jobType() == jobType)
                .toList();
    }

    public Optional<TownLocation> nearest(LocationType type, BlockPos from, long dayTime) {
        return locations.values().stream()
                .filter(location -> location.type() == type)
                .filter(location -> location.isOpen(dayTime))
                .min(Comparator.comparingDouble(location -> location.anchor().distSqr(from)));
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", id);
        tag.putString("DisplayName", displayName);
        tag.putLong("Center", center.asLong());
        tag.putInt("Radius", radius);

        ListTag list = new ListTag();
        for (TownLocation location : locations.values()) {
            list.add(location.save());
        }
        tag.put("Locations", list);
        return tag;
    }

    public static Town load(CompoundTag tag) {
        Town town = new Town(tag.getString("Id"), BlockPos.of(tag.getLong("Center")), tag.getInt("Radius"));
        if (tag.contains("DisplayName", Tag.TAG_STRING)) {
            town.displayName = tag.getString("DisplayName");
        }
        ListTag list = tag.getList("Locations", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            TownLocation location = TownLocation.load(list.getCompound(i));
            town.locations.put(location.id(), location);
        }
        return town;
    }

    public static String normalizeId(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private static String prettyName(String id) {
        String[] words = id.replace('-', '_').split("_");
        List<String> result = new ArrayList<>();
        for (String word : words) {
            if (word.isBlank()) continue;
            result.add(Character.toUpperCase(word.charAt(0)) + word.substring(1));
        }
        return String.join(" ", result);
    }
}
