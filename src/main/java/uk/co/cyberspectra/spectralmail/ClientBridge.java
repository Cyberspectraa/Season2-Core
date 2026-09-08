package uk.co.cyberspectra.spectralmail;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/** Common-side bridge so mail items never directly reference client-only Minecraft classes. */
public final class ClientBridge {
    private static Consumer<ItemStack> opener = stack -> {};
    private static ComposerOpener composerOpener = (recipients, maxLength, hand) -> {};

    private ClientBridge() {}

    public static void setOpener(Consumer<ItemStack> newOpener) {
        opener = newOpener == null ? stack -> {} : newOpener;
    }

    public static void openLetter(ItemStack stack) {
        if (stack != null) opener.accept(stack.copy());
    }

    public static void setComposerOpener(ComposerOpener newOpener) {
        composerOpener = newOpener == null ? (recipients, maxLength, hand) -> {} : newOpener;
    }

    public static void openComposer(List<ComposeRecipient> recipients, int maxLength, InteractionHand hand) {
        List<ComposeRecipient> safe = recipients == null ? List.of() : List.copyOf(recipients);
        composerOpener.open(safe, Math.max(32, maxLength), hand == null ? InteractionHand.MAIN_HAND : hand);
    }

    @FunctionalInterface
    public interface ComposerOpener {
        void open(List<ComposeRecipient> recipients, int maxLength, InteractionHand hand);
    }
}
