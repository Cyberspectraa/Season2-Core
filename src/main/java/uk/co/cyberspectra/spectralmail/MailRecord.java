package uk.co.cyberspectra.spectralmail;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/** Immutable identity/content plus mutable delivery state for one server-owned letter. */
public final class MailRecord {
    public static final int PENDING = 0;
    public static final int DELIVERED = 1;
    public static final int READ = 2;
    /** Delivered into the owner's registered physical Letter Box, but not yet collected as an item. */
    public static final int BOXED = 3;

    public final String id;
    public final UUID senderUuid;
    public final String senderName;
    public final UUID recipientUuid;
    public final String recipientName;
    public final String message;
    public final long sentAt;
    public int state;

    public MailRecord(String id, UUID senderUuid, String senderName, UUID recipientUuid,
                      String recipientName, String message, long sentAt, int state) {
        this.id = id;
        this.senderUuid = senderUuid;
        this.senderName = senderName;
        this.recipientUuid = recipientUuid;
        this.recipientName = recipientName;
        this.message = message;
        this.sentAt = sentAt;
        this.state = state;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", id);
        tag.putUUID("SenderUUID", senderUuid);
        tag.putString("SenderName", senderName);
        tag.putUUID("RecipientUUID", recipientUuid);
        tag.putString("RecipientName", recipientName);
        tag.putString("Message", message);
        tag.putLong("SentAt", sentAt);
        tag.putInt("State", state);
        return tag;
    }

    public static MailRecord load(CompoundTag tag) {
        if (!tag.hasUUID("SenderUUID") || !tag.hasUUID("RecipientUUID")) return null;
        String id = tag.getString("Id");
        if (id == null || id.isBlank()) return null;
        return new MailRecord(
                id,
                tag.getUUID("SenderUUID"),
                tag.getString("SenderName"),
                tag.getUUID("RecipientUUID"),
                tag.getString("RecipientName"),
                tag.getString("Message"),
                tag.getLong("SentAt"),
                tag.getInt("State")
        );
    }
}
