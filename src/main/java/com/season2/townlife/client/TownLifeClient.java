package com.season2.townlife.client;

import com.season2.townlife.TownLife;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Client-only presentation bootstrap for Town Life tools. */
@Mod.EventBusSubscriber(modid = TownLife.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class TownLifeClient {
    private TownLifeClient() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        TownPathClientBridge.setConfigOpener((mode, type, hand) ->
                Minecraft.getInstance().setScreen(new PathWandConfigScreen(mode, type, hand)));
    }
}
