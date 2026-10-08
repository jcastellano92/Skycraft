package com.skycraft.atmosphere;

import net.minecraftforge.common.ForgeConfigSpec;

/** Client settings of the atmosphere module: {@code config/skycraft-atmosphere-client.toml}. */
public final class AtmosphereConfig {
    public static final ForgeConfigSpec CLIENT_SPEC;

    public static final ForgeConfigSpec.BooleanValue SKY;
    public static final ForgeConfigSpec.BooleanValue SECUNDA;
    public static final ForgeConfigSpec.BooleanValue MASSER_HALO;
    public static final ForgeConfigSpec.BooleanValue AURORA;
    public static final ForgeConfigSpec.BooleanValue AURORA_EVERYWHERE;
    public static final ForgeConfigSpec.DoubleValue AURORA_MAX_TEMPERATURE;
    public static final ForgeConfigSpec.DoubleValue AURORA_BRIGHTNESS;
    public static final ForgeConfigSpec.BooleanValue STARS;
    public static final ForgeConfigSpec.BooleanValue GALAXY;
    public static final ForgeConfigSpec.BooleanValue SKIP_WITH_SHADERS;
    public static final ForgeConfigSpec.BooleanValue LEVEL_UP_STING;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Skyrim sky: Masser & Secunda, aurora, stars. Masser itself replaces the vanilla moon texture",
                "(assets/minecraft/textures/environment/moon_phases.png); a resource pack can override it.").push("sky");
        SKY = b.comment("Master switch for every extra sky element below.").define("enabled", true);
        SECUNDA = b.comment("Draw Secunda, the small white moon, on its own tilted orbit.").define("secunda", true);
        MASSER_HALO = b.comment("Faint reddish glow around Masser at night.").define("masserHalo", true);
        AURORA = b.comment("Aurora borealis on clear nights in cold biomes.").define("aurora", true);
        AURORA_EVERYWHERE = b.comment("Show the aurora in every biome, not just cold ones.").define("auroraEverywhere", false);
        AURORA_MAX_TEMPERATURE = b.comment("Biomes with a base temperature at or below this count as cold (taiga 0.25, snowy plains 0.0).")
                .defineInRange("auroraMaxTemperature", 0.35, -2.0, 2.0);
        AURORA_BRIGHTNESS = b.comment("Aurora brightness multiplier.").defineInRange("auroraBrightness", 1.0, 0.0, 3.0);
        STARS = b.comment("Denser, coloured, gently twinkling star field at night.").define("stars", true);
        GALAXY = b.comment("Faint galaxy band across the night sky.").define("galaxy", true);
        SKIP_WITH_SHADERS = b.comment("Don't draw the extra sky while an Oculus/Iris shader pack is active (shader packs draw their own sky).")
                .define("skipWithShaders", true);
        b.pop();
        b.push("music");
        LEVEL_UP_STING = b.comment("Play the short level-up fanfare (skycraft:music.level_up) when your character levels up.")
                .define("levelUpSting", true);
        b.pop();
        CLIENT_SPEC = b.build();
    }

    private AtmosphereConfig() {}
}
