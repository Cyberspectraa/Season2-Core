package uk.co.cyberspectra.spectralmail;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Spectral Mail shared entry point. The same JAR is installed on server and clients,
 * while all mailbox, courier, Discord and postal routing authority remains server-side.
 */
@Mod(SpectralMail.MODID)
public final class SpectralMail {
    public static final String MODID = "spectralmail";
    public static final String VERSION = "1.1.1-alpha.4";

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MODID);

    public static final RegistryObject<Block> DROP_BOX = BLOCKS.register(
            "drop_box", () -> new PostalBlock(PostalBlock.Kind.DROP_BOX));
    public static final RegistryObject<Block> LETTER_BOX = BLOCKS.register(
            "letter_box", () -> new PostalBlock(PostalBlock.Kind.LETTER_BOX));

    public static final RegistryObject<Item> DROP_BOX_ITEM = ITEMS.register(
            "drop_box", () -> new BlockItem(DROP_BOX.get(), new Item.Properties().m_41487_(16)));
    public static final RegistryObject<Item> LETTER_BOX_ITEM = ITEMS.register(
            "letter_box", () -> new BlockItem(LETTER_BOX.get(), new Item.Properties().m_41487_(16)));

    public static final RegistryObject<Item> LETTER_PAPER = ITEMS.register(
            "letter_paper", () -> new Item(new Item.Properties().m_41487_(16)));
    public static final RegistryObject<Item> ADDRESSED_LETTER = ITEMS.register(
            "addressed_letter", () -> new AddressedLetterItem(new Item.Properties().m_41487_(1)));

    public static final RegistryObject<Item> SEALED_LETTER = ITEMS.register(
            "sealed_letter", () -> new LetterItem(true, new Item.Properties().m_41487_(1)));
    public static final RegistryObject<Item> OPENED_LETTER = ITEMS.register(
            "opened_letter", () -> new LetterItem(false, new Item.Properties().m_41487_(1)));

    public SpectralMail() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
    }
}
