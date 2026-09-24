package com.season2.townlife.network;

import com.season2.townlife.TownLife;
import com.season2.townlife.client.TownWandClientBridge;
import com.season2.townlife.data.Resident;
import com.season2.townlife.data.TownLifeSavedData;
import com.season2.townlife.item.DevClockItem;
import com.season2.townlife.item.TownWandAction;
import com.season2.townlife.item.TownWandItem;
import com.season2.townlife.registry.ModItems;
import com.season2.townlife.runtime.TownLifeLiteService;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
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

/** Town Wand GUI packet boundary: the server remains authoritative. */
public final class TownWandNetwork {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(TownLife.MOD_ID, "town_wand_config"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    private static boolean registered;
    private TownWandNetwork() {}

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        CHANNEL.registerMessage(0, OpenPacket.class, OpenPacket::encode, OpenPacket::decode, OpenPacket::handle);
        CHANNEL.registerMessage(1, ActionPacket.class, ActionPacket::encode, ActionPacket::decode, ActionPacket::handle);
    }

    public static void openConfig(ServerPlayer player, InteractionHand hand) {
        if (!canEdit(player, hand)) return;
        ItemStack wand = player.getItemInHand(hand);
        Resident resident = TownWandItem.selectedNpc(wand) == null ? null :
                TownLifeSavedData.get(player.serverLevel()).resident(TownWandItem.selectedNpc(wand)).orElse(null);
        if (resident == null && TownWandItem.hasSelectedNpc(wand)) {
            player.displayClientMessage(Component.literal("Selected resident is not loaded in this dimension. Selection preserved.")
                    .withStyle(ChatFormatting.YELLOW), true);
        }
        String name = resident == null ? (TownWandItem.hasSelectedNpc(wand) ? TownWandItem.selectedName(wand) : "None") : resident.identityName();
        String profession = resident == null ? "No resident loaded" : resident.jobType().name().replace('_', ' ');
        String home = resident == null ? "Not available" : (resident.homeLocationId().isBlank() ? "Not assigned" : "Assigned");
        String workplace = resident == null ? "Not available" : (resident.workplaceLocationId().isBlank() ? "Not assigned" : "Assigned");
        String position = resident == null ? "Not available" : (resident.workPosition() == null ? "Not assigned" : resident.workPosition().toShortString());
        String schedule = resident == null ? "Select an EasyNPC to view its schedule" :
                "Wake " + DevClockItem.formatTime(resident.wakeTime())
                        + "  Work " + DevClockItem.formatTime(resident.workStart()) + "-" + DevClockItem.formatTime(resident.workEnd())
                        + "  Break " + DevClockItem.formatTime(resident.breakStart()) + "-" + DevClockItem.formatTime(resident.breakEnd())
                        + "  Bed " + DevClockItem.formatTime(resident.sleepTime());
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new OpenPacket(hand,
                name, profession, home, workplace, position, schedule, TownWandItem.action(wand), resident != null));
    }

    private static boolean canEdit(ServerPlayer player, InteractionHand hand) {
        return player != null && hand != null && player.hasPermissions(2)
                && player.getItemInHand(hand).is(ModItems.TOWN_WAND.get());
    }

    public record OpenPacket(InteractionHand hand, String name, String job, String home,
                             String workplace, String position, String schedule,
                             TownWandAction action, boolean selectedLoaded) {
        private static void encode(OpenPacket packet, FriendlyByteBuf buf) {
            buf.writeBoolean(packet.hand == InteractionHand.OFF_HAND);
            buf.writeUtf(packet.name, 128);
            buf.writeUtf(packet.job, 80);
            buf.writeUtf(packet.home, 80);
            buf.writeUtf(packet.workplace, 80);
            buf.writeUtf(packet.position, 80);
            buf.writeUtf(packet.schedule, 200);
            buf.writeVarInt(packet.action.ordinal());
            buf.writeBoolean(packet.selectedLoaded);
        }
        private static OpenPacket decode(FriendlyByteBuf buf) {
            return new OpenPacket(buf.readBoolean() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND,
                    buf.readUtf(128), buf.readUtf(80), buf.readUtf(80), buf.readUtf(80), buf.readUtf(80),
                    buf.readUtf(200), TownWandAction.safeOrdinal(buf.readVarInt()), buf.readBoolean());
        }
        private static void handle(OpenPacket packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            context.enqueueWork(() -> TownWandClientBridge.open(packet));
            context.setPacketHandled(true);
        }
    }

    public record ActionPacket(InteractionHand hand, TownWandAction action) {
        private static void encode(ActionPacket packet, FriendlyByteBuf buf) {
            buf.writeBoolean(packet.hand == InteractionHand.OFF_HAND);
            buf.writeVarInt(packet.action.ordinal());
        }
        private static ActionPacket decode(FriendlyByteBuf buf) {
            return new ActionPacket(buf.readBoolean() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND,
                    TownWandAction.safeOrdinal(buf.readVarInt()));
        }
        private static void handle(ActionPacket packet, Supplier<NetworkEvent.Context> supplier) {
            NetworkEvent.Context context = supplier.get();
            ServerPlayer sender = context.getSender();
            if (sender != null) context.enqueueWork(() -> apply(sender, packet));
            context.setPacketHandled(true);
        }
    }

    private static void apply(ServerPlayer player, ActionPacket packet) {
        if (!canEdit(player, packet.hand)) return;
        ItemStack wand = player.getItemInHand(packet.hand);
        if (packet.action == TownWandAction.CLEAR_SELECTION) {
            TownLifeLiteService.clearSelection(player, wand);
            return;
        }
        if (TownWandItem.selectedNpc(wand) == null) {
            player.displayClientMessage(Component.literal("Select an Easy NPC first."), true);
            return;
        }
        if (packet.action == TownWandAction.CLEAR_POSITION) {
            TownLifeLiteService.clearWorkPosition(player.serverLevel(), player, wand);
            return;
        }
        if (!packet.action.requiresBlock() && packet.action != TownWandAction.NONE) return;
        if (packet.action.requiresBlock()
                && TownLifeSavedData.get(player.serverLevel()).resident(TownWandItem.selectedNpc(wand)).isEmpty()) {
            player.displayClientMessage(Component.literal("Selected resident is not loaded in this dimension."), true);
            return;
        }
        TownWandItem.setAction(wand, packet.action);
        player.displayClientMessage(Component.literal(packet.action == TownWandAction.NONE
                ? "Town Wand action cancelled." : packet.action.label() + ": right-click the target block (sneaking is not required).")
                .withStyle(ChatFormatting.AQUA), false);
    }
}
