package com.skycraft.core;

import net.minecraft.network.chat.Component;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/** The ten playable races with their Skyrim starting skill bonuses, passives and once-per-day greater power. */
public enum Race {
    NORD(Skill.TWO_HANDED, new Skill[]{Skill.ONE_HANDED, Skill.BLOCK, Skill.LIGHT_ARMOR, Skill.SMITHING, Skill.SPEECH}, 0, "battle_cry"),
    IMPERIAL(Skill.RESTORATION, new Skill[]{Skill.BLOCK, Skill.DESTRUCTION, Skill.ENCHANTING, Skill.HEAVY_ARMOR, Skill.ONE_HANDED}, 0, "voice_of_the_emperor"),
    BRETON(Skill.CONJURATION, new Skill[]{Skill.ALCHEMY, Skill.ALTERATION, Skill.ILLUSION, Skill.RESTORATION, Skill.SPEECH}, 50, "dragonskin"),
    REDGUARD(Skill.ONE_HANDED, new Skill[]{Skill.ARCHERY, Skill.ALTERATION, Skill.BLOCK, Skill.DESTRUCTION, Skill.SMITHING}, 0, "adrenaline_rush"),
    ALTMER(Skill.ILLUSION, new Skill[]{Skill.ALTERATION, Skill.CONJURATION, Skill.DESTRUCTION, Skill.ENCHANTING, Skill.RESTORATION}, 50, "highborn"),
    BOSMER(Skill.ARCHERY, new Skill[]{Skill.ALCHEMY, Skill.LIGHT_ARMOR, Skill.LOCKPICKING, Skill.PICKPOCKET, Skill.SNEAK}, 0, "command_animal"),
    DUNMER(Skill.DESTRUCTION, new Skill[]{Skill.ALCHEMY, Skill.ALTERATION, Skill.ILLUSION, Skill.LIGHT_ARMOR, Skill.SNEAK}, 0, "ancestors_wrath"),
    ORSIMER(Skill.HEAVY_ARMOR, new Skill[]{Skill.BLOCK, Skill.ENCHANTING, Skill.ONE_HANDED, Skill.SMITHING, Skill.TWO_HANDED}, 0, "berserker_rage"),
    KHAJIIT(Skill.SNEAK, new Skill[]{Skill.ALCHEMY, Skill.ARCHERY, Skill.LOCKPICKING, Skill.ONE_HANDED, Skill.PICKPOCKET}, 0, "night_eye"),
    ARGONIAN(Skill.LOCKPICKING, new Skill[]{Skill.ALCHEMY, Skill.LIGHT_ARMOR, Skill.PICKPOCKET, Skill.RESTORATION, Skill.SNEAK}, 0, "histskin");

    public static final Race[] VALUES = values();

    public final Skill major;
    public final Skill[] minors;
    public final int bonusMagicka;
    public final String power;

    Race(Skill major, Skill[] minors, int bonusMagicka, String power) {
        this.major = major;
        this.minors = minors;
        this.bonusMagicka = bonusMagicka;
        this.power = power;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Component displayName() {
        return Component.translatable("race.skycraft." + id());
    }

    public Component description() {
        return Component.translatable("race.skycraft." + id() + ".desc");
    }

    public Component powerName() {
        return Component.translatable("power.skycraft." + power);
    }

    public Map<Skill, Integer> startingSkills() {
        Map<Skill, Integer> map = new EnumMap<>(Skill.class);
        for (Skill s : Skill.VALUES) map.put(s, Skill.BASE_LEVEL);
        map.put(major, Skill.BASE_LEVEL + 10);
        for (Skill s : minors) map.put(s, Skill.BASE_LEVEL + 5);
        return map;
    }

    /** Fraction of incoming damage of the given elemental kind that this race resists. */
    public float resistance(String element) {
        return switch (this) {
            case NORD -> element.equals("frost") ? 0.5f : 0f;
            case DUNMER -> element.equals("fire") ? 0.5f : 0f;
            case REDGUARD, BOSMER -> element.equals("poison") ? 0.5f : 0f;
            case BRETON -> element.equals("magic") ? 0.25f : 0f;
            case ALTMER -> 0f;
            case ARGONIAN -> element.equals("poison") ? 0.5f : 0f;
            default -> 0f;
        };
    }

    public static Race byId(String id) {
        for (Race r : VALUES) {
            if (r.id().equals(id)) return r;
        }
        return null;
    }
}
