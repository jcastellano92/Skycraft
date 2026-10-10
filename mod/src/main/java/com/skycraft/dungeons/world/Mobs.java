package com.skycraft.dungeons.world;

/**
 * Dungeon inhabitants by registry id. Creatures (contract 7) and optional mods are looked up at generation time and
 * silently skipped (or replaced by a vanilla stand-in) when missing.
 */
public final class Mobs {
    private Mobs() {}

    public static final String DRAUGR = "skycraft:draugr";
    public static final String DEATHLORD = "skycraft:draugr_deathlord";
    public static final String BANDIT = "skycraft:bandit";
    public static final String BANDIT_CHIEF = "skycraft:bandit_chief";
    public static final String SKEEVER = "skycraft:skeever";
    public static final String TROLL = "skycraft:troll";
    public static final String GIANT = "skycraft:giant";
    public static final String DWARVEN_SPIDER = "skycraft:dwarven_spider";
    public static final String DWARVEN_SPHERE = "skycraft:dwarven_sphere";
    public static final String DWARVEN_CENTURION = "skycraft:dwarven_centurion";

    public static String draugr() {
        return orElse(DRAUGR, "minecraft:zombie");
    }

    public static String deathlord() {
        return orElse(DEATHLORD, "minecraft:wither_skeleton");
    }

    public static String bandit() {
        return orElse(BANDIT, "minecraft:vindicator");
    }

    public static String banditChief() {
        return orElse(BANDIT_CHIEF, BANDIT, "minecraft:vindicator");
    }

    public static String skeever() {
        return orElse(SKEEVER, "minecraft:cave_spider");
    }

    public static String spider() {
        return orElse("skycraft:frostbite_spider", "minecraft:cave_spider");
    }

    public static String vampire() {
        return orElse("skycraft:vampire", "vampirism:vampire", "minecraft:spider");
    }

    public static String vampireMaster() {
        return orElse("skycraft:vampire", "vampirism:advanced_vampire", "minecraft:witch");
    }

    public static String necromancer() {
        return orElse("skycraft:necromancer", "minecraft:evoker");
    }

    public static String hagraven() {
        return orElse("skycraft:hagraven", "minecraft:witch");
    }

    public static String falmer() {
        return orElse("skycraft:falmer", "minecraft:zombie");
    }

    /** Wildlife for animal dens: fauna's beasts if registered, frostbite spiders, wolves, and skeevers. */
    public static String denBeast(int roll) {
        return switch (Math.floorMod(roll, 4)) {
            case 0 -> orElse("skycraft:bear", "skycraft:wolf", SKEEVER, "minecraft:wolf");
            case 1 -> orElse("skycraft:wolf", "minecraft:wolf");
            case 2 -> spider();
            default -> skeever();
        };
    }

    public static String denBoss() {
        return orElse(TROLL, "skycraft:frostbite_spider", "skycraft:bear", "minecraft:ravager");
    }

    public static String mammoth() {
        return Painter.firstExisting("skycraft:mammoth");
    }

    /** First registered id of the list. */
    public static String orElse(String... ids) {
        String id = Painter.firstExisting(ids);
        return id != null ? id : ids[ids.length - 1];
    }
}
