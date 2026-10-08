package com.skycraft.arsenal.client;

import com.skycraft.Skycraft;
import com.skycraft.arsenal.ArsenalEntities;
import com.skycraft.arsenal.ArsenalItems;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.registries.RegistryObject;

/** Mod-bus client setup of the arsenal: projectile renderers and bow/crossbow model properties. */
@Mod.EventBusSubscriber(modid = Skycraft.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ArsenalClient {
    private static final ResourceLocation PULL = new ResourceLocation("pull");
    private static final ResourceLocation PULLING = new ResourceLocation("pulling");
    private static final ResourceLocation CHARGED = new ResourceLocation("charged");
    private static final ResourceLocation FIREWORK = new ResourceLocation("firework");

    private ArsenalClient() {}

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ArsenalEntities.SKY_ARROW.get(), SkyArrowRenderer::new);
        event.registerEntityRenderer(ArsenalEntities.STAFF_BOLT.get(), NoopRenderer::new);
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            for (RegistryObject<Item> bow : ArsenalItems.ARTIFACT_BOWS) {
                // same predicates as the vanilla bow
                ItemProperties.register(bow.get(), PULL, (stack, level, entity, seed) -> {
                    if (entity == null || entity.getUseItem() != stack) return 0f;
                    return (stack.getUseDuration() - entity.getUseItemRemainingTicks()) / 20f;
                });
                ItemProperties.register(bow.get(), PULLING, (stack, level, entity, seed) ->
                        entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1f : 0f);
            }
            for (RegistryObject<Item> crossbow : ArsenalItems.CROSSBOWS) {
                // same predicates as the vanilla crossbow
                Item item = crossbow.get();
                ItemProperties.register(item, PULL, (stack, level, entity, seed) -> {
                    if (entity == null) return 0f;
                    return CrossbowItem.isCharged(stack) ? 0f
                            : (stack.getUseDuration() - entity.getUseItemRemainingTicks()) / (float) CrossbowItem.getChargeDuration(stack);
                });
                ItemProperties.register(item, PULLING, (stack, level, entity, seed) ->
                        entity != null && entity.isUsingItem() && entity.getUseItem() == stack && !CrossbowItem.isCharged(stack) ? 1f : 0f);
                ItemProperties.register(item, CHARGED, (stack, level, entity, seed) ->
                        entity != null && CrossbowItem.isCharged(stack) ? 1f : 0f);
                ItemProperties.register(item, FIREWORK, (stack, level, entity, seed) ->
                        entity != null && CrossbowItem.isCharged(stack) && CrossbowItem.containsChargedProjectile(stack, Items.FIREWORK_ROCKET) ? 1f : 0f);
            }
        });
    }
}
