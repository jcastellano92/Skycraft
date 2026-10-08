package com.skycraft.inventory;

import com.skycraft.Skycraft;
import com.skycraft.network.SkyNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Forge-bus events of the inventory module: loading and syncing item weights. */
@Mod.EventBusSubscriber(modid = Skycraft.MODID)
public final class InventoryEvents {
    private InventoryEvents() {}

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new ItemWeights.Loader());
    }

    /** Fired when a player joins (player set) and after /reload (player null = everyone). */
    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        InventoryPackets.SyncWeights msg = InventoryPackets.SyncWeights.current();
        ServerPlayer target = event.getPlayer();
        if (target != null) {
            SkyNetwork.sendToPlayer(target, msg);
            CarryWeight.invalidate(target);
        } else {
            for (ServerPlayer p : event.getPlayerList().getPlayers()) {
                SkyNetwork.sendToPlayer(p, msg);
                CarryWeight.invalidate(p);
            }
        }
    }

    /** Item tags were (re)bound on this side: tag-based weights must be resolved again. */
    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        ItemWeights.clearCache();
    }
}
