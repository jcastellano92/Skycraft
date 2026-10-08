package com.skycraft.inventory;

import net.minecraftforge.eventbus.api.IEventBus;

/** Skyrim inventory screen, item weights, carry weight, favorites, hand assignment and item persistence. */
public final class InventoryModule {
    private InventoryModule() {}

    /** Registers this module's DeferredRegisters and mod-bus listeners. Called from the mod constructor. */
    public static void init(IEventBus modBus) {
    }

    /** Registers this module's packets via {@link com.skycraft.network.SkyNetwork#register}. Called once at startup. */
    public static void registerPackets() {
    }
}
