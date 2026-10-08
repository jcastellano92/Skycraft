package com.skycraft.survival.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A placed cheese wheel: table decoration, as on every Skyrim inn counter. Breaking it gives the wheel back. */
public class CheeseWheelBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 6, 14);

    public CheeseWheelBlock(Properties props) {
        super(props);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }
}
