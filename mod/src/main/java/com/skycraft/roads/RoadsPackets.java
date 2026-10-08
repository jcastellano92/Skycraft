package com.skycraft.roads;

import com.skycraft.Skycraft;
import com.skycraft.core.PlayerData;
import com.skycraft.core.SkyData;
import com.skycraft.network.SkyNetwork;
import com.skycraft.roads.client.RoadsClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** Roads packets: nearby undiscovered settlements for the compass (compass source {@code "roads"}). */
@Mod.EventBusSubscriber(modid = Skycraft.MODID)
public final class RoadsPackets {
    private static final int MAX_MARKERS = 12;
    /** Hash of the last marker list sent to each player, to skip identical updates. */
    private static final Map<UUID, Integer> LAST = new HashMap<>();

    private RoadsPackets() {}

    static void register() {
        SkyNetwork.register(SettlementMarkers.class, NetworkDirection.PLAY_TO_CLIENT, SettlementMarkers::encode,
                SettlementMarkers::decode, SettlementMarkers::handle);
    }

    public record Entry(int x, int y, int z, String name) {
    }

    /** The full list of settlement markers the client should show (replaces the previous list). */
    public record SettlementMarkers(int range, List<Entry> entries) {
        static void encode(SettlementMarkers m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.range);
            buf.writeVarInt(m.entries.size());
            for (Entry e : m.entries) {
                buf.writeVarInt(e.x());
                buf.writeVarInt(e.y());
                buf.writeVarInt(e.z());
                buf.writeUtf(e.name(), 64);
            }
        }

        static SettlementMarkers decode(FriendlyByteBuf buf) {
            int range = buf.readVarInt();
            int n = Math.min(buf.readVarInt(), 64);
            List<Entry> list = new ArrayList<>(n);
            for (int i = 0; i < n; i++) list.add(new Entry(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(64)));
            return new SettlementMarkers(range, list);
        }

        static void handle(SettlementMarkers m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> RoadsClient.setMarkers(m.range, m.entries));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Sends every player in the road level the settlements near them that they haven't discovered yet. */
    static void sendMarkers(ServerLevel level, RoadsData data) {
        boolean enabled = RoadsConfig.COMPASS_MARKERS.get();
        int range = RoadsConfig.COMPASS_RANGE.get();
        double reach = (range + 64.0) * (range + 64.0);
        for (ServerPlayer player : level.players()) {
            List<Entry> list = new ArrayList<>();
            if (enabled) {
                PlayerData pd = SkyData.get(player);
                List<Settlement> near = new ArrayList<>();
                for (Settlement s : data.settlements) {
                    if (s.distSq(player.getX(), player.getZ()) <= reach && !SettlementNames.discovered(pd, s)) near.add(s);
                }
                near.sort(Comparator.comparingDouble(s -> s.distSq(player.getX(), player.getZ())));
                for (int i = 0; i < near.size() && i < MAX_MARKERS; i++) {
                    Settlement s = near.get(i);
                    list.add(new Entry(s.x, s.y, s.z, s.name));
                }
            }
            send(player, new SettlementMarkers(range, list));
        }
    }

    private static void send(ServerPlayer player, SettlementMarkers msg) {
        int hash = msg.hashCode();
        Integer last = LAST.get(player.getUUID());
        if (last != null && last == hash) return;
        LAST.put(player.getUUID(), hash);
        SkyNetwork.sendToPlayer(player, msg);
    }

    static void clear() {
        LAST.clear();
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !RoadNetwork.isRoadLevel(player.level())) {
            send(player, new SettlementMarkers(0, List.of()));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST.remove(event.getEntity().getUUID());
    }
}
