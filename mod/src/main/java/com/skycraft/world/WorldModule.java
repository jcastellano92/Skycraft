package com.skycraft.world;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

/**
 * Location discovery, compass, world map & fast travel, sleeping and waiting, music and Oblivion.
 *
 * <p>Server side: {@link Discovery}, {@link RestManager}, {@link FastTravelHandler} (Forge-bus subscribers).
 * Client side: {@code com.skycraft.world.client} (map, wait/sleep screen, soundtrack, fog, compass markers).</p>
 */
public final class WorldModule {
    private WorldModule() {}

    /** Registers this module's DeferredRegisters and mod-bus listeners. Called from the mod constructor. */
    public static void init(IEventBus modBus) {
        WorldSounds.init(modBus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, WorldConfig.COMMON_SPEC, "skycraft-world-common.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, WorldConfig.CLIENT_SPEC, "skycraft-world-client.toml");
    }

    /** Registers this module's packets via {@link com.skycraft.network.SkyNetwork#register}. Called once at startup. */
    public static void registerPackets() {
        WorldPackets.register();
    }
}
