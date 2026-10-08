package com.skycraft.magic;

import net.minecraftforge.eventbus.api.IEventBus;

/** Spells (learned from tomes), casting, magicka costs, Dragon Shouts, Words of Power and Word Walls. */
public final class MagicModule {
    private MagicModule() {}

    /** Registers this module's DeferredRegisters and mod-bus listeners. Called from the mod constructor. */
    public static void init(IEventBus modBus) {
    }

    /** Registers this module's packets via {@link com.skycraft.network.SkyNetwork#register}. Called once at startup. */
    public static void registerPackets() {
    }
}
