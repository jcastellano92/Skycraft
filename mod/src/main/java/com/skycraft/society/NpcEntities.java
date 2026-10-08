package com.skycraft.society;

import com.skycraft.Skycraft;
import com.skycraft.society.entity.NpcEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Entity types of the society module. {@code skycraft:npc} (contract 22) holds the civilian roles; the soldiers and
 * villains use {@code skycraft:npc_fighter} (same class) so the crime module never counts their deaths as murders.
 */
public final class NpcEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, Skycraft.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, Skycraft.MODID);

    public static final RegistryObject<EntityType<NpcEntity>> NPC = ENTITIES.register("npc",
            () -> EntityType.Builder.<NpcEntity>of(NpcEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.95f).clientTrackingRange(10).build(Skycraft.MODID + ":npc"));
    public static final RegistryObject<EntityType<NpcEntity>> NPC_FIGHTER = ENTITIES.register("npc_fighter",
            () -> EntityType.Builder.<NpcEntity>of(NpcEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.95f).clientTrackingRange(10).build(Skycraft.MODID + ":npc_fighter"));

    public static final RegistryObject<Item> NPC_EGG = ITEMS.register("npc_spawn_egg",
            () -> new ForgeSpawnEggItem(NPC, 0x8A6A44, 0xD8C8A0, new Item.Properties()));
    public static final RegistryObject<Item> NPC_FIGHTER_EGG = ITEMS.register("npc_fighter_spawn_egg",
            () -> new ForgeSpawnEggItem(NPC_FIGHTER, 0x3A3A44, 0xB03030, new Item.Properties()));

    /** Society NPCs that talk (civilians). Meant to be included in {@code #skycraft:talkers}. */
    public static final TagKey<EntityType<?>> SOCIETY_TALKERS = TagKey.create(Registries.ENTITY_TYPE,
            new ResourceLocation(Skycraft.MODID, "society_talkers"));
    /** Animals hunters go after. */
    public static final TagKey<EntityType<?>> GAME = TagKey.create(Registries.ENTITY_TYPE,
            new ResourceLocation(Skycraft.MODID, "society_game"));

    private NpcEntities() {}

    public static void init(IEventBus modBus) {
        ENTITIES.register(modBus);
        ITEMS.register(modBus);
    }

    public static EntityType<NpcEntity> typeFor(NpcRole role) {
        return role.civilian ? NPC.get() : NPC_FIGHTER.get();
    }
}
