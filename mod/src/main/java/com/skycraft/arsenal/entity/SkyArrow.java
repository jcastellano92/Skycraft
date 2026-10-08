package com.skycraft.arsenal.entity;

import com.skycraft.arsenal.ArsenalEntities;
import com.skycraft.arsenal.item.ArrowKind;
import com.skycraft.arsenal.item.SkyArrowItem;
import com.skycraft.registry.ModEffects;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The arrow entity of every Skycraft arrow and bolt. It remembers the exact item it was shot as (synced, so the
 * client can pick the texture and particles), which is what you pick back up - and what is recorded on a mob's body
 * when it hits one.
 */
public class SkyArrow extends AbstractArrow {
    private static final EntityDataAccessor<ItemStack> AMMO = SynchedEntityData.defineId(SkyArrow.class, EntityDataSerializers.ITEM_STACK);

    public SkyArrow(EntityType<? extends SkyArrow> type, Level level) {
        super(type, level);
    }

    public SkyArrow(Level level, LivingEntity shooter, ItemStack ammo) {
        super(ArsenalEntities.SKY_ARROW.get(), shooter, level);
        setAmmo(ammo);
    }

    public SkyArrow(Level level, double x, double y, double z, ItemStack ammo) {
        super(ArsenalEntities.SKY_ARROW.get(), x, y, z, level);
        setAmmo(ammo);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(AMMO, ItemStack.EMPTY);
    }

    public void setAmmo(ItemStack stack) {
        ItemStack copy = stack.copy();
        copy.setCount(1);
        this.entityData.set(AMMO, copy);
    }

    public ItemStack ammo() {
        return this.entityData.get(AMMO);
    }

    @Nullable
    public ArrowKind kind() {
        return ammo().getItem() instanceof SkyArrowItem item ? item.kind : null;
    }

    public ArrowKind.Element element() {
        ArrowKind kind = kind();
        return kind == null ? ArrowKind.Element.NONE : kind.element;
    }

    /** The item you get back for this arrow (also recorded on bodies). */
    public ItemStack pickupStack() {
        return getPickupItem();
    }

    @Override
    protected ItemStack getPickupItem() {
        ItemStack stack = ammo().copy();
        if (stack.isEmpty()) return new ItemStack(Items.ARROW);
        stack.setCount(1);
        return stack;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide || inGround) return;
        Vec3 m = getDeltaMovement();
        switch (element()) {
            case FROST -> level().addParticle(ParticleTypes.SNOWFLAKE, getX() - m.x * 0.5, getY() - m.y * 0.5, getZ() - m.z * 0.5, 0, 0, 0);
            case SHOCK -> level().addParticle(ParticleTypes.ELECTRIC_SPARK, getX() - m.x * 0.5, getY() - m.y * 0.5, getZ() - m.z * 0.5, 0, 0, 0);
            case FIRE -> level().addParticle(ParticleTypes.SMALL_FLAME, getX(), getY(), getZ(), 0, 0.01, 0);
            case EXPLOSIVE -> level().addParticle(ParticleTypes.SMOKE, getX(), getY(), getZ(), 0, 0, 0);
            default -> {
            }
        }
    }

    @Override
    protected void doPostHurtEffects(LivingEntity target) {
        super.doPostHurtEffects(target);
        switch (element()) {
            case FROST -> {
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1));
                target.setTicksFrozen(Math.max(target.getTicksFrozen(), target.getTicksRequiredToFreeze() + 60));
            }
            case SHOCK -> {
                if (random.nextFloat() < 0.15f && !target.getType().is(com.skycraft.combat.CombatHandler.BOSSES)) {
                    target.addEffect(new MobEffectInstance(ModEffects.STAGGER.get(), 15, 0, false, false));
                }
            }
            case EXPLOSIVE -> explode();
            default -> {
            }
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (element() == ArrowKind.Element.EXPLOSIVE && !level().isClientSide) explode();
    }

    private void explode() {
        if (level().isClientSide || isRemoved()) return;
        level().explode(this, getX(), getY(), getZ(), 1.6f, Level.ExplosionInteraction.NONE);
        discard();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        ItemStack ammo = ammo();
        if (!ammo.isEmpty()) tag.put("ammo", ammo.save(new CompoundTag()));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("ammo")) setAmmo(ItemStack.of(tag.getCompound("ammo")));
    }
}
