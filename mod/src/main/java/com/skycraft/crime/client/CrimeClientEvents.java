package com.skycraft.crime.client;

import com.skycraft.Skycraft;
import com.skycraft.crime.Bounty;
import com.skycraft.crime.CrimePackets;
import com.skycraft.network.SkyNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Forge-bus client events: asks the server about the container under the crosshair, "Stolen" tooltips. */
@Mod.EventBusSubscriber(modid = Skycraft.MODID, value = Dist.CLIENT)
public final class CrimeClientEvents {
    private static BlockPos lastQuery;
    private static int sinceQuery;

    private CrimeClientEvents() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null) return;
        sinceQuery++;
        HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult bhr) || hit.getType() != HitResult.Type.BLOCK) return;
        BlockPos pos = bhr.getBlockPos();
        BlockEntity be = mc.level.getBlockEntity(pos);
        if (!(be instanceof Container)) return;
        if (!pos.equals(lastQuery) || sinceQuery > 40) {
            lastQuery = pos.immutable();
            sinceQuery = 0;
            SkyNetwork.sendToServer(new CrimePackets.QueryContainer(lastQuery));
        }
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        if (Bounty.isStolen(event.getItemStack())) {
            event.getToolTip().add(Component.translatable("tooltip.skycraft.stolen").withStyle(ChatFormatting.RED));
        }
    }
}
