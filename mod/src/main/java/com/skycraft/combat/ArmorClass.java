package com.skycraft.combat;

import com.skycraft.Skycraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Light vs heavy armor classification (tags first, vanilla material heuristic as fallback). */
public final class ArmorClass {
    public static final TagKey<Item> HEAVY = TagKey.create(Registries.ITEM, new ResourceLocation(Skycraft.MODID, "heavy_armor"));
    public static final TagKey<Item> LIGHT = TagKey.create(Registries.ITEM, new ResourceLocation(Skycraft.MODID, "light_armor"));
    private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private ArmorClass() {}

    public static boolean isHeavy(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ArmorItem armor)) return false;
        if (stack.is(HEAVY)) return true;
        if (stack.is(LIGHT)) return false;
        var mat = armor.getMaterial();
        return mat != ArmorMaterials.LEATHER && mat != ArmorMaterials.CHAIN && mat != ArmorMaterials.TURTLE;
    }

    public static boolean isLight(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ArmorItem && !isHeavy(stack);
    }

    public static int countHeavy(LivingEntity entity) {
        int n = 0;
        for (EquipmentSlot slot : ARMOR_SLOTS) if (isHeavy(entity.getItemBySlot(slot))) n++;
        return n;
    }

    public static int countLight(LivingEntity entity) {
        int n = 0;
        for (EquipmentSlot slot : ARMOR_SLOTS) if (isLight(entity.getItemBySlot(slot))) n++;
        return n;
    }

    /** True if all four pieces are armor of the same material (Skyrim "Matching Set"). */
    public static boolean matchingSet(LivingEntity entity) {
        Object material = null;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (!(stack.getItem() instanceof ArmorItem armor)) return false;
            if (material == null) material = armor.getMaterial();
            else if (material != armor.getMaterial()) return false;
        }
        return true;
    }

    public static boolean wearsNoArmor(LivingEntity entity) {
        for (EquipmentSlot slot : ARMOR_SLOTS) if (!entity.getItemBySlot(slot).isEmpty()) return false;
        return true;
    }
}
