package com.skycraft.arsenal.client;

import com.skycraft.arsenal.entity.SkyArrow;
import com.skycraft.arsenal.item.ArrowKind;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/** Skyrim arrows use the vanilla arrow model; elemental arrows and bolts glow like spectral arrows. */
public class SkyArrowRenderer extends ArrowRenderer<SkyArrow> {
    private static final ResourceLocation NORMAL = new ResourceLocation("textures/entity/projectiles/arrow.png");
    private static final ResourceLocation GLOWING = new ResourceLocation("textures/entity/projectiles/spectral_arrow.png");

    public SkyArrowRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(SkyArrow arrow) {
        return arrow.element() == ArrowKind.Element.NONE ? NORMAL : GLOWING;
    }
}
