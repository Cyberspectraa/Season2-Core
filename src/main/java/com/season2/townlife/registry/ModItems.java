package com.season2.townlife.registry;

import com.season2.townlife.TownLife;
import com.season2.townlife.item.DevClockItem;
import com.season2.townlife.item.PathWandItem;
import com.season2.townlife.item.TownWandItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, TownLife.MOD_ID);

    public static final RegistryObject<Item> TOWN_WAND = ITEMS.register(
            "town_wand",
            () -> new TownWandItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> PATH_WAND = ITEMS.register(
            "path_wand",
            () -> new PathWandItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> DEV_CLOCK = ITEMS.register(
            "dev_clock",
            () -> new DevClockItem(new Item.Properties().stacksTo(1)));

    private ModItems() {}
}
