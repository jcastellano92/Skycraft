package com.skycraft.crafting.item;

import com.skycraft.crafting.SmithingTier;
import com.skycraft.crafting.WeaponType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraftforge.common.ToolAction;
import net.minecraftforge.common.ToolActions;

/**
 * Daggers, swords, greatswords, maces and warhammers. Maces, warhammers and daggers don't sweep like swords do.
 */
public class SkyMeleeItem extends SwordItem {
    public final SmithingTier smithingTier;
    public final WeaponType type;

    public SkyMeleeItem(SmithingTier tier, WeaponType type, Properties props) {
        super(tier, type.damage, type.speed, props);
        this.smithingTier = tier;
        this.type = type;
    }

    @Override
    public boolean canPerformAction(ItemStack stack, ToolAction toolAction) {
        if (toolAction == ToolActions.SWORD_SWEEP && type != WeaponType.SWORD && type != WeaponType.GREATSWORD) return false;
        return super.canPerformAction(stack, toolAction);
    }
}
