package com.skycraft.crafting;

import com.skycraft.Skycraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TieredItem;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;

/**
 * Base gold values used by this module for Smithing XP (Skyrim awards smithing XP based on the value of the created
 * item). The economy module owns the real value API and loads the same numbers from
 * {@code data/skycraft/skycraft_values/crafting.json}.
 */
public final class CraftingValues {
    private static final Map<Item, Integer> VANILLA = new HashMap<>();

    static {
        VANILLA.put(Items.IRON_INGOT, 7);
        VANILLA.put(Items.GOLD_INGOT, 15);
        VANILLA.put(Items.COPPER_INGOT, 3);
        VANILLA.put(Items.LEATHER, 5);
        VANILLA.put(Items.DIAMOND, 100);
        VANILLA.put(Items.NETHERITE_SCRAP, 150);
        VANILLA.put(Items.IRON_HELMET, 60);
        VANILLA.put(Items.IRON_CHESTPLATE, 125);
        VANILLA.put(Items.IRON_LEGGINGS, 80);
        VANILLA.put(Items.IRON_BOOTS, 25);
        VANILLA.put(Items.LEATHER_HELMET, 25);
        VANILLA.put(Items.LEATHER_CHESTPLATE, 50);
        VANILLA.put(Items.LEATHER_LEGGINGS, 35);
        VANILLA.put(Items.LEATHER_BOOTS, 20);
    }

    private CraftingValues() {}

    public static int of(ItemStack stack) {
        return stack.isEmpty() ? 0 : of(stack.getItem());
    }

    public static int of(Item item) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        if (id != null && id.getNamespace().equals(Skycraft.MODID)) {
            Integer v = CraftingItems.VALUES.get(id.getPath());
            if (v != null) return v;
        }
        Integer v = VANILLA.get(item);
        if (v != null) return v;
        if (item instanceof ArmorItem armor) return 10 + armor.getDefense() * 12;
        if (item instanceof TieredItem tiered) return Math.round(10 + tiered.getTier().getAttackDamageBonus() * 15);
        return 5;
    }
}
