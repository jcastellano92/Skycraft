package com.skycraft.fauna.client;

import com.skycraft.client.SkyKeys;
import com.skycraft.fauna.FaunaPackets;
import com.skycraft.network.SkyNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Client events for Fauna:
 * - H key to call horse
 * - A / D counter-steering during horse taming minigame
 * - HUD display for bucking horse balance minigame
 */
@Mod.EventBusSubscriber(modid = com.skycraft.Skycraft.MODID, value = Dist.CLIENT)
public final class FaunaClientEvents {
    private static int promptDirection = 0; // -1 left, 1 right
    private static int promptTicks = 0;
    private static int progress = 0;
    private static boolean activeTame = false;

    private FaunaClientEvents() {}

    public static void handleTamePrompt(FaunaPackets.TamePrompt p) {
        promptDirection = p.direction();
        promptTicks = p.ticks();
        progress = p.progress();
        activeTame = (p.ticks() > 0 || p.progress() > 0) && p.progress() < 100;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Check Call Horse key (H)
        while (SkyKeys.CALL_HORSE.consumeClick()) {
            SkyNetwork.sendToServer(new FaunaPackets.CallHorse());
        }

        // Check steer inputs during taming minigame
        if (activeTame && promptTicks > 0) {
            promptTicks--;
            if (mc.options.keyLeft.consumeClick()) {
                SkyNetwork.sendToServer(new FaunaPackets.SteerBalance(-1));
            } else if (mc.options.keyRight.consumeClick()) {
                SkyNetwork.sendToServer(new FaunaPackets.SteerBalance(1));
            }
        }
    }

    @SubscribeEvent
    public static void renderTameOverlay(RenderGuiOverlayEvent.Post event) {
        if (!activeTame || !VanillaGuiOverlay.CROSSHAIR.id().equals(event.getOverlay().id())) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        GuiGraphics g = event.getGuiGraphics();
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();
        Font font = mc.font;

        int cx = width / 2;
        int cy = height / 2 - 35;

        // Progress bar background
        int barW = 100;
        int barH = 6;
        g.fill(cx - barW / 2 - 1, cy - 1, cx + barW / 2 + 1, cy + barH + 1, 0xA0000000);
        int fill = (int) (barW * (progress / 100.0f));
        g.fill(cx - barW / 2, cy, cx - barW / 2 + fill, cy + barH, 0xFF40C040);

        // Direction prompt
        Component promptText;
        int textColor;
        if (promptDirection < 0) {
            promptText = Component.translatable("fauna.skycraft.tame.counter_left");
            textColor = 0xFFFFE060;
        } else if (promptDirection > 0) {
            promptText = Component.translatable("fauna.skycraft.tame.counter_right");
            textColor = 0xFFFFE060;
        } else {
            promptText = Component.translatable("fauna.skycraft.tame.hold_on");
            textColor = 0xFFFFFFFF;
        }

        int textW = font.width(promptText);
        g.drawString(font, promptText, cx - textW / 2, cy - 12, textColor, true);
    }
}

