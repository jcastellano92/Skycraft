package com.skycraft.creatures;

import com.skycraft.creatures.entity.BanditChiefEntity;
import com.skycraft.creatures.entity.BanditEntity;
import com.skycraft.creatures.entity.DragonEntity;
import com.skycraft.creatures.entity.DraugrDeathlordEntity;
import com.skycraft.creatures.entity.DraugrEntity;
import com.skycraft.creatures.entity.GiantEntity;
import com.skycraft.creatures.entity.GuardEntity;
import com.skycraft.creatures.entity.SkeeverEntity;
import com.skycraft.creatures.entity.TrollEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

/**
 * Draugr, bandits, giants, trolls, skeevers, dragons, hold guards, lootable corpses, leveled enemies and
 * Skyrim-like spawning.
 *
 * <ul>
 *     <li>{@link ModEntities}: entity types (ids are a cross-module contract), spawn eggs, the bandit camp feature</li>
 *     <li>{@link Leveling}: leveled enemies and rank names</li>
 *     <li>{@link CorpseEvents}: lootable bodies</li>
 *     <li>{@link CreatureSpawns}: draugr instead of zombies, dragon attacks, village guards, no phantoms</li>
 *     <li>{@code creatures.client}: models and renderers</li>
 * </ul>
 */
public final class CreaturesModule {
    private CreaturesModule() {}

    /** Registers this module's DeferredRegisters and mod-bus listeners. Called from the mod constructor. */
    public static void init(IEventBus modBus) {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, CreaturesConfig.SPEC, "skycraft-creatures.toml");
        ModEntities.init(modBus);
        modBus.addListener(CreaturesModule::onAttributes);
        modBus.addListener(CreaturesModule::onSpawnPlacements);
    }

    /** Registers this module's packets via {@link com.skycraft.network.SkyNetwork#register}. Called once at startup. */
    public static void registerPackets() {
        // Creatures sync everything through SynchedEntityData; no packets needed.
    }

    private static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.BANDIT.get(), BanditEntity.createAttributes().build());
        event.put(ModEntities.BANDIT_CHIEF.get(), BanditChiefEntity.createAttributes().build());
        event.put(ModEntities.DRAUGR.get(), DraugrEntity.createAttributes().build());
        event.put(ModEntities.DRAUGR_DEATHLORD.get(), DraugrDeathlordEntity.createAttributes().build());
        event.put(ModEntities.SKEEVER.get(), SkeeverEntity.createAttributes().build());
        event.put(ModEntities.TROLL.get(), TrollEntity.createAttributes().build());
        event.put(ModEntities.GIANT.get(), GiantEntity.createAttributes().build());
        event.put(ModEntities.DRAGON.get(), DragonEntity.createAttributes().build());
        event.put(ModEntities.GUARD.get(), GuardEntity.createAttributes().build());
    }

    private static void onSpawnPlacements(SpawnPlacementRegisterEvent event) {
        SpawnPlacementRegisterEvent.Operation op = SpawnPlacementRegisterEvent.Operation.REPLACE;
        // bandits and giants roam in daylight, on the surface; few at a time
        event.register(ModEntities.BANDIT.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (type, level, reason, pos, random) -> surfaceSpawn(type, level, reason, pos, random) && fewNearby(type, level, pos, 64, 4), op);
        event.register(ModEntities.GIANT.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (type, level, reason, pos, random) -> surfaceSpawn(type, level, reason, pos, random) && fewNearby(type, level, pos, 128, 1), op);
        // trolls hunt at any hour
        event.register(ModEntities.TROLL.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (type, level, reason, pos, random) -> Monster.checkAnyLightMonsterSpawnRules(type, level, reason, pos, random)
                        && fewNearby(type, level, pos, 96, 1), op);
        // draugr and skeevers need darkness (night, caves, tombs)
        event.register(ModEntities.DRAUGR.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Monster::checkMonsterSpawnRules, op);
        event.register(ModEntities.SKEEVER.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Monster::checkMonsterSpawnRules, op);
    }

    /**
     * Any light level, but only under the open sky (no bandits in caves). Daylight spawns are throttled: vanilla
     * monsters can't spawn in daylight, so without this the whole monster cap would fill with bandits and giants.
     */
    private static boolean surfaceSpawn(EntityType<? extends Monster> type, ServerLevelAccessor level, MobSpawnType reason,
                                        BlockPos pos, RandomSource random) {
        if (!Monster.checkAnyLightMonsterSpawnRules(type, level, reason, pos, random)) return false;
        if (reason != MobSpawnType.NATURAL) return true;
        if (!level.canSeeSky(pos)) return false;
        return level.getLevel().isNight() || random.nextInt(6) == 0;
    }

    /** True when fewer than {@code max} creatures of this type are within {@code radius} blocks. */
    private static boolean fewNearby(EntityType<?> type, ServerLevelAccessor level, BlockPos pos, double radius, int max) {
        return level.getEntitiesOfClass(Monster.class, new AABB(pos).inflate(radius), e -> e.getType() == type).size() < max;
    }
}
