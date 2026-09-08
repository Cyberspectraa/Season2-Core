package com.season2.townlife.data;

import com.season2.townlife.logic.Activity;
import com.season2.townlife.logic.DailyFocus;
import com.season2.townlife.logic.JobType;
import com.season2.townlife.logic.NeedType;
import com.season2.townlife.logic.Needs;
import com.season2.townlife.logic.PersonalityTrait;
import java.util.EnumSet;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

public final class Resident {
    private final UUID entityUuid;
    private String townId;
    private String identityName;
    private final Needs needs = new Needs();
    private EnumSet<PersonalityTrait> traits = EnumSet.noneOf(PersonalityTrait.class);
    private JobType jobType = JobType.UNEMPLOYED;
    private String homeLocationId = "";
    private String workplaceLocationId = "";
    private String favoriteLocationId = "";
    private int wakeTime = 500;
    private int sleepTime = 13000;
    private int workStart = 1800;
    private int workEnd = 9800;
    private DailyFocus dailyFocus = DailyFocus.BALANCED;
    private long lastPlannedDay = Long.MIN_VALUE;
    private Activity activity = Activity.IDLE;
    private String targetLocationId = "";
    private boolean performing;
    private long activityEndsAt;
    private long lastDecisionTick;
    private String reason = "Waiting for first decision";

    public Resident(UUID entityUuid, String townId, String identityName) {
        this.entityUuid = entityUuid;
        this.townId = Town.normalizeId(townId);
        this.identityName = identityName;
    }

    public UUID entityUuid() { return entityUuid; }
    public String townId() { return townId; }
    public String identityName() { return identityName; }
    public Needs needs() { return needs; }
    public EnumSet<PersonalityTrait> traits() { return traits.clone(); }
    public JobType jobType() { return jobType; }
    public String homeLocationId() { return homeLocationId; }
    public String workplaceLocationId() { return workplaceLocationId; }
    public String favoriteLocationId() { return favoriteLocationId; }
    public int wakeTime() { return wakeTime; }
    public int sleepTime() { return sleepTime; }
    public int workStart() { return workStart; }
    public int workEnd() { return workEnd; }
    public DailyFocus dailyFocus() { return dailyFocus; }
    public long lastPlannedDay() { return lastPlannedDay; }
    public Activity activity() { return activity; }
    public String targetLocationId() { return targetLocationId; }
    public boolean performing() { return performing; }
    public long activityEndsAt() { return activityEndsAt; }
    public long lastDecisionTick() { return lastDecisionTick; }
    public String reason() { return reason; }

    public void setTownId(String townId) { this.townId = Town.normalizeId(townId); }
    public void setIdentityName(String identityName) { this.identityName = identityName; }
    public void setTraits(EnumSet<PersonalityTrait> traits) { this.traits = traits.clone(); }
    public void setJobType(JobType jobType) { this.jobType = jobType; }
    public void setHomeLocationId(String id) { this.homeLocationId = normalizeOptional(id); }
    public void setWorkplaceLocationId(String id) { this.workplaceLocationId = normalizeOptional(id); }
    public void setFavoriteLocationId(String id) { this.favoriteLocationId = normalizeOptional(id); }
    public void setSchedule(int wakeTime, int sleepTime, int workStart, int workEnd) {
        this.wakeTime = Math.floorMod(wakeTime, 24000);
        this.sleepTime = Math.floorMod(sleepTime, 24000);
        this.workStart = Math.floorMod(workStart, 24000);
        this.workEnd = Math.floorMod(workEnd, 24000);
    }
    public void setDailyFocus(DailyFocus dailyFocus) { this.dailyFocus = dailyFocus; }
    public void setLastPlannedDay(long lastPlannedDay) { this.lastPlannedDay = lastPlannedDay; }
    public void setLastDecisionTick(long lastDecisionTick) { this.lastDecisionTick = lastDecisionTick; }
    public void setReason(String reason) { this.reason = reason; }

    public void beginTravel(Activity activity, String locationId, String reason, long gameTime) {
        this.activity = activity;
        this.targetLocationId = normalizeOptional(locationId);
        this.performing = false;
        this.activityEndsAt = 0L;
        this.lastDecisionTick = gameTime;
        this.reason = reason;
    }

    public void beginPerforming(long endsAt, String reason) {
        this.performing = true;
        this.activityEndsAt = endsAt;
        this.reason = reason;
    }

    public void clearActivity(String reason, long gameTime) {
        this.activity = Activity.IDLE;
        this.targetLocationId = "";
        this.performing = false;
        this.activityEndsAt = 0L;
        this.lastDecisionTick = gameTime;
        this.reason = reason;
    }

    public boolean isWorkHours(long dayTime) {
        return inWindow(dayTime, workStart, workEnd);
    }

    public boolean isNighttime(long dayTime) {
        int time = (int) Math.floorMod(dayTime, 24000L);
        if (sleepTime >= wakeTime) {
            return time >= sleepTime || time < wakeTime;
        }
        return time >= sleepTime && time < wakeTime;
    }

    private static boolean inWindow(long dayTime, int start, int end) {
        int time = (int) Math.floorMod(dayTime, 24000L);
        if (start == end) return true;
        if (start < end) return time >= start && time < end;
        return time >= start || time < end;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("EntityUuid", entityUuid);
        tag.putString("TownId", townId);
        tag.putString("IdentityName", identityName);
        for (NeedType need : NeedType.values()) {
            tag.putFloat("Need_" + need.name(), needs.get(need));
        }

        ListTag traitList = new ListTag();
        for (PersonalityTrait trait : traits) {
            traitList.add(StringTag.valueOf(trait.name()));
        }
        tag.put("Traits", traitList);
        tag.putString("JobType", jobType.name());
        tag.putString("Home", homeLocationId);
        tag.putString("Workplace", workplaceLocationId);
        tag.putString("Favorite", favoriteLocationId);
        tag.putInt("WakeTime", wakeTime);
        tag.putInt("SleepTime", sleepTime);
        tag.putInt("WorkStart", workStart);
        tag.putInt("WorkEnd", workEnd);
        tag.putString("DailyFocus", dailyFocus.name());
        tag.putLong("LastPlannedDay", lastPlannedDay);
        tag.putString("Activity", activity.name());
        tag.putString("TargetLocation", targetLocationId);
        tag.putBoolean("Performing", performing);
        tag.putLong("ActivityEndsAt", activityEndsAt);
        tag.putLong("LastDecisionTick", lastDecisionTick);
        tag.putString("Reason", reason);
        return tag;
    }

    public static Resident load(CompoundTag tag) {
        Resident resident = new Resident(
                tag.getUUID("EntityUuid"),
                tag.getString("TownId"),
                tag.getString("IdentityName"));
        for (NeedType need : NeedType.values()) {
            String key = "Need_" + need.name();
            if (tag.contains(key, Tag.TAG_FLOAT)) resident.needs.set(need, tag.getFloat(key));
        }

        EnumSet<PersonalityTrait> traits = EnumSet.noneOf(PersonalityTrait.class);
        ListTag traitList = tag.getList("Traits", Tag.TAG_STRING);
        for (int i = 0; i < traitList.size(); i++) {
            try {
                traits.add(PersonalityTrait.valueOf(traitList.getString(i)));
            } catch (IllegalArgumentException ignored) {}
        }
        resident.traits = traits;
        try { resident.jobType = JobType.valueOf(tag.getString("JobType")); }
        catch (IllegalArgumentException ignored) {}
        resident.homeLocationId = normalizeOptional(tag.getString("Home"));
        resident.workplaceLocationId = normalizeOptional(tag.getString("Workplace"));
        resident.favoriteLocationId = normalizeOptional(tag.getString("Favorite"));
        resident.wakeTime = Math.floorMod(tag.getInt("WakeTime"), 24000);
        resident.sleepTime = Math.floorMod(tag.getInt("SleepTime"), 24000);
        resident.workStart = Math.floorMod(tag.getInt("WorkStart"), 24000);
        resident.workEnd = Math.floorMod(tag.getInt("WorkEnd"), 24000);
        try { resident.dailyFocus = DailyFocus.valueOf(tag.getString("DailyFocus")); }
        catch (IllegalArgumentException ignored) {}
        resident.lastPlannedDay = tag.getLong("LastPlannedDay");
        try { resident.activity = Activity.valueOf(tag.getString("Activity")); }
        catch (IllegalArgumentException ignored) {}
        resident.targetLocationId = normalizeOptional(tag.getString("TargetLocation"));
        resident.performing = tag.getBoolean("Performing");
        resident.activityEndsAt = tag.getLong("ActivityEndsAt");
        resident.lastDecisionTick = tag.getLong("LastDecisionTick");
        if (tag.contains("Reason", Tag.TAG_STRING)) resident.reason = tag.getString("Reason");
        return resident;
    }

    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) return "";
        return value.toLowerCase(Locale.ROOT);
    }
}
