package com.skycraft.magic.bound;

import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Bound Bow (Conjuration, Apprentice): needs no arrows, its conjured arrows vanish after use. */
public class BoundBowItem extends BowItem implements BoundWeapons.BoundItem {
    public BoundBowItem(Properties props) {
        super(props.stacksTo(1).rarity(Rarity.UNCOMMON).fireResistant());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player player)) return;
        int charge = this.getUseDuration(stack) - timeLeft;
        float power = getPowerForTime(charge);
        if (power < 0.1f) return;
        if (!level.isClientSide) {
            Arrow arrow = new Arrow(level, player);
            arrow.shootFromRotation(player, player.getXRot(), player.getYRot(), 0f, power * 3.2f, 0.8f);
            if (power >= 1f) arrow.setCritArrow(true);
            arrow.setBaseDamage(arrow.getBaseDamage() + 1.0);
            arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
            arrow.getPersistentData().putBoolean(BoundWeapons.ARROW_TAG, true);
            level.addFreshEntity(arrow);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1f,
                1f / (level.getRandom().nextFloat() * 0.4f + 1.2f) + power * 0.5f);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6f, 1.6f);
        player.awardStat(Stats.ITEM_USED.get(this));
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        BoundWeapons.inventoryTick(stack, level, entity);
    }

    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        return BoundWeapons.onEntityItemUpdate(entity);
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
    public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        BoundTooltip.add(stack, level, tooltip);
    }
}
