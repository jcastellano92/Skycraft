package com.skycraft.dungeons;

import net.minecraftforge.eventbus.api.IEventBus;

/** Procedural Skyrim dungeons: Nordic barrows, caves, Dwemer ruins, lairs, forts, mines, camps, shrines. */
public final class DungeonsModule {
    private DungeonsModule() {}

    /** Registers this module's DeferredRegisters and mod-bus listeners. Called from the mod constructor. */
    public static void init(IEventBus modBus) {
    }

    /** Registers this module's packets via {@link com.skycraft.network.SkyNetwork#register}. Called once at startup. */
    public static void registerPackets() {
    }
}
