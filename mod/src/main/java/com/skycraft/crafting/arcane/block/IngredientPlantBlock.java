package com.skycraft.crafting.arcane.block;

import com.skycraft.perk.Perks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A wild alchemy plant (Nightshade, Deathbell, Nirnroot, Frost Mirriam). Grows on dirt, sand, moss and snow.
 *
 * <p>Right-click to harvest (fauna module): the player gets the ingredient (two with Green Thumb) and the plant is left
 * as bare stems ({@link #HARVESTED}), which regrow after about one in-game day of random ticks. Harvested stems drop
 * nothing when broken.</p>
 */
public class IngredientPlantBlock extends FlowerBlock {
    public static final BooleanProperty HARVESTED = BooleanProperty.create("harvested");
    /** One random tick in this many regrows the plant: ~18 random ticks at the default tick speed is about 24000 ticks. */
    private static final int REGROW_CHANCE = 18;

    private final boolean glowing;

    public IngredientPlantBlock(MobEffect stewEffect, int stewDuration, boolean glowing, Properties props) {
        super(stewEffect, stewDuration, props);
        this.glowing = glowing;
        this.registerDefaultState(this.stateDefinition.any().setValue(HARVESTED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(HARVESTED);
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return super.mayPlaceOn(state, level, pos) || state.is(BlockTags.SAND) || state.is(Blocks.SNOW_BLOCK)
                || state.is(Blocks.MOSS_BLOCK) || state.is(Blocks.GRAVEL);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (state.getValue(HARVESTED)) return InteractionResult.PASS;
        if (!level.isClientSide) {
            ItemStack harvest = new ItemStack(this.asItem(), Perks.has(player, "alchemy.green_thumb") ? 2 : 1);
            if (!player.getInventory().add(harvest)) Block.popResource(level, pos, harvest);
            level.setBlock(pos, state.setValue(HARVESTED, true), Block.UPDATE_CLIENTS);
            level.playSound(null, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 1.0f, 0.8f + level.random.nextFloat() * 0.4f);
            level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return state.getValue(HARVESTED);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(HARVESTED) && random.nextInt(REGROW_CHANCE) == 0) {
            level.setBlock(pos, state.setValue(HARVESTED, false), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        // Nirnroot glitters and hums
        if (glowing && !state.getValue(HARVESTED) && random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 0.4 + random.nextDouble() * 0.5,
                    pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0, 0.01, 0);
        }
    }
}
