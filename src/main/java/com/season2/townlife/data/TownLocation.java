package com.season2.townlife.data;

import com.season2.townlife.logic.JobType;
import com.season2.townlife.logic.LocationType;
import com.season2.townlife.logic.SpotKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * A semantic town area. 0.2.x locations were a single anchor; 0.3.x keeps the
 * anchor for compatibility but adds inclusive cuboid bounds and discovered POIs.
 */
public final class TownLocation {
    private final String id;
    private LocationType type;
    private BlockPos anchor;
    private BlockPos min;
    private BlockPos max;
    private int capacity;
    private int openTime;
    private int closeTime;
    private JobType jobType;
    private boolean needsReview;
    private final List<BlockPos> activitySpots = new ArrayList<>();
    private final List<TownSpot> spots = new ArrayList<>();

    public TownLocation(String id, LocationType type, BlockPos anchor) {
        this.id = normalizeId(id);
        this.type = type;
        this.anchor = anchor.immutable();
        this.min = anchor.immutable();
        this.max = anchor.immutable();
        this.capacity = 1;
        this.openTime = type.defaultOpenTime();
        this.closeTime = type.defaultCloseTime();
        this.jobType = JobType.UNEMPLOYED;
        this.needsReview = true;
    }

    public String id() { return id; }
    public LocationType type() { return type; }
    public BlockPos anchor() { return anchor; }
    public BlockPos min() { return min; }
    public BlockPos max() { return max; }
    public int capacity() { return capacity; }
    public int openTime() { return openTime; }
    public int closeTime() { return closeTime; }
    public JobType jobType() { return jobType; }
    public boolean needsReview() { return needsReview; }
    public List<BlockPos> activitySpots() { return List.copyOf(activitySpots); }
    public List<TownSpot> spots() { return List.copyOf(spots); }

    public void setType(LocationType type) { this.type = type; }
    public void setAnchor(BlockPos anchor) { this.anchor = anchor.immutable(); }
    public void setCapacity(int capacity) { this.capacity = Math.max(1, capacity); }
    public void setNeedsReview(boolean value) { this.needsReview = value; }
    public void setHours(int openTime, int closeTime) {
        this.openTime = Math.floorMod(openTime, 24000);
        this.closeTime = Math.floorMod(closeTime, 24000);
    }
    public void setJobType(JobType jobType) { this.jobType = jobType; }

    public void setBounds(BlockPos first, BlockPos second) {
        this.min = new BlockPos(
                Math.min(first.getX(), second.getX()),
                Math.min(first.getY(), second.getY()),
                Math.min(first.getZ(), second.getZ()));
        this.max = new BlockPos(
                Math.max(first.getX(), second.getX()),
                Math.max(first.getY(), second.getY()),
                Math.max(first.getZ(), second.getZ()));
        this.anchor = new BlockPos(
                (min.getX() + max.getX()) / 2,
                min.getY(),
                (min.getZ() + max.getZ()) / 2);
        this.needsReview = false;
    }

    public int sizeX() { return max.getX() - min.getX() + 1; }
    public int sizeY() { return max.getY() - min.getY() + 1; }
    public int sizeZ() { return max.getZ() - min.getZ() + 1; }
    public long volume() { return (long) sizeX() * sizeY() * sizeZ(); }

    public boolean contains(BlockPos pos) {
        return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    public BlockPos center() {
        return new BlockPos(
                (min.getX() + max.getX()) / 2,
                (min.getY() + max.getY()) / 2,
                (min.getZ() + max.getZ()) / 2);
    }

    public List<TownSpot> spotsOfKind(SpotKind kind) {
        return spots.stream().filter(spot -> spot.kind() == kind).toList();
    }

    public void replaceScannedSpots(List<TownSpot> scanned) {
        spots.clear();
        spots.addAll(scanned);
        if (!spots.isEmpty()) capacity = Math.max(capacity, Math.min(32, spots.size()));
    }

    public void addSpot(SpotKind kind, BlockPos pos, Direction facing) {
        TownSpot candidate = new TownSpot(kind, pos, facing);
        if (!spots.contains(candidate)) spots.add(candidate);
    }

    public void addActivitySpot(BlockPos pos) {
        BlockPos immutable = pos.immutable();
        if (!activitySpots.contains(immutable)) {
            activitySpots.add(immutable);
            addSpot(SpotKind.WORK, immutable, Direction.NORTH);
            if (capacity < activitySpots.size()) capacity = activitySpots.size();
        }
    }

    public void clearActivitySpots() {
        activitySpots.clear();
        spots.removeIf(spot -> spot.kind() == SpotKind.WORK);
        capacity = Math.max(1, capacity);
    }

    public boolean isOpen(long dayTime) {
        if (openTime == closeTime) return true;
        int time = (int) Math.floorMod(dayTime, 24000L);
        if (openTime < closeTime) return time >= openTime && time < closeTime;
        return time >= openTime || time < closeTime;
    }

    /** Legacy callers get a varied valid point where possible. */
    public BlockPos pickActivitySpot(Random random) {
        List<TownSpot> roam = spotsOfKind(SpotKind.ROAM);
        if (!roam.isEmpty()) return roam.get(random.nextInt(roam.size())).pos();
        if (!activitySpots.isEmpty()) return activitySpots.get(random.nextInt(activitySpots.size()));
        return center();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", id);
        tag.putString("Type", type.name());
        tag.putLong("Anchor", anchor.asLong());
        tag.putLong("Min", min.asLong());
        tag.putLong("Max", max.asLong());
        tag.putBoolean("NeedsReview", needsReview);
        tag.putInt("Capacity", capacity);
        tag.putInt("OpenTime", openTime);
        tag.putInt("CloseTime", closeTime);
        tag.putString("JobType", jobType.name());

        ListTag legacySpots = new ListTag();
        for (BlockPos spot : activitySpots) {
            CompoundTag spotTag = new CompoundTag();
            spotTag.putLong("Pos", spot.asLong());
            legacySpots.add(spotTag);
        }
        tag.put("ActivitySpots", legacySpots);

        ListTag spotList = new ListTag();
        for (TownSpot spot : spots) spotList.add(spot.save());
        tag.put("TownSpots", spotList);
        return tag;
    }

    public static TownLocation load(CompoundTag tag) {
        LocationType type;
        try { type = LocationType.valueOf(tag.getString("Type")); }
        catch (IllegalArgumentException ex) { type = LocationType.TOWN_CENTRE; }

        BlockPos anchor = BlockPos.of(tag.getLong("Anchor"));
        TownLocation location = new TownLocation(tag.getString("Id"), type, anchor);
        if (tag.contains("Min", Tag.TAG_LONG) && tag.contains("Max", Tag.TAG_LONG)) {
            location.min = BlockPos.of(tag.getLong("Min"));
            location.max = BlockPos.of(tag.getLong("Max"));
            location.needsReview = tag.getBoolean("NeedsReview");
        } else {
            // 0.2.x migration: retain the old marker as a one-block placeholder.
            location.min = anchor;
            location.max = anchor;
            location.needsReview = true;
        }
        location.capacity = Math.max(1, tag.getInt("Capacity"));
        location.openTime = Math.floorMod(tag.getInt("OpenTime"), 24000);
        location.closeTime = Math.floorMod(tag.getInt("CloseTime"), 24000);
        try { location.jobType = JobType.valueOf(tag.getString("JobType")); }
        catch (IllegalArgumentException ex) { location.jobType = JobType.UNEMPLOYED; }

        ListTag legacySpots = tag.getList("ActivitySpots", Tag.TAG_COMPOUND);
        for (int i = 0; i < legacySpots.size(); i++) {
            BlockPos pos = BlockPos.of(legacySpots.getCompound(i).getLong("Pos"));
            location.activitySpots.add(pos);
        }

        ListTag spotList = tag.getList("TownSpots", Tag.TAG_COMPOUND);
        for (int i = 0; i < spotList.size(); i++) location.spots.add(TownSpot.load(spotList.getCompound(i)));
        if (spotList.isEmpty()) {
            for (BlockPos pos : location.activitySpots) location.addSpot(SpotKind.WORK, pos, Direction.NORTH);
        }
        return location;
    }

    private static String normalizeId(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
