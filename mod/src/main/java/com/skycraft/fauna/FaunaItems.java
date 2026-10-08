package com.skycraft.fauna;

import com.skycraft.Skycraft;
import com.skycraft.crafting.arcane.ArcaneRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Animal parts and insect ingredients of the fauna module. Alchemy ingredients are edible (Skyrim style, see
 * {@link ArcaneRegistry#INGREDIENT_FOOD}); their effects live in arcane's ingredient table.
 */
public final class FaunaItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, Skycraft.MODID);

    // ---------------------------------------------------------------- animal parts
    public static final RegistryObject<Item> SMALL_ANTLERS = ingredient("small_antlers");
    public static final RegistryObject<Item> LARGE_ANTLERS = ingredient("large_antlers");
    public static final RegistryObject<Item> SABRE_CAT_TOOTH = ingredient("sabre_cat_tooth");
    public static final RegistryObject<Item> MUDCRAB_CHITIN = ingredient("mudcrab_chitin");
    public static final RegistryObject<Item> SLAUGHTERFISH_SCALES = ingredient("slaughterfish_scales");
    public static final RegistryObject<Item> HORKER_TUSK = ITEMS.register("horker_tusk", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> MAMMOTH_TUSK = ITEMS.register("mammoth_tusk",
            () -> new Item(new Item.Properties().rarity(Rarity.UNCOMMON)));

    // ---------------------------------------------------------------- insects (caught by hand)
    public static final RegistryObject<Item> MONARCH_WING = ingredient("monarch_wing");
    public static final RegistryObject<Item> BLUE_BUTTERFLY_WING = ingredient("blue_butterfly_wing");
    public static final RegistryObject<Item> BLUE_DARTWING = ingredient("blue_dartwing");
    public static final RegistryObject<Item> TORCHBUG_THORAX = ingredient("torchbug_thorax");
    public static final RegistryObject<Item> LUNA_MOTH_WING = ingredient("luna_moth_wing");

    private FaunaItems() {}

    private static RegistryObject<Item> ingredient(String id) {
        return ITEMS.register(id, () -> new Item(new Item.Properties().food(ArcaneRegistry.INGREDIENT_FOOD)));
    }

    public static void init(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
