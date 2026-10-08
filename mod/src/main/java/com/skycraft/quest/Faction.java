package com.skycraft.quest;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** The guilds and armies of Skyrim. Ids are part of the cross-module contract ({@code module("quest").factions}). */
public enum Faction {
    COMPANIONS("companions", 4, 0xC8A050),
    COLLEGE("college", 5, 0x5A7FD0),
    THIEVES_GUILD("thieves_guild", 6, 0x707070),
    DARK_BROTHERHOOD("dark_brotherhood", 6, 0xA02020),
    IMPERIAL_LEGION("imperial_legion", 5, 0xB04040),
    STORMCLOAKS("stormcloaks", 5, 0x3D6DB5),
    BARDS_COLLEGE("bards_college", 3, 0x9A60B0);

    public static final Faction[] VALUES = values();

    public final String id;
    public final int ranks;
    public final int color;

    Faction(String id, int ranks, int color) {
        this.id = id;
        this.ranks = ranks;
        this.color = color;
    }

    @Nullable
    public static Faction byId(String id) {
        for (Faction f : VALUES) if (f.id.equals(id)) return f;
        return null;
    }

    public int maxRank() {
        return ranks - 1;
    }

    /** Total reputation points needed for a rank: 0, 2, 6, 12, 20, 30. */
    public static int pointsFor(int rank) {
        return rank <= 0 ? 0 : rank * (rank + 1);
    }

    /** The faction the civil war makes you an enemy of, if any. */
    @Nullable
    public Faction rival() {
        return switch (this) {
            case IMPERIAL_LEGION -> STORMCLOAKS;
            case STORMCLOAKS -> IMPERIAL_LEGION;
            default -> null;
        };
    }

    public Component displayName() {
        return Component.translatable("faction.skycraft." + id);
    }

    public Component rankName(int rank) {
        return Component.translatable("faction.skycraft." + id + ".rank." + Math.max(0, Math.min(maxRank(), rank)));
    }

    public Component description() {
        return Component.translatable("faction.skycraft." + id + ".desc");
    }

    public Component howToJoin() {
        return Component.translatable("faction.skycraft." + id + ".join");
    }
}
