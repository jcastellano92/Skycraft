package com.skycraft.crime;

import com.skycraft.Skycraft;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Crime items: the lockpick. */
public final class CrimeItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Skycraft.MODID);

    public static final RegistryObject<Item> LOCKPICK = ITEMS.register("lockpick", () -> new LockpickItem(new Item.Properties().stacksTo(64)));
    public static final RegistryObject<Item> JAIL_KEY = ITEMS.register("jail_key", () -> new Item(new Item.Properties().stacksTo(1)));

    private CrimeItems() {}

    static void init(IEventBus modBus) {
        ITEMS.register(modBus);
    }

    public static class LockpickItem extends Item {
        public LockpickItem(Properties props) {
            super(props);
        }

        @Override
        public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("item.skycraft.lockpick.desc").withStyle(ChatFormatting.GRAY));
        }
    }
}
