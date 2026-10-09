package com.skycraft.crafting;

import com.skycraft.Skycraft;
import com.skycraft.crafting.item.SkyAxeItem;
import com.skycraft.crafting.item.SkyBowItem;
import com.skycraft.crafting.item.SkyMeleeItem;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Every item of the smithing module: raw ores, ingots, creature parts, gems, jewelry, 72 Skyrim weapons, 40 armor
 * pieces and the block items of ore veins and stations. Also records each item's gold value (see
 * {@link CraftingValues}; the same numbers ship in {@code data/skycraft/skycraft_values/crafting.json}).
 */
public final class CraftingItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Skycraft.MODID);

    /** Gold value by item id path (filled during class init). */
    static final Map<String, Integer> VALUES = new HashMap<>();

    public static final Map<SkyOre, RegistryObject<Item>> RAW = new EnumMap<>(SkyOre.class);
    public static final Map<SkyOre, RegistryObject<Item>> INGOTS = new EnumMap<>(SkyOre.class);
    public static final Map<SmithingTier, Map<WeaponType, RegistryObject<Item>>> WEAPONS = new EnumMap<>(SmithingTier.class);
    public static final Map<SkyArmorMaterial, Map<ArmorItem.Type, RegistryObject<Item>>> ARMOR = new EnumMap<>(SkyArmorMaterial.class);
    public static final Map<String, RegistryObject<Item>> BLOCK_ITEMS = new LinkedHashMap<>();

    public static final ArmorItem.Type[] ARMOR_TYPES = {ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS};

    static {
        for (SkyOre ore : SkyOre.values()) {
            RAW.put(ore, simple(ore.rawId(), ore.rawValue));
            INGOTS.put(ore, simple(ore.ingotId, ore.ingotValue));
        }
    }

    // ------------------------------------------------------------------ materials
    public static final RegistryObject<Item> STEEL_INGOT = simple("steel_ingot", 20);
    public static final RegistryObject<Item> DWARVEN_METAL_INGOT = simple("dwarven_metal_ingot", 30);
    public static final RegistryObject<Item> DWARVEN_SCRAP = simple("dwarven_scrap", 20);
    public static final RegistryObject<Item> LEATHER_STRIPS = simple("leather_strips", 3);
    public static final RegistryObject<Item> HIDE = simple("hide", 10);
    public static final RegistryObject<Item> FIREWOOD = simple("firewood", 5);

    // ------------------------------------------------------------------ creature parts (dropped by creatures' loot tables)
    public static final RegistryObject<Item> DAEDRA_HEART = item("daedra_heart", 250, () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> DRAGON_BONE = item("dragon_bone", 500, () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON).fireResistant()));
    public static final RegistryObject<Item> DRAGON_SCALE = item("dragon_scale", 250, () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON).fireResistant()));
    public static final RegistryObject<Item> GIANTS_TOE = simple("giants_toe", 20);
    public static final RegistryObject<Item> TROLL_FAT = simple("troll_fat", 15);
    public static final RegistryObject<Item> SKEEVER_TAIL = simple("skeever_tail", 3);
    public static final RegistryObject<Item> BONE_MEAL_DRAUGR = simple("bone_meal_draugr", 5);

    // ------------------------------------------------------------------ gems & jewelry
    public static final RegistryObject<Item> GARNET = simple("garnet", 100);
    public static final RegistryObject<Item> RUBY = simple("ruby", 200);
    public static final RegistryObject<Item> SAPPHIRE = simple("sapphire", 250);
    public static final RegistryObject<Item> FLAWLESS_GARNET = item("flawless_garnet", 150, () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> FLAWLESS_RUBY = item("flawless_ruby", 275, () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> FLAWLESS_SAPPHIRE = item("flawless_sapphire", 350, () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> FLAWLESS_DIAMOND = item("flawless_diamond", 1000, () -> new Item(new Item.Properties().rarity(Rarity.RARE)));

    public static final RegistryObject<Item> SILVER_RING = jewelry("silver_ring", 30);
    public static final RegistryObject<Item> GOLD_RING = jewelry("gold_ring", 75);
    public static final RegistryObject<Item> SILVER_NECKLACE = jewelry("silver_necklace", 60);
    public static final RegistryObject<Item> GOLD_NECKLACE = jewelry("gold_necklace", 120);
    public static final RegistryObject<Item> SILVER_GARNET_RING = jewelry("silver_garnet_ring", 200);
    public static final RegistryObject<Item> GOLD_RUBY_NECKLACE = jewelry("gold_ruby_necklace", 450);
    public static final RegistryObject<Item> GOLD_DIAMOND_RING = jewelry("gold_diamond_ring", 900);

    public static final RegistryObject<Item> COPPER_CIRCLET = jewelry("copper_circlet", 180);
    public static final RegistryObject<Item> SILVER_CIRCLET = jewelry("silver_circlet", 350);
    public static final RegistryObject<Item> GOLD_CIRCLET = jewelry("gold_circlet", 550);
    public static final RegistryObject<Item> JADE_CIRCLET = jewelry("jade_circlet", 750);
    public static final RegistryObject<Item> AMULET_OF_TALOS = jewelry("amulet_of_talos", 300);
    public static final RegistryObject<Item> AMULET_OF_MARA = jewelry("amulet_of_mara", 300);
    public static final RegistryObject<Item> JADE_PENDANT = jewelry("jade_pendant", 280);
    public static final RegistryObject<Item> SAPPHIRE_PENDANT = jewelry("sapphire_pendant", 450);

    // ------------------------------------------------------------------ weapons & armor
    static {
        for (SmithingTier tier : SmithingTier.values()) {
            Map<WeaponType, RegistryObject<Item>> byType = new EnumMap<>(WeaponType.class);
            for (WeaponType type : WeaponType.values()) {
                int value = Math.round(tier.swordValue * type.valueMult);
                byType.put(type, item(type.itemId(tier), value, () -> weapon(tier, type)));
            }
            WEAPONS.put(tier, byType);
        }
        for (SkyArmorMaterial mat : SkyArmorMaterial.values()) {
            Map<ArmorItem.Type, RegistryObject<Item>> byType = new EnumMap<>(ArmorItem.Type.class);
            for (ArmorItem.Type type : ARMOR_TYPES) {
                int value = Math.round(mat.cuirassValue * SkyArmorMaterial.valueMult(type));
                byType.put(type, item(mat.itemId(type), value, () -> {
                    Item.Properties props = new Item.Properties().rarity(mat.rarity);
                    if (mat.fireResistant) props.fireResistant();
                    return new ArmorItem(mat, type, props);
                }));
            }
            ARMOR.put(mat, byType);
        }
        CraftingBlocks.ALL.forEach((id, block) ->
                BLOCK_ITEMS.put(id, ITEMS.register(id, () -> new BlockItem(block.get(), new Item.Properties()))));
    }

    private CraftingItems() {}

    private static Item weapon(SmithingTier tier, WeaponType type) {
        Item.Properties props = new Item.Properties().rarity(tier.rarity);
        if (tier.fireResistant) props.fireResistant();
        if (type == WeaponType.BOW) {
            props.durability(200 + Math.round(tier.getUses() * 0.8f));
            return new SkyBowItem(tier, props);
        }
        props.durability(Math.max(1, Math.round(tier.getUses() * type.durabilityMult)));
        if (type.axe()) return new SkyAxeItem(tier, type, props);
        return new SkyMeleeItem(tier, type, props);
    }

    private static RegistryObject<Item> simple(String id, int value) {
        return item(id, value, () -> new Item(new Item.Properties()));
    }

    private static RegistryObject<Item> jewelry(String id, int value) {
        return item(id, value, () -> new Item(new Item.Properties().stacksTo(16)));
    }

    private static RegistryObject<Item> item(String id, int value, Supplier<Item> factory) {
        VALUES.put(id, value);
        return ITEMS.register(id, factory);
    }

    // ------------------------------------------------------------------ lookups

    public static Item ingot(SkyOre ore) {
        return INGOTS.get(ore).get();
    }

    public static Item raw(SkyOre ore) {
        return RAW.get(ore).get();
    }

    public static Item weaponItem(SmithingTier tier, WeaponType type) {
        return WEAPONS.get(tier).get(type).get();
    }

    public static Item armor(SkyArmorMaterial mat, ArmorItem.Type type) {
        return ARMOR.get(mat).get(type).get();
    }
}
