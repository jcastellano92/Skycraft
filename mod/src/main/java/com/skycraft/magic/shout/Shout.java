package com.skycraft.magic.shout;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Dragon Shouts (Thu'um): three Words of Power each, in dragon language with their translations, and the voice
 * cooldown (seconds) for shouting one, two or all three words. {@code wallWeight} is how often a Word Wall teaches it.
 */
public enum Shout {
    UNRELENTING_FORCE(new String[]{"FUS", "RO", "DAH"}, new int[]{20, 25, 45}, 30),
    FIRE_BREATH(new String[]{"YOL", "TOOR", "SHUL"}, new int[]{30, 50, 100}, 14),
    FROST_BREATH(new String[]{"FO", "KRAH", "DIIN"}, new int[]{30, 50, 100}, 14),
    WHIRLWIND_SPRINT(new String[]{"WULD", "NAH", "KEST"}, new int[]{20, 25, 35}, 14),
    BECOME_ETHEREAL(new String[]{"FEIM", "ZII", "GRON"}, new int[]{20, 30, 40}, 10),
    CLEAR_SKIES(new String[]{"LOK", "VAH", "KOOR"}, new int[]{5, 10, 15}, 6),
    AURA_WHISPER(new String[]{"LAAS", "YAH", "NIR"}, new int[]{30, 40, 50}, 8),
    SLOW_TIME(new String[]{"TIID", "KLO", "UL"}, new int[]{30, 45, 60}, 8),
    MARKED_FOR_DEATH(new String[]{"KRII", "LUN", "AUS"}, new int[]{20, 30, 40}, 6),
    DISARM(new String[]{"ZUN", "HAAL", "VIIK"}, new int[]{30, 35, 40}, 8),
    ELEMENTAL_FURY(new String[]{"SU", "GRAH", "DUN"}, new int[]{30, 40, 50}, 8),
    KYNES_PEACE(new String[]{"KAAN", "DREM", "OV"}, new int[]{40, 50, 60}, 6),
    ANIMAL_ALLEGIANCE(new String[]{"RAAN", "MIR", "TAH"}, new int[]{50, 60, 70}, 6),
    STORM_CALL(new String[]{"STRUN", "BAH", "QO"}, new int[]{300, 480, 600}, 4),
    DRAGONREND(new String[]{"JOOR", "ZAH", "FRUL"}, new int[]{10, 12, 15}, 3);

    public static final Shout[] VALUES = values();

    private final String[] words;
    private final int[] cooldowns;
    public final int wallWeight;

    Shout(String[] words, int[] cooldowns, int wallWeight) {
        this.words = words;
        this.cooldowns = cooldowns;
        this.wallWeight = wallWeight;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Dragon word {@code i} (0..2), e.g. "FUS". */
    public String word(int i) {
        return words[i];
    }

    /** Translation of word {@code i}, e.g. "Force". */
    public Component translation(int i) {
        return Component.translatable("word.skycraft." + words[i].toLowerCase(Locale.ROOT));
    }

    /** "FUS - Force". */
    public MutableComponent wordWithTranslation(int i) {
        return Component.literal(words[i] + " - ").append(translation(i));
    }

    /** "FUS RO DAH" for the given number of words. */
    public String phrase(int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(3, count); i++) {
            if (i > 0) sb.append(' ');
            sb.append(words[i]);
        }
        return sb.toString();
    }

    public int cooldownSeconds(int wordCount) {
        return cooldowns[Math.max(1, Math.min(3, wordCount)) - 1];
    }

    public Component displayName() {
        return Component.translatable("shout.skycraft." + id());
    }

    public Component description() {
        return Component.translatable("shout.skycraft." + id() + ".desc");
    }

    @Nullable
    public static Shout byId(String id) {
        for (Shout s : VALUES) {
            if (s.id().equals(id)) return s;
        }
        return null;
    }

    public static Shout byOrdinal(int i) {
        return i >= 0 && i < VALUES.length ? VALUES[i] : UNRELENTING_FORCE;
    }
}
