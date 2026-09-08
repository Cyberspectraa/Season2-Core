package com.season2.townlife.item;

import com.season2.townlife.data.Resident;
import com.season2.townlife.data.TownLifeSavedData;
import com.season2.townlife.runtime.EasyNpcCompat;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** Operator-only schedule tester with a readable digital Minecraft clock. */
public final class DevClockItem extends Item {
    private static final String TAG_MODE = "TownLifeDevClockMode";
    private static final String TAG_RESIDENT = "TownLifeDevClockResident";
    private static final String TAG_RESIDENT_NAME = "TownLifeDevClockResidentName";

    public DevClockItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.sidedSuccess(stack, true);
        if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.pass(stack);
        }
        if (!serverPlayer.hasPermissions(2)) {
            serverPlayer.displayClientMessage(Component.literal("Dev Clock requires operator permission.")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }

        if (player.isShiftKeyDown()) {
            Mode next = mode(stack).next();
            setMode(stack, next);
            Schedule schedule = schedule(serverLevel, stack);
            serverPlayer.displayClientMessage(Component.literal("Dev Clock preset: " + next.label + " "
                    + formatTime(next.time(schedule))).withStyle(ChatFormatting.AQUA), true);
            return InteractionResultHolder.success(stack);
        }

        Schedule schedule = schedule(serverLevel, stack);
        Mode selected = mode(stack);
        int target = selected.time(schedule);
        long current = serverLevel.getDayTime();
        long dayBase = current - Math.floorMod(current, 24000L);
        serverLevel.setDayTime(dayBase + target);
        serverPlayer.displayClientMessage(Component.literal("Set town time to " + formatTime(target)
                + " • " + selected.label + scheduleSuffix(stack)).withStyle(ChatFormatting.GREEN), false);
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, level, entity, slot, selected);
        if (!selected || level.isClientSide || !(entity instanceof ServerPlayer player)) return;
        if (level.getGameTime() % 10L != 0L) return;
        Schedule schedule = schedule((ServerLevel) level, stack);
        Mode selectedMode = mode(stack);
        String text = formatTime(level.getDayTime()) + "  •  " + selectedMode.label + " "
                + formatTime(selectedMode.time(schedule));
        if (stack.getTag() != null && stack.getTag().hasUUID(TAG_RESIDENT)) {
            String name = stack.getTag().getString(TAG_RESIDENT_NAME);
            if (!name.isBlank()) text += "  •  " + name;
        }
        player.displayClientMessage(Component.literal(text).withStyle(ChatFormatting.AQUA), true);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("DEV ONLY • operator tool").withStyle(ChatFormatting.RED));
        tooltip.add(Component.literal("Hold: shows digital Minecraft time").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Right-click: jump to selected schedule time").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Sneak + right-click: cycle schedule time").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Right-click Town Life NPC: use that NPC's schedule").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Preset: " + mode(stack).label).withStyle(ChatFormatting.AQUA));
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.hasUUID(TAG_RESIDENT)) {
            String name = tag.getString(TAG_RESIDENT_NAME);
            tooltip.add(Component.literal("Schedule: " + (name.isBlank() ? "bound resident" : name))
                    .withStyle(ChatFormatting.GREEN));
        } else {
            tooltip.add(Component.literal("Schedule: default resident times").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    public static boolean bindToNpc(ServerLevel level, ServerPlayer player, ItemStack stack, Mob mob) {
        if (!EasyNpcCompat.isEasyNpc(mob)) {
            player.displayClientMessage(Component.literal("That is not an Easy NPC.").withStyle(ChatFormatting.RED), true);
            return false;
        }
        Resident resident = TownLifeSavedData.get(level).resident(mob.getUUID()).orElse(null);
        if (resident == null) {
            player.displayClientMessage(Component.literal("That NPC is not a Town Life resident yet. Select it with the Town Wand first.")
                    .withStyle(ChatFormatting.YELLOW), false);
            return false;
        }
        CompoundTag tag = stack.getOrCreateTag();
        tag.putUUID(TAG_RESIDENT, resident.entityUuid());
        tag.putString(TAG_RESIDENT_NAME, resident.identityName());
        player.displayClientMessage(Component.literal("Dev Clock now uses " + resident.identityName() + "'s schedule: "
                + "work " + formatTime(resident.workStart()) + ", bed " + formatTime(resident.sleepTime()))
                .withStyle(ChatFormatting.GREEN), false);
        return true;
    }

    public static String formatTime(long dayTime) {
        int ticks = (int) Math.floorMod(dayTime, 24000L);
        int minutesAfterSix = (ticks * 1440) / 24000;
        int totalMinutes = (minutesAfterSix + 360) % 1440;
        int hour = totalMinutes / 60;
        int minute = totalMinutes % 60;
        return String.format("%02d:%02d", hour, minute);
    }

    private static Schedule schedule(ServerLevel level, ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.hasUUID(TAG_RESIDENT)) {
            UUID uuid = tag.getUUID(TAG_RESIDENT);
            Resident resident = TownLifeSavedData.get(level).resident(uuid).orElse(null);
            if (resident != null) {
                return new Schedule(resident.wakeTime(), resident.workStart(), resident.workEnd(), resident.sleepTime());
            }
        }
        return new Schedule(500, 1800, 9800, 13000);
    }

    private static String scheduleSuffix(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.hasUUID(TAG_RESIDENT)) return "";
        String name = tag.getString(TAG_RESIDENT_NAME);
        return name.isBlank() ? "" : " for " + name;
    }

    private static Mode mode(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        int value = tag == null ? 0 : tag.getInt(TAG_MODE);
        Mode[] values = Mode.values();
        return values[Math.floorMod(value, values.length)];
    }

    private static void setMode(ItemStack stack, Mode mode) {
        stack.getOrCreateTag().putInt(TAG_MODE, mode.ordinal());
    }

    private record Schedule(int wake, int workStart, int workEnd, int sleep) {}

    private enum Mode {
        WORK_START("Work starts") {
            @Override int time(Schedule schedule) { return schedule.workStart(); }
        },
        WORK_END("Work ends") {
            @Override int time(Schedule schedule) { return schedule.workEnd(); }
        },
        BEDTIME("Bedtime") {
            @Override int time(Schedule schedule) { return schedule.sleep(); }
        },
        WAKE_UP("Wake up") {
            @Override int time(Schedule schedule) { return schedule.wake(); }
        };

        private final String label;
        Mode(String label) { this.label = label; }
        abstract int time(Schedule schedule);
        private Mode next() { return values()[(ordinal() + 1) % values().length]; }
    }
}
