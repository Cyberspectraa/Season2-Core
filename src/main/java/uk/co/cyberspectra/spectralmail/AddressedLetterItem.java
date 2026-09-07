package uk.co.cyberspectra.spectralmail;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** Physical outgoing letter. It is harmless until a server-side Drop Box validates and accepts it. */
public final class AddressedLetterItem extends Item {
    public AddressedLetterItem(Properties properties) {
        super(properties);
    }

    @Override
    public void m_7373_(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        super.m_7373_(stack, level, tooltip, flag);
        String recipient = DraftLetterData.recipientName(stack);
        tooltip.add(Component.m_237113_("To: " + (recipient == null || recipient.isBlank() ? "Unknown" : recipient)));
        tooltip.add(Component.m_237113_("Ready to post in a Drop Box"));
    }
}
