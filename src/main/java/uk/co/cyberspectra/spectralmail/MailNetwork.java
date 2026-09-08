package uk.co.cyberspectra.spectralmail;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Network boundary for the physical-letter composer. All letter creation remains server-authoritative. */
public final class MailNetwork {
    private static final String PROTOCOL = "1";
    private static final int MAX_RECIPIENTS = 512;

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(SpectralMail.MODID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals);

    private static int packetId;
    private static boolean registered;

    private MailNetwork() {}

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        CHANNEL.registerMessage(packetId++, OpenComposerPacket.class,
                OpenComposerPacket::encode, OpenComposerPacket::decode, OpenComposerPacket::handle);
        CHANNEL.registerMessage(packetId++, SubmitDraftPacket.class,
                SubmitDraftPacket::encode, SubmitDraftPacket::decode, SubmitDraftPacket::handle);
    }

    public static void openComposer(ServerPlayer player, InteractionHand hand) {
        if (player == null) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;

        ItemStack held = player.getItemInHand(hand);
        if (!held.is(SpectralMail.LETTER_PAPER.get())) return;

        MailSavedData data = MailSavedData.get(server);
        data.remember(player);
        for (ServerPlayer online : server.getPlayerList().getPlayers()) data.remember(online);

        List<ComposeRecipient> recipients = new ArrayList<>();
        for (MailSavedData.KnownPlayer known : data.knownPlayersAlphabetical()) {
            if (known.uuid().equals(player.getUUID())) continue;
            recipients.add(new ComposeRecipient(known.uuid(), known.name()));
            if (recipients.size() >= MAX_RECIPIENTS) break;
        }

        OpenComposerPacket packet = new OpenComposerPacket(
                recipients,
                SpectralMailConfig.get().maxMessageLength,
                hand == null ? InteractionHand.MAIN_HAND : hand);
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    private static void addressLetter(ServerPlayer sender, UUID recipientUuid, String rawMessage, InteractionHand hand) {
        if (sender == null || recipientUuid == null) return;
        MinecraftServer server = sender.getServer();
        if (server == null) return;

        InteractionHand safeHand = hand == null ? InteractionHand.MAIN_HAND : hand;
        ItemStack paper = sender.getItemInHand(safeHand);
        if (!paper.is(SpectralMail.LETTER_PAPER.get())) {
            sender.displayClientMessage(Component.literal("Hold Letter Paper while addressing the letter."), false);
            return;
        }

        String message = rawMessage == null ? "" : rawMessage.trim();
        SpectralMailConfig config = SpectralMailConfig.get();
        if (message.isEmpty()) {
            sender.displayClientMessage(Component.literal("Your letter is empty."), false);
            return;
        }
        if (message.length() > config.maxMessageLength) {
            sender.displayClientMessage(Component.literal(
                    "That letter is too long. Maximum: " + config.maxMessageLength + " characters."), false);
            return;
        }
        if (recipientUuid.equals(sender.getUUID())) {
            sender.displayClientMessage(Component.literal("Pick another player as the recipient."), false);
            return;
        }

        MailSavedData data = MailSavedData.get(server);
        data.remember(sender);
        ServerPlayer onlineRecipient = server.getPlayerList().getPlayer(recipientUuid);
        if (onlineRecipient != null) data.remember(onlineRecipient);
        String recipientName = data.canonicalName(recipientUuid);
        if (recipientName == null || recipientName.isBlank()) {
            sender.displayClientMessage(Component.literal(
                    "That recipient is no longer known to Spectral Mail. Reopen the paper and choose again."), false);
            return;
        }

        ItemStack addressed = DraftLetterData.addressed(
                sender.getUUID(),
                sender.getName().getString(),
                recipientUuid,
                recipientName,
                message,
                System.currentTimeMillis());

        if (paper.getCount() <= 1) {
            sender.setItemInHand(safeHand, addressed);
        } else {
            if (!sender.getInventory().add(addressed)) {
                sender.displayClientMessage(Component.literal(
                        "Make one inventory slot free before addressing this letter."), false);
                return;
            }
            paper.shrink(1);
        }

        sender.displayClientMessage(Component.literal(
                "Addressed a letter to " + recipientName + ". Put it in a Spectral Mail Drop Box to send it."), false);
    }

    public record OpenComposerPacket(List<ComposeRecipient> recipients, int maxLength, InteractionHand hand) {
        private static void encode(OpenComposerPacket packet, FriendlyByteBuf buffer) {
            List<ComposeRecipient> recipients = packet.recipients == null ? List.of() : packet.recipients;
            buffer.writeVarInt(Math.min(MAX_RECIPIENTS, recipients.size()));
            for (int i = 0; i < recipients.size() && i < MAX_RECIPIENTS; i++) {
                ComposeRecipient recipient = recipients.get(i);
                buffer.writeUUID(recipient.uuid());
                buffer.writeUtf(recipient.name(), 64);
            }
            buffer.writeVarInt(Math.max(32, packet.maxLength));
            buffer.writeBoolean(packet.hand == InteractionHand.OFF_HAND);
        }

        private static OpenComposerPacket decode(FriendlyByteBuf buffer) {
            int count = Math.min(MAX_RECIPIENTS, Math.max(0, buffer.readVarInt()));
            List<ComposeRecipient> recipients = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                recipients.add(new ComposeRecipient(buffer.readUUID(), buffer.readUtf(64)));
            }
            int maxLength = Math.max(32, Math.min(12000, buffer.readVarInt()));
            InteractionHand hand = buffer.readBoolean() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            return new OpenComposerPacket(List.copyOf(recipients), maxLength, hand);
        }

        private static void handle(OpenComposerPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            context.enqueueWork(() -> ClientBridge.openComposer(packet.recipients, packet.maxLength, packet.hand));
            context.setPacketHandled(true);
        }
    }

    public record SubmitDraftPacket(UUID recipientUuid, String message, InteractionHand hand) {
        private static void encode(SubmitDraftPacket packet, FriendlyByteBuf buffer) {
            buffer.writeUUID(packet.recipientUuid);
            buffer.writeUtf(packet.message == null ? "" : packet.message, 12000);
            buffer.writeBoolean(packet.hand == InteractionHand.OFF_HAND);
        }

        private static SubmitDraftPacket decode(FriendlyByteBuf buffer) {
            UUID recipient = buffer.readUUID();
            String message = buffer.readUtf(12000);
            InteractionHand hand = buffer.readBoolean() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            return new SubmitDraftPacket(recipient, message, hand);
        }

        private static void handle(SubmitDraftPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            ServerPlayer sender = context.getSender();
            if (sender != null) {
                context.enqueueWork(() -> addressLetter(sender, packet.recipientUuid, packet.message, packet.hand));
            }
            context.setPacketHandled(true);
        }
    }
}
