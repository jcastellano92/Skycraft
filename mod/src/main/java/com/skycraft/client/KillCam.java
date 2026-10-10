package com.skycraft.client;

import com.skycraft.Skycraft;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Skyrim Cinematic Kill Cam:
 * When landing a lethal final blow against a boss or powerful enemy, triggers
 * a cinematic 3rd-person slow-motion orbit with impact screen shake and FOV dilation.
 * Cosine interpolation guarantees zero jitter across all frame rates.
 */
@Mod.EventBusSubscriber(modid = Skycraft.MODID, value = Dist.CLIENT)
public final class KillCam {
    private static boolean active = false;
    private static int targetEntityId = -1;
    private static int elapsedTicks = 0;
    private static int maxTicks = 48; // ~2.4 seconds
    private static float startYaw = 0.0f;
    private static float startPitch = 0.0f;
    private static float targetOrbitSpan = 110.0f;
    private static CameraType previousCameraType = CameraType.FIRST_PERSON;
    private static float shakeIntensity = 0.0f;

    private KillCam() {}

    public static boolean isActive() {
        return active;
    }

    public static void trigger(LivingEntity victim) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || victim == null || mc.level == null) return;
        if (active) return; // Already in a kill cam

        active = true;
        targetEntityId = victim.getId();
        elapsedTicks = 0;
        maxTicks = 48;
        shakeIntensity = 1.0f;

        previousCameraType = mc.options.getCameraType();
        // Switch to dramatic third person view
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);

        startYaw = player.getYRot();
        startPitch = player.getXRot();
        targetOrbitSpan = player.getRandom().nextBoolean() ? 100.0f : -100.0f;

        // Play cinematic impact boom sound
        mc.level.playSound(player, player.blockPosition(), SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.PLAYERS, 0.6f, 0.7f);
    }

    public static void stop() {
        if (!active) return;
        active = false;
        targetEntityId = -1;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options != null && previousCameraType != null) {
            mc.options.setCameraType(previousCameraType);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!active) return;

        elapsedTicks++;
        shakeIntensity = Math.max(0.0f, shakeIntensity - 0.08f);

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            stop();
            return;
        }

        if (elapsedTicks >= maxTicks) {
            stop();
        }
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (!active || targetEntityId < 0) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null) return;
        Entity target = mc.level.getEntity(targetEntityId);

        float partial = (float) event.getPartialTick();
        float progress = Mth.clamp((elapsedTicks + partial) / (float) maxTicks, 0.0f, 1.0f);

        // Smooth cosine S-curve for cinematic camera pan
        float smoothProgress = 0.5f - 0.5f * Mth.cos(progress * (float) Math.PI);

        float orbitYaw = startYaw + (smoothProgress * targetOrbitSpan);
        float orbitPitch = Mth.lerp(smoothProgress, Math.min(25.0f, startPitch), 12.0f);

        // Add impact camera shake decaying over time
        if (shakeIntensity > 0.01f) {
            float shake = (float) Math.sin((elapsedTicks + partial) * 2.2f) * shakeIntensity * 1.8f;
            orbitPitch += shake;
            event.setRoll(shake * 0.75f);
        } else {
            event.setRoll(0.0f);
        }

        event.setYaw(orbitYaw);
        event.setPitch(orbitPitch);
    }

    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (!active) return;

        float partial = (float) event.getPartialTick();
        float progress = Mth.clamp((elapsedTicks + partial) / (float) maxTicks, 0.0f, 1.0f);

        // Slow cinematic FOV punch and ease
        float smoothProgress = 0.5f - 0.5f * Mth.cos(progress * (float) Math.PI);
        double baseFov = event.getFOV();
        double targetFov = baseFov * 0.78; // 22% cinematic slow-mo zoom
        event.setFOV(Mth.lerp(smoothProgress, baseFov, targetFov));
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!event.getEntity().level().isClientSide) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Check if victim died to player
        if (event.getSource().getEntity() == mc.player) {
            LivingEntity victim = event.getEntity();
            String typeName = victim.getType().getDescriptionId().toLowerCase(java.util.Locale.ROOT);

            boolean isBoss = typeName.contains("dragon") || typeName.contains("giant")
                    || typeName.contains("troll") || typeName.contains("chief")
                    || typeName.contains("deathlord") || typeName.contains("wisp")
                    || typeName.contains("centurion") || typeName.contains("hagraven");

            if (isBoss) {
                trigger(victim);
            } else if (mc.player.getRandom().nextFloat() < 0.18f && mc.player.distanceTo(victim) < 8.0f) {
                // 18% chance on normal combat final hits
                trigger(victim);
            }
        }
    }
}
