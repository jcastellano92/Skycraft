package com.skycraft.client;

import com.skycraft.Skycraft;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Skyrim-style conversation camera ease: smoothly rotates view toward NPC face and zooms FOV in.
 */
@Mod.EventBusSubscriber(modid = Skycraft.MODID, value = Dist.CLIENT)
public final class DialogueCamera {
    private static int targetEntityId = -1;
    private static boolean active = false;
    private static float easeProgress = 0.0f;

    private DialogueCamera() {}

    public static void start(int entityId) {
        targetEntityId = entityId;
        active = true;
    }

    public static void stop() {
        active = false;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (active) {
            if (easeProgress < 1.0f) {
                easeProgress = Math.min(1.0f, easeProgress + 0.1f);
            }
        } else {
            if (easeProgress > 0.0f) {
                easeProgress = Math.max(0.0f, easeProgress - 0.12f);
                if (easeProgress <= 0.0f) {
                    targetEntityId = -1;
                }
            }
        }
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (easeProgress <= 0.0f || targetEntityId < 0) return;
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null) return;
        Entity target = mc.level.getEntity(targetEntityId);
        if (target == null) return;

        Vec3 playerEye = player.getEyePosition((float) event.getPartialTick());
        Vec3 targetEye = target.getEyePosition((float) event.getPartialTick());
        Vec3 diff = targetEye.subtract(playerEye);
        double distHoriz = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
        if (distHoriz < 0.001) return;

        float wantedYaw = (float) (Mth.atan2(diff.z, diff.x) * (180.0 / Math.PI)) - 90.0F;
        float wantedPitch = (float) -(Mth.atan2(diff.y, distHoriz) * (180.0 / Math.PI));

        float smoothFactor = Mth.sin(easeProgress * (float) (Math.PI / 2.0));
        float currentYaw = event.getYaw();
        float currentPitch = event.getPitch();

        float blendedYaw = Mth.rotLerp(smoothFactor, currentYaw, wantedYaw);
        float blendedPitch = Mth.rotLerp(smoothFactor, currentPitch, wantedPitch);

        event.setYaw(blendedYaw);
        event.setPitch(blendedPitch);
    }

    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (easeProgress <= 0.0f) return;
        float smoothFactor = Mth.sin(easeProgress * (float) (Math.PI / 2.0));
        double baseFov = event.getFOV();
        // Zoom in by ~18% (Skyrim conversation zoom)
        double targetFov = baseFov * 0.82;
        event.setFOV(Mth.lerp(smoothFactor, baseFov, targetFov));
    }
}

