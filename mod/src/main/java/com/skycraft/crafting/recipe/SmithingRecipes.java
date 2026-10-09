package com.skycraft.crafting.recipe;

import com.skycraft.crafting.CraftingItems;
import com.skycraft.crafting.CraftingValues;
import com.skycraft.crafting.SkyArmorMaterial;
import com.skycraft.crafting.SkyOre;
import com.skycraft.crafting.SmithingTier;
import com.skycraft.crafting.StationType;
import com.skycraft.crafting.WeaponType;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Every station recipe, defined in code (Skyrim-like material lists). Built lazily on first use, after registries are
 * populated; identical on client and server so the client screen can list them and the server re-validates by id.
 */
public final class SmithingRecipes {
    /** Category order per station (lang key {@code category.skycraft.smithing.<id>}). */
    public static final Map<StationType, List<String>> CATEGORIES = new EnumMap<>(StationType.class);

    static {
        CATEGORIES.put(StationType.FORGE, List.of("iron", "steel", "elven", "dwarven", "orcish", "glass", "ebony", "daedric", "dragon", "leather", "jewelry"));
        CATEGORIES.put(StationType.SMELTER, List.of("ingots", "alloys"));
        CATEGORIES.put(StationType.TANNING_RACK, List.of("tanning", "leather"));
        CATEGORIES.put(StationType.GRINDSTONE, List.of());
        CATEGORIES.put(StationType.ARMOR_WORKBENCH, List.of());
    }

    private static List<SmithingRecipe> all;
    private static Map<String, SmithingRecipe> byId;
    private static Map<StationType, Map<String, List<SmithingRecipe>>> byStation;

    private SmithingRecipes() {}

    public static synchronized List<SmithingRecipe> all() {
        if (all == null) build();
        return all;
    }

    @Nullable
    public static SmithingRecipe get(String id) {
        all();
        return byId.get(id);
    }

    /** Recipes of a station grouped by category, in display order. */
    public static Map<String, List<SmithingRecipe>> forStation(StationType station) {
        all();
        return byStation.getOrDefault(station, Collections.emptyMap());
    }

    // ------------------------------------------------------------------ definitions

    private static void build() {
        List<SmithingRecipe> out = new ArrayList<>();
        Supplier<Item> iron = () -> Items.IRON_INGOT;
        Supplier<Item> gold = () -> Items.GOLD_INGOT;
        Supplier<Item> strips = CraftingItems.LEATHER_STRIPS;
        Supplier<Item> moonstone = () -> CraftingItems.ingot(SkyOre.MOONSTONE);
        Supplier<Item> silver = () -> CraftingItems.ingot(SkyOre.SILVER);
        Supplier<Item> malachite = () -> CraftingItems.ingot(SkyOre.MALACHITE);

        // ---------------------------------------------------------------- forge: weapons
        for (SmithingTier tier : SmithingTier.values()) {
            for (WeaponType type : WeaponType.values()) {
                List<Cost> costs = new ArrayList<>();
                int main = tier == SmithingTier.DRAGONBONE ? Math.min(type.materialCount, 3) : type.materialCount;
                costs.add(Cost.of(tier::material, main));
                switch (tier) {
                    case STEEL, ORCISH, DWARVEN -> costs.add(Cost.of(iron, 1));
                    case ELVEN -> {
                        costs.add(Cost.of(iron, 1));
                        costs.add(Cost.of(() -> CraftingItems.ingot(SkyOre.QUICKSILVER), 1));
                    }
                    case GLASS -> costs.add(Cost.of(moonstone, 1));
                    case DAEDRIC -> costs.add(Cost.of(CraftingItems.DAEDRA_HEART, 1));
                    case DRAGONBONE -> costs.add(Cost.of(() -> CraftingItems.ingot(SkyOre.EBONY), 1));
                    default -> {
                    }
                }
                if (type == WeaponType.BOW) costs.add(Cost.of(CraftingItems.FIREWOOD, 1));
                costs.add(Cost.of(strips, type.strips));
                Supplier<Item> result = CraftingItems.WEAPONS.get(tier).get(type);
                out.add(recipe("forge/" + type.itemId(tier), StationType.FORGE, tier.category, result, 1, costs, tier.perk));
            }
            if (tier == SmithingTier.IRON) {
                // vanilla iron armor is Skyrim's Iron Armor
                Item[] pieces = {Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS};
                int[] ingots = {3, 5, 4, 2};
                int[] stripCount = {1, 3, 2, 1};
                for (int i = 0; i < 4; i++) {
                    Item piece = pieces[i];
                    out.add(recipe("forge/" + net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(piece).getPath(), StationType.FORGE, "iron",
                            () -> piece, 1, List.of(Cost.of(iron, ingots[i]), Cost.of(strips, stripCount[i])), null));
                }
            }
        }

        // ---------------------------------------------------------------- forge: armor
        int[] stripsByPiece = {1, 3, 2, 1};
        for (SkyArmorMaterial mat : SkyArmorMaterial.values()) {
            for (ArmorItem.Type type : CraftingItems.ARMOR_TYPES) {
                int i = SkyArmorMaterial.index(type);
                List<Cost> costs = armorCosts(mat, i, stripsByPiece[i]);
                Supplier<Item> result = CraftingItems.ARMOR.get(mat).get(type);
                out.add(recipe("forge/" + mat.itemId(type), StationType.FORGE, mat.category, result, 1, costs, mat.perk));
                if (mat == SkyArmorMaterial.HIDE) {
                    out.add(recipe("tanning_rack/" + mat.itemId(type), StationType.TANNING_RACK, "leather", result, 1, costs, null));
                }
            }
        }

        // ---------------------------------------------------------------- forge: jewelry
        out.add(recipe("forge/silver_ring", StationType.FORGE, "jewelry", CraftingItems.SILVER_RING, 1, List.of(Cost.of(silver, 1)), null));
        out.add(recipe("forge/gold_ring", StationType.FORGE, "jewelry", CraftingItems.GOLD_RING, 1, List.of(Cost.of(gold, 1)), null));
        out.add(recipe("forge/silver_necklace", StationType.FORGE, "jewelry", CraftingItems.SILVER_NECKLACE, 1, List.of(Cost.of(silver, 2)), null));
        out.add(recipe("forge/gold_necklace", StationType.FORGE, "jewelry", CraftingItems.GOLD_NECKLACE, 1, List.of(Cost.of(gold, 2)), null));
        out.add(recipe("forge/silver_garnet_ring", StationType.FORGE, "jewelry", CraftingItems.SILVER_GARNET_RING, 1,
                List.of(Cost.of(silver, 1), Cost.of(CraftingItems.GARNET, 1)), null));
        out.add(recipe("forge/gold_ruby_necklace", StationType.FORGE, "jewelry", CraftingItems.GOLD_RUBY_NECKLACE, 1,
                List.of(Cost.of(gold, 2), Cost.of(CraftingItems.FLAWLESS_RUBY, 1)), null));
        out.add(recipe("forge/gold_diamond_ring", StationType.FORGE, "jewelry", CraftingItems.GOLD_DIAMOND_RING, 1,
                List.of(Cost.of(gold, 1), Cost.of(CraftingItems.FLAWLESS_DIAMOND, 1)), null));
        out.add(recipe("forge/copper_circlet", StationType.FORGE, "jewelry", CraftingItems.COPPER_CIRCLET, 1,
                List.of(Cost.of(() -> Items.COPPER_INGOT, 2)), null));
        out.add(recipe("forge/silver_circlet", StationType.FORGE, "jewelry", CraftingItems.SILVER_CIRCLET, 1,
                List.of(Cost.of(silver, 2)), null));
        out.add(recipe("forge/gold_circlet", StationType.FORGE, "jewelry", CraftingItems.GOLD_CIRCLET, 1,
                List.of(Cost.of(gold, 2)), null));
        out.add(recipe("forge/jade_circlet", StationType.FORGE, "jewelry", CraftingItems.JADE_CIRCLET, 1,
                List.of(Cost.of(gold, 2), Cost.of(malachite, 2)), null));
        out.add(recipe("forge/amulet_of_talos", StationType.FORGE, "jewelry", CraftingItems.AMULET_OF_TALOS, 1,
                List.of(Cost.of(iron, 2), Cost.of(silver, 1)), null));
        out.add(recipe("forge/amulet_of_mara", StationType.FORGE, "jewelry", CraftingItems.AMULET_OF_MARA, 1,
                List.of(Cost.of(gold, 2), Cost.of(CraftingItems.GARNET, 1)), null));
        out.add(recipe("forge/jade_pendant", StationType.FORGE, "jewelry", CraftingItems.JADE_PENDANT, 1,
                List.of(Cost.of(silver, 1), Cost.of(malachite, 1)), null));
        out.add(recipe("forge/sapphire_pendant", StationType.FORGE, "jewelry", CraftingItems.SAPPHIRE_PENDANT, 1,
                List.of(Cost.of(silver, 1), Cost.of(CraftingItems.SAPPHIRE, 1)), null));

        // ---------------------------------------------------------------- smelter
        for (SkyOre ore : SkyOre.values()) {
            out.add(recipe("smelter/" + ore.ingotId, StationType.SMELTER, "ingots", CraftingItems.INGOTS.get(ore), 1,
                    List.of(Cost.of(CraftingItems.RAW.get(ore), 1)), null));
        }
        out.add(recipe("smelter/iron_ingot", StationType.SMELTER, "ingots", iron, 1, List.of(Cost.of(() -> Items.RAW_IRON, 1)), null));
        out.add(recipe("smelter/gold_ingot", StationType.SMELTER, "ingots", gold, 1, List.of(Cost.of(() -> Items.RAW_GOLD, 1)), null));
        out.add(recipe("smelter/copper_ingot", StationType.SMELTER, "ingots", () -> Items.COPPER_INGOT, 1, List.of(Cost.of(() -> Items.RAW_COPPER, 1)), null));
        out.add(recipe("smelter/dwarven_metal_ingot", StationType.SMELTER, "ingots", CraftingItems.DWARVEN_METAL_INGOT, 2,
                List.of(Cost.of(CraftingItems.DWARVEN_SCRAP, 1)), null));
        Cost coal = Cost.tag(ItemTags.COALS, () -> Items.COAL, 1);
        out.add(recipe("smelter/steel_ingot", StationType.SMELTER, "alloys", CraftingItems.STEEL_INGOT, 2,
                List.of(Cost.of(iron, 1), Cost.of(() -> CraftingItems.ingot(SkyOre.CORUNDUM), 1), coal), null));
        out.add(recipe("smelter/steel_ingot_from_ore", StationType.SMELTER, "alloys", CraftingItems.STEEL_INGOT, 2,
                List.of(Cost.of(() -> Items.RAW_IRON, 1), Cost.of(() -> CraftingItems.raw(SkyOre.CORUNDUM), 1), coal), null));

        // ---------------------------------------------------------------- tanning rack
        out.add(recipe("tanning_rack/leather", StationType.TANNING_RACK, "tanning", () -> Items.LEATHER, 1,
                List.of(Cost.of(CraftingItems.HIDE, 1)), null));
        out.add(recipe("tanning_rack/leather_from_rabbit_hide", StationType.TANNING_RACK, "tanning", () -> Items.LEATHER, 1,
                List.of(Cost.of(() -> Items.RABBIT_HIDE, 3)), null));
        out.add(recipe("tanning_rack/leather_strips", StationType.TANNING_RACK, "tanning", CraftingItems.LEATHER_STRIPS, 4,
                List.of(Cost.of(() -> Items.LEATHER, 1)), null));
        Item[] leather = {Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS};
        int[] leatherCount = {2, 4, 3, 2};
        int[] leatherStrips = {1, 2, 1, 1};
        for (int i = 0; i < 4; i++) {
            Item piece = leather[i];
            out.add(recipe("tanning_rack/" + net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(piece).getPath(), StationType.TANNING_RACK,
                    "leather", () -> piece, 1, List.of(Cost.of(() -> Items.LEATHER, leatherCount[i]), Cost.of(strips, leatherStrips[i])), null));
        }

        // ---------------------------------------------------------------- index
        Map<String, SmithingRecipe> ids = new LinkedHashMap<>();
        for (SmithingRecipe r : out) ids.put(r.id(), r);
        Map<StationType, Map<String, List<SmithingRecipe>>> stations = new EnumMap<>(StationType.class);
        for (StationType station : StationType.values()) {
            Map<String, List<SmithingRecipe>> cats = new LinkedHashMap<>();
            for (String cat : CATEGORIES.get(station)) {
                List<SmithingRecipe> list = new ArrayList<>();
                for (SmithingRecipe r : out) if (r.station() == station && r.category().equals(cat)) list.add(r);
                if (!list.isEmpty()) cats.put(cat, Collections.unmodifiableList(list));
            }
            stations.put(station, Collections.unmodifiableMap(cats));
        }
        all = Collections.unmodifiableList(out);
        byId = ids;
        byStation = stations;
    }

    private static List<Cost> armorCosts(SkyArmorMaterial mat, int piece, int strips) {
        List<Cost> costs = new ArrayList<>();
        Supplier<Item> main = mat::material;
        int[] mainCount = switch (mat) {
            case HIDE, ELVEN, GLASS, DRAGONSCALE -> new int[]{2, 4, 3, 2};
            case DRAGONPLATE -> new int[]{1, 2, 2, 1};
            default -> new int[]{3, 5, 4, 2};
        };
        int[] scales = {1, 2, 1, 1};
        costs.add(Cost.of(mat == SkyArmorMaterial.HIDE ? CraftingItems.HIDE : main, mainCount[piece]));
        switch (mat) {
            case STEEL, ORCISH, ELVEN -> costs.add(Cost.of(() -> Items.IRON_INGOT, 1));
            case DWARVEN -> costs.add(Cost.of(CraftingItems.STEEL_INGOT, 1));
            case GLASS -> costs.add(Cost.of(() -> CraftingItems.ingot(SkyOre.MOONSTONE), 1));
            case DAEDRIC -> costs.add(Cost.of(CraftingItems.DAEDRA_HEART, 1));
            case DRAGONPLATE -> costs.add(Cost.of(CraftingItems.DRAGON_SCALE, scales[piece]));
            default -> {
            }
        }
        costs.add(Cost.of(CraftingItems.LEATHER_STRIPS, strips));
        return List.copyOf(costs);
    }

    private static SmithingRecipe recipe(String id, StationType station, String category, Supplier<? extends ItemLike> result, int count,
                                         List<Cost> costs, @Nullable String perk) {
        int value = CraftingValues.of(result.get().asItem()) * count;
        return new SmithingRecipe(id, station, category, result, count, List.copyOf(costs), perk, value);
    }
}
