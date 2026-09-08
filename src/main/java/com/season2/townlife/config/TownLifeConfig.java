package com.season2.townlife.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class TownLifeConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue MAX_RESIDENTS_PER_TOWN;
    public static final ForgeConfigSpec.DoubleValue HUNGER_DECAY_PER_SECOND;
    public static final ForgeConfigSpec.DoubleValue ENERGY_DECAY_PER_SECOND;
    public static final ForgeConfigSpec.DoubleValue SOCIAL_DECAY_PER_SECOND;
    public static final ForgeConfigSpec.DoubleValue FUN_DECAY_PER_SECOND;
    public static final ForgeConfigSpec.IntValue HOME_ROAM_RADIUS;
    public static final ForgeConfigSpec.IntValue WORK_ROAM_RADIUS;
    public static final ForgeConfigSpec.DoubleValue WALK_SPEED;
    public static final ForgeConfigSpec.DoubleValue INDOOR_WALK_SPEED;
    public static final ForgeConfigSpec.DoubleValue EMERGENCY_WALK_SPEED;
    public static final ForgeConfigSpec.IntValue PATH_ENTRY_RADIUS;
    public static final ForgeConfigSpec.IntValue PATH_WAYPOINT_SPACING;
    public static final ForgeConfigSpec.IntValue PATH_BULK_REGISTER_LIMIT;
    public static final ForgeConfigSpec.IntValue PATH_ROUTE_SEARCH_LIMIT;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("simulation");
        MAX_RESIDENTS_PER_TOWN = builder
                .comment("Safety cap for automatically managed residents in one invisible town cluster.")
                .defineInRange("maxResidentsPerTown", 32, 1, 128);
        HUNGER_DECAY_PER_SECOND = builder
                .defineInRange("hungerDecayPerSecond", 0.030D, 0D, 2D);
        ENERGY_DECAY_PER_SECOND = builder
                .defineInRange("energyDecayPerSecond", 0.025D, 0D, 2D);
        SOCIAL_DECAY_PER_SECOND = builder
                .defineInRange("socialDecayPerSecond", 0.012D, 0D, 2D);
        FUN_DECAY_PER_SECOND = builder
                .defineInRange("funDecayPerSecond", 0.010D, 0D, 2D);
        builder.pop();

        builder.push("movement");
        HOME_ROAM_RADIUS = builder
                .comment("How far residents casually wander from their assigned bed while at home.")
                .defineInRange("homeRoamRadius", 7, 2, 16);
        WORK_ROAM_RADIUS = builder
                .comment("How far residents casually wander from their assigned workstation while working.")
                .defineInRange("workRoamRadius", 6, 2, 16);
        WALK_SPEED = builder
                .comment("Normal path-navigation speed multiplier. Kept deliberately close to vanilla villager walking pace.")
                .defineInRange("walkSpeed", 0.60D, 0.25D, 1.20D);
        INDOOR_WALK_SPEED = builder
                .comment("Casual roaming speed near home/work anchors.")
                .defineInRange("indoorWalkSpeed", 0.48D, 0.20D, 1.00D);
        EMERGENCY_WALK_SPEED = builder
                .comment("Faster pace used only when fleeing nearby hostile mobs.")
                .defineInRange("emergencyWalkSpeed", 0.82D, 0.40D, 1.40D);
        PATH_ENTRY_RADIUS = builder
                .comment("Maximum horizontal distance from an NPC/destination to a registered Town Path before road routing is used.")
                .defineInRange("pathEntryRadius", 16, 4, 32);
        PATH_WAYPOINT_SPACING = builder
                .comment("Maximum straight-road spacing between high-level road waypoints. Corners are always kept.")
                .defineInRange("pathWaypointSpacing", 5, 2, 12);
        PATH_BULK_REGISTER_LIMIT = builder
                .comment("Maximum connected matching path blocks the Path Wand may register in one click.")
                .defineInRange("pathBulkRegisterLimit", 4096, 64, 16384);
        PATH_ROUTE_SEARCH_LIMIT = builder
                .comment("Safety cap for registered road nodes considered while planning one Town Path route.")
                .defineInRange("pathRouteSearchLimit", 20000, 512, 100000);
        builder.pop();

        SPEC = builder.build();
    }

    private TownLifeConfig() {}
}
