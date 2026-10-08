package com.skycraft.survival;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

/**
 * Skyrim food and drink, cooking, inns and renting rooms, diseases and shrines, regenerating ore veins.
 *
 * <ul>
 *     <li>Food & drink: {@link SurvivalRegistry} items ({@link com.skycraft.survival.item.SkyFoodItem}), crops, the
 *     {@code skycraft:cooking_pot} ({@link com.skycraft.survival.cooking.CookingRecipes}).</li>
 *     <li>Inns: {@link com.skycraft.survival.inn.Innkeepers} (one per settlement of the roads registry) and
 *     {@link SurvivalDialogue}.</li>
 *     <li>Diseases (contract 24): {@link Diseases}; cures by {@code skycraft:cure_disease}, {@link Shrines} and priests.</li>
 *     <li>Ore veins: {@link OreVeins} and {@code skycraft:depleted_ore}.</li>
 * </ul>
 */
public final class SurvivalModule {
    private SurvivalModule() {}

    /** Registers this module's DeferredRegisters and mod-bus listeners. Called from the mod constructor. */
    public static void init(IEventBus modBus) {
        SurvivalEffects.init(modBus);
        SurvivalRegistry.init(modBus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SurvivalConfig.SPEC, "skycraft-survival.toml");
        SurvivalDialogue.register();
        SurvivalEvents.register();
    }

    /** Registers this module's packets via {@link com.skycraft.network.SkyNetwork#register}. Called once at startup. */
    public static void registerPackets() {
        SurvivalPackets.register();
    }
}
