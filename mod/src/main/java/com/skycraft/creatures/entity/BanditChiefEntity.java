package com.skycraft.creatures.entity;

import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** {@code skycraft:bandit_chief}: leader of a bandit camp. Heavy armor, two-handed weapon, hits hard. */
public class BanditChiefEntity extends BanditEntity {
    public BanditChiefEntity(EntityType<? extends BanditChiefEntity> type, Level level) {
        super(type, level);
        this.xpReward = 25;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 55.0)
                .add(Attributes.ATTACK_DAMAGE, 5.0)
                .add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.ARMOR, 4.0)
                .add(Attributes.ARMOR_TOUGHNESS, 2.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.5);
    }

    @Override
    protected int skinCount() {
        return 1;
    }

    @Override
    protected void equip(RandomSource random, DifficultyInstance difficulty) {
        Item[] weapons = {
                Gear.modItem("steel_greatsword", Items.IRON_SWORD), Gear.modItem("steel_battleaxe", Items.IRON_AXE),
                Gear.modItem("iron_warhammer", Items.IRON_AXE), Gear.modItem("orcish_battleaxe", Items.DIAMOND_AXE)
        };
        Gear.equip(this, EquipmentSlot.MAINHAND, Gear.pick(random, weapons), 0.12f);
        for (EquipmentSlot slot : Gear.ARMOR_SLOTS) {
            ItemStack stack = slot == EquipmentSlot.CHEST && random.nextFloat() < 0.25f ? new ItemStack(Items.DIAMOND_CHESTPLATE)
                    : random.nextFloat() < 0.75f ? new ItemStack(Gear.iron(slot)) : new ItemStack(Gear.chainmail(slot));
            Gear.equip(this, slot, stack, 0.1f);
        }
    }

    /** Two-handed blows knock the target back hard. */
    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof LivingEntity living) {
            living.knockback(0.6, this.getX() - target.getX(), this.getZ() - target.getZ());
            living.hurtMarked = true;
        }
        return hit;
    }

    @Override
    @Nullable
    public Component rankName(int level) {
        return null;
    }
}
