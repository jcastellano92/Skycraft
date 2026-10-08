package com.skycraft.world;

import net.minecraftforge.eventbus.api.IEventBus;

/** Location discovery, compass, sleeping and waiting, music and Oblivion. */
public final class WorldModule {
    private WorldModule() {}

    /** Registers this module's DeferredRegisters and mod-bus listeners. Called from the mod constructor. */
    public static void init(IEventBus modBus) {
    }

    /** Registers this module's packets via {@link com.skycraft.network.SkyNetwork#register}. Called once at startup. */
    public static void registerPackets() {
    }
}
