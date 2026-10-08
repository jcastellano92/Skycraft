package com.skycraft.atmosphere;

import net.minecraftforge.eventbus.api.IEventBus;

/** Skyrim sky (Masser and Secunda, aurora, stars) and the original Skycraft soundtrack. */
public final class AtmosphereModule {
    private AtmosphereModule() {}

    /** Registers this module's DeferredRegisters and mod-bus listeners. Called from the mod constructor. */
    public static void init(IEventBus modBus) {
    }

    /** Registers this module's packets via {@link com.skycraft.network.SkyNetwork#register}. Called once at startup. */
    public static void registerPackets() {
    }
}
