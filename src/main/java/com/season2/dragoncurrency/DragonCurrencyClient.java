package com.season2.dragoncurrency;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Registers the dedicated one-slot Dragon Bank screen. */
@Mod.EventBusSubscriber(modid = DragonCurrency.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class DragonCurrencyClient {
    private DragonCurrencyClient() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() ->
                MenuScreens.register(DragonCurrency.BANK_MENU.get(), CoinPouchScreen::new)
        );
    }
}
