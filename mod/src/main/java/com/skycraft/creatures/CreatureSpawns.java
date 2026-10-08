package com.skycraft.creatures;

import com.skycraft.Skycraft;
import com.skycraft.core.SkyData;
import com.skycraft.creatures.entity.DragonEntity;
import com.skycraft.creatures.entity.DraugrEntity;
import com.skycraft.creatures.entity.GuardEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;

/**
 * Skyrim-like world population: draugr replace zombies in the cold north, phantoms are disabled, dragons attack
 * travelers, and every village keeps a few hold guards around its meeting point.
 */
@Mod.EventBusSubscriber(modid = Skycraft.MODID)
public final class CreatureSpawns {
    private static final int GUARD_CHECK_INTERVAL = 600;
    private static final int MIN_GUARDS = 2;
    private static final int MAX_GUARDS = 4;

    private CreatureSpawns() {}

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (CreaturesConfig.DISABLE_INSOMNIA.get()) {
            event.getServer().getGameRules().getRule(GameRules.RULE_DOINSOMNIA).set(false, event.getServer());
        }
    }

    // ------------------------------------------------------------------ draugr instead of zombies

    @SubscribeEvent
    public static void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        Mob mob = event.getEntity();
        if (mob.getType() != EntityType.ZOMBIE || event.isSpawnCancelled()) return;
        MobSpawnType type = event.getSpawnType();
        if (type != MobSpawnType.NATURAL && type != MobSpawnType.CHUNK_GENERATION) return;
        ServerLevelAccessor level = event.getLevel();
        BlockPos pos = BlockPos.containing(event.getX(), event.getY(), event.getZ());
        Holder<Biome> biome = level.getBiome(pos);
        boolean north = biome.is(BiomeTags.IS_TAIGA) || biome.is(BiomeTags.IS_MOUNTAIN) || biome.is(Tags.Biomes.IS_SNOWY)
                || biome.value().coldEnoughToSnow(pos);
        if (!north || level.getRandom().nextFloat() >= CreaturesConfig.DRAUGR_REPLACES_ZOMBIES.get()) return;

        DraugrEntity draugr = ModEntities.DRAUGR.get().create(level.getLevel());
        if (draugr == null) return;
        draugr.moveTo(event.getX(), event.getY(), event.getZ(), mob.getYRot(), 0);
        draugr.finalizeSpawn(level, event.getDifficulty(), type, null, null);
        event.setSpawnCancelled(true);
        level.addFreshEntity(draugr);
    }

    // ------------------------------------------------------------------ per-player world events

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        ServerLevel level = player.serverLevel();
        long time = level.getGameTime() + player.getId() * 37L;
        if (CreaturesConfig.GUARD_SPAWNING.get() && time % GUARD_CHECK_INTERVAL == 0) maintainGuards(level, player);
        if (time % CreaturesConfig.DRAGON_ATTACK_INTERVAL.get() == 0) maybeDragonAttack(level, player);
    }

    /** Keeps 2-4 guards around the village meeting point (bell) nearest to the player. */
    private static void maintainGuards(ServerLevel level, ServerPlayer player) {
        if (player.isSpectator()) return;
        Optional<BlockPos> bell = level.getPoiManager().findClosest(holder -> holder.is(PoiTypes.MEETING), player.blockPosition(), 64,
                PoiManager.Occupancy.ANY);
        if (bell.isEmpty()) return;
        BlockPos center = bell.get();
        if (!level.isLoaded(center)) return;
        int guards = level.getEntitiesOfClass(GuardEntity.class, new AABB(center).inflate(64)).size();
        if (guards >= MIN_GUARDS) return;
        RandomSource random = level.getRandom();
        int wanted = Math.min(MAX_GUARDS, MIN_GUARDS + random.nextInt(2)) - guards;
        for (int i = 0; i < wanted; i++) {
            BlockPos pos = findGroundNear(level, center, 10, random);
            if (pos == null) continue;
            GuardEntity guard = ModEntities.GUARD.get().create(level);
            if (guard == null) continue;
            guard.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360f, 0);
            if (!level.noCollision(guard)) continue;
            guard.setHome(center);
            guard.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null, null);
            guard.setPersistenceRequired();
            level.addFreshEntity(guard);
        }
    }

    private static BlockPos findGroundNear(ServerLevel level, BlockPos center, int radius, RandomSource random) {
        for (int attempt = 0; attempt < 12; attempt++) {
            int x = center.getX() + random.nextInt(radius * 2 + 1) - radius;
            int z = center.getZ() + random.nextInt(radius * 2 + 1) - radius;
            BlockPos column = new BlockPos(x, center.getY(), z);
            if (!level.isLoaded(column)) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            BlockPos below = pos.below();
            if (Math.abs(y - center.getY()) > 8) continue;
            if (!level.getFluidState(below).isEmpty()) continue;
            if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) continue;
            return pos;
        }
        return null;
    }

    /** Skyrim: out of nowhere, a roar. A dragon appears ~100 blocks away and hunts the player. */
    private static void maybeDragonAttack(ServerLevel level, ServerPlayer player) {
        if (level.dimension() != Level.OVERWORLD || player.isCreative() || player.isSpectator() || !player.isAlive()) return;
        if (SkyData.get(player).getLevel() < CreaturesConfig.DRAGON_MIN_LEVEL.get()) return;
        long dayTime = level.getDayTime() % 24000L;
        if (dayTime > 13500L) return; // day and dusk only
        if (level.getRandom().nextFloat() >= CreaturesConfig.DRAGON_ATTACK_CHANCE.get()) return;
        trySpawnDragonAttack(level, player);
    }

    /**
     * Spawns an attacking dragon for {@code player} (also usable by other modules / commands). Returns the dragon or
     * null if the player is underground or another dragon is already near.
     */
    public static DragonEntity trySpawnDragonAttack(ServerLevel level, ServerPlayer player) {
        BlockPos feet = player.blockPosition();
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, feet.getX(), feet.getZ());
        if (feet.getY() < surface - 6 && !level.canSeeSky(feet)) return null; // underground
        if (!level.getEntitiesOfClass(DragonEntity.class, player.getBoundingBox().inflate(256)).isEmpty()) return null;

        RandomSource random = level.getRandom();
        for (double dist : new double[]{100, 80, 60, 40}) {
            double angle = random.nextDouble() * Math.PI * 2;
            double x = player.getX() + Math.cos(angle) * dist;
            double z = player.getZ() + Math.sin(angle) * dist;
            BlockPos column = BlockPos.containing(x, player.getY(), z);
            if (!level.isLoaded(column)) continue;
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, column.getX(), column.getZ());
            double y = Math.max(player.getY() + 40, ground + 30);
            y = Math.min(y, level.getMaxBuildHeight() - 5);
            DragonEntity dragon = ModEntities.DRAGON.get().create(level);
            if (dragon == null) return null;
            dragon.moveTo(x, y, z, (float) (Mth.atan2(player.getZ() - z, player.getX() - x) * Mth.RAD_TO_DEG) - 90f, 0);
            dragon.finalizeSpawn(level, level.getCurrentDifficultyAt(column), MobSpawnType.EVENT, null, null);
            dragon.setFlying(true);
            dragon.noPhysics = true;
            dragon.setTarget(player);
            level.addFreshEntity(dragon);
            // the roar carries: play it between the dragon and the player so they hear where it comes from
            double rx = player.getX() + (x - player.getX()) * 0.3;
            double ry = player.getY() + 10;
            double rz = player.getZ() + (z - player.getZ()) * 0.3;
            level.playSound(null, rx, ry, rz, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 8.0f, 0.75f);
            return dragon;
        }
        return null;
    }
}
