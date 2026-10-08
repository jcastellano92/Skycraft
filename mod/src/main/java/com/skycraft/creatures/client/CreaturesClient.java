package com.skycraft.creatures.client;

import com.skycraft.Skycraft;
import com.skycraft.core.Holds;
import com.skycraft.creatures.ModEntities;
import com.skycraft.creatures.entity.BanditChiefEntity;
import com.skycraft.creatures.entity.BanditEntity;
import com.skycraft.creatures.entity.DraugrDeathlordEntity;
import com.skycraft.creatures.entity.DraugrEntity;
import com.skycraft.creatures.entity.GiantEntity;
import com.skycraft.creatures.entity.GuardEntity;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Mod-bus client registration for the creatures module: model layers and entity renderers. */
@Mod.EventBusSubscriber(modid = Skycraft.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CreaturesClient {
    public static final ModelLayerLocation HUMANOID = layer("humanoid");
    public static final ModelLayerLocation SKEEVER = layer("skeever");
    public static final ModelLayerLocation TROLL = layer("troll");
    public static final ModelLayerLocation DRAGON = layer("dragon");

    private static final ResourceLocation[] BANDIT = textures("bandit/bandit_", BanditEntity.SKINS);
    private static final ResourceLocation BANDIT_CHIEF = tex("bandit/bandit_chief");
    private static final ResourceLocation[] DRAUGR = textures("draugr/draugr_", DraugrEntity.SKINS);
    private static final ResourceLocation DEATHLORD = tex("draugr/draugr_deathlord");
    private static final ResourceLocation DRAUGR_EYES = tex("draugr/draugr_eyes");
    private static final ResourceLocation GIANT = tex("giant");
    private static final ResourceLocation[] GUARD = new ResourceLocation[Holds.NAMES.length];

    static {
        for (int i = 0; i < GUARD.length; i++) GUARD[i] = tex("guard/guard_" + Holds.NAMES[i]);
    }

    private CreaturesClient() {}

    private static ModelLayerLocation layer(String name) {
        return new ModelLayerLocation(new ResourceLocation(Skycraft.MODID, name), "main");
    }

    private static ResourceLocation tex(String path) {
        return new ResourceLocation(Skycraft.MODID, "textures/entity/" + path + ".png");
    }

    private static ResourceLocation[] textures(String prefix, int count) {
        ResourceLocation[] out = new ResourceLocation[count];
        for (int i = 0; i < count; i++) out[i] = tex(prefix + i);
        return out;
    }

    @SubscribeEvent
    public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(HUMANOID, SkyHumanoidModel::createLayer);
        event.registerLayerDefinition(SKEEVER, SkeeverModel::createLayer);
        event.registerLayerDefinition(TROLL, TrollModel::createLayer);
        event.registerLayerDefinition(DRAGON, DragonModel::createLayer);
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.BANDIT.get(),
                ctx -> new SkyHumanoidRenderer<BanditEntity>(ctx, HUMANOID, b -> BANDIT[Math.floorMod(b.getSkin(), BANDIT.length)], 0.5f, 1.0f, true));
        event.registerEntityRenderer(ModEntities.BANDIT_CHIEF.get(),
                ctx -> new SkyHumanoidRenderer<BanditChiefEntity>(ctx, HUMANOID, b -> BANDIT_CHIEF, 0.55f, 1.05f, true));
        event.registerEntityRenderer(ModEntities.DRAUGR.get(),
                ctx -> new SkyHumanoidRenderer<DraugrEntity>(ctx, HUMANOID, d -> DRAUGR[Math.floorMod(d.getSkin(), DRAUGR.length)], 0.5f, 1.0f, true)
                        .withEyes(DRAUGR_EYES));
        event.registerEntityRenderer(ModEntities.DRAUGR_DEATHLORD.get(),
                ctx -> new SkyHumanoidRenderer<DraugrDeathlordEntity>(ctx, HUMANOID, d -> DEATHLORD, 0.6f, 1.08f, true)
                        .withEyes(DRAUGR_EYES));
        event.registerEntityRenderer(ModEntities.GUARD.get(),
                ctx -> new SkyHumanoidRenderer<GuardEntity>(ctx, HUMANOID, g -> GUARD[g.getHold()], 0.5f, 1.0f, true));
        event.registerEntityRenderer(ModEntities.GIANT.get(),
                ctx -> new SkyHumanoidRenderer<GiantEntity>(ctx, HUMANOID, g -> GIANT, 1.4f, 2.5f, false));
        event.registerEntityRenderer(ModEntities.SKEEVER.get(), SkeeverRenderer::new);
        event.registerEntityRenderer(ModEntities.TROLL.get(), TrollRenderer::new);
        event.registerEntityRenderer(ModEntities.DRAGON.get(), DragonRenderer::new);
        event.registerEntityRenderer(ModEntities.CORPSE.get(), CorpseRenderer::new);
    }
}
