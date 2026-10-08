package com.skycraft.crafting;

import com.skycraft.crafting.arcane.ArcaneRegistry;
import com.skycraft.roads.Settlement;
import com.skycraft.society.Encounters;
import com.skycraft.survival.SurvivalRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Places Skyrim crafting stations (forge, smelter, tanning rack, workbench, grindstone,
 * alchemy lab, enchanter, cooking pot) and world clutter in settlements.
 */
public final class SettlementStations {
    private SettlementStations() {}

    public static void populate(ServerLevel level, Settlement s, BlockPos center, RandomSource r) {
        // Place stations
        placeStation(level, center, CraftingBlocks.STATIONS.get(StationType.FORGE).get(), 6, 16, r);
        placeStation(level, center, CraftingBlocks.STATIONS.get(StationType.SMELTER).get(), 6, 16, r);
        placeStation(level, center, CraftingBlocks.STATIONS.get(StationType.ARMOR_WORKBENCH).get(), 6, 16, r);
        placeStation(level, center, CraftingBlocks.STATIONS.get(StationType.GRINDSTONE).get(), 6, 16, r);
        placeStation(level, center, CraftingBlocks.STATIONS.get(StationType.TANNING_RACK).get(), 6, 16, r);
        placeStation(level, center, ArcaneRegistry.ALCHEMY_LAB.get(), 8, 20, r);
        placeStation(level, center, ArcaneRegistry.ARCANE_ENCHANTER.get(), 10, 22, r);
        placeStation(level, center, SurvivalRegistry.COOKING_POT.get(), 5, 15, r);

        // Place world clutter items
        List<ItemStack> clutterItems = List.of(
                new ItemStack(Items.BREAD),
                new ItemStack(Items.APPLE),
                new ItemStack(Items.POTATO),
                new ItemStack(Items.IRON_INGOT),
                new ItemStack(CraftingItems.LEATHER_STRIPS.get()),
                new ItemStack(CraftingItems.FIREWOOD.get())
        );

        for (ItemStack item : clutterItems) {
            BlockPos p = findSurface(level, center, 4, 18, r);
            if (p != null) {
                ItemEntity entity = new ItemEntity(level, p.getX() + 0.5, p.getY() + 0.1, p.getZ() + 0.5, item.copy());
                entity.setDeltaMovement(0, 0, 0);
                entity.lifespan = Integer.MAX_VALUE;
                entity.setExtendedLifetime();
                level.addFreshEntity(entity);
            }
        }
    }

    private static void placeStation(ServerLevel level, BlockPos center, Block block, int minR, int maxR, RandomSource r) {
        BlockPos pos = findSurface(level, center, minR, maxR, r);
        if (pos != null) {
            level.setBlock(pos, block.defaultBlockState(), 3);
        }
    }

    private static BlockPos findSurface(ServerLevel level, BlockPos center, int minR, int maxR, RandomSource r) {
        for (int i = 0; i < 16; i++) {
            int dx = (r.nextBoolean() ? 1 : -1) * (minR + r.nextInt(Math.max(1, maxR - minR)));
            int dz = (r.nextBoolean() ? 1 : -1) * (minR + r.nextInt(Math.max(1, maxR - minR)));
            int x = center.getX() + dx;
            int z = center.getZ() + dz;
            BlockPos ground = Encounters.groundAt(level, x, z);
            if (ground != null && Math.abs(ground.getY() - center.getY()) <= 8) {
                BlockState above = level.getBlockState(ground);
                BlockState floor = level.getBlockState(ground.below());
                if (above.isAir() && floor.isSolidRender(level, ground.below())) {
                    return ground;
                }
            }
        }
        return null;
    }
}

