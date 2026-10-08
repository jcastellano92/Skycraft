package com.skycraft.society;

import com.skycraft.core.Notifier;
import com.skycraft.core.PlayerData;
import com.skycraft.core.SkyData;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Per-player standing with Skyrim's factions (cross-module contract 23): {@code data.module("society")
 * .getCompound("reputation")} maps faction id -> int in -100..100 (absent = 0). Synced to the owning client with the
 * rest of the player data, so the reputation screen reads it directly.
 *
 * <p>Changes come from {@link ReputationEvents} (kills, assaults, crime counters, faction ranks and quest points).
 * At or below {@link SocietyConfig#HOSTILE_THRESHOLD} the faction's NPCs attack on sight and {@link HitSquads} may
 * come for the player.</p>
 */
public final class Reputation {
    public static final String MODULE = "society";
    public static final String KEY = "reputation";

    public static final String TOWNSFOLK = "townsfolk";
    public static final String GUARDS = "guards";
    public static final String THALMOR = "thalmor";
    public static final String IMPERIAL_LEGION = "imperial_legion";
    public static final String STORMCLOAKS = "stormcloaks";
    public static final String FORSWORN = "forsworn";
    public static final String BANDITS = "bandits";
    public static final String VAMPIRES = "vampires";
    public static final String DARK_BROTHERHOOD = "dark_brotherhood";
    public static final String THIEVES_GUILD = "thieves_guild";
    public static final String COMPANIONS = "companions";
    public static final String COLLEGE = "college";

    public static final String[] FACTIONS = {
            TOWNSFOLK, GUARDS, THALMOR, IMPERIAL_LEGION, STORMCLOAKS, FORSWORN, BANDITS, VAMPIRES,
            DARK_BROTHERHOOD, THIEVES_GUILD, COMPANIONS, COLLEGE
    };

    public static final int MIN = -100;
    public static final int MAX = 100;

    /** Standing tiers, lowest first: the lower bound of each tier. */
    public enum Tier {
        HATED(-100, 0xC03020), HOSTILE(-59, 0xD06030), DISLIKED(-39, 0xC8A050), NEUTRAL(-14, 0xB8B0A0),
        LIKED(15, 0x90C060), HONORED(50, 0x60C080), REVERED(80, 0x70D0E0);

        public final int min;
        public final int color;

        Tier(int min, int color) {
            this.min = min;
            this.color = color;
        }

        public static Tier of(int value) {
            Tier out = HATED;
            for (Tier t : values()) if (value >= t.min) out = t;
            return out;
        }

        public Component displayName() {
            return Component.translatable("society.skycraft.tier." + name().toLowerCase(java.util.Locale.ROOT));
        }
    }

    private Reputation() {}

    public static boolean isFaction(String id) {
        for (String f : FACTIONS) if (f.equals(id)) return true;
        return false;
    }

    public static Component factionName(String id) {
        return Component.translatable("society.skycraft.faction." + id);
    }

    /** The reputation compound (read-only use on the client is fine). */
    public static CompoundTag tag(PlayerData data) {
        CompoundTag mod = data.module(MODULE);
        if (!mod.contains(KEY, Tag.TAG_COMPOUND)) mod.put(KEY, new CompoundTag());
        return mod.getCompound(KEY);
    }

    public static int get(Player player, String faction) {
        CompoundTag mod = SkyData.get(player).module(MODULE);
        return mod.contains(KEY, Tag.TAG_COMPOUND) ? mod.getCompound(KEY).getInt(faction) : 0;
    }

    public static boolean hostile(Player player, String faction) {
        return get(player, faction) <= SocietyConfig.HOSTILE_THRESHOLD.get();
    }

    /** Sets a reputation silently (commands). */
    public static void set(Player player, String faction, int value) {
        PlayerData data = SkyData.get(player);
        int v = Math.max(MIN, Math.min(MAX, value));
        if (v == 0) tag(data).remove(faction);
        else tag(data).putInt(faction, v);
        data.markDirty();
    }

    /** Changes a reputation; tells the player when their standing tier changes. */
    public static void add(ServerPlayer player, String faction, int delta) {
        if (delta == 0 || !isFaction(faction) || player.isCreative() || player.isSpectator()) return;
        int before = get(player, faction);
        int after = Math.max(MIN, Math.min(MAX, before + delta));
        if (after == before) return;
        set(player, faction, after);
        Tier was = Tier.of(before);
        Tier now = Tier.of(after);
        if (was != now) {
            Component tier = now.displayName().copy().withStyle(s -> s.withColor(now.color));
            Notifier.message(player, Component.translatable("society.skycraft.rep.tier", factionName(faction), tier));
        }
        int threshold = SocietyConfig.HOSTILE_THRESHOLD.get();
        if (before > threshold && after <= threshold) {
            Notifier.message(player, Component.translatable("society.skycraft.rep.enemy", factionName(faction))
                    .withStyle(ChatFormatting.RED));
        }
    }

    /** Normalized 0..1 position of a value on the -100..100 bar. */
    public static float fraction(int value) {
        return (Math.max(MIN, Math.min(MAX, value)) - MIN) / (float) (MAX - MIN);
    }
}
