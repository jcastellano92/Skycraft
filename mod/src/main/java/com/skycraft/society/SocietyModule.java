package com.skycraft.society;

import net.minecraftforge.eventbus.api.IEventBus;

/** NPC types, overhead dialogue barks, reputation, random encounters, faction cosmetics and PvP rules. */
public final class SocietyModule {
    private SocietyModule() {}

    /** Registers this module's DeferredRegisters and mod-bus listeners. Called from the mod constructor. */
    public static void init(IEventBus modBus) {
    }

    /** Registers this module's packets via {@link com.skycraft.network.SkyNetwork#register}. Called once at startup. */
    public static void registerPackets() {
    }
}
