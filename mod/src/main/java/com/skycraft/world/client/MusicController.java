package com.skycraft.world.client;

import com.skycraft.client.ClientState;
import com.skycraft.core.SkyData;
import com.skycraft.network.CorePackets;
import com.skycraft.world.LocationKind;
import com.skycraft.world.WorldConfig;
import com.skycraft.world.WorldData;
import com.skycraft.world.WorldSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;

import java.util.Random;

/**
 * Skyrim-style situational soundtrack. Once a second it decides the situation (combat, dungeon, town, Oblivion,
 * Sovngarde, day/night exploration) and plays the matching {@code skycraft:music.*} event with fades. Exploration
 * tracks are followed by a pause of silence; combat interrupts immediately and the previous mood returns after.
 * Vanilla music is muted while this is active (see {@link WorldClientEvents#onPlaySound}).
 */
public final class MusicController {
    public enum Situation {
        EXPLORE, EXPLORE_NIGHT, TOWN, DUNGEON, COMBAT, OBLIVION, SOVNGARDE;

        SoundEvent sound() {
            return switch (this) {
                case EXPLORE -> WorldSounds.MUSIC_EXPLORE.get();
                case EXPLORE_NIGHT -> WorldSounds.MUSIC_EXPLORE_NIGHT.get();
                case TOWN -> WorldSounds.MUSIC_TOWN.get();
                case DUNGEON -> WorldSounds.MUSIC_DUNGEON.get();
                case COMBAT -> WorldSounds.MUSIC_COMBAT.get();
                case OBLIVION -> WorldSounds.MUSIC_OBLIVION.get();
                case SOVNGARDE -> WorldSounds.MUSIC_SOVNGARDE.get();
            };
        }

        /** Day and night exploration count as one mood: the change waits for the track to end. */
        int group() {
            return this == EXPLORE_NIGHT ? EXPLORE.ordinal() : ordinal();
        }
    }

    private static final TagKey<EntityType<?>> TALKERS = TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation("skycraft", "talkers"));
    private static final Random RANDOM = new Random();

    private static FadingMusic current;
    private static Situation currentSituation;
    private static Situation pendingSituation;
    private static int pendingCount;
    private static int silenceSeconds = 3;
    private static int tickCounter;

    private MusicController() {}

    public static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && WorldConfig.MUSIC.get();
    }

    public static boolean isOurs(SoundInstance sound) {
        return sound instanceof FadingMusic;
    }

    public static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null || !WorldConfig.MUSIC.get()) {
            stopNow(mc);
            return;
        }
        if (++tickCounter % 20 != 0) return;

        boolean audible = mc.options.getSoundSourceVolume(SoundSource.MUSIC) > 0f && mc.options.getSoundSourceVolume(SoundSource.MASTER) > 0f;
        if (!audible) {
            stopNow(mc);
            return;
        }

        if (nearPerformingBard(mc)) {
            if (current != null && !current.isStopped() && mc.getSoundManager().isActive(current)) {
                current.fadeOut(30);
                current = null;
                currentSituation = null;
                silenceSeconds = 2;
            }
            return;
        }

        Situation want = decide(mc);
        // require a mood to hold for a few seconds before switching (combat switches at once)
        if (want != pendingSituation) {
            pendingSituation = want;
            pendingCount = 0;
        } else {
            pendingCount++;
        }
        boolean stable = want == Situation.COMBAT || pendingCount >= 3;

        boolean playing = current != null && !current.isStopped() && mc.getSoundManager().isActive(current);
        if (playing) {
            if (currentSituation != null && currentSituation.group() != want.group() && stable) {
                current.fadeOut(want == Situation.COMBAT ? 20 : 50);
                current = null;
                silenceSeconds = want == Situation.COMBAT ? 0 : 2;
                currentSituation = null;
            } else {
                return;
            }
        } else if (current != null) {
            // the track ended on its own (or failed to start): Skyrim leaves a pause between pieces
            boolean wasCombat = currentSituation == Situation.COMBAT;
            current = null;
            silenceSeconds = wasCombat ? 0 : 20 + RANDOM.nextInt(40);
            currentSituation = null;
        }

        if (current == null) {
            if (want == Situation.COMBAT) silenceSeconds = 0;
            if (silenceSeconds > 0) {
                silenceSeconds--;
                return;
            }
            start(mc, want);
        }
    }

    private static void start(Minecraft mc, Situation situation) {
        mc.getMusicManager().stopPlaying();
        current = new FadingMusic(situation.sound(), situation == Situation.COMBAT ? 15 : 60);
        currentSituation = situation;
        mc.getSoundManager().play(current);
    }

    private static void stopNow(Minecraft mc) {
        if (current != null) {
            mc.getSoundManager().stop(current);
            current = null;
            currentSituation = null;
        }
        silenceSeconds = 3;
    }

    public static void reset() {
        stopNow(Minecraft.getInstance());
        pendingSituation = null;
        pendingCount = 0;
    }

    /** Short sting on "DISCOVERED". */
    public static void playDiscoverySting() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.getSoundSourceVolume(SoundSource.MUSIC) <= 0f) return;
        mc.getSoundManager().play(SimpleSoundInstance.forUI(WorldSounds.MUSIC_DISCOVERY.get(), 1.0f,
                0.6f * mc.options.getSoundSourceVolume(SoundSource.MUSIC)));
    }

    // ------------------------------------------------------------------ situation

    public static Situation decide(Minecraft mc) {
        Level level = mc.level;
        var player = mc.player;
        if (level.dimension() == Level.NETHER) {
            return inCombat() ? Situation.COMBAT : Situation.OBLIVION;
        }
        if (level.dimension() == Level.END) {
            return inCombat() ? Situation.COMBAT : Situation.SOVNGARDE;
        }
        if (inCombat()) return Situation.COMBAT;
        BlockPos pos = player.blockPosition();
        if (inDungeon(mc, pos)) return Situation.DUNGEON;
        if (nearTown(mc)) return Situation.TOWN;
        long time = Math.floorMod(level.getDayTime(), 24000L);
        boolean night = time >= 13000 && time < 23000;
        return night ? Situation.EXPLORE_NIGHT : Situation.EXPLORE;
    }

    private static boolean inCombat() {
        return ClientState.has(CorePackets.SyncVitals.IN_COMBAT) || ClientState.has(CorePackets.SyncVitals.DETECTED);
    }

    private static boolean inDungeon(Minecraft mc, BlockPos pos) {
        Level level = mc.level;
        if (pos.getY() < level.getSeaLevel() - 10 && level.getBrightness(LightLayer.SKY, pos) == 0) return true;
        // inside a discovered dungeon-like place (barrows, mines, Dwemer ruins...)
        ListTag list = SkyData.get(mc.player).module(WorldData.MODULE).getList("discovered", Tag.TAG_COMPOUND);
        String dim = level.dimension().location().toString();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag loc = list.getCompound(i);
            if (!loc.getString("dim").equals(dim)) continue;
            LocationKind kind = LocationKind.byId(loc.getString("type"));
            if (!kind.underground()) continue;
            if (WorldData.inBox(loc, pos.getX(), pos.getY(), pos.getZ(), 0) && level.getBrightness(LightLayer.SKY, pos) < 4) return true;
        }
        return false;
    }

    private static boolean nearTown(Minecraft mc) {
        var box = mc.player.getBoundingBox().inflate(32);
        int count = mc.level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e instanceof AbstractVillager || e.getType().is(TALKERS)).size();
        return count >= 2;
    }

    private static boolean nearPerformingBard(Minecraft mc) {
        if (mc.level == null || mc.player == null) return false;
        var box = mc.player.getBoundingBox().inflate(24);
        for (LivingEntity e : mc.level.getEntitiesOfClass(LivingEntity.class, box)) {
            if (e instanceof com.skycraft.society.entity.NpcEntity npc && npc.isPlaying()) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ sound instance

    /** A non-positional music track whose volume fades in and out (the sound engine re-reads the volume every tick). */
    static final class FadingMusic extends AbstractTickableSoundInstance {
        private final int fadeInTicks;
        private float level;
        private int fadeOutTicks;
        private float fadeOutStep;

        FadingMusic(SoundEvent event, int fadeInTicks) {
            super(event, SoundSource.MUSIC, SoundInstance.createUnseededRandom());
            this.fadeInTicks = Math.max(1, fadeInTicks);
            this.looping = false;
            this.delay = 0;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.x = 0;
            this.y = 0;
            this.z = 0;
            this.level = 0.02f;
            this.volume = level;
        }

        void fadeOut(int ticks) {
            fadeOutTicks = Math.max(1, ticks);
            fadeOutStep = Math.max(0.001f, level / fadeOutTicks);
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            if (fadeOutTicks > 0) {
                level -= fadeOutStep;
                if (--fadeOutTicks <= 0 || level <= 0f) {
                    level = 0f;
                    volume = 0f;
                    stop();
                    return;
                }
            } else if (level < 1f) {
                level = Math.min(1f, level + 1f / fadeInTicks);
            }
            volume = level;
        }
    }
}
