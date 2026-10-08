package com.skycraft.vitals;

import com.skycraft.SkyConfig;
import com.skycraft.Skycraft;
import com.skycraft.core.Buffs;
import com.skycraft.core.PlayerData;
import com.skycraft.core.SkyData;
import com.skycraft.combat.ArmorClass;
import com.skycraft.network.CorePackets;
import com.skycraft.network.SkyNetwork;
import com.skycraft.perk.Perks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * Health, Magicka and Stamina: Skyrim-style pools, regeneration rates, sprint drain, and attribute syncing.
 * Other modules spend resources through {@link #consumeMagicka} / {@link #consumeStamina}.
 */
@Mod.EventBusSubscriber(modid = Skycraft.MODID)
public final class Vitals {
    private static final UUID HEALTH_MOD = UUID.fromString("6f1d3b4c-2a8e-4f7d-9b1a-5c3e2d1f0a01");
    private static final UUID HEAVY_SPEED_MOD = UUID.fromString("6f1d3b4c-2a8e-4f7d-9b1a-5c3e2d1f0a02");
    /** Ticks after which a player counts as out of combat. */
    public static final int COMBAT_TIMEOUT = 160;

    private Vitals() {}

    // ------------------------------------------------------------------ public API

    public static boolean inCombat(Player player) {
        return player.level().getGameTime() - SkyData.get(player).getLastCombatTick() < COMBAT_TIMEOUT;
    }

    public static void markInCombat(Player player) {
        SkyData.get(player).setLastCombatTick(player.level().getGameTime());
    }

    /** Spends magicka if the player has enough. Creative players cast for free. */
    public static boolean consumeMagicka(Player player, float amount) {
        if (player.isCreative()) return true;
        PlayerData data = SkyData.get(player);
        if (data.getMagicka() < amount) return false;
        data.setMagicka(data.getMagicka() - amount);
        data.setLastMagickaUseTick(player.level().getGameTime());
        return true;
    }

    /**
     * Spends stamina. If {@code partial} is true the action happens even with too little stamina (draining to 0),
     * and the return value says whether there was enough for full effect.
     */
    public static boolean consumeStamina(Player player, float amount, boolean partial) {
        if (player.isCreative()) return true;
        PlayerData data = SkyData.get(player);
        boolean enough = data.getStamina() >= amount;
        if (!enough && !partial) return false;
        data.setStamina(data.getStamina() - amount);
        data.setLastStaminaUseTick(player.level().getGameTime());
        return enough;
    }

    /** Re-applies max-health and armor-weight attribute modifiers after level-ups, perks or race changes. */
    public static void refreshAttributes(ServerPlayer player) {
        PlayerData data = SkyData.get(player);
        AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            double bonus = data.maxHealth() / 5.0 - 20.0;
            AttributeModifier existing = maxHealth.getModifier(HEALTH_MOD);
            if (existing == null || existing.getAmount() != bonus) {
                if (existing != null) maxHealth.removeModifier(HEALTH_MOD);
                if (bonus != 0) maxHealth.addPermanentModifier(new AttributeModifier(HEALTH_MOD, "Skycraft health", bonus, AttributeModifier.Operation.ADDITION));
                if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
            }
        }
        updateArmorWeight(player);
        data.setMagicka(Math.min(data.getMagicka(), data.maxMagicka()));
        data.setStamina(Math.min(data.getStamina(), data.maxStamina()));
        data.markDirty();
        data.markVitalsDirty();
    }

    /** Heavy armor slows you down (2.5% per piece) unless you have the Conditioning perk. */
    private static void updateArmorWeight(ServerPlayer player) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        int heavy = ArmorClass.countHeavy(player);
        double penalty = Perks.has(player, "heavy_armor.conditioning") ? 0 : -0.025 * heavy;
        AttributeModifier existing = speed.getModifier(HEAVY_SPEED_MOD);
        if (existing != null && existing.getAmount() == penalty) return;
        if (existing != null) speed.removeModifier(HEAVY_SPEED_MOD);
        if (penalty != 0) speed.addTransientModifier(new AttributeModifier(HEAVY_SPEED_MOD, "Skycraft heavy armor", penalty, AttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    // ------------------------------------------------------------------ ticking

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        PlayerData data = SkyData.get(player);
        long now = player.level().getGameTime();
        boolean combat = inCombat(player);

        // Sprinting drains stamina.
        if (player.isSprinting() && !player.isCreative() && !player.isSpectator()) {
            float drain = (float) (SkyConfig.SPRINT_STAMINA_PER_SECOND.get() / 20.0);
            if (Perks.has(player, "light_armor.unhindered") && ArmorClass.countLight(player) >= 4) drain *= 0.85f;
            data.setStamina(data.getStamina() - drain);
            data.setLastStaminaUseTick(now);
            if (data.getStamina() <= 0) player.setSprinting(false);
        }

        if (now % 5 == 0) {
            regen(player, data, combat, now);
            updateArmorWeight(player);
        }

        // Skyrim hunger: food never drains. While exhausted we hold food at 6 so the client cannot sprint.
        if (SkyConfig.SKYRIM_HUNGER.get()) {
            int target = data.getStamina() < 1f || isExhausted(player) ? 6 : 17;
            if (player.getFoodData().getFoodLevel() != target) player.getFoodData().setFoodLevel(target);
            player.getFoodData().setSaturation(0f);
            player.getFoodData().setExhaustion(0f);
        }

        if (now % 20 == 0) {
            data.module("vitals").putBoolean("detected", isDetected(player));
        }

        sync(player, data, now);
    }

    private static final java.util.Set<UUID> EXHAUSTED = new java.util.HashSet<>();

    /** Hysteresis: once out of stamina, sprinting is blocked until 15 stamina has regenerated. */
    public static boolean isExhausted(Player player) {
        PlayerData data = SkyData.get(player);
        if (data.getStamina() < 1f) EXHAUSTED.add(player.getUUID());
        else if (data.getStamina() >= 15f) EXHAUSTED.remove(player.getUUID());
        return EXHAUSTED.contains(player.getUUID());
    }

    private static void regen(ServerPlayer player, PlayerData data, boolean combat, long now) {
        float seconds = 0.25f;
        // Magicka
        float magPct = (float) (double) SkyConfig.MAGICKA_REGEN_PERCENT.get() / 100f;
        magPct *= 1f + 0.25f * Perks.rank(player, "restoration.recovery");
        if (Buffs.active(player, "highborn")) magPct *= 25f;
        if (combat) magPct *= 0.33f;
        if (now - data.getLastMagickaUseTick() > 20) {
            data.setMagicka(data.getMagicka() + data.maxMagicka() * magPct * seconds);
        }
        // Stamina
        float stPct = (float) (double) SkyConfig.STAMINA_REGEN_PERCENT.get() / 100f;
        if (Perks.has(player, "light_armor.wind_walker") && ArmorClass.countLight(player) >= 4) stPct *= 1.5f;
        if (Buffs.active(player, "adrenaline_rush")) stPct *= 10f;
        if (combat) stPct *= 0.5f;
        if (now - data.getLastStaminaUseTick() > 20 && !player.isSprinting()) {
            data.setStamina(data.getStamina() + data.maxStamina() * stPct * seconds);
        }
        // Health
        if (SkyConfig.SKYRIM_REGEN.get() && player.isAlive() && player.getHealth() < player.getMaxHealth()) {
            float hpPct = (float) (double) SkyConfig.HEALTH_REGEN_PERCENT.get() / 100f;
            if (Buffs.active(player, "histskin")) hpPct *= 10f;
            if (combat) hpPct *= 0.1f;
            player.heal(player.getMaxHealth() * hpPct * seconds);
        }
        if (SkyConfig.SKYRIM_REGEN.get() && player.server.getGameRules().getBoolean(GameRules.RULE_NATURAL_REGENERATION)) {
            player.server.getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION).set(false, player.server);
        }
    }

    /** A player is "detected" when a hostile mob nearby is targeting them (drives the sneak eye on the HUD). */
    public static boolean isDetected(ServerPlayer player) {
        for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(24),
                m -> m instanceof Enemy && m.isAlive())) {
            if (mob.getTarget() == player) return true;
        }
        return false;
    }

    private static void sync(ServerPlayer player, PlayerData data, long now) {
        if (data.consumeDirty()) {
            SkyNetwork.sendToPlayer(player, new CorePackets.SyncData(data.save()));
            data.consumeVitalsDirty();
            sendVitals(player, data);
        } else if (now % 2 == 0 && data.consumeVitalsDirty() || now % 20 == 0) {
            sendVitals(player, data);
        }
    }

    private static void sendVitals(ServerPlayer player, PlayerData data) {
        byte flags = 0;
        if (data.module("vitals").getBoolean("detected")) flags |= CorePackets.SyncVitals.DETECTED;
        if (inCombat(player)) flags |= CorePackets.SyncVitals.IN_COMBAT;
        if (isExhausted(player)) flags |= CorePackets.SyncVitals.EXHAUSTED;
        SkyNetwork.sendToPlayer(player, new CorePackets.SyncVitals(data.getMagicka(), data.getStamina(), flags));
    }

    // ------------------------------------------------------------------ jumping & food

    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && SkyConfig.JUMP_STAMINA.get() > 0) {
            consumeStamina(player, (float) (double) SkyConfig.JUMP_STAMINA.get(), true);
        }
    }

    /** With Skyrim hunger, eating heals and restores stamina instead of filling a hunger bar. */
    @SubscribeEvent
    public static void onEat(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !SkyConfig.SKYRIM_HUNGER.get()) return;
        var food = event.getItem().getFoodProperties(player);
        if (food == null) return;
        player.heal(food.getNutrition() * 0.75f);
        PlayerData data = SkyData.get(player);
        data.setStamina(data.getStamina() + food.getNutrition() * 4f + food.getSaturationModifier() * 10f);
    }
}
