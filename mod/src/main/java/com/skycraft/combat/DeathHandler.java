package com.skycraft.combat;

import com.skycraft.Skycraft;
import com.skycraft.core.PlayerData;
import com.skycraft.core.SkyData;
import com.skycraft.quest.QuestItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerWakeUpEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Handles Skyrim player death rules, kept equipment and quest items, respawn points, and legendary item tooltips.
 */
@Mod.EventBusSubscriber(modid = Skycraft.MODID)
public final class DeathHandler {
    private DeathHandler() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PlayerData data = SkyData.get(player);
        if (data == null) return;

        CompoundTag keptTag = data.module("death_kept");
        ListTag list = new ListTag();

        // 1. Equipped gear: head, chest, legs, feet, mainhand, offhand
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = player.getItemBySlot(slot);
            if (!stack.isEmpty()) {
                CompoundTag entry = new CompoundTag();
                entry.putString("slot", slot.getName());
                entry.put("item", stack.save(new CompoundTag()));
                list.add(entry);
            }
        }

        // 2. Quest items in backpack
        Inventory inv = player.getInventory();
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            if (i == inv.selected) continue; // main hand already saved above
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && QuestItems.isQuestItem(stack)) {
                CompoundTag entry = new CompoundTag();
                entry.putInt("inv_slot", i);
                entry.put("item", stack.save(new CompoundTag()));
                list.add(entry);
            }
        }

        // 3. Place "Your Corpse" death marker on compass and map
        CompoundTag corpseLoc = new CompoundTag();
        String corpseId = "corpse|" + player.getStringUUID();
        corpseLoc.putString("id", corpseId);
        corpseLoc.putString("name", "Your Corpse");
        corpseLoc.putString("type", "tomb");
        corpseLoc.putInt("x", player.getBlockX());
        corpseLoc.putInt("y", player.getBlockY());
        corpseLoc.putInt("z", player.getBlockZ());
        corpseLoc.putString("dim", player.level().dimension().location().toString());
        corpseLoc.putLong("t", player.level().getGameTime());
        corpseLoc.putInt("ax", player.getBlockX());
        corpseLoc.putInt("ay", player.getBlockY());
        corpseLoc.putInt("az", player.getBlockZ());
        corpseLoc.putIntArray("box", new int[]{player.getBlockX() - 2, player.getBlockY() - 2, player.getBlockZ() - 2,
                player.getBlockX() + 2, player.getBlockY() + 2, player.getBlockZ() + 2});
        corpseLoc.putString("structure", "skycraft:player_corpse");

        ListTag discovered = com.skycraft.world.WorldData.discovered(data);
        for (int i = discovered.size() - 1; i >= 0; i--) {
            if (discovered.getCompound(i).getString("name").equals("Your Corpse")) {
                discovered.remove(i);
            }
        }
        discovered.add(corpseLoc);

        keptTag.put("items", list);
        data.markDirty();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PlayerData data = SkyData.get(player);
        if (data == null) return;
        CompoundTag keptTag = data.module("death_kept");
        if (!keptTag.contains("items", Tag.TAG_LIST)) return;

        ListTag list = keptTag.getList("items", Tag.TAG_COMPOUND);
        List<ItemStack> toKeep = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            ItemStack s = ItemStack.of(entry.getCompound("item"));
            if (!s.isEmpty()) toKeep.add(s);
        }

        Iterator<ItemEntity> it = event.getDrops().iterator();
        while (it.hasNext() && !toKeep.isEmpty()) {
            ItemEntity drop = it.next();
            ItemStack dropStack = drop.getItem();
            for (int i = 0; i < toKeep.size(); i++) {
                ItemStack keep = toKeep.get(i);
                if (dropStack.getItem() == keep.getItem()) {
                    it.remove();
                    toKeep.remove(i);
                    break;
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath() || !(event.getEntity() instanceof ServerPlayer newPlayer)) return;
        PlayerData data = SkyData.get(newPlayer);
        if (data == null) return;

        // Reset negative status effects and cure diseases on respawn
        newPlayer.removeAllEffects();
        com.skycraft.survival.Diseases.cureAll(newPlayer);

        CompoundTag keptTag = data.module("death_kept");
        if (!keptTag.contains("items", Tag.TAG_LIST)) return;

        ListTag list = keptTag.getList("items", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            ItemStack stack = ItemStack.of(entry.getCompound("item"));
            if (stack.isEmpty()) continue;
            if (entry.contains("slot")) {
                EquipmentSlot slot = EquipmentSlot.byName(entry.getString("slot"));
                newPlayer.setItemSlot(slot, stack);
            } else if (entry.contains("inv_slot")) {
                int slot = entry.getInt("inv_slot");
                if (slot >= 0 && slot < newPlayer.getInventory().getContainerSize()
                        && newPlayer.getInventory().getItem(slot).isEmpty()) {
                    newPlayer.getInventory().setItem(slot, stack);
                } else {
                    newPlayer.getInventory().add(stack);
                }
            } else {
                newPlayer.getInventory().add(stack);
            }
        }

        keptTag.remove("items");
        data.markDirty();
    }

    @SubscribeEvent
    public static void onWakeUp(PlayerWakeUpEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BlockPos bed = player.getSleepingPos().orElse(player.blockPosition());
            RespawnPoints.set(player, player.level().dimension(), bed);
        }
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        LegendaryItem.Prefix prefix = LegendaryItem.getPrefix(stack);
        if (prefix != null) {
            event.getToolTip().add(Component.literal("★ " + prefix.displayName + ": " + prefix.description)
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
        }
    }
}

