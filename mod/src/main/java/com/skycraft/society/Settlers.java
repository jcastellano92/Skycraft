package com.skycraft.society;

import com.skycraft.core.Holds;
import com.skycraft.roads.RoadsData;
import com.skycraft.roads.Settlement;
import com.skycraft.society.entity.NpcEntity;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Gives each settlement detected by the roads module its people, once: a bard (playing for the inn the survival
 * module runs there), a priest, maybe a beggar, a worker (hunter, miner or lumberjack) and a mage or adventurer passing through. The first settlement found
 * in each hold becomes its seat: the Jarl and a housecarl live there.
 */
public final class Settlers {
    private Settlers() {}

    /** Called every 5 seconds per player. */
    static void tick(ServerPlayer player) {
        if (!SocietyConfig.POPULATE_SETTLEMENTS.get() || player.isSpectator()) return;
        ServerLevel level = player.serverLevel();
        if (level.dimension() != Level.OVERWORLD) return;
        Settlement s;
        try {
            s = RoadsData.get(level).zoneAt(player.getBlockX(), player.getBlockZ());
        } catch (RuntimeException e) {
            return;
        }
        if (s == null) return;
        Data data = Data.get(level);
        if (data.populated.contains(s.key)) return;
        BlockPos center = new BlockPos(s.x, player.getBlockY(), s.z);
        if (player.distanceToSqr(s.x + 0.5, player.getY(), s.z + 0.5) > 80 * 80 || !level.isPositionEntityTicking(center)) return;
        populate(level, s, data);
    }

    private static void populate(ServerLevel level, Settlement s, Data data) {
        data.populated.add(s.key);
        data.setDirty();
        RandomSource r = level.getRandom();
        // the survival module gives every settlement its innkeeper
        List<NpcRole> roles = new ArrayList<>(List.of(NpcRole.BARD, NpcRole.PRIEST));
        if (r.nextBoolean()) roles.add(NpcRole.BEGGAR);
        NpcRole[] workers = {NpcRole.HUNTER, NpcRole.MINER, NpcRole.LUMBERJACK};
        roles.add(workers[r.nextInt(workers.length)]);
        if (r.nextInt(3) == 0) roles.add(r.nextBoolean() ? NpcRole.MAGE : NpcRole.ADVENTURER);

        BlockPos center = Encounters.groundAt(level, s.x, s.z);
        if (center == null) center = new BlockPos(s.x, s.y, s.z);
        for (NpcRole role : roles) spawn(level, s, center, role, r);

        String hold = Holds.holdAt(level, center);
        if (!data.seats.contains(hold)) {
            data.seats.add(hold);
            NpcEntity jarl = spawn(level, s, center, NpcRole.JARL, r);
            if (jarl != null) {
                NpcEntity housecarl = spawn(level, s, jarl.blockPosition(), NpcRole.HOUSECARL, r);
                if (housecarl != null) housecarl.setLeader(jarl.getUUID());
            }
        }
    }

    @Nullable
    private static NpcEntity spawn(ServerLevel level, Settlement s, BlockPos center, NpcRole role, RandomSource r) {
        BlockPos pos = null;
        for (int i = 0; i < 12 && pos == null; i++) {
            int x = center.getX() + r.nextInt(33) - 16;
            int z = center.getZ() + r.nextInt(33) - 16;
            BlockPos p = Encounters.groundAt(level, x, z);
            if (p != null && Math.abs(p.getY() - center.getY()) <= 6 && level.getBlockState(p).isAir()
                    && level.getBlockState(p.above()).isAir()) pos = p;
        }
        if (pos == null) pos = center;
        NpcEntity npc = Npcs.create(level, pos, role);
        if (npc == null) return null;
        npc.setPersistenceRequired();
        npc.setHome(center, role == NpcRole.HUNTER || role == NpcRole.LUMBERJACK || role == NpcRole.MINER ? 48 : 28);
        return level.addFreshEntity(npc) ? npc : null;
    }

    /** Which settlements got their people, and which holds have a Jarl. */
    static final class Data extends SavedData {
        static final String NAME = "skycraft_society";
        final LongOpenHashSet populated = new LongOpenHashSet();
        final Set<String> seats = new HashSet<>();

        static Data get(ServerLevel level) {
            return level.getDataStorage().computeIfAbsent(Data::load, Data::new, NAME);
        }

        static Data load(CompoundTag tag) {
            Data d = new Data();
            ListTag list = tag.getList("populated", Tag.TAG_LONG);
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i) instanceof LongTag l) d.populated.add(l.getAsLong());
            }
            ListTag seats = tag.getList("seats", Tag.TAG_STRING);
            for (int i = 0; i < seats.size(); i++) d.seats.add(seats.getString(i));
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            ListTag list = new ListTag();
            for (long k : populated) list.add(LongTag.valueOf(k));
            tag.put("populated", list);
            ListTag seatList = new ListTag();
            for (String h : seats) seatList.add(StringTag.valueOf(h));
            tag.put("seats", seatList);
            return tag;
        }
    }
}
