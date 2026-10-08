package com.skycraft.world;

import com.skycraft.Skycraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Soundtrack events. {@code assets/skycraft/sounds.json} points them at vanilla music by default; a resource pack
 * can replace any of them (e.g. {@code skycraft:music.combat}) with its own tracks.
 */
public final class WorldSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, Skycraft.MODID);

    public static final RegistryObject<SoundEvent> MUSIC_EXPLORE = register("music.explore");
    public static final RegistryObject<SoundEvent> MUSIC_EXPLORE_NIGHT = register("music.explore_night");
    public static final RegistryObject<SoundEvent> MUSIC_TOWN = register("music.town");
    public static final RegistryObject<SoundEvent> MUSIC_DUNGEON = register("music.dungeon");
    public static final RegistryObject<SoundEvent> MUSIC_COMBAT = register("music.combat");
    public static final RegistryObject<SoundEvent> MUSIC_OBLIVION = register("music.oblivion");
    public static final RegistryObject<SoundEvent> MUSIC_SOVNGARDE = register("music.sovngarde");
    public static final RegistryObject<SoundEvent> MUSIC_DISCOVERY = register("music.discovery");

    private WorldSounds() {}

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(Skycraft.MODID, name)));
    }

    public static void init(IEventBus modBus) {
        SOUNDS.register(modBus);
    }
}
