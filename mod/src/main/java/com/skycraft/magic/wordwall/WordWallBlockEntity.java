package com.skycraft.magic.wordwall;

import com.skycraft.magic.MagicRegistry;
import com.skycraft.magic.shout.Shout;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Remembers which shout a Word Wall teaches (chosen at random, weighted, the first time it's needed). */
public class WordWallBlockEntity extends BlockEntity {
    private String shout = "";

    public WordWallBlockEntity(BlockPos pos, BlockState state) {
        super(MagicRegistry.WORD_WALL_BE.get(), pos, state);
    }

    public Shout shout(RandomSource random) {
        Shout s = Shout.byId(shout);
        if (s == null) {
            s = WordWalls.randomShout(random);
            setShout(s);
        }
        return s;
    }

    public void setShout(Shout s) {
        this.shout = s.id();
        setChanged();
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.shout = tag.getString("shout");
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("shout", shout);
    }

    /** Players who walk up to the wall (within 3 blocks) read it. */
    public static void serverTick(Level level, BlockPos pos, BlockState state, WordWallBlockEntity be) {
        if (level.getGameTime() % 20 != (pos.asLong() & 15) || !(level instanceof ServerLevel server)) return;
        AABB area = new AABB(pos).inflate(3.0);
        for (ServerPlayer p : server.getEntitiesOfClass(ServerPlayer.class, area, pl -> pl.isAlive() && !pl.isSpectator())) {
            if (p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 3.5 * 3.5) {
                WordWalls.teach(p, server, pos, be.shout(level.random), false);
            }
        }
    }
}
