package com.skycraft.crafting;

import com.google.common.base.Suppliers;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.function.Supplier;

/**
 * Skyrim weapon materials, from Iron to Dragonbone. Implements the vanilla {@link Tier} so the weapons are regular
 * swords/axes. Damage bonus steps by 0.5 per tier: an Iron Sword hits like a vanilla iron sword (6), a Daedric Sword
 * (9.5) beats netherite (8) and Dragonbone is the top tier.
 */
public enum SmithingTier implements Tier {
    IRON("iron", "iron", 250, 6.0f, 2.0f, 2, 14, 2.00f, 25, null, "smithing.steel_smithing", Rarity.COMMON, false),
    STEEL("steel", "steel", 400, 6.5f, 2.5f, 2, 12, 2.15f, 45, "smithing.steel_smithing", "smithing.steel_smithing", Rarity.COMMON, false),
    ORCISH("orcish", "orcish", 550, 7.0f, 3.0f, 3, 10, 2.30f, 75, "smithing.orcish_smithing", "smithing.orcish_smithing", Rarity.COMMON, false),
    DWARVEN("dwarven", "dwarven", 700, 7.0f, 3.5f, 3, 12, 2.50f, 135, "smithing.dwarven_smithing", "smithing.dwarven_smithing", Rarity.COMMON, false),
    ELVEN("elven", "elven", 650, 8.0f, 4.0f, 3, 20, 2.65f, 235, "smithing.elven_smithing", "smithing.elven_smithing", Rarity.UNCOMMON, false),
    GLASS("glass", "glass", 600, 8.5f, 4.5f, 3, 18, 2.85f, 410, "smithing.glass_smithing", "smithing.glass_smithing", Rarity.UNCOMMON, false),
    EBONY("ebony", "ebony", 1500, 8.5f, 5.0f, 4, 14, 3.05f, 720, "smithing.ebony_smithing", "smithing.ebony_smithing", Rarity.UNCOMMON, false),
    DAEDRIC("daedric", "daedric", 2000, 9.0f, 5.5f, 4, 16, 3.30f, 1250, "smithing.daedric_smithing", "smithing.daedric_smithing", Rarity.RARE, true),
    DRAGONBONE("dragonbone", "dragon", 2200, 9.5f, 6.0f, 4, 18, 3.50f, 1500, "smithing.dragon_armor", "smithing.dragon_armor", Rarity.RARE, true);

    public final String id;
    /** Forge recipe category. */
    public final String category;
    private final int uses;
    private final float speed;
    private final float damage;
    private final int level;
    private final int enchantment;
    /** Arrow base damage when shot from this tier's bow (vanilla arrows: 2.0). */
    public final float bowDamage;
    /** Gold value of the tier's sword (Skyrim values); other weapon types scale it. */
    public final int swordValue;
    /** Perk needed to forge this tier (null = anyone). */
    public final String perk;
    /** Perk that doubles tempering for this tier and allows Legendary. */
    public final String temperPerk;
    public final Rarity rarity;
    public final boolean fireResistant;
    private final Supplier<Ingredient> repair = Suppliers.memoize(() -> Ingredient.of(material()));

    SmithingTier(String id, String category, int uses, float speed, float damage, int level, int enchantment, float bowDamage,
                 int swordValue, String perk, String temperPerk, Rarity rarity, boolean fireResistant) {
        this.id = id;
        this.category = category;
        this.uses = uses;
        this.speed = speed;
        this.damage = damage;
        this.level = level;
        this.enchantment = enchantment;
        this.bowDamage = bowDamage;
        this.swordValue = swordValue;
        this.perk = perk;
        this.temperPerk = temperPerk;
        this.rarity = rarity;
        this.fireResistant = fireResistant;
    }

    /** The main crafting and tempering material of this tier. */
    public Item material() {
        return switch (this) {
            case IRON -> Items.IRON_INGOT;
            case STEEL -> CraftingItems.STEEL_INGOT.get();
            case ORCISH -> CraftingItems.ingot(SkyOre.ORICHALCUM);
            case DWARVEN -> CraftingItems.DWARVEN_METAL_INGOT.get();
            case ELVEN -> CraftingItems.ingot(SkyOre.MOONSTONE);
            case GLASS -> CraftingItems.ingot(SkyOre.MALACHITE);
            case EBONY, DAEDRIC -> CraftingItems.ingot(SkyOre.EBONY);
            case DRAGONBONE -> CraftingItems.DRAGON_BONE.get();
        };
    }

    @Override
    public int getUses() {
        return uses;
    }

    @Override
    public float getSpeed() {
        return speed;
    }

    @Override
    public float getAttackDamageBonus() {
        return damage;
    }

    @Override
    public int getLevel() {
        return level;
    }

    @Override
    public int getEnchantmentValue() {
        return enchantment;
    }

    @Override
    public Ingredient getRepairIngredient() {
        return repair.get();
    }
}
