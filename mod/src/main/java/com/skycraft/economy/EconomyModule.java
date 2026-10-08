package com.skycraft.economy;

import net.minecraftforge.eventbus.api.IEventBus;

/** Item values, barter menus, merchants, skill trainers and Speech. */
public final class EconomyModule {
    private EconomyModule() {}

    /** Registers this module's DeferredRegisters and mod-bus listeners. Called from the mod constructor. */
    public static void init(IEventBus modBus) {
    }

    /** Registers this module's packets via {@link com.skycraft.network.SkyNetwork#register}. Called once at startup. */
    public static void registerPackets() {
    }
}
