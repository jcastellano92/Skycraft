package com.skycraft.crafting.item;

import com.skycraft.crafting.SmithingTier;
import com.skycraft.crafting.WeaponType;
import net.minecraft.world.item.AxeItem;

/** War axes and battleaxes: real axes, so they still chop wood. */
public class SkyAxeItem extends AxeItem {
    public final SmithingTier smithingTier;
    public final WeaponType type;

    public SkyAxeItem(SmithingTier tier, WeaponType type, Properties props) {
        super(tier, (float) type.damage, type.speed, props);
        this.smithingTier = tier;
        this.type = type;
    }
}
