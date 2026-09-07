package uk.co.cyberspectra.spectralmail;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/** NBT format for the physical letter presentation object. */
public final class MailItemData {
    private static final String ID = "SpectralMailId";
    private static final String SENDER = "SpectralMailSender";
    private static final String RECIPIENT = "SpectralMailRecipient";
    private static final String RECIPIENT_UUID = "SpectralMailRecipientUUID";
    private static final String SENT_AT = "SpectralMailSentAt";
    private static final String MESSAGE = "SpectralMailMessage";

    private MailItemData() {}

    public static ItemStack sealed(MailRecord record) {
        ItemStack stack = new ItemStack(SpectralMail.SEALED_LETTER.get());
        write(stack, record);
        return stack;
    }


    /** Visual-only envelope placed in the courier hand. It intentionally has no mail id or recipient UUID. */
    public static ItemStack courierVisual(MailRecord record) {
        ItemStack stack = new ItemStack(SpectralMail.SEALED_LETTER.get());
        CompoundTag tag = stack.m_41784_();
        tag.m_128359_(SENDER, record == null ? "Post Office" : record.senderName);
        if (record != null) tag.m_128356_(SENT_AT, record.sentAt);
        tag.m_128359_("SpectralMailCourierVisual", "1");
        return stack;
    }

    public static ItemStack openedFrom(ItemStack original) {
        ItemStack stack = new ItemStack(SpectralMail.OPENED_LETTER.get());
        CompoundTag originalTag = original == null ? null : original.m_41783_();
        if (originalTag != null) stack.m_41751_(originalTag);
        return stack;
    }

    public static void write(ItemStack stack, MailRecord record) {
        CompoundTag tag = stack.m_41784_();
        tag.m_128359_(ID, record.id);
        tag.m_128359_(SENDER, record.senderName);
        tag.m_128359_(RECIPIENT, record.recipientName);
        tag.m_128362_(RECIPIENT_UUID, record.recipientUuid);
        tag.m_128356_(SENT_AT, record.sentAt);
        tag.m_128359_(MESSAGE, record.message);
    }

    public static String id(ItemStack stack) {
        CompoundTag tag = stack == null ? null : stack.m_41783_();
        return tag == null ? "" : tag.m_128461_(ID);
    }

    public static String sender(ItemStack stack) {
        CompoundTag tag = stack == null ? null : stack.m_41783_();
        return tag == null ? "Unknown" : tag.m_128461_(SENDER);
    }

    public static String recipient(ItemStack stack) {
        CompoundTag tag = stack == null ? null : stack.m_41783_();
        return tag == null ? "Unknown" : tag.m_128461_(RECIPIENT);
    }

    public static UUID recipientUuid(ItemStack stack) {
        CompoundTag tag = stack == null ? null : stack.m_41783_();
        return tag != null && tag.m_128403_(RECIPIENT_UUID) ? tag.m_128342_(RECIPIENT_UUID) : null;
    }

    public static long sentAt(ItemStack stack) {
        CompoundTag tag = stack == null ? null : stack.m_41783_();
        return tag == null ? 0L : tag.m_128454_(SENT_AT);
    }

    public static String message(ItemStack stack) {
        CompoundTag tag = stack == null ? null : stack.m_41783_();
        return tag == null ? "" : tag.m_128461_(MESSAGE);
    }
}
