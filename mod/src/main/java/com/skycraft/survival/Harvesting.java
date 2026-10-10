package com.skycraft.survival;

import com.skycraft.Skycraft;
import com.skycraft.core.Notifier;
import com.skycraft.core.Skill;
import com.skycraft.crafting.arcane.block.IngredientPlantBlock;
import com.skycraft.perk.Perks;
import com.skycraft.skills.Progression;
import com.skycraft.survival.block.SkyCropBlock;
import com.skycraft.vitals.ActionHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.FungusBlock;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Skyrim-style harvesting of crops, wild flora, tall grass, mushrooms and plants.
 *
 * <p>Deliberate harvesting ([F] / Take or left-click breaking) adds the produce
 * directly into the player's inventory/bags, plays authentic harvest audio, awards
 * Alchemy/Farming progression, and cleanly replants mature crops so farmlands remain intact.</p>
 */
@Mod.EventBusSubscriber(modid = Skycraft.MODID)
public final class Harvesting {

    private Harvesting() {}

    /**
     * True if this block can be harvested as flora, herbs, flowers, mushrooms or crops.
     * Ambient grass, tall grass, ferns, and dead bushes cannot be harvested (matching Skyrim).
     */
    public static boolean isHarvestable(BlockState state) {
        if (state == null || state.isAir()) return false;
        Block b = state.getBlock();

        // Ambient grass, ferns, seagrass, saplings and dead bushes can NEVER be harvested
        if (state.is(Blocks.GRASS) || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.FERN) || state.is(Blocks.LARGE_FERN)
                || state.is(Blocks.SEAGRASS) || state.is(Blocks.TALL_SEAGRASS)
                || state.is(Blocks.DEAD_BUSH)
                || b instanceof net.minecraft.world.level.block.SaplingBlock) {
            return false;
        }

        // 1. Crops
        if (b instanceof CropBlock || state.is(BlockTags.CROPS) || b instanceof NetherWartBlock || b instanceof CocoaBlock) {
            return true;
        }

        // 2. Alchemy custom plants
        if (b instanceof IngredientPlantBlock) {
            return true;
        }

        // 3. Berry bushes and vines
        if (b instanceof SweetBerryBushBlock || state.is(Blocks.CAVE_VINES) || state.is(Blocks.CAVE_VINES_PLANT)) {
            return true;
        }

        // 4. Flowers and double flowers
        if (b instanceof FlowerBlock || state.is(BlockTags.FLOWERS)) {
            return true;
        }
        if (state.is(Blocks.SUNFLOWER) || state.is(Blocks.LILAC) || state.is(Blocks.ROSE_BUSH) || state.is(Blocks.PEONY)) {
            return true;
        }

        // 5. Mushrooms and fungi
        if (b instanceof MushroomBlock || b instanceof FungusBlock
                || state.is(Blocks.BROWN_MUSHROOM) || state.is(Blocks.RED_MUSHROOM)
                || state.is(Blocks.CRIMSON_FUNGUS) || state.is(Blocks.WARPED_FUNGUS)) {
            return true;
        }

        return false;
    }

    /** True if the plant or crop is mature/ready for harvest. */
    public static boolean isReadyToHarvest(BlockState state) {
        if (!isHarvestable(state)) return false;
        Block b = state.getBlock();

        if (b instanceof CropBlock crop) {
            return crop.isMaxAge(state);
        }
        if (b instanceof NetherWartBlock) {
            return state.getValue(NetherWartBlock.AGE) >= 3;
        }
        if (b instanceof CocoaBlock) {
            return state.getValue(CocoaBlock.AGE) >= 2;
        }
        if (b instanceof IngredientPlantBlock) {
            return !state.getValue(IngredientPlantBlock.HARVESTED);
        }
        if (b instanceof SweetBerryBushBlock) {
            return state.getValue(SweetBerryBushBlock.AGE) >= 2;
        }
        if (state.is(Blocks.CAVE_VINES) || state.is(Blocks.CAVE_VINES_PLANT)) {
            return CaveVines.hasGlowBerries(state);
        }

        // Wild flowers and mushrooms are always ready
        return true;
    }

    /**
     * Executes deliberate harvesting on the server.
     * Yields items directly into the player's bags, plays sounds, and updates the block state.
     */
    public static boolean harvest(ServerPlayer player, BlockPos pos) {
        if (player == null || pos == null || player.isSpectator()) return false;
        ServerLevel level = player.serverLevel();
        if (player.distanceToSqr(Vec3.atCenterOf(pos)) > 36.0) return false;

        BlockState state = level.getBlockState(pos);
        if (!isHarvestable(state)) return false;

        Block block = state.getBlock();
        boolean greenThumb = Perks.has(player, "alchemy.green_thumb");
        List<ItemStack> harvestedItems = new ArrayList<>();

        // ------------------------------------------------------------------ 1. Crops
        if (block instanceof CropBlock crop) {
            if (!crop.isMaxAge(state)) {
                Notifier.message(player, Component.literal("This crop is still growing."));
                return false;
            }

            // Retrieve drops from loot tables
            List<ItemStack> drops = Block.getDrops(state, level, pos, null, player, player.getMainHandItem());
            if (drops.isEmpty()) {
                // Fallback produce if loot table returned empty
                drops = getCropFallbackDrops(crop, state);
            }

            for (ItemStack s : drops) {
                if (s.isEmpty()) continue;
                ItemStack copy = s.copy();
                // Green thumb doubles the edible produce
                if (greenThumb && copy.getItem() != getSeedItem(crop)) {
                    copy.grow(copy.getCount());
                }
                harvestedItems.add(copy);
            }

            // Replant crop at stage 0 (Skyrim farm preservation)
            level.setBlock(pos, crop.getStateForAge(0), 3);
            level.playSound(null, pos, SoundEvents.CROP_BREAK, SoundSource.BLOCKS, 1.0f, 1.0f);
            level.playSound(null, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 0.8f, 1.2f);
            Progression.addSkillXp(player, Skill.ALCHEMY, 2.0f);

        } else if (block instanceof NetherWartBlock) {
            int age = state.getValue(NetherWartBlock.AGE);
            if (age < 3) {
                Notifier.message(player, Component.literal("Nether wart is still growing."));
                return false;
            }
            int count = 2 + level.random.nextInt(3);
            if (greenThumb) count *= 2;
            harvestedItems.add(new ItemStack(Items.NETHER_WART, count));

            level.setBlock(pos, state.setValue(NetherWartBlock.AGE, 0), 3);
            level.playSound(null, pos, SoundEvents.NETHER_WART_BREAK, SoundSource.BLOCKS, 1.0f, 1.0f);
            Progression.addSkillXp(player, Skill.ALCHEMY, 2.0f);

        } else if (block instanceof CocoaBlock) {
            int age = state.getValue(CocoaBlock.AGE);
            if (age < 2) {
                Notifier.message(player, Component.literal("Cocoa is still ripening."));
                return false;
            }
            int count = 2 + (greenThumb ? 2 : 0);
            harvestedItems.add(new ItemStack(Items.COCOA_BEANS, count));

            level.setBlock(pos, state.setValue(CocoaBlock.AGE, 0), 3);
            level.playSound(null, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 1.0f, 1.0f);
            Progression.addSkillXp(player, Skill.ALCHEMY, 2.0f);

        // ------------------------------------------------------------------ 2. Alchemy Plants
        } else if (block instanceof IngredientPlantBlock) {
            if (state.getValue(IngredientPlantBlock.HARVESTED)) {
                Notifier.message(player, Component.literal("Already harvested."));
                return false;
            }
            int count = greenThumb ? 2 : 1;
            harvestedItems.add(new ItemStack(block.asItem(), count));

            level.setBlock(pos, state.setValue(IngredientPlantBlock.HARVESTED, true), Block.UPDATE_CLIENTS);
            level.playSound(null, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 1.0f, 1.0f);
            Progression.addSkillXp(player, Skill.ALCHEMY, 2.5f);

        // ------------------------------------------------------------------ 3. Berry Bushes & Vines
        } else if (block instanceof SweetBerryBushBlock) {
            int age = state.getValue(SweetBerryBushBlock.AGE);
            if (age < 2) {
                Notifier.message(player, Component.literal("Berries are not ripe yet."));
                return false;
            }
            int count = 1 + level.random.nextInt(2) + (age == 3 ? 1 : 0);
            if (greenThumb) count *= 2;
            harvestedItems.add(new ItemStack(Items.SWEET_BERRIES, count));

            level.setBlock(pos, state.setValue(SweetBerryBushBlock.AGE, 1), 3);
            level.playSound(null, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 1.0f, 1.0f);
            Progression.addSkillXp(player, Skill.ALCHEMY, 1.5f);

        } else if (state.is(Blocks.CAVE_VINES) || state.is(Blocks.CAVE_VINES_PLANT)) {
            if (!CaveVines.hasGlowBerries(state)) {
                Notifier.message(player, Component.literal("No glow berries ripe."));
                return false;
            }
            int count = greenThumb ? 2 : 1;
            harvestedItems.add(new ItemStack(Items.GLOW_BERRIES, count));

            level.setBlock(pos, state.setValue(CaveVines.BERRIES, false), 3);
            level.playSound(null, pos, SoundEvents.CAVE_VINES_PICK_BERRIES, SoundSource.BLOCKS, 1.0f, 1.0f);
            Progression.addSkillXp(player, Skill.ALCHEMY, 1.5f);

        // ------------------------------------------------------------------ 4. Flowers, Mushrooms, Flora
        } else {
            Item item = block.asItem();
            if (item != Items.AIR) {
                int count = greenThumb ? 2 : 1;
                harvestedItems.add(new ItemStack(item, count));
            }

            removeFlora(level, pos, state);
            level.playSound(null, pos, SoundEvents.GRASS_BREAK, SoundSource.BLOCKS, 0.9f, 1.1f);
            Progression.addSkillXp(player, Skill.ALCHEMY, 1.0f);
        }

        // Deliver all items directly into bags
        for (ItemStack item : harvestedItems) {
            deliverToPlayer(player, level, item);
        }

        return true;
    }

    private static void removeFlora(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof DoublePlantBlock && state.hasProperty(DoublePlantBlock.HALF)) {
            DoubleBlockHalf half = state.getValue(DoublePlantBlock.HALF);
            BlockPos otherPos = (half == DoubleBlockHalf.UPPER) ? pos.below() : pos.above();
            level.removeBlock(pos, false);
            if (level.getBlockState(otherPos).is(state.getBlock())) {
                level.removeBlock(otherPos, false);
            }
        } else {
            level.removeBlock(pos, false);
        }
    }

    private static void deliverToPlayer(ServerPlayer player, ServerLevel level, ItemStack item) {
        if (item.isEmpty()) return;
        String name = item.getHoverName().getString();
        int count = item.getCount();

        // 1. Put item directly into bags / inventory
        ActionHandler.addToBags(player, item);

        // 2. If bags are full, pop surplus at player feet so nothing is lost
        if (!item.isEmpty()) {
            Block.popResource(level, player.blockPosition(), item);
        }

        // 3. Skyrim HUD message: "Wheat (2) added"
        Component msg = Component.literal(name + (count > 1 ? " (" + count + ")" : "") + " added");
        Notifier.message(player, msg);
    }

    private static List<ItemStack> getCropFallbackDrops(CropBlock crop, BlockState state) {
        List<ItemStack> list = new ArrayList<>();
        if (crop == Blocks.WHEAT) {
            list.add(new ItemStack(Items.WHEAT, 1));
            list.add(new ItemStack(Items.WHEAT_SEEDS, 1 + (int) (Math.random() * 2)));
        } else if (crop == Blocks.CARROTS) {
            list.add(new ItemStack(Items.CARROT, 2 + (int) (Math.random() * 2)));
        } else if (crop == Blocks.POTATOES) {
            list.add(new ItemStack(Items.POTATO, 2 + (int) (Math.random() * 2)));
        } else if (crop == Blocks.BEETROOTS) {
            list.add(new ItemStack(Items.BEETROOT, 1));
            list.add(new ItemStack(Items.BEETROOT_SEEDS, 1 + (int) (Math.random() * 2)));
        } else if (crop instanceof SkyCropBlock) {
            Item seedItem = getSeedItem(crop);
            list.add(new ItemStack(seedItem, 1 + (int) (Math.random() * 2)));
            Item produceItem = getProduceForSkyCrop(crop);
            if (produceItem != null && produceItem != Items.AIR) {
                list.add(new ItemStack(produceItem, 1 + (int) (Math.random() * 2)));
            }
        }
        return list;
    }

    public static Item getSeedItem(CropBlock crop) {
        if (crop == Blocks.WHEAT) return Items.WHEAT_SEEDS;
        if (crop == Blocks.CARROTS) return Items.CARROT;
        if (crop == Blocks.POTATOES) return Items.POTATO;
        if (crop == Blocks.BEETROOTS) return Items.BEETROOT_SEEDS;
        if (crop == SurvivalRegistry.CABBAGE_CROP.get()) return SurvivalRegistry.CABBAGE_SEEDS.get();
        if (crop == SurvivalRegistry.LEEK_CROP.get()) return SurvivalRegistry.LEEK_SEEDS.get();
        if (crop == SurvivalRegistry.TOMATO_CROP.get()) return SurvivalRegistry.TOMATO_SEEDS.get();
        return crop.asItem();
    }

    private static Item getProduceForSkyCrop(CropBlock crop) {
        if (crop == SurvivalRegistry.CABBAGE_CROP.get()) return SurvivalRegistry.CABBAGE.get();
        if (crop == SurvivalRegistry.LEEK_CROP.get()) return SurvivalRegistry.LEEK.get();
        if (crop == SurvivalRegistry.TOMATO_CROP.get()) return SurvivalRegistry.TOMATO.get();
        return null;
    }

    /**
     * Intercepts left-click punching / breaking of tall grass, flowers, mushrooms and crops.
     * Prevents drops from falling to the ground uncollected, ensuring they go directly to inventory.
     */
    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || !(event.getPlayer() instanceof ServerPlayer player) || player.isCreative()) return;
        BlockState state = event.getState();
        BlockPos pos = event.getPos();

        if (isHarvestable(state)) {
            // If it's ready, harvest directly into inventory!
            if (isReadyToHarvest(state)) {
                harvest(player, pos);
                event.setCanceled(true); // Cancel vanilla break so drops aren't spawned twice or onto ground
            } else if (state.getBlock() instanceof CropBlock crop) {
                // Immature crop broken with left-click: give seed back directly to bags
                Item seedItem = getSeedItem(crop);
                if (seedItem != Items.AIR) {
                    deliverToPlayer(player, player.serverLevel(), new ItemStack(seedItem, 1));
                }
                player.serverLevel().removeBlock(pos, false);
                event.setCanceled(true);
            }
        }
    }
}
