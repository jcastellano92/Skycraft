package com.skycraft.arsenal.item;

import com.skycraft.arsenal.entity.SkyArrow;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/** A Skyrim arrow or crossbow bolt: shoots a {@link SkyArrow} with the kind's base damage and element. */
public class SkyArrowItem extends ArrowItem {
    public final ArrowKind kind;

    public SkyArrowItem(ArrowKind kind, Properties props) {
        super(props);
        this.kind = kind;
    }

    @Override
    public AbstractArrow createArrow(Level level, ItemStack stack, LivingEntity shooter) {
        SkyArrow arrow = new SkyArrow(level, shooter, stack);
        arrow.setBaseDamage(kind.damage);
        if (kind.element == ArrowKind.Element.FIRE) arrow.setSecondsOnFire(100);
        return arrow;
    }

    /** Skyrim arrows never benefit from Infinity. */
    @Override
    public boolean isInfinite(ItemStack stack, ItemStack bow, net.minecraft.world.entity.player.Player player) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skycraft.arsenal.arrow_damage", String.format(Locale.ROOT, "%.1f", kind.damage))
                .withStyle(ChatFormatting.GRAY));
        if (kind.element != ArrowKind.Element.NONE) {
            tooltip.add(Component.translatable("tooltip.skycraft.arsenal.arrow." + kind.element.name().toLowerCase(Locale.ROOT))
                    .withStyle(ChatFormatting.AQUA));
        }
        if (kind.bolt) tooltip.add(Component.translatable("tooltip.skycraft.arsenal.bolt").withStyle(ChatFormatting.DARK_GRAY));
    }
}
