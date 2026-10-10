package com.skycraft.client;

import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.skycraft.Skycraft;
import com.skycraft.client.screen.RaceScreen;
import com.skycraft.core.Race;
import com.skycraft.core.SkyData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.util.EnumMap;
import java.util.Map;

/**
 * Skyrim Race Skin Overhaul:
 * Overrides player skin textures with authentic Skyrim race models (Nord, Imperial, Dunmer, Orc, Khajiit, etc.)
 * both in the Character Creator preview screen and in-game, working alongside Essential.
 */
@Mod.EventBusSubscriber(modid = Skycraft.MODID, value = Dist.CLIENT)
public final class RaceSkins {
    private static final Map<Race, ResourceLocation> TEXTURES = new EnumMap<>(Race.class);

    static {
        for (Race r : Race.VALUES) {
            TEXTURES.put(r, new ResourceLocation(Skycraft.MODID, "textures/entity/race/" + r.id() + ".png"));
        }
    }

    private static Race previewRace = null;
    private static Field textureLocationsField = null;
    private static boolean reflectionFailed = false;

    private RaceSkins() {}

    public static ResourceLocation getTexture(Race race) {
        if (race == null) return TEXTURES.get(Race.NORD);
        return TEXTURES.getOrDefault(race, TEXTURES.get(Race.NORD));
    }

    public static void setPreviewRace(Race race) {
        previewRace = race;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            applyToPlayer(mc.player, race);
        }
    }

    public static Race getPreviewRace() {
        return previewRace;
    }

    public static Race getEffectiveRace(Player player) {
        if (player == null) return null;
        if (player == Minecraft.getInstance().player && previewRace != null) {
            return previewRace;
        }
        return SkyData.get(player).getRace();
    }

    public static void applyToPlayer(Player player, Race race) {
        if (player == null || race == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(player.getUUID());
            if (info != null) {
                applyToPlayerInfo(info, race);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static void applyToPlayerInfo(PlayerInfo info, Race race) {
        if (info == null || race == null || reflectionFailed) return;
        try {
            if (textureLocationsField == null) {
                for (Field f : PlayerInfo.class.getDeclaredFields()) {
                    if (Map.class.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        textureLocationsField = f;
                        break;
                    }
                }
            }
            if (textureLocationsField != null) {
                Map<MinecraftProfileTexture.Type, ResourceLocation> map =
                        (Map<MinecraftProfileTexture.Type, ResourceLocation>) textureLocationsField.get(info);
                if (map != null) {
                    map.put(MinecraftProfileTexture.Type.SKIN, getTexture(race));
                }
            }
        } catch (Throwable t) {
            reflectionFailed = true;
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // In Character Creator: maintain selected race skin
        if (mc.screen instanceof RaceScreen) {
            if (previewRace != null) {
                applyToPlayer(mc.player, previewRace);
            }
            return;
        }

        // In active world: apply player's saved Skyrim race
        Race race = SkyData.get(mc.player).getRace();
        if (race != null) {
            applyToPlayer(mc.player, race);
        }
    }

    /**
     * Render layer attached to PlayerRenderer to render the full Skyrim race model over the player body.
     */
    public static class Layer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        public Layer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                           AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                           float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
            Race race = getEffectiveRace(player);
            if (race == null) return;

            ResourceLocation skin = getTexture(race);
            VertexConsumer vertexConsumer = buffer.getBuffer(RenderType.entityCutoutNoCull(skin));
            this.getParentModel().renderToBuffer(poseStack, vertexConsumer, packedLight,
                    LivingEntityRenderer.getOverlayCoords(player, 0.0F), 1.0F, 1.0F, 1.0F, 1.0F);
        }
    }
}
