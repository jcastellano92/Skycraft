package com.skycraft.economy;

import com.skycraft.Skycraft;
import com.skycraft.network.SkyNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Forge-bus events of the economy: loading and syncing item values. */
@Mod.EventBusSubscriber(modid = Skycraft.MODID)
public final class EconomyEvents {
    private EconomyEvents() {}

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new ItemValues.Loader());
    }

    /** Fired when a player joins (player set) and after /reload (player null = everyone). */
    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        EconomyPackets.SyncValues msg = EconomyPackets.SyncValues.current();
        ServerPlayer target = event.getPlayer();
        if (target != null) {
            SkyNetwork.sendToPlayer(target, msg);
        } else {
            for (ServerPlayer p : event.getPlayerList().getPlayers()) SkyNetwork.sendToPlayer(p, msg);
        }
    }

    /** Item tags were (re)bound on this side: tag-based values must be resolved again. */
    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        ItemValues.clearCache();
    }
}
