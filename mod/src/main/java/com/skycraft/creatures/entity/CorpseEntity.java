package com.skycraft.creatures.entity;

import com.skycraft.core.Notifier;
import com.skycraft.creatures.CreaturesConfig;
import com.skycraft.vitals.Vitals;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
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

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * {@code skycraft:corpse}: the lootable body of a creature killed by a player.
 *
 * <ul>
 *     <li><b>Per-player loot</b> (like Lootr chests): the body keeps a base loot container; every player who opens it
 *     gets a private copy initialised from the base the first time. Copies are saved per UUID (capped).</li>
 *     <li><b>Despawn</b>: 60 s after every player who opened it has emptied their copy, otherwise after 15 minutes
 *     (both configurable). Never while someone is looking inside or dragging it.</li>
 *     <li><b>Physics</b>: gravity, sliding with block friction, knocked around by hits and explosions (with a little
 *     tumble), falls off ledges and floats in water.</li>
 *     <li><b>Dragging</b>: sneak + right-click grabs the body; it follows in front of the player, costs stamina every
 *     second (more for bigger creatures) and is dropped when stamina runs out, on another sneak + right-click, when the
 *     player gets too far away, or after 30 s.</li>
 *     <li><b>Rendering</b>: the client builds a never-ticked dummy of the dead creature from its saved NBT.</li>
 * </ul>
 */
public class CorpseEntity extends Entity {
    public static final int SIZE = 27;
    /** At most this many private loot copies are kept; older emptied copies are dropped first. */
    public static final int MAX_COPIES = 24;
    private static final int MAX_FINISHED = 128;
    public static final int MAX_DRAG_TICKS = 600;
    private static final double MAX_DRAG_DISTANCE = 8.0;

    private static final EntityDataAccessor<String> BODY_TYPE = SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<CompoundTag> BODY = SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.COMPOUND_TAG);
    private static final EntityDataAccessor<Float> BODY_HEIGHT = SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> APPEAR_TICKS = SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> CENTERED = SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DRAGGER = SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> ROLL = SynchedEntityData.defineId(CorpseEntity.class, EntityDataSerializers.FLOAT);

    /** NBT keys stripped from the stored body: big, irrelevant for rendering, or would tint/animate the dummy. */
    private static final String[] STRIP = {"Brain", "Offers", "Gossips", "Inventory", "Items", "Passengers", "ActiveEffects",
            "Attributes", "Leash", "HurtTime", "DeathTime", "HurtByTimestamp", "CustomName", "CustomNameVisible", "UUID",
            "ForgeCaps", "ForgeData", "Motion", "FallDistance", "Fire", "Air", "PortalCooldown", "Tags", "Team", "Xp",
            "RecipeBook", "abilities", "EnderItems", "SelectedItem", "ChestedHorse", "Bees", "Trusted", "Owner", "AngryAt"};

    /** The loot every new looter starts from. Never opened directly. */
    private final SimpleContainer base = new SimpleContainer(SIZE);
    /** Private copies by player, oldest first. */
    private final Map<UUID, LootCopy> copies = new LinkedHashMap<>();
    /** Players whose (emptied or evicted) copy is gone: they get an empty view if they come back. */
    private final Set<UUID> finished = new LinkedHashSet<>();
    private int age;
    private int emptyTicks;
    private float bodyWidth = 0.6f;
    private int dragTicks;

    // client-side
    @Nullable
    private Entity renderDummy;
    private boolean dummyFailed;
    private int lerpSteps;
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private float lerpYRot;

    public CorpseEntity(EntityType<? extends CorpseEntity> type, Level level) {
        super(type, level);
        this.noCulling = true; // the lying body is much bigger than the corpse's box
        this.setMaxUpStep(0.5f);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(BODY_TYPE, "");
        this.entityData.define(BODY, new CompoundTag());
        this.entityData.define(BODY_HEIGHT, 1.8f);
        this.entityData.define(APPEAR_TICKS, 0);
        this.entityData.define(CENTERED, true);
        this.entityData.define(DRAGGER, -1);
        this.entityData.define(ROLL, 0.0f);
    }

    // ------------------------------------------------------------------ setup (server)

    /** Remembers what the body looks like. Call before adding the corpse to the world. */
    public void setBody(LivingEntity dead) {
        String typeId = EntityType.getKey(dead.getType()).toString();
        CompoundTag body;
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
        this.bodyWidth = dead.getBbWidth();
        this.setCustomName(dead.getName().copy());
    }

    /** Ticks during which the body stays invisible (the vanilla death animation is still playing). */
    public void setAppearDelay(int ticks) {
        this.entityData.set(APPEAR_TICKS, ticks);
    }

    /** Stores all equipped armor and weapons from the dead creature into the body's base loot container. */
    public void populateEquipment(LivingEntity dead) {
        net.minecraft.world.entity.EquipmentSlot[] armorSlots = {
                net.minecraft.world.entity.EquipmentSlot.FEET,
                net.minecraft.world.entity.EquipmentSlot.LEGS,
                net.minecraft.world.entity.EquipmentSlot.CHEST,
                net.minecraft.world.entity.EquipmentSlot.HEAD
        };
        for (int i = 0; i < 4; i++) {
            ItemStack stack = dead.getItemBySlot(armorSlots[i]);
            if (!stack.isEmpty()) {
                base.setItem(i, stack.copy());
            }
        }
        ItemStack main = dead.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        if (!main.isEmpty()) base.setItem(4, main.copy());
        ItemStack off = dead.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND);
        if (!off.isEmpty()) base.setItem(5, off.copy());
    }

    /** Adds base loot (what every looter finds); returns the part that didn't fit. */
    public ItemStack addLoot(ItemStack stack) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        for (int i = 6; i < SIZE; i++) {
            ItemStack existing = base.getItem(i);
            if (existing.isEmpty()) {
                base.setItem(i, stack);
                return ItemStack.EMPTY;
            } else if (ItemStack.isSameItemSameTags(existing, stack)) {
                int space = existing.getMaxStackSize() - existing.getCount();
                if (space > 0) {
                    int add = Math.min(space, stack.getCount());
                    existing.grow(add);
                    stack.shrink(add);
                    if (stack.isEmpty()) return ItemStack.EMPTY;
                }
            }
        }
        return base.addItem(stack);
    }

    /** Updates the visual body model NBT when equipped gear is removed by a looter. */
    public void syncEquipmentAppearance(Container container) {
        if (this.level().isClientSide) return;
        CompoundTag body = this.entityData.get(BODY).copy();
        if (body.isEmpty()) return;

        ListTag armor = body.getList("ArmorItems", Tag.TAG_COMPOUND);
        ListTag hands = body.getList("HandItems", Tag.TAG_COMPOUND);

        boolean changed = false;
        // Armor: 0=feet, 1=legs, 2=chest, 3=head
        for (int i = 0; i < 4; i++) {
            if (i < armor.size()) {
                ItemStack current = container.getItem(i);
                CompoundTag slotTag = armor.getCompound(i);
                if (current.isEmpty() && !slotTag.isEmpty()) {
                    armor.set(i, new CompoundTag());
                    changed = true;
                }
            }
        }
        // Hands: 0=mainhand (slot 4), 1=offhand (slot 5)
        for (int i = 0; i < 2; i++) {
            if (i < hands.size()) {
                ItemStack current = container.getItem(4 + i);
                CompoundTag slotTag = hands.getCompound(i);
                if (current.isEmpty() && !slotTag.isEmpty()) {
                    hands.set(i, new CompoundTag());
                    changed = true;
                }
            }
        }

        if (changed) {
            body.put("ArmorItems", armor);
            body.put("HandItems", hands);
            this.entityData.set(BODY, body);
        }
    }

    /** True when the base loot is empty. */
    public boolean isEmptyOfLoot() {
        return base.isEmpty();
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

    /** Tumble angle (degrees) around the body's long axis, changed by hits. */
    public float getRoll() {
        return this.entityData.get(ROLL);
    }

    private void setRoll(float roll) {
        this.entityData.set(ROLL, Mth.clamp(roll, -45.0f, 45.0f));
    }

    public boolean isDragged() {
        return this.entityData.get(DRAGGER) >= 0;
    }

    @Nullable
    private Player getDragger() {
        int id = this.entityData.get(DRAGGER);
        if (id < 0) return null;
        return this.level().getEntity(id) instanceof Player p ? p : null;
    }

    // ------------------------------------------------------------------ per-player loot

    private LootCopy copyFor(Player player) {
        UUID id = player.getUUID();
        LootCopy copy = copies.get(id);
        if (copy != null) return copy;
        copy = new LootCopy(id);
        if (!finished.contains(id)) {
            for (int i = 0; i < base.getContainerSize(); i++) copy.setItem(i, base.getItem(i).copy());
        }
        if (copies.size() >= MAX_COPIES) evictCopy();
        copies.put(id, copy);
        finished.remove(id);
        return copy;
    }

    /** Frees a slot: the oldest emptied copy goes first, then the oldest copy nobody is looking at. */
    private void evictCopy() {
        UUID victim = null;
        for (Map.Entry<UUID, LootCopy> e : copies.entrySet()) {
            if (e.getValue().openers == 0 && e.getValue().isEmpty()) {
                victim = e.getKey();
                break;
            }
        }
        if (victim == null) {
            for (Map.Entry<UUID, LootCopy> e : copies.entrySet()) {
                if (e.getValue().openers == 0) {
                    victim = e.getKey();
                    break;
                }
            }
        }
        if (victim == null) return;
        copies.remove(victim);
        markFinished(victim);
    }

    private void markFinished(UUID id) {
        finished.add(id);
        if (finished.size() > MAX_FINISHED) {
            Iterator<UUID> it = finished.iterator();
            it.next();
            it.remove();
        }
    }

    private int openers() {
        int n = 0;
        for (LootCopy c : copies.values()) n += c.openers;
        return n;
    }

    /** Someone has looted the body and every looter has emptied their copy. */
    private boolean everyoneEmptied() {
        if (copies.isEmpty()) return !finished.isEmpty();
        for (LootCopy c : copies.values()) if (!c.isEmpty()) return false;
        return true;
    }

    // ------------------------------------------------------------------ ticking

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            tickLerp();
            return;
        }
        Player dragger = getDragger();
        if (isDragged() && !tickDrag(dragger)) stopDragging();
        if (!isDragged()) tickPhysics();

        age++;
        if (everyoneEmptied()) emptyTicks++;
        else emptyTicks = 0;
        if (openers() > 0 || isDragged()) return;
        if (emptyTicks > CreaturesConfig.EMPTY_CORPSE_DESPAWN_SECONDS.get() * 20
                || age > CreaturesConfig.CORPSE_DESPAWN_SECONDS.get() * 20) {
            this.discard();
        }
    }

    /** Gravity, block friction, buoyancy. */
    private void tickPhysics() {
        Vec3 v = this.getDeltaMovement();
        if (!this.isNoGravity()) v = v.add(0, -0.04, 0);
        if (this.isInWater()) {
            v = new Vec3(v.x * 0.85, v.y * 0.8 + 0.055, v.z * 0.85); // bodies float
        } else if (this.isInLava()) {
            v = v.multiply(0.5, 0.6, 0.5);
        }
        this.setDeltaMovement(v);
        this.move(MoverType.SELF, v);
        v = this.getDeltaMovement();
        float friction = 0.98f;
        if (this.onGround()) {
            friction = this.level().getBlockState(this.getBlockPosBelowThatAffectsMyMovement()).getBlock().getFriction() * 0.91f;
        }
        double vy = this.onGround() && v.y < 0 ? 0 : v.y * 0.98;
        this.setDeltaMovement(v.x * friction, vy, v.z * friction);
    }

    /** Moves the body in front of its dragger; returns false when it must be dropped. */
    private boolean tickDrag(@Nullable Player player) {
        if (player == null || !player.isAlive() || player.isSpectator() || player.level() != this.level()
                || player.distanceToSqr(this) > MAX_DRAG_DISTANCE * MAX_DRAG_DISTANCE || ++dragTicks > MAX_DRAG_TICKS) {
            return false;
        }
        if (dragTicks % 20 == 0 && !Vitals.consumeStamina(player, dragCost(), false)) {
            if (player instanceof ServerPlayer sp) Notifier.message(sp, Component.translatable("creatures.skycraft.corpse.too_tired"));
            return false;
        }
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        if (flat.lengthSqr() < 1.0E-4) flat = Vec3.directionFromRotation(0, player.getYRot());
        flat = flat.normalize();
        double half = isCentered() ? getBodyHeight() * 0.5 : 0.0;
        double ahead = Math.max(1.5, 0.8 + half);
        Vec3 target = player.position().add(flat.scale(ahead));

        Vec3 to = target.subtract(this.position());
        double hx = to.x * 0.3;
        double hz = to.z * 0.3;
        double h = Math.sqrt(hx * hx + hz * hz);
        if (h > 0.8) {
            hx *= 0.8 / h;
            hz *= 0.8 / h;
        }
        Vec3 v = this.getDeltaMovement();
        double vy = to.y > 0.3 ? Math.min(0.4, to.y * 0.3) : (this.onGround() ? 0 : v.y - 0.04);
        if (this.isInWater()) vy = Math.max(vy, 0.02);
        this.setDeltaMovement(hx, vy, hz);
        this.move(MoverType.SELF, this.getDeltaMovement());

        // the head end points at the player: head direction is (-cos a, 0, sin a) with a = 180 - yaw
        Vec3 toPlayer = player.position().subtract(this.position());
        if (toPlayer.horizontalDistanceSqr() > 0.01) {
            double a = Math.toDegrees(Math.atan2(toPlayer.z, -toPlayer.x));
            this.setYRot(Mth.approachDegrees(this.getYRot(), (float) (180.0 - a), 8.0f));
        }
        return true;
    }

    /** Stamina per second: bigger creatures are heavier to drag. */
    private float dragCost() {
        double volume = bodyWidth * bodyWidth * getBodyHeight();
        return (float) Mth.clamp(3.0 + volume * 4.0, 3.0, 50.0);
    }

    private void startDragging(Player player) {
        for (CorpseEntity other : this.level().getEntitiesOfClass(CorpseEntity.class, player.getBoundingBox().inflate(16),
                c -> c != this && c.entityData.get(DRAGGER) == player.getId())) {
            other.stopDragging();
        }
        this.entityData.set(DRAGGER, player.getId());
        this.setCentered(true);
        this.dragTicks = 0;
        this.setMaxUpStep(1.0f);
        this.playSound(SoundEvents.ARMOR_EQUIP_LEATHER, 0.8f, 0.6f);
    }

    public void stopDragging() {
        if (!isDragged()) return;
        this.entityData.set(DRAGGER, -1);
        this.dragTicks = 0;
        this.setMaxUpStep(0.5f);
        this.setDeltaMovement(this.getDeltaMovement().multiply(0.3, 1.0, 0.3));
    }

    /** Client: smooth movement between the server's position updates. */
    private void tickLerp() {
        if (lerpSteps <= 0) return;
        double x = this.getX() + (lerpX - this.getX()) / lerpSteps;
        double y = this.getY() + (lerpY - this.getY()) / lerpSteps;
        double z = this.getZ() + (lerpZ - this.getZ()) / lerpSteps;
        float yaw = this.getYRot() + Mth.wrapDegrees(lerpYRot - this.getYRot()) / lerpSteps;
        lerpSteps--;
        this.setPos(x, y, z);
        this.setYRot(yaw);
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps, boolean teleport) {
        this.lerpX = x;
        this.lerpY = y;
        this.lerpZ = z;
        this.lerpYRot = yRot;
        this.lerpSteps = teleport ? 1 : Math.max(steps, 3);
    }

    // ------------------------------------------------------------------ interaction

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (player.isShiftKeyDown()) {
            if (!this.level().isClientSide) {
                if (this.entityData.get(DRAGGER) == player.getId()) stopDragging();
                else if (getDragger() == null) startDragging(player);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        if (!this.level().isClientSide) {
            LootCopy copy = copyFor(player);
            player.openMenu(new SimpleMenuProvider((id, inventory, p) -> ChestMenu.threeRows(id, inventory, copy), this.getDisplayName()));
            this.playSound(SoundEvents.ARMOR_EQUIP_LEATHER, 0.6f, 0.9f);
            if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                com.skycraft.core.PlayerData data = com.skycraft.core.SkyData.get(sp);
                if (data != null) {
                    net.minecraft.nbt.ListTag list = com.skycraft.world.WorldData.discovered(data);
                    boolean removed = false;
                    for (int i = list.size() - 1; i >= 0; i--) {
                        net.minecraft.nbt.CompoundTag loc = list.getCompound(i);
                        if ("Your Corpse".equals(loc.getString("name")) || loc.getString("id").startsWith("corpse|")) {
                            list.remove(i);
                            removed = true;
                        }
                    }
                    if (removed) {
                        data.markDirty();
                        com.skycraft.core.PlayerDataEvents.fullSync(sp);
                    }
                }
            }
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

    /** Punching a body shoves it; a sneaking creative-mode player removes it. */
    @Override
    public boolean skipAttackInteraction(Entity attacker) {
        if (!this.level().isClientSide && attacker instanceof Player p) {
            if (p.isCreative() && p.isShiftKeyDown()) this.discard();
            else knock(p.position(), 0.35);
        }
        return true;
    }

    /** Bodies take no damage, but hits and blasts shove them around (explosions also push them directly). */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!this.level().isClientSide && !this.isRemoved() && !isDragged()) {
            Vec3 from = source.getSourcePosition();
            if (from != null) knock(from, 0.2 + Math.min(amount, 12.0f) * 0.04);
        }
        return false;
    }

    private void knock(Vec3 from, double strength) {
        Vec3 dir = new Vec3(this.getX() - from.x, 0, this.getZ() - from.z);
        dir = dir.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0, this.random.nextFloat() * 360f) : dir.normalize();
        double weight = Math.max(0.5, Math.sqrt(bodyWidth * bodyWidth * getBodyHeight()));
        double s = strength / weight;
        this.setDeltaMovement(this.getDeltaMovement().add(dir.x * s, 0.1 + s * 0.4, dir.z * s));
        this.hasImpulse = true;
        setRoll(getRoll() + (this.random.nextFloat() - 0.5f) * 40.0f);
        this.setYRot(this.getYRot() + (this.random.nextFloat() - 0.5f) * 30.0f);
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

    private int renderFailures;

    /** Called by the renderer when drawing the dummy threw: marks failed after repeated errors. */
    public void markRenderFailed() {
        renderFailures++;
        if (renderFailures > 15) {
            dummyFailed = true;
            renderDummy = null;
        }
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

    private static ListTag saveItems(Container container) {
        ListTag items = new ListTag();
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) continue;
            CompoundTag t = new CompoundTag();
            t.putByte("Slot", (byte) i);
            stack.save(t);
            items.add(t);
        }
        return items;
    }

    private static void loadItems(Container container, ListTag items) {
        for (int i = 0; i < items.size(); i++) {
            CompoundTag t = items.getCompound(i);
            int slot = t.getByte("Slot") & 255;
            if (slot < container.getContainerSize()) container.setItem(slot, ItemStack.of(t));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.put("Loot", saveItems(base));
        ListTag copyList = new ListTag();
        for (Map.Entry<UUID, LootCopy> e : copies.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putUUID("Id", e.getKey());
            c.put("Items", saveItems(e.getValue()));
            copyList.add(c);
        }
        tag.put("Copies", copyList);
        ListTag done = new ListTag();
        for (UUID id : finished) {
            CompoundTag c = new CompoundTag();
            c.putUUID("Id", id);
            done.add(c);
        }
        tag.put("Finished", done);
        tag.putString("BodyType", this.entityData.get(BODY_TYPE));
        tag.put("Body", this.entityData.get(BODY));
        tag.putFloat("BodyHeight", getBodyHeight());
        tag.putFloat("BodyWidth", bodyWidth);
        tag.putBoolean("Centered", isCentered());
        tag.putFloat("Roll", getRoll());
        tag.putInt("Age", age);
        tag.putInt("EmptyTicks", emptyTicks);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        loadItems(base, tag.getList("Loot", Tag.TAG_COMPOUND));
        copies.clear();
        ListTag copyList = tag.getList("Copies", Tag.TAG_COMPOUND);
        for (int i = 0; i < copyList.size() && copies.size() < MAX_COPIES; i++) {
            CompoundTag c = copyList.getCompound(i);
            if (!c.hasUUID("Id")) continue;
            LootCopy copy = new LootCopy(c.getUUID("Id"));
            loadItems(copy, c.getList("Items", Tag.TAG_COMPOUND));
            copies.put(copy.owner, copy);
        }
        finished.clear();
        ListTag done = tag.getList("Finished", Tag.TAG_COMPOUND);
        for (int i = 0; i < done.size(); i++) {
            if (done.getCompound(i).hasUUID("Id")) markFinished(done.getCompound(i).getUUID("Id"));
        }
        this.entityData.set(BODY_TYPE, tag.getString("BodyType"));
        this.entityData.set(BODY, tag.getCompound("Body"));
        if (tag.contains("BodyHeight")) this.entityData.set(BODY_HEIGHT, tag.getFloat("BodyHeight"));
        if (tag.contains("BodyWidth")) bodyWidth = tag.getFloat("BodyWidth");
        if (tag.contains("Centered")) setCentered(tag.getBoolean("Centered"));
        setRoll(tag.getFloat("Roll"));
        age = tag.getInt("Age");
        emptyTicks = tag.getInt("EmptyTicks");
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }

    /** One player's private view of the loot; only valid for its owner, while the corpse exists and is close. */
    private class LootCopy extends SimpleContainer {
        final UUID owner;
        int openers;

        LootCopy(UUID owner) {
            super(SIZE);
            this.owner = owner;
            this.addListener(container -> syncEquipmentAppearance(container));
        }

        @Override
        public void setChanged() {
            super.setChanged();
            syncEquipmentAppearance(this);
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            ItemStack stack = super.removeItem(slot, amount);
            syncEquipmentAppearance(this);
            return stack;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            ItemStack stack = super.removeItemNoUpdate(slot);
            syncEquipmentAppearance(this);
            return stack;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            super.setItem(slot, stack);
            syncEquipmentAppearance(this);
        }

        @Override
        public boolean stillValid(Player player) {
            return CorpseEntity.this.isAlive() && player.getUUID().equals(owner) && player.distanceToSqr(CorpseEntity.this) < 64.0;
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
