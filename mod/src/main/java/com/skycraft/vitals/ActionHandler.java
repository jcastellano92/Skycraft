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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
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
            case Action.TAKE_WORLD_ITEM -> handleTakeWorldItem(player, arg);
            case Action.SHIELD_BASH -> handleShieldBash(player);
            default -> {
            }
        }
    }

    private static void handleShieldBash(ServerPlayer player) {
        if (!Vitals.consumeStamina(player, 15f, false)) return;
        net.minecraft.server.level.ServerLevel level = player.serverLevel();
        level.playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.SHIELD_BLOCK, net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 0.9f);
        net.minecraft.world.phys.Vec3 look = player.getViewVector(1.0f);
        net.minecraft.world.phys.AABB box = player.getBoundingBox().expandTowards(look.scale(3.0)).inflate(1.0);
        for (net.minecraft.world.entity.LivingEntity target : level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, box, e -> e != player && e.isAlive())) {
            if (player.distanceTo(target) < 3.0) {
                float bashDmg = 4.0f + (com.skycraft.perk.Perks.has(player, "block.deadly_bash") ? 6.0f : 0f);
                target.hurt(player.damageSources().playerAttack(player), bashDmg);
                target.knockback(0.6, -look.x, -look.z);
                break;
            }
        }
    }

    private static void handleTakeWorldItem(ServerPlayer player, int entityId) {
        net.minecraft.world.entity.Entity entity = player.serverLevel().getEntity(entityId);
        if (!(entity instanceof net.minecraft.world.entity.item.ItemEntity itemEntity) || !itemEntity.isAlive()) return;
        if (player.distanceToSqr(itemEntity) > 25.0) return; // within 5 blocks

        ItemStack stack = itemEntity.getItem();
        if (stack.isEmpty()) return;

        // Check if item is currency (Septims)
        long coinVal = com.skycraft.core.Currency.valueOf(stack);
        if (coinVal > 0) {
            com.skycraft.core.Currency.give(player, coinVal);
            player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.6f, 1.2f);
            itemEntity.discard();
            return;
        }

        // Check ownership & theft
        boolean owned = com.skycraft.crime.Ownership.isOwnedByOther(player, itemEntity);
        if (owned) {
            com.skycraft.crime.Bounty.markStolen(stack);
            com.skycraft.crime.Bounty.increment(player, "items_stolen", stack.getCount());
            net.minecraft.world.entity.LivingEntity witness = com.skycraft.crime.Crimes.findWitness(player, null);
            if (witness != null) {
                int bounty = (int) Math.max(com.skycraft.crime.Bounty.MIN_THEFT,
                        Math.min(100000, com.skycraft.economy.ItemValues.get(stack) * stack.getCount() / 2));
                com.skycraft.crime.Theft.handleWitnessedTheft(player, witness, itemEntity.blockPosition(), bounty);
            }
        }

        // Put item into storage slots (slots 9-35) or non-active slots so it NEVER equips into the active hand!
        int origCount = stack.getCount();
        addToBags(player, stack);

        if (stack.getCount() < origCount) {
            player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.5f, 1.1f);
            if (stack.isEmpty()) {
                itemEntity.discard();
            } else {
                itemEntity.setItem(stack);
            }
            player.inventoryMenu.broadcastChanges();
        }
    }

    /**
     * Routes items directly into backpack slots (9..35) or inactive hotbar slots.
     * Auto-deposits currency directly into the player's gold wallet.
     * Never equips into the player's active held hand slot unless completely full.
     */
    public static boolean addToBags(Player player, ItemStack stack) {
        if (stack.isEmpty()) return true;

        // Auto-deposit currency directly into gold wallet
        long coinVal = com.skycraft.core.Currency.valueOf(stack);
        if (coinVal > 0) {
            com.skycraft.core.Currency.give(player, coinVal);
            stack.setCount(0);
            return true;
        }

        net.minecraft.world.entity.player.Inventory inv = player.getInventory();
        int activeSlot = inv.selected;

        // 1. Try to merge into existing matching stacks in storage (9..35)
        for (int i = 9; i < 36; i++) {
            ItemStack inSlot = inv.getItem(i);
            if (ItemStack.isSameItemSameTags(inSlot, stack)) {
                int space = inSlot.getMaxStackSize() - inSlot.getCount();
                if (space > 0) {
                    int move = Math.min(space, stack.getCount());
                    inSlot.grow(move);
                    stack.shrink(move);
                    if (stack.isEmpty()) return true;
                }
            }
        }

        // 2. Try to merge into existing matching stacks in hotbar (except active slot)
        for (int i = 0; i < 9; i++) {
            if (i == activeSlot) continue;
            ItemStack inSlot = inv.getItem(i);
            if (ItemStack.isSameItemSameTags(inSlot, stack)) {
                int space = inSlot.getMaxStackSize() - inSlot.getCount();
                if (space > 0) {
                    int move = Math.min(space, stack.getCount());
                    inSlot.grow(move);
                    stack.shrink(move);
                    if (stack.isEmpty()) return true;
                }
            }
        }

        // 3. Try empty storage slots (9..35)
        if (!stack.isEmpty()) {
            for (int i = 9; i < 36; i++) {
                if (inv.getItem(i).isEmpty()) {
                    inv.setItem(i, stack.copy());
                    stack.setCount(0);
                    return true;
                }
            }
        }

        // 4. Try empty hotbar slots (except active slot)
        if (!stack.isEmpty()) {
            for (int i = 0; i < 9; i++) {
                if (i == activeSlot) continue;
                if (inv.getItem(i).isEmpty()) {
                    inv.setItem(i, stack.copy());
                    stack.setCount(0);
                    return true;
                }
            }
        }

        // 5. Fallback only if all other 35 slots are completely full
        if (!stack.isEmpty()) {
            return inv.add(stack);
        }
        return true;
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

    public static void handleContainerTake(ServerPlayer player, int containerId, int slotIndex, boolean all) {
        if (player == null || !player.isAlive()) return;
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null || menu == player.inventoryMenu || menu.containerId != containerId) return;

        int containerSlotCount = (menu instanceof ChestMenu cm)
                ? cm.getRowCount() * 9
                : Math.max(0, menu.slots.size() - 36);
        if (containerSlotCount <= 0) return;

        boolean takenAny = false;
        if (all) {
            for (int i = 0; i < containerSlotCount; i++) {
                Slot slot = menu.getSlot(i);
                if (slot != null && slot.hasItem()) {
                    ItemStack stack = slot.getItem();
                    int countBefore = stack.getCount();
                    addToBags(player, stack);
                    if (stack.getCount() < countBefore) {
                        takenAny = true;
                        if (stack.isEmpty()) {
                            slot.set(ItemStack.EMPTY);
                        } else {
                            slot.setChanged();
                        }
                    }
                }
            }
        } else {
            if (slotIndex >= 0 && slotIndex < containerSlotCount) {
                Slot slot = menu.getSlot(slotIndex);
                if (slot != null && slot.hasItem()) {
                    ItemStack stack = slot.getItem();
                    int countBefore = stack.getCount();
                    addToBags(player, stack);
                    if (stack.getCount() < countBefore) {
                        takenAny = true;
                        if (stack.isEmpty()) {
                            slot.set(ItemStack.EMPTY);
                        } else {
                            slot.setChanged();
                        }
                    }
                }
            }
        }

        if (takenAny) {
            player.level().playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.5f, 1.2f);
            menu.broadcastChanges();
            player.inventoryMenu.broadcastChanges();
            player.getInventory().setChanged();
        }
    }

    public static void handleContainerStore(ServerPlayer player, int containerId, int slotIndex, boolean all) {
        if (player == null || !player.isAlive()) return;
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null || menu == player.inventoryMenu || menu.containerId != containerId) return;

        int containerSlotCount = (menu instanceof ChestMenu cm)
                ? cm.getRowCount() * 9
                : Math.max(0, menu.slots.size() - 36);
        if (containerSlotCount <= 0) return;

        boolean storedAny = false;
        if (all) {
            for (int i = containerSlotCount; i < menu.slots.size(); i++) {
                Slot pSlot = menu.getSlot(i);
                if (pSlot != null && pSlot.hasItem()) {
                    ItemStack stack = pSlot.getItem();
                    // Don't store actively held mainhand or offhand item when storing all
                    if (stack == player.getMainHandItem() || stack == player.getOffhandItem()) {
                        continue;
                    }
                    if (storeIntoContainerSlots(menu, containerSlotCount, stack)) {
                        storedAny = true;
                        if (stack.isEmpty()) {
                            pSlot.set(ItemStack.EMPTY);
                        } else {
                            pSlot.setChanged();
                        }
                    }
                }
            }
        } else {
            if (slotIndex >= containerSlotCount && slotIndex < menu.slots.size()) {
                Slot pSlot = menu.getSlot(slotIndex);
                if (pSlot != null && pSlot.hasItem()) {
                    ItemStack stack = pSlot.getItem();
                    if (storeIntoContainerSlots(menu, containerSlotCount, stack)) {
                        storedAny = true;
                        if (stack.isEmpty()) {
                            pSlot.set(ItemStack.EMPTY);
                        } else {
                            pSlot.setChanged();
                        }
                    }
                }
            }
        }

        if (storedAny) {
            player.level().playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4f, 0.9f);
            menu.broadcastChanges();
            player.inventoryMenu.broadcastChanges();
            player.getInventory().setChanged();
        }
    }

    private static boolean storeIntoContainerSlots(AbstractContainerMenu menu, int containerSlotCount, ItemStack stack) {
        if (stack.isEmpty()) return false;
        int orig = stack.getCount();
        // 1. Merge into matching existing stacks in container
        for (int i = 0; i < containerSlotCount; i++) {
            Slot s = menu.getSlot(i);
            if (s != null && s.hasItem() && s.mayPlace(stack)) {
                ItemStack inSlot = s.getItem();
                if (ItemStack.isSameItemSameTags(inSlot, stack)) {
                    int space = Math.min(s.getMaxStackSize(stack), inSlot.getMaxStackSize()) - inSlot.getCount();
                    if (space > 0) {
                        int move = Math.min(space, stack.getCount());
                        inSlot.grow(move);
                        stack.shrink(move);
                        s.setChanged();
                        if (stack.isEmpty()) return true;
                    }
                }
            }
        }
        // 2. Put into empty container slots
        for (int i = 0; i < containerSlotCount; i++) {
            Slot s = menu.getSlot(i);
            if (s != null && !s.hasItem() && s.mayPlace(stack)) {
                int max = Math.min(s.getMaxStackSize(stack), stack.getMaxStackSize());
                int move = Math.min(max, stack.getCount());
                ItemStack placed = stack.split(move);
                s.set(placed);
                s.setChanged();
                if (stack.isEmpty()) return true;
            }
        }
        return stack.getCount() < orig;
    }
}
