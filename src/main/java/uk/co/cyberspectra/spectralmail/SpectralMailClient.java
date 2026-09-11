package uk.co.cyberspectra.spectralmail;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Client-only presentation bootstrap. No mailbox, delivery, or Discord logic is loaded here. */
@Mod.EventBusSubscriber(modid = SpectralMail.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class SpectralMailClient {
    private SpectralMailClient() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        ClientBridge.setOpener(stack -> Minecraft.getInstance().setScreen(new LetterScreen(stack)));
        ClientBridge.setComposerOpener((recipients, maxLength, hand) ->
                Minecraft.getInstance().setScreen(new LetterComposeScreen(recipients, maxLength, hand)));
    }
}
