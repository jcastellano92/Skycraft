package com.skycraft.crafting.arcane.client;

import com.skycraft.Skycraft;
import com.skycraft.crafting.arcane.ArcaneRegistry;
import com.skycraft.crafting.arcane.item.SoulGemItem;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.registries.RegistryObject;

/** Mod-bus client setup: the {@code skycraft:filled} item property that switches soul gems to their glowing model. */
@Mod.EventBusSubscriber(modid = Skycraft.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ArcaneClientSetup {
    public static final ResourceLocation FILLED = new ResourceLocation(Skycraft.MODID, "filled");

    private ArcaneClientSetup() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            for (RegistryObject<Item> gem : ArcaneRegistry.soulGems()) {
                ItemProperties.register(gem.get(), FILLED, (stack, level, entity, seed) -> SoulGemItem.getSoul(stack) > 0 ? 1f : 0f);
            }
        });
    }
}
