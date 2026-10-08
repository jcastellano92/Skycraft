package com.skycraft.creatures;

import net.minecraftforge.eventbus.api.IEventBus;

/** Draugr, bandits, giants, dragons, lootable corpses, leveled enemies and Skyrim-like spawning. */
public final class CreaturesModule {
    private CreaturesModule() {}

    /** Registers this module's DeferredRegisters and mod-bus listeners. Called from the mod constructor. */
    public static void init(IEventBus modBus) {
    }

    /** Registers this module's packets via {@link com.skycraft.network.SkyNetwork#register}. Called once at startup. */
    public static void registerPackets() {
    }
}
