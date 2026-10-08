package com.skycraft.creatures.client;

import com.skycraft.Skycraft;
import com.skycraft.creatures.entity.TrollEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class TrollRenderer extends MobRenderer<TrollEntity, TrollModel<TrollEntity>> {
    private static final ResourceLocation TROLL = new ResourceLocation(Skycraft.MODID, "textures/entity/troll/troll.png");
    private static final ResourceLocation FROST = new ResourceLocation(Skycraft.MODID, "textures/entity/troll/frost_troll.png");

    public TrollRenderer(EntityRendererProvider.Context context) {
        super(context, new TrollModel<>(context.bakeLayer(CreaturesClient.TROLL)), 1.0f);
    }

    @Override
    public ResourceLocation getTextureLocation(TrollEntity troll) {
        return troll.isFrost() ? FROST : TROLL;
    }
}
