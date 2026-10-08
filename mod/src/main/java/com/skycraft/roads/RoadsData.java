package com.skycraft.roads;

import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Per-dimension road network (only the overworld is used): settlements, roads, roadside features and the set of
 * chunks that are already paved. Spatial indexes are transient and rebuilt on load.
 */
public final class RoadsData extends SavedData {
    public static final String NAME = "skycraft_roads";
    /** Road points are indexed into every chunk their footprint (this many blocks around the point) touches. */
    static final int FOOTPRINT = 3;
    /** Size (log2 blocks) of the regions used to look up settlement zones. */
    private static final int ZONE_SHIFT = 8;

    final List<Settlement> settlements = new ArrayList<>();
    final Int2ObjectOpenHashMap<Settlement> settlementById = new Int2ObjectOpenHashMap<>();
    final Long2ObjectOpenHashMap<Settlement> settlementByKey = new Long2ObjectOpenHashMap<>();
    final Long2ObjectOpenHashMap<List<Settlement>> settlementByCenterChunk = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<List<Settlement>> zoneIndex = new Long2ObjectOpenHashMap<>();
    private int zoneIndexRadius = -1;

    final Int2ObjectLinkedOpenHashMap<Road> roads = new Int2ObjectLinkedOpenHashMap<>();
    final List<RoadFeature> features = new ArrayList<>();
    final Long2ObjectOpenHashMap<List<RoadFeature>> featuresByChunk = new Long2ObjectOpenHashMap<>();
    /** chunk -> encoded (roadId << 32 | pointIndex) for every point whose footprint touches the chunk. */
    final Long2ObjectOpenHashMap<LongArrayList> chunkIndex = new Long2ObjectOpenHashMap<>();
    /** Coarse (8-block) planner cells that already carry a road: new roads are encouraged to merge into them. */
    final LongOpenHashSet roadCells = new LongOpenHashSet();
    final LongOpenHashSet paved = new LongOpenHashSet();
    /** Settlement pairs that are connected or waiting to be planned. */
    final LongOpenHashSet pairs = new LongOpenHashSet();
    /** Pairs waiting to be planned (FIFO). */
    final ArrayDeque<Long> pending = new ArrayDeque<>();
    /** Pair currently being planned on the background thread (-1 = none); saved back into pending. */
    long inFlight = -1;
    int nextSettlementId = 1;
    int nextRoadId = 1;
    /** Bumped by replanning so results from older plans are discarded. Not saved. */
    int generation;

    public static RoadsData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(RoadsData::load, RoadsData::new, NAME);
    }

    public RoadsData() {
    }

    // ------------------------------------------------------------------ keys

    static long pairKey(int a, int b) {
        int lo = Math.min(a, b);
        int hi = Math.max(a, b);
        return ((long) lo << 32) | (hi & 0xffffffffL);
    }

    static int pairA(long key) {
        return (int) (key >> 32);
    }

    static int pairB(long key) {
        return (int) key;
    }

    static long entry(int roadId, int index) {
        return ((long) roadId << 32) | (index & 0xffffffffL);
    }

    static int entryRoad(long e) {
        return (int) (e >> 32);
    }

    static int entryIndex(long e) {
        return (int) e;
    }

    static long cellKey(int blockX, int blockZ) {
        return ChunkPos.asLong(Math.floorDiv(blockX, RoadPlanner.CELL), Math.floorDiv(blockZ, RoadPlanner.CELL));
    }

    // ------------------------------------------------------------------ settlements

    Settlement addSettlement(Settlement s) {
        settlements.add(s);
        settlementById.put(s.id, s);
        settlementByKey.put(s.key, s);
        settlementByCenterChunk.computeIfAbsent(ChunkPos.asLong(s.x >> 4, s.z >> 4), k -> new ArrayList<>()).add(s);
        if (zoneIndexRadius >= 0) indexZone(s, zoneIndexRadius);
        setDirty();
        return s;
    }

    @Nullable
    public Settlement settlement(int id) {
        return settlementById.get(id);
    }

    public List<Settlement> settlements() {
        return Collections.unmodifiableList(settlements);
    }

    private void indexZone(Settlement s, int radius) {
        int x0 = s.zoneMinX(radius) >> ZONE_SHIFT, x1 = s.zoneMaxX(radius) >> ZONE_SHIFT;
        int z0 = s.zoneMinZ(radius) >> ZONE_SHIFT, z1 = s.zoneMaxZ(radius) >> ZONE_SHIFT;
        for (int rx = x0; rx <= x1; rx++) {
            for (int rz = z0; rz <= z1; rz++) {
                zoneIndex.computeIfAbsent(ChunkPos.asLong(rx, rz), k -> new ArrayList<>(2)).add(s);
            }
        }
    }

    private void ensureZoneIndex() {
        int radius = RoadsConfig.SETTLEMENT_RADIUS.get();
        if (radius == zoneIndexRadius) return;
        zoneIndex.clear();
        zoneIndexRadius = radius;
        for (Settlement s : settlements) indexZone(s, radius);
    }

    /** The settlement whose zone (no-paving area) contains this column, if any. */
    @Nullable
    public Settlement zoneAt(int x, int z) {
        ensureZoneIndex();
        List<Settlement> list = zoneIndex.get(ChunkPos.asLong(x >> ZONE_SHIFT, z >> ZONE_SHIFT));
        if (list == null) return null;
        for (Settlement s : list) if (s.inZone(x, z, zoneIndexRadius)) return s;
        return null;
    }

    /** Settlements whose zone may overlap the given chunk. */
    List<Settlement> zonesNearChunk(int cx, int cz) {
        ensureZoneIndex();
        List<Settlement> out = new ArrayList<>(2);
        int bx = cx << 4, bz = cz << 4;
        int r0x = bx >> ZONE_SHIFT, r1x = (bx + 15) >> ZONE_SHIFT;
        int r0z = bz >> ZONE_SHIFT, r1z = (bz + 15) >> ZONE_SHIFT;
        for (int rx = r0x; rx <= r1x; rx++) {
            for (int rz = r0z; rz <= r1z; rz++) {
                List<Settlement> list = zoneIndex.get(ChunkPos.asLong(rx, rz));
                if (list == null) continue;
                for (Settlement s : list) {
                    if (out.contains(s)) continue;
                    if (s.zoneMaxX(zoneIndexRadius) < bx || s.zoneMinX(zoneIndexRadius) > bx + 15
                            || s.zoneMaxZ(zoneIndexRadius) < bz || s.zoneMinZ(zoneIndexRadius) > bz + 15) continue;
                    out.add(s);
                }
            }
        }
        return out;
    }

    int zoneRadius() {
        ensureZoneIndex();
        return zoneIndexRadius;
    }

    // ------------------------------------------------------------------ roads

    Road addRoad(int a, int b, int[] xs, int[] zs, byte[] flags) {
        Road road = new Road(nextRoadId++, a, b, xs, zs, flags);
        roads.put(road.id, road);
        pairs.add(pairKey(a, b));
        setDirty();
        return road;
    }

    @Nullable
    public Road road(int id) {
        return roads.get(id);
    }

    /** Adds the road's points to the chunk index and returns every chunk they touch. */
    LongOpenHashSet indexRoad(Road road) {
        LongOpenHashSet touched = new LongOpenHashSet();
        for (int i = 0; i < road.size(); i++) {
            int x = road.xs[i], z = road.zs[i];
            int cx0 = (x - FOOTPRINT) >> 4, cx1 = (x + FOOTPRINT) >> 4;
            int cz0 = (z - FOOTPRINT) >> 4, cz1 = (z + FOOTPRINT) >> 4;
            for (int cx = cx0; cx <= cx1; cx++) {
                for (int cz = cz0; cz <= cz1; cz++) {
                    long c = ChunkPos.asLong(cx, cz);
                    chunkIndex.computeIfAbsent(c, k -> new LongArrayList()).add(entry(road.id, i));
                    touched.add(c);
                }
            }
            roadCells.add(cellKey(x, z));
        }
        return touched;
    }

    /** Registers a feature and returns its chunk. */
    long addFeature(RoadFeature f) {
        features.add(f);
        long c = ChunkPos.asLong(f.x >> 4, f.z >> 4);
        featuresByChunk.computeIfAbsent(c, k -> new ArrayList<>(1)).add(f);
        setDirty();
        return c;
    }

    /** The nearest signpost within {@code radius} blocks, if any. */
    @Nullable
    RoadFeature signpostNear(int x, int z, int radius) {
        RoadFeature best = null;
        long bestD = (long) radius * radius;
        for (int cx = (x - radius) >> 4; cx <= (x + radius) >> 4; cx++) {
            for (int cz = (z - radius) >> 4; cz <= (z + radius) >> 4; cz++) {
                List<RoadFeature> list = featuresByChunk.get(ChunkPos.asLong(cx, cz));
                if (list == null) continue;
                for (RoadFeature f : list) {
                    if (f.type != RoadFeature.SIGNPOST) continue;
                    long dx = f.x - x, dz = f.z - z;
                    long d = dx * dx + dz * dz;
                    if (d <= bestD) {
                        bestD = d;
                        best = f;
                    }
                }
            }
        }
        return best;
    }

    /** True if this chunk has road points or features that are not paved yet. */
    boolean needsPaving(long chunk) {
        return !paved.contains(chunk) && (chunkIndex.containsKey(chunk) || featuresByChunk.containsKey(chunk));
    }

    /** Forgets all roads, features and paving state (settlements are kept). Placed blocks stay in the world. */
    void clearRoads() {
        roads.clear();
        features.clear();
        featuresByChunk.clear();
        chunkIndex.clear();
        roadCells.clear();
        paved.clear();
        pairs.clear();
        pending.clear();
        inFlight = -1;
        generation++;
        setDirty();
    }

    // ------------------------------------------------------------------ persistence

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag s = new ListTag();
        for (Settlement st : settlements) s.add(st.save());
        tag.put("settlements", s);
        ListTag r = new ListTag();
        for (Road road : roads.values()) r.add(road.save());
        tag.put("roads", r);
        ListTag f = new ListTag();
        for (RoadFeature feature : features) f.add(feature.save());
        tag.put("features", f);
        tag.putLongArray("paved", paved.toLongArray());
        LongArrayList pend = new LongArrayList();
        if (inFlight != -1) pend.add(inFlight);
        for (Long p : pending) pend.add(p.longValue());
        tag.putLongArray("pending", pend.toLongArray());
        tag.putInt("nextSettlement", nextSettlementId);
        tag.putInt("nextRoad", nextRoadId);
        return tag;
    }

    public static RoadsData load(CompoundTag tag) {
        RoadsData data = new RoadsData();
        data.nextSettlementId = Math.max(1, tag.getInt("nextSettlement"));
        data.nextRoadId = Math.max(1, tag.getInt("nextRoad"));
        ListTag s = tag.getList("settlements", Tag.TAG_COMPOUND);
        for (int i = 0; i < s.size(); i++) {
            Settlement st = Settlement.load(s.getCompound(i));
            if (st == null || data.settlementByKey.containsKey(st.key)) continue;
            data.addSettlement(st);
            data.nextSettlementId = Math.max(data.nextSettlementId, st.id + 1);
        }
        ListTag r = tag.getList("roads", Tag.TAG_COMPOUND);
        for (int i = 0; i < r.size(); i++) {
            Road road = Road.load(r.getCompound(i));
            if (road == null) continue;
            data.roads.put(road.id, road);
            data.pairs.add(pairKey(road.a, road.b));
            data.indexRoad(road);
            data.nextRoadId = Math.max(data.nextRoadId, road.id + 1);
        }
        ListTag f = tag.getList("features", Tag.TAG_COMPOUND);
        for (int i = 0; i < f.size(); i++) data.addFeature(RoadFeature.load(f.getCompound(i)));
        for (long c : tag.getLongArray("paved")) data.paved.add(c);
        for (long p : tag.getLongArray("pending")) {
            if (data.pairs.add(p)) data.pending.add(p);
        }
        data.setDirty(false);
        return data;
    }
}
