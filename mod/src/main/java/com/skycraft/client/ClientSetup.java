package com.skycraft.client;

import com.skycraft.Skycraft;
import com.skycraft.client.hud.SkyHud;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Mod-bus client registration for the core: key mappings and HUD overlays. */
@Mod.EventBusSubscriber(modid = Skycraft.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}

    @SubscribeEvent
    public static void keys(RegisterKeyMappingsEvent event) {
        for (KeyMapping key : SkyKeys.ALL) event.register(key);
    }

    @SubscribeEvent
    public static void overlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "vitals", SkyHud::renderVitals);
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "sneak_eye", SkyHud::renderSneakEye);
        event.registerAboveAll("notifications", SkyHud::renderNotifications);
    }
}
