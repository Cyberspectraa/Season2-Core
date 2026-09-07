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
        tag.m_128359_("Id", id);
        tag.m_128362_("SenderUUID", senderUuid);
        tag.m_128359_("SenderName", senderName);
        tag.m_128362_("RecipientUUID", recipientUuid);
        tag.m_128359_("RecipientName", recipientName);
        tag.m_128359_("Message", message);
        tag.m_128356_("SentAt", sentAt);
        tag.m_128405_("State", state);
        return tag;
    }

    public static MailRecord load(CompoundTag tag) {
        if (!tag.m_128403_("SenderUUID") || !tag.m_128403_("RecipientUUID")) return null;
        String id = tag.m_128461_("Id");
        if (id == null || id.isBlank()) return null;
        return new MailRecord(
                id,
                tag.m_128342_("SenderUUID"),
                tag.m_128461_("SenderName"),
                tag.m_128342_("RecipientUUID"),
                tag.m_128461_("RecipientName"),
                tag.m_128461_("Message"),
                tag.m_128454_("SentAt"),
                tag.m_128451_("State")
        );
    }
}
