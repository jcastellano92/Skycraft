package com.skycraft.survival.block;

import com.skycraft.survival.SurvivalRegistry;
import com.skycraft.world.WorldSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The iconic Nirnroot of Skyrim: a glowing teal plant that grows by the water
 * and constantly produces a high-pitched humming chime.
 * Right-clicking harvests the root, temporarily silencing the chime until it regrows.
 */
public class NirnrootBlock extends BushBlock {
    public static final BooleanProperty HARVESTED = BooleanProperty.create("harvested");
    protected static final VoxelShape SHAPE = box(2.0, 0.0, 2.0, 14.0, 13.0, 14.0);

    public NirnrootBlock(Properties props) {
        super(props);
        this.registerDefaultState(this.stateDefinition.any().setValue(HARVESTED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HARVESTED);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        Vec3 vec3 = state.getOffset(level, pos);
        return SHAPE.move(vec3.x, vec3.y, vec3.z);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!state.getValue(HARVESTED)) {
            if (!level.isClientSide) {
                level.setBlock(pos, state.setValue(HARVESTED, true), 3);
                ItemStack root = new ItemStack(SurvivalRegistry.NIRNROOT.get());
                if (!player.getInventory().add(root)) {
                    popResource(level, pos, root);
                }
                level.playSound(null, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 1.0f, 0.9f);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.use(state, level, pos, player, hand, hit);
    }

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return state.getValue(HARVESTED);
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(HARVESTED) && random.nextInt(5) == 0) {
            level.setBlock(pos, state.setValue(HARVESTED, false), 3);
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(HARVESTED)) return;

        // Shimmering teal particles
        double x = pos.getX() + 0.2 + random.nextDouble() * 0.6;
        double y = pos.getY() + 0.2 + random.nextDouble() * 0.6;
        double z = pos.getZ() + 0.2 + random.nextDouble() * 0.6;
        level.addParticle(ParticleTypes.GLOW, x, y, z, 0.0, 0.01, 0.0);

        // Periodically emit the iconic Nirnroot humming chime
        if (random.nextInt(35) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    WorldSounds.WORLD_NIRNROOT_HUM.get(), SoundSource.BLOCKS, 0.6f, 1.0f, false);
        }
    }
}

