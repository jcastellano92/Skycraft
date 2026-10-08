package com.skycraft.roads;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.skycraft.Skycraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Arrays;

/**
 * {@code /skycraft-roads info | replan | repave | nearest | traveler [kind]} (operators only).
 */
@Mod.EventBusSubscriber(modid = Skycraft.MODID)
public final class RoadsCommands {
    private RoadsCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("skycraft-roads").requires(s -> s.hasPermission(2))
                .then(Commands.literal("info").executes(RoadsCommands::info))
                .then(Commands.literal("replan").executes(RoadsCommands::replan))
                .then(Commands.literal("repave").executes(RoadsCommands::repave))
                .then(Commands.literal("nearest").executes(RoadsCommands::nearest))
                .then(Commands.literal("traveler")
                        .executes(c -> traveler(c, null))
                        .then(Commands.argument("kind", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.asList(Travelers.KINDS), b))
                                .executes(c -> traveler(c, StringArgumentType.getString(c, "kind"))))));
    }

    private static ServerLevel overworld(CommandContext<CommandSourceStack> c) {
        return c.getSource().getServer().overworld();
    }

    private static int info(CommandContext<CommandSourceStack> c) {
        RoadsData data = RoadsData.get(overworld(c));
        int signs = 0;
        for (RoadFeature f : data.features) if (f.type == RoadFeature.SIGNPOST) signs++;
        int lanterns = data.features.size() - signs;
        float km = 0;
        for (Road r : data.roads.values()) km += r.length() / 1000f;
        String length = String.format(java.util.Locale.ROOT, "%.1f", km);
        int s = data.settlements.size(), r = data.roads.size(), p = RoadNetwork.pendingPlans(data), q = Paver.queued(),
                t = Travelers.loadedCount(), signsF = signs;
        c.getSource().sendSuccess(() -> Component.translatable("command.skycraft.roads.info", s, r, length, signsF, lanterns,
                p, q, t), false);
        if (!RoadsConfig.ENABLED.get()) c.getSource().sendSuccess(() -> Component.translatable("command.skycraft.roads.disabled"), false);
        return s;
    }

    private static int replan(CommandContext<CommandSourceStack> c) {
        ServerLevel level = overworld(c);
        RoadsData data = RoadsData.get(level);
        int queued = RoadNetwork.replan(level, data);
        int s = data.settlements.size();
        c.getSource().sendSuccess(() -> Component.translatable("command.skycraft.roads.replan", s, queued), true);
        return queued;
    }

    private static int repave(CommandContext<CommandSourceStack> c) {
        ServerLevel level = overworld(c);
        int n = RoadNetwork.repave(level, RoadsData.get(level));
        c.getSource().sendSuccess(() -> Component.translatable("command.skycraft.roads.repave", n), true);
        return n;
    }

    private static int nearest(CommandContext<CommandSourceStack> c) {
        ServerLevel level = overworld(c);
        RoadsData data = RoadsData.get(level);
        var pos = c.getSource().getPosition();
        Settlement best = null;
        double bestD = Double.MAX_VALUE;
        for (Settlement s : data.settlements) {
            double d = s.distSq(pos.x, pos.z);
            if (d < bestD) {
                bestD = d;
                best = s;
            }
        }
        if (best == null) {
            c.getSource().sendFailure(Component.translatable("command.skycraft.roads.none"));
            return 0;
        }
        Settlement s = best;
        int dist = (int) Math.round(Math.sqrt(bestD));
        long roads = data.roads.values().stream().filter(r -> r.a == s.id || r.b == s.id).count();
        c.getSource().sendSuccess(() -> Component.translatable("command.skycraft.roads.nearest", s.name, dist, s.x, s.z, roads), false);
        return 1;
    }

    private static int traveler(CommandContext<CommandSourceStack> c, String kind) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        if (!(player.level() instanceof ServerLevel level) || !RoadNetwork.isRoadLevel(level)) {
            c.getSource().sendFailure(Component.translatable("command.skycraft.roads.traveler_failed"));
            return 0;
        }
        String forced = kind;
        if (forced == null) {
            java.util.List<String> available = new java.util.ArrayList<>();
            for (String k : Travelers.KINDS) if (Travelers.kindAvailable(level, k)) available.add(k);
            forced = available.get(level.getRandom().nextInt(available.size())); // caravan/courier always available
        }
        String spawned = Travelers.trySpawn(level, RoadsData.get(level), player, forced);
        if (spawned == null) {
            c.getSource().sendFailure(Component.translatable("command.skycraft.roads.traveler_failed"));
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.translatable("command.skycraft.roads.traveler",
                Component.translatable("roads.skycraft.kind." + spawned)), true);
        return 1;
    }
}
