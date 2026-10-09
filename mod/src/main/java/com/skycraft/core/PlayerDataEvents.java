package com.skycraft.core;

import com.skycraft.SkyConfig;
import com.skycraft.Skycraft;
import com.skycraft.network.CorePackets;
import com.skycraft.network.SkyNetwork;
import com.skycraft.skills.Progression;
import com.skycraft.vitals.ActionHandler;
import com.skycraft.vitals.Vitals;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Capability lifecycle (attach, death copy, sync on login/respawn/dimension change) and coin pickup. */
@Mod.EventBusSubscriber(modid = Skycraft.MODID)
public final class PlayerDataEvents {
    private PlayerDataEvents() {}

    @SubscribeEvent
    public static void attach(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            SkyData.Provider provider = new SkyData.Provider();
            event.addCapability(SkyData.KEY, provider);
            event.addListener(provider::invalidate);
        }
    }

    @SubscribeEvent
    public static void clone(PlayerEvent.Clone event) {
        Player original = event.getOriginal();
        original.reviveCaps();
        PlayerData old = SkyData.get(original);
        PlayerData now = SkyData.get(event.getEntity());
        now.copyFrom(old);
        if (event.isWasDeath()) {
            now.setMagicka(now.maxMagicka());
            now.setStamina(now.maxStamina());
        }
        original.invalidateCaps();
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            fullSync(player);
            PlayerData data = SkyData.get(player);
            if (!data.module("core").getBoolean("spawn_placed")) {
                data.module("core").putBoolean("spawn_placed", true);
                data.markDirty();
                net.minecraft.server.level.ServerLevel level = player.serverLevel();
                if (level.dimension() == net.minecraft.world.level.Level.OVERWORLD) {
                    net.minecraft.core.BlockPos village = com.skycraft.quest.Locate.nearestVillage(level, player.blockPosition());
                    if (village != null) {
                        int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, village.getX(), village.getZ());
                        player.teleportTo(level, village.getX() + 0.5, Math.max(64, y), village.getZ() + 0.5, player.getYRot(), player.getXRot());
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) fullSync(player);
    }

    @SubscribeEvent
    public static void changeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) fullSync(player);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        ActionHandler.forget(event.getEntity().getUUID());
        Progression.forget(event.getEntity().getUUID());
    }

    public static void fullSync(ServerPlayer player) {
        PlayerData data = SkyData.get(player);
        Vitals.refreshAttributes(player);
        SkyNetwork.sendToPlayer(player, new CorePackets.SyncData(data.save()));
    }

    /** Until a race is chosen, periodically re-open the race menu (the first time shortly after joining). */
    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        if (!SkyConfig.PROMPT_RACE.get()) return;
        PlayerData data = SkyData.get(player);
        if (data.getRace() == null && !com.skycraft.crime.Jail.isJailed(player) && player.tickCount % 600 == 60) {
            SkyNetwork.sendToPlayer(player, new CorePackets.OpenScreen(CorePackets.OpenScreen.RACE));
        }
    }

    /** Invulnerable while choosing race / character */
    @SubscribeEvent
    public static void onHurt(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
        if (event.getEntity() instanceof Player player) {
            PlayerData data = SkyData.get(player);
            if (data.getRace() == null) event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onTarget(net.minecraftforge.event.entity.living.LivingChangeTargetEvent event) {
        if (event.getNewTarget() instanceof Player player) {
            PlayerData data = SkyData.get(player);
            if (data.getRace() == null) event.setCanceled(true);
        }
    }

    /** Septims go straight into the wallet. */
    @SubscribeEvent
    public static void pickup(EntityItemPickupEvent event) {
        ItemStack stack = event.getItem().getItem();
        long value = Currency.valueOf(stack);
        if (value > 0 && !event.getEntity().level().isClientSide) {
            Currency.give(event.getEntity(), value);
            event.getItem().discard();
            event.setCanceled(true);
        }
    }
}
