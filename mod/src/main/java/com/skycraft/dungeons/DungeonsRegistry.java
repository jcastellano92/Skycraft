package com.skycraft.dungeons;

import com.skycraft.Skycraft;
import com.skycraft.dungeons.entity.DwarvenCenturion;
import com.skycraft.dungeons.entity.DwarvenSphere;
import com.skycraft.dungeons.entity.DwarvenSpider;
import com.skycraft.dungeons.world.DungeonPiece;
import com.skycraft.dungeons.world.DungeonStructure;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** Registry objects of the dungeons module: the dungeon structure type and piece type, automatons and their eggs. */
public final class DungeonsRegistry {
    private DungeonsRegistry() {}

    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, Skycraft.MODID);
    public static final DeferredRegister<StructurePieceType> PIECE_TYPES = DeferredRegister.create(Registries.STRUCTURE_PIECE, Skycraft.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, Skycraft.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, Skycraft.MODID);

    public static final RegistryObject<StructureType<DungeonStructure>> DUNGEON_STRUCTURE =
            STRUCTURE_TYPES.register("dungeon", () -> () -> DungeonStructure.CODEC);
    public static final RegistryObject<StructurePieceType> DUNGEON_PIECE =
            PIECE_TYPES.register("dungeon_piece", () -> (StructurePieceType) DungeonPiece::new);

    public static final RegistryObject<EntityType<DwarvenSpider>> DWARVEN_SPIDER = ENTITIES.register("dwarven_spider",
            () -> EntityType.Builder.<DwarvenSpider>of(DwarvenSpider::new, MobCategory.MONSTER)
                    .sized(0.9f, 0.65f).fireImmune().clientTrackingRange(8).build(id("dwarven_spider")));
    public static final RegistryObject<EntityType<DwarvenSphere>> DWARVEN_SPHERE = ENTITIES.register("dwarven_sphere",
            () -> EntityType.Builder.<DwarvenSphere>of(DwarvenSphere::new, MobCategory.MONSTER)
                    .sized(0.9f, 1.5f).fireImmune().clientTrackingRange(8).build(id("dwarven_sphere")));
    public static final RegistryObject<EntityType<DwarvenCenturion>> DWARVEN_CENTURION = ENTITIES.register("dwarven_centurion",
            () -> EntityType.Builder.<DwarvenCenturion>of(DwarvenCenturion::new, MobCategory.MONSTER)
                    .sized(1.6f, 3.6f).fireImmune().clientTrackingRange(10).build(id("dwarven_centurion")));

    public static final RegistryObject<Item> DWARVEN_SPIDER_EGG = ITEMS.register("dwarven_spider_spawn_egg",
            () -> new ForgeSpawnEggItem(DWARVEN_SPIDER, 0xB0803A, 0x4FD8FF, new Item.Properties()));
    public static final RegistryObject<Item> DWARVEN_SPHERE_EGG = ITEMS.register("dwarven_sphere_spawn_egg",
            () -> new ForgeSpawnEggItem(DWARVEN_SPHERE, 0xA87432, 0x3A3A3A, new Item.Properties()));
    public static final RegistryObject<Item> DWARVEN_CENTURION_EGG = ITEMS.register("dwarven_centurion_spawn_egg",
            () -> new ForgeSpawnEggItem(DWARVEN_CENTURION, 0x8C5E2A, 0xFFB040, new Item.Properties()));

    /** {@code #skycraft:automatons}: every Dwemer automaton (dungeons owns). */
    public static final TagKey<EntityType<?>> AUTOMATONS = TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(Skycraft.MODID, "automatons"));
    /** {@code #skycraft:dungeon_bosses}: boss creatures this module adds (the Centurion). */
    public static final TagKey<EntityType<?>> DUNGEON_BOSSES = TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(Skycraft.MODID, "dungeon_bosses"));

    static void init(IEventBus modBus) {
        STRUCTURE_TYPES.register(modBus);
        PIECE_TYPES.register(modBus);
        ENTITIES.register(modBus);
        ITEMS.register(modBus);
    }

    private static String id(String path) {
        return Skycraft.MODID + ":" + path;
    }
}
