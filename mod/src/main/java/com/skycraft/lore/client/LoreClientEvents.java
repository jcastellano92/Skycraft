package com.skycraft.lore.client;

import com.skycraft.Skycraft;
import com.skycraft.lore.LorePackets;
import com.skycraft.network.SkyNetwork;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Forge-bus client events for the lore module: the stone power key and the stone-change HUD banner. */
@Mod.EventBusSubscriber(modid = Skycraft.MODID, value = Dist.CLIENT)
public final class LoreClientEvents {
    private LoreClientEvents() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            StoneHud.reset();
            return;
        }
        while (LoreClientSetup.STONE_POWER.consumeClick()) {
            SkyNetwork.sendToServer(new LorePackets.UsePower());
        }
        StoneHud.tick(mc);
    }
}
