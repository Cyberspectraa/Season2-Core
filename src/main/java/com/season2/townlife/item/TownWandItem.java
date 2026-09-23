package com.season2.townlife.item;

import com.season2.townlife.network.TownWandNetwork;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public final class TownWandItem extends Item {
    public static final String TAG_SELECTED_NPC = "TownLifeSelectedNpc";
    public static final String TAG_SELECTED_NAME = "TownLifeSelectedName";
    private static final String TAG_ACTION = "TownLifeWandAction";

    public TownWandItem(Properties properties) { super(properties); }

    @Override
    public boolean isFoil(ItemStack stack) {
        return hasSelectedNpc(stack) || super.isFoil(stack);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            if (!serverPlayer.hasPermissions(2)) {
                serverPlayer.displayClientMessage(Component.literal("Town Wand requires operator permission.")
                        .withStyle(ChatFormatting.RED), true);
                return InteractionResultHolder.fail(stack);
            }
            TownWandNetwork.openConfig(serverPlayer, hand);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Right-click Easy NPC: select + apply Town Resident preset").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Right-click air: open the configuration menu").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Choose an action, then right-click the target block").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Sneaking never clears the selected resident").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Clear selection and position separately in the menu").withStyle(ChatFormatting.DARK_GRAY));
        if (hasSelectedNpc(stack)) {
            tooltip.add(Component.literal("Selected: " + selectedName(stack)).withStyle(ChatFormatting.GREEN));
            tooltip.add(Component.literal("Action: " + action(stack).label()).withStyle(ChatFormatting.AQUA));
        }
    }

    public static void selectNpc(ItemStack stack, UUID uuid, String name) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putUUID(TAG_SELECTED_NPC, uuid);
        tag.putString(TAG_SELECTED_NAME, name);
        setAction(stack, TownWandAction.NONE);
    }

    public static boolean hasSelectedNpc(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.hasUUID(TAG_SELECTED_NPC);
    }

    public static @Nullable UUID selectedNpc(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.hasUUID(TAG_SELECTED_NPC) ? tag.getUUID(TAG_SELECTED_NPC) : null;
    }

    public static String selectedName(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return "None";
        String name = tag.getString(TAG_SELECTED_NAME);
        return name.isBlank() ? "Resident" : name;
    }

    public static TownWandAction action(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? TownWandAction.NONE : TownWandAction.safeName(tag.getString(TAG_ACTION));
    }

    public static void setAction(ItemStack stack, TownWandAction action) {
        stack.getOrCreateTag().putString(TAG_ACTION, action.name());
    }

    public static void clearSelectedNpc(ItemStack stack) {
        setAction(stack, TownWandAction.NONE);
        CompoundTag tag = stack.getTag();
        if (tag == null) return;
        tag.remove(TAG_SELECTED_NPC);
        tag.remove(TAG_SELECTED_NAME);
    }
}
