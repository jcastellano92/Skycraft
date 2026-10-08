package com.skycraft.core;

import com.skycraft.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Septims. Gold lives in a weightless wallet on {@link PlayerData}; physical Septim coin items are deposited into
 * the wallet automatically when picked up and can be withdrawn with {@code /withdraw}.
 */
public final class Currency {
    private Currency() {}

    public static long balance(Player player) {
        return SkyData.get(player).getGold();
    }

    public static boolean canAfford(Player player, long amount) {
        return balance(player) >= amount;
    }

    /** Removes gold if possible. Returns false (and changes nothing) if the player can't afford it. */
    public static boolean take(Player player, long amount) {
        PlayerData data = SkyData.get(player);
        if (amount < 0 || data.getGold() < amount) return false;
        data.setGold(data.getGold() - amount);
        return true;
    }

    public static void give(Player player, long amount) {
        if (amount <= 0) return;
        PlayerData data = SkyData.get(player);
        data.setGold(data.getGold() + amount);
        data.addStat("gold_found", (int) Math.min(Integer.MAX_VALUE, amount));
        if (player instanceof ServerPlayer sp) {
            Notifier.message(sp, Component.translatable("message.skycraft.gold_added", amount));
        }
    }

    /** Creates physical gold: loose Septims for small amounts, a coin purse holding the amount otherwise. */
    public static ItemStack coins(long amount) {
        if (amount <= 64) return new ItemStack(ModItems.SEPTIM.get(), (int) Math.max(1, amount));
        ItemStack purse = new ItemStack(ModItems.COIN_PURSE.get());
        purse.getOrCreateTag().putLong("gold", amount);
        return purse;
    }

    /** Gold value of a coin stack (Septims or purse), 0 for anything else. */
    public static long valueOf(ItemStack stack) {
        if (stack.is(ModItems.SEPTIM.get())) return stack.getCount();
        if (stack.is(ModItems.COIN_PURSE.get())) {
            long v = stack.getTag() != null ? stack.getTag().getLong("gold") : 0;
            return (v <= 0 ? 25 : v) * stack.getCount();
        }
        return 0;
    }
}
