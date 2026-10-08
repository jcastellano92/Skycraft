package com.skycraft.world;

import com.skycraft.core.PlayerData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/**
 * Accessors for {@code data.module("world")}:
 * <ul>
 *     <li>{@code discovered}: list of {id, name, type, x, y, z, dim, t, ax, ay, az, box[6], cleared}</li>
 *     <li>{@code holds}: list of hold ids the player has entered</li>
 *     <li>{@code seen_nether}, {@code seen_end}: realm titles already shown</li>
 * </ul>
 * (x, y, z) is the centre of the location; (ax, ay, az) is where the player stood when discovering it (the
 * fast-travel arrival point).
 */
public final class WorldData {
    public static final String MODULE = "world";

    private WorldData() {}

    public static CompoundTag root(PlayerData data) {
        return data.module(MODULE);
    }

    public static ListTag discovered(PlayerData data) {
        CompoundTag m = root(data);
        if (!m.contains("discovered", Tag.TAG_LIST)) m.put("discovered", new ListTag());
        return m.getList("discovered", Tag.TAG_COMPOUND);
    }

    public static CompoundTag find(PlayerData data, String id) {
        ListTag list = discovered(data);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            if (t.getString("id").equals(id)) return t;
        }
        return null;
    }

    public static boolean hasVisitedHold(PlayerData data, String hold) {
        ListTag list = root(data).getList("holds", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) if (list.getString(i).equals(hold)) return true;
        return false;
    }

    public static void addVisitedHold(PlayerData data, String hold) {
        CompoundTag m = root(data);
        if (!m.contains("holds", Tag.TAG_LIST)) m.put("holds", new ListTag());
        m.getList("holds", Tag.TAG_STRING).add(StringTag.valueOf(hold));
        data.markDirty();
    }

    /** True if (x, y, z) lies within the stored bounding box of a location, grown by {@code margin}. */
    public static boolean inBox(CompoundTag loc, double x, double y, double z, int margin) {
        int[] b = loc.getIntArray("box");
        if (b.length != 6) return false;
        return x >= b[0] - margin && x < b[3] + 1 + margin && y >= b[1] - margin && y < b[4] + 1 + margin
                && z >= b[2] - margin && z < b[5] + 1 + margin;
    }
}
