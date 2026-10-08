package com.skycraft.creatures.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.skycraft.Skycraft;
import com.skycraft.creatures.entity.DragonEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Renders dragons at twice the model size, pitching and banking with their flight. */
public class DragonRenderer extends MobRenderer<DragonEntity, DragonModel> {
    private static final ResourceLocation[] TEXTURES = new ResourceLocation[DragonEntity.VARIANTS.length];

    static {
        for (int i = 0; i < TEXTURES.length; i++) {
            TEXTURES[i] = new ResourceLocation(Skycraft.MODID, "textures/entity/dragon/" + DragonEntity.VARIANTS[i] + ".png");
        }
    }

    public DragonRenderer(EntityRendererProvider.Context context) {
        super(context, new DragonModel(context.bakeLayer(CreaturesClient.DRAGON)), 3.0f);
    }

    @Override
    public ResourceLocation getTextureLocation(DragonEntity dragon) {
        return TEXTURES[dragon.getVariant()];
    }

    @Override
    protected void scale(DragonEntity dragon, PoseStack poseStack, float partialTick) {
        poseStack.scale(2.0f, 2.0f, 2.0f);
    }

    @Override
    protected void setupRotations(DragonEntity dragon, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTicks) {
        super.setupRotations(dragon, poseStack, ageInTicks, rotationYaw, partialTicks);
        if (dragon.isFlying() && dragon.deathTime <= 0 && !dragon.isCorpsePose()) {
            float pitch = Mth.lerp(partialTicks, dragon.xRotO, dragon.getXRot());
            float turn = Mth.wrapDegrees(dragon.yBodyRot - dragon.yBodyRotO);
            float roll = Mth.clamp(turn * 4.0f, -35.0f, 35.0f);
            poseStack.translate(0.0, 1.75, 0.0);
            poseStack.mulPose(Axis.XP.rotationDegrees(-pitch));
            poseStack.mulPose(Axis.ZP.rotationDegrees(roll));
            poseStack.translate(0.0, -1.75, 0.0);
        }
    }

    /** Dragons don't flip on their side when dying: they crash down belly first. */
    @Override
    protected float getFlipDegrees(DragonEntity dragon) {
        return 0.0f;
    }
}
