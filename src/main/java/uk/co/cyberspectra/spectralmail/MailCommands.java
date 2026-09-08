package uk.co.cyberspectra.spectralmail;

import java.util.List;
import java.util.UUID;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Player mail plus operator-only courier/Discord administration. */
@Mod.EventBusSubscriber(modid = SpectralMail.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MailCommands {
    private MailCommands() {}

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        LiteralArgumentBuilder<CommandSourceStack> mail = LiteralArgumentBuilder.literal("mail");

        RequiredArgumentBuilder<CommandSourceStack, String> message =
                RequiredArgumentBuilder.argument("message", StringArgumentType.greedyString());
        message.executes(ctx -> send(
                ctx.getSource(),
                StringArgumentType.getString(ctx, "player"),
                StringArgumentType.getString(ctx, "message")
        ));
        RequiredArgumentBuilder<CommandSourceStack, String> target =
                RequiredArgumentBuilder.argument("player", StringArgumentType.word());
        target.then(message);
        mail.then(LiteralArgumentBuilder.<CommandSourceStack>literal("send").then(target));

        RequiredArgumentBuilder<CommandSourceStack, String> writeMessage =
                RequiredArgumentBuilder.argument("message", StringArgumentType.greedyString());
        writeMessage.executes(ctx -> write(
                ctx.getSource(),
                StringArgumentType.getString(ctx, "player"),
                StringArgumentType.getString(ctx, "message")
        ));
        RequiredArgumentBuilder<CommandSourceStack, String> writeTarget =
                RequiredArgumentBuilder.argument("player", StringArgumentType.word());
        writeTarget.then(writeMessage);
        mail.then(LiteralArgumentBuilder.<CommandSourceStack>literal("write").then(writeTarget));

        mail.then(LiteralArgumentBuilder.<CommandSourceStack>literal("collect").executes(ctx -> collect(ctx.getSource())));
        mail.then(LiteralArgumentBuilder.<CommandSourceStack>literal("unread").executes(ctx -> unread(ctx.getSource())));
        mail.then(LiteralArgumentBuilder.<CommandSourceStack>literal("inbox").executes(ctx -> inbox(ctx.getSource())));

        RequiredArgumentBuilder<CommandSourceStack, String> courierPresetPlayer =
                RequiredArgumentBuilder.argument("player", StringArgumentType.word());
        courierPresetPlayer.executes(ctx -> courierPresetBind(
                ctx.getSource(), StringArgumentType.getString(ctx, "player")));
        mail.then(LiteralArgumentBuilder.<CommandSourceStack>literal("courierpresetbind").then(courierPresetPlayer));

        LiteralArgumentBuilder<CommandSourceStack> courier = LiteralArgumentBuilder.<CommandSourceStack>literal("courier")
                .requires(source -> source.hasPermission(2));
        courier.then(LiteralArgumentBuilder.<CommandSourceStack>literal("bind").executes(ctx -> courierBind(ctx.getSource())));
        courier.then(LiteralArgumentBuilder.<CommandSourceStack>literal("sethome").executes(ctx -> courierHome(ctx.getSource())));
        courier.then(LiteralArgumentBuilder.<CommandSourceStack>literal("return").executes(ctx -> courierReturn(ctx.getSource())));
        courier.then(LiteralArgumentBuilder.<CommandSourceStack>literal("clear").executes(ctx -> courierClear(ctx.getSource())));
        courier.then(LiteralArgumentBuilder.<CommandSourceStack>literal("status").executes(ctx -> courierStatus(ctx.getSource())));
        mail.then(courier);

        mail.then(LiteralArgumentBuilder.<CommandSourceStack>literal("discordstatus")
                .requires(source -> source.hasPermission(2)).executes(ctx -> discordStatus(ctx.getSource())));
        mail.then(LiteralArgumentBuilder.<CommandSourceStack>literal("discordpanel")
                .requires(source -> source.hasPermission(2)).executes(ctx -> discordPanel(ctx.getSource())));
        mail.then(LiteralArgumentBuilder.<CommandSourceStack>literal("discordreload")
                .requires(source -> source.hasPermission(2)).executes(ctx -> discordReload(ctx.getSource())));
        mail.then(LiteralArgumentBuilder.<CommandSourceStack>literal("discordsetup")
                .requires(source -> source.hasPermission(2)).executes(ctx -> discordSetup(ctx.getSource())));

        dispatcher.register(mail);
    }

    private static int send(CommandSourceStack source, String targetName, String rawMessage) {
        ServerPlayer sender = source.getPlayer();
        if (sender == null) {
            source.sendFailure(Component.literal("Only a player can send personal mail."));
            return 0;
        }

        SpectralMailConfig config = SpectralMailConfig.get();
        String message = rawMessage == null ? "" : rawMessage.trim();
        if (message.isEmpty()) {
            source.sendFailure(Component.literal("Your letter is empty."));
            return 0;
        }
        if (message.length() > config.maxMessageLength) {
            source.sendFailure(Component.literal("That letter is too long. Maximum: " + config.maxMessageLength + " characters."));
            return 0;
        }

        long now = System.currentTimeMillis();
        long remaining = MailCooldowns.remainingMillis(sender.getUUID(), now, config.sendCooldownSeconds);
        if (remaining > 0L) {
            long seconds = Math.max(1L, (remaining + 999L) / 1000L);
            source.sendFailure(Component.literal("Please wait " + seconds + "s before sending another letter."));
            return 0;
        }

        MinecraftServer server = source.getServer();
        MailSavedData data = MailSavedData.get(server);
        data.remember(sender);

        ServerPlayer onlineTarget = server.getPlayerList().getPlayerByName(targetName);
        UUID recipientUuid;
        String recipientName;
        if (onlineTarget != null) {
            data.remember(onlineTarget);
            recipientUuid = onlineTarget.getUUID();
            recipientName = onlineTarget.getName().getString();
        } else {
            recipientUuid = data.findKnownPlayer(targetName);
            recipientName = recipientUuid == null ? null : data.canonicalName(recipientUuid);
        }

        if (recipientUuid == null) {
            source.sendFailure(Component.literal("Unknown player. They need to have joined the server at least once."));
            return 0;
        }

        MailRecord record = new MailRecord(
                UUID.randomUUID().toString(),
                sender.getUUID(),
                sender.getName().getString(),
                recipientUuid,
                recipientName == null ? targetName : recipientName,
                message,
                now,
                MailRecord.PENDING
        );
        data.add(record);
        MailCooldowns.markSent(sender.getUUID(), now, config.sendCooldownSeconds);

        MailDelivery.DeliveryResult result = MailDelivery.route(data, record, onlineTarget);
        String response = switch (result) {
            case DELIVERED -> "Your sealed letter was delivered to " + record.recipientName + ".";
            case QUEUED_FOR_COURIER -> "Your letter was handed to the Post Office courier for " + record.recipientName + ".";
            case PENDING -> "Your letter is waiting safely for " + record.recipientName + ".";
        };
        source.sendSuccess(() -> Component.literal(response), false);
        return 1;
    }

    private static int write(CommandSourceStack source, String targetName, String rawMessage) {
        ServerPlayer sender = source.getPlayer();
        if (sender == null) {
            source.sendFailure(Component.literal("Only a player can write physical mail."));
            return 0;
        }
        ItemStack paper = sender.getItemInHand(InteractionHand.MAIN_HAND);
        if (paper == null || paper.isEmpty() || paper.getItem() != SpectralMail.LETTER_PAPER.get()) {
            source.sendFailure(Component.literal("Hold Letter Paper in your main hand before using /mail write."));
            return 0;
        }

        String message = rawMessage == null ? "" : rawMessage.trim();
        SpectralMailConfig config = SpectralMailConfig.get();
        if (message.isEmpty() || message.length() > config.maxMessageLength) {
            source.sendFailure(Component.literal(message.isEmpty()
                    ? "Your letter is empty."
                    : "That letter is too long. Maximum: " + config.maxMessageLength + " characters."));
            return 0;
        }

        MinecraftServer server = source.getServer();
        MailSavedData data = MailSavedData.get(server);
        data.remember(sender);
        ServerPlayer onlineTarget = server.getPlayerList().getPlayerByName(targetName);
        UUID recipientUuid;
        String recipientName;
        if (onlineTarget != null) {
            data.remember(onlineTarget);
            recipientUuid = onlineTarget.getUUID();
            recipientName = onlineTarget.getName().getString();
        } else {
            recipientUuid = data.findKnownPlayer(targetName);
            recipientName = recipientUuid == null ? null : data.canonicalName(recipientUuid);
        }
        if (recipientUuid == null) {
            source.sendFailure(Component.literal("Unknown player. They need to have joined the server at least once."));
            return 0;
        }

        ItemStack addressed = DraftLetterData.addressed(
                sender.getUUID(), sender.getName().getString(), recipientUuid,
                recipientName == null ? targetName : recipientName, message, System.currentTimeMillis());
        if (paper.getCount() <= 1) {
            sender.setItemInHand(InteractionHand.MAIN_HAND, addressed);
        } else {
            if (!sender.getInventory().add(addressed)) {
                source.sendFailure(Component.literal("Make one inventory slot free before addressing this letter."));
                return 0;
            }
            paper.shrink(1);
        }
        source.sendSuccess(() -> Component.literal("Addressed a letter to " + (recipientName == null ? targetName : recipientName)
                + ". Put it in a Spectral Mail Drop Box to send it."), false);
        return 1;
    }

    private static int collect(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        int delivered = MailDelivery.deliverPending(player);
        if (delivered > 0) {
            source.sendSuccess(() -> Component.literal("Collected " + delivered + " letter" + (delivered == 1 ? "." : "s.")), false);
            return delivered;
        }
        MailSavedData data = MailSavedData.get(source.getServer());
        if (!data.pendingFor(player.getUUID()).isEmpty()) {
            source.sendFailure(Component.literal("Your inventory may be full, or the pending letter could not be collected yet."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("You have no mail waiting to be collected."), false);
        return 1;
    }

    private static int unread(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        int count = MailSavedData.get(source.getServer()).unreadCount(player.getUUID());
        source.sendSuccess(() -> Component.literal("Unread mail: " + count), false);
        return count;
    }

    private static int inbox(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        List<MailRecord> records = MailSavedData.get(source.getServer()).forRecipient(player.getUUID());
        if (records.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Your mailbox is empty."), false);
            return 1;
        }
        int shown = Math.min(8, records.size());
        source.sendSuccess(() -> Component.literal("Recent mail (" + records.size() + " total):"), false);
        for (int i = 0; i < shown; i++) {
            MailRecord record = records.get(i);
            String state = record.state == MailRecord.READ ? "read"
                    : (record.state == MailRecord.DELIVERED ? "delivered"
                    : (record.state == MailRecord.BOXED ? "letter box" : "waiting"));
            source.sendSuccess(() -> Component.literal("- From " + record.senderName + " [" + state + "]"), false);
        }
        return shown;
    }

    /**
     * EasyNPC preset binding entry point. Non-operator players cannot use this command directly.
     * When invoked by the courier NPC, the named @initiator must be an online operator standing
     * within eight blocks, and the exact command-source EasyNPC is bound.
     */
    private static int courierPresetBind(CommandSourceStack source, String playerName) {
        MinecraftServer server = source.getServer();
        ServerPlayer player = server.getPlayerList().getPlayerByName(playerName);
        if (player == null) {
            source.sendFailure(Component.literal("The courier setup player must be online."));
            return 0;
        }
        if (!MinecraftRuntime.isOperator(server, player)) {
            player.displayClientMessage(Component.literal("Only a server operator can bind the Post Office courier."), false);
            return 0;
        }

        Object commandEntity = source.getEntity();
        boolean sourceIsNpc = MinecraftRuntime.isEasyNpc(commandEntity);
        if (!source.hasPermission(2) && !sourceIsNpc) {
            player.displayClientMessage(Component.literal("Courier preset binding must be run by an operator or from the EasyNPC courier preset."), false);
            return 0;
        }

        String result;
        if (sourceIsNpc) {
            if (!MinecraftRuntime.sameLevel(commandEntity, player)
                    || MinecraftRuntime.distanceSq(commandEntity, player) > 64.0D) {
                player.displayClientMessage(Component.literal("Stand within 8 blocks of the courier before using its setup button."), false);
                return 0;
            }
            result = CourierManager.bindEntity(server, commandEntity);
        } else {
            result = CourierManager.bindNearest(server, player);
        }
        player.displayClientMessage(Component.literal(result), false);
        return result.startsWith("Bound") ? 1 : 0;
    }

    private static int courierBind(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Stand near the EasyNPC postman and run this command as a player."));
            return 0;
        }
        String result = CourierManager.bindNearest(source.getServer(), player);
        source.sendSuccess(() -> Component.literal(result), false);
        return result.startsWith("Bound") ? 1 : 0;
    }

    private static int courierHome(CommandSourceStack source) {
        return adminResult(source, CourierManager.setHome(source.getServer()));
    }

    private static int courierReturn(CommandSourceStack source) {
        return adminResult(source, CourierManager.forceReturn(source.getServer()));
    }

    private static int courierClear(CommandSourceStack source) {
        return adminResult(source, CourierManager.clear(source.getServer()));
    }

    private static int courierStatus(CommandSourceStack source) {
        return adminResult(source, CourierManager.status(source.getServer()));
    }

    private static int discordStatus(CommandSourceStack source) {
        MailSavedData data = MailSavedData.get(source.getServer());
        for (String line : DiscordService.statusLines(data)) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    private static int discordPanel(CommandSourceStack source) {
        DiscordService.start();
        MailSavedData data = MailSavedData.get(source.getServer());
        boolean queued = DiscordService.ensurePanel(data);
        String text = queued ? "Discord Post Office panel refresh queued." : "Discord Post Office panel could not be queued; use /mail discordstatus.";
        source.sendSuccess(() -> Component.literal(text), false);
        return queued ? 1 : 0;
    }

    private static int discordReload(CommandSourceStack source) {
        DiscordService.reload();
        source.sendSuccess(() -> Component.literal("Reloaded " + SpectralMailConfig.configPath() + ". Use /mail discordstatus for connection details."), false);
        return 1;
    }

    private static int discordSetup(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Spectral Mail Discord setup:"), false);
        source.sendSuccess(() -> Component.literal("1. Edit config/spectralmail-server.toml and enable [discord]."), false);
        source.sendSuccess(() -> Component.literal("2. Set bot_token and post_office_channel_id; guild_id and role restrictions are optional."), false);
        source.sendSuccess(() -> Component.literal("3. The bot only requests the GUILDS gateway intent; Message Content is not required."), false);
        source.sendSuccess(() -> Component.literal("4. Run /mail discordreload, then /mail discordpanel."), false);
        return 1;
    }

    private static int adminResult(CommandSourceStack source, String result) {
        source.sendSuccess(() -> Component.literal(result), false);
        return 1;
    }
}
