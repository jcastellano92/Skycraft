package com.skycraft.roads;

import com.skycraft.core.Currency;
import com.skycraft.core.Holds;
import com.skycraft.core.Notifier;
import com.skycraft.dialogue.Dialogue;
import com.skycraft.dialogue.DialogueOption;
import com.skycraft.network.SkyNetwork;
import com.skycraft.vitals.Vitals;
import com.skycraft.world.FastTravelHandler;
import com.skycraft.world.RestManager;
import com.skycraft.world.WorldPackets;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;

/**
 * Skyrim paid horse carriage travel between hold capitals.
 * Carriage drivers and stable masters stationed outside holds offer travel to any hold capital
 * for 20-50 Septims. Fast travels player and any followers/ridden horse, advances time, and plays travel fade.
 */
public final class Carriages {
    private Carriages() {}

    public record HoldDestination(
            String id,
            String nameKey,
            int defaultCost,
            BlockPos approxPos
    ) {}

    public static final List<HoldDestination> DESTINATIONS = List.of(
            new HoldDestination("whiterun", "hold.skycraft.whiterun", 20, new BlockPos(12, 65, 18)),
            new HoldDestination("the_rift", "hold.skycraft.the_rift", 20, new BlockPos(1400, 65, -800)),
            new HoldDestination("the_reach", "hold.skycraft.the_reach", 20, new BlockPos(-1500, 75, 400)),
            new HoldDestination("eastmarch", "hold.skycraft.eastmarch", 20, new BlockPos(1800, 68, 1200)),
            new HoldDestination("haafingar", "hold.skycraft.haafingar", 20, new BlockPos(-800, 72, -1600)),
            new HoldDestination("falkreath", "hold.skycraft.falkreath", 50, new BlockPos(-400, 66, 1100)),
            new HoldDestination("hjaalmarch", "hold.skycraft.hjaalmarch", 50, new BlockPos(-600, 63, -400)),
            new HoldDestination("the_pale", "hold.skycraft.the_pale", 50, new BlockPos(600, 65, -1400)),
            new HoldDestination("winterhold", "hold.skycraft.winterhold", 50, new BlockPos(1600, 70, -1800))
    );

    public static void register() {
        Dialogue.registerProvider(Carriages::addOptions);
    }

    public static boolean isCarriageDriver(LivingEntity npc) {
        if (npc == null || !npc.isAlive()) return false;
        if (npc.getPersistentData().getBoolean("skycraft_carriage_driver")) return true;
        String desc = npc.getType().getDescriptionId().toLowerCase(java.util.Locale.ROOT);
        if (desc.contains("carriage") || desc.contains("driver")) return true;
        if (npc instanceof Villager v && !v.isBaby()) {
            VillagerProfession p = v.getVillagerData().getProfession();
            if (p == VillagerProfession.LEATHERWORKER || p == VillagerProfession.SHEPHERD || p == VillagerProfession.FARMER) {
                return true;
            }
        }
        return npc.getPersistentData().getBoolean("skycraft_stable_master");
    }

    private static void addOptions(ServerPlayer player, LivingEntity npc, List<DialogueOption> out) {
        if (!RoadsConfig.CARRIAGES.get()) return;
        if (!isCarriageDriver(npc)) return;

        String currentHold = Holds.holdAt(player.level(), npc.blockPosition());
        int baseFare = RoadsConfig.CARRIAGE_COST.get();

        for (HoldDestination dest : DESTINATIONS) {
            if (currentHold.startsWith(dest.id())) continue;

            int fare = dest.defaultCost() == 20 ? baseFare : (int) Math.round(baseFare * 2.5);
            out.add(new DialogueOption(
                    "roads.carriage.dest." + dest.id(),
                    Component.translatable("dialogue.skycraft.carriage.destination", Component.translatable(dest.nameKey()), fare),
                    170,
                    (p, n) -> travelToDestination(p, n, dest, fare)
            ));
        }
    }

    private static void travelToDestination(ServerPlayer player, LivingEntity npc, HoldDestination dest, int fare) {
        if (Vitals.inCombat(player)) {
            Dialogue.open(player, npc, Component.translatable("world.skycraft.travel.combat"));
            return;
        }

        if (!Currency.take(player, fare)) {
            Dialogue.open(player, npc, Component.translatable("dialogue.skycraft.carriage.no_gold", fare));
            return;
        }

        ServerLevel level = player.serverLevel();
        BlockPos targetPos = findDestinationArrival(level, dest);

        // Find mounted horse if riding, or nearby followers
        Entity vehicle = player.getVehicle();
        List<Mob> followers = level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(16),
                m -> m.isAlive() && !m.isPassenger() && FastTravelHandler.isFollower(m, player));

        double distance = Math.sqrt(player.blockPosition().distSqr(targetPos));

        player.stopRiding();
        player.teleportTo(level, targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5, player.getYRot(), player.getXRot());
        player.resetFallDistance();

        if (vehicle != null && vehicle.isAlive()) {
            vehicle.teleportTo(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5);
            vehicle.resetFallDistance();
            player.startRiding(vehicle, true);
        }

        int i = 0;
        for (Mob mob : followers) {
            double angle = i++ * (Math.PI * 2 / Math.max(1, followers.size()));
            double fx = targetPos.getX() + 0.5 + Math.cos(angle) * 1.5;
            double fz = targetPos.getZ() + 0.5 + Math.sin(angle) * 1.5;
            mob.getNavigation().stop();
            mob.teleportTo(fx, targetPos.getY(), fz);
            mob.resetFallDistance();
        }

        // Advance time for carriage journey
        int hours = 0;
        int perHour = com.skycraft.world.WorldConfig.BLOCKS_PER_TRAVEL_HOUR.get();
        long online = player.server.getPlayerList().getPlayers().stream().filter(p -> !p.isSpectator()).count();
        if (perHour > 0 && online <= 1) {
            hours = Mth.clamp((int) Math.round(distance / perHour), 1, 48);
            RestManager.advanceTime(player.server, hours * (long) RestManager.TICKS_PER_HOUR);
        }

        SkyNetwork.sendToPlayer(player, new WorldPackets.RestFade(hours, WorldPackets.RestFade.TRAVEL));
        Notifier.message(player, hours > 0
                ? Component.translatable("dialogue.skycraft.carriage.arrived_hours", Component.translatable(dest.nameKey()), hours)
                : Component.translatable("dialogue.skycraft.carriage.arrived", Component.translatable(dest.nameKey())));
    }

    private static BlockPos findDestinationArrival(ServerLevel level, HoldDestination dest) {
        RoadsData data = RoadsData.get(level);
        for (Settlement s : data.settlements()) {
            String sHold = Holds.holdAt(level, new BlockPos(s.x, s.y, s.z));
            if (sHold.startsWith(dest.id())) {
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, s.x, s.z);
                return new BlockPos(s.x, y, s.z);
            }
        }

        // Fallback to approximate hold position
        BlockPos approx = dest.approxPos();
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, approx.getX(), approx.getZ());
        return new BlockPos(approx.getX(), y, approx.getZ());
    }
}
