package com.skycraft.magic.wordwall;

import com.skycraft.core.Notifier;
import com.skycraft.magic.MagicData;
import com.skycraft.magic.MagicFx;
import com.skycraft.magic.shout.Shout;
import com.skycraft.magic.spell.Element;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Learning Words of Power from Word Walls. */
public final class WordWalls {
    private WordWalls() {}

    /** Picks a shout for a new wall, weighted by {@link Shout#wallWeight}. */
    public static Shout randomShout(RandomSource random) {
        int total = 0;
        for (Shout s : Shout.VALUES) total += s.wallWeight;
        int roll = random.nextInt(total);
        for (Shout s : Shout.VALUES) {
            roll -= s.wallWeight;
            if (roll < 0) return s;
        }
        return Shout.UNRELENTING_FORCE;
    }

    public static String wallKey(ServerLevel level, BlockPos pos) {
        return level.dimension().location() + "@" + pos.asLong();
    }

    /**
     * Teaches the next word of the wall's shout. If the player already knows all three, the wall teaches the next
     * word of another shout they haven't mastered (so no wall is ever wasted).
     *
     * @param explicit true when the player clicked the wall (shows "already read" feedback)
     */
    public static void teach(ServerPlayer p, ServerLevel level, BlockPos pos, Shout shout, boolean explicit) {
        String key = wallKey(level, pos);
        if (MagicData.hasReadWall(p, key)) {
            if (explicit) Notifier.message(p, Component.translatable("message.skycraft.wall_already_read"));
            return;
        }
        Shout target = MagicData.wordsLearned(p, shout) < 3 ? shout : fallback(p, pos);
        MagicData.markWallRead(p, key);
        if (target == null) {
            Notifier.message(p, Component.translatable("message.skycraft.all_words_known"));
            return;
        }
        int index = MagicData.wordsLearned(p, target);
        MagicData.setWordsLearned(p, target, index + 1);

        Vec3 from = Vec3.atCenterOf(pos);
        MagicFx.sendNear(level, from, 64, MagicFx.STREAM, Element.ARCANE, from, from, p.getId(), 70);
        level.playSound(null, pos, SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.BLOCKS, 0.5f, 0.6f);
        level.playSound(null, pos, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 1.5f, 0.6f);
        level.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1.5f, 0.5f);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 2f, 0.6f);
        Notifier.title(p, Component.translatable("notify.skycraft.word_learned"), target.wordWithTranslation(index));
        if (MagicData.dragonSouls(p) > 0) {
            Notifier.message(p, Component.translatable("message.skycraft.word_learned_hint_soul", target.displayName(), Component.keybind("key.skycraft.magic_menu")));
        } else {
            Notifier.message(p, Component.translatable("message.skycraft.word_learned_hint", target.displayName(), Component.keybind("key.skycraft.magic_menu")));
        }
    }

    @Nullable
    private static Shout fallback(ServerPlayer p, BlockPos pos) {
        int n = Shout.VALUES.length;
        int start = Math.floorMod(Long.hashCode(pos.asLong()), n);
        for (int i = 0; i < n; i++) {
            Shout s = Shout.VALUES[(start + i) % n];
            if (MagicData.wordsLearned(p, s) < 3) return s;
        }
        return null;
    }
}
