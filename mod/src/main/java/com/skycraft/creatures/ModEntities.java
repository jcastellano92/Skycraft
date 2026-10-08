package com.skycraft.creatures;

import com.skycraft.Skycraft;
import com.skycraft.creatures.entity.BanditChiefEntity;
import com.skycraft.creatures.entity.BanditEntity;
import com.skycraft.creatures.entity.CorpseEntity;
import com.skycraft.creatures.entity.DragonEntity;
import com.skycraft.creatures.entity.DraugrDeathlordEntity;
import com.skycraft.creatures.entity.DraugrEntity;
import com.skycraft.creatures.entity.GiantEntity;
import com.skycraft.creatures.entity.GuardEntity;
import com.skycraft.creatures.entity.SkeeverEntity;
import com.skycraft.creatures.entity.TrollEntity;
import com.skycraft.creatures.world.BanditCampFeature;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

/** Entity types, spawn eggs and world-gen features of the creatures module. Registry ids are a cross-module contract. */
public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, Skycraft.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, Skycraft.MODID);
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, Skycraft.MODID);

    public static final RegistryObject<EntityType<BanditEntity>> BANDIT = ENTITIES.register("bandit",
            () -> EntityType.Builder.<BanditEntity>of(BanditEntity::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.95f).clientTrackingRange(8).build(id("bandit")));
    public static final RegistryObject<EntityType<BanditChiefEntity>> BANDIT_CHIEF = ENTITIES.register("bandit_chief",
            () -> EntityType.Builder.<BanditChiefEntity>of(BanditChiefEntity::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.95f).clientTrackingRange(8).build(id("bandit_chief")));
    public static final RegistryObject<EntityType<DraugrEntity>> DRAUGR = ENTITIES.register("draugr",
            () -> EntityType.Builder.<DraugrEntity>of(DraugrEntity::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.95f).clientTrackingRange(8).build(id("draugr")));
    public static final RegistryObject<EntityType<DraugrDeathlordEntity>> DRAUGR_DEATHLORD = ENTITIES.register("draugr_deathlord",
            () -> EntityType.Builder.<DraugrDeathlordEntity>of(DraugrDeathlordEntity::new, MobCategory.MONSTER)
                    .sized(0.7f, 2.1f).clientTrackingRange(10).build(id("draugr_deathlord")));
    public static final RegistryObject<EntityType<SkeeverEntity>> SKEEVER = ENTITIES.register("skeever",
            () -> EntityType.Builder.<SkeeverEntity>of(SkeeverEntity::new, MobCategory.MONSTER)
                    .sized(0.7f, 0.55f).clientTrackingRange(8).build(id("skeever")));
    public static final RegistryObject<EntityType<TrollEntity>> TROLL = ENTITIES.register("troll",
            () -> EntityType.Builder.<TrollEntity>of(TrollEntity::new, MobCategory.MONSTER)
                    .sized(1.4f, 2.5f).clientTrackingRange(10).build(id("troll")));
    public static final RegistryObject<EntityType<GiantEntity>> GIANT = ENTITIES.register("giant",
            () -> EntityType.Builder.<GiantEntity>of(GiantEntity::new, MobCategory.MONSTER)
                    .sized(1.5f, 4.9f).clientTrackingRange(10).build(id("giant")));
    public static final RegistryObject<EntityType<DragonEntity>> DRAGON = ENTITIES.register("dragon",
            () -> EntityType.Builder.<DragonEntity>of(DragonEntity::new, MobCategory.MONSTER)
                    .sized(3.0f, 2.5f).clientTrackingRange(16).updateInterval(2).build(id("dragon")));
    public static final RegistryObject<EntityType<GuardEntity>> GUARD = ENTITIES.register("guard",
            () -> EntityType.Builder.<GuardEntity>of(GuardEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.95f).clientTrackingRange(10).build(id("guard")));
    public static final RegistryObject<EntityType<CorpseEntity>> CORPSE = ENTITIES.register("corpse",
            () -> EntityType.Builder.<CorpseEntity>of(CorpseEntity::new, MobCategory.MISC)
                    .sized(1.0f, 0.5f).fireImmune().clientTrackingRange(8).updateInterval(10).build(id("corpse")));

    public static final RegistryObject<Item> BANDIT_EGG = egg("bandit", BANDIT, 0x5A3E2B, 0xB08A5A);
    public static final RegistryObject<Item> BANDIT_CHIEF_EGG = egg("bandit_chief", BANDIT_CHIEF, 0x3B2A1E, 0xC9C9C9);
    public static final RegistryObject<Item> DRAUGR_EGG = egg("draugr", DRAUGR, 0x5E6A70, 0x6FD3FF);
    public static final RegistryObject<Item> DRAUGR_DEATHLORD_EGG = egg("draugr_deathlord", DRAUGR_DEATHLORD, 0x2D3236, 0x3FB8FF);
    public static final RegistryObject<Item> SKEEVER_EGG = egg("skeever", SKEEVER, 0x6B5A4A, 0xD9A08C);
    public static final RegistryObject<Item> TROLL_EGG = egg("troll", TROLL, 0xC8C2B4, 0x5A4E44);
    public static final RegistryObject<Item> GIANT_EGG = egg("giant", GIANT, 0xD8C7B0, 0x7A5A3A);
    public static final RegistryObject<Item> DRAGON_EGG = egg("dragon", DRAGON, 0x5B4A32, 0xB89A5E);
    public static final RegistryObject<Item> GUARD_EGG = egg("guard", GUARD, 0xC8A23A, 0x8C8C8C);

    public static final RegistryObject<Feature<NoneFeatureConfiguration>> BANDIT_CAMP = FEATURES.register("bandit_camp",
            () -> new BanditCampFeature(NoneFeatureConfiguration.CODEC));

    private ModEntities() {}

    public static void init(IEventBus modBus) {
        ENTITIES.register(modBus);
        ITEMS.register(modBus);
        FEATURES.register(modBus);
    }

    private static String id(String path) {
        return Skycraft.MODID + ":" + path;
    }

    private static RegistryObject<Item> egg(String name, Supplier<? extends EntityType<? extends Mob>> type, int bg, int fg) {
        return ITEMS.register(name + "_spawn_egg", () -> new ForgeSpawnEggItem(type, bg, fg, new Item.Properties()));
    }
}
