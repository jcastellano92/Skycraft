package com.skycraft.homestead;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Whitelist of Nordic building blocks allowed for homestead construction.
 * Griefing materials (TNT, Bedrock, Spawners, Obsidian, Portal frames) are forbidden.
 */
public final class HomesteadBlocks {
    private HomesteadBlocks() {}

    public static boolean isAllowedBuildingBlock(BlockState state) {
        Block block = state.getBlock();

        // 1. All woods (planks, logs, wood stairs, slabs, fences, gates, doors, trapdoors)
        if (state.is(BlockTags.PLANKS)
                || state.is(BlockTags.LOGS)
                || state.is(BlockTags.WOODEN_STAIRS)
                || state.is(BlockTags.WOODEN_SLABS)
                || state.is(BlockTags.WOODEN_FENCES)
                || state.is(BlockTags.FENCE_GATES)
                || state.is(BlockTags.WOODEN_DOORS)
                || state.is(BlockTags.WOODEN_TRAPDOORS)
                || state.is(BlockTags.WOODEN_PRESSURE_PLATES)
                || state.is(BlockTags.WOODEN_BUTTONS)
                || state.is(BlockTags.ALL_SIGNS)
                || state.is(BlockTags.ALL_HANGING_SIGNS)) {
            return true;
        }

        // 2. Stone, masonry, masonry stairs & slabs, bricks, deepslate, tuff
        if (state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(BlockTags.STONE_BRICKS)
                || state.is(BlockTags.STAIRS)
                || state.is(BlockTags.SLABS)
                || state.is(BlockTags.WALLS)) {
            // Exclude obsidian and bedrock
            if (block == Blocks.OBSIDIAN || block == Blocks.CRYING_OBSIDIAN || block == Blocks.BEDROCK) return false;
            return true;
        }

        // 3. Glass, windows & roof tiles
        if (block instanceof AbstractGlassBlock || block instanceof StainedGlassBlock
                || block instanceof StainedGlassPaneBlock || block instanceof GlassBlock
                || block instanceof IronBarsBlock) {
            return true;
        }

        // 4. Clays, bricks, terracotta, mud bricks, wool, carpets
        if (state.is(BlockTags.WOOL) || state.is(BlockTags.WOOL_CARPETS) || state.is(BlockTags.TERRACOTTA)
                || block instanceof GlazedTerracottaBlock
                || block == Blocks.BRICKS || block == Blocks.MUD_BRICKS || block == Blocks.PACKED_MUD) {
            return true;
        }

        // 5. Furniture, fixtures & lighting
        if (block instanceof BedBlock
                || block instanceof ChestBlock
                || block instanceof BarrelBlock
                || block instanceof TorchBlock
                || block instanceof LanternBlock
                || block instanceof CampfireBlock
                || block instanceof CandleBlock
                || block instanceof CraftingTableBlock
                || block instanceof FurnaceBlock
                || block instanceof AnvilBlock
                || block instanceof GrindstoneBlock
                || block instanceof SmithingTableBlock
                || block instanceof FlowerPotBlock
                || block == Blocks.BOOKSHELF
                || block instanceof ChiseledBookShelfBlock
                || block instanceof LadderBlock
                || block == Blocks.HAY_BLOCK) {
            return true;
        }

        // Custom skycraft crafting stations & drafting table
        String regName = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(block) != null
                ? net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(block).toString()
                : "";
        if (regName.startsWith("skycraft:")) {
            return true;
        }

        return false;
    }
}
