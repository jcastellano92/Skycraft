package com.skycraft.survival;

import net.minecraftforge.common.ForgeConfigSpec;

/** Survival module settings: {@code config/skycraft-survival.toml}. */
public final class SurvivalConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue ORE_REGEN;
    public static final ForgeConfigSpec.IntValue ORE_REGEN_TICKS;
    public static final ForgeConfigSpec.BooleanValue ORE_REGEN_NETHER;

    public static final ForgeConfigSpec.BooleanValue DISEASES;
    public static final ForgeConfigSpec.DoubleValue DISEASE_CHANCE;
    public static final ForgeConfigSpec.IntValue CURE_PRICE;
    public static final ForgeConfigSpec.IntValue BLESSING_TICKS;

    public static final ForgeConfigSpec.BooleanValue INNKEEPERS;
    public static final ForgeConfigSpec.IntValue ROOM_PRICE;
    public static final ForgeConfigSpec.IntValue INNKEEPER_RESPAWN_TICKS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("ore_veins");
        ORE_REGEN = b.comment("Mined natural ore veins become depleted and grow back after a while (Skyrim ore veins).")
                .define("regenerateOre", true);
        ORE_REGEN_TICKS = b.comment("Ticks until a depleted vein grows back (72000 = three in-game days; sleeping and waiting count).")
                .defineInRange("regenTicks", 72000, 200, 100_000_000);
        ORE_REGEN_NETHER = b.comment("Also deplete ores mined in the Nether (the Overworld always does when enabled).")
                .define("nether", true);
        b.pop();

        b.push("diseases");
        DISEASES = b.comment("Creatures (skeevers, wolves, bears, trolls, draugr, vampires...) can give you diseases.")
                .define("enabled", true);
        DISEASE_CHANCE = b.comment("Base chance per hit from a disease carrier (Argonians and Bosmer resist half).")
                .defineInRange("chance", 0.07, 0.0, 1.0);
        CURE_PRICE = b.comment("Gold a priest asks to cure all diseases.").defineInRange("curePrice", 100, 0, 100000);
        BLESSING_TICKS = b.comment("Duration of a shrine blessing (8000 = eight in-game hours).")
                .defineInRange("blessingTicks", 8000, 20, 10_000_000);
        b.pop();

        b.push("inns");
        INNKEEPERS = b.comment("Spawn an innkeeper near the center of every settlement the first time a player visits it.")
                .define("spawnInnkeepers", true);
        ROOM_PRICE = b.comment("Gold for renting a room for one day.").defineInRange("roomPrice", 10, 0, 100000);
        INNKEEPER_RESPAWN_TICKS = b.comment("A settlement whose innkeeper has not been seen for this long gets a new one.")
                .defineInRange("innkeeperRespawnTicks", 72000, 1200, 100_000_000);
        b.pop();
        SPEC = b.build();
    }

    private SurvivalConfig() {}
}
