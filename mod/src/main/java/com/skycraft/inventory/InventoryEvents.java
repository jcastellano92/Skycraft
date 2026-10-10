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

    /** Convert any coin purse or septims sitting in player inventory directly into gold. */
    @SubscribeEvent
    public static void onPlayerTick(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END || event.player.level().isClientSide) return;
        var inv = event.player.getInventory();
        long totalFound = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            net.minecraft.world.item.ItemStack stack = inv.getItem(i);
            long val = com.skycraft.core.Currency.valueOf(stack);
            if (val > 0) {
                totalFound += val;
                inv.setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
            }
        }
        if (totalFound > 0) {
            com.skycraft.core.Currency.give(event.player, totalFound);
        }
    }

    /** Item tags were (re)bound on this side: tag-based weights must be resolved again. */
    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        ItemWeights.clearCache();
    }
}
