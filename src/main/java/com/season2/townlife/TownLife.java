package com.season2.townlife;

import com.mojang.logging.LogUtils;
import com.season2.townlife.config.TownLifeConfig;
import com.season2.townlife.registry.ModItems;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(TownLife.MOD_ID)
public final class TownLife {
    public static final String MOD_ID = "townlife";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TownLife() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.ITEMS.register(modBus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, TownLifeConfig.SPEC);
        LOGGER.info("Town Life Lite initialized.");
    }
}
