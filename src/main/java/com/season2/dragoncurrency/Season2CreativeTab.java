package com.season2.dragoncurrency;

import com.season2.townlife.registry.ModItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import uk.co.cyberspectra.spectralmail.SpectralMail;

/**
 * One shared creative inventory tab for the player-facing content shipped in
 * the Season2 Core JAR. Registry IDs remain owned by their original modules.
 */
public final class Season2CreativeTab {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, DragonCurrency.MODID);

    public static final RegistryObject<CreativeModeTab> SEASON2_CORE = TABS.register(
            "season2_core",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.season2_core"))
                    .icon(() -> new ItemStack(DragonCurrency.DRAGON_COIN.get()))
                    .displayItems((parameters, output) -> {
                        // Dragon Currency - current player-facing currency only.
                        output.accept(DragonCurrency.COPPER_COIN.get());
                        output.accept(DragonCurrency.SILVER_COIN.get());
                        output.accept(DragonCurrency.GOLD_COIN.get());
                        output.accept(DragonCurrency.PLATINUM_COIN.get());
                        output.accept(DragonCurrency.DRAGON_COIN.get());

                        // Spectral Mail.
                        output.accept(SpectralMail.LETTER_PAPER.get());
                        output.accept(SpectralMail.ADDRESSED_LETTER.get());
                        output.accept(SpectralMail.SEALED_LETTER.get());
                        output.accept(SpectralMail.OPENED_LETTER.get());
                        output.accept(SpectralMail.DROP_BOX_ITEM.get());
                        output.accept(SpectralMail.LETTER_BOX_ITEM.get());

                        // Town Life administration and setup tools.
                        output.accept(ModItems.TOWN_WAND.get());
                        output.accept(ModItems.DEV_CLOCK.get());
                    })
                    .build()
    );

    private Season2CreativeTab() {}
}
