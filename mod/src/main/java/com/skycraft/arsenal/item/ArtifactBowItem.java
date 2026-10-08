package com.skycraft.arsenal.item;

import com.skycraft.crafting.SmithingTier;
import com.skycraft.crafting.item.SkyBowItem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A named artifact bow (Auriel's Bow, Nightingale Bow, Zephyr, Bow of Shadows). Arrows it shoots carry the
 * artifact id in their persistent data ({@link #ARROW_TAG}) so the arsenal events can apply its power on impact.
 */
public class ArtifactBowItem extends SkyBowItem implements ArtifactItem {
    public static final String ARROW_TAG = "skycraft_artifact";

    private final String id;

    public ArtifactBowItem(String id, SmithingTier tier, Properties props) {
        super(tier, props);
        this.id = id;
    }

    @Override
    public String artifactId() {
        return id;
    }

    @Override
    public AbstractArrow customArrow(AbstractArrow arrow) {
        AbstractArrow result = super.customArrow(arrow);
        result.getPersistentData().putString(ARROW_TAG, id);
        return result;
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
