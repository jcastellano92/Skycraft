package com.skycraft.fauna;

import com.skycraft.core.Notifier;
import com.skycraft.core.SkyData;
import com.skycraft.quest.Faction;
import com.skycraft.quest.Factions;
import com.skycraft.quest.party.Party;
import com.skycraft.quest.party.PartyManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * Manages player-owned horses, calling them with H, respawning on death after cooldown,
 * ownership enforcement (only owner can steer/ride), and passenger riding for same-faction/party members.
 */
public final class HorseManager {
    public static final String MODULE = "fauna";
    public static final String HORSE_UUID = "owned_horse_uuid";
    public static final String HORSE_BREED = "owned_horse_breed";
    public static final String HORSE_RESPAWN_COOLDOWN = "horse_respawn_ready";
    public static final long DEATH_COOLDOWN_TICKS = 1200; // 1 minute cooldown

    private HorseManager() {}

    public static void setPlayerHorse(ServerPlayer player, Horse horse, HorseBreed breed) {
        CompoundTag tag = SkyData.get(player).module(MODULE);
        tag.putUUID(HORSE_UUID, horse.getUUID());
        tag.putString(HORSE_BREED, breed.id);
        tag.remove(HORSE_RESPAWN_COOLDOWN);
    }

    public static boolean hasOwnedHorse(Player player) {
        CompoundTag tag = SkyData.get(player).module(MODULE);
        return tag.hasUUID(HORSE_UUID);
    }

    /**
     * Called when player presses H (Call Horse).
     */
    public static void callHorse(ServerPlayer player) {
        CompoundTag tag = SkyData.get(player).module(MODULE);
        if (!tag.hasUUID(HORSE_UUID)) {
            Notifier.message(player, Component.translatable("fauna.skycraft.horse.none"));
            return;
        }

        long now = player.level().getGameTime();
        if (tag.contains(HORSE_RESPAWN_COOLDOWN)) {
            long ready = tag.getLong(HORSE_RESPAWN_COOLDOWN);
            if (now < ready) {
                int secs = (int) Math.ceil((ready - now) / 20.0);
                Notifier.message(player, Component.translatable("fauna.skycraft.horse.cooldown", secs));
                return;
            }
        }

        UUID horseId = tag.getUUID(HORSE_UUID);
        ServerLevel level = player.serverLevel();
        Entity existing = level.getEntity(horseId);

        // Find safe spawn / teleport position near player
        BlockPos targetPos = player.blockPosition().relative(player.getDirection().getOpposite(), 3);
        if (!level.getBlockState(targetPos).isAir()) targetPos = player.blockPosition();

        if (existing instanceof Horse horse && existing.isAlive()) {
            horse.teleportTo(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5);
            level.playSound(null, player.blockPosition(), SoundEvents.HORSE_GALLOP, SoundSource.PLAYERS, 1.0f, 1.0f);
            Notifier.message(player, Component.translatable("fauna.skycraft.horse.called"));
        } else {
            // Respawn the horse with stored breed
            HorseBreed breed = HorseBreed.byId(tag.getString(HORSE_BREED));
            Horse horse = EntityType.HORSE.create(level);
            if (horse != null) {
                horse.moveTo(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5, player.getYRot(), 0.0f);
                breed.apply(horse);
                horse.tameWithName(player);
                horse.equipSaddle(SoundSource.NEUTRAL);
                horse.getPersistentData().putString("skycraft_owner", "player:" + player.getStringUUID());
                horse.getPersistentData().putBoolean(Livestock.PLAYER_OWNED, true);

                level.addFreshEntity(horse);
                tag.putUUID(HORSE_UUID, horse.getUUID());
                tag.remove(HORSE_RESPAWN_COOLDOWN);

                level.playSound(null, player.blockPosition(), SoundEvents.HORSE_AMBIENT, SoundSource.PLAYERS, 1.0f, 1.0f);
                Notifier.message(player, Component.translatable("fauna.skycraft.horse.called"));
            }
        }
    }

    public static void onHorseDeath(AbstractHorse horse) {
        String ownerId = horse.getPersistentData().getString("skycraft_owner");
        if (ownerId.startsWith("player:")) {
            try {
                UUID pUuid = UUID.fromString(ownerId.substring("player:".length()));
                if (horse.level().getServer() != null) {
                    ServerPlayer owner = horse.level().getServer().getPlayerList().getPlayer(pUuid);
                    if (owner != null) {
                        CompoundTag tag = SkyData.get(owner).module(MODULE);
                        tag.putLong(HORSE_RESPAWN_COOLDOWN, owner.level().getGameTime() + DEATH_COOLDOWN_TICKS);
                        Notifier.message(owner, Component.translatable("fauna.skycraft.horse.died"));
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    /**
     * Checks if a player can mount or ride this horse.
     * Owner can always ride and steer.
     * Non-owner can only ride as a passenger (behind owner) if in same faction or party.
     * Nobody can mount an owned horse without permission.
     */
    public static boolean canMount(Player player, AbstractHorse horse) {
        if (player.isCreative()) return true;

        String ownerStr = horse.getPersistentData().getString("skycraft_owner");
        if (ownerStr.isEmpty()) {
            // Unowned horse (e.g. wild or vanilla)
            return true;
        }

        if (ownerStr.equals("player:" + player.getStringUUID())) {
            return true; // The owner
        }

        // Check if there is already an owner riding
        Entity firstPassenger = horse.getFirstPassenger();
        if (firstPassenger instanceof Player owner) {
            if (isSameFactionOrParty(player, owner)) {
                // Can ride behind them as a secondary passenger
                return true;
            }
        }

        return false;
    }

    public static boolean isSameFactionOrParty(Player a, Player b) {
        // Party check
        if (a.getServer() != null) {
            PartyManager pm = PartyManager.get(a.getServer());
            Party pa = pm.partyOf(a.getUUID());
            if (pa != null && pa.has(b.getUUID())) return true;
        }

        // Faction check
        var dataA = SkyData.get(a);
        var dataB = SkyData.get(b);
        for (Faction f : Faction.VALUES) {
            if (Factions.isMember(dataA, f) && Factions.isMember(dataB, f)) {
                return true;
            }
        }

        return false;
    }
}

