package com.season2.townlife.event;

import com.season2.townlife.TownLife;
import com.season2.townlife.registry.ModItems;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TownLife.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class TownLifeModEvents {
    private TownLifeModEvents() {}

    @SubscribeEvent
    public static void onBuildCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.OP_BLOCKS || event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModItems.TOWN_WAND.get());
            event.accept(ModItems.DEV_CLOCK.get());
        }
    }
}
