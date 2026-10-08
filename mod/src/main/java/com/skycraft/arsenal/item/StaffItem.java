package com.skycraft.arsenal.item;

import com.skycraft.Skycraft;
import com.skycraft.arsenal.ArsenalTags;
import com.skycraft.arsenal.StaffEffects;
import com.skycraft.core.Notifier;
import com.skycraft.core.Skill;
import com.skycraft.skills.Progression;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A Skyrim staff. Right-click casts (or, for streaming staves, hold to channel). Charges are stored in NBT
 * {@value #CHARGES}; a stack without the tag is fully charged. Sneak + use with a filled soul gem
 * ({@code #skycraft:soul_gems}, NBT int {@code soul}) in the other hand recharges it.
 */
public class StaffItem extends Item {
    public static final String CHARGES = "skycraft_charges";
    private static final ResourceLocation AZURAS_STAR = new ResourceLocation(Skycraft.MODID, "azuras_star");
    private static final ResourceLocation BLACK_STAR = new ResourceLocation(Skycraft.MODID, "black_star");

    public final StaffKind kind;

    public StaffItem(StaffKind kind, Properties props) {
        super(props.stacksTo(1));
        this.kind = kind;
    }

    // ------------------------------------------------------------------ charges

    public static int charges(ItemStack stack) {
        if (!(stack.getItem() instanceof StaffItem staff)) return 0;
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(CHARGES)) return staff.kind.maxCharges;
        return Math.max(0, Math.min(staff.kind.maxCharges, tag.getInt(CHARGES)));
    }

    public static void setCharges(ItemStack stack, int charges) {
        if (stack.getItem() instanceof StaffItem staff) {
            stack.getOrCreateTag().putInt(CHARGES, Math.max(0, Math.min(staff.kind.maxCharges, charges)));
        }
    }

    /** Spends one charge (creative players cast for free). Returns false if the staff is empty. */
    public static boolean spend(Player player, ItemStack stack) {
        int c = charges(stack);
        if (c <= 0) return false;
        if (!player.getAbilities().instabuild) setCharges(stack, c - 1);
        return true;
    }

    // ------------------------------------------------------------------ use

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        ItemStack other = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if (player.isShiftKeyDown() && other.is(ArsenalTags.SOUL_GEMS)) {
            if (!level.isClientSide && player instanceof ServerPlayer sp) recharge(sp, stack, other);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (charges(stack) <= 0) {
            if (!level.isClientSide && player instanceof ServerPlayer sp) {
                Notifier.message(sp, Component.translatable("message.skycraft.arsenal.staff_empty", stack.getHoverName()));
                level.playSound(null, player.blockPosition(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.6f, 1.4f);
            }
            return InteractionResultHolder.fail(stack);
        }
        if (kind.stream) {
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(stack);
        }
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            if (StaffEffects.cast(sp, stack, kind)) {
                spend(sp, stack);
                if (kind.cooldown > 0) sp.getCooldowns().addCooldown(this, kind.cooldown);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return kind.stream ? 72000 : 0;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return kind.stream ? UseAnim.BOW : UseAnim.NONE;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (level.isClientSide || !(entity instanceof ServerPlayer player) || !kind.stream) return;
        int used = getUseDuration(stack) - remaining;
        if (charges(stack) <= 0) {
            player.releaseUsingItem();
            Notifier.message(player, Component.translatable("message.skycraft.arsenal.staff_empty", stack.getHoverName()));
            return;
        }
        StaffEffects.stream(player, stack, kind, used);
        if (used > 0 && used % 20 == 0) spend(player, stack);
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    // ------------------------------------------------------------------ recharge

    private void recharge(ServerPlayer player, ItemStack staff, ItemStack gem) {
        CompoundTag gemTag = gem.getTag();
        int soul = gemTag == null ? 0 : gemTag.getInt("soul");
        if (soul <= 0) {
            Notifier.message(player, Component.translatable("message.skycraft.arsenal.gem_empty"));
            return;
        }
        int charges = charges(staff);
        if (charges >= kind.maxCharges) {
            Notifier.message(player, Component.translatable("message.skycraft.arsenal.staff_full", staff.getHoverName()));
            return;
        }
        int restore = Math.max(1, Math.round(kind.maxCharges * Math.min(5, soul) / 5f));
        setCharges(staff, charges + restore);

        ResourceLocation gemId = ForgeRegistries.ITEMS.getKey(gem.getItem());
        if (AZURAS_STAR.equals(gemId) || BLACK_STAR.equals(gemId)) {
            gemTag.remove("soul");
            gemTag.remove("skycraft_value");
            if (gemTag.isEmpty()) gem.setTag(null);
        } else {
            gem.shrink(1);
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1f, 0.7f);
        if (player.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 1.2, player.getZ(), 12, 0.3, 0.3, 0.3, 0.02);
        }
        Progression.addSkillXp(player, Skill.ENCHANTING, 0.04f * Math.min(5, soul));
        Notifier.message(player, Component.translatable("message.skycraft.arsenal.staff_recharged", staff.getHoverName()));
    }

    // ------------------------------------------------------------------ display

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return charges(stack) < kind.maxCharges;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13f * charges(stack) / kind.maxCharges);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x9B5DE5;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skycraft.staff." + kind.id).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.skycraft.arsenal.charges", charges(stack), kind.maxCharges).withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("tooltip.skycraft.arsenal.staff_skill", kind.skill.displayName()).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.skycraft.arsenal.recharge_hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
