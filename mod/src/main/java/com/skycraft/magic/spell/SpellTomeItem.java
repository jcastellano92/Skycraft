package com.skycraft.magic.spell;

import com.skycraft.core.Notifier;
import com.skycraft.magic.MagicData;
import com.skycraft.magic.MagicFx;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** A spell tome: read it (use) to learn its spell. The tome is consumed. Spells cannot be crafted. */
public class SpellTomeItem extends Item {
    private final String spellId;

    public SpellTomeItem(String spellId, Properties props) {
        super(props);
        this.spellId = spellId;
    }

    @Nullable
    public Spell spell() {
        return Spells.byId(spellId);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        Spell spell = spell();
        if (spell == null) return InteractionResultHolder.pass(stack);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        ServerPlayer sp = (ServerPlayer) player;
        if (MagicData.knows(sp, spell.id)) {
            Notifier.message(sp, Component.translatable("message.skycraft.spell_known", spell.displayName()));
            return InteractionResultHolder.fail(stack);
        }
        MagicData.learn(sp, spell.id);
        if (!sp.isCreative()) stack.shrink(1);
        Notifier.title(sp, Component.translatable("notify.skycraft.spell_learned"), spell.displayName());
        level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 0.9f);
        level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1f, 1.1f);
        level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.4f, 1.6f);
        MagicFx.send(sp, MagicFx.AURA, spell.element, sp.position(), sp.position(), sp.getId(), 4);
        sp.awardStat(net.minecraft.stats.Stats.ITEM_USED.get(this));
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        Spell spell = spell();
        if (spell == null) return;
        tooltip.add(spell.displayName().copy().withStyle(ChatFormatting.WHITE));
        tooltip.add(Component.translatable("tooltip.skycraft.spell_school", spell.school.displayName(), spell.tier.displayName())
                .withStyle(s -> s.withColor(spell.school.color)));
        String unit = spell.isConcentration() ? "tooltip.skycraft.spell_cost_per_second" : "tooltip.skycraft.spell_cost";
        tooltip.add(Component.translatable(unit, Math.round(spell.cost)).withStyle(ChatFormatting.AQUA));
        if (spell.chargeTicks > 0) tooltip.add(Component.translatable("tooltip.skycraft.spell_charged").withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(spell.description().copy().withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.skycraft.spell_tome_hint").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        Spell spell = spell();
        return spell != null && spell.tier.ordinal() >= Tier.EXPERT.ordinal();
    }
}
