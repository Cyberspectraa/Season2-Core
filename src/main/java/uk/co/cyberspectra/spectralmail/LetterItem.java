package uk.co.cyberspectra.spectralmail;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** Physical letter item. Server validates state; client bridge only renders it. */
public final class LetterItem extends Item {
    private static final DateTimeFormatter TOOLTIP_DATE =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm").withZone(ZoneId.systemDefault());

    private final boolean sealed;

    public LetterItem(boolean sealed, Properties properties) {
        super(properties);
        this.sealed = sealed;
    }

    @Override
    public InteractionResultHolder<ItemStack> m_7203_(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.m_21120_(hand);

        if (level.m_5776_()) {
            UUID expectedRecipient = MailItemData.recipientUuid(held);
            if (expectedRecipient != null && expectedRecipient.equals(player.m_20148_())) {
                ClientBridge.openLetter(held);
                return InteractionResultHolder.m_19092_(held, true);
            }
            return InteractionResultHolder.m_19098_(held);
        }

        if (!sealed) return InteractionResultHolder.m_19092_(held, false);

        String id = MailItemData.id(held);
        UUID expectedRecipient = MailItemData.recipientUuid(held);
        MinecraftServer server = player.m_20194_();
        if (server == null || id.isBlank() || expectedRecipient == null || !expectedRecipient.equals(player.m_20148_())) {
            return InteractionResultHolder.m_19098_(held);
        }

        MailSavedData data = MailSavedData.get(server);
        MailRecord record = data.getRecord(id);
        if (record == null || !record.recipientUuid.equals(player.m_20148_())) {
            return InteractionResultHolder.m_19098_(held);
        }

        ItemStack opened = MailItemData.openedFrom(held);
        player.m_21008_(hand, opened);
        data.setState(record, MailRecord.READ);
        return InteractionResultHolder.m_19092_(opened, false);
    }

    @Override
    public void m_7373_(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        super.m_7373_(stack, level, tooltip, flag);
        String sender = MailItemData.sender(stack);
        long time = MailItemData.sentAt(stack);
        tooltip.add(Component.m_237113_("From: " + (sender == null || sender.isBlank() ? "Unknown" : sender)));
        if (time > 0L) tooltip.add(Component.m_237113_("Sent: " + TOOLTIP_DATE.format(Instant.ofEpochMilli(time))));
        tooltip.add(Component.m_237113_(sealed ? "Right-click to break the seal" : "Right-click to read again"));
    }
}
