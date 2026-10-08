package com.skycraft.society;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * {@code /skycraft-society npc <role> | encounter [kind] | squad [faction] | rep <player> [faction value]}
 * (operators only).
 */
final class SocietyCommands {
    private SocietyCommands() {}

    static void register(CommandDispatcher<CommandSourceStack> d) {
        List<String> roles = new ArrayList<>();
        for (NpcRole r : NpcRole.VALUES) roles.add(r.id);
        List<String> kinds = new ArrayList<>();
        for (Encounters.Kind k : Encounters.Kind.values()) kinds.add(k.id());
        List<String> factions = Arrays.asList(Reputation.FACTIONS);

        d.register(Commands.literal("skycraft-society").requires(s -> s.hasPermission(2))
                .then(Commands.literal("npc")
                        .then(Commands.argument("role", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(roles, b))
                                .executes(SocietyCommands::npc)))
                .then(Commands.literal("encounter")
                        .executes(c -> encounter(c, null))
                        .then(Commands.argument("kind", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(kinds, b))
                                .executes(c -> encounter(c, StringArgumentType.getString(c, "kind")))))
                .then(Commands.literal("squad")
                        .executes(c -> squad(c, null))
                        .then(Commands.argument("faction", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(factions, b))
                                .executes(c -> squad(c, StringArgumentType.getString(c, "faction")))))
                .then(Commands.literal("rep")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(SocietyCommands::showRep)
                                .then(Commands.argument("faction", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(factions, b))
                                        .then(Commands.argument("value", IntegerArgumentType.integer(Reputation.MIN, Reputation.MAX))
                                                .executes(SocietyCommands::setRep))))));
    }

    private static int npc(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        String role = StringArgumentType.getString(c, "role");
        Mob mob = Npcs.spawn(player.serverLevel(), BlockPos.containing(c.getSource().getPosition()), role);
        if (mob == null) {
            c.getSource().sendFailure(Component.translatable("command.skycraft.society.unknown", role));
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.translatable("command.skycraft.society.npc", mob.getDisplayName()), true);
        return 1;
    }

    private static int encounter(CommandContext<CommandSourceStack> c, String kindId) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        Encounters.Kind kind = kindId == null ? null : Encounters.Kind.byId(kindId);
        if (kindId != null && kind == null) {
            c.getSource().sendFailure(Component.translatable("command.skycraft.society.unknown", kindId));
            return 0;
        }
        Encounters.Kind spawned = kind != null ? Encounters.trySpawn(player, kind) : Encounters.trySpawn(player, null);
        if (spawned == null) {
            c.getSource().sendFailure(Component.translatable("command.skycraft.society.encounter_failed"));
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.translatable("command.skycraft.society.encounter", spawned.id()), true);
        return 1;
    }

    private static int squad(CommandContext<CommandSourceStack> c, String faction) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        if (faction != null && HitSquads.roleFor(faction) == null) {
            c.getSource().sendFailure(Component.translatable("command.skycraft.society.unknown", faction));
            return 0;
        }
        String sent = HitSquads.send(player, faction);
        if (sent == null) {
            c.getSource().sendFailure(Component.translatable("command.skycraft.society.squad_failed"));
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.translatable("command.skycraft.society.squad", Reputation.factionName(sent)), true);
        return 1;
    }

    private static int showRep(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(c, "player");
        MutableComponent out = Component.translatable("command.skycraft.society.rep_header", player.getDisplayName());
        for (String f : Reputation.FACTIONS) {
            int v = Reputation.get(player, f);
            out.append("\n").append(Component.translatable("command.skycraft.society.rep_line", Reputation.factionName(f), v,
                    Reputation.Tier.of(v).displayName()));
        }
        c.getSource().sendSuccess(() -> out, false);
        return 1;
    }

    private static int setRep(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(c, "player");
        String faction = StringArgumentType.getString(c, "faction");
        if (!Reputation.isFaction(faction)) {
            c.getSource().sendFailure(Component.translatable("command.skycraft.society.unknown", faction));
            return 0;
        }
        int value = IntegerArgumentType.getInteger(c, "value");
        Reputation.set(player, faction, value);
        c.getSource().sendSuccess(() -> Component.translatable("command.skycraft.society.rep_set", player.getDisplayName(),
                Reputation.factionName(faction), value), true);
        return 1;
    }
}
