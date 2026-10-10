package com.skycraft.fauna;

import com.skycraft.core.Notifier;
import com.skycraft.core.Skill;
import com.skycraft.network.SkyNetwork;
import com.skycraft.skills.Progression;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wild horse taming minigame:
 * 1. Player sneaks up undetected with saddle in hand; wild horse flees if standing/sprinting nearby.
 * 2. Player uses saddle on wild horse -> saddle thrown/equipped and player mounts.
 * 3. Horse begins bucking: balance minigame starts. Player must counter-steer (press A/D or steer keys)
 *    sent via packet. Higher breed difficulty makes balance windows shorter and penalties harsher.
 * 4. Success tames the horse and awards HUNTING and ATHLETICS skill XP!
 */
public final class WildHorseTaming {
    private static final Map<UUID, TamingSession> ACTIVE_SESSIONS = new ConcurrentHashMap<>();

    public static class TamingSession {
        public final UUID playerId;
        public final int horseId;
        public final HorseBreed breed;
        public int progress = 0; // 0 to 100
        public int required = 100;
        public int balanceDirection = 0; // -1 (left), 1 (right)
        public int ticksRemainingInPrompt = 0;
        public int sessionTicks = 0;

        public TamingSession(UUID playerId, int horseId, HorseBreed breed) {
            this.playerId = playerId;
            this.horseId = horseId;
            this.breed = breed;
        }
    }

    private WildHorseTaming() {}

    public static boolean tryStartTaming(ServerPlayer player, AbstractHorse horse, ItemStack stack) {
        if (horse.isTamed() || !horse.isAlive()) return false;
        if (!stack.is(Items.SADDLE)) return false;

        // Check if undetected / sneaking
        if (!player.isCrouching()) {
            Notifier.message(player, Component.translatable("fauna.skycraft.tame.must_sneak"));
            // Horse spooks and runs
            horse.setDeltaMovement(horse.getDeltaMovement().add(
                    (horse.getX() - player.getX()) * 0.4,
                    0.2,
                    (horse.getZ() - player.getZ()) * 0.4
            ));
            return true;
        }

        // Consume saddle if not creative
        if (!player.isCreative()) {
            stack.shrink(1);
        }

        if (horse instanceof Horse h) {
            h.equipSaddle(SoundSource.NEUTRAL);
        }

        // Mount player
        player.startRiding(horse);

        HorseBreed breed = HorseBreed.byId(horse.getPersistentData().getString("skycraft_breed"));
        TamingSession session = new TamingSession(player.getUUID(), horse.getId(), breed);
        ACTIVE_SESSIONS.put(player.getUUID(), session);

        Notifier.message(player, Component.translatable("fauna.skycraft.tame.started", breed.displayName()));
        promptNextBalance(player, session);
        return true;
    }

    public static void promptNextBalance(ServerPlayer player, TamingSession session) {
        session.balanceDirection = player.getRandom().nextBoolean() ? -1 : 1;
        int promptTicks = Math.max(15, 40 - session.breed.difficulty * 5);
        session.ticksRemainingInPrompt = promptTicks;

        // Send packet or notification to client
        SkyNetwork.sendToPlayer(player, new FaunaPackets.TamePrompt(
                session.balanceDirection,
                promptTicks,
                session.progress
        ));

        // Horse bucking effect
        if (player.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY() + 0.5, player.getZ(), 4, 0.2, 0.2, 0.2, 0.02);
            sl.playSound(null, player.blockPosition(), SoundEvents.HORSE_ANGRY, SoundSource.NEUTRAL, 1.0f, 1.0f);
        }
    }

    public static void onClientSteer(ServerPlayer player, int direction) {
        TamingSession session = ACTIVE_SESSIONS.get(player.getUUID());
        if (session == null) return;

        if (direction == session.balanceDirection) {
            // Correct counter-steer!
            session.progress += 25 - session.breed.difficulty * 2;
            player.level().playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5f, 1.2f);
            if (session.progress >= session.required) {
                completeTaming(player, session, true);
                return;
            }
        } else {
            // Failed balance steer
            session.progress = Math.max(0, session.progress - 20);
            player.level().playSound(null, player.blockPosition(), SoundEvents.HORSE_HURT, SoundSource.NEUTRAL, 0.6f, 1.0f);
        }

        promptNextBalance(player, session);
    }

    public static void tickServer(ServerPlayer player) {
        TamingSession session = ACTIVE_SESSIONS.get(player.getUUID());
        if (session == null) return;

        if (!(player.getVehicle() instanceof AbstractHorse horse) || !horse.isAlive()) {
            ACTIVE_SESSIONS.remove(player.getUUID());
            return;
        }

        session.sessionTicks++;
        session.ticksRemainingInPrompt--;

        if (session.ticksRemainingInPrompt <= 0) {
            // Player timed out answering bucking prompt: bucked off!
            completeTaming(player, session, false);
        }
    }

    private static void completeTaming(ServerPlayer player, TamingSession session, boolean success) {
        ACTIVE_SESSIONS.remove(player.getUUID());
        if (!(player.getVehicle() instanceof AbstractHorse horse)) return;

        if (success) {
            horse.tameWithName(player);
            horse.getPersistentData().putString("skycraft_owner", "player:" + player.getStringUUID());
            horse.getPersistentData().putBoolean(Livestock.PLAYER_OWNED, true);

            if (horse instanceof Horse h) {
                HorseManager.setPlayerHorse(player, h, session.breed);
            }

            // Award skills: HUNTING and ATHLETICS
            Progression.addSkillXp(player, Skill.HUNTING, 25.0f * session.breed.difficulty);
            Progression.addSkillXp(player, Skill.ATHLETICS, 20.0f * session.breed.difficulty);

            if (player.level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.HEART, horse.getX(), horse.getY() + 1.0, horse.getZ(), 8, 0.3, 0.3, 0.3, 0.05);
                sl.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.2f);
            }

            Notifier.message(player, Component.translatable("fauna.skycraft.tame.success", session.breed.displayName()));
            SkyNetwork.sendToPlayer(player, new FaunaPackets.TamePrompt(0, 0, 100));
        } else {
            // Bucked off!
            player.stopRiding();
            horse.setDeltaMovement(horse.getDeltaMovement().add(
                    (player.getRandom().nextDouble() - 0.5) * 0.5,
                    0.4,
                    (player.getRandom().nextDouble() - 0.5) * 0.5
            ));
            Notifier.message(player, Component.translatable("fauna.skycraft.tame.bucked_off"));
            SkyNetwork.sendToPlayer(player, new FaunaPackets.TamePrompt(0, 0, 0));
        }
    }
}

