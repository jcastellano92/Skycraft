package com.skycraft.arsenal.item;

import com.skycraft.arsenal.ArsenalTags;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Dawnguard crossbows. They load arrows and bolts ({@code #skycraft:bolts}), hit harder than bows (+{@code bonus}
 * base damage) and pierce armor: the arsenal events let their bolts ignore {@code armorPierce} of the target's armor.
 */
public class SkyCrossbowItem extends CrossbowItem {
    public final float bonusDamage;
    public final float armorPierce;

    public SkyCrossbowItem(float bonusDamage, float armorPierce, Properties props) {
        super(props);
        this.bonusDamage = bonusDamage;
        this.armorPierce = armorPierce;
    }

    @Override
    public Predicate<ItemStack> getAllSupportedProjectiles() {
        return stack -> ARROW_ONLY.test(stack) || stack.is(ArsenalTags.BOLTS);
    }

    @Override
    public Predicate<ItemStack> getSupportedHeldProjectiles() {
        return stack -> ARROW_OR_FIREWORK.test(stack) || stack.is(ArsenalTags.BOLTS);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.skycraft.arsenal.crossbow_damage", String.format(Locale.ROOT, "+%.1f", bonusDamage))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.skycraft.arsenal.armor_pierce", Math.round(armorPierce * 100))
                .withStyle(ChatFormatting.BLUE));
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(com.skycraft.arsenal.client.ArsenalItemExtensions.CROSSBOW);
    }
}
