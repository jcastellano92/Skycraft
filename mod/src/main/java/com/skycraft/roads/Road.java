package com.skycraft.roads;

import net.minecraft.nbt.CompoundTag;

/**
 * A planned road between settlements {@link #a} and {@link #b}: a polyline of block columns roughly 2 blocks apart,
 * from the center of {@code a} to the center of {@code b}. Heights are not stored; paving reads the real surface.
 */
public final class Road {
    /** The planner expects water at this point (bridge). */
    public static final byte WATER = 1;
    /** Part of a water crossing longer than the maximum bridge length: nothing is built here. */
    public static final byte LONG_WATER = 2;

    public final int id;
    public final int a;
    public final int b;
    public final int[] xs;
    public final int[] zs;
    public final byte[] flags;
    /** Cumulative arc length (blocks) from point 0, computed on load. */
    final float[] cum;

    public Road(int id, int a, int b, int[] xs, int[] zs, byte[] flags) {
        this.id = id;
        this.a = a;
        this.b = b;
        this.xs = xs;
        this.zs = zs;
        this.flags = flags.length == xs.length ? flags : new byte[xs.length];
        this.cum = new float[xs.length];
        for (int i = 1; i < xs.length; i++) {
            double dx = xs[i] - xs[i - 1];
            double dz = zs[i] - zs[i - 1];
            cum[i] = cum[i - 1] + (float) Math.sqrt(dx * dx + dz * dz);
        }
    }

    public int size() {
        return xs.length;
    }

    public float length() {
        return xs.length == 0 ? 0 : cum[xs.length - 1];
    }

    /** Distance along the road from point {@code i} to the end at settlement {@code toward}. */
    public float distanceTo(int i, int toward) {
        return toward == a ? cum[i] : length() - cum[i];
    }

    public int clamp(int i) {
        return Math.max(0, Math.min(xs.length - 1, i));
    }

    public boolean longWater(int i) {
        return (flags[i] & LONG_WATER) != 0;
    }

    CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("id", id);
        t.putInt("a", a);
        t.putInt("b", b);
        t.putIntArray("x", xs);
        t.putIntArray("z", zs);
        t.putByteArray("f", flags);
        return t;
    }

    static Road load(CompoundTag t) {
        int[] xs = t.getIntArray("x");
        int[] zs = t.getIntArray("z");
        if (xs.length < 2 || xs.length != zs.length) return null;
        return new Road(t.getInt("id"), t.getInt("a"), t.getInt("b"), xs, zs, t.getByteArray("f"));
    }
}
