package com.skycraft.arsenal.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Spellbreaker, Peryite's Dwemer shield: while blocking, a ward stops incoming spells (see ArtifactEvents). */
public class SpellbreakerItem extends ShieldItem implements ArtifactItem {
    public static final String ID = "spellbreaker";

    public SpellbreakerItem(Properties props) {
        super(props);
    }

    @Override
    public String artifactId() {
        return ID;
    }

    @Override
    public String getDescriptionId(ItemStack stack) {
        // ShieldItem appends the banner colour to the description id; Spellbreaker never carries a banner.
        return getDescriptionId();
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
        ArtifactItem.tooltip(ID, tooltip);
    }
}
