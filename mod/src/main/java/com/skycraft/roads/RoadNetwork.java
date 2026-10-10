package com.skycraft.roads;

import com.skycraft.Skycraft;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Orchestrates the road network on the server: settlement detection from loaded chunks, road planning on a
 * background thread, and feeding loaded road chunks to the {@link Paver}. Only the overworld has roads.
 *
 * <p>The chunk load event only records what it saw in thread-safe queues; all world access happens in the level
 * tick on the server thread.</p>
 */
@Mod.EventBusSubscriber(modid = Skycraft.MODID)
public final class RoadNetwork {
    public static final TagKey<Structure> SETTLEMENTS =
            TagKey.create(Registries.STRUCTURE, new ResourceLocation(Skycraft.MODID, "settlements"));

    private record FoundStart(Structure structure, long key, BoundingBox box) {
    }

    private static final ConcurrentLinkedQueue<FoundStart> FOUND = new ConcurrentLinkedQueue<>();
    private static final ConcurrentLinkedQueue<Long> LOADED = new ConcurrentLinkedQueue<>();
    private static final ConcurrentLinkedQueue<RoadPlanner.Result> RESULTS = new ConcurrentLinkedQueue<>();
    private static ExecutorService planner;
    private static Future<?> running;
    private static long tick;

    private RoadNetwork() {}

    static boolean isRoadLevel(Level level) {
        return level instanceof ServerLevel && level.dimension() == Level.OVERWORLD;
    }

    // ------------------------------------------------------------------ events

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;
        if (!RoadsConfig.ENABLED.get()) return;
        LOADED.add(chunk.getPos().toLong());
        Map<Structure, StructureStart> starts = chunk.getAllStarts();
        if (starts.isEmpty()) return;
        for (Map.Entry<Structure, StructureStart> e : starts.entrySet()) {
            StructureStart start = e.getValue();
            if (start == null || !start.isValid()) continue;
            FOUND.add(new FoundStart(e.getKey(), start.getChunkPos().toLong(), start.getBoundingBox()));
        }
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.side != LogicalSide.SERVER) return;
        if (!(event.level instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        tick++;
        if (!RoadsConfig.ENABLED.get()) {
            FOUND.clear();
            LOADED.clear();
            return;
        }
        RoadsData data = RoadsData.get(level);
        try {
            drainFound(level, data);
            drainLoaded(level, data);
            applyResults(level, data);
            if (tick % RoadsConfig.PLAN_INTERVAL_TICKS.get() == 0) startPlanning(level, data);
            Paver.tick(level, data, tick);
            Travelers.tick(level, data, tick);
            if (tick % 100 == 13) clearRoadSnow(level, data);
            if (tick % 200 == 17) RoadsPackets.sendMarkers(level, data);
        } catch (RuntimeException e) {
            Skycraft.LOGGER.error("Skycraft roads tick failed", e);
        }
    }

    /** Clears snow settling on active road chunks near players so the road surface is always visible. */
    private static void clearRoadSnow(ServerLevel level, RoadsData data) {
        for (net.minecraft.server.level.ServerPlayer player : level.players()) {
            ChunkPos cp = player.chunkPosition();
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    long c = ChunkPos.asLong(cp.x + dx, cp.z + dz);
                    if (data.paved.contains(c)) {
                        LevelChunk chunk = level.getChunkSource().getChunkNow(cp.x + dx, cp.z + dz);
                        if (chunk == null) continue;
                        for (int bx = 0; bx < 16; bx += 2) {
                            for (int bz = 0; bz < 16; bz += 2) {
                                int x = (cp.x + dx) * 16 + bx;
                                int z = (cp.z + dz) * 16 + bz;
                                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                                net.minecraft.core.BlockPos above = new net.minecraft.core.BlockPos(x, y - 1, z);
                                if (level.getBlockState(above).is(net.minecraft.world.level.block.Blocks.SNOW)) {
                                    net.minecraft.world.level.block.state.BlockState ground = level.getBlockState(above.below());
                                    if (Paver.isRoadMaterial(ground)) {
                                        level.setBlock(above, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        if (planner != null) {
            planner.shutdownNow();
            planner = null;
        }
        running = null;
        FOUND.clear();
        LOADED.clear();
        RESULTS.clear();
        Paver.clear();
        Travelers.clear();
        RoadsPackets.clear();
    }

    // ------------------------------------------------------------------ settlements

    private static void drainFound(ServerLevel level, RoadsData data) {
        if (FOUND.isEmpty()) return;
        Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        FoundStart f;
        while ((f = FOUND.poll()) != null) {
            if (data.settlementByKey.containsKey(f.key())) continue;
            Optional<ResourceKey<Structure>> key = registry.getResourceKey(f.structure());
            if (key.isEmpty()) continue;
            boolean settlement = registry.getHolder(key.get()).map(h -> h.is(SETTLEMENTS)).orElse(false);
            if (!settlement) {
                String path = key.get().location().getPath().toLowerCase();
                String ns = key.get().location().getNamespace().toLowerCase();
                if (path.contains("village") || path.contains("town") || path.contains("tavern") || path.contains("settlement")
                        || path.contains("inn") || path.contains("hamlet") || path.contains("outpost")
                        || ns.equals("ctov") || ns.equals("towns_and_towers") || ns.equals("dungeons_and_taverns")) {
                    settlement = true;
                }
            }
            if (!settlement) continue;
            register(level, data, f, key.get().location());
        }
    }

    private static void register(ServerLevel level, RoadsData data, FoundStart f, ResourceLocation structure) {
        BoundingBox box = f.box();
        ChunkPos startChunk = new ChunkPos(f.key());
        String locationId = SettlementNames.locationId(level.dimension(), structure, startChunk);
        String name = SettlementNames.name(level.dimension(), structure, locationId);
        int cx = (box.minX() + box.maxX()) / 2;
        int cz = (box.minZ() + box.maxZ()) / 2;
        int cy = (box.minY() + box.maxY()) / 2;
        Settlement s = new Settlement(data.nextSettlementId++, f.key(), cx, cy, cz, box.minX(), box.minZ(), box.maxX(),
                box.maxZ(), name, structure.toString(), locationId, false);
        data.addSettlement(s);
        resolveY(level, s);
        Skycraft.LOGGER.debug("Skycraft roads: settlement {} ({}) at {}, {}", s.name, structure, s.x, s.z);
        planConnections(data, s);
    }

    private static void resolveY(ServerLevel level, Settlement s) {
        if (s.yResolved || level.getChunkSource().getChunkNow(s.x >> 4, s.z >> 4) == null) return;
        s.y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, s.x, s.z);
        s.yResolved = true;
    }

    /** Queues roads from {@code s} to its nearest settlements (or the nearest one at all if it would be isolated). */
    static int planConnections(RoadsData data, Settlement s) {
        int maxDist = RoadsConfig.MAX_DISTANCE.get();
        int wanted = RoadsConfig.CONNECTIONS.get();
        int minDist = data.zoneRadius() * 2;
        List<Settlement> others = new ArrayList<>(data.settlements);
        others.remove(s);
        if (others.isEmpty()) return 0;
        others.sort(Comparator.comparingDouble(o -> s.distSq(o.x + 0.5, o.z + 0.5)));
        int linked = 0, queued = 0;
        for (Settlement o : others) {
            if (linked >= wanted) break;
            double d = Math.sqrt(s.distSq(o.x + 0.5, o.z + 0.5));
            if (d > maxDist) break;
            linked++;
            if (d < minDist) continue; // practically the same place
            if (queue(data, s, o)) queued++;
        }
        if (linked == 0) {
            Settlement nearest = others.get(0);
            if (Math.sqrt(s.distSq(nearest.x + 0.5, nearest.z + 0.5)) <= maxDist * 2.0 && queue(data, s, nearest)) queued++;
        }
        queued += bridgeComponents(data, s, others, maxDist * 3.0);
        return queued;
    }

    /**
     * No orphan towns: if {@code s}'s connected group does not reach every known settlement, links the group to the
     * nearest settlement of another group (within {@code limit} blocks).
     */
    private static int bridgeComponents(RoadsData data, Settlement s, List<Settlement> others, double limit) {
        java.util.Map<Integer, Integer> parent = new java.util.HashMap<>();
        for (Settlement o : data.settlements) parent.put(o.id, o.id);
        for (long pair : data.pairs) {
            int ra = find(parent, RoadsData.pairA(pair)), rb = find(parent, RoadsData.pairB(pair));
            if (ra != rb) parent.put(ra, rb);
        }
        int mine = find(parent, s.id);
        Settlement best = null;
        Settlement from = null;
        double bestD = Double.MAX_VALUE;
        // nearest pair between s's group and any other group
        for (Settlement a : data.settlements) {
            if (find(parent, a.id) != mine) continue;
            for (Settlement o : others) {
                if (find(parent, o.id) == mine) continue;
                double d = a.distSq(o.x + 0.5, o.z + 0.5);
                if (d < bestD) {
                    bestD = d;
                    best = o;
                    from = a;
                }
            }
        }
        if (best == null || Math.sqrt(bestD) > limit) return 0;
        return queue(data, from, best) ? 1 : 0;
    }

    private static int find(java.util.Map<Integer, Integer> parent, int x) {
        int root = x;
        while (parent.getOrDefault(root, root) != root) root = parent.get(root);
        return root;
    }

    private static boolean queue(RoadsData data, Settlement a, Settlement b) {
        long key = RoadsData.pairKey(a.id, b.id);
        if (!data.pairs.add(key)) return false;
        data.pending.add(key);
        data.setDirty();
        return true;
    }

    // ------------------------------------------------------------------ chunks

    private static void drainLoaded(ServerLevel level, RoadsData data) {
        Long c;
        while ((c = LOADED.poll()) != null) {
            long chunk = c;
            if (data.needsPaving(chunk)) Paver.enqueue(chunk);
            List<Settlement> centers = data.settlementByCenterChunk.get(chunk);
            if (centers != null) {
                for (Settlement s : centers) {
                    if (!s.yResolved) {
                        resolveY(level, s);
                        data.setDirty();
                    }
                }
            }
        }
    }

    /** Re-queues every loaded chunk that still needs paving (after roads changed). */
    static void queueLoaded(ServerLevel level, RoadsData data, LongOpenHashSet chunks) {
        for (long c : chunks) {
            data.paved.remove(c);
            if (level.getChunkSource().getChunkNow(ChunkPos.getX(c), ChunkPos.getZ(c)) != null) Paver.enqueue(c);
        }
        data.setDirty();
    }

    // ------------------------------------------------------------------ planning

    private static ExecutorService planner() {
        if (planner == null) {
            planner = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "Skycraft Road Planner");
                t.setDaemon(true);
                t.setPriority(Thread.MIN_PRIORITY);
                return t;
            });
        }
        return planner;
    }

    static boolean planning() {
        return running != null && !running.isDone();
    }

    private static void startPlanning(ServerLevel level, RoadsData data) {
        if (planning() || data.pending.isEmpty()) return;
        long pair = data.pending.poll();
        Settlement a = data.settlement(RoadsData.pairA(pair));
        Settlement b = data.settlement(RoadsData.pairB(pair));
        if (a == null || b == null) {
            data.pairs.remove(pair);
            data.setDirty();
            return;
        }
        data.inFlight = pair;
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        RandomState random = level.getChunkSource().randomState();
        int longWater = Math.max(1, (int) Math.round(RoadsConfig.MAX_BRIDGE_LENGTH.get() / RoadPlanner.SPACING));
        RoadPlanner.Job job = new RoadPlanner.Job(data.generation, a.id, b.id, a.x, a.z, b.x, b.z, level.getSeaLevel(),
                RoadsConfig.MAX_EXPANSIONS.get(), longWater, new LongOpenHashSet(data.roadCells));
        running = planner().submit(() -> {
            try {
                RESULTS.add(RoadPlanner.plan(job, generator, random, level));
            } catch (Throwable t) {
                Skycraft.LOGGER.warn("Skycraft roads: planning {} -> {} failed", a.name, b.name, t);
                RESULTS.add(new RoadPlanner.Result(job.generation(), a.id, b.id, new int[0], new int[0], new byte[0], true, 0));
            }
        });
    }

    private static void applyResults(ServerLevel level, RoadsData data) {
        RoadPlanner.Result r;
        while ((r = RESULTS.poll()) != null) {
            if (r.generation() != data.generation) continue;
            long pair = RoadsData.pairKey(r.a(), r.b());
            if (data.inFlight == pair) data.inFlight = -1;
            Settlement a = data.settlement(r.a()), b = data.settlement(r.b());
            if (a == null || b == null || r.xs().length < 2) {
                data.pairs.remove(pair);
                data.setDirty();
                continue;
            }
            Road road = data.addRoad(r.a(), r.b(), r.xs(), r.zs(), r.flags());
            LongOpenHashSet touched = RoadLayout.layout(data, road); // before indexing: junctions see only other roads
            touched.addAll(data.indexRoad(road));
            queueLoaded(level, data, touched);
            Skycraft.LOGGER.debug("Skycraft roads: road {} -> {} planned ({} points, {} m{}, {} expansions)", a.name, b.name,
                    road.size(), Math.round(road.length()), r.fallback() ? ", straight fallback" : "", r.expansions());
        }
    }

    /** Forgets every road and plans the network again from the known settlements. */
    static int replan(ServerLevel level, RoadsData data) {
        if (running != null) running.cancel(true);
        running = null;
        RESULTS.clear();
        Paver.clear();
        data.clearRoads();
        int queued = 0;
        for (Settlement s : data.settlements) queued += planConnections(data, s);
        return queued;
    }

    /** Clears the paved state of every road chunk so loaded ones are paved again (e.g. after a config change). */
    static int repave(ServerLevel level, RoadsData data) {
        LongOpenHashSet all = new LongOpenHashSet(data.chunkIndex.keySet());
        all.addAll(data.featuresByChunk.keySet());
        data.paved.clear();
        int loaded = 0;
        for (long c : all) {
            if (level.getChunkSource().getChunkNow(ChunkPos.getX(c), ChunkPos.getZ(c)) != null) {
                Paver.enqueue(c);
                loaded++;
            }
        }
        data.setDirty();
        return loaded;
    }

    static int pendingPlans(RoadsData data) {
        return data.pending.size() + (data.inFlight != -1 ? 1 : 0);
    }
}
