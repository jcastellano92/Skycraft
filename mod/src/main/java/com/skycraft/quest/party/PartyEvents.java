package com.skycraft.quest.party;

import com.skycraft.Skycraft;
import com.skycraft.magic.MagicDamage;
import com.skycraft.quest.QuestConfig;
import com.skycraft.quest.Quests;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * No friendly fire between party members (direct melee weapons, punching, arrows, or direct spells).
 * Indirect accidental environmental hazards (lightning traveling through water, standing in fire or lava)
 * remain possible.
 */
@Mod.EventBusSubscriber(modid = Skycraft.MODID)
public final class PartyEvents {
    private PartyEvents() {}

    /** Prevents swinging at, punching, or melee-attacking party members. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPlayerAttack(AttackEntityEvent event) {
        if (QuestConfig.FRIENDLY_FIRE.get()) return;
        if (event.getTarget() instanceof Player target && event.getEntity() instanceof ServerPlayer attacker) {
            if (attacker != target && target instanceof ServerPlayer spTarget) {
                if (Parties.sameParty(attacker.server, attacker, spTarget)) {
                    event.setCanceled(true);
                }
            }
        }
    }

    /** Checks whether damage is an accidental indirect environmental effect. */
    private static boolean isIndirectEnvironmental(DamageSource source, ServerPlayer victim) {
        if (source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE) || source.is(DamageTypes.LAVA)
                || source.is(DamageTypes.HOT_FLOOR)) {
            return true;
        }
        if (source.is(DamageTypes.LIGHTNING_BOLT)) {
            return true;
        }
        // Lightning / shock traveling through water:
        if (victim.isInWaterRainOrBubble() && source.is(MagicDamage.SHOCK)) {
            return true;
        }
        return false;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer target) || QuestConfig.FRIENDLY_FIRE.get()) return;
        ServerPlayer attacker = Quests.asPlayer(target.server, event.getSource().getEntity());
        if (attacker == null || attacker == target) return;
        if (Parties.sameParty(target.server, attacker, target)) {
            if (isIndirectEnvironmental(event.getSource(), target)) return;
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer target) || QuestConfig.FRIENDLY_FIRE.get()) return;
        ServerPlayer attacker = Quests.asPlayer(target.server, event.getSource().getEntity());
        if (attacker == null || attacker == target) return;
        if (Parties.sameParty(target.server, attacker, target)) {
            if (isIndirectEnvironmental(event.getSource(), target)) return;
            event.setCanceled(true);
        }
    }
}

