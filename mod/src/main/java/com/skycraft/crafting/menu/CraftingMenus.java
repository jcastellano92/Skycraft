package com.skycraft.crafting.menu;

import com.skycraft.Skycraft;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Menu types of the smithing module. */
public final class CraftingMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, Skycraft.MODID);

    public static final RegistryObject<MenuType<StationMenu>> STATION = MENUS.register("smithing_station",
            () -> IForgeMenuType.create(StationMenu::fromNetwork));

    private CraftingMenus() {}
}
