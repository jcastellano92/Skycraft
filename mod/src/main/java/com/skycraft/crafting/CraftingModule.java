package com.skycraft.crafting;

import com.skycraft.crafting.arcane.ArcaneModule;
import net.minecraftforge.eventbus.api.IEventBus;

/** Ores, ingots, Skyrim weapon & armor tiers, the forge, tempering, enchanting, alchemy and soul gems. */
public final class CraftingModule {
    private CraftingModule() {}

    /** Registers this module's DeferredRegisters and mod-bus listeners. Called from the mod constructor. */
    public static void init(IEventBus modBus) {
        // keep: the arcane sub-module (enchanting, soul gems, alchemy) is developed separately
        ArcaneModule.init(modBus);
    }

    /** Registers this module's packets via {@link com.skycraft.network.SkyNetwork#register}. Called once at startup. */
    public static void registerPackets() {
        // keep: arcane packets are registered after the crafting ones
        ArcaneModule.registerPackets();
    }
}
