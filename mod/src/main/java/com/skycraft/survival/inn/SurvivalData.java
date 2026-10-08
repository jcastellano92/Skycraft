package com.skycraft.survival.inn;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** World data of the survival module (stored with the Overworld): which settlements have their innkeeper. */
public final class SurvivalData extends SavedData {
    public static final String NAME = "skycraft_survival";

    public static final class Inn {
        boolean spawned;
        long lastSeen;
        /** Not saved: last time we looked for the innkeeper. */
        transient long lastCheck = Long.MIN_VALUE / 2;
    }

    private final Int2ObjectOpenHashMap<Inn> inns = new Int2ObjectOpenHashMap<>();

    public static SurvivalData get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(SurvivalData::load, SurvivalData::new, NAME);
    }

    public SurvivalData() {
    }

    Inn inn(int settlementId) {
        return inns.computeIfAbsent(settlementId, k -> new Inn());
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        inns.forEach((id, inn) -> {
            CompoundTag t = new CompoundTag();
            t.putInt("id", id);
            t.putBoolean("spawned", inn.spawned);
            t.putLong("seen", inn.lastSeen);
            list.add(t);
        });
        tag.put("inns", list);
        return tag;
    }

    public static SurvivalData load(CompoundTag tag) {
        SurvivalData data = new SurvivalData();
        ListTag list = tag.getList("inns", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            Inn inn = data.inn(t.getInt("id"));
            inn.spawned = t.getBoolean("spawned");
            inn.lastSeen = t.getLong("seen");
        }
        return data;
    }
}
