package com.skycraft.crafting;

import net.minecraftforge.eventbus.api.IEventBus;

/** Ores, ingots, Skyrim weapon & armor tiers, the forge, tempering, enchanting, alchemy and soul gems. */
public final class CraftingModule {
    private CraftingModule() {}

    /** Registers this module's DeferredRegisters and mod-bus listeners. Called from the mod constructor. */
    public static void init(IEventBus modBus) {
    }

    /** Registers this module's packets via {@link com.skycraft.network.SkyNetwork#register}. Called once at startup. */
    public static void registerPackets() {
    }
}
