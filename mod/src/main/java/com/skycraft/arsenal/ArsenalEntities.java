package com.skycraft.arsenal;

import com.skycraft.Skycraft;
import com.skycraft.arsenal.entity.SkyArrow;
import com.skycraft.arsenal.entity.StaffBolt;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Arsenal entity types: the Skyrim arrow/bolt and the staff/energy-blade projectile. */
public final class ArsenalEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Skycraft.MODID);

    public static final RegistryObject<EntityType<SkyArrow>> SKY_ARROW = ENTITIES.register("sky_arrow",
            () -> EntityType.Builder.<SkyArrow>of(SkyArrow::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(4).updateInterval(20).build("sky_arrow"));

    public static final RegistryObject<EntityType<StaffBolt>> STAFF_BOLT = ENTITIES.register("staff_bolt",
            () -> EntityType.Builder.<StaffBolt>of(StaffBolt::new, MobCategory.MISC)
                    .sized(0.4f, 0.4f).clientTrackingRange(6).updateInterval(5).build("staff_bolt"));

    private ArsenalEntities() {}
}
