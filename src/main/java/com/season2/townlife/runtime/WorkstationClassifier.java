package com.season2.townlife.runtime;

import com.season2.townlife.logic.JobType;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Deliberately small, obvious workstation mapping. The registry-name fallback
 * also makes common modded blocks usable without a compile-time dependency.
 */
public final class WorkstationClassifier {
    private WorkstationClassifier() {}

    public static Optional<JobType> classify(BlockState state) {
        if (state.is(Blocks.SMITHING_TABLE) || state.is(Blocks.ANVIL)
                || state.is(Blocks.CHIPPED_ANVIL) || state.is(Blocks.DAMAGED_ANVIL)
                || state.is(Blocks.GRINDSTONE) || state.is(Blocks.BLAST_FURNACE)) {
            return Optional.of(JobType.SMITH_APPRENTICE);
        }
        if (state.is(Blocks.COMPOSTER)) return Optional.of(JobType.GARDENER);
        if (state.is(Blocks.SMOKER)) return Optional.of(JobType.MARKET_VENDOR);
        if (state.is(Blocks.BARREL) || state.is(Blocks.BREWING_STAND)) return Optional.of(JobType.TAVERN_WORKER);
        if (state.is(Blocks.BELL)) return Optional.of(JobType.WATCHPERSON);

        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (key == null) return Optional.empty();
        String path = key.getPath().toLowerCase(Locale.ROOT);
        if (containsAny(path, "smith", "anvil", "forge", "grindstone", "tool_station", "weapon_station")) {
            return Optional.of(JobType.SMITH_APPRENTICE);
        }
        if (containsAny(path, "composter", "garden", "farm_station", "farmer_station")) {
            return Optional.of(JobType.GARDENER);
        }
        if (containsAny(path, "smoker", "oven", "bakery", "food_stall", "market_stall")) {
            return Optional.of(JobType.MARKET_VENDOR);
        }
        if (containsAny(path, "tavern", "bar_counter", "brewing", "taproom")) {
            return Optional.of(JobType.TAVERN_WORKER);
        }
        if (containsAny(path, "guard_post", "watch_post")) {
            return Optional.of(JobType.WATCHPERSON);
        }
        return Optional.empty();
    }

    public static String display(JobType job) {
        return switch (job) {
            case SMITH_APPRENTICE -> "Smith";
            case GARDENER -> "Gardener";
            case MARKET_VENDOR -> "Food Vendor";
            case TAVERN_WORKER -> "Tavern Worker";
            case WATCHPERSON -> "Watchperson";
            case UNEMPLOYED -> "Unemployed";
        };
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) if (value.contains(needle)) return true;
        return false;
    }
}
