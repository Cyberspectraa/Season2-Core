package uk.co.cyberspectra.spectralmail;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/** Server-created NBT carried by an addressed but not-yet-posted physical letter. */
public final class DraftLetterData {
    private static final String SENDER_UUID = "SpectralMailDraftSenderUUID";
    private static final String SENDER_NAME = "SpectralMailDraftSenderName";
    private static final String RECIPIENT_UUID = "SpectralMailDraftRecipientUUID";
    private static final String RECIPIENT_NAME = "SpectralMailDraftRecipientName";
    private static final String MESSAGE = "SpectralMailDraftMessage";
    private static final String WRITTEN_AT = "SpectralMailDraftWrittenAt";

    private DraftLetterData() {}

    public static ItemStack addressed(UUID senderUuid, String senderName, UUID recipientUuid,
                                      String recipientName, String message, long writtenAt) {
        ItemStack stack = new ItemStack(SpectralMail.ADDRESSED_LETTER.get());
        CompoundTag tag = stack.getOrCreateTag();
        tag.putUUID(SENDER_UUID, senderUuid);
        tag.putString(SENDER_NAME, senderName == null ? "Unknown" : senderName);
        tag.putUUID(RECIPIENT_UUID, recipientUuid);
        tag.putString(RECIPIENT_NAME, recipientName == null ? "Unknown" : recipientName);
        tag.putString(MESSAGE, message == null ? "" : message);
        tag.putLong(WRITTEN_AT, writtenAt);
        return stack;
    }

    public static UUID senderUuid(ItemStack stack) {
        CompoundTag tag = stack == null ? null : stack.getTag();
        return tag != null && tag.hasUUID(SENDER_UUID) ? tag.getUUID(SENDER_UUID) : null;
    }

    public static String senderName(ItemStack stack) {
        CompoundTag tag = stack == null ? null : stack.getTag();
        return tag == null ? "" : tag.getString(SENDER_NAME);
    }

    public static UUID recipientUuid(ItemStack stack) {
        CompoundTag tag = stack == null ? null : stack.getTag();
        return tag != null && tag.hasUUID(RECIPIENT_UUID) ? tag.getUUID(RECIPIENT_UUID) : null;
    }

    public static String recipientName(ItemStack stack) {
        CompoundTag tag = stack == null ? null : stack.getTag();
        return tag == null ? "" : tag.getString(RECIPIENT_NAME);
    }

    public static String message(ItemStack stack) {
        CompoundTag tag = stack == null ? null : stack.getTag();
        return tag == null ? "" : tag.getString(MESSAGE);
    }

    public static long writtenAt(ItemStack stack) {
        CompoundTag tag = stack == null ? null : stack.getTag();
        return tag == null ? 0L : tag.getLong(WRITTEN_AT);
    }
}
