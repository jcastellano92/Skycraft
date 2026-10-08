package com.skycraft.lore;

import net.minecraftforge.eventbus.api.IEventBus;

/** Standing Stones (Guardian Stones), readable Skyrim-style books and notes. */
public final class LoreModule {
    private LoreModule() {}

    /** Registers this module's DeferredRegisters and mod-bus listeners. Called from the mod constructor. */
    public static void init(IEventBus modBus) {
    }

    /** Registers this module's packets via {@link com.skycraft.network.SkyNetwork#register}. Called once at startup. */
    public static void registerPackets() {
    }
}
