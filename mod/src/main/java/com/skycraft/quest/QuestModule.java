package com.skycraft.quest;

import net.minecraftforge.eventbus.api.IEventBus;

/** Parties, quests, radiant bounties, factions (Companions, College, Thieves Guild, Dark Brotherhood...) and the journal. */
public final class QuestModule {
    private QuestModule() {}

    /** Registers this module's DeferredRegisters and mod-bus listeners. Called from the mod constructor. */
    public static void init(IEventBus modBus) {
    }

    /** Registers this module's packets via {@link com.skycraft.network.SkyNetwork#register}. Called once at startup. */
    public static void registerPackets() {
    }
}
