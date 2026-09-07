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
                .requires(source -> source.m_6761_(2));
        courier.then(LiteralArgumentBuilder.<CommandSourceStack>literal("bind").executes(ctx -> courierBind(ctx.getSource())));
        courier.then(LiteralArgumentBuilder.<CommandSourceStack>literal("sethome").executes(ctx -> courierHome(ctx.getSource())));
        courier.then(LiteralArgumentBuilder.<CommandSourceStack>literal("return").executes(ctx -> courierReturn(ctx.getSource())));
        courier.then(LiteralArgumentBuilder.<CommandSourceStack>literal("clear").executes(ctx -> courierClear(ctx.getSource())));
        courier.then(LiteralArgumentBuilder.<CommandSourceStack>literal("status").executes(ctx -> courierStatus(ctx.getSource())));
        mail.then(courier);

        mail.then(LiteralArgumentBuilder.<CommandSourceStack>literal("discordstatus")
                .requires(source -> source.m_6761_(2)).executes(ctx -> discordStatus(ctx.getSource())));
        mail.then(LiteralArgumentBuilder.<CommandSourceStack>literal("discordpanel")
                .requires(source -> source.m_6761_(2)).executes(ctx -> discordPanel(ctx.getSource())));
        mail.then(LiteralArgumentBuilder.<CommandSourceStack>literal("discordreload")
                .requires(source -> source.m_6761_(2)).executes(ctx -> discordReload(ctx.getSource())));
        mail.then(LiteralArgumentBuilder.<CommandSourceStack>literal("discordsetup")
                .requires(source -> source.m_6761_(2)).executes(ctx -> discordSetup(ctx.getSource())));

        dispatcher.register(mail);
    }

    private static int send(CommandSourceStack source, String targetName, String rawMessage) {
        ServerPlayer sender = source.m_230896_();
        if (sender == null) {
            source.m_81352_(Component.m_237113_("Only a player can send personal mail."));
            return 0;
        }

        SpectralMailConfig config = SpectralMailConfig.get();
        String message = rawMessage == null ? "" : rawMessage.trim();
        if (message.isEmpty()) {
            source.m_81352_(Component.m_237113_("Your letter is empty."));
            return 0;
        }
        if (message.length() > config.maxMessageLength) {
            source.m_81352_(Component.m_237113_("That letter is too long. Maximum: " + config.maxMessageLength + " characters."));
            return 0;
        }

        long now = System.currentTimeMillis();
        long remaining = MailCooldowns.remainingMillis(sender.m_20148_(), now, config.sendCooldownSeconds);
        if (remaining > 0L) {
            long seconds = Math.max(1L, (remaining + 999L) / 1000L);
            source.m_81352_(Component.m_237113_("Please wait " + seconds + "s before sending another letter."));
            return 0;
        }

        MinecraftServer server = source.m_81377_();
        MailSavedData data = MailSavedData.get(server);
        data.remember(sender);

        ServerPlayer onlineTarget = server.m_6846_().m_11255_(targetName);
        UUID recipientUuid;
        String recipientName;
        if (onlineTarget != null) {
            data.remember(onlineTarget);
            recipientUuid = onlineTarget.m_20148_();
            recipientName = onlineTarget.m_7755_().getString();
        } else {
            recipientUuid = data.findKnownPlayer(targetName);
            recipientName = recipientUuid == null ? null : data.canonicalName(recipientUuid);
        }

        if (recipientUuid == null) {
            source.m_81352_(Component.m_237113_("Unknown player. They need to have joined the server at least once."));
            return 0;
        }

        MailRecord record = new MailRecord(
                UUID.randomUUID().toString(),
                sender.m_20148_(),
                sender.m_7755_().getString(),
                recipientUuid,
                recipientName == null ? targetName : recipientName,
                message,
                now,
                MailRecord.PENDING
        );
        data.add(record);
        MailCooldowns.markSent(sender.m_20148_(), now, config.sendCooldownSeconds);

        MailDelivery.DeliveryResult result = MailDelivery.route(data, record, onlineTarget);
        String response = switch (result) {
            case DELIVERED -> "Your sealed letter was delivered to " + record.recipientName + ".";
            case QUEUED_FOR_COURIER -> "Your letter was handed to the Post Office courier for " + record.recipientName + ".";
            case PENDING -> "Your letter is waiting safely for " + record.recipientName + ".";
        };
        source.m_288197_(() -> Component.m_237113_(response), false);
        return 1;
    }

    private static int write(CommandSourceStack source, String targetName, String rawMessage) {
        ServerPlayer sender = source.m_230896_();
        if (sender == null) {
            source.m_81352_(Component.m_237113_("Only a player can write physical mail."));
            return 0;
        }
        ItemStack paper = sender.m_21120_(InteractionHand.MAIN_HAND);
        if (paper == null || paper.m_41619_() || paper.m_41720_() != SpectralMail.LETTER_PAPER.get()) {
            source.m_81352_(Component.m_237113_("Hold Letter Paper in your main hand before using /mail write."));
            return 0;
        }

        String message = rawMessage == null ? "" : rawMessage.trim();
        SpectralMailConfig config = SpectralMailConfig.get();
        if (message.isEmpty() || message.length() > config.maxMessageLength) {
            source.m_81352_(Component.m_237113_(message.isEmpty()
                    ? "Your letter is empty."
                    : "That letter is too long. Maximum: " + config.maxMessageLength + " characters."));
            return 0;
        }

        MinecraftServer server = source.m_81377_();
        MailSavedData data = MailSavedData.get(server);
        data.remember(sender);
        ServerPlayer onlineTarget = server.m_6846_().m_11255_(targetName);
        UUID recipientUuid;
        String recipientName;
        if (onlineTarget != null) {
            data.remember(onlineTarget);
            recipientUuid = onlineTarget.m_20148_();
            recipientName = onlineTarget.m_7755_().getString();
        } else {
            recipientUuid = data.findKnownPlayer(targetName);
            recipientName = recipientUuid == null ? null : data.canonicalName(recipientUuid);
        }
        if (recipientUuid == null) {
            source.m_81352_(Component.m_237113_("Unknown player. They need to have joined the server at least once."));
            return 0;
        }

        ItemStack addressed = DraftLetterData.addressed(
                sender.m_20148_(), sender.m_7755_().getString(), recipientUuid,
                recipientName == null ? targetName : recipientName, message, System.currentTimeMillis());
        if (paper.m_41613_() <= 1) {
            sender.m_21008_(InteractionHand.MAIN_HAND, addressed);
        } else {
            if (!sender.m_150109_().m_36054_(addressed)) {
                source.m_81352_(Component.m_237113_("Make one inventory slot free before addressing this letter."));
                return 0;
            }
            paper.m_41774_(1);
        }
        source.m_288197_(() -> Component.m_237113_("Addressed a letter to " + (recipientName == null ? targetName : recipientName)
                + ". Put it in a Spectral Mail Drop Box to send it."), false);
        return 1;
    }

    private static int collect(CommandSourceStack source) {
        ServerPlayer player = source.m_230896_();
        if (player == null) return 0;
        int delivered = MailDelivery.deliverPending(player);
        if (delivered > 0) {
            source.m_288197_(() -> Component.m_237113_("Collected " + delivered + " letter" + (delivered == 1 ? "." : "s.")), false);
            return delivered;
        }
        MailSavedData data = MailSavedData.get(source.m_81377_());
        if (!data.pendingFor(player.m_20148_()).isEmpty()) {
            source.m_81352_(Component.m_237113_("Your inventory may be full, or the pending letter could not be collected yet."));
            return 0;
        }
        source.m_288197_(() -> Component.m_237113_("You have no mail waiting to be collected."), false);
        return 1;
    }

    private static int unread(CommandSourceStack source) {
        ServerPlayer player = source.m_230896_();
        if (player == null) return 0;
        int count = MailSavedData.get(source.m_81377_()).unreadCount(player.m_20148_());
        source.m_288197_(() -> Component.m_237113_("Unread mail: " + count), false);
        return count;
    }

    private static int inbox(CommandSourceStack source) {
        ServerPlayer player = source.m_230896_();
        if (player == null) return 0;
        List<MailRecord> records = MailSavedData.get(source.m_81377_()).forRecipient(player.m_20148_());
        if (records.isEmpty()) {
            source.m_288197_(() -> Component.m_237113_("Your mailbox is empty."), false);
            return 1;
        }
        int shown = Math.min(8, records.size());
        source.m_288197_(() -> Component.m_237113_("Recent mail (" + records.size() + " total):"), false);
        for (int i = 0; i < shown; i++) {
            MailRecord record = records.get(i);
            String state = record.state == MailRecord.READ ? "read"
                    : (record.state == MailRecord.DELIVERED ? "delivered"
                    : (record.state == MailRecord.BOXED ? "letter box" : "waiting"));
            source.m_288197_(() -> Component.m_237113_("- From " + record.senderName + " [" + state + "]"), false);
        }
        return shown;
    }

    /**
     * EasyNPC preset binding entry point. Non-operator players cannot use this command directly.
     * When invoked by the courier NPC, the named @initiator must be an online operator standing
     * within eight blocks, and the exact command-source EasyNPC is bound.
     */
    private static int courierPresetBind(CommandSourceStack source, String playerName) {
        MinecraftServer server = source.m_81377_();
        ServerPlayer player = server.m_6846_().m_11255_(playerName);
        if (player == null) {
            source.m_81352_(Component.m_237113_("The courier setup player must be online."));
            return 0;
        }
        if (!MinecraftRuntime.isOperator(server, player)) {
            player.m_5661_(Component.m_237113_("Only a server operator can bind the Post Office courier."), false);
            return 0;
        }

        Object commandEntity = source.m_81373_();
        boolean sourceIsNpc = MinecraftRuntime.isEasyNpc(commandEntity);
        if (!source.m_6761_(2) && !sourceIsNpc) {
            player.m_5661_(Component.m_237113_("Courier preset binding must be run by an operator or from the EasyNPC courier preset."), false);
            return 0;
        }

        String result;
        if (sourceIsNpc) {
            if (!MinecraftRuntime.sameLevel(commandEntity, player)
                    || MinecraftRuntime.distanceSq(commandEntity, player) > 64.0D) {
                player.m_5661_(Component.m_237113_("Stand within 8 blocks of the courier before using its setup button."), false);
                return 0;
            }
            result = CourierManager.bindEntity(server, commandEntity);
        } else {
            result = CourierManager.bindNearest(server, player);
        }
        player.m_5661_(Component.m_237113_(result), false);
        return result.startsWith("Bound") ? 1 : 0;
    }

    private static int courierBind(CommandSourceStack source) {
        ServerPlayer player = source.m_230896_();
        if (player == null) {
            source.m_81352_(Component.m_237113_("Stand near the EasyNPC postman and run this command as a player."));
            return 0;
        }
        String result = CourierManager.bindNearest(source.m_81377_(), player);
        source.m_288197_(() -> Component.m_237113_(result), false);
        return result.startsWith("Bound") ? 1 : 0;
    }

    private static int courierHome(CommandSourceStack source) {
        return adminResult(source, CourierManager.setHome(source.m_81377_()));
    }

    private static int courierReturn(CommandSourceStack source) {
        return adminResult(source, CourierManager.forceReturn(source.m_81377_()));
    }

    private static int courierClear(CommandSourceStack source) {
        return adminResult(source, CourierManager.clear(source.m_81377_()));
    }

    private static int courierStatus(CommandSourceStack source) {
        return adminResult(source, CourierManager.status(source.m_81377_()));
    }

    private static int discordStatus(CommandSourceStack source) {
        MailSavedData data = MailSavedData.get(source.m_81377_());
        for (String line : DiscordService.statusLines(data)) {
            source.m_288197_(() -> Component.m_237113_(line), false);
        }
        return 1;
    }

    private static int discordPanel(CommandSourceStack source) {
        DiscordService.start();
        MailSavedData data = MailSavedData.get(source.m_81377_());
        boolean queued = DiscordService.ensurePanel(data);
        String text = queued ? "Discord Post Office panel refresh queued." : "Discord Post Office panel could not be queued; use /mail discordstatus.";
        source.m_288197_(() -> Component.m_237113_(text), false);
        return queued ? 1 : 0;
    }

    private static int discordReload(CommandSourceStack source) {
        DiscordService.reload();
        source.m_288197_(() -> Component.m_237113_("Reloaded " + SpectralMailConfig.configPath() + ". Use /mail discordstatus for connection details."), false);
        return 1;
    }

    private static int discordSetup(CommandSourceStack source) {
        source.m_288197_(() -> Component.m_237113_("Spectral Mail Discord setup:"), false);
        source.m_288197_(() -> Component.m_237113_("1. Edit config/spectralmail-server.toml and enable [discord]."), false);
        source.m_288197_(() -> Component.m_237113_("2. Set bot_token and post_office_channel_id; guild_id and role restrictions are optional."), false);
        source.m_288197_(() -> Component.m_237113_("3. The bot only requests the GUILDS gateway intent; Message Content is not required."), false);
        source.m_288197_(() -> Component.m_237113_("4. Run /mail discordreload, then /mail discordpanel."), false);
        return 1;
    }

    private static int adminResult(CommandSourceStack source, String result) {
        source.m_288197_(() -> Component.m_237113_(result), false);
        return 1;
    }
}
