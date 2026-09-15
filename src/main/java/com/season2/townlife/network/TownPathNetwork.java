package com.season2.townlife.network;

import com.season2.townlife.TownLife;
import com.season2.townlife.client.TownPathClientBridge;
import com.season2.townlife.data.TownPathType;
import com.season2.townlife.item.PathEditMode;
import com.season2.townlife.item.PathWandSettings;
import com.season2.townlife.registry.ModItems;
import com.season2.townlife.runtime.TownPathService;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Network boundary for the operator Path Wand configuration screen. */
public final class TownPathNetwork {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(TownLife.MOD_ID, "path_wand"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals);

    private static int packetId;
    private static boolean registered;

    private TownPathNetwork() {}

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        CHANNEL.registerMessage(packetId++, OpenConfigPacket.class,
                OpenConfigPacket::encode, OpenConfigPacket::decode, OpenConfigPacket::handle);
        CHANNEL.registerMessage(packetId++, UpdateSettingsPacket.class,
                UpdateSettingsPacket::encode, UpdateSettingsPacket::decode, UpdateSettingsPacket::handle);
        CHANNEL.registerMessage(packetId++, InspectNearbyPacket.class,
                InspectNearbyPacket::encode, InspectNearbyPacket::decode, InspectNearbyPacket::handle);
    }

    public static void openConfig(ServerPlayer player, InteractionHand hand) {
        if (player == null || hand == null) return;
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.is(ModItems.PATH_WAND.get())) return;
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new OpenConfigPacket(PathWandSettings.mode(stack), PathWandSettings.type(stack), hand));
    }

    private static boolean canEdit(ServerPlayer player, InteractionHand hand) {
        return player != null
                && player.hasPermissions(2)
                && player.getItemInHand(hand).is(ModItems.PATH_WAND.get());
    }

    public record OpenConfigPacket(PathEditMode mode, TownPathType type, InteractionHand hand) {
        private static void encode(OpenConfigPacket packet, FriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.mode.ordinal());
            buffer.writeVarInt(packet.type.ordinal());
            buffer.writeBoolean(packet.hand == InteractionHand.OFF_HAND);
        }

        private static OpenConfigPacket decode(FriendlyByteBuf buffer) {
            PathEditMode mode = PathEditMode.fromOrdinal(buffer.readVarInt());
            int typeOrdinal = buffer.readVarInt();
            TownPathType[] types = TownPathType.values();
            TownPathType type = typeOrdinal >= 0 && typeOrdinal < types.length ? types[typeOrdinal] : TownPathType.NORMAL;
            InteractionHand hand = buffer.readBoolean() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            return new OpenConfigPacket(mode, type, hand);
        }

        private static void handle(OpenConfigPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            context.enqueueWork(() -> TownPathClientBridge.openConfig(packet.mode, packet.type, packet.hand));
            context.setPacketHandled(true);
        }
    }

    public record UpdateSettingsPacket(PathEditMode mode, TownPathType type, InteractionHand hand) {
        private static void encode(UpdateSettingsPacket packet, FriendlyByteBuf buffer) {
            buffer.writeVarInt(packet.mode.ordinal());
            buffer.writeVarInt(packet.type.ordinal());
            buffer.writeBoolean(packet.hand == InteractionHand.OFF_HAND);
        }

        private static UpdateSettingsPacket decode(FriendlyByteBuf buffer) {
            PathEditMode mode = PathEditMode.fromOrdinal(buffer.readVarInt());
            int typeOrdinal = buffer.readVarInt();
            TownPathType[] types = TownPathType.values();
            TownPathType type = typeOrdinal >= 0 && typeOrdinal < types.length ? types[typeOrdinal] : TownPathType.NORMAL;
            InteractionHand hand = buffer.readBoolean() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            return new UpdateSettingsPacket(mode, type, hand);
        }

        private static void handle(UpdateSettingsPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            ServerPlayer sender = context.getSender();
            if (sender != null) {
                context.enqueueWork(() -> {
                    if (!canEdit(sender, packet.hand)) {
                        sender.displayClientMessage(Component.literal("Path Wand settings require operator permission."), true);
                        return;
                    }
                    ItemStack stack = sender.getItemInHand(packet.hand);
                    PathWandSettings.set(stack, packet.mode, packet.type);
                    sender.displayClientMessage(Component.literal(
                            "Path Wand: " + packet.mode.displayName() + " • " + packet.type.displayName()), true);
                });
            }
            context.setPacketHandled(true);
        }
    }

    public record InspectNearbyPacket(InteractionHand hand) {
        private static void encode(InspectNearbyPacket packet, FriendlyByteBuf buffer) {
            buffer.writeBoolean(packet.hand == InteractionHand.OFF_HAND);
        }

        private static InspectNearbyPacket decode(FriendlyByteBuf buffer) {
            return new InspectNearbyPacket(buffer.readBoolean() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        }

        private static void handle(InspectNearbyPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            ServerPlayer sender = context.getSender();
            if (sender != null) {
                context.enqueueWork(() -> {
                    if (canEdit(sender, packet.hand)) {
                        TownPathService.inspectNearby(sender.serverLevel(), sender);
                    }
                });
            }
            context.setPacketHandled(true);
        }
    }
}
