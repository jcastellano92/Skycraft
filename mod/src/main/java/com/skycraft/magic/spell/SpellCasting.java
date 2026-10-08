package com.skycraft.magic.spell;

import com.skycraft.combat.CombatHandler;
import com.skycraft.core.Notifier;
import com.skycraft.core.PlayerData;
import com.skycraft.core.SkyData;
import com.skycraft.core.Skill;
import com.skycraft.magic.MagicData;
import com.skycraft.magic.MagicDamage;
import com.skycraft.magic.MagicFx;
import com.skycraft.magic.MagicRegistry;
import com.skycraft.perk.Perks;
import com.skycraft.registry.ModEffects;
import com.skycraft.skills.Progression;
import com.skycraft.vitals.Vitals;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side casting state. Clients send "cast key down/up" ({@link com.skycraft.magic.MagicPackets.Cast});
 * fire-and-forget spells are released on key down (master spells after a 2 second charge while the key is held),
 * concentration spells run every tick until key up or until magicka runs out.
 */
public final class SpellCasting {
    /** Global cooldown between fire-and-forget casts (Skyrim's cast animation). */
    private static final int CAST_COOLDOWN = 10;

    private static final Map<UUID, Active> ACTIVE = new HashMap<>();
    private static final Map<UUID, Long> NEXT_CAST = new HashMap<>();
    private static final Map<UUID, Long> LAST_FAIL = new HashMap<>();
    private static final Map<UUID, Float> WARD = new HashMap<>();

    private SpellCasting() {}

    private static final class Active {
        final Spell spell;
        int ticks;
        boolean hadEffect;

        Active(Spell spell) {
            this.spell = spell;
        }
    }

    private static boolean hasMagicka(ServerPlayer p, float amount) {
        return p.isCreative() || SkyData.get(p).getMagicka() >= amount;
    }

    private static void fail(ServerPlayer p) {
        long now = p.level().getGameTime();
        Long last = LAST_FAIL.get(p.getUUID());
        if (last != null && now - last < 20) return;
        LAST_FAIL.put(p.getUUID(), now);
        Notifier.message(p, Component.translatable("message.skycraft.not_enough_magicka"));
        p.playNotifySound(SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.4f, 1.8f);
    }

    /** Cast key pressed. */
    public static void start(ServerPlayer p) {
        if (!p.isAlive() || p.isSpectator() || ACTIVE.containsKey(p.getUUID())) return;
        if (p.hasEffect(ModEffects.PARALYSIS.get())) return;
        Spell spell = Spells.byId(MagicData.selectedSpell(p));
        if (spell == null || !MagicData.knows(p, spell.id) && !p.isCreative()) {
            Notifier.message(p, Component.translatable("message.skycraft.no_spell", Component.keybind("key.skycraft.magic_menu")));
            return;
        }
        if (spell.hostile && p.hasEffect(MagicRegistry.ETHEREAL.get())) {
            Notifier.message(p, Component.translatable("message.skycraft.ethereal_no_attack"));
            return;
        }
        float cost = SpellMath.cost(p, spell);
        if (spell.isConcentration()) {
            if (!hasMagicka(p, cost / 5f)) {
                fail(p);
                return;
            }
            ACTIVE.put(p.getUUID(), new Active(spell));
            return;
        }
        long now = p.level().getGameTime();
        if (NEXT_CAST.getOrDefault(p.getUUID(), 0L) > now) return;
        if (!hasMagicka(p, cost)) {
            fail(p);
            return;
        }
        if (spell.chargeTicks > 0) {
            ACTIVE.put(p.getUUID(), new Active(spell));
            MagicFx.sound(p, SoundEvents.BEACON_POWER_SELECT, 0.8f, 0.6f);
            return;
        }
        release(p, spell, cost);
    }

    /** Cast key released. */
    public static void stop(ServerPlayer p) {
        Active a = ACTIVE.remove(p.getUUID());
        WARD.remove(p.getUUID());
        if (a != null && a.spell.isConcentration() && a.ticks > 4 && a.spell.element == Element.FIRE) {
            MagicFx.sound(p, SoundEvents.FIRE_EXTINGUISH, 0.3f, 1.6f);
        }
    }

    private static void release(ServerPlayer p, Spell spell, float cost) {
        Spell.Result result = spell.action.cast(p, spell, 0);
        if (result == Spell.Result.FAILED) return;
        Vitals.consumeMagicka(p, cost);
        NEXT_CAST.put(p.getUUID(), p.level().getGameTime() + CAST_COOLDOWN);
        if (result == Spell.Result.EFFECT) SpellEffects.xp(p, spell);
        p.swing(InteractionHand.MAIN_HAND, true);
        SkyData.get(p).addStat("spells_cast", 1);
    }

    /** Called every tick for every server player. */
    public static void tick(ServerPlayer p) {
        Active a = ACTIVE.get(p.getUUID());
        if (a == null) return;
        if (!p.isAlive() || p.isSpectator() || p.hasEffect(ModEffects.PARALYSIS.get())) {
            stop(p);
            return;
        }
        Spell spell = a.spell;
        if (spell.isConcentration()) {
            float perTick = SpellMath.cost(p, spell) / 20f;
            if (!Vitals.consumeMagicka(p, perTick)) {
                stop(p);
                fail(p);
                return;
            }
            Spell.Result result = spell.action.cast(p, spell, a.ticks);
            if (result == Spell.Result.FAILED) {
                stop(p);
                return;
            }
            if (result == Spell.Result.EFFECT) a.hadEffect = true;
            if (a.ticks % 20 == 19) {
                if (a.hadEffect) Progression.addSkillXp(p, spell.school.skill, spell.cost);
                a.hadEffect = false;
            }
            if (spell.hostile && a.ticks % 20 == 0) Vitals.markInCombat(p);
            a.ticks++;
            return;
        }
        // Charging a master spell.
        a.ticks++;
        if (a.ticks % 3 == 0) {
            MagicFx.send(p, MagicFx.CHARGE, spell.element, p.position(), p.position(), p.getId(), Math.min(100, a.ticks * 100 / Math.max(1, spell.chargeTicks)));
        }
        if (a.ticks % 10 == 0) MagicFx.sound(p, SoundEvents.BEACON_AMBIENT, 1f, 0.8f + a.ticks / (float) spell.chargeTicks);
        if (a.ticks >= spell.chargeTicks) {
            ACTIVE.remove(p.getUUID());
            float cost = SpellMath.cost(p, spell);
            if (!hasMagicka(p, cost)) {
                fail(p);
                return;
            }
            release(p, spell, cost);
        }
    }

    public static boolean isCasting(ServerPlayer p, String spellId) {
        Active a = ACTIVE.get(p.getUUID());
        return a != null && a.spell.id.equals(spellId);
    }

    // ------------------------------------------------------------------ wards

    static void raiseWard(ServerPlayer p, float strength) {
        WARD.put(p.getUUID(), strength);
    }

    /** Absorbs incoming spell damage with an active ward. Returns the damage that gets through. */
    public static float absorbWithWard(ServerPlayer p, DamageSource source, float amount) {
        Float pool = WARD.get(p.getUUID());
        if (pool == null || !isCasting(p, "lesser_ward") || !MagicDamage.isWardable(source)) return amount;
        Vec3 from = source.getSourcePosition();
        if (from != null) {
            Vec3 dir = from.subtract(p.getEyePosition()).normalize();
            if (dir.dot(p.getViewVector(1f)) < 0.1) return amount; // wards only cover the front
        }
        float absorbed = Math.min(pool, amount);
        pool -= absorbed;
        if (Perks.has(p, "restoration.ward_absorb")) {
            PlayerData data = SkyData.get(p);
            data.setMagicka(data.getMagicka() + absorbed * CombatHandler.SKYRIM_SCALE);
        }
        Progression.addSkillXp(p, Skill.RESTORATION, absorbed * CombatHandler.SKYRIM_SCALE * 0.5f);
        MagicFx.send(p, MagicFx.WARD, Element.HOLY, p.getEyePosition(), p.getViewVector(1f), p.getId(), 1);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 1.2f, 1.4f);
        if (pool <= 0.01f) {
            stop(p);
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1f, 1.2f);
            Notifier.message(p, Component.translatable("message.skycraft.ward_broken"));
        } else {
            WARD.put(p.getUUID(), pool);
        }
        return amount - absorbed;
    }

    public static void forget(UUID id) {
        ACTIVE.remove(id);
        NEXT_CAST.remove(id);
        LAST_FAIL.remove(id);
        WARD.remove(id);
    }

    public static void clear() {
        ACTIVE.clear();
        NEXT_CAST.clear();
        LAST_FAIL.clear();
        WARD.clear();
    }
}
