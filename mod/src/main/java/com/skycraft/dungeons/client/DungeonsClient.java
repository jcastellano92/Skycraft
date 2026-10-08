package com.skycraft.dungeons.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.skycraft.Skycraft;
import com.skycraft.dungeons.DungeonsRegistry;
import com.skycraft.dungeons.entity.DwarvenCenturion;
import com.skycraft.dungeons.entity.DwarvenSphere;
import com.skycraft.dungeons.entity.DwarvenSpider;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Mod-bus client registration for the dungeons module: automaton model layers and renderers. */
@Mod.EventBusSubscriber(modid = Skycraft.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class DungeonsClient {
    public static final ModelLayerLocation SPIDER = layer("dwarven_spider");
    public static final ModelLayerLocation SPHERE = layer("dwarven_sphere");
    public static final ModelLayerLocation CENTURION = layer("dwarven_centurion");

    private static final ResourceLocation SPIDER_TEX = tex("dwarven_spider");
    private static final ResourceLocation SPHERE_TEX = tex("dwarven_sphere");
    private static final ResourceLocation CENTURION_TEX = tex("dwarven_centurion");

    private DungeonsClient() {}

    private static ModelLayerLocation layer(String name) {
        return new ModelLayerLocation(new ResourceLocation(Skycraft.MODID, name), "main");
    }

    private static ResourceLocation tex(String name) {
        return new ResourceLocation(Skycraft.MODID, "textures/entity/dwemer/" + name + ".png");
    }

    @SubscribeEvent
    public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(SPIDER, DwarvenSpiderModel::createLayer);
        event.registerLayerDefinition(SPHERE, DwarvenSphereModel::createLayer);
        event.registerLayerDefinition(CENTURION, DwarvenCenturionModel::createLayer);
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(DungeonsRegistry.DWARVEN_SPIDER.get(), SpiderRenderer::new);
        event.registerEntityRenderer(DungeonsRegistry.DWARVEN_SPHERE.get(), SphereRenderer::new);
        event.registerEntityRenderer(DungeonsRegistry.DWARVEN_CENTURION.get(), CenturionRenderer::new);
    }

    static final class SpiderRenderer extends MobRenderer<DwarvenSpider, DwarvenSpiderModel<DwarvenSpider>> {
        SpiderRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, new DwarvenSpiderModel<>(ctx.bakeLayer(SPIDER)), 0.5f);
        }

        @Override
        public ResourceLocation getTextureLocation(DwarvenSpider entity) {
            return SPIDER_TEX;
        }
    }

    static final class SphereRenderer extends MobRenderer<DwarvenSphere, DwarvenSphereModel> {
        SphereRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, new DwarvenSphereModel(ctx.bakeLayer(SPHERE)), 0.5f);
        }

        @Override
        public ResourceLocation getTextureLocation(DwarvenSphere entity) {
            return SPHERE_TEX;
        }
    }

    static final class CenturionRenderer extends MobRenderer<DwarvenCenturion, DwarvenCenturionModel> {
        CenturionRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, new DwarvenCenturionModel(ctx.bakeLayer(CENTURION)), 1.2f);
        }

        @Override
        protected void scale(DwarvenCenturion entity, PoseStack pose, float partialTick) {
            pose.scale(1.75f, 1.75f, 1.75f);
        }

        @Override
        public ResourceLocation getTextureLocation(DwarvenCenturion entity) {
            return CENTURION_TEX;
        }
    }
}
