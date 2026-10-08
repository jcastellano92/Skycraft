package com.skycraft.registry;

import com.skycraft.Skycraft;
import com.skycraft.core.Currency;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Items shared by every module (currency). Module-specific items live in each module's own registry. */
public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Skycraft.MODID);

    public static final RegistryObject<Item> SEPTIM = ITEMS.register("septim", () -> new CoinItem(new Item.Properties()));
    public static final RegistryObject<Item> COIN_PURSE = ITEMS.register("coin_purse",
            () -> new CoinItem(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)));

    private ModItems() {}

    public static void init(IEventBus modBus) {
        ITEMS.register(modBus);
    }

    /** Coins are deposited into the wallet when picked up; using them in hand deposits them too. */
    public static class CoinItem extends Item {
        public CoinItem(Properties props) {
            super(props);
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (!level.isClientSide) {
                long value = Currency.valueOf(stack);
                Currency.give(player, value);
                stack.setCount(0);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        @Override
        public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("tooltip.skycraft.gold_value", Currency.valueOf(stack)).withStyle(ChatFormatting.GOLD));
        }
    }
}
