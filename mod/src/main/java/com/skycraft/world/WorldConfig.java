package com.skycraft.world;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * World module settings: {@code config/skycraft-world-common.toml} (server rules) and
 * {@code config/skycraft-world-client.toml} (music & atmosphere, per player).
 */
public final class WorldConfig {
    public static final ForgeConfigSpec COMMON_SPEC;
    public static final ForgeConfigSpec CLIENT_SPEC;

    // common
    public static final ForgeConfigSpec.BooleanValue DISCOVERY;
    public static final ForgeConfigSpec.BooleanValue FAST_TRAVEL;
    public static final ForgeConfigSpec.IntValue BLOCKS_PER_TRAVEL_HOUR;
    public static final ForgeConfigSpec.BooleanValue BED_MENU;
    public static final ForgeConfigSpec.IntValue REST_VOTE_SECONDS;
    public static final ForgeConfigSpec.IntValue WELL_RESTED_TICKS;

    // client
    public static final ForgeConfigSpec.BooleanValue MUSIC;
    public static final ForgeConfigSpec.BooleanValue REALM_FOG;
    public static final ForgeConfigSpec.IntValue COMPASS_RANGE;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("world");
        DISCOVERY = b.comment("Announce structures as Skyrim locations (\"DISCOVERED: Bleakwind Barrow\") and track them per player.")
                .define("discovery", true);
        FAST_TRAVEL = b.comment("Allow fast travel to discovered locations from the world map.").define("fastTravel", true);
        BLOCKS_PER_TRAVEL_HOUR = b.comment("Fast travel advances the clock by one hour per this many blocks travelled",
                        "(only while a single player is online; 0 = never advance time).")
                .defineInRange("blocksPerTravelHour", 400, 0, 1000000);
        BED_MENU = b.comment("Right-clicking a bed opens the Skyrim sleep menu at any time of day (sneak + empty hand for vanilla sleeping).")
                .define("bedMenu", true);
        REST_VOTE_SECONDS = b.comment("Multiplayer: how long a player's request to wait/sleep stays valid while others agree.")
                .defineInRange("restVoteSeconds", 60, 5, 3600);
        WELL_RESTED_TICKS = b.comment("Duration of Well Rested after sleeping at least one hour (ticks).")
                .defineInRange("wellRestedTicks", 28800, 0, 10000000);
        b.pop();
        COMMON_SPEC = b.build();

        ForgeConfigSpec.Builder c = new ForgeConfigSpec.Builder();
        c.push("atmosphere");
        MUSIC = c.comment("Play the Skycraft situational soundtrack (exploration, towns, dungeons, combat...) instead of vanilla music.")
                .define("music", true);
        REALM_FOG = c.comment("Tint the fog of Oblivion (Nether) and Sovngarde (End).").define("realmFog", true);
        COMPASS_RANGE = c.comment("Discovered locations closer than this are shown on the compass.")
                .defineInRange("compassRange", 350, 16, 10000);
        c.pop();
        CLIENT_SPEC = c.build();
    }

    private WorldConfig() {}
}
