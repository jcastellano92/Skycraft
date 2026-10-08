package com.skycraft.roads;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Plans a road between two points without touching any chunk: A* over a coarse grid (8-block cells) whose heights
 * come from the chunk generator's noise ({@link ChunkGenerator#getBaseHeight}), then Chaikin smoothing and
 * densification to one point every 2 blocks. Runs on the background planner thread; everything it reads is either
 * immutable or thread-safe world generation state.
 */
final class RoadPlanner {
    static final int CELL = 8;
    static final double SPACING = 2.0;
    private static final double SQRT2 = Math.sqrt(2.0);
    /** Even directions are orthogonal, odd ones diagonal. */
    private static final int[] DX = {0, 1, 1, 1, 0, -1, -1, -1};
    private static final int[] DZ = {-1, -1, 0, 1, 1, 1, 0, -1};
    private static final double SLOPE_K = 25.0;
    private static final int CLIFF = 8;
    private static final double CLIFF_COST = 400.0;
    private static final double SHALLOW_WATER = 6.0;
    private static final double DEEP_WATER = 30.0;
    private static final double ROAD_DISCOUNT = 0.55;
    private static final double TURN_COST = 3.0;
    private static final double HEURISTIC_WEIGHT = 1.15;

    record Job(int generation, int a, int b, int ax, int az, int bx, int bz, int seaLevel, int maxExpansions,
               int longWaterPoints, LongOpenHashSet roadCells) {
    }

    record Result(int generation, int a, int b, int[] xs, int[] zs, byte[] flags, boolean fallback, int expansions) {
    }

    private RoadPlanner() {}

    /** Noise-based terrain heights per coarse cell, cached. */
    private static final class Heights {
        private final ChunkGenerator generator;
        private final RandomState random;
        private final LevelHeightAccessor heightAccessor;
        private final int sea;
        private final Long2IntOpenHashMap cache = new Long2IntOpenHashMap();

        Heights(ChunkGenerator generator, RandomState random, LevelHeightAccessor heightAccessor, int sea) {
            this.generator = generator;
            this.random = random;
            this.heightAccessor = heightAccessor;
            this.sea = sea;
            cache.defaultReturnValue(Integer.MIN_VALUE);
        }

        /** First free y above the solid terrain (water does not count as solid). */
        int raw(int cx, int cz) {
            long k = ChunkPos.asLong(cx, cz);
            int h = cache.get(k);
            if (h != Integer.MIN_VALUE) return h;
            try {
                h = generator.getBaseHeight(cx * CELL + CELL / 2, cz * CELL + CELL / 2, Heightmap.Types.OCEAN_FLOOR_WG,
                        heightAccessor, random);
            } catch (RuntimeException e) {
                h = sea;
            }
            cache.put(k, h);
            return h;
        }

        boolean water(int cx, int cz) {
            return raw(cx, cz) < sea;
        }

        /** Walking height: the water surface counts as the deck of a bridge. */
        int effective(int cx, int cz) {
            return Math.max(raw(cx, cz), sea);
        }
    }

    private static final class Node {
        final int x, z;
        double g = Double.MAX_VALUE;
        Node parent;
        int dir = -1;
        boolean closed;

        Node(int x, int z) {
            this.x = x;
            this.z = z;
        }
    }

    private record Open(double f, double g, Node node) {
    }

    static Result plan(Job job, ChunkGenerator generator, RandomState random, LevelHeightAccessor heightAccessor) {
        Heights heights = new Heights(generator, random, heightAccessor, job.seaLevel());
        int[] expansions = new int[1];
        List<double[]> poly = astar(job, heights, expansions);
        boolean fallback = poly == null;
        if (fallback) {
            poly = new ArrayList<>();
            poly.add(new double[]{job.ax() + 0.5, job.az() + 0.5});
            poly.add(new double[]{job.bx() + 0.5, job.bz() + 0.5});
        } else {
            poly = chaikin(chaikin(poly));
        }
        IntArrayList xs = new IntArrayList();
        IntArrayList zs = new IntArrayList();
        densify(poly, xs, zs);
        byte[] flags = waterFlags(xs, zs, heights, job.longWaterPoints());
        return new Result(job.generation(), job.a(), job.b(), xs.toIntArray(), zs.toIntArray(), flags, fallback, expansions[0]);
    }

    // ------------------------------------------------------------------ A*

    private static List<double[]> astar(Job job, Heights heights, int[] expansionsOut) {
        int sx = Math.floorDiv(job.ax(), CELL), sz = Math.floorDiv(job.az(), CELL);
        int gx = Math.floorDiv(job.bx(), CELL), gz = Math.floorDiv(job.bz(), CELL);
        int span = Math.max(Math.abs(gx - sx), Math.abs(gz - sz));
        int margin = Math.max(16, span / 2);
        int minX = Math.min(sx, gx) - margin, maxX = Math.max(sx, gx) + margin;
        int minZ = Math.min(sz, gz) - margin, maxZ = Math.max(sz, gz) + margin;
        int sea = job.seaLevel();

        Long2ObjectOpenHashMap<Node> nodes = new Long2ObjectOpenHashMap<>();
        PriorityQueue<Open> open = new PriorityQueue<>((p, q) -> Double.compare(p.f(), q.f()));
        Node start = new Node(sx, sz);
        start.g = 0;
        nodes.put(ChunkPos.asLong(sx, sz), start);
        open.add(new Open(heuristic(sx, sz, gx, gz), 0, start));
        int expansions = 0;

        while (!open.isEmpty()) {
            Open entry = open.poll();
            Node cur = entry.node();
            if (cur.closed || entry.g() > cur.g + 1e-6) continue;
            cur.closed = true;
            if (cur.x == gx && cur.z == gz) {
                expansionsOut[0] = expansions;
                return reconstruct(cur, job);
            }
            if (++expansions > job.maxExpansions()) break;
            if ((expansions & 1023) == 0 && Thread.currentThread().isInterrupted()) break;

            int hc = heights.effective(cur.x, cur.z);
            for (int d = 0; d < 8; d++) {
                int nx = cur.x + DX[d], nz = cur.z + DZ[d];
                if (nx < minX || nx > maxX || nz < minZ || nz > maxZ) continue;
                long key = ChunkPos.asLong(nx, nz);
                Node nb = nodes.get(key);
                if (nb != null && nb.closed) continue;

                double len = ((d & 1) == 0 ? 1.0 : SQRT2) * CELL;
                int raw = heights.raw(nx, nz);
                int hn = Math.max(raw, sea);
                int rise = Math.abs(hn - hc);
                double grade = rise / len;
                double cost = len * (1.0 + SLOPE_K * grade * grade);
                if (rise > CLIFF) cost += CLIFF_COST;
                if (raw < sea) {
                    cost += len * (sea - raw > 6 ? DEEP_WATER : SHALLOW_WATER);
                } else if (job.roadCells().contains(key)) {
                    cost *= ROAD_DISCOUNT;
                }
                if (cur.dir >= 0 && cur.dir != d) {
                    int turn = Math.abs(cur.dir - d);
                    cost += TURN_COST * Math.min(turn, 8 - turn);
                }
                double g = cur.g + cost;
                if (nb == null) {
                    nb = new Node(nx, nz);
                    nodes.put(key, nb);
                } else if (g >= nb.g) {
                    continue;
                }
                nb.g = g;
                nb.parent = cur;
                nb.dir = d;
                open.add(new Open(g + heuristic(nx, nz, gx, gz), g, nb));
            }
        }
        expansionsOut[0] = expansions;
        return null;
    }

    /** Octile distance in blocks, slightly inflated (weighted A*: much faster, near-optimal). */
    private static double heuristic(int x, int z, int gx, int gz) {
        int dx = Math.abs(gx - x), dz = Math.abs(gz - z);
        double octile = Math.max(dx, dz) + (SQRT2 - 1.0) * Math.min(dx, dz);
        return octile * CELL * HEURISTIC_WEIGHT;
    }

    private static List<double[]> reconstruct(Node goal, Job job) {
        List<double[]> out = new ArrayList<>();
        for (Node n = goal; n != null; n = n.parent) {
            out.add(new double[]{n.x * CELL + CELL / 2.0, n.z * CELL + CELL / 2.0});
        }
        Collections.reverse(out);
        out.set(0, new double[]{job.ax() + 0.5, job.az() + 0.5});
        if (out.size() == 1) out.add(new double[]{job.bx() + 0.5, job.bz() + 0.5});
        else out.set(out.size() - 1, new double[]{job.bx() + 0.5, job.bz() + 0.5});
        return out;
    }

    // ------------------------------------------------------------------ smoothing

    /** One round of Chaikin corner cutting; endpoints are kept. */
    static List<double[]> chaikin(List<double[]> p) {
        if (p.size() < 3) return p;
        List<double[]> out = new ArrayList<>(p.size() * 2);
        out.add(p.get(0));
        for (int i = 0; i < p.size() - 1; i++) {
            double[] a = p.get(i), b = p.get(i + 1);
            out.add(new double[]{a[0] * 0.75 + b[0] * 0.25, a[1] * 0.75 + b[1] * 0.25});
            out.add(new double[]{a[0] * 0.25 + b[0] * 0.75, a[1] * 0.25 + b[1] * 0.75});
        }
        out.add(p.get(p.size() - 1));
        return out;
    }

    /** Resamples the polyline to block columns {@link #SPACING} blocks apart. */
    static void densify(List<double[]> poly, IntArrayList xs, IntArrayList zs) {
        add(xs, zs, poly.get(0)[0], poly.get(0)[1]);
        double carry = 0;
        for (int i = 0; i < poly.size() - 1; i++) {
            double[] a = poly.get(i), b = poly.get(i + 1);
            double dx = b[0] - a[0], dz = b[1] - a[1];
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len < 1e-9) continue;
            double t = SPACING - carry;
            while (t <= len) {
                add(xs, zs, a[0] + dx * t / len, a[1] + dz * t / len);
                t += SPACING;
            }
            carry = len - (t - SPACING);
        }
        double[] last = poly.get(poly.size() - 1);
        add(xs, zs, last[0], last[1]);
        if (xs.size() == 1) {
            xs.add(xs.getInt(0));
            zs.add(zs.getInt(0));
        }
    }

    private static void add(IntArrayList xs, IntArrayList zs, double x, double z) {
        int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
        int n = xs.size();
        if (n > 0 && xs.getInt(n - 1) == ix && zs.getInt(n - 1) == iz) return;
        xs.add(ix);
        zs.add(iz);
    }

    /** Marks water points and the ones inside crossings too long to bridge. */
    private static byte[] waterFlags(IntArrayList xs, IntArrayList zs, Heights heights, int longWaterPoints) {
        int n = xs.size();
        byte[] flags = new byte[n];
        for (int i = 0; i < n; i++) {
            if (heights.water(Math.floorDiv(xs.getInt(i), CELL), Math.floorDiv(zs.getInt(i), CELL))) flags[i] = Road.WATER;
        }
        int i = 0;
        while (i < n) {
            if ((flags[i] & Road.WATER) == 0) {
                i++;
                continue;
            }
            int j = i;
            while (j < n && (flags[j] & Road.WATER) != 0) j++;
            if (j - i > longWaterPoints) {
                for (int k = i; k < j; k++) flags[k] |= Road.LONG_WATER;
            }
            i = j;
        }
        return flags;
    }
}
