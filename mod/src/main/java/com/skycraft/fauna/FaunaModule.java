package com.skycraft.fauna;

import net.minecraftforge.eventbus.api.IEventBus;

/** Skyrim wildlife, livestock, catchable insects and regrowing alchemy plants. */
public final class FaunaModule {
    private FaunaModule() {}

    /** Registers this module's DeferredRegisters and mod-bus listeners. Called from the mod constructor. */
    public static void init(IEventBus modBus) {
    }

    /** Registers this module's packets via {@link com.skycraft.network.SkyNetwork#register}. Called once at startup. */
    public static void registerPackets() {
    }
}
