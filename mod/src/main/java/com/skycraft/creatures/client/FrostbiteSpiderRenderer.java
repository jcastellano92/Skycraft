package com.skycraft.creatures.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.skycraft.Skycraft;
import com.skycraft.creatures.entity.FrostbiteSpiderEntity;
import net.minecraft.client.model.SpiderModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Renderer for Frostbite Spiders: 1.35x scale and icy blue/white texture. */
public class FrostbiteSpiderRenderer extends MobRenderer<FrostbiteSpiderEntity, SpiderModel<FrostbiteSpiderEntity>> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(Skycraft.MODID, "textures/entity/creatures/frostbite_spider.png");

    public FrostbiteSpiderRenderer(EntityRendererProvider.Context context) {
        super(context, new SpiderModel<>(context.bakeLayer(ModelLayers.SPIDER)), 1.1F);
    }

    @Override
    protected void scale(FrostbiteSpiderEntity entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(1.35F, 1.35F, 1.35F);
    }

    @Override
    public ResourceLocation getTextureLocation(FrostbiteSpiderEntity entity) {
        return TEXTURE;
    }
}
