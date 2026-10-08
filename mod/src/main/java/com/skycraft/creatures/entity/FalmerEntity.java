package com.skycraft.creatures.entity;

import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** {@code skycraft:falmer}: feral, blind subterranean dwellers residing in deep barrows and Dwemer ruins. */
public class FalmerEntity extends SkyHumanoid implements Ranked {
    public FalmerEntity(EntityType<? extends FalmerEntity> type, Level level) {
        super(type, level);
        this.xpReward = 16;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 32.0)
                .add(Attributes.ATTACK_DAMAGE, 4.0)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.ARMOR, 3.0);
    }

    @Override
    protected int skinCount() {
        return 1;
    }

    @Override
    protected void equip(RandomSource random, DifficultyInstance difficulty) {
        if (random.nextFloat() < 0.35f) {
            Gear.equip(this, EquipmentSlot.MAINHAND, new ItemStack(Items.BOW), 0.08f);
        } else {
            Gear.equip(this, EquipmentSlot.MAINHAND, new ItemStack(Gear.modItem("falmer_sword", Items.IRON_SWORD)), 0.08f);
            if (random.nextFloat() < 0.35f) {
                Gear.equip(this, EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD), 0.06f);
            }
        }
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof LivingEntity living) {
            // Falmer coat their weapons in Chaurus venom
            living.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 0), this);
        }
        return hit;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.STRAY_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.STRAY_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.STRAY_DEATH;
    }

    @Override
    @Nullable
    public Component rankName(int level) {
        String rank = level >= 28 ? "shadowmaster" : level >= 20 ? "nightprowler" : level >= 12 ? "skulker" : null;
        return rank == null ? null : Component.translatable("entity.skycraft.falmer." + rank);
    }
}
