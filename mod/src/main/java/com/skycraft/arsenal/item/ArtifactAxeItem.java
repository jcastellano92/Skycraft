package com.skycraft.arsenal.item;

import com.skycraft.crafting.SmithingTier;
import com.skycraft.crafting.WeaponType;
import com.skycraft.crafting.item.SkyAxeItem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** A named artifact war axe or battleaxe (Wuuthrad). */
public class ArtifactAxeItem extends SkyAxeItem implements ArtifactItem {
    private final String id;

    public ArtifactAxeItem(String id, SmithingTier tier, WeaponType type, Properties props) {
        super(tier, type, props);
        this.id = id;
    }

    @Override
    public String artifactId() {
        return id;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }

    @Override
    public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        ArtifactItem.tooltip(id, tooltip);
    }
}
