package com.skycraft.crafting.arcane.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.state.BlockState;

/** A wild alchemy plant (Nightshade, Deathbell, Nirnroot, Frost Mirriam). Grows on dirt, sand, moss and snow. */
public class IngredientPlantBlock extends FlowerBlock {
    private final boolean glowing;

    public IngredientPlantBlock(MobEffect stewEffect, int stewDuration, boolean glowing, Properties props) {
        super(stewEffect, stewDuration, props);
        this.glowing = glowing;
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return super.mayPlaceOn(state, level, pos) || state.is(BlockTags.SAND) || state.is(Blocks.SNOW_BLOCK)
                || state.is(Blocks.MOSS_BLOCK) || state.is(Blocks.GRAVEL);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        // Nirnroot glitters and hums
        if (glowing && random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 0.4 + random.nextDouble() * 0.5,
                    pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0, 0.01, 0);
        }
    }
}
