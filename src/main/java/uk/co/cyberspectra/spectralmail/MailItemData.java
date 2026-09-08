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
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString(SENDER, record == null ? "Post Office" : record.senderName);
        if (record != null) tag.putLong(SENT_AT, record.sentAt);
        tag.putString("SpectralMailCourierVisual", "1");
        return stack;
    }

    public static ItemStack openedFrom(ItemStack original) {
        ItemStack stack = new ItemStack(SpectralMail.OPENED_LETTER.get());
        CompoundTag originalTag = original == null ? null : original.getTag();
        if (originalTag != null) stack.setTag(originalTag);
        return stack;
    }

    public static void write(ItemStack stack, MailRecord record) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString(ID, record.id);
        tag.putString(SENDER, record.senderName);
        tag.putString(RECIPIENT, record.recipientName);
        tag.putUUID(RECIPIENT_UUID, record.recipientUuid);
        tag.putLong(SENT_AT, record.sentAt);
        tag.putString(MESSAGE, record.message);
    }

    public static String id(ItemStack stack) {
        CompoundTag tag = stack == null ? null : stack.getTag();
        return tag == null ? "" : tag.getString(ID);
    }

    public static String sender(ItemStack stack) {
        CompoundTag tag = stack == null ? null : stack.getTag();
        return tag == null ? "Unknown" : tag.getString(SENDER);
    }

    public static String recipient(ItemStack stack) {
        CompoundTag tag = stack == null ? null : stack.getTag();
        return tag == null ? "Unknown" : tag.getString(RECIPIENT);
    }

    public static UUID recipientUuid(ItemStack stack) {
        CompoundTag tag = stack == null ? null : stack.getTag();
        return tag != null && tag.hasUUID(RECIPIENT_UUID) ? tag.getUUID(RECIPIENT_UUID) : null;
    }

    public static long sentAt(ItemStack stack) {
        CompoundTag tag = stack == null ? null : stack.getTag();
        return tag == null ? 0L : tag.getLong(SENT_AT);
    }

    public static String message(ItemStack stack) {
        CompoundTag tag = stack == null ? null : stack.getTag();
        return tag == null ? "" : tag.getString(MESSAGE);
    }
}
