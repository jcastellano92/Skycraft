package com.skycraft.world;

import java.util.Locale;
import java.util.Random;

/**
 * Deterministic Skyrim-style names for discovered structures. The same structure always gets the same name for
 * every player (the seed is the stable location id), so a party sees "Bleakwind Barrow" together.
 */
public final class LocationNames {
    private LocationNames() {}

    static final String[] TOWNS = {
            "Riverwood", "Rorikstead", "Ivarstead", "Dragon Bridge", "Karthwasten", "Shor's Stone", "Kynesgrove",
            "Helgen", "Darkwater Crossing", "Stonehills", "Old Hroldan", "Morthal", "Dawnstar", "Falkreath",
            "Winterhold", "Soljund's Sinkhole", "Heartwood Mill", "Anga's Mill", "Half-Moon Mill", "Riverside Hamlet",
            "Snowfall Crossing", "Pinewatch", "Granite Hill", "Rimeholm", "Thornbury", "Eastbrook", "Greywater",
            "Mistveil", "Rimerock", "Selvenhold"
    };
    static final String[] TOWN_PREFIX = {
            "Frost", "Iron", "Raven", "Wolf", "Pine", "Stone", "Snow", "Ash", "Elk", "Bear", "Mead", "Oak", "Thorn",
            "Silver", "Winter", "Mist", "Amber", "Cold", "Grey", "High", "Rune", "Hearth", "Brook", "Birch", "Hawk"
    };
    static final String[] TOWN_SUFFIX = {
            "hollow", "stead", "wick", "wood", "ford", "holm", "bridge", "mere", "dale", "vale", "brook", "haven",
            "moor", "crossing", "watch", "rest", "fell", "gate", "hill", "run"
    };
    static final String[] ROOT_PREFIX = {
            "Bleak", "Frost", "Raven", "Iron", "Shroud", "Silver", "Dead", "Black", "White", "Ice", "Storm", "Wolf",
            "Bear", "Hag", "Gloom", "Shadow", "Fell", "Night", "Ember", "Snow", "Sky", "Ash", "Bone", "Thorn", "Mist",
            "Grey", "Red", "Brittle", "Wind", "Dragon", "Gallows", "Korv", "Yngol", "Ust", "Folg", "Ragn", "Geir",
            "Haal", "Rime", "Vald", "Saar", "Vol", "Sarth", "Ysgr", "Skuld", "Hjal", "Draug", "Gjuk", "Morv", "Hrag"
    };
    static final String[] ROOT_SUFFIX = {
            "wind", "fall", "mere", "crest", "hollow", "rock", "peak", "watch", "moor", "reach", "holm", "gard",
            "vale", "spire", "helm", "shade", "fang", "crag", "shroud", "cairn", "maw", "tooth", "heim", "stad", "run",
            "barrow", "kar", "ungr", "vild", "dun", "skar", "hund", "ald", "vard"
    };
    static final String[] MINE_SUFFIX = {"shard", "belly", "vein", "stone", "deep", "delve", "pit", "breaker", "wallow", "silver"};
    static final String[] DWEMER = {
            "Mzinchaleft", "Nchuand-Zel", "Alftand", "Arkngthamz", "Raldbthar", "Bthardamz", "Mzulft", "Irkngthand",
            "Kagrenzel", "Nchardak", "Mzark", "Avanchnzel", "Bthalft", "Rkundzelft", "Nchuthand", "Arkngthunch"
    };
    static final String[] SHIPS = {
            "Brinehammer", "Icerunner", "Winter War", "Pride of Tel Vos", "Red Wave", "Sea Squall", "Northern Maiden",
            "Katariah", "Salty Skeever", "Frost Gull", "Stormcrow", "Horker's Folly", "Kraken's Kiss", "Ysgramor's Oar",
            "Bloated Man", "Grey Mare", "Solitude Star", "Wavebreaker"
    };
    static final String[] DIVINES = {
            "Akatosh", "Arkay", "Dibella", "Julianos", "Kynareth", "Mara", "Stendarr", "Talos", "Zenithar", "Auri-El"
    };
    static final String[] DAEDRA = {
            "Dagon", "Molag Bal", "Boethiah", "Mephala", "Vaermina", "Malacath", "Namira", "Peryite", "Sanguine",
            "Hircine", "Clavicus Vile", "Azura", "Meridia", "Nocturnal", "Sheogorath", "Hermaeus Mora"
    };
    static final String[] INN_ADJ = {
            "Bannered", "Sleeping", "Drunken", "Frozen", "Winking", "Ragged", "Silver", "Old", "Laughing", "Mead-Soaked",
            "Wandering", "Golden", "Howling", "Lucky", "Rusty", "Snoring"
    };
    static final String[] INN_NOUN = {
            "Mare", "Giant", "Huntsman", "Hearth", "Skeever", "Flagon", "Horker", "Mammoth", "Troll", "Bard", "Tankard",
            "Wolf", "Goat", "Draugr", "Nord"
    };
    static final String[] HALLS = {"Valor", "Heroes", "the Honored Dead", "the Ancestors", "Shor", "the Brave"};
    static final String[] FORTS = {"Greymoor", "Dawnguard", "Sungard", "Amol", "Kastav", "Neugrad", "Snowhawk", "Hraggstad", "Dunstad", "Greenwall"};

    /** FNV-1a 64-bit: stable across JVMs and runs. */
    public static long hash(String s) {
        long h = 0xcbf29ce484222325L;
        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= 0x100000001b3L;
        }
        return h;
    }

    private static String pick(Random r, String[] list) {
        return list[r.nextInt(list.length)];
    }

    private static String root(Random r) {
        String a = pick(r, ROOT_PREFIX);
        String b = pick(r, ROOT_SUFFIX);
        if (a.toLowerCase(Locale.ROOT).endsWith(b.substring(0, 1))) b = pick(r, ROOT_SUFFIX);
        return a + b;
    }

    /**
     * @param id   stable location id (dimension|structure|chunk)
     * @param path structure registry path, used for flavour (crypt vs barrow)
     */
    public static String generate(String id, LocationKind kind, String path) {
        Random r = new Random(hash(id));
        r.nextInt(); // decorrelate from the raw seed
        return switch (kind) {
            case TOWN -> r.nextInt(5) < 2 ? pick(r, TOWNS) : pick(r, TOWN_PREFIX) + pick(r, TOWN_SUFFIX);
            case MINE -> pick(r, ROOT_PREFIX) + pick(r, MINE_SUFFIX) + " Mine";
            case SANCTUM -> r.nextInt(8) == 0 ? "Labyrinthian" : root(r) + " Sanctum";
            case TOMB -> root(r) + " Tomb";
            case RUIN -> path.contains("ocean") ? "Sunken " + root(r) + " Ruins" : root(r) + " Ruins";
            case SHRINE -> root(r) + " Sunken Shrine";
            case DWEMER -> r.nextInt(4) == 0 ? "Blackreach" : pick(r, DWEMER);
            case BARROW -> {
                if (path.contains("catacomb")) yield root(r) + " Catacombs";
                if (path.contains("crypt") || path.contains("mausoleum")) yield root(r) + " Crypt";
                yield root(r) + (r.nextBoolean() ? " Barrow" : " Tomb");
            }
            case HUT -> root(r) + (path.contains("swamp") || path.contains("witch") ? " Hagraven Hut" : r.nextBoolean() ? " Hut" : " Shack");
            case MANOR -> root(r) + " Manor";
            case BANDIT_CAMP -> root(r) + (r.nextBoolean() ? " Bandit Outpost" : " Redoubt");
            case WRECK -> "Wreck of the " + pick(r, SHIPS);
            case OBLIVION_GATE -> "Shattered Oblivion Gate";
            case CITADEL -> r.nextInt(3) == 0 ? "Daedric Citadel" : "Citadel of " + pick(r, DAEDRA);
            case DAEDRIC_TOWER -> r.nextInt(3) == 0 ? "Oblivion Tower" : "Sigil Tower of " + pick(r, DAEDRA);
            case HALL_OF_VALOR -> "Hall of " + pick(r, HALLS);
            case FORT -> "Fort " + (r.nextInt(3) == 0 ? pick(r, FORTS) : root(r));
            case TOWER -> root(r) + (r.nextBoolean() ? " Tower" : " Watchtower");
            case CAMP -> root(r) + " Camp";
            case CAVE -> {
                int v = r.nextInt(4);
                yield root(r) + (v == 0 ? " Grotto" : v == 1 ? " Den" : " Cave");
            }
            case TEMPLE -> r.nextBoolean() ? "Temple of " + pick(r, DIVINES) : "Shrine of " + pick(r, DIVINES);
            case INN -> "The " + pick(r, INN_ADJ) + " " + pick(r, INN_NOUN);
            case LANDMARK -> root(r) + (r.nextBoolean() ? " Stones" : " Monolith");
        };
    }
}
