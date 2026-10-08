package com.skycraft.skills;

import com.skycraft.Skycraft;
import com.skycraft.core.Notifier;
import com.skycraft.core.SkyData;
import com.skycraft.core.Skill;
import com.skycraft.dig.DiggingRules;
import com.skycraft.perk.Perks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.PolarBear;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.ItemFishedEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Mining, Woodcutting, Fishing and Hunting: XP gains and perk effects. */
@Mod.EventBusSubscriber(modid = Skycraft.MODID)
public final class LifeSkills {
    /** Extra-valuable ores (Skyrim ebony, moonstone, etc.) give more Mining XP. */
    public static final TagKey<Block> VALUABLE_ORES = TagKey.create(Registries.BLOCK, new ResourceLocation(Skycraft.MODID, "valuable_ores"));
    private static final ThreadLocal<Boolean> CHAIN_BREAKING = ThreadLocal.withInitial(() -> false);

    private LifeSkills() {}

    // ------------------------------------------------------------------ mining & woodcutting

    public static float oreValue(BlockState state) {
        if (state.is(Tags.Blocks.ORES_NETHERITE_SCRAP)) return 25;
        if (state.is(VALUABLE_ORES)) return 14;
        if (state.is(Tags.Blocks.ORES_DIAMOND) || state.is(Tags.Blocks.ORES_EMERALD)) return 15;
        if (state.is(Tags.Blocks.ORES_GOLD)) return 8;
        if (state.is(Tags.Blocks.ORES_IRON) || state.is(Tags.Blocks.ORES_LAPIS)) return 6;
        if (state.is(Tags.Blocks.ORES_REDSTONE)) return 5;
        if (state.is(Tags.Blocks.ORES_COPPER) || state.is(Tags.Blocks.ORES_QUARTZ)) return 4;
        if (state.is(Tags.Blocks.ORES_COAL)) return 3;
        if (state.is(Tags.Blocks.ORES)) return 7;
        return 0;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || !(event.getPlayer() instanceof ServerPlayer player) || player.isCreative()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        BlockState state = event.getState();
        BlockPos pos = event.getPos();
        var rnd = player.getRandom();

        float ore = oreValue(state);
        if (ore > 0) {
            Progression.addSkillXp(player, Skill.MINING, ore);
            SkyData.get(player).addStat("ores_mined", 1);
            int prospector = Perks.rank(player, "mining.prospector");
            if (prospector > 0 && rnd.nextFloat() < 0.1f * prospector) {
                for (ItemStack drop : Block.getDrops(state, level, pos, level.getBlockEntity(pos), player, player.getMainHandItem())) {
                    Block.popResource(level, pos, drop);
                }
            }
            if (!CHAIN_BREAKING.get() && player.isCrouching() && Perks.has(player, "mining.vein_miner")) {
                chainBreak(player, level, pos, state.getBlock(), 24, false);
            }
        } else if (state.is(DiggingRules.HARD) || state.is(DiggingRules.DEEP)) {
            Progression.addSkillXp(player, Skill.MINING, 0.3f);
            if (Perks.has(player, "mining.geologist") && rnd.nextFloat() < 0.02f) {
                ItemStack gem = switch (rnd.nextInt(4)) {
                    case 0 -> new ItemStack(Items.AMETHYST_SHARD);
                    case 1 -> new ItemStack(Items.LAPIS_LAZULI, 2);
                    case 2 -> new ItemStack(Items.EMERALD);
                    default -> new ItemStack(Items.DIAMOND);
                };
                Block.popResource(level, pos, gem);
            }
        } else if (state.is(DiggingRules.SOFT)) {
            Progression.addSkillXp(player, Skill.MINING, 0.15f);
        } else if (state.is(BlockTags.LOGS)) {
            Progression.addSkillXp(player, Skill.WOODCUTTING, 2.5f);
            SkyData.get(player).addStat("logs_chopped", 1);
            if (!CHAIN_BREAKING.get() && player.isCrouching() && Perks.has(player, "woodcutting.timber")) {
                chainBreak(player, level, pos, null, 96, true);
            }
        } else if (state.is(BlockTags.LEAVES) && Perks.has(player, "woodcutting.forager") && rnd.nextFloat() < 0.1f) {
            Block.popResource(level, pos, new ItemStack(rnd.nextBoolean() ? Items.APPLE : Items.STICK));
        }
    }

    /** Breaks connected blocks (ore veins, or whole trees when {@code logs} is true). */
    private static void chainBreak(ServerPlayer player, ServerLevel level, BlockPos origin, Block match, int max, boolean logs) {
        CHAIN_BREAKING.set(true);
        try {
            Deque<BlockPos> queue = new ArrayDeque<>();
            Set<BlockPos> seen = new HashSet<>();
            queue.add(origin);
            seen.add(origin);
            int broken = 0;
            while (!queue.isEmpty() && broken < max) {
                BlockPos current = queue.poll();
                for (BlockPos next : BlockPos.betweenClosed(current.offset(-1, -1, -1), current.offset(1, 1, 1))) {
                    if (seen.contains(next)) continue;
                    BlockState s = level.getBlockState(next);
                    boolean ok = logs ? s.is(BlockTags.LOGS) : s.is(match);
                    if (!ok) continue;
                    BlockPos immutable = next.immutable();
                    seen.add(immutable);
                    queue.add(immutable);
                    if (player.gameMode.destroyBlock(immutable)) broken++;
                    if (broken >= max) break;
                }
            }
        } finally {
            CHAIN_BREAKING.set(false);
        }
    }

    // ------------------------------------------------------------------ fishing

    @SubscribeEvent
    public static void onFished(ItemFishedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        var rnd = player.getRandom();
        float xp = 8f * event.getDrops().size();
        if (Perks.has(player, "fishing.patient_hands")) xp *= 1.5f;
        Progression.addSkillXp(player, Skill.FISHING, xp);
        SkyData.get(player).addStat("fish_caught", event.getDrops().size());

        List<ItemStack> extra = new ArrayList<>();
        int angler = Perks.rank(player, "fishing.angler");
        if (angler > 0 && rnd.nextFloat() < 0.1f * angler) extra.add(new ItemStack(rnd.nextBoolean() ? Items.COD : Items.SALMON));
        if (Perks.has(player, "fishing.double_catch") && rnd.nextFloat() < 0.25f) {
            for (ItemStack s : event.getDrops()) extra.add(s.copy());
        }
        int treasure = Perks.rank(player, "fishing.treasure_fisher");
        if (treasure > 0 && rnd.nextFloat() < 0.08f * treasure && player.level() instanceof ServerLevel level) {
            LootParams params = new LootParams.Builder(level)
                    .withParameter(LootContextParams.ORIGIN, player.position())
                    .withParameter(LootContextParams.TOOL, player.getMainHandItem())
                    .withLuck(player.getLuck())
                    .create(LootContextParamSets.FISHING);
            extra.addAll(level.getServer().getLootData().getLootTable(BuiltInLootTables.FISHING_TREASURE).getRandomItems(params));
        }
        if (Perks.has(player, "fishing.legendary_angler") && rnd.nextFloat() < 0.02f) {
            ItemStack legendary = new ItemStack(Items.TROPICAL_FISH);
            legendary.setHoverName(Component.translatable("item.skycraft.legendary_fish").withStyle(ChatFormatting.GOLD));
            extra.add(legendary);
            Notifier.message(player, Component.translatable("message.skycraft.legendary_fish"));
            Progression.addSkillXp(player, Skill.FISHING, 100f);
        }
        for (ItemStack stack : extra) {
            if (!player.getInventory().add(stack)) player.drop(stack, false);
        }
    }

    // ------------------------------------------------------------------ hunting

    @SubscribeEvent
    public static void onKill(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        LivingEntity victim = event.getEntity();
        if (victim instanceof Animal) {
            float xp = 4f + victim.getMaxHealth() * 0.8f;
            if (Perks.has(player, "hunting.apex_predator")) xp *= 2f;
            Progression.addSkillXp(player, Skill.HUNTING, xp);
            SkyData.get(player).addStat("animals_killed", 1);
        } else if (victim.getType().is(com.skycraft.combat.CombatHandler.MONSTERS)) {
            Progression.addSkillXp(player, Skill.HUNTING, victim.getMaxHealth() * 0.5f);
        }
        if (victim instanceof net.minecraft.world.entity.monster.Enemy) SkyData.get(player).addStat("enemies_killed", 1);
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player) || !(event.getEntity() instanceof Animal animal)) return;
        var rnd = player.getRandom();
        List<ItemEntity> extra = new ArrayList<>();
        for (ItemEntity drop : event.getDrops()) {
            ItemStack stack = drop.getItem();
            boolean hide = stack.is(Items.LEATHER) || stack.is(Items.RABBIT_HIDE) || stack.is(Items.RABBIT_FOOT) || stack.is(net.minecraft.tags.ItemTags.WOOL);
            boolean meat = stack.isEdible();
            boolean dup = hide && Perks.has(player, "hunting.skinner") && rnd.nextFloat() < 0.5f
                    || meat && Perks.has(player, "hunting.field_dresser") && rnd.nextFloat() < 0.5f
                    || Perks.has(player, "hunting.apex_predator") && rnd.nextFloat() < 0.25f;
            if (dup) extra.add(new ItemEntity(animal.level(), drop.getX(), drop.getY(), drop.getZ(), stack.copy()));
        }
        if (Perks.has(player, "hunting.skinner") && rnd.nextFloat() < 0.3f) {
            extra.add(new ItemEntity(animal.level(), animal.getX(), animal.getY(), animal.getZ(), new ItemStack(Items.LEATHER)));
        }
        event.getDrops().addAll(extra);
    }

    /** Hunting "Beast Lore": wild predators leave you alone unless you attack them first. */
    @SubscribeEvent
    public static void onTarget(LivingChangeTargetEvent event) {
        if (event.getNewTarget() instanceof Player player && Perks.has(player, "hunting.beast_lore")) {
            LivingEntity mob = event.getEntity();
            boolean beast = mob instanceof Animal || mob instanceof Wolf || mob instanceof PolarBear;
            if (beast && mob.getLastHurtByMob() != player) event.setCanceled(true);
        }
    }
}
