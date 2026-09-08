package com.season2.dragoncurrency;

import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(DragonCurrency.MODID)
public final class DragonCurrency {
    public static final String MODID = "dragoncurrency";

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, MODID);

    public static final RegistryObject<MenuType<BankMenu>> BANK_MENU = MENUS.register(
            "bank",
            () -> IForgeMenuType.create(BankMenu::new)
    );

    public static final RegistryObject<Item> COPPER_COIN = item("copper_coin");
    public static final RegistryObject<Item> SILVER_COIN = item("silver_coin");
    public static final RegistryObject<Item> GOLD_COIN = item("gold_coin");
    public static final RegistryObject<Item> PLATINUM_COIN = item("platinum_coin");
    public static final RegistryObject<Item> DRAGON_COIN = item("dragon_coin");

    public static final RegistryObject<Item> COIN_POUCH = ITEMS.register(
            "coin_pouch",
            () -> new CoinPouchItem(new Item.Properties().stacksTo(1))
    );

    // Legacy GUI-only items retained for registry/world compatibility. They are
    // intentionally not exposed in the Season 2 Core creative tab.
    public static final RegistryObject<Item> PREVIOUS_BUTTON = item("pouch_previous_button");
    public static final RegistryObject<Item> NEXT_BUTTON = item("pouch_next_button");
    public static final RegistryObject<Item> WITHDRAW_ONE_BUTTON = item("pouch_withdraw_one_button");
    public static final RegistryObject<Item> WITHDRAW_STACK_BUTTON = item("pouch_withdraw_stack_button");

    public static final RegistryObject<Item> COPPER_DISPLAY = item("pouch_copper_display");
    public static final RegistryObject<Item> SILVER_DISPLAY = item("pouch_silver_display");
    public static final RegistryObject<Item> GOLD_DISPLAY = item("pouch_gold_display");
    public static final RegistryObject<Item> PLATINUM_DISPLAY = item("pouch_platinum_display");
    public static final RegistryObject<Item> DRAGON_DISPLAY = item("pouch_dragon_display");

    private static RegistryObject<Item> item(String id) {
        return ITEMS.register(id, () -> new Item(new Item.Properties()));
    }

    public DragonCurrency() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(modEventBus);
        MENUS.register(modEventBus);
        Season2CreativeTab.TABS.register(modEventBus);
    }
}
