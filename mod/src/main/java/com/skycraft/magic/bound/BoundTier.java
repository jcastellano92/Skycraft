package com.skycraft.magic.bound;

import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;

/** Tier of conjured Daedric weapons: unbreakable (no durability), cannot be repaired or enchanted. */
public enum BoundTier implements Tier {
    INSTANCE;

    @Override
    public int getUses() {
        return 0;
    }

    @Override
    public float getSpeed() {
        return 8f;
    }

    @Override
    public float getAttackDamageBonus() {
        return 2f;
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getLevel() {
        return 3;
    }

    @Override
    public int getEnchantmentValue() {
        return 0;
    }

    @Override
    public Ingredient getRepairIngredient() {
        return Ingredient.EMPTY;
    }
}
