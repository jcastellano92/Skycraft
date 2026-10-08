package com.skycraft.crafting.arcane.alchemy;

import com.skycraft.core.Notifier;
import com.skycraft.core.PlayerData;
import com.skycraft.core.SkyData;
import com.skycraft.core.Skill;
import com.skycraft.crafting.arcane.ArcaneModule;
import com.skycraft.crafting.arcane.ArcaneRegistry;
import com.skycraft.crafting.arcane.effect.ArcaneEffects;
import com.skycraft.perk.Perks;
import com.skycraft.skills.Progression;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Skyrim alchemy: 2-3 different ingredients are combined at an Alchemy Lab; every effect shared by at least two of
 * them goes into the result. The most valuable effect decides whether it is a potion (drinkable) or a poison
 * (splash). Known effects are stored per ingredient in {@code data.module("arcane").getCompound("alchemy_known")}
 * (item id -> bitmask of known effect indices 0..3).
 *
 * <p>Calculation methods are shared by the client screen (preview) and the server (authoritative).</p>
 */
public final class Alchemy {
    public static final String KNOWN = "alchemy_known";
    public static final String VALUE_TAG = "skycraft_value";
    public static final String POISON_TAG = "skycraft_poison";
    public static final String ALCHEMY_TAG = "skycraft_alchemy";

    private Alchemy() {}

    // ------------------------------------------------------------------ knowledge

    public static int knownMask(Player player, Ingredients.Ingredient ing) {
        return SkyData.get(player).module(ArcaneModule.DATA).getCompound(KNOWN).getInt(ing.id());
    }

    public static boolean knows(Player player, Ingredients.Ingredient ing, int index) {
        return (knownMask(player, ing) & (1 << index)) != 0;
    }

    /** Marks effect indices as known; returns the mask of newly learned indices. */
    public static int learn(ServerPlayer player, Ingredients.Ingredient ing, int mask) {
        PlayerData data = SkyData.get(player);
        CompoundTag module = data.module(ArcaneModule.DATA);
        CompoundTag known = module.getCompound(KNOWN);
        int old = known.getInt(ing.id());
        int now = old | (mask & 0xF);
        if (now == old) return 0;
        known.putInt(ing.id(), now);
        module.put(KNOWN, known);
        data.markDirty();
        return now & ~old;
    }

    /** Reveals the first {@code count} unknown effects of the ingredient (eating / tasting). */
    public static void discoverByTasting(ServerPlayer player, Ingredients.Ingredient ing) {
        int count = 1 + Perks.rank(player, "alchemy.experimenter");
        int known = knownMask(player, ing);
        int add = 0;
        for (int i = 0; i < 4 && count > 0; i++) {
            if ((known & (1 << i)) == 0) {
                add |= 1 << i;
                count--;
            }
        }
        int learned = learn(player, ing, add);
        SkyData.get(player).addStat("ingredients_eaten", 1);
        announce(player, ing, learned);
    }

    private static void announce(ServerPlayer player, Ingredients.Ingredient ing, int learned) {
        if (learned == 0) return;
        Component name = ing.item() == null ? Component.literal(ing.id()) : new ItemStack(ing.item()).getHoverName();
        for (int i = 0; i < 4; i++) {
            if ((learned & (1 << i)) != 0) {
                Notifier.message(player, Component.translatable("message.skycraft.alchemy.learned_effect", name, ing.effects()[i].coloredName()));
            }
        }
    }

    // ------------------------------------------------------------------ brewing math

    /** One effect of a brewed potion. */
    public record BrewEffect(AlchemyEffect effect, float strength) {
        public int amplifier() {
            return effect.amplifier(strength);
        }

        public int duration() {
            return effect.durationTicks(strength);
        }

        public float value() {
            return effect.value(strength);
        }

        public MobEffectInstance toInstance() {
            return new MobEffectInstance(effect.mobEffect(), duration(), amplifier());
        }
    }

    /** The result of combining ingredients. {@code effects} is sorted most valuable first. */
    public record Brew(List<BrewEffect> effects, boolean poison) {
        public boolean isEmpty() {
            return effects.isEmpty();
        }

        public int value() {
            float v = 0;
            for (BrewEffect e : effects) v += e.value();
            return Math.max(1, Math.round(v));
        }

        public AlchemyEffect main() {
            return effects.get(0).effect();
        }
    }

    /** Effects carried by at least two of the ingredients, in order of first appearance. */
    public static List<AlchemyEffect> sharedEffects(List<Ingredients.Ingredient> ings) {
        Map<AlchemyEffect, Integer> counts = new LinkedHashMap<>();
        for (Ingredients.Ingredient ing : ings) {
            for (AlchemyEffect e : ing.effects()) counts.merge(e, 1, Integer::sum);
        }
        List<AlchemyEffect> out = new ArrayList<>();
        counts.forEach((e, n) -> {
            if (n >= 2) out.add(e);
        });
        return out;
    }

    /**
     * Strength multiplier for an effect: (1 + skill/100 * 1.5) * (1 + 0.2 * Alchemist rank), * 1.25 for Physician
     * (restore effects), Benefactor (beneficial) and Poisoner (harmful), * (1 + 0.1 per Fortify Alchemy level).
     */
    public static float strength(Player player, AlchemyEffect effect) {
        PlayerData data = SkyData.get(player);
        float m = (1f + data.getSkill(Skill.ALCHEMY) / 100f * 1.5f) * (1f + 0.2f * Perks.rank(player, "alchemy.alchemist"));
        if (effect.restore && Perks.has(player, "alchemy.physician")) m *= 1.25f;
        if (effect.positive && Perks.has(player, "alchemy.benefactor")) m *= 1.25f;
        if (!effect.positive && Perks.has(player, "alchemy.poisoner")) m *= 1.25f;
        m *= 1f + 0.1f * ArcaneEffects.level(player, ArcaneEffects.FORTIFY_ALCHEMY);
        return m;
    }

    public static Brew brew(Player player, List<Ingredients.Ingredient> ings) {
        List<BrewEffect> list = new ArrayList<>();
        for (AlchemyEffect e : sharedEffects(ings)) list.add(new BrewEffect(e, strength(player, e)));
        if (list.isEmpty()) return new Brew(List.of(), false);
        list.sort(Comparator.comparingDouble((BrewEffect b) -> b.value()).reversed());
        boolean poison = !list.get(0).effect().positive;
        if (Perks.has(player, "alchemy.purity")) {
            list.removeIf(b -> b.effect().positive == poison);
        }
        return new Brew(List.copyOf(list), poison);
    }

    /** Builds the potion (or splash poison) item for a brew. */
    public static ItemStack createItem(Brew brew) {
        ItemStack stack = new ItemStack(brew.poison() ? Items.SPLASH_POTION : Items.POTION);
        List<MobEffectInstance> instances = new ArrayList<>();
        for (BrewEffect e : brew.effects()) instances.add(e.toInstance());
        PotionUtils.setCustomEffects(stack, instances);
        CompoundTag tag = stack.getOrCreateTag();
        tag.putInt("CustomPotionColor", PotionUtils.getColor(instances));
        tag.putInt(VALUE_TAG, brew.value());
        tag.putBoolean(ALCHEMY_TAG, true);
        if (brew.poison()) tag.putBoolean(POISON_TAG, true);
        stack.setHoverName(Component.translatable(brew.poison() ? "item.skycraft.poison_of" : "item.skycraft.potion_of",
                brew.main().displayName()).withStyle(s -> s.withItalic(false)));
        return stack;
    }

    // ------------------------------------------------------------------ server actions

    /** Combines the ingredients in the given inventory slots (2 or 3 different ingredients). */
    public static void combine(ServerPlayer player, BlockPos pos, int[] slots) {
        if (!ArcaneModule.atStation(player, pos, ArcaneRegistry.ALCHEMY_LAB.get())) return;
        if (slots.length < 2 || slots.length > 3) return;
        Inventory inv = player.getInventory();
        List<Ingredients.Ingredient> ings = new ArrayList<>();
        Set<Integer> used = new HashSet<>();
        Set<String> ids = new HashSet<>();
        for (int slot : slots) {
            if (slot < 0 || slot >= inv.getContainerSize() || !used.add(slot)) return;
            Ingredients.Ingredient ing = Ingredients.get(inv.getItem(slot));
            if (ing == null || !ids.add(ing.id())) return;
            ings.add(ing);
        }

        Brew brew = brew(player, ings);
        for (int slot : slots) inv.getItem(slot).shrink(1);
        inv.setChanged();

        if (brew.isEmpty()) {
            player.level().playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.6f, 1.2f);
            Notifier.message(player, Component.translatable("message.skycraft.alchemy.failed"));
            return;
        }

        // learn every effect that matched (even those removed by Purity)
        List<AlchemyEffect> shared = sharedEffects(ings);
        for (Ingredients.Ingredient ing : ings) {
            int mask = 0;
            for (AlchemyEffect e : shared) {
                int idx = ing.indexOf(e);
                if (idx >= 0) mask |= 1 << idx;
            }
            announce(player, ing, learn(player, ing, mask));
        }

        ItemStack result = createItem(brew);
        if (!inv.add(result)) player.drop(result, false);
        player.level().playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 1f, 1f);
        Progression.addSkillXp(player, Skill.ALCHEMY, brew.value());
        SkyData.get(player).addStat(brew.poison() ? "poisons_mixed" : "potions_mixed", 1);
        Notifier.message(player, Component.translatable("message.skycraft.alchemy.created", result.getHoverName()));
    }

    /** Eats one ingredient from a slot to learn its effects (for ingredients that aren't food). */
    public static void taste(ServerPlayer player, BlockPos pos, int slot) {
        if (!ArcaneModule.atStation(player, pos, ArcaneRegistry.ALCHEMY_LAB.get())) return;
        Inventory inv = player.getInventory();
        if (slot < 0 || slot >= inv.getContainerSize()) return;
        ItemStack stack = inv.getItem(slot);
        Ingredients.Ingredient ing = Ingredients.get(stack);
        if (ing == null) return;
        if (knownMask(player, ing) == 0xF) {
            Notifier.message(player, Component.translatable("message.skycraft.alchemy.all_known"));
            return;
        }
        stack.shrink(1);
        inv.setChanged();
        player.level().playSound(null, player.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.PLAYERS, 0.8f, 1f);
        discoverByTasting(player, ing);
    }
}
