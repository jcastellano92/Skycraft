package com.skycraft.roads;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

/** A roadside object placed when its chunk is paved: a lantern post or a signpost. */
public final class RoadFeature {
    public static final int LANTERN = 0;
    public static final int SIGNPOST = 1;
    /** A signpost carries at most this many destination signs. */
    public static final int MAX_DESTINATIONS = 3;

    /**
     * One sign: destination settlement, distance in blocks, and the direction (unit vector) the road leaves toward it.
     */
    public record Dest(int settlement, int distance, float dirX, float dirZ) {
    }

    public final int type;
    public final int x;
    public final int z;
    /** Road that created this feature. */
    public final int road;
    public final List<Dest> dests = new ArrayList<>();

    public RoadFeature(int type, int x, int z, int road) {
        this.type = type;
        this.x = x;
        this.z = z;
        this.road = road;
    }

    public boolean hasDest(int settlement) {
        for (Dest d : dests) if (d.settlement() == settlement) return true;
        return false;
    }

    CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("type", type);
        t.putInt("x", x);
        t.putInt("z", z);
        t.putInt("road", road);
        if (!dests.isEmpty()) {
            ListTag list = new ListTag();
            for (Dest d : dests) {
                CompoundTag dt = new CompoundTag();
                dt.putInt("s", d.settlement());
                dt.putInt("d", d.distance());
                dt.putFloat("dx", d.dirX());
                dt.putFloat("dz", d.dirZ());
                list.add(dt);
            }
            t.put("dests", list);
        }
        return t;
    }

    static RoadFeature load(CompoundTag t) {
        RoadFeature f = new RoadFeature(t.getInt("type"), t.getInt("x"), t.getInt("z"), t.getInt("road"));
        ListTag list = t.getList("dests", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag dt = list.getCompound(i);
            f.dests.add(new Dest(dt.getInt("s"), dt.getInt("d"), dt.getFloat("dx"), dt.getFloat("dz")));
        }
        return f;
    }
}
