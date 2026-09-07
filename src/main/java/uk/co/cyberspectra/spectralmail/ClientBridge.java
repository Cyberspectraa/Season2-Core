package uk.co.cyberspectra.spectralmail;

import java.util.function.Consumer;
import net.minecraft.world.item.ItemStack;

/** Common-side bridge so letter items never directly reference client-only Minecraft classes. */
public final class ClientBridge {
    private static Consumer<ItemStack> opener = stack -> {};

    private ClientBridge() {}

    public static void setOpener(Consumer<ItemStack> newOpener) {
        opener = newOpener == null ? stack -> {} : newOpener;
    }

    public static void openLetter(ItemStack stack) {
        if (stack != null) opener.accept(stack.m_41777_());
    }
}
