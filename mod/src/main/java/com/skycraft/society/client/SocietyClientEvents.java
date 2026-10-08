package com.skycraft.society.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.skycraft.Skycraft;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.List;

/** Forge-bus client events: overhead speech bubbles, the reputation key, cleanup on disconnect. */
@Mod.EventBusSubscriber(modid = Skycraft.MODID, value = Dist.CLIENT)
public final class SocietyClientEvents {
    private static final int TEXT = 0xF5EBC8;
    private static final int WRAP = 150;
    private static final float FADE_IN = 4f;
    private static final float FADE_OUT = 15f;

    private SocietyClientEvents() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ClientSociety.tick();
        Minecraft mc = Minecraft.getInstance();
        while (SocietyClient.REPUTATION.consumeClick()) {
            if (mc.player != null && mc.screen == null) mc.setScreen(new ReputationScreen());
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientSociety.clear();
    }

    /** Draws a speech bubble above entities that are talking (billboarded, fading in and out). */
    @SubscribeEvent
    public static void onRenderLiving(RenderLivingEvent.Post<?, ?> event) {
        if (ClientSociety.BARKS.isEmpty()) return;
        LivingEntity entity = event.getEntity();
        ClientSociety.Bark bark = ClientSociety.BARKS.get(entity.getId());
        if (bark == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || entity.isInvisible()) return;
        if (entity == mc.player && mc.options.getCameraType().isFirstPerson()) return;
        if (entity.distanceToSqr(mc.player) > 32 * 32) return;

        float age = (ClientSociety.ticks - bark.start()) + event.getPartialTick();
        float alpha = Math.min(1f, age / FADE_IN);
        float remaining = bark.ticks() - age;
        if (remaining < FADE_OUT) alpha = Math.min(alpha, remaining / FADE_OUT);
        int a = (int) (alpha * 255f);
        if (a < 8) return;

        Font font = mc.font;
        List<FormattedCharSequence> lines = font.split(bark.text(), WRAP);
        if (lines.isEmpty()) return;
        PoseStack pose = event.getPoseStack();
        MultiBufferSource buffers = event.getMultiBufferSource();
        pose.pushPose();
        pose.translate(0.0, entity.getBbHeight() + 0.8, 0.0);
        pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        pose.scale(-0.025f, -0.025f, 0.025f);
        Matrix4f matrix = pose.last().pose();
        int color = (a << 24) | TEXT;
        int background = ((int) (alpha * 0.6f * 255f) << 24);
        int lineHeight = 10;
        float top = -lines.size() * lineHeight;
        for (int i = 0; i < lines.size(); i++) {
            FormattedCharSequence line = lines.get(i);
            float x = -font.width(line) / 2f;
            font.drawInBatch(line, x, top + i * lineHeight, color, false, matrix, buffers, Font.DisplayMode.NORMAL,
                    background, LightTexture.FULL_BRIGHT);
        }
        pose.popPose();
    }
}
