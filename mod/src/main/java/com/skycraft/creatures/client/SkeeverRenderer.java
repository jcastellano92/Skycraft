package com.skycraft.creatures.client;

import com.skycraft.Skycraft;
import com.skycraft.creatures.entity.SkeeverEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class SkeeverRenderer extends MobRenderer<SkeeverEntity, SkeeverModel<SkeeverEntity>> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(Skycraft.MODID, "textures/entity/skeever.png");

    public SkeeverRenderer(EntityRendererProvider.Context context) {
        super(context, new SkeeverModel<>(context.bakeLayer(CreaturesClient.SKEEVER)), 0.35f);
    }

    @Override
    public ResourceLocation getTextureLocation(SkeeverEntity skeever) {
        return TEXTURE;
    }
}
