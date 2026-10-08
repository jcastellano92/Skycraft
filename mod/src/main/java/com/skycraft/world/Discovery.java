package com.skycraft.world;

import com.skycraft.Skycraft;
import com.skycraft.core.Holds;
import com.skycraft.core.Notifier;
import com.skycraft.core.PlayerData;
import com.skycraft.core.SkyData;
import com.skycraft.network.NotifyKind;
import com.skycraft.network.SkyNetwork;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server side of exploration: structure discovery ("DISCOVERED: Bleakwind Barrow"), "CLEARED" when the last
 * hostile inside a discovered location dies, hold border crossings, and the first entry into Oblivion/Sovngarde.
 * Every player discovers individually (multiplayer-first).
 */
@Mod.EventBusSubscriber(modid = Skycraft.MODID)
public final class Discovery {
    private static final int CHECK_INTERVAL = 40;
    private static final Map<UUID, String> LAST_HOLD = new HashMap<>();

    private Discovery() {}

    /**
     * Contract 6 (docs/PLAYTEST-1.md): marks the nearest location this player hasn't discovered as known (shown
     * with its name on the map and compass, but not discovered). Returns its name, or null if there is none.
     *
     * <p>STUB: workstream C replaces the body; the signature is fixed.
     */
    @javax.annotation.Nullable
    public static Component revealNear(ServerPlayer player, BlockPos center, int radius) {
        return null;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        if (player.isSpectator()) return;
        // spread the work of many players over different ticks
        if ((player.tickCount + (player.getId() & 31)) % CHECK_INTERVAL != 0) return;
        try {
            if (WorldConfig.DISCOVERY.get()) checkStructures(player);
            checkHold(player);
        } catch (RuntimeException e) {
            Skycraft.LOGGER.debug("Skycraft discovery check failed for {}", player.getName().getString(), e);
        }
    }

    // ------------------------------------------------------------------ structures

    private static void checkStructures(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        BlockPos pos = player.blockPosition();
        if (!level.isLoaded(pos)) return;
        StructureManager structures = level.structureManager();
        Map<Structure, LongSet> refs = structures.getAllStructuresAt(pos);
        if (refs.isEmpty()) return;
        Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        PlayerData data = SkyData.get(player);
        for (Structure structure : refs.keySet()) {
            ResourceLocation key = registry.getKey(structure);
            if (key == null) continue;
            LocationKind kind = LocationKind.classify(key, level.dimension());
            if (kind == null) continue;
            StructureStart start = kind.insidePieces
                    ? structures.getStructureWithPieceAt(pos, structure)
                    : structures.getStructureAt(pos, structure);
            if (start == null || !start.isValid()) continue;
            String id = level.dimension().location() + "|" + key + "|" + start.getChunkPos().x + "," + start.getChunkPos().z;
            if (WorldData.find(data, id) != null) continue;
            discover(player, data, id, kind, key, start.getBoundingBox());
        }
    }

    private static void discover(ServerPlayer player, PlayerData data, String id, LocationKind kind, ResourceLocation key, BoundingBox box) {
        String name = LocationNames.generate(id, kind, key.getPath());
        BlockPos center = box.getCenter();
        CompoundTag loc = new CompoundTag();
        loc.putString("id", id);
        loc.putString("name", name);
        loc.putString("type", kind.id);
        loc.putInt("x", center.getX());
        loc.putInt("y", kind.underground() ? player.getBlockY() : center.getY());
        loc.putInt("z", center.getZ());
        loc.putString("dim", player.level().dimension().location().toString());
        loc.putLong("t", player.level().getGameTime());
        loc.putInt("ax", player.getBlockX());
        loc.putInt("ay", player.getBlockY());
        loc.putInt("az", player.getBlockZ());
        loc.putIntArray("box", new int[]{box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()});
        loc.putString("structure", key.toString());
        ListTag list = WorldData.discovered(data);
        list.add(loc);
        data.addStat("locations_discovered", 1);
        data.markDirty();
        Notifier.send(player, NotifyKind.LOCATION_DISCOVERED, Component.literal(name), kind.displayName());
        SkyNetwork.sendToPlayer(player, new WorldPackets.Cue(WorldPackets.Cue.DISCOVERY));
    }

    // ------------------------------------------------------------------ cleared

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || !(victim instanceof Enemy)) return;
        ServerLevel level = (ServerLevel) victim.level();
        String dim = level.dimension().location().toString();
        // everyone nearby who discovered the place shares the clear (party play)
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(victim) > 64 * 64) continue;
            PlayerData data = SkyData.get(player);
            ListTag list = WorldData.discovered(data);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag loc = list.getCompound(i);
                if (loc.getBoolean("cleared") || !loc.getString("dim").equals(dim)) continue;
                LocationKind kind = LocationKind.byId(loc.getString("type"));
                if (!kind.clearable || !WorldData.inBox(loc, victim.getX(), victim.getY(), victim.getZ(), 6)) continue;
                if (hostilesRemain(level, loc, victim)) continue;
                loc.putBoolean("cleared", true);
                data.addStat("locations_cleared", 1);
                data.markDirty();
                Notifier.send(player, NotifyKind.LOCATION_CLEARED, Component.literal(loc.getString("name")), kind.displayName());
            }
        }
    }

    private static boolean hostilesRemain(ServerLevel level, CompoundTag loc, LivingEntity victim) {
        int[] b = loc.getIntArray("box");
        AABB box = new AABB(b[0] - 4, b[1] - 4, b[2] - 4, b[3] + 5, b[4] + 5, b[5] + 5);
        AABB near = victim.getBoundingBox().inflate(48);
        AABB search = box.intersect(near);
        return !level.getEntitiesOfClass(Mob.class, search, m -> m != victim && m.isAlive() && m instanceof Enemy).isEmpty();
    }

    // ------------------------------------------------------------------ holds

    private static void checkHold(ServerPlayer player) {
        if (player.level().dimension() != Level.OVERWORLD) {
            LAST_HOLD.remove(player.getUUID());
            return;
        }
        String hold = Holds.holdAt(player.level(), player.blockPosition());
        String last = LAST_HOLD.put(player.getUUID(), hold);
        if (hold.equals(last)) return;
        PlayerData data = SkyData.get(player);
        if (!WorldData.hasVisitedHold(data, hold)) {
            WorldData.addVisitedHold(data, hold);
            Notifier.send(player, NotifyKind.LOCATION_DISCOVERED, Holds.displayName(hold), Component.translatable("world.skycraft.hold"));
        } else if (last != null) {
            Notifier.message(player, Component.translatable("world.skycraft.hold.entering", Holds.displayName(hold)));
        }
    }

    // ------------------------------------------------------------------ realms

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        LAST_HOLD.remove(player.getUUID());
        PlayerData data = SkyData.get(player);
        CompoundTag root = WorldData.root(data);
        if (event.getTo() == Level.NETHER) {
            realm(player, data, root, "seen_nether", "oblivion");
        } else if (event.getTo() == Level.END) {
            realm(player, data, root, "seen_end", "sovngarde");
        }
    }

    private static void realm(ServerPlayer player, PlayerData data, CompoundTag root, String flag, String name) {
        if (!root.getBoolean(flag)) {
            root.putBoolean(flag, true);
            data.markDirty();
            Notifier.title(player, Component.translatable("world.skycraft.realm." + name), Component.translatable("world.skycraft.realm." + name + ".sub"));
        } else {
            Notifier.message(player, Component.translatable("world.skycraft.realm.entering", Component.translatable("world.skycraft.realm." + name + ".name")));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_HOLD.remove(event.getEntity().getUUID());
    }
}
