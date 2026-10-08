package com.skycraft.creatures.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.skycraft.Skycraft;
import com.skycraft.creatures.entity.IceWraithEntity;
import net.minecraft.client.model.BlazeModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Renderer for Ice Wraiths: swirling crystalline frost shards with ice wraith texture. */
public class IceWraithRenderer extends MobRenderer<IceWraithEntity, BlazeModel<IceWraithEntity>> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(Skycraft.MODID, "textures/entity/creatures/ice_wraith.png");

    public IceWraithRenderer(EntityRendererProvider.Context context) {
        super(context, new BlazeModel<>(context.bakeLayer(ModelLayers.BLAZE)), 0.5F);
    }

    @Override
    protected void scale(IceWraithEntity entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(0.85F, 0.85F, 0.85F);
    }

    @Override
    public ResourceLocation getTextureLocation(IceWraithEntity entity) {
        return TEXTURE;
    }
}
