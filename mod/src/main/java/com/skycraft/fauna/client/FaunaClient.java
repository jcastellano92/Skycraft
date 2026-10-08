package com.skycraft.fauna.client;

import com.skycraft.Skycraft;
import com.skycraft.fauna.FaunaEntities;
import com.skycraft.fauna.entity.BearEntity;
import com.skycraft.fauna.entity.DeerEntity;
import com.skycraft.fauna.entity.ElkEntity;
import com.skycraft.fauna.entity.HorkerEntity;
import com.skycraft.fauna.entity.InsectEntity;
import com.skycraft.fauna.entity.MammothEntity;
import com.skycraft.fauna.entity.MudcrabEntity;
import com.skycraft.fauna.entity.SabreCatEntity;
import com.skycraft.fauna.entity.SlaughterfishEntity;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Mod-bus client registration for the fauna module: model layers and entity renderers. */
@Mod.EventBusSubscriber(modid = Skycraft.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class FaunaClient {
    public static final ModelLayerLocation DEER = layer("deer");
    public static final ModelLayerLocation ELK = layer("elk");
    public static final ModelLayerLocation SABRE_CAT = layer("sabre_cat");
    public static final ModelLayerLocation HORKER = layer("horker");
    public static final ModelLayerLocation MUDCRAB = layer("mudcrab");
    public static final ModelLayerLocation MAMMOTH = layer("mammoth");
    public static final ModelLayerLocation BEAR = layer("bear");
    public static final ModelLayerLocation SLAUGHTERFISH = layer("slaughterfish");
    public static final ModelLayerLocation BUTTERFLY = layer("butterfly");
    public static final ModelLayerLocation DRAGONFLY = layer("dragonfly");
    public static final ModelLayerLocation TORCHBUG = layer("torchbug");

    private static final ResourceLocation DEER_TEX = tex("deer");
    private static final ResourceLocation ELK_TEX = tex("elk");
    private static final ResourceLocation[] SABRE_CAT_TEX = {tex("sabre_cat"), tex("sabre_cat_snowy")};
    private static final ResourceLocation HORKER_TEX = tex("horker");
    private static final ResourceLocation MUDCRAB_TEX = tex("mudcrab");
    private static final ResourceLocation MAMMOTH_TEX = tex("mammoth");
    private static final ResourceLocation[] BEAR_TEX = {tex("bear_brown"), tex("bear_cave"), tex("bear_snow")};
    private static final ResourceLocation SLAUGHTERFISH_TEX = tex("slaughterfish");
    private static final ResourceLocation[] BUTTERFLY_TEX = {tex("butterfly_monarch"), tex("butterfly_blue")};
    private static final ResourceLocation MOTH_TEX = tex("luna_moth");
    private static final ResourceLocation DRAGONFLY_TEX = tex("dragonfly");
    private static final ResourceLocation TORCHBUG_TEX = tex("torchbug");
    private static final ResourceLocation TORCHBUG_GLOW = tex("torchbug_glow");

    private FaunaClient() {}

    private static ModelLayerLocation layer(String name) {
        return new ModelLayerLocation(new ResourceLocation(Skycraft.MODID, name), "main");
    }

    private static ResourceLocation tex(String name) {
        return new ResourceLocation(Skycraft.MODID, "textures/entity/fauna/" + name + ".png");
    }

    private static ResourceLocation pick(ResourceLocation[] options, int variant) {
        return options[Math.floorMod(variant, options.length)];
    }

    @SubscribeEvent
    public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(DEER, () -> DeerModel.createLayer(false));
        event.registerLayerDefinition(ELK, () -> DeerModel.createLayer(true));
        event.registerLayerDefinition(SABRE_CAT, SabreCatModel::createLayer);
        event.registerLayerDefinition(HORKER, HorkerModel::createLayer);
        event.registerLayerDefinition(MUDCRAB, MudcrabModel::createLayer);
        event.registerLayerDefinition(MAMMOTH, MammothModel::createLayer);
        event.registerLayerDefinition(BEAR, BearModel::createLayer);
        event.registerLayerDefinition(SLAUGHTERFISH, SlaughterfishModel::createLayer);
        event.registerLayerDefinition(BUTTERFLY, InsectModels.Butterfly::createLayer);
        event.registerLayerDefinition(DRAGONFLY, InsectModels.Dragonfly::createLayer);
        event.registerLayerDefinition(TORCHBUG, InsectModels.Torchbug::createLayer);
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(FaunaEntities.DEER.get(), ctx -> new FaunaRenderer<DeerEntity, DeerModel<DeerEntity>>(
                ctx, new DeerModel<>(ctx.bakeLayer(DEER)), 0.6f, 0.8f, e -> DEER_TEX));
        event.registerEntityRenderer(FaunaEntities.ELK.get(), ctx -> new FaunaRenderer<ElkEntity, DeerModel<ElkEntity>>(
                ctx, new DeerModel<>(ctx.bakeLayer(ELK)), 0.6f, 1.05f, e -> ELK_TEX));
        event.registerEntityRenderer(FaunaEntities.SABRE_CAT.get(), ctx -> new FaunaRenderer<SabreCatEntity, SabreCatModel<SabreCatEntity>>(
                ctx, new SabreCatModel<>(ctx.bakeLayer(SABRE_CAT)), 0.6f, 1.0f, e -> pick(SABRE_CAT_TEX, e.getVariant())));
        event.registerEntityRenderer(FaunaEntities.HORKER.get(), ctx -> new FaunaRenderer<HorkerEntity, HorkerModel<HorkerEntity>>(
                ctx, new HorkerModel<>(ctx.bakeLayer(HORKER)), 0.8f, 1.1f, e -> HORKER_TEX));
        event.registerEntityRenderer(FaunaEntities.MUDCRAB.get(), ctx -> new FaunaRenderer<MudcrabEntity, MudcrabModel<MudcrabEntity>>(
                ctx, new MudcrabModel<>(ctx.bakeLayer(MUDCRAB)), 0.45f, 1.0f, e -> MUDCRAB_TEX));
        event.registerEntityRenderer(FaunaEntities.MAMMOTH.get(), ctx -> new FaunaRenderer<MammothEntity, MammothModel<MammothEntity>>(
                ctx, new MammothModel<>(ctx.bakeLayer(MAMMOTH)), 0.75f, 2.0f, e -> MAMMOTH_TEX));
        event.registerEntityRenderer(FaunaEntities.BEAR.get(), ctx -> new FaunaRenderer<BearEntity, BearModel<BearEntity>>(
                ctx, new BearModel<>(ctx.bakeLayer(BEAR)), 0.7f, 1.15f, e -> pick(BEAR_TEX, e.getVariant())));
        event.registerEntityRenderer(FaunaEntities.SLAUGHTERFISH.get(), ctx -> new FaunaRenderer.Fish<SlaughterfishEntity, SlaughterfishModel<SlaughterfishEntity>>(
                ctx, new SlaughterfishModel<>(ctx.bakeLayer(SLAUGHTERFISH)), 0.3f, 0.8f, e -> SLAUGHTERFISH_TEX));

        event.registerEntityRenderer(FaunaEntities.BUTTERFLY.get(), ctx -> new FaunaRenderer<InsectEntity, InsectModels.Butterfly<InsectEntity>>(
                ctx, new InsectModels.Butterfly<>(ctx.bakeLayer(BUTTERFLY)), 0.0f, 0.5f, e -> pick(BUTTERFLY_TEX, e.getVariant())));
        event.registerEntityRenderer(FaunaEntities.MOTH.get(), ctx -> new FaunaRenderer<InsectEntity, InsectModels.Butterfly<InsectEntity>>(
                ctx, new InsectModels.Butterfly<>(ctx.bakeLayer(BUTTERFLY)), 0.0f, 0.65f, e -> MOTH_TEX));
        event.registerEntityRenderer(FaunaEntities.DRAGONFLY.get(), ctx -> new FaunaRenderer<InsectEntity, InsectModels.Dragonfly<InsectEntity>>(
                ctx, new InsectModels.Dragonfly<>(ctx.bakeLayer(DRAGONFLY)), 0.0f, 0.55f, e -> DRAGONFLY_TEX));
        event.registerEntityRenderer(FaunaEntities.TORCHBUG.get(), ctx -> new FaunaRenderer<InsectEntity, InsectModels.Torchbug<InsectEntity>>(
                ctx, new InsectModels.Torchbug<>(ctx.bakeLayer(TORCHBUG)), 0.0f, 0.6f, e -> TORCHBUG_TEX).withGlow(TORCHBUG_GLOW));
    }
}
