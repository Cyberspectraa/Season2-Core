package uk.co.cyberspectra.spectralmail;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Server-only delivery helper shared by direct fallback, Letter Boxes and the EasyNPC courier. */
public final class MailDelivery {
    public enum DeliveryResult { DELIVERED, QUEUED_FOR_COURIER, PENDING }

    private MailDelivery() {}

    public static boolean deliver(MailSavedData data, MailRecord record, ServerPlayer recipient) {
        if (data == null || record == null || recipient == null) return false;
        if (record.state != MailRecord.PENDING) return false;
        if (!record.recipientUuid.equals(recipient.getUUID())) return false;

        ItemStack letter = MailItemData.sealed(record);
        if (!recipient.getInventory().add(letter)) return false;

        data.setState(record, MailRecord.DELIVERED);
        recipient.displayClientMessage(Component.literal("You received a sealed letter from " + record.senderName + "."), true);
        return true;
    }

    /**
     * Normal route: when a courier exists, queue if either the player is online or a registered
     * Letter Box gives the courier a physical destination. Otherwise keep the authoritative record pending.
     */
    public static DeliveryResult route(MailSavedData data, MailRecord record, ServerPlayer recipient) {
        if (data == null || record == null) return DeliveryResult.PENDING;
        SpectralMailConfig config = SpectralMailConfig.get();
        if (config.courierEnabled && data.hasCourier()
                && (recipient != null || data.hasLetterBox(record.recipientUuid))) {
            CourierManager.enqueue(record.id);
            return DeliveryResult.QUEUED_FOR_COURIER;
        }
        if (recipient != null && deliver(data, record, recipient)) return DeliveryResult.DELIVERED;
        return DeliveryResult.PENDING;
    }

    /** Manual safety fallback. /mail collect intentionally bypasses courier routing and physical boxes. */
    public static int deliverPending(ServerPlayer player) {
        MailSavedData data = MailSavedData.get(player.getServer());
        data.remember(player);
        List<MailRecord> pending = data.pendingFor(player.getUUID());
        int delivered = 0;
        for (MailRecord record : pending) {
            if (!deliver(data, record, player)) break;
            delivered++;
        }
        return delivered;
    }

    public static void routePending(ServerPlayer player) {
        if (player == null) return;
        MailSavedData data = MailSavedData.get(player.getServer());
        data.remember(player);
        if (SpectralMailConfig.get().courierEnabled && data.hasCourier()) {
            for (MailRecord record : data.pendingFor(player.getUUID())) CourierManager.enqueue(record.id);
        } else {
            deliverPending(player);
        }
    }
}
