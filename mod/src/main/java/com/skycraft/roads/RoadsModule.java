package com.skycraft.roads;

import net.minecraftforge.eventbus.api.IEventBus;

/** Road network between settlements, signposts, and travelers/caravans/patrols walking the roads. */
public final class RoadsModule {
    private RoadsModule() {}

    /** Registers this module's DeferredRegisters and mod-bus listeners. Called from the mod constructor. */
    public static void init(IEventBus modBus) {
    }

    /** Registers this module's packets via {@link com.skycraft.network.SkyNetwork#register}. Called once at startup. */
    public static void registerPackets() {
    }
}
