package com.skycraft.magic;

import com.skycraft.core.PlayerData;
import com.skycraft.core.SkyData;
import com.skycraft.magic.shout.Shout;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Accessors for the {@code "magic"} module tag of {@link PlayerData}. The tag is synced to the owning client, so the
 * client reads the same keys for the HUD and the magic menu.
 *
 * <p>Cross-module contract keys: {@code spells} (list of spell ids), {@code words} (shout id -> words learned),
 * {@code dragon_souls}. Our own keys: {@code right_spell} (alias {@code selected_spell}), {@code left_spell}, {@code selected_shout}, {@code unlocked} (shout id ->
 * words unlocked with dragon souls), {@code walls} (word walls already read), {@code favorites},
 * {@code shout_ready}/{@code shout_total} (voice cooldown), {@code dragonborn}, {@code avoid_death}.</p>
 */
public final class MagicData {
    public static final String MODULE = "magic";

    private MagicData() {}

    public static CompoundTag tag(Player player) {
        return SkyData.get(player).module(MODULE);
    }

    private static void dirty(Player player) {
        SkyData.get(player).markDirty();
    }

    // ------------------------------------------------------------------ spells

    public static List<String> knownSpells(Player player) {
        ListTag list = tag(player).getList("spells", Tag.TAG_STRING);
        List<String> out = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) out.add(list.getString(i));
        return out;
    }

    public static boolean knows(Player player, String spellId) {
        ListTag list = tag(player).getList("spells", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            if (list.getString(i).equals(spellId)) return true;
        }
        return false;
    }

    /** Adds a spell; returns false if it was already known. */
    public static boolean learn(Player player, String spellId) {
        if (knows(player, spellId)) return false;
        CompoundTag tag = tag(player);
        ListTag list = tag.getList("spells", Tag.TAG_STRING);
        list.add(StringTag.valueOf(spellId));
        tag.put("spells", list);
        // New spells fill an empty hand: right first, then left.
        if (selectedSpell(player).isEmpty()) {
            tag.putString("right_spell", spellId);
            tag.putString("selected_spell", spellId);
        } else if (tag.getString("left_spell").isEmpty()) {
            tag.putString("left_spell", spellId);
        }
        dirty(player);
        return true;
    }

    /** The right-hand spell ({@code right_spell}; {@code selected_spell} is kept as an alias for older data). */
    public static String selectedSpell(Player player) {
        CompoundTag tag = tag(player);
        String right = tag.getString("right_spell");
        return right.isEmpty() ? tag.getString("selected_spell") : right;
    }

    /** Equips the right-hand spell. */
    public static void setSelectedSpell(Player player, String spellId) {
        CompoundTag tag = tag(player);
        tag.putString("right_spell", spellId);
        tag.putString("selected_spell", spellId);
        dirty(player);
    }

    /** The left-hand spell, or "" (casting with an empty left hand then uses the right-hand spell). */
    public static String leftSpell(Player player) {
        return tag(player).getString("left_spell");
    }

    public static void setLeftSpell(Player player, String spellId) {
        tag(player).putString("left_spell", spellId);
        dirty(player);
    }

    public static List<String> favorites(Player player) {
        ListTag list = tag(player).getList("favorites", Tag.TAG_STRING);
        List<String> out = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) out.add(list.getString(i));
        return out;
    }

    public static void toggleFavorite(Player player, String id) {
        CompoundTag tag = tag(player);
        ListTag list = tag.getList("favorites", Tag.TAG_STRING);
        boolean removed = false;
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.getString(i).equals(id)) {
                list.remove(i);
                removed = true;
            }
        }
        if (!removed) list.add(StringTag.valueOf(id));
        tag.put("favorites", list);
        dirty(player);
    }

    // ------------------------------------------------------------------ shouts

    public static int wordsLearned(Player player, Shout shout) {
        return Math.min(3, tag(player).getCompound("words").getInt(shout.id()));
    }

    public static void setWordsLearned(Player player, Shout shout, int words) {
        CompoundTag tag = tag(player);
        CompoundTag w = tag.getCompound("words");
        w.putInt(shout.id(), Math.max(0, Math.min(3, words)));
        tag.put("words", w);
        if (tag.getString("selected_shout").isEmpty()) tag.putString("selected_shout", shout.id());
        dirty(player);
    }

    public static int wordsUnlocked(Player player, Shout shout) {
        return Math.min(3, tag(player).getCompound("unlocked").getInt(shout.id()));
    }

    public static void setWordsUnlocked(Player player, Shout shout, int words) {
        CompoundTag tag = tag(player);
        CompoundTag w = tag.getCompound("unlocked");
        w.putInt(shout.id(), Math.max(0, Math.min(3, words)));
        tag.put("unlocked", w);
        dirty(player);
    }

    /** Words that can actually be shouted: learned on a word wall and unlocked with a dragon soul. */
    public static int usableWords(Player player, Shout shout) {
        return Math.min(wordsLearned(player, shout), wordsUnlocked(player, shout));
    }

    public static int dragonSouls(Player player) {
        return tag(player).getInt("dragon_souls");
    }

    public static void setDragonSouls(Player player, int souls) {
        tag(player).putInt("dragon_souls", Math.max(0, souls));
        dirty(player);
    }

    public static String selectedShout(Player player) {
        return tag(player).getString("selected_shout");
    }

    public static void setSelectedShout(Player player, String shoutId) {
        tag(player).putString("selected_shout", shoutId);
        dirty(player);
    }

    /** Game time at which the voice is ready again. */
    public static long shoutReadyAt(Player player) {
        return tag(player).getLong("shout_ready");
    }

    /** Length of the current voice cooldown in ticks (for the HUD meter). */
    public static long shoutCooldownTotal(Player player) {
        return tag(player).getLong("shout_total");
    }

    public static void setShoutCooldown(Player player, long readyAt, long total) {
        CompoundTag tag = tag(player);
        tag.putLong("shout_ready", readyAt);
        tag.putLong("shout_total", total);
        dirty(player);
    }

    // ------------------------------------------------------------------ word walls

    public static boolean hasReadWall(Player player, String wallKey) {
        ListTag list = tag(player).getList("walls", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            if (list.getString(i).equals(wallKey)) return true;
        }
        return false;
    }

    public static void markWallRead(Player player, String wallKey) {
        CompoundTag tag = tag(player);
        ListTag list = tag.getList("walls", Tag.TAG_STRING);
        list.add(StringTag.valueOf(wallKey));
        tag.put("walls", list);
        dirty(player);
    }
}
