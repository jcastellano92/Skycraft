package com.skycraft.client;

import com.skycraft.Skycraft;
import com.skycraft.client.screen.RaceScreen;
import com.skycraft.client.screen.SkillsScreen;
import com.skycraft.core.SkyData;
import com.skycraft.network.CorePackets;
import com.skycraft.network.SkyNetwork;
import com.skycraft.perk.Perks;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.BowItem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Forge-bus client events for the core: keys, sprint exhaustion, hiding vanilla HUD bars, bow zoom. */
@Mod.EventBusSubscriber(modid = Skycraft.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    private static boolean wasBlocking;
    private static int powerAttackResend;

    private ClientEvents() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        while (SkyKeys.HUB.consumeClick()) {
            if (mc.screen == null) mc.setScreen(new com.skycraft.client.screen.HubScreen());
        }
        while (SkyKeys.SKILLS.consumeClick()) {
            if (SkyData.get(mc.player).getRace() == null) mc.setScreen(new RaceScreen());
            else mc.setScreen(new SkillsScreen());
        }
        while (SkyKeys.SHOUT.consumeClick()) {
            SkyNetwork.sendToServer(new CorePackets.Action(CorePackets.Action.USE_POWER, 0));
        }

        // Power attack: while the key is held, the server treats swings as power attacks.
        if (SkyKeys.POWER_ATTACK.isDown()) {
            if (powerAttackResend-- <= 0) {
                SkyNetwork.sendToServer(new CorePackets.Action(CorePackets.Action.POWER_ATTACK, 0));
                powerAttackResend = 5;
            }
        } else {
            powerAttackResend = 0;
        }

        boolean blocking = SkyKeys.BLOCK.isDown() && mc.screen == null;
        if (blocking != wasBlocking) {
            SkyNetwork.sendToServer(new CorePackets.Action(blocking ? CorePackets.Action.BLOCK_START : CorePackets.Action.BLOCK_STOP, 0));
            wasBlocking = blocking;
        }

        // Out of stamina: no sprinting until it recovers.
        if (ClientState.has(CorePackets.SyncVitals.EXHAUSTED) && !mc.player.isCreative()) {
            mc.player.setSprinting(false);
            mc.options.keySprint.setDown(false);
        }
    }

    @SubscribeEvent
    public static void hideVanillaBars(RenderGuiOverlayEvent.Pre event) {
        var id = event.getOverlay().id();
        if (id.equals(VanillaGuiOverlay.PLAYER_HEALTH.id()) || id.equals(VanillaGuiOverlay.FOOD_LEVEL.id())
                || id.equals(VanillaGuiOverlay.ARMOR_LEVEL.id()) || id.equals(VanillaGuiOverlay.EXPERIENCE_BAR.id())
                || id.equals(VanillaGuiOverlay.HOTBAR.id())) {
            event.setCanceled(true);
        }
    }

    /** Archery "Eagle Eye": sneak while drawing a bow to zoom in. */
    @SubscribeEvent
    public static void fov(ComputeFovModifierEvent event) {
        var player = event.getPlayer();
        if (player.isUsingItem() && player.getUseItem().getItem() instanceof BowItem && player.isCrouching()
                && Perks.has(player, "archery.eagle_eye")) {
            event.setNewFovModifier(event.getNewFovModifier() * 0.6f);
        }
    }

    /** No vanilla tutorials/toasts: clear toasts on every HUD render. */
    @SubscribeEvent
    public static void clearToasts(net.minecraftforge.client.event.RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getToasts() != null) {
            mc.getToasts().clear();
        }
    }

    /** Intercept screens: prevent MCA destiny/editor screen, redirect death and title screen if desired. */
    @SubscribeEvent
    public static void onScreenOpen(net.minecraftforge.client.event.ScreenEvent.Opening event) {
        net.minecraft.client.gui.screens.Screen screen = event.getNewScreen();
        if (screen == null) return;
        String name = screen.getClass().getName().toLowerCase(java.util.Locale.ROOT);
        if (name.contains("destiny") || name.contains("editor") && name.contains("mca")) {
            event.setCanceled(true);
        }
    }
}
