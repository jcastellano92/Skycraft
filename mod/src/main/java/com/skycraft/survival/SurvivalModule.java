package com.skycraft.survival;

import net.minecraftforge.eventbus.api.IEventBus;

/** Skyrim food and drink, cooking, inns and renting rooms, diseases and shrines, regenerating ore veins. */
public final class SurvivalModule {
    private SurvivalModule() {}

    /** Registers this module's DeferredRegisters and mod-bus listeners. Called from the mod constructor. */
    public static void init(IEventBus modBus) {
    }

    /** Registers this module's packets via {@link com.skycraft.network.SkyNetwork#register}. Called once at startup. */
    public static void registerPackets() {
    }
}
