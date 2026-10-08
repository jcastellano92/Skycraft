package com.skycraft.arsenal;

import net.minecraftforge.eventbus.api.IEventBus;

/** Leveled loot, named artifacts, arrow types, crossbows, staves, arrow physics and kill cams. */
public final class ArsenalModule {
    private ArsenalModule() {}

    /** Registers this module's DeferredRegisters and mod-bus listeners. Called from the mod constructor. */
    public static void init(IEventBus modBus) {
    }

    /** Registers this module's packets via {@link com.skycraft.network.SkyNetwork#register}. Called once at startup. */
    public static void registerPackets() {
    }
}
