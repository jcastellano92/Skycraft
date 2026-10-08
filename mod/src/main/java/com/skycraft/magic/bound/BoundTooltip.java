package com.skycraft.magic.bound;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

final class BoundTooltip {
    private BoundTooltip() {}

    static void add(ItemStack stack, @Nullable Level level, List<Component> tooltip) {
        tooltip.add(Component.translatable("tooltip.skycraft.bound_weapon").withStyle(ChatFormatting.LIGHT_PURPLE));
        if (level != null && stack.hasTag()) {
            tooltip.add(Component.translatable("tooltip.skycraft.bound_time", BoundWeapons.secondsLeft(stack, level)).withStyle(ChatFormatting.GRAY));
        }
    }
}
