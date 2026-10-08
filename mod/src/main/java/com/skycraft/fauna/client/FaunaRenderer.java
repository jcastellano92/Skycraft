package com.skycraft.fauna.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;

import java.util.function.Function;

/** Generic renderer of the fauna module: a model, a per-entity texture and a uniform scale. */
public class FaunaRenderer<T extends Mob, M extends EntityModel<T>> extends MobRenderer<T, M> {
    private final Function<T, ResourceLocation> texture;
    private final float scale;

    public FaunaRenderer(EntityRendererProvider.Context context, M model, float shadow, float scale, Function<T, ResourceLocation> texture) {
        super(context, model, shadow * scale);
        this.texture = texture;
        this.scale = scale;
    }

    /** Adds a full-bright layer (e.g. the torchbug's glowing abdomen). */
    public FaunaRenderer<T, M> withGlow(ResourceLocation glowTexture) {
        this.addLayer(new GlowLayer<>(this, glowTexture));
        return this;
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return texture.apply(entity);
    }

    @Override
    protected void scale(T entity, PoseStack poseStack, float partialTick) {
        if (scale != 1.0f) poseStack.scale(scale, scale, scale);
    }

    /** Fish out of water: wiggle, and flop on the side (like vanilla cod). */
    public static class Fish<T extends Mob, M extends EntityModel<T>> extends FaunaRenderer<T, M> {
        public Fish(EntityRendererProvider.Context context, M model, float shadow, float scale, Function<T, ResourceLocation> texture) {
            super(context, model, shadow, scale, texture);
        }

        @Override
        protected void setupRotations(T entity, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTicks) {
            super.setupRotations(entity, poseStack, ageInTicks, rotationYaw, partialTicks);
            poseStack.mulPose(Axis.YP.rotationDegrees(4.3f * Mth.sin(0.6f * ageInTicks)));
            if (!entity.isInWater()) {
                poseStack.translate(0.1f, 0.1f, -0.1f);
                poseStack.mulPose(Axis.ZP.rotationDegrees(90.0f));
            }
        }
    }

    /** A full-bright emissive layer. */
    public static class GlowLayer<T extends Mob, M extends EntityModel<T>> extends EyesLayer<T, M> {
        private final RenderType type;

        public GlowLayer(RenderLayerParent<T, M> parent, ResourceLocation texture) {
            super(parent);
            this.type = RenderType.eyes(texture);
        }

        @Override
        public RenderType renderType() {
            return type;
        }
    }
}
