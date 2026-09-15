package com.season2.townlife.item;

import com.season2.townlife.data.TownPathType;
import com.season2.townlife.network.TownPathNetwork;
import com.season2.townlife.runtime.TownPathService;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** Operator tool used to configure, register, remove and inspect preferred Town Path blocks. */
public final class PathWandItem extends Item {
    public PathWandItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;

        if (!serverPlayer.hasPermissions(2)) {
            serverPlayer.displayClientMessage(Component.literal("Path Wand requires operator permission.")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }

        ItemStack stack = context.getItemInHand();
        PathEditMode mode = PathWandSettings.mode(stack);
        TownPathType type = PathWandSettings.type(stack);
        switch (mode) {
            case ADD_CONNECTED -> TownPathService.registerConnected(
                    serverPlayer.serverLevel(), serverPlayer, context.getClickedPos(), type);
            case ADD_SINGLE -> TownPathService.registerSingle(
                    serverPlayer.serverLevel(), serverPlayer, context.getClickedPos(), type);
            case REMOVE_SINGLE -> TownPathService.removeSingle(
                    serverPlayer.serverLevel(), serverPlayer, context.getClickedPos());
            case REMOVE_CONNECTED -> TownPathService.removeConnected(
                    serverPlayer.serverLevel(), serverPlayer, context.getClickedPos());
            case INSPECT -> TownPathService.inspectBlock(
                    serverPlayer.serverLevel(), serverPlayer, context.getClickedPos());
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (!serverPlayer.hasPermissions(2)) {
                serverPlayer.displayClientMessage(Component.literal("Path Wand requires operator permission.")
                        .withStyle(ChatFormatting.RED), true);
                return InteractionResultHolder.fail(stack);
            }
            TownPathNetwork.openConfig(serverPlayer, hand);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        PathEditMode mode = PathWandSettings.mode(stack);
        TownPathType type = PathWandSettings.type(stack);
        tooltip.add(Component.literal("Mode: " + mode.displayName()).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("Path type: " + type.displayName()).withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.literal("Right-click air: configure tool / highlight nearby")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Right-click block: perform selected edit mode")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Town Life chooses road waypoints; EasyNPC/Minecraft still handles walking")
                .withStyle(ChatFormatting.DARK_AQUA));
    }
}
