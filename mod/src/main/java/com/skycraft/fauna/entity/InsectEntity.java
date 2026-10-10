package com.skycraft.fauna.entity;

import com.skycraft.core.Skill;
import com.skycraft.fauna.FaunaItems;
import com.skycraft.skills.Progression;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * A catchable insect (Bat-style ambient flier): butterflies and dragonflies by day, torchbugs and luna moths by night.
 * Flutters about its home spot (a flower patch, a pond) a few blocks above the ground. Right-click it with an empty
 * hand to catch it: the player gets its alchemy ingredient and a little Alchemy XP. Spawned near players by
 * {@link com.skycraft.fauna.InsectSpawner}; vanishes when no player is near, or when its time of day is over.
 */
public class InsectEntity extends AmbientCreature {
    public enum Kind {
        BUTTERFLY(true, 0.10, 3),
        DRAGONFLY(true, 0.24, 2),
        TORCHBUG(false, 0.06, 4),
        MOTH(false, 0.09, 3);

        /** Active by day (true) or by night (false). */
        public final boolean diurnal;
        final double speed;
        final int heightRange;

        Kind(boolean diurnal, double speed, int heightRange) {
            this.diurnal = diurnal;
            this.speed = speed;
            this.heightRange = heightRange;
        }
    }

    /** Butterfly variants. */
    public static final int MONARCH = 0;
    public static final int BLUE = 1;

    private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(InsectEntity.class, EntityDataSerializers.INT);
    private static final Vector3f TORCHBUG_GLOW = new Vector3f(1.0f, 0.9f, 0.35f);

    private final Kind kind;
    @Nullable
    private BlockPos target;
    @Nullable
    private BlockPos home;
    private int hover;

    public InsectEntity(EntityType<? extends InsectEntity> type, Level level, Kind kind) {
        super(type, level);
        this.kind = kind;
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 1.0).add(Attributes.MOVEMENT_SPEED, 0.1);
    }

    public Kind kind() {
        return kind;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(VARIANT, 0);
    }

    public int getVariant() {
        return this.entityData.get(VARIANT);
    }

    public void setVariant(int variant) {
        this.entityData.set(VARIANT, variant);
    }

    public void setHome(BlockPos home) {
        this.home = home.immutable();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Variant", getVariant());
        if (home != null) tag.putLong("Home", home.asLong());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setVariant(tag.getInt("Variant"));
        if (tag.contains("Home")) home = BlockPos.of(tag.getLong("Home"));
    }

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                        @Nullable SpawnGroupData data, @Nullable CompoundTag tag) {
        if (kind == Kind.BUTTERFLY) setVariant(this.random.nextInt(3) == 0 ? BLUE : MONARCH);
        if (home == null) home = blockPosition();
        return super.finalizeSpawn(level, difficulty, reason, data, tag);
    }

    /** The ingredient this insect yields when caught. */
    public Item ingredient() {
        return switch (kind) {
            case BUTTERFLY -> getVariant() == BLUE ? FaunaItems.BLUE_BUTTERFLY_WING.get() : FaunaItems.MONARCH_WING.get();
            case DRAGONFLY -> FaunaItems.BLUE_DARTWING.get();
            case TORCHBUG -> FaunaItems.TORCHBUG_THORAX.get();
            case MOTH -> FaunaItems.LUNA_MOTH_WING.get();
        };
    }

    // ------------------------------------------------------------------ catching

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || !this.isAlive()) {
            return super.mobInteract(player, hand);
        }
        if (this.level() instanceof ServerLevel level) {
            ItemStack loot = new ItemStack(ingredient());
            if (!com.skycraft.vitals.ActionHandler.addToBags(player, loot)) {
                player.drop(loot, false);
            }
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.35f, 1.6f + this.random.nextFloat() * 0.3f);
            level.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.15, getZ(), 3, 0.05, 0.05, 0.05, 0.01);
            if (player instanceof ServerPlayer sp) Progression.addSkillXp(sp, Skill.ALCHEMY, 0.5f);
            this.discard();
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    // ------------------------------------------------------------------ flight

    @Override
    public void tick() {
        super.tick();
        Level level = this.level();
        if (level.isClientSide) {
            if (kind == Kind.TORCHBUG && this.random.nextInt(10) == 0) {
                level.addParticle(new DustParticleOptions(TORCHBUG_GLOW, 0.45f), getX(), getY() + 0.05, getZ(), 0, 0, 0);
            }
            return;
        }
        if (this.tickCount % 100 == 0) {
            Player near = level.getNearestPlayer(this, 64.0);
            if (near == null) {
                this.discard();
                return;
            }
            // daytime insects leave at dusk, night insects at dawn (out of sight)
            boolean day = level.isDay();
            if (day != kind.diurnal && this.random.nextInt(3) == 0 && this.distanceToSqr(near) > 144.0) this.discard();
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (home == null) home = blockPosition();
        if (target != null && (!this.level().isEmptyBlock(target) || target.getY() <= this.level().getMinBuildHeight())) target = null;
        if (hover > 0) {
            // dragonflies hang in the air, then dart off
            hover--;
            setDeltaMovement(getDeltaMovement().scale(0.5));
            return;
        }
        int retarget = kind == Kind.DRAGONFLY ? 30 : 70;
        if (target == null || this.random.nextInt(retarget) == 0 || target.closerToCenterThan(position(), 1.0)) {
            if (target != null && kind == Kind.DRAGONFLY && this.random.nextInt(2) == 0) hover = 15 + this.random.nextInt(40);
            target = pickTarget();
        }
        if (target == null) return;
        double dx = target.getX() + 0.5 - getX();
        double dy = target.getY() + 0.5 - getY();
        double dz = target.getZ() + 0.5 - getZ();
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist < 1.0e-3) return;
        double s = kind.speed;
        double flutter = (kind == Kind.BUTTERFLY || kind == Kind.MOTH) ? Mth.sin(this.tickCount * 0.7f) * 0.05 : 0.0;
        Vec3 v = getDeltaMovement();
        Vec3 nv = v.add((dx / dist * s - v.x) * 0.15, (dy / dist * s * 0.8 + flutter - v.y) * 0.15, (dz / dist * s - v.z) * 0.15);
        setDeltaMovement(nv);
        float yaw = (float) (Mth.atan2(nv.z, nv.x) * (180.0 / Math.PI)) - 90.0f;
        setYRot(getYRot() + Mth.wrapDegrees(yaw - getYRot()) * 0.6f);
        this.yBodyRot = getYRot();
        this.yHeadRot = getYRot();
    }

    @Nullable
    private BlockPos pickTarget() {
        BlockPos base = home != null && home.distSqr(blockPosition()) < 24 * 24 ? home : blockPosition();
        int r = kind == Kind.DRAGONFLY ? 7 : 5;
        for (int i = 0; i < 6; i++) {
            int x = base.getX() + this.random.nextInt(2 * r + 1) - r;
            int z = base.getZ() + this.random.nextInt(2 * r + 1) - r;
            int ground = this.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            int y = ground + 1 + this.random.nextInt(kind.heightRange);
            if (Math.abs(y - getY()) > 8) continue;
            BlockPos p = new BlockPos(x, y, z);
            if (this.level().isEmptyBlock(p)) return p;
        }
        return null;
    }

    // ------------------------------------------------------------------ ambient-creature plumbing

    @Override
    public boolean isNoGravity() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {
    }

    @Override
    protected void pushEntities() {
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
    }

    @Override
    public boolean isIgnoringBlockTriggers() {
        return true;
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }

    @Override
    protected float getStandingEyeHeight(Pose pose, EntityDimensions dimensions) {
        return dimensions.height * 0.5f;
    }

    @Override
    protected float getSoundVolume() {
        return 0.2f;
    }

    @Override
    @Nullable
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    @Nullable
    protected SoundEvent getHurtSound(DamageSource source) {
        return null;
    }

    @Override
    @Nullable
    protected SoundEvent getDeathSound() {
        return null;
    }
}
