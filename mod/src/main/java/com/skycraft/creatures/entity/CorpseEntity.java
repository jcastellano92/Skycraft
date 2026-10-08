package com.skycraft.creatures.entity;

import com.skycraft.creatures.CreaturesConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * {@code skycraft:corpse}: the lootable body of a creature killed by a player. Holds the drops in a 27-slot
 * container (right-click to loot) and remembers the dead creature's type and NBT so the client can render it
 * lying on its side. Despawns 60 s after being emptied, or after 15 minutes (configurable).
 */
public class CorpseEntity extends Entity {
    public static final int SIZE = 27;
    private static final EntityDataAccessor<String> BODY_TYPE = SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<CompoundTag> BODY = SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.COMPOUND_TAG);
    private static final EntityDataAccessor<Float> BODY_HEIGHT = SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> APPEAR_TICKS = SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> CENTERED = SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.BOOLEAN);

    /** NBT keys stripped from the stored body: big, irrelevant for rendering, or would tint/animate the dummy. */
    private static final String[] STRIP = {"Brain", "Offers", "Gossips", "Inventory", "Items", "Passengers", "ActiveEffects",
            "Attributes", "Leash", "HurtTime", "DeathTime", "HurtByTimestamp", "CustomName", "CustomNameVisible", "UUID",
            "ForgeCaps", "ForgeData", "Motion", "FallDistance", "Fire", "Air", "PortalCooldown", "Tags", "Team", "Xp",
            "RecipeBook", "abilities", "EnderItems", "SelectedItem", "ChestedHorse", "Bees", "Trusted", "Owner", "AngryAt"};

    private final CorpseContainer container = new CorpseContainer();
    private int age;
    private int emptyTicks;

    // client-side render cache
    @Nullable
    private Entity renderDummy;
    private boolean dummyFailed;

    public CorpseEntity(EntityType<? extends CorpseEntity> type, Level level) {
        super(type, level);
        this.noCulling = true; // the lying body is much bigger than the corpse's box
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(BODY_TYPE, "");
        this.entityData.define(BODY, new CompoundTag());
        this.entityData.define(BODY_HEIGHT, 1.8f);
        this.entityData.define(APPEAR_TICKS, 0);
        this.entityData.define(CENTERED, true);
    }

    // ------------------------------------------------------------------ setup (server)

    /** Remembers what the body looks like. Call before adding the corpse to the world. */
    public void setBody(LivingEntity dead) {
        String typeId = EntityType.getKey(dead.getType()).toString();
        CompoundTag body = new CompoundTag();
        try {
            body = dead.saveWithoutId(new CompoundTag());
            for (String key : STRIP) body.remove(key);
            body.putFloat("Health", 1.0f);
        } catch (Exception ignored) {
            body = new CompoundTag();
        }
        this.entityData.set(BODY_TYPE, typeId);
        this.entityData.set(BODY, body);
        this.entityData.set(BODY_HEIGHT, dead.getBbHeight());
        this.setCustomName(dead.getName().copy());
    }

    /** Ticks during which the body stays invisible (the vanilla death animation is still playing). */
    public void setAppearDelay(int ticks) {
        this.entityData.set(APPEAR_TICKS, ticks);
    }

    /** Adds loot; returns the part that didn't fit (empty when everything was stored). */
    public ItemStack addLoot(ItemStack stack) {
        return container.addItem(stack);
    }

    public boolean isEmptyOfLoot() {
        return container.isEmpty();
    }

    public float getBodyHeight() {
        return this.entityData.get(BODY_HEIGHT);
    }

    /** True when the corpse sits at the middle of the lying body, false when it sits at the dead creature's feet. */
    public boolean isCentered() {
        return this.entityData.get(CENTERED);
    }

    public void setCentered(boolean centered) {
        this.entityData.set(CENTERED, centered);
    }

    public boolean isVisibleYet() {
        return this.tickCount >= this.entityData.get(APPEAR_TICKS);
    }

    // ------------------------------------------------------------------ ticking

    @Override
    public void tick() {
        super.tick();
        if (!this.isNoGravity()) this.setDeltaMovement(this.getDeltaMovement().add(0, -0.04, 0));
        this.move(MoverType.SELF, this.getDeltaMovement());
        Vec3 v = this.getDeltaMovement();
        if (this.onGround()) this.setDeltaMovement(v.x * 0.5, 0, v.z * 0.5);
        else this.setDeltaMovement(v.x * 0.9, v.y * 0.98, v.z * 0.9);
        if (this.isInWater()) this.setDeltaMovement(this.getDeltaMovement().multiply(0.8, 0.5, 0.8));

        if (this.level().isClientSide) return;
        age++;
        if (container.isEmpty()) emptyTicks++;
        else emptyTicks = 0;
        if (container.openers > 0) return;
        if (emptyTicks > CreaturesConfig.EMPTY_CORPSE_DESPAWN_SECONDS.get() * 20
                || age > CreaturesConfig.CORPSE_DESPAWN_SECONDS.get() * 20) {
            this.discard();
        }
    }

    // ------------------------------------------------------------------ interaction

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (!this.level().isClientSide) {
            player.openMenu(new SimpleMenuProvider((id, inventory, p) -> ChestMenu.threeRows(id, inventory, container), this.getDisplayName()));
            this.playSound(SoundEvents.ARMOR_EQUIP_LEATHER, 0.6f, 0.9f);
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    public boolean canBeHitByProjectile() {
        return false; // arrows fly over bodies
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    /** Bodies can't be damaged; a creative-mode player punching one removes it. */
    @Override
    public boolean skipAttackInteraction(Entity attacker) {
        if (attacker instanceof Player p && p.isCreative() && !this.level().isClientSide) this.discard();
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean shouldShowName() {
        return false;
    }

    // ------------------------------------------------------------------ client render dummy

    /** A client-side, never-ticked instance of the dead creature loaded from its saved NBT, or null. */
    @Nullable
    public Entity getRenderDummy() {
        if (renderDummy != null || dummyFailed) return renderDummy;
        String typeId = this.entityData.get(BODY_TYPE);
        if (typeId.isEmpty()) return null; // data not synced yet
        try {
            Optional<EntityType<?>> type = EntityType.byString(typeId);
            if (type.isEmpty()) {
                dummyFailed = true;
                return null;
            }
            Entity e = type.get().create(this.level());
            if (e == null) {
                dummyFailed = true;
                return null;
            }
            CompoundTag tag = this.entityData.get(BODY).copy();
            if (!tag.isEmpty()) e.load(tag);
            e.setPos(this.getX(), this.getY(), this.getZ());
            e.setYRot(180f);
            e.yRotO = 180f;
            e.setXRot(0f);
            e.xRotO = 0f;
            if (e instanceof LivingEntity living) {
                living.hurtTime = 0;
                living.deathTime = 0;
                living.yBodyRot = 180f;
                living.yBodyRotO = 180f;
                living.yHeadRot = 180f;
                living.yHeadRotO = 180f;
            }
            if (e instanceof DragonEntity dragon) dragon.setCorpsePose(true);
            renderDummy = e;
        } catch (Throwable t) {
            dummyFailed = true;
            renderDummy = null;
        }
        return renderDummy;
    }

    /** Called by the renderer when drawing the dummy threw: never try again for this corpse. */
    public void markRenderFailed() {
        dummyFailed = true;
        renderDummy = null;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (BODY.equals(key) || BODY_TYPE.equals(key)) {
            renderDummy = null;
            dummyFailed = false;
        }
    }

    // ------------------------------------------------------------------ save

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        ListTag items = new ListTag();
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) continue;
            CompoundTag t = new CompoundTag();
            t.putByte("Slot", (byte) i);
            stack.save(t);
            items.add(t);
        }
        tag.put("Loot", items);
        tag.putString("BodyType", this.entityData.get(BODY_TYPE));
        tag.put("Body", this.entityData.get(BODY));
        tag.putFloat("BodyHeight", getBodyHeight());
        tag.putBoolean("Centered", isCentered());
        tag.putInt("Age", age);
        tag.putInt("EmptyTicks", emptyTicks);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        ListTag items = tag.getList("Loot", Tag.TAG_COMPOUND);
        for (int i = 0; i < items.size(); i++) {
            CompoundTag t = items.getCompound(i);
            int slot = t.getByte("Slot") & 255;
            if (slot < container.getContainerSize()) container.setItem(slot, ItemStack.of(t));
        }
        this.entityData.set(BODY_TYPE, tag.getString("BodyType"));
        this.entityData.set(BODY, tag.getCompound("Body"));
        if (tag.contains("BodyHeight")) this.entityData.set(BODY_HEIGHT, tag.getFloat("BodyHeight"));
        if (tag.contains("Centered")) setCentered(tag.getBoolean("Centered"));
        age = tag.getInt("Age");
        emptyTicks = tag.getInt("EmptyTicks");
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }

    /** The loot container; only valid while the corpse exists and the player is close. */
    private class CorpseContainer extends SimpleContainer {
        int openers;

        CorpseContainer() {
            super(SIZE);
        }

        @Override
        public boolean stillValid(Player player) {
            return CorpseEntity.this.isAlive() && player.distanceToSqr(CorpseEntity.this) < 64.0;
        }

        @Override
        public void startOpen(Player player) {
            openers++;
        }

        @Override
        public void stopOpen(Player player) {
            openers = Math.max(0, openers - 1);
        }
    }
}
