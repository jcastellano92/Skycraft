package com.skycraft.vitals;

import com.skycraft.combat.RespawnPoints;
import com.skycraft.combat.Sheathe;
import com.skycraft.combat.WeaponClass;
import com.skycraft.core.Notifier;
import com.skycraft.core.PlayerData;
import com.skycraft.core.SkyData;
import com.skycraft.core.Skill;
import com.skycraft.network.CorePackets.Action;
import com.skycraft.perk.Perks;
import com.skycraft.skills.Progression;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Server-side state for keybind actions sent by clients (power attacks, weapon blocking, racial power, climbing, dodge, unstuck). */
public final class ActionHandler {
    private static final Map<UUID, Long> POWER_ATTACK_ARMED = new HashMap<>();
    private static final Set<UUID> BLOCKING = new HashSet<>();

    private ActionHandler() {}

    public static void handle(ServerPlayer player, int action, int arg) {
        switch (action) {
            case Action.POWER_ATTACK -> POWER_ATTACK_ARMED.put(player.getUUID(), player.level().getGameTime() + 10);
            case Action.BLOCK_START -> {
                ItemStack main = player.getMainHandItem();
                ItemStack off = player.getOffhandItem();
                boolean dual = !main.isEmpty() && !off.isEmpty()
                        && WeaponClass.of(main).skill == Skill.ONE_HANDED
                        && WeaponClass.of(off).skill == Skill.ONE_HANDED;
                if (!dual) BLOCKING.add(player.getUUID());
            }
            case Action.BLOCK_STOP -> BLOCKING.remove(player.getUUID());
            case Action.USE_POWER -> RacePowers.use(player);
            case Action.MAKE_LEGENDARY -> {
                if (arg >= 0 && arg < Skill.VALUES.length) Progression.makeLegendary(player, Skill.VALUES[arg]);
            }
            case Action.SHEATHE_TOGGLE -> Sheathe.toggle(player);
            case Action.DODGE_ROLL -> handleDodgeRoll(player);
            case Action.CLIMB_TICK -> handleClimbTick(player);
            case Action.UNSTUCK -> handleUnstuck(player);
            default -> {
            }
        }
    }

    private static void handleDodgeRoll(ServerPlayer player) {
        if (!Perks.has(player, "athletics.dodge_roll")) return;
        if (Vitals.consumeStamina(player, 15f, false)) {
            player.invulnerableTime = 12; // ~0.6s i-frames
            player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8f, 1.4f);
            Progression.addSkillXp(player, Skill.ATHLETICS, 1.5f);
        }
    }

    private static void handleClimbTick(ServerPlayer player) {
        BlockPos pos = player.blockPosition();
        boolean nearWall = false;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            if (player.level().getBlockState(pos.relative(dir)).isSolid()) {
                nearWall = true;
                break;
            }
        }
        if (nearWall) {
            float cost = Perks.has(player, "athletics.climber") ? 0.8f : 1.2f;
            if (Vitals.consumeStamina(player, cost, false)) {
                Progression.addSkillXp(player, Skill.ATHLETICS, 0.15f);
                player.resetFallDistance();
            } else {
                Vitals.isExhausted(player);
            }
        }
    }

    private static void handleUnstuck(ServerPlayer player) {
        if (Vitals.inCombat(player)) {
            Notifier.message(player, Component.translatable("message.skycraft.unstuck_combat"));
            return;
        }
        PlayerData data = SkyData.get(player);
        long now = player.level().getGameTime();
        long next = data.module("unstuck").getLong("cooldown");
        if (now < next) {
            long sec = (next - now) / 20;
            Notifier.message(player, Component.translatable("message.skycraft.unstuck_cooldown", sec));
            return;
        }
        data.module("unstuck").putLong("cooldown", now + 6000); // 5 minutes
        data.markDirty();
        RespawnPoints.teleportToRestPoint(player);
    }

    /** Consumes the "power attack" flag the client sets while the power-attack key is held during a swing. */
    public static boolean consumePowerAttack(ServerPlayer player) {
        Long until = POWER_ATTACK_ARMED.remove(player.getUUID());
        return until != null && until >= player.level().getGameTime();
    }

    public static boolean isWeaponBlocking(ServerPlayer player) {
        return BLOCKING.contains(player.getUUID());
    }

    public static void forget(UUID player) {
        POWER_ATTACK_ARMED.remove(player);
        BLOCKING.remove(player);
    }
}
