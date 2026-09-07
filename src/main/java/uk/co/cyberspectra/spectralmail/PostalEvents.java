package uk.co.cyberspectra.spectralmail;

import java.util.List;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Server-authoritative interaction layer for the physical Drop Box and personal Letter Box. */
@Mod.EventBusSubscriber(modid = SpectralMail.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PostalEvents {
    private PostalEvents() {}

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Block block = PostalRuntime.blockAt(event.getLevel(), event.getPos());
        MailSavedData.PostalAddress address = PostalRuntime.address(event.getLevel(), event.getPos());
        if (address == null) return;

        MailSavedData data = MailSavedData.get(player.m_20194_());
        if (block == SpectralMail.LETTER_BOX.get()) {
            data.registerLetterBox(player.m_20148_(), address);
            player.m_5661_(Component.m_237113_("This is now your active Letter Box."), true);
            for (MailRecord record : data.pendingFor(player.m_20148_())) CourierManager.enqueue(record.id);
        } else if (block == SpectralMail.DROP_BOX.get()) {
            data.registerDropBox(address);
            player.m_5661_(Component.m_237113_("Drop Box ready for addressed letters."), true);
        }
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        Block block = PostalRuntime.blockAt(event.getLevel(), event.getPos());
        MailSavedData.PostalAddress address = PostalRuntime.address(event.getLevel(), event.getPos());
        if (address == null) return;
        MailSavedData data = MailSavedData.get(player.m_20194_());

        if (block == SpectralMail.LETTER_BOX.get()) {
            UUID owner = data.letterBoxOwnerAt(address);
            List<String> released = data.unregisterLetterBoxAt(address);
            for (String id : released) CourierManager.enqueue(id);
            if (owner != null) {
                String ownerName = data.canonicalName(owner);
                player.m_5661_(Component.m_237113_("Letter Box removed" + (ownerName == null ? "." : " for " + ownerName + ".")
                        + " Stored mail returned safely to pending delivery."), true);
            }
        } else if (block == SpectralMail.DROP_BOX.get()) {
            List<String> released = data.unregisterDropBox(address);
            for (String id : released) CourierManager.enqueue(id);
            if (!released.isEmpty()) {
                player.m_5661_(Component.m_237113_("Drop Box removed. Its " + released.size()
                        + " posted letter" + (released.size() == 1 ? " is" : "s are") + " still safe at the Post Office."), true);
            }
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Block block = PostalRuntime.blockAt(event.getLevel(), event.getPos());
        MailSavedData.PostalAddress address = PostalRuntime.address(event.getLevel(), event.getPos());
        if (address == null) return;

        if (block == SpectralMail.DROP_BOX.get()) {
            handleDropBox(player, address, event.getItemStack());
        } else if (block == SpectralMail.LETTER_BOX.get()) {
            handleLetterBox(player, address);
        }
    }

    private static void handleDropBox(ServerPlayer player, MailSavedData.PostalAddress address, ItemStack held) {
        MailSavedData data = MailSavedData.get(player.m_20194_());
        SpectralMailConfig config = SpectralMailConfig.get();

        if (held == null || held.m_41619_() || held.m_41720_() != SpectralMail.ADDRESSED_LETTER.get()) {
            int queued = data.dropBoxQueueSize(address);
            player.m_5661_(Component.m_237113_(queued == 0
                    ? "Drop Box: hold an Addressed Letter and right-click to post it."
                    : "Drop Box: " + queued + " letter" + (queued == 1 ? "" : "s") + " waiting for collection."), true);
            return;
        }

        UUID senderUuid = DraftLetterData.senderUuid(held);
        UUID recipientUuid = DraftLetterData.recipientUuid(held);
        String message = DraftLetterData.message(held);
        if (senderUuid == null || !senderUuid.equals(player.m_20148_())) {
            player.m_5661_(Component.m_237113_("That addressed letter is not signed by you."), true);
            return;
        }
        if (recipientUuid == null || data.canonicalName(recipientUuid) == null) {
            player.m_5661_(Component.m_237113_("That recipient is no longer known to the Post Office."), true);
            return;
        }
        if (message == null || message.isBlank() || message.length() > config.maxMessageLength) {
            player.m_5661_(Component.m_237113_("That letter cannot be accepted because its message is invalid."), true);
            return;
        }
        if (data.dropBoxQueueSize(address) >= config.dropBoxCapacity) {
            player.m_5661_(Component.m_237113_("This Drop Box is full. Try another box or wait for the postman."), true);
            return;
        }

        long now = System.currentTimeMillis();
        long remaining = MailCooldowns.remainingMillis(player.m_20148_(), now, config.sendCooldownSeconds);
        if (remaining > 0L) {
            long seconds = Math.max(1L, (remaining + 999L) / 1000L);
            player.m_5661_(Component.m_237113_("Please wait " + seconds + "s before posting another letter."), true);
            return;
        }

        String recipientName = data.canonicalName(recipientUuid);
        MailRecord record = new MailRecord(
                UUID.randomUUID().toString(),
                player.m_20148_(),
                player.m_7755_().getString(),
                recipientUuid,
                recipientName,
                message,
                now,
                MailRecord.PENDING
        );
        data.add(record);
        if (!data.postAtDropBox(address, record.id, config.dropBoxCapacity)) {
            // Capacity was checked immediately above; this is only a defensive fallback.
            data.removeRecord(record);
            player.m_5661_(Component.m_237113_("The Drop Box could not accept that letter. It remains in your hand."), true);
            return;
        }

        held.m_41774_(1);
        MailCooldowns.markSent(player.m_20148_(), now, config.sendCooldownSeconds);
        CourierManager.enqueue(record.id);
        PostalFeedback.posted(player.m_20194_(), address);
        player.m_5661_(Component.m_237113_("Posted to " + recipientName + ". The postman will collect it."), true);
    }

    private static void handleLetterBox(ServerPlayer player, MailSavedData.PostalAddress address) {
        MinecraftServer server = player.m_20194_();
        MailSavedData data = MailSavedData.get(server);
        UUID owner = data.letterBoxOwnerAt(address);
        if (owner == null) {
            data.registerLetterBox(player.m_20148_(), address);
            owner = player.m_20148_();
            player.m_5661_(Component.m_237113_("You claimed this as your active Letter Box."), true);
        }
        if (!owner.equals(player.m_20148_())) {
            String ownerName = data.canonicalName(owner);
            player.m_5661_(Component.m_237113_("This Letter Box belongs to "
                    + (ownerName == null ? "another player" : ownerName) + "."), true);
            return;
        }

        List<MailRecord> boxed = data.boxedFor(owner);
        if (boxed.isEmpty()) {
            int pending = data.pendingFor(owner).size();
            player.m_5661_(Component.m_237113_(pending > 0
                    ? "Your Letter Box is empty, with " + pending + " letter" + (pending == 1 ? "" : "s") + " still in transit."
                    : "Your Letter Box is empty."), true);
            return;
        }

        int collected = 0;
        for (MailRecord record : boxed) {
            ItemStack letter = MailItemData.sealed(record);
            if (!player.m_150109_().m_36054_(letter)) break;
            data.markLetterBoxCollected(record);
            collected++;
        }
        if (collected == 0) {
            player.m_5661_(Component.m_237113_("Your inventory is full. The letters will stay safely in the box."), true);
            return;
        }

        PostalFeedback.collected(server, address);
        player.m_5661_(Component.m_237113_("Collected " + collected + " letter" + (collected == 1 ? "" : "s") + " from your Letter Box."), true);
        for (MailRecord record : data.pendingFor(owner)) CourierManager.enqueue(record.id);
    }
}
