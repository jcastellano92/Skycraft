package com.skycraft.economy;

import com.skycraft.core.Notifier;
import com.skycraft.core.PlayerData;
import com.skycraft.core.SkyData;
import com.skycraft.core.Skill;
import com.skycraft.economy.client.EconomyClient;
import com.skycraft.skills.Progression;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A Skyrim skill book. Reading it for the first time raises its skill by one level; the book is never consumed,
 * and reading it again does nothing. Read books are tracked per character in {@code module("economy").books_read}.
 */
public class SkillBookItem extends Item {
    public static final String MODULE = "economy";
    public static final String READ_KEY = "books_read";

    private final Skill skill;

    public SkillBookItem(Skill skill, Properties props) {
        super(props);
        this.skill = skill;
    }

    public Skill skill() {
        return skill;
    }

    public static boolean hasRead(PlayerData data, Skill skill) {
        ListTag read = data.module(MODULE).getList(READ_KEY, Tag.TAG_STRING);
        for (int i = 0; i < read.size(); i++) {
            if (read.getString(i).equals(skill.id())) return true;
        }
        return false;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            PlayerData data = SkyData.get(sp);
            if (hasRead(data, skill)) {
                Notifier.message(sp, Component.translatable("message.skycraft.economy.book_already_read"));
            } else {
                CompoundTag module = data.module(MODULE);
                ListTag read = module.getList(READ_KEY, Tag.TAG_STRING);
                read.add(StringTag.valueOf(skill.id()));
                module.put(READ_KEY, read);
                data.addStat("skill_books_read", 1);
                data.markDirty();
                if (data.getSkill(skill) < Skill.MAX_LEVEL) {
                    Progression.increaseSkill(sp, skill, 1);
                } else {
                    Notifier.message(sp, Component.translatable("message.skycraft.economy.book_mastered", skill.displayName()));
                }
            }
            level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 1f);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.skycraft.economy.skill_book", skill.displayName()).withStyle(ChatFormatting.GRAY));
        if (level != null && level.isClientSide) {
            Boolean read = DistExecutor.unsafeCallWhenOn(Dist.CLIENT, () -> () -> EconomyClient.hasReadBook(skill));
            if (Boolean.TRUE.equals(read)) {
                tooltip.add(Component.translatable("tooltip.skycraft.economy.skill_book_read").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
            }
        }
    }
}
