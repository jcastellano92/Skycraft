package com.skycraft.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * All persistent RPG state of a player. Lives in a capability on the player (both sides; the client copy is
 * filled by {@link com.skycraft.network.SyncPlayerDataPacket}).
 *
 * <p>Feature modules that need their own persistent per-player state store it in a named sub-tag obtained
 * through {@link #module(String)} and call {@link #markDirty()} after changing it so it gets synced.</p>
 */
public class PlayerData {
    public static final int BASE_HEALTH = 100;
    public static final int BASE_MAGICKA = 100;
    public static final int BASE_STAMINA = 100;
    public static final int ATTRIBUTE_STEP = 10;

    private final Map<Skill, Integer> skillLevels = new EnumMap<>(Skill.class);
    private final Map<Skill, Float> skillXp = new EnumMap<>(Skill.class);
    private final Map<Skill, Integer> legendary = new EnumMap<>(Skill.class);
    private final Map<String, Integer> perks = new HashMap<>();
    private final Map<String, Integer> stats = new HashMap<>();
    private final Map<String, CompoundTag> modules = new HashMap<>();

    private int level = 1;
    private float levelXp = 0;
    private int perkPoints = 0;
    private int pendingLevelUps = 0;
    private int healthPoints = 0;
    private int magickaPoints = 0;
    private int staminaPoints = 0;
    private float magicka = BASE_MAGICKA;
    private float stamina = BASE_STAMINA;
    private long gold = 0;
    private Race race = null;
    private long powerReadyAt = 0;
    private int trainingsThisLevel = 0;

    // transient (not saved)
    private transient long lastCombatTick = -1000;
    private transient long lastStaminaUseTick = -1000;
    private transient long lastMagickaUseTick = -1000;
    private transient boolean dirty = true;
    private transient boolean vitalsDirty = true;

    public PlayerData() {
        for (Skill s : Skill.VALUES) {
            skillLevels.put(s, Skill.BASE_LEVEL);
            skillXp.put(s, 0f);
            legendary.put(s, 0);
        }
    }

    // ------------------------------------------------------------------ skills

    public int getSkill(Skill skill) {
        return skillLevels.getOrDefault(skill, Skill.BASE_LEVEL);
    }

    public void setSkill(Skill skill, int level) {
        skillLevels.put(skill, Math.max(0, Math.min(Skill.MAX_LEVEL, level)));
        markDirty();
    }

    public float getSkillXp(Skill skill) {
        return skillXp.getOrDefault(skill, 0f);
    }

    public void setSkillXp(Skill skill, float xp) {
        skillXp.put(skill, Math.max(0, xp));
        markDirty();
    }

    public void addSkillXp(Skill skill, float xp) {
        awardSkill(skill, xp);
    }

    public void awardSkill(Skill skill, float xp) {
        int currentLvl = getSkill(skill);
        if (currentLvl >= Skill.MAX_LEVEL) return;
        float currentXp = getSkillXp(skill) + xp;
        float needed = skill.xpToNext(currentLvl);
        while (currentXp >= needed && currentLvl < Skill.MAX_LEVEL) {
            currentXp -= needed;
            currentLvl++;
            setSkill(skill, currentLvl);
            addLevelXp(currentLvl);
            needed = skill.xpToNext(currentLvl);
        }
        setSkillXp(skill, currentXp);
    }

    public void addLevelXp(float xp) {
        this.levelXp += xp;
        float needed = levelXpToNext(this.level);
        while (this.levelXp >= needed) {
            this.levelXp -= needed;
            this.level++;
            this.perkPoints++;
            this.pendingLevelUps++;
            needed = levelXpToNext(this.level);
        }
        markDirty();
    }

    /** Progress 0..1 towards the next skill level. */
    public float skillProgress(Skill skill) {
        int lvl = getSkill(skill);
        if (lvl >= Skill.MAX_LEVEL) return 1f;
        return Math.min(1f, getSkillXp(skill) / skill.xpToNext(lvl));
    }

    public int getLegendary(Skill skill) {
        return legendary.getOrDefault(skill, 0);
    }

    public void setLegendary(Skill skill, int times) {
        legendary.put(skill, times);
        markDirty();
    }

    // ------------------------------------------------------------------ character level

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = Math.max(1, level);
        markDirty();
    }

    public float getLevelXp() {
        return levelXp;
    }

    public void setLevelXp(float xp) {
        this.levelXp = Math.max(0, xp);
        markDirty();
    }

    /** Skyrim: XP to go from level L to L+1 is (L + 3) * 25. */
    public static float levelXpToNext(int level) {
        return (level + 3) * 25f;
    }

    public float levelProgress() {
        return Math.min(1f, levelXp / levelXpToNext(level));
    }

    public int getPerkPoints() {
        return perkPoints;
    }

    public void setPerkPoints(int points) {
        this.perkPoints = Math.max(0, points);
        markDirty();
    }

    public int getPendingLevelUps() {
        return pendingLevelUps;
    }

    public void setPendingLevelUps(int n) {
        this.pendingLevelUps = Math.max(0, n);
        markDirty();
    }

    public int getTrainingsThisLevel() {
        return trainingsThisLevel;
    }

    public void setTrainingsThisLevel(int n) {
        this.trainingsThisLevel = n;
        markDirty();
    }

    // ------------------------------------------------------------------ perks

    public int getPerkRank(String perkId) {
        return perks.getOrDefault(perkId, 0);
    }

    public boolean hasPerk(String perkId) {
        return getPerkRank(perkId) > 0;
    }

    public void setPerkRank(String perkId, int rank) {
        if (rank <= 0) perks.remove(perkId);
        else perks.put(perkId, rank);
        markDirty();
    }

    public Map<String, Integer> perks() {
        return perks;
    }

    // ------------------------------------------------------------------ attributes / vitals

    public int getHealthPoints() {
        return healthPoints;
    }

    public int getMagickaPoints() {
        return magickaPoints;
    }

    public int getStaminaPoints() {
        return staminaPoints;
    }

    public void addAttributePoint(int which) {
        switch (which) {
            case 0 -> healthPoints++;
            case 1 -> magickaPoints++;
            default -> staminaPoints++;
        }
        markDirty();
        markVitalsDirty();
    }

    /** Skyrim-scale max health (100 base). One Minecraft half-heart is 5 Skyrim health. */
    public int maxHealth() {
        return BASE_HEALTH + healthPoints * ATTRIBUTE_STEP + (int) module("bonus").getFloat("health");
    }

    public float maxMagicka() {
        int race = this.race == null ? 0 : this.race.bonusMagicka;
        return BASE_MAGICKA + magickaPoints * ATTRIBUTE_STEP + race + module("bonus").getFloat("magicka");
    }

    public float maxStamina() {
        return BASE_STAMINA + staminaPoints * ATTRIBUTE_STEP + module("bonus").getFloat("stamina");
    }

    public float getMagicka() {
        return magicka;
    }

    public void setMagicka(float value) {
        float v = Math.max(0, Math.min(maxMagicka(), value));
        if (v != magicka) {
            magicka = v;
            markVitalsDirty();
        }
    }

    public float getStamina() {
        return stamina;
    }

    public void setStamina(float value) {
        float v = Math.max(0, Math.min(maxStamina(), value));
        if (v != stamina) {
            stamina = v;
            markVitalsDirty();
        }
    }

    public long getLastCombatTick() {
        return lastCombatTick;
    }

    public void setLastCombatTick(long tick) {
        this.lastCombatTick = tick;
    }

    public long getLastStaminaUseTick() {
        return lastStaminaUseTick;
    }

    public void setLastStaminaUseTick(long tick) {
        this.lastStaminaUseTick = tick;
    }

    public long getLastMagickaUseTick() {
        return lastMagickaUseTick;
    }

    public void setLastMagickaUseTick(long tick) {
        this.lastMagickaUseTick = tick;
    }

    // ------------------------------------------------------------------ gold / race / power

    public long getGold() {
        return gold;
    }

    public void setGold(long gold) {
        this.gold = Math.max(0, gold);
        markDirty();
    }

    public Race getRace() {
        return race;
    }

    public void setRace(Race race) {
        this.race = race;
        markDirty();
        markVitalsDirty();
    }

    public long getPowerReadyAt() {
        return powerReadyAt;
    }

    public void setPowerReadyAt(long gameTime) {
        this.powerReadyAt = gameTime;
        markDirty();
    }

    // ------------------------------------------------------------------ stats

    public int getStat(String key) {
        return stats.getOrDefault(key, 0);
    }

    public void addStat(String key, int amount) {
        stats.merge(key, amount, Integer::sum);
        markDirty();
    }

    public Map<String, Integer> stats() {
        return stats;
    }

    // ------------------------------------------------------------------ module storage

    /** Mutable per-module storage. Remember to call {@link #markDirty()} after modifying it. */
    public CompoundTag module(String name) {
        return modules.computeIfAbsent(name, k -> new CompoundTag());
    }

    // ------------------------------------------------------------------ dirty tracking

    public void markDirty() {
        dirty = true;
    }

    public boolean consumeDirty() {
        boolean d = dirty;
        dirty = false;
        return d;
    }

    public void markVitalsDirty() {
        vitalsDirty = true;
    }

    public boolean consumeVitalsDirty() {
        boolean d = vitalsDirty;
        vitalsDirty = false;
        return d;
    }

    // ------------------------------------------------------------------ serialization

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        CompoundTag skills = new CompoundTag();
        for (Skill s : Skill.VALUES) {
            CompoundTag st = new CompoundTag();
            st.putInt("lvl", getSkill(s));
            st.putFloat("xp", getSkillXp(s));
            st.putInt("legendary", getLegendary(s));
            skills.put(s.id(), st);
        }
        tag.put("skills", skills);
        CompoundTag perkTag = new CompoundTag();
        perks.forEach(perkTag::putInt);
        tag.put("perks", perkTag);
        CompoundTag statTag = new CompoundTag();
        stats.forEach(statTag::putInt);
        tag.put("stats", statTag);
        CompoundTag moduleTag = new CompoundTag();
        modules.forEach((k, v) -> moduleTag.put(k, v.copy()));
        tag.put("modules", moduleTag);

        tag.putInt("level", level);
        tag.putFloat("levelXp", levelXp);
        tag.putInt("perkPoints", perkPoints);
        tag.putInt("pendingLevelUps", pendingLevelUps);
        tag.putInt("hp", healthPoints);
        tag.putInt("mp", magickaPoints);
        tag.putInt("sp", staminaPoints);
        tag.putFloat("magicka", magicka);
        tag.putFloat("stamina", stamina);
        tag.putLong("gold", gold);
        if (race != null) tag.putString("race", race.id());
        tag.putLong("powerReadyAt", powerReadyAt);
        tag.putInt("trainings", trainingsThisLevel);
        return tag;
    }

    public void load(CompoundTag tag) {
        CompoundTag skills = tag.getCompound("skills");
        for (Skill s : Skill.VALUES) {
            if (skills.contains(s.id(), Tag.TAG_COMPOUND)) {
                CompoundTag st = skills.getCompound(s.id());
                skillLevels.put(s, st.getInt("lvl"));
                skillXp.put(s, st.getFloat("xp"));
                legendary.put(s, st.getInt("legendary"));
            }
        }
        perks.clear();
        CompoundTag perkTag = tag.getCompound("perks");
        for (String k : perkTag.getAllKeys()) perks.put(k, perkTag.getInt(k));
        stats.clear();
        CompoundTag statTag = tag.getCompound("stats");
        for (String k : statTag.getAllKeys()) stats.put(k, statTag.getInt(k));
        modules.clear();
        CompoundTag moduleTag = tag.getCompound("modules");
        for (String k : moduleTag.getAllKeys()) modules.put(k, moduleTag.getCompound(k).copy());

        level = Math.max(1, tag.getInt("level"));
        levelXp = tag.getFloat("levelXp");
        perkPoints = tag.getInt("perkPoints");
        pendingLevelUps = tag.getInt("pendingLevelUps");
        healthPoints = tag.getInt("hp");
        magickaPoints = tag.getInt("mp");
        staminaPoints = tag.getInt("sp");
        magicka = tag.getFloat("magicka");
        stamina = tag.getFloat("stamina");
        gold = tag.getLong("gold");
        race = tag.contains("race") ? Race.byId(tag.getString("race")) : null;
        powerReadyAt = tag.getLong("powerReadyAt");
        trainingsThisLevel = tag.getInt("trainings");
        markDirty();
        markVitalsDirty();
    }

    public void copyFrom(PlayerData other) {
        load(other.save());
    }
}
