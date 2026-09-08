package com.season2.townlife.runtime;

import com.season2.townlife.config.TownLifeConfig;
import com.season2.townlife.data.Resident;
import com.season2.townlife.data.Town;
import com.season2.townlife.data.TownLifeSavedData;
import com.season2.townlife.data.TownLocation;
import com.season2.townlife.item.TownWandItem;
import com.season2.townlife.logic.JobType;
import com.season2.townlife.logic.LocationType;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Simple click-NPC-then-click-block setup used by Town Life Lite. */
public final class TownLifeLiteService {
    private static final int AUTO_TOWN_RADIUS = 128;

    private TownLifeLiteService() {}

    public static boolean selectNpc(ServerLevel level, ServerPlayer player, ItemStack wand, Mob mob) {
        if (!EasyNpcCompat.isEasyNpc(mob)) {
            player.displayClientMessage(Component.literal("That is not an Easy NPC.").withStyle(ChatFormatting.RED), true);
            return false;
        }

        Optional<String> protectedReason = Season2NpcProtection.protectedReason(level, mob);
        if (protectedReason.isPresent()) {
            player.displayClientMessage(Component.literal(protectedReason.get()).withStyle(ChatFormatting.RED), false);
            return false;
        }

        TownLifeSavedData data = TownLifeSavedData.get(level);
        Resident resident = data.resident(mob.getUUID()).orElse(null);
        if (resident == null) {
            Town town = findOrCreateTown(data, mob.blockPosition());
            if (data.residentsInTown(town.id()) >= TownLifeConfig.MAX_RESIDENTS_PER_TOWN.get()) {
                player.displayClientMessage(Component.literal("This town cluster has reached its Town Life resident limit.")
                        .withStyle(ChatFormatting.RED), false);
                return false;
            }
            resident = ResidentFactory.create(mob, town);
            // Lite setup is explicit: selecting a new resident never silently inherits an old area assignment.
            resident.setHomeLocationId("");
            resident.setWorkplaceLocationId("");
            resident.setFavoriteLocationId("");
            resident.setJobType(JobType.UNEMPLOYED);
            data.addResident(resident);
        }

        // 0.4 migration: old marker/area assignments are not valid Lite anchors.
        boolean migrated = false;
        if (!resident.homeLocationId().isBlank() && !resident.homeLocationId().startsWith("home_")) {
            resident.setHomeLocationId("");
            migrated = true;
        }
        if (!resident.workplaceLocationId().isBlank() && !resident.workplaceLocationId().startsWith("work_")) {
            resident.setWorkplaceLocationId("");
            resident.setJobType(JobType.UNEMPLOYED);
            migrated = true;
        }
        if (migrated) {
            resident.setFavoriteLocationId("");
            resident.clearActivity("Migrated to Town Life Lite", level.getGameTime());
            TownLifeManager.forgetRuntime(resident.entityUuid());
            data.setDirty();
        }

        boolean presetApplied = EasyNpcCompat.applyTownResidentPreset(mob);
        TownWandItem.selectNpc(wand, mob.getUUID(), resident.identityName());
        player.displayClientMessage(Component.literal("Selected " + resident.identityName()
                + (presetApplied ? " • Town Resident preset applied" : " • Town Resident preset already active")
                + " • now click a bed or workstation").withStyle(ChatFormatting.GREEN), true);
        return true;
    }

    public static boolean assignClickedBlock(ServerLevel level, ServerPlayer player, ItemStack wand, BlockPos pos) {
        UUID uuid = TownWandItem.selectedNpc(wand);
        if (uuid == null) {
            player.displayClientMessage(Component.literal("Select an Easy NPC first.").withStyle(ChatFormatting.YELLOW), true);
            return false;
        }

        TownLifeSavedData data = TownLifeSavedData.get(level);
        Resident resident = data.resident(uuid).orElse(null);
        Entity entity = level.getEntity(uuid);
        if (resident == null || !(entity instanceof Mob mob) || !EasyNpcCompat.isEasyNpc(mob)) {
            TownWandItem.clearSelectedNpc(wand);
            player.displayClientMessage(Component.literal("That selected NPC is not loaded here anymore.")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }

        Town town = data.town(resident.townId()).orElse(null);
        if (town == null) {
            town = findOrCreateTown(data, mob.blockPosition());
            resident.setTownId(town.id());
            data.setDirty();
        }
        BlockState state = level.getBlockState(pos);

        if (state.getBlock() instanceof BedBlock) {
            return assignHome(data, town, resident, player, pos);
        }

        Optional<JobType> job = WorkstationClassifier.classify(state);
        if (job.isPresent()) {
            return assignWorkplace(data, town, resident, player, pos, job.get());
        }

        player.displayClientMessage(Component.literal("That block is not a recognised Town Life workstation. "
                + "Use a bed, smithing/anvil/grindstone/blast furnace, composter, smoker, barrel/brewing stand, or bell.")
                .withStyle(ChatFormatting.YELLOW), false);
        return false;
    }

    public static void clearSelection(ServerPlayer player, ItemStack wand) {
        if (TownWandItem.hasSelectedNpc(wand)) {
            String name = TownWandItem.selectedName(wand);
            TownWandItem.clearSelectedNpc(wand);
            player.displayClientMessage(Component.literal("Cleared Town Wand selection for " + name + ".")
                    .withStyle(ChatFormatting.GRAY), true);
        } else {
            player.displayClientMessage(Component.literal("Town Wand has no selected NPC.")
                    .withStyle(ChatFormatting.GRAY), true);
        }
    }

    private static boolean assignHome(TownLifeSavedData data, Town town, Resident resident, ServerPlayer player, BlockPos pos) {
        String id = anchorId("home", pos);
        boolean occupied = data.residents().stream()
                .anyMatch(other -> !other.entityUuid().equals(resident.entityUuid())
                        && other.townId().equals(town.id()) && other.homeLocationId().equals(id));
        if (occupied) {
            player.displayClientMessage(Component.literal("That bed is already assigned to another resident.")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }

        TownLocation home = town.location(id).orElseGet(() -> {
            TownLocation created = new TownLocation(id, LocationType.HOME, pos);
            town.addLocation(created);
            return created;
        });
        home.setType(LocationType.HOME);
        home.setAnchor(pos);
        home.setBounds(pos, pos);
        home.setCapacity(1);
        home.setHours(0, 0);
        resident.setHomeLocationId(id);
        resident.clearActivity("Home reassigned", player.serverLevel().getGameTime());
        TownLifeManager.forgetRuntime(resident.entityUuid());
        data.setDirty();
        player.displayClientMessage(Component.literal(resident.identityName() + " now lives at this bed.")
                .withStyle(ChatFormatting.GREEN), false);
        return true;
    }

    private static boolean assignWorkplace(TownLifeSavedData data, Town town, Resident resident, ServerPlayer player,
                                           BlockPos pos, JobType job) {
        String id = anchorId("work", pos);
        TownLocation workplace = town.location(id).orElseGet(() -> {
            TownLocation created = new TownLocation(id, LocationType.WORKPLACE, pos);
            town.addLocation(created);
            return created;
        });
        workplace.setType(LocationType.WORKPLACE);
        workplace.setAnchor(pos);
        workplace.setBounds(pos, pos);
        workplace.setCapacity(4);
        workplace.setJobType(job);
        setDefaultHours(workplace, job);

        resident.setJobType(job);
        resident.setWorkplaceLocationId(id);
        resident.clearActivity("Workplace reassigned", player.serverLevel().getGameTime());
        TownLifeManager.forgetRuntime(resident.entityUuid());
        data.setDirty();
        player.displayClientMessage(Component.literal(resident.identityName() + " is now a "
                + WorkstationClassifier.display(job) + " at this workstation.")
                .withStyle(ChatFormatting.GREEN), false);
        return true;
    }

    private static void setDefaultHours(TownLocation location, JobType job) {
        switch (job) {
            case MARKET_VENDOR -> location.setHours(1000, 11000);
            case TAVERN_WORKER -> location.setHours(5000, 16000);
            case WATCHPERSON -> location.setHours(0, 0);
            case GARDENER, SMITH_APPRENTICE -> location.setHours(1500, 10000);
            case UNEMPLOYED -> location.setHours(0, 0);
        }
    }

    private static Town findOrCreateTown(TownLifeSavedData data, BlockPos pos) {
        Optional<Town> containing = data.towns().stream().filter(town -> town.isInside(pos)).findFirst();
        if (containing.isPresent()) return containing.get();
        Optional<Town> near = data.towns().stream()
                .min(Comparator.comparingDouble(town -> town.center().distSqr(pos)))
                .filter(town -> town.center().distSqr(pos) <= (AUTO_TOWN_RADIUS * 1.5D) * (AUTO_TOWN_RADIUS * 1.5D));
        if (near.isPresent()) return near.get();

        int index = 1;
        while (data.town("town_" + index).isPresent()) index++;
        Town town = new Town("town_" + index, pos, AUTO_TOWN_RADIUS);
        town.setDisplayName("Automatic Town " + index);
        data.addTown(town);
        return town;
    }

    private static String anchorId(String prefix, BlockPos pos) {
        return (prefix + "_" + pos.getX() + "_" + pos.getY() + "_" + pos.getZ()).toLowerCase(Locale.ROOT);
    }
}
