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
        CompoundTag tag = stack.m_41784_();
        tag.m_128362_(SENDER_UUID, senderUuid);
        tag.m_128359_(SENDER_NAME, senderName == null ? "Unknown" : senderName);
        tag.m_128362_(RECIPIENT_UUID, recipientUuid);
        tag.m_128359_(RECIPIENT_NAME, recipientName == null ? "Unknown" : recipientName);
        tag.m_128359_(MESSAGE, message == null ? "" : message);
        tag.m_128356_(WRITTEN_AT, writtenAt);
        return stack;
    }

    public static UUID senderUuid(ItemStack stack) {
        CompoundTag tag = stack == null ? null : stack.m_41783_();
        return tag != null && tag.m_128403_(SENDER_UUID) ? tag.m_128342_(SENDER_UUID) : null;
    }

    public static String senderName(ItemStack stack) {
        CompoundTag tag = stack == null ? null : stack.m_41783_();
        return tag == null ? "" : tag.m_128461_(SENDER_NAME);
    }

    public static UUID recipientUuid(ItemStack stack) {
        CompoundTag tag = stack == null ? null : stack.m_41783_();
        return tag != null && tag.m_128403_(RECIPIENT_UUID) ? tag.m_128342_(RECIPIENT_UUID) : null;
    }

    public static String recipientName(ItemStack stack) {
        CompoundTag tag = stack == null ? null : stack.m_41783_();
        return tag == null ? "" : tag.m_128461_(RECIPIENT_NAME);
    }

    public static String message(ItemStack stack) {
        CompoundTag tag = stack == null ? null : stack.m_41783_();
        return tag == null ? "" : tag.m_128461_(MESSAGE);
    }

    public static long writtenAt(ItemStack stack) {
        CompoundTag tag = stack == null ? null : stack.m_41783_();
        return tag == null ? 0L : tag.m_128454_(WRITTEN_AT);
    }
}
