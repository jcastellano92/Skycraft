package com.skycraft.society.client;

import com.skycraft.Skycraft;
import com.skycraft.society.NpcRole;
import com.skycraft.society.entity.NpcEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;

/** Renders society NPCs with their role skin, vanilla armor and held items. */
public class NpcRenderer extends HumanoidMobRenderer<NpcEntity, NpcModel> {
    private static final ResourceLocation[][] TEXTURES = new ResourceLocation[NpcRole.VALUES.length][NpcRole.SKINS];

    static {
        for (NpcRole role : NpcRole.VALUES) {
            for (int i = 0; i < NpcRole.SKINS; i++) {
                TEXTURES[role.ordinal()][i] = new ResourceLocation(Skycraft.MODID, "textures/entity/society/" + role.id + "_" + i + ".png");
            }
        }
    }

    public NpcRenderer(EntityRendererProvider.Context context) {
        super(context, new NpcModel(context.bakeLayer(SocietyClient.NPC_LAYER)), 0.5f);
        this.addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()));
    }

    @Override
    public ResourceLocation getTextureLocation(NpcEntity entity) {
        return TEXTURES[entity.role().ordinal()][Math.floorMod(entity.getSkin(), NpcRole.SKINS)];
    }
}
