package com.skycraft.creatures.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.skycraft.creatures.entity.CorpseEntity;
import com.skycraft.creatures.entity.DragonEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.InventoryMenu;

/**
 * Renders a corpse as the dead creature lying on its side (exactly the pose of the vanilla death animation), using
 * a cached client-side dummy of the creature. Shows the name only when looked at.
 */
public class CorpseRenderer extends EntityRenderer<CorpseEntity> {
    public CorpseRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0f;
    }

    @Override
    public void render(CorpseEntity corpse, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        if (corpse.isVisibleYet()) {
            Entity dummy = corpse.getRenderDummy();
            if (dummy != null) {
                poseStack.pushPose();
                try {
                    EntityRenderer<? super Entity> renderer = this.entityRenderDispatcher.getRenderer(dummy);
                    poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - corpse.getYRot()));
                    if (dummy instanceof DragonEntity) {
                        poseStack.mulPose(Axis.ZP.rotationDegrees(10.0f));
                    } else {
                        float half = corpse.isCentered() ? corpse.getBodyHeight() * 0.5f : 0.0f;
                        float lift = Math.min(dummy.getBbWidth() * 0.5f, 0.75f);
                        poseStack.translate(half, lift, 0.0f);
                        poseStack.mulPose(Axis.ZP.rotationDegrees(90.0f));
                    }
                    renderer.render(dummy, 0.0f, 0.0f, poseStack, buffers, packedLight);
                } catch (Throwable t) {
                    corpse.markRenderFailed();
                } finally {
                    poseStack.popPose();
                }
            }
        }
        super.render(corpse, entityYaw, partialTick, poseStack, buffers, packedLight);
    }

    @Override
    protected boolean shouldShowName(CorpseEntity corpse) {
        return corpse.hasCustomName() && corpse.isVisibleYet() && corpse == this.entityRenderDispatcher.crosshairPickEntity;
    }

    @Override
    public ResourceLocation getTextureLocation(CorpseEntity corpse) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
