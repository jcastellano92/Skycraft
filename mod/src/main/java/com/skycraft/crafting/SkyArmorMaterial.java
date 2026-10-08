package com.skycraft.crafting;

import com.google.common.base.Suppliers;
import com.skycraft.Skycraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.function.Supplier;

/**
 * Skyrim armor sets. Heavy: Steel, Dwarven, Orcish, Ebony, Daedric, Dragonplate (vanilla iron is Skyrim iron).
 * Light: Hide, Elven, Glass, Dragonscale (vanilla leather is Skyrim leather).
 *
 * <p>{@link #getName()} is {@code skycraft:<id>} so Forge resolves the layer textures at
 * {@code assets/skycraft/textures/models/armor/<id>_layer_1.png} / {@code _layer_2.png}.</p>
 */
public enum SkyArmorMaterial implements ArmorMaterial {
    // id, heavy, durability mult, defense {helmet, chest, legs, boots}, enchant, toughness, knockback, cuirass value
    HIDE("hide", false, 6, new int[]{1, 3, 2, 1}, 15, 0f, 0f, 50, null, "smithing.steel_smithing", "leather", Rarity.COMMON, false),
    ELVEN("elven", false, 18, new int[]{2, 5, 4, 2}, 20, 0.5f, 0f, 225, "smithing.elven_smithing", "smithing.elven_smithing", "elven", Rarity.UNCOMMON, false),
    GLASS("glass", false, 22, new int[]{3, 6, 5, 2}, 18, 1.0f, 0f, 900, "smithing.glass_smithing", "smithing.glass_smithing", "glass", Rarity.UNCOMMON, false),
    DRAGONSCALE("dragonscale", false, 35, new int[]{3, 7, 6, 3}, 16, 2.0f, 0f, 1500, "smithing.dragon_armor", "smithing.dragon_armor", "dragon", Rarity.RARE, true),
    STEEL("steel", true, 18, new int[]{2, 6, 5, 2}, 9, 0.5f, 0f, 275, "smithing.steel_smithing", "smithing.steel_smithing", "steel", Rarity.COMMON, false),
    DWARVEN("dwarven", true, 22, new int[]{2, 7, 5, 2}, 10, 1.0f, 0f, 400, "smithing.dwarven_smithing", "smithing.dwarven_smithing", "dwarven", Rarity.COMMON, false),
    ORCISH("orcish", true, 26, new int[]{3, 7, 5, 3}, 10, 1.5f, 0f, 1000, "smithing.orcish_smithing", "smithing.orcish_smithing", "orcish", Rarity.COMMON, false),
    EBONY("ebony", true, 33, new int[]{3, 8, 6, 3}, 12, 2.5f, 0.05f, 1500, "smithing.ebony_smithing", "smithing.ebony_smithing", "ebony", Rarity.UNCOMMON, false),
    DAEDRIC("daedric", true, 37, new int[]{3, 8, 6, 3}, 15, 3.5f, 0.1f, 3200, "smithing.daedric_smithing", "smithing.daedric_smithing", "daedric", Rarity.RARE, true),
    DRAGONPLATE("dragonplate", true, 40, new int[]{4, 9, 7, 4}, 14, 4.0f, 0.15f, 2125, "smithing.dragon_armor", "smithing.dragon_armor", "dragon", Rarity.RARE, true);

    /** Vanilla durability per piece type (multiplied by the material's durability multiplier). */
    private static final int[] HEALTH = {11, 16, 15, 13};

    public final String id;
    public final boolean heavy;
    private final int durability;
    private final int[] defense;
    private final int enchantment;
    private final float toughness;
    private final float knockback;
    /** Gold value of the cuirass; other pieces scale it. */
    public final int cuirassValue;
    public final String perk;
    public final String temperPerk;
    public final String category;
    public final Rarity rarity;
    public final boolean fireResistant;
    private final Supplier<Ingredient> repair = Suppliers.memoize(() -> Ingredient.of(material()));

    SkyArmorMaterial(String id, boolean heavy, int durability, int[] defense, int enchantment, float toughness, float knockback,
                     int cuirassValue, String perk, String temperPerk, String category, Rarity rarity, boolean fireResistant) {
        this.id = id;
        this.heavy = heavy;
        this.durability = durability;
        this.defense = defense;
        this.enchantment = enchantment;
        this.toughness = toughness;
        this.knockback = knockback;
        this.cuirassValue = cuirassValue;
        this.perk = perk;
        this.temperPerk = temperPerk;
        this.category = category;
        this.rarity = rarity;
        this.fireResistant = fireResistant;
    }

    public static int index(ArmorItem.Type type) {
        return switch (type) {
            case HELMET -> 0;
            case CHESTPLATE -> 1;
            case LEGGINGS -> 2;
            default -> 3;
        };
    }

    /** Value multiplier per piece relative to the cuirass. */
    public static float valueMult(ArmorItem.Type type) {
        return switch (index(type)) {
            case 0 -> 0.5f;
            case 1 -> 1.0f;
            case 2 -> 0.7f;
            default -> 0.4f;
        };
    }

    /** Item id suffix per piece: helmet, chestplate, leggings, boots. */
    public static String pieceName(ArmorItem.Type type) {
        return switch (index(type)) {
            case 0 -> "helmet";
            case 1 -> "chestplate";
            case 2 -> "leggings";
            default -> "boots";
        };
    }

    public String itemId(ArmorItem.Type type) {
        return id + "_" + pieceName(type);
    }

    /** Main crafting and tempering material. */
    public Item material() {
        return switch (this) {
            case HIDE -> Items.LEATHER;
            case ELVEN -> CraftingItems.ingot(SkyOre.MOONSTONE);
            case GLASS -> CraftingItems.ingot(SkyOre.MALACHITE);
            case DRAGONSCALE -> CraftingItems.DRAGON_SCALE.get();
            case STEEL -> CraftingItems.STEEL_INGOT.get();
            case DWARVEN -> CraftingItems.DWARVEN_METAL_INGOT.get();
            case ORCISH -> CraftingItems.ingot(SkyOre.ORICHALCUM);
            case EBONY, DAEDRIC -> CraftingItems.ingot(SkyOre.EBONY);
            case DRAGONPLATE -> CraftingItems.DRAGON_BONE.get();
        };
    }

    @Override
    public int getDurabilityForType(ArmorItem.Type type) {
        return HEALTH[index(type)] * durability;
    }

    @Override
    public int getDefenseForType(ArmorItem.Type type) {
        return defense[index(type)];
    }

    @Override
    public int getEnchantmentValue() {
        return enchantment;
    }

    @Override
    public SoundEvent getEquipSound() {
        return switch (this) {
            case HIDE, DRAGONSCALE -> SoundEvents.ARMOR_EQUIP_LEATHER;
            case ELVEN -> SoundEvents.ARMOR_EQUIP_GOLD;
            case GLASS -> SoundEvents.ARMOR_EQUIP_DIAMOND;
            case STEEL, DWARVEN, ORCISH -> SoundEvents.ARMOR_EQUIP_IRON;
            case EBONY, DAEDRIC, DRAGONPLATE -> SoundEvents.ARMOR_EQUIP_NETHERITE;
        };
    }

    @Override
    public Ingredient getRepairIngredient() {
        return repair.get();
    }

    @Override
    public String getName() {
        return Skycraft.MODID + ":" + id;
    }

    @Override
    public float getToughness() {
        return toughness;
    }

    @Override
    public float getKnockbackResistance() {
        return knockback;
    }
}
