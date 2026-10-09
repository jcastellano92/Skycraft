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
        event.getEntity().setMaxUpStep(1.0625f);
        original.invalidateCaps();
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            player.setMaxUpStep(1.0625f);
            fullSync(player);
            PlayerData data = SkyData.get(player);
            if (!data.module("core").getBoolean("spawn_placed")) {
                data.module("core").putBoolean("spawn_placed", true);
                data.markDirty();
                net.minecraft.server.level.ServerLevel level = player.serverLevel();
                if (level.dimension() == net.minecraft.world.level.Level.OVERWORLD) {
                    net.minecraft.core.BlockPos village = com.skycraft.quest.Locate.nearestVillage(level, player.blockPosition());
                    if (village != null) {
                        // Find a safe spot with solid ground beneath and 2 air blocks above, avoiding water/ocean
                        net.minecraft.core.BlockPos.MutableBlockPos safe = new net.minecraft.core.BlockPos.MutableBlockPos(village.getX(), 64, village.getZ());
                        boolean foundSafe = false;
                        for (int dx = -4; dx <= 4 && !foundSafe; dx += 2) {
                            for (int dz = -4; dz <= 4 && !foundSafe; dz += 2) {
                                int testX = village.getX() + dx;
                                int testZ = village.getZ() + dz;
                                int surfaceY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, testX, testZ);
                                safe.set(testX, surfaceY, testZ);
                                if (!level.getFluidState(safe).isEmpty() || !level.getFluidState(safe.below()).isEmpty()) continue;
                                if (!level.getBlockState(safe.below()).isSolid()) continue;
                                if (level.getBlockState(safe).isAir() && level.getBlockState(safe.above()).isAir()) {
                                    foundSafe = true;
                                }
                            }
                        }
                        if (foundSafe) {
                            player.teleportTo(level, safe.getX() + 0.5, safe.getY() + 0.1, safe.getZ() + 0.5, player.getYRot(), player.getXRot());
                        } else {
                            int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, village.getX(), village.getZ());
                            player.teleportTo(level, village.getX() + 0.5, Math.max(64, y) + 1.0, village.getZ() + 0.5, player.getYRot(), player.getXRot());
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            player.setMaxUpStep(1.0625f);
            fullSync(player);
        }
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
        if (player.isCrouching() && player.tickCount % 10 == 0) {
            int sneakSkill = SkyData.get(player).getSkill(Skill.SNEAK);
            int light = player.level().getMaxLocalRawBrightness(player.blockPosition());
            for (net.minecraft.world.entity.Mob mob : player.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, player.getBoundingBox().inflate(24), m -> m.getTarget() == player)) {
                boolean hasLos = mob.hasLineOfSight(player);
                double dist = mob.distanceTo(player);
                if (!hasLos && dist > 5.0) {
                    mob.setTarget(null);
                    mob.getNavigation().stop();
                } else if (hasLos && light < 7 && dist > Math.max(4.0, 14.0 - sneakSkill * 0.1)) {
                    mob.setTarget(null);
                    mob.getNavigation().stop();
                }
            }
        }
        // Auto-clear corpse marker on map and compass when player reaches within 6 blocks of their corpse
        if (player.tickCount % 20 == 0) {
            PlayerData data = SkyData.get(player);
            net.minecraft.nbt.ListTag discovered = com.skycraft.world.WorldData.discovered(data);
            boolean removedCorpse = false;
            String curDim = player.level().dimension().location().toString();
            for (int i = discovered.size() - 1; i >= 0; i--) {
                net.minecraft.nbt.CompoundTag loc = discovered.getCompound(i);
                String name = loc.getString("name");
                String id = loc.getString("id");
                if ("Your Corpse".equals(name) || id.startsWith("corpse|")) {
                    String dim = loc.getString("dim");
                    if (curDim.equals(dim)) {
                        double cx = loc.getInt("x");
                        double cy = loc.getInt("y");
                        double cz = loc.getInt("z");
                        if (player.distanceToSqr(cx + 0.5, cy + 0.5, cz + 0.5) <= 36.0) {
                            discovered.remove(i);
                            removedCorpse = true;
                        }
                    }
                }
            }
            if (removedCorpse) {
                data.markDirty();
                fullSync(player);
                Notifier.send(player, com.skycraft.network.NotifyKind.LOCATION_CLEARED,
                        net.minecraft.network.chat.Component.literal("Corpse Reached"),
                        net.minecraft.network.chat.Component.literal("Death marker cleared"));
            }
        }
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
            if (data.getRace() == null) {
                event.setCanceled(true);
                return;
            }
            if (player.isCrouching() && event.getEntity() instanceof net.minecraft.world.entity.Mob mob) {
                if (!mob.hasLineOfSight(player)) {
                    event.setCanceled(true);
                    return;
                }
                int sneakSkill = data.getSkill(Skill.SNEAK);
                int light = player.level().getMaxLocalRawBrightness(player.blockPosition());
                double dist = mob.distanceTo(player);
                double detectDist = Math.max(3.0, 16.0 - (sneakSkill * 0.12) - (light < 8 ? 6.0 : 0.0));
                if (dist > detectDist) {
                    event.setCanceled(true);
                }
            }
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
