package com.season2.dragoncurrency;

import java.text.NumberFormat;
import java.util.Locale;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = DragonCurrency.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BankCommands {
    private BankCommands() {}

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        LiteralArgumentBuilder<CommandSourceStack> balance = LiteralArgumentBuilder.literal("bal");
        balance.executes(context -> showBalance(context.getSource()));
        dispatcher.register(balance);

        LiteralArgumentBuilder<CommandSourceStack> bank = LiteralArgumentBuilder.literal("dragonbank");
        RequiredArgumentBuilder<CommandSourceStack, String> target =
                RequiredArgumentBuilder.argument("player", StringArgumentType.word());
        target.executes(context -> openBank(
                context.getSource(),
                StringArgumentType.getString(context, "player")
        ));
        bank.then(target);
        dispatcher.register(bank);
    }

    private static int showBalance(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("This command can only be used by a player."));
            return 0;
        }
        BankAccount.migrateLegacyPouches(player);
        String formatted = NumberFormat.getIntegerInstance(Locale.US).format(BankAccount.getBalance(player));
        source.sendSuccess(() -> Component.literal("Balance: " + formatted), false);
        return 1;
    }

    private static int openBank(CommandSourceStack source, String playerName) {
        // Normal players cannot invoke the banker GUI themselves. EasyNPC command
        // actions run as the NPC, so they pass this check while targeting @initiator.
        if (source.isPlayer()) {
            source.sendFailure(Component.literal("You must speak to a banker to access the bank."));
            return 0;
        }

        ServerPlayer target = source.getServer().getPlayerList().getPlayerByName(playerName);
        if (target == null) {
            source.sendFailure(Component.literal("Bank customer is not online."));
            return 0;
        }

        BankAccount.migrateLegacyPouches(target);
        BankContainer container = new BankContainer(target);
        // This menu does not need any extra network data, so use Minecraft's
        // normal menu-open packet. IForgeMenuType's factory also supports the
        // vanilla two-argument creation path and passes null for FriendlyByteBuf.
        // This keeps EasyNPC command actions on the same reliable path used by v1.4.0.
        target.openMenu(new SimpleMenuProvider(
                (containerId, inventory, player) -> new BankMenu(containerId, inventory, container),
                Component.translatable("container.dragoncurrency.bank")
        ));
        return 1;
    }
}
