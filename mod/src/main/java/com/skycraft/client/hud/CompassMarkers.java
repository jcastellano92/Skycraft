package com.skycraft.client.hud;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side markers shown on the Skyrim compass. Each module owns a "source" (e.g. {@code "quest"},
 * {@code "locations"}) and replaces its whole marker list whenever it changes.
 */
public final class CompassMarkers {
    public enum Shape { QUEST, LOCATION, DISCOVERED, ENEMY, PLAYER, DOOR }

    /**
     * @param range markers farther than this are hidden (0 = always shown, e.g. quest targets)
     */
    public record Marker(Vec3 pos, Shape shape, int color, String label, double range) {
    }

    private static final Map<String, List<Marker>> SOURCES = new ConcurrentHashMap<>();

    private CompassMarkers() {}

    public static void set(String source, List<Marker> markers) {
        SOURCES.put(source, List.copyOf(markers));
    }

    public static void clear(String source) {
        SOURCES.remove(source);
    }

    public static List<Marker> all() {
        List<Marker> out = new ArrayList<>();
        SOURCES.values().forEach(out::addAll);
        return out;
    }
}
