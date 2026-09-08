package com.season2.townlife.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.season2.townlife.data.Resident;
import com.season2.townlife.data.TownLifeSavedData;
import com.season2.townlife.logic.NeedType;
import com.season2.townlife.runtime.TownLifeManager;
import java.util.Comparator;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/** Small recovery/debug surface; normal setup is entirely wand based. */
public final class TownLifeCommands {
    private TownLifeCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("townlife")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("inspectnear").executes(TownLifeCommands::inspectNearest))
                .then(Commands.literal("forgetnear").executes(TownLifeCommands::forgetNearest))
                .then(Commands.literal("help").executes(TownLifeCommands::help)));
    }

    private static int inspectNearest(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        TownLifeSavedData data = TownLifeSavedData.get(ctx.getSource().getLevel());
        Optional<Resident> resident = data.residents().stream()
                .filter(r -> {
                    Entity entity = ctx.getSource().getLevel().getEntity(r.entityUuid());
                    return entity != null && entity.distanceToSqr(player) <= 100D;
                })
                .min(Comparator.comparingDouble(r -> ctx.getSource().getLevel().getEntity(r.entityUuid()).distanceToSqr(player)));
        if (resident.isEmpty()) return fail(ctx, "No Town Life resident is within 10 blocks.");
        Resident r = resident.get();
        ctx.getSource().sendSuccess(() -> Component.literal(r.identityName() + " — Town Life Lite").withStyle(ChatFormatting.GOLD), false);
        ctx.getSource().sendSuccess(() -> Component.literal("Home: " + none(r.homeLocationId()) + " | Job: " + r.jobType().name().toLowerCase().replace('_', ' ')), false);
        ctx.getSource().sendSuccess(() -> Component.literal("Work: " + none(r.workplaceLocationId())
                + " | Activity: " + r.activity().name().toLowerCase().replace('_', ' ')
                + " | Mode: " + TownLifeManager.modeName(r.entityUuid())), false);
        ctx.getSource().sendSuccess(() -> Component.literal("Hunger " + Math.round(r.needs().get(NeedType.HUNGER))
                + " | Energy " + Math.round(r.needs().get(NeedType.ENERGY)) + " | " + r.reason()).withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static int forgetNearest(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        TownLifeSavedData data = TownLifeSavedData.get(ctx.getSource().getLevel());
        Optional<Resident> resident = data.residents().stream()
                .filter(r -> {
                    Entity entity = ctx.getSource().getLevel().getEntity(r.entityUuid());
                    return entity != null && entity.distanceToSqr(player) <= 64D;
                })
                .min(Comparator.comparingDouble(r -> ctx.getSource().getLevel().getEntity(r.entityUuid()).distanceToSqr(player)));
        if (resident.isEmpty()) return fail(ctx, "No Town Life resident is within 8 blocks.");
        Resident r = resident.get();
        data.removeResident(r.entityUuid());
        TownLifeManager.forgetRuntime(r.entityUuid());
        ctx.getSource().sendSuccess(() -> Component.literal("Town Life stopped managing " + r.identityName() + ".")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int help(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal("Town Life Lite: right-click an Easy NPC with the Town Wand, then click a bed or recognised workstation. Commands are only for recovery/debug.")
                .withStyle(ChatFormatting.AQUA), false);
        return 1;
    }

    private static int fail(CommandContext<CommandSourceStack> ctx, String message) {
        ctx.getSource().sendFailure(Component.literal(message));
        return 0;
    }

    private static String none(String value) {
        return value == null || value.isBlank() ? "none" : value;
    }
}
