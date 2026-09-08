package com.season2.townlife.event;

import com.season2.townlife.TownLife;
import com.season2.townlife.command.TownLifeCommands;
import com.season2.townlife.data.TownLifeSavedData;
import com.season2.townlife.item.DevClockItem;
import com.season2.townlife.registry.ModItems;
import com.season2.townlife.runtime.TownLifeLiteService;
import com.season2.townlife.runtime.TownLifeManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TownLife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TownLifeEvents {
    private TownLifeEvents() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        TownLifeCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.level.isClientSide) return;
        if (event.level instanceof ServerLevel level) TownLifeManager.tick(level);
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        if (!(event.getTarget() instanceof Mob mob)) return;

        ItemStack held = player.getMainHandItem();
        if (held.is(ModItems.TOWN_WAND.get())) {
            if (!player.hasPermissions(2)) return;
            TownLifeLiteService.selectNpc(level, player, held, mob);
            consume(event);
            return;
        }

        if (held.is(ModItems.DEV_CLOCK.get())) {
            if (!player.hasPermissions(2)) return;
            DevClockItem.bindToNpc(level, player, held, mob);
            consume(event);
            return;
        }

        if (TownLifeSavedData.get(level).resident(mob.getUUID()).isEmpty()) return;
        TownLifeManager.pauseForInteraction(level, mob.getUUID(), mob, player);
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        ItemStack held = player.getMainHandItem();
        if (!held.is(ModItems.TOWN_WAND.get()) || !player.hasPermissions(2)) return;

        if (TownLifeLiteService.assignClickedBlock(level, player, held, event.getPos())) {
            consume(event);
        }
    }

    private static void consume(PlayerInteractEvent event) {
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }
}
