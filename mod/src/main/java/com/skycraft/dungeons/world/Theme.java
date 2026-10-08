package com.skycraft.dungeons.world;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * The kind of dungeon a {@link DungeonStructure} builds. One structure type serves every theme; the structure JSON
 * picks the theme ({@code "theme": "barrow"}).
 */
public enum Theme implements StringRepresentable {
    //               id                  style           rooms  depth   room height  puzzle
    BARROW("barrow", Style.BUILT, 7, 11, 14, 22, 5, 7, true),
    CAVE("cave", Style.NATURAL, 5, 8, 10, 16, 4, 6, false),
    DWEMER("dwemer", Style.BUILT, 7, 11, 18, 28, 6, 8, true),
    VAMPIRE_LAIR("vampire_lair", Style.NATURAL, 4, 7, 10, 16, 4, 6, false),
    NECROMANCER_LAIR("necromancer_lair", Style.NATURAL, 4, 7, 10, 16, 4, 6, false),
    HAGRAVEN_LAIR("hagraven_lair", Style.NATURAL, 4, 6, 9, 14, 4, 6, false),
    MINE("mine", Style.NATURAL, 5, 8, 10, 18, 4, 5, false),
    FORT("fort", Style.BUILT, 3, 5, 8, 11, 4, 5, false),
    GIANT_CAMP("giant_camp", Style.SURFACE, 0, 0, 0, 0, 0, 0, false),
    DRAGON_LAIR("dragon_lair", Style.SURFACE, 0, 0, 0, 0, 0, 0, false),
    DAEDRIC_SHRINE("daedric_shrine", Style.SURFACE, 0, 0, 0, 0, 0, 0, false);

    public static final Codec<Theme> CODEC = StringRepresentable.fromEnum(Theme::values);

    /** How rooms are shaped: masonry boxes, noise-carved caverns, or a single surface site. */
    public enum Style { BUILT, NATURAL, SURFACE }

    public final String id;
    public final Style style;
    public final int minRooms;
    public final int maxRooms;
    public final int minDepth;
    public final int maxDepth;
    public final int minHeight;
    public final int maxHeight;
    /** Whether the room before the boss is sealed with a lever-operated gate. */
    public final boolean puzzle;

    Theme(String id, Style style, int minRooms, int maxRooms, int minDepth, int maxDepth, int minHeight, int maxHeight, boolean puzzle) {
        this.id = id;
        this.style = style;
        this.minRooms = minRooms;
        this.maxRooms = maxRooms;
        this.minDepth = minDepth;
        this.maxDepth = maxDepth;
        this.minHeight = minHeight;
        this.maxHeight = maxHeight;
        this.puzzle = puzzle;
    }

    public boolean underground() {
        return style != Style.SURFACE;
    }

    public boolean natural() {
        return style == Style.NATURAL;
    }

    /** Lairs share the cave carving with their own dressing. */
    public boolean lair() {
        return this == VAMPIRE_LAIR || this == NECROMANCER_LAIR || this == HAGRAVEN_LAIR;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    public static Theme byOrdinal(int ordinal) {
        Theme[] v = values();
        return ordinal >= 0 && ordinal < v.length ? v[ordinal] : BARROW;
    }
}
