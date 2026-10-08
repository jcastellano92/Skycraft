package com.skycraft.atmosphere.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import com.skycraft.Skycraft;
import com.skycraft.atmosphere.AtmosphereConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * The Skyrim night sky, drawn right after the vanilla sky ({@link RenderLevelStageEvent.Stage#AFTER_SKY}) in the
 * overworld: Secunda on its own tilted orbit, a glow around Masser (the retextured vanilla moon), a denser twinkling
 * star field, a faint galaxy band and, on clear nights in cold biomes, the aurora. Skipped while an Oculus/Iris
 * shader pack is active. All draws are additive, like vanilla's sun, moon and stars.
 */
@Mod.EventBusSubscriber(modid = Skycraft.MODID, value = Dist.CLIENT)
public final class SkyRenderer {
    private static final ResourceLocation SECUNDA = new ResourceLocation(Skycraft.MODID, "textures/environment/secunda_phases.png");
    private static final ResourceLocation MASSER_HALO = new ResourceLocation(Skycraft.MODID, "textures/environment/masser_halo.png");
    private static final ResourceLocation GALAXY = new ResourceLocation(Skycraft.MODID, "textures/environment/galaxy.png");

    /** Secunda's orbit: inclined to Masser's and running a little ahead of it. */
    private static final float SECUNDA_TILT = 24.0F;
    private static final float SECUNDA_LEAD = 31.0F;
    private static final float SECUNDA_SIZE = 6.0F;
    private static final float HALO_SIZE = 36.0F;
    private static final double AURORA_KEY_SECONDS = 6.0;

    /** 0..1, how cold the player's surroundings are (smoothed by {@link AtmosphereClientEvents}). */
    static float coldness;
    static float coldnessPrev;

    private SkyRenderer() {}

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) return;
        if (level.dimension() != Level.OVERWORLD || level.effects().skyType() != DimensionSpecialEffects.SkyType.NORMAL) return;
        if (!AtmosphereConfig.SKY.get()) return;
        if (AtmosphereConfig.SKIP_WITH_SHADERS.get() && ShaderCompat.shadersActive()) return;
        Camera camera = event.getCamera();
        if (camera.getFluidInCamera() != FogType.NONE) return;
        if (mc.player.hasEffect(MobEffects.BLINDNESS) || mc.player.hasEffect(MobEffects.DARKNESS)) return;
        if (mc.gui.getBossOverlay().shouldCreateWorldFog()) return;

        float pt = event.getPartialTick();
        float clear = 1.0F - level.getRainLevel(pt);
        float night = Mth.clamp(level.getStarBrightness(pt) * 2.0F, 0.0F, 1.0F);
        if (clear <= 0.01F) return;

        SkyGeometry.ensureBuilt();
        PoseStack pose = event.getPoseStack();
        Matrix4f proj = event.getProjectionMatrix();
        double seconds = (level.getGameTime() + pt) / 20.0;

        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        try {
            if (AtmosphereConfig.AURORA.get()) {
                float cold = AtmosphereConfig.AURORA_EVERYWHERE.get() ? 1.0F : Mth.lerp(pt, coldnessPrev, coldness);
                float alpha = night * night * clear * clear * cold * activity(level) * AtmosphereConfig.AURORA_BRIGHTNESS.get().floatValue();
                if (alpha > 0.003F) drawAurora(pose, proj, alpha, seconds);
            }

            // celestial frame: identical to vanilla's sun/moon/star rotation
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(-90.0F));
            pose.mulPose(Axis.XP.rotationDegrees(level.getTimeOfDay(pt) * 360.0F));
            float starAlpha = night * clear;
            if (starAlpha > 0.003F) {
                if (AtmosphereConfig.GALAXY.get()) drawGalaxy(pose, proj, starAlpha * 0.55F);
                if (AtmosphereConfig.STARS.get()) drawStars(pose, proj, starAlpha, seconds);
            }
            if (AtmosphereConfig.MASSER_HALO.get() && starAlpha > 0.003F) drawHalo(pose.last().pose(), starAlpha * 0.5F);
            pose.popPose();

            if (AtmosphereConfig.SECUNDA.get()) {
                pose.pushPose();
                pose.mulPose(Axis.YP.rotationDegrees(-90.0F));
                pose.mulPose(Axis.ZP.rotationDegrees(SECUNDA_TILT));
                pose.mulPose(Axis.XP.rotationDegrees(level.getTimeOfDay(pt) * 360.0F + SECUNDA_LEAD));
                drawSecunda(pose.last().pose(), clear, secundaPhase(level));
                pose.popPose();
            }
        } finally {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
        }
    }

    // ------------------------------------------------------------------ elements

    private static void drawStars(PoseStack pose, Matrix4f proj, float alpha, double seconds) {
        float a = alpha * (0.82F + 0.18F * (float) Math.sin(seconds * 0.9));
        float b = alpha * (0.82F + 0.18F * (float) Math.sin(seconds * 1.3 + 2.1));
        drawBuffer(SkyGeometry.starsA, pose.last().pose(), proj, GameRenderer.getPositionColorShader(), a);
        drawBuffer(SkyGeometry.starsB, pose.last().pose(), proj, GameRenderer.getPositionColorShader(), b);
    }

    private static void drawGalaxy(PoseStack pose, Matrix4f proj, float alpha) {
        RenderSystem.setShaderTexture(0, GALAXY);
        drawBuffer(SkyGeometry.galaxy, pose.last().pose(), proj, GameRenderer.getPositionTexShader(), alpha);
    }

    private static void drawAurora(PoseStack pose, Matrix4f proj, float alpha, double seconds) {
        double phase = seconds / AURORA_KEY_SECONDS;
        int key = Math.floorMod((long) Math.floor(phase), SkyGeometry.AURORA_KEYS);
        float w = (float) (phase - Math.floor(phase));
        w = w * w * (3.0F - 2.0F * w);
        int next = (key + 1) % SkyGeometry.AURORA_KEYS;
        for (int b = 0; b < SkyGeometry.AURORA_BANDS; b++) {
            float band = alpha * (0.55F + 0.45F * (float) Math.sin(seconds * 0.045 + b * 1.7));
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees((float) Math.sin(seconds * 0.011 + b * 2.3) * 4.0F));
            Matrix4f m = pose.last().pose();
            drawBuffer(SkyGeometry.AURORA[b][key], m, proj, GameRenderer.getPositionColorShader(), band * (1.0F - w));
            drawBuffer(SkyGeometry.AURORA[b][next], m, proj, GameRenderer.getPositionColorShader(), band * w);
            pose.popPose();
        }
    }

    private static void drawBuffer(VertexBuffer vb, Matrix4f modelView, Matrix4f proj, ShaderInstance shader, float alpha) {
        if (vb == null || shader == null || alpha <= 0.002F) return;
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, Math.min(1.0F, alpha));
        vb.bind();
        vb.drawWithShader(modelView, proj, shader);
        VertexBuffer.unbind();
    }

    /** Soft red glow around the vanilla moon (= Masser); same placement as vanilla's moon quad. */
    private static void drawHalo(Matrix4f m, float alpha) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, MASSER_HALO);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
        float s = HALO_SIZE;
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        bb.vertex(m, -s, -100.0F, s).uv(1.0F, 1.0F).endVertex();
        bb.vertex(m, s, -100.0F, s).uv(0.0F, 1.0F).endVertex();
        bb.vertex(m, s, -100.0F, -s).uv(0.0F, 0.0F).endVertex();
        bb.vertex(m, -s, -100.0F, -s).uv(1.0F, 0.0F).endVertex();
        BufferUploader.drawWithShader(bb.end());
    }

    private static void drawSecunda(Matrix4f m, float alpha, int phase) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, SECUNDA);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
        int col = phase % 4;
        int row = phase / 4 % 2;
        float u0 = col / 4.0F, v0 = row / 2.0F, u1 = (col + 1) / 4.0F, v1 = (row + 1) / 2.0F;
        float s = SECUNDA_SIZE;
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        bb.vertex(m, -s, -100.0F, s).uv(u1, v1).endVertex();
        bb.vertex(m, s, -100.0F, s).uv(u0, v1).endVertex();
        bb.vertex(m, s, -100.0F, -s).uv(u0, v0).endVertex();
        bb.vertex(m, -s, -100.0F, -s).uv(u1, v0).endVertex();
        BufferUploader.drawWithShader(bb.end());
    }

    // ------------------------------------------------------------------ timing

    /** Secunda runs a 9-day cycle of 8 phases, out of step with Masser's (vanilla) 8-day one. */
    private static int secundaPhase(ClientLevel level) {
        double days = level.getDayTime() / 24000.0;
        return (int) Math.floorMod((long) Math.floor(days * 8.0 / 9.0) + 3L, 8L);
    }

    /** Some nights the aurora is faint, some nights it blazes: a stable pseudo-random per in-game day, plus slow surges. */
    private static float activity(ClientLevel level) {
        long day = Math.floorDiv(level.getDayTime() + 12000L, 24000L);
        long h = day * 0x9E3779B97F4A7C15L;
        h ^= (h >>> 29);
        h *= 0xBF58476D1CE4E5B9L;
        h ^= (h >>> 32);
        float r = (h & 0xFFFF) / 65535.0F;
        return 0.35F + 0.65F * r;
    }
}
