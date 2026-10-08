package com.skycraft.combat;

import com.skycraft.SkyConfig;
import com.skycraft.Skycraft;
import com.skycraft.core.Notifier;
import com.skycraft.core.PlayerData;
import com.skycraft.core.Race;
import com.skycraft.core.SkyData;
import com.skycraft.core.Skill;
import com.skycraft.perk.Perks;
import com.skycraft.registry.ModEffects;
import com.skycraft.skills.Progression;
import com.skycraft.vitals.ActionHandler;
import com.skycraft.vitals.RacePowers;
import com.skycraft.vitals.Vitals;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.ShieldBlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Skyrim combat rules: skill/perk damage scaling, power attacks (stamina), sneak attacks, weapon & shield
 * blocking, armor skills, racial resistances and the combat/armor/block/sneak/archery perks.
 */
@Mod.EventBusSubscriber(modid = Skycraft.MODID)
public final class CombatHandler {
    /** Entities that take reduced sneak attack multipliers and can't be paralyzed (bosses, dragons). */
    public static final TagKey<EntityType<?>> BOSSES = TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation("forge", "bosses"));
    /** Creatures counted as "beasts/monsters" for the Hunting tree's Monster Hunter perk. */
    public static final TagKey<EntityType<?>> MONSTERS = TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(Skycraft.MODID, "monsters"));
    /** Skyrim damage is roughly 5x Minecraft damage; XP use values are computed in Skyrim units. */
    public static final float SKYRIM_SCALE = 5f;

    private CombatHandler() {}

    // ------------------------------------------------------------------ offense & defense

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onHurt(LivingHurtEvent event) {
        LivingEntity target = event.getEntity();
        DamageSource source = event.getSource();
        if (target.level().isClientSide) return;

        if (source.getEntity() instanceof ServerPlayer attacker && attacker != target) {
            Vitals.markInCombat(attacker);
            float amount = event.getAmount();
            if (source.getDirectEntity() == attacker) {
                amount = meleeDamage(attacker, target, amount);
            } else if (source.getDirectEntity() instanceof AbstractArrow arrow) {
                amount = arrowDamage(attacker, target, arrow, amount);
            }
            event.setAmount(amount);
        }

        if (target instanceof ServerPlayer defender) {
            Vitals.markInCombat(defender);
            float amount = defend(defender, source, event.getAmount());
            if (amount <= 0) {
                event.setCanceled(true);
                return;
            }
            event.setAmount(amount);
        }
    }

    private static float meleeDamage(ServerPlayer player, LivingEntity target, float amount) {
        PlayerData data = SkyData.get(player);
        ItemStack weapon = player.getMainHandItem();
        WeaponClass wc = WeaponClass.of(weapon);
        float mult = 1f;
        double scale = SkyConfig.WEAPON_SKILL_DAMAGE_SCALE.get();
        var rnd = player.getRandom();

        if (wc.skill == Skill.ONE_HANDED) {
            mult += (float) (data.getSkill(Skill.ONE_HANDED) * scale) + 0.2f * Perks.rank(player, "one_handed.armsman");
        } else if (wc.skill == Skill.TWO_HANDED) {
            mult += (float) (data.getSkill(Skill.TWO_HANDED) * scale) + 0.2f * Perks.rank(player, "two_handed.barbarian");
        } else if (wc == WeaponClass.UNARMED) {
            if (data.getRace() == Race.KHAJIIT) amount += 3f;
            if (Perks.has(player, "heavy_armor.fists_of_steel")) {
                // Minecraft has no gauntlet slot: heavy chest armor rating stands in for it.
                ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
                if (chest.getItem() instanceof ArmorItem a && ArmorClass.isHeavy(chest)) amount += a.getDefense() * 0.5f;
            }
        }

        // Weapon-family perks
        switch (wc) {
            case SWORD, DAGGER -> {
                int r = Perks.rank(player, "one_handed.bladesman");
                if (r > 0 && rnd.nextFloat() < 0.1f * r) {
                    mult *= 1.5f;
                    Notifier.message(player, Component.translatable("message.skycraft.critical"));
                }
            }
            case GREATSWORD -> {
                int r = Perks.rank(player, "two_handed.deep_wounds");
                if (r > 0 && rnd.nextFloat() < 0.1f * r) {
                    mult *= 1.5f;
                    Notifier.message(player, Component.translatable("message.skycraft.critical"));
                }
            }
            case WAR_AXE -> bleed(target, Perks.rank(player, "one_handed.hack_and_slash"));
            case BATTLEAXE -> bleed(target, Perks.rank(player, "two_handed.limbsplitter"));
            case MACE -> mult += 0.15f * Perks.rank(player, "one_handed.bone_breaker");
            case WARHAMMER -> mult += 0.15f * Perks.rank(player, "two_handed.skullcrusher");
            default -> {
            }
        }

        // Power attacks: hold the power-attack key while swinging, or attack while sprinting.
        boolean sprinting = player.isSprinting();
        boolean power = ActionHandler.consumePowerAttack(player) || sprinting;
        if (power && wc != WeaponClass.UNARMED && wc != WeaponClass.BOW && wc != WeaponClass.OTHER) {
            float cost = (float) (double) SkyConfig.POWER_ATTACK_STAMINA.get();
            if (wc.twoHanded()) cost *= 1.3f;
            if (wc.skill == Skill.ONE_HANDED && Perks.has(player, "one_handed.fighting_stance")) cost *= 0.75f;
            if (wc.skill == Skill.TWO_HANDED && Perks.has(player, "two_handed.champions_stance")) cost *= 0.75f;
            if (Vitals.consumeStamina(player, cost, true)) {
                mult *= wc.twoHanded() ? 1.75f : 1.5f;
                if (wc.skill == Skill.ONE_HANDED && Perks.has(player, "one_handed.savage_strike")) mult *= 1.25f;
                if (wc.skill == Skill.TWO_HANDED && Perks.has(player, "two_handed.devastating_blow")) mult *= 1.25f;
                if (sprinting && (wc.skill == Skill.ONE_HANDED && Perks.has(player, "one_handed.critical_charge")
                        || wc.skill == Skill.TWO_HANDED && Perks.has(player, "two_handed.great_critical_charge"))) {
                    mult *= 1.5f;
                }
                stagger(player, target, 20);
                boolean paralyzePerk = wc.skill == Skill.ONE_HANDED ? Perks.has(player, "one_handed.paralyzing_strike")
                        : Perks.has(player, "two_handed.warmaster");
                if (paralyzePerk && rnd.nextFloat() < 0.25f && !target.getType().is(BOSSES)) {
                    target.addEffect(new MobEffectInstance(ModEffects.PARALYSIS.get(), 60));
                }
                if (wc.twoHanded() && Perks.has(player, "two_handed.sweep")) sweep(player, target, amount * mult * 0.5f);
                player.level().playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1f, 0.7f);
            } else {
                mult *= 0.75f; // exhausted power attacks are weak
            }
        }

        mult *= huntingMultiplier(player, target);
        mult *= sneakMultiplier(player, target, wc);

        float result = amount * mult;
        if (wc.skill != null && wc.skill != Skill.ARCHERY) {
            Progression.addSkillXp(player, wc.skill, Math.min(result, target.getHealth()) * SKYRIM_SCALE * 0.2f);
        }
        return result;
    }

    private static float arrowDamage(ServerPlayer player, LivingEntity target, AbstractArrow arrow, float amount) {
        PlayerData data = SkyData.get(player);
        var rnd = player.getRandom();
        float mult = 1f + (float) (data.getSkill(Skill.ARCHERY) * SkyConfig.WEAPON_SKILL_DAMAGE_SCALE.get())
                + 0.2f * Perks.rank(player, "archery.overdraw");
        int crit = Perks.rank(player, "archery.critical_shot");
        if (crit > 0 && rnd.nextFloat() < 0.1f * crit) {
            mult *= 1.5f;
            Notifier.message(player, Component.translatable("message.skycraft.critical"));
        }
        if (Perks.has(player, "archery.power_shot") && arrow.isCritArrow()) stagger(player, target, 20);
        if (Perks.has(player, "archery.bullseye") && rnd.nextFloat() < 0.15f && !target.getType().is(BOSSES)) {
            target.addEffect(new MobEffectInstance(ModEffects.PARALYSIS.get(), 60));
        }
        if (Perks.has(player, "archery.hunters_discipline") && arrow.pickup == AbstractArrow.Pickup.ALLOWED && rnd.nextBoolean()) {
            player.getInventory().add(new ItemStack(Items.ARROW));
        }
        mult *= huntingMultiplier(player, target);
        mult *= sneakMultiplier(player, target, WeaponClass.BOW);
        float result = amount * mult;
        Progression.addSkillXp(player, Skill.ARCHERY, Math.min(result, target.getHealth()) * SKYRIM_SCALE * 0.2f);
        return result;
    }

    private static float huntingMultiplier(ServerPlayer player, LivingEntity target) {
        float mult = 1f;
        if (target instanceof Animal) mult += 0.2f * Perks.rank(player, "hunting.tracker");
        if (target.getType().is(MONSTERS) && Perks.has(player, "hunting.monster_hunter")) mult += 0.25f;
        if (Perks.has(player, "hunting.apex_predator") && (target instanceof Animal || target.getType().is(MONSTERS))) mult += 0.25f;
        return mult;
    }

    /** Skyrim sneak attacks against targets that aren't aware of you. */
    private static float sneakMultiplier(ServerPlayer player, LivingEntity target, WeaponClass wc) {
        if (!SkyConfig.SNEAK_ATTACKS.get() || !player.isCrouching() || target instanceof Player) return 1f;
        if (target instanceof Mob mob && mob.getTarget() == player) return 1f;
        float mult = switch (wc) {
            case DAGGER -> Perks.has(player, "sneak.assassins_blade") ? 15f : Perks.has(player, "sneak.backstab") ? 6f : 3f;
            case SWORD, WAR_AXE, MACE -> Perks.has(player, "sneak.backstab") ? 6f : 3f;
            case BOW -> Perks.has(player, "sneak.deadly_aim") ? 3f : 2f;
            default -> 2f;
        };
        if (target.getType().is(BOSSES)) mult = Math.min(mult, 2f);
        Notifier.message(player, Component.translatable("message.skycraft.sneak_attack", (int) mult));
        Progression.addSkillXp(player, Skill.SNEAK, 2.5f * mult);
        SkyData.get(player).addStat("sneak_attacks", 1);
        return mult;
    }

    private static float defend(ServerPlayer player, DamageSource source, float amount) {
        PlayerData data = SkyData.get(player);
        var rnd = player.getRandom();
        Entity attacker = source.getEntity();
        boolean melee = attacker != null && source.getDirectEntity() == attacker;
        boolean armorApplies = !source.is(DamageTypeTags.BYPASSES_ARMOR);

        // Racial and magical resistances
        Race race = data.getRace();
        if (race != null) {
            if (source.is(DamageTypeTags.IS_FIRE)) amount *= 1f - race.resistance("fire");
            if (source.is(DamageTypeTags.IS_FREEZING)) amount *= 1f - race.resistance("frost");
            if (source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC)) {
                amount *= 1f - race.resistance("magic") - race.resistance("poison") * 0.5f;
            }
        }
        if (source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC)) {
            amount *= 1f - 0.1f * Perks.rank(player, "alteration.magic_resistance");
        }

        int heavy = ArmorClass.countHeavy(player);
        int light = ArmorClass.countLight(player);

        if (melee && light >= 4 && Perks.has(player, "light_armor.deft_movement") && rnd.nextFloat() < 0.1f) {
            Notifier.message(player, Component.translatable("message.skycraft.evaded"));
            return 0;
        }
        if (melee && heavy >= 4 && Perks.has(player, "heavy_armor.reflect_blows") && rnd.nextFloat() < 0.1f
                && attacker instanceof LivingEntity living) {
            living.hurt(player.damageSources().thorns(player), amount);
            return 0;
        }

        // Weapon blocking (hold the block key with a melee weapon and no shield).
        if (armorApplies && attacker != null && ActionHandler.isWeaponBlocking(player) && facing(player, attacker)) {
            WeaponClass wc = WeaponClass.of(player.getMainHandItem());
            if (wc.skill == Skill.ONE_HANDED || wc.skill == Skill.TWO_HANDED) {
                float pct = Math.min(0.7f, 0.3f + data.getSkill(Skill.BLOCK) * 0.002f + 0.05f * Perks.rank(player, "block.shield_wall"));
                float blocked = amount * pct;
                if (!Vitals.consumeStamina(player, blocked * SKYRIM_SCALE * 0.4f, true)) blocked *= 0.5f;
                amount -= blocked;
                Progression.addSkillXp(player, Skill.BLOCK, blocked * SKYRIM_SCALE * 0.2f);
                player.level().playSound(null, player.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.3f, 1.8f);
            }
        }

        // Armor skills
        if (armorApplies && (heavy > 0 || light > 0)) {
            float reduction = 0f;
            if (heavy > 0) {
                float r = data.getSkill(Skill.HEAVY_ARMOR) * 0.0015f + 0.04f * Perks.rank(player, "heavy_armor.juggernaut");
                if (heavy >= 4 && Perks.has(player, "heavy_armor.well_fitted")) r += 0.05f;
                if (heavy >= 4 && Perks.has(player, "heavy_armor.matching_set") && ArmorClass.matchingSet(player)) r += 0.05f;
                reduction += r * heavy / 4f;
                Progression.addSkillXp(player, Skill.HEAVY_ARMOR, amount * SKYRIM_SCALE * 0.2f * heavy / 4f);
            }
            if (light > 0) {
                float r = data.getSkill(Skill.LIGHT_ARMOR) * 0.0015f + 0.04f * Perks.rank(player, "light_armor.agile_defender");
                if (light >= 4 && Perks.has(player, "light_armor.custom_fit")) r += 0.05f;
                if (light >= 4 && Perks.has(player, "light_armor.matching_set_light") && ArmorClass.matchingSet(player)) r += 0.05f;
                reduction += r * light / 4f;
                Progression.addSkillXp(player, Skill.LIGHT_ARMOR, amount * SKYRIM_SCALE * 0.2f * light / 4f);
            }
            amount *= 1f - Math.min(0.5f, reduction);
        }

        // Heavy armor makes you hard to knock around.
        if (heavy >= 4 && Perks.has(player, "heavy_armor.tower_of_strength")) {
            player.removeEffect(ModEffects.STAGGER.get());
        }
        return amount;
    }

    private static boolean facing(Player player, Entity attacker) {
        Vec3 look = player.getViewVector(1f).multiply(1, 0, 1).normalize();
        Vec3 dir = attacker.position().subtract(player.position()).multiply(1, 0, 1).normalize();
        return look.dot(dir) > 0.2;
    }

    private static void bleed(LivingEntity target, int rank) {
        if (rank > 0) target.addEffect(new MobEffectInstance(ModEffects.BLEEDING.get(), 60, rank - 1));
    }

    public static void stagger(Entity source, LivingEntity target, int ticks) {
        if (target.getType().is(BOSSES)) return;
        if (target instanceof ServerPlayer p && ArmorClass.countHeavy(p) >= 4 && Perks.has(p, "heavy_armor.tower_of_strength")) return;
        target.addEffect(new MobEffectInstance(ModEffects.STAGGER.get(), ticks, 0, false, false));
        Vec3 push = target.position().subtract(source.position()).multiply(1, 0, 1).normalize().scale(0.6);
        target.push(push.x, 0.15, push.z);
        target.hurtMarked = true;
    }

    private static void sweep(ServerPlayer player, LivingEntity primary, float damage) {
        Vec3 look = player.getViewVector(1f);
        for (LivingEntity other : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(3.5),
                e -> e != player && e != primary && e.isAlive() && (e instanceof Enemy || e instanceof Mob m && m.getTarget() == player))) {
            Vec3 dir = other.position().subtract(player.position()).normalize();
            if (look.dot(dir) > 0.3) other.hurt(player.damageSources().playerAttack(player), damage);
        }
    }

    // ------------------------------------------------------------------ shields

    @SubscribeEvent
    public static void onShieldBlock(ShieldBlockEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PlayerData data = SkyData.get(player);
        DamageSource source = event.getDamageSource();
        float original = event.getOriginalBlockedDamage();
        float pct = Math.min(0.95f, 0.6f + data.getSkill(Skill.BLOCK) * 0.002f + 0.05f * Perks.rank(player, "block.shield_wall"));
        if (source.getDirectEntity() instanceof AbstractArrow && Perks.has(player, "block.deflect_arrows")) pct = 1f;
        if (source.is(DamageTypeTags.IS_FIRE) && Perks.has(player, "block.elemental_protection")) pct = Math.max(pct, 0.9f);
        float blocked = original * pct;
        if (!Vitals.consumeStamina(player, blocked * SKYRIM_SCALE * 0.25f, true)) blocked *= 0.5f;
        event.setBlockedDamage(blocked);
        Progression.addSkillXp(player, Skill.BLOCK, blocked * SKYRIM_SCALE * 0.25f);
        Vitals.markInCombat(player);

        if (source.getEntity() instanceof LivingEntity attacker && source.getDirectEntity() == attacker) {
            if (Perks.has(player, "block.quick_reflexes") || Perks.has(player, "block.power_bash")) stagger(player, attacker, 20);
            if (Perks.has(player, "block.deadly_bash")) attacker.hurt(player.damageSources().playerAttack(player), blocked * 0.25f);
            if (Perks.has(player, "block.disarming_bash") && attacker instanceof Mob mob && player.getRandom().nextFloat() < 0.1f
                    && !mob.getMainHandItem().isEmpty()) {
                mob.spawnAtLocation(mob.getMainHandItem().copy());
                mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                Notifier.message(player, Component.translatable("message.skycraft.disarmed"));
            }
        }
    }

    // ------------------------------------------------------------------ stealth

    @SubscribeEvent
    public static void onVisibility(LivingEvent.LivingVisibilityEvent event) {
        if (!(event.getEntity() instanceof Player player) || !player.isCrouching()) return;
        PlayerData data = SkyData.get(player);
        double mult = 0.8 - data.getSkill(Skill.SNEAK) * 0.004 - 0.05 * Perks.rank(player, "sneak.stealth");
        int heavy = ArmorClass.countHeavy(player);
        if (heavy > 0 && !Perks.has(player, "sneak.muffled_movement")) mult += 0.05 * heavy;
        if (Perks.has(player, "sneak.silence")) mult -= 0.1;
        if (Perks.has(player, "sneak.shadow_warrior")) mult *= 0.75;
        event.modifyVisibility(Math.max(0.1, Math.min(1.0, mult)));
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof Player player && ArmorClass.countHeavy(player) >= 4 && Perks.has(player, "heavy_armor.cushioned")) {
            event.setDamageMultiplier(event.getDamageMultiplier() * 0.5f);
        }
    }

    /** Archery "Quick Shot": draw bows 30% faster. */
    @SubscribeEvent
    public static void onUseTick(LivingEntityUseItemEvent.Tick event) {
        if (event.getEntity() instanceof Player player && event.getItem().getItem() instanceof BowItem
                && Perks.has(player, "archery.quick_shot") && player.tickCount % 3 == 0) {
            event.setDuration(event.getDuration() - 1);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        if (player.tickCount % 20 == 0) {
            RacePowers.tickFireCloak(player);
            // Sneak trains while you are hidden near enemies.
            if (player.isCrouching()) {
                boolean nearEnemy = !player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(12),
                        m -> m instanceof Enemy && m.isAlive() && m.getTarget() != player).isEmpty();
                if (nearEnemy) Progression.addSkillXp(player, Skill.SNEAK, 0.75f);
            }
            // Block "Shield Charge": sprinting into enemies with a shield knocks them down.
            if (player.isSprinting() && Perks.has(player, "block.shield_charge") && player.getOffhandItem().getItem() instanceof ShieldItem) {
                for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(1.2),
                        e -> e != player && e instanceof Enemy)) {
                    stagger(player, e, 40);
                }
            }
        }
    }
}
