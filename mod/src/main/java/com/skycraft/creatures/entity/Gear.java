package com.skycraft.creatures.entity;

import com.skycraft.Skycraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

/** Equipment helpers for humanoid creatures. Skyrim weapons from the crafting module are used when present. */
public final class Gear {
    public static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private Gear() {}

    /** A {@code skycraft:<path>} item (owned by another module) or the vanilla fallback when it doesn't exist. */
    public static Item modItem(String path, Item fallback) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(Skycraft.MODID, path));
        return item == null || item == Items.AIR ? fallback : item;
    }

    public static ItemStack pick(RandomSource random, Item... items) {
        return new ItemStack(items[random.nextInt(items.length)]);
    }

    public static ItemStack dyedLeather(Item item, int color) {
        ItemStack stack = new ItemStack(item);
        if (stack.getItem() instanceof DyeableLeatherItem dyeable) dyeable.setColor(stack, color);
        return stack;
    }

    public static Item leather(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> Items.LEATHER_HELMET;
            case CHEST -> Items.LEATHER_CHESTPLATE;
            case LEGS -> Items.LEATHER_LEGGINGS;
            default -> Items.LEATHER_BOOTS;
        };
    }

    public static Item chainmail(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> Items.CHAINMAIL_HELMET;
            case CHEST -> Items.CHAINMAIL_CHESTPLATE;
            case LEGS -> Items.CHAINMAIL_LEGGINGS;
            default -> Items.CHAINMAIL_BOOTS;
        };
    }

    public static Item iron(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> Items.IRON_HELMET;
            case CHEST -> Items.IRON_CHESTPLATE;
            case LEGS -> Items.IRON_LEGGINGS;
            default -> Items.IRON_BOOTS;
        };
    }

    /** Equips {@code stack} in {@code slot} with the given drop chance. */
    public static void equip(Mob mob, EquipmentSlot slot, ItemStack stack, float dropChance) {
        mob.setItemSlot(slot, stack);
        mob.setDropChance(slot, dropChance);
    }
}
