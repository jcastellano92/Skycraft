package com.skycraft.society;

import net.minecraft.util.RandomSource;

/**
 * Skyrim-flavoured first names, built from per-race syllables (plus a few well-known names) so towns full of NPCs
 * rarely repeat themselves.
 */
public final class NpcNames {
    public enum Race { NORD, IMPERIAL, BRETON, REDGUARD, ALTMER, BOSMER, DUNMER, KHAJIIT, ARGONIAN, ORC }

    private static final String[][] MALE_PRE = {
            {"Bj", "Ulf", "Hro", "Sig", "Tor", "Ing", "Vig", "Gun", "Hal", "Ey", "As", "Bal", "Ar", "Gor", "Sv", "Kjel", "Hjal", "Ste", "Rag", "Ulv"},
            {"Luc", "Mar", "Gai", "Ser", "Ant", "Cass", "Vel", "Quin", "Tit", "Dec", "Fla", "Aul", "Corn", "Hadr"},
            {"Bren", "Mau", "Ger", "Cal", "Dan", "Ali", "Mel", "Ry", "Ad", "Em", "Jor", "Ver"},
            {"Am", "Kem", "Saa", "Naz", "Ha", "Ja", "Ra", "Ishr", "Kay", "Cyr"},
            {"Ond", "Anc", "Taa", "Ael", "Rul", "Ner", "Cal", "Ven", "Ond", "Ery"},
            {"Faen", "Gwi", "Elr", "Ber", "Ind", "Nir", "Ath", "Gle"},
            {"Ral", "Bru", "Dra", "Nil", "Tel", "Ad", "Ser", "Gol", "Fal", "Ath"},
            {"J'", "Ri'", "Dro'", "M'", "Ra'", "S'", "Ka'", "Ma'"},
            {"Dee", "Kee", "Mad", "Wu", "Nee", "Tee", "Sha", "Gul"},
            {"Gra", "Mog", "Ghor", "Lob", "Dush", "Yam", "Bor", "Ugr", "Mau", "Shag"}
    };
    private static final String[][] MALE_SUF = {
            {"orn", "ric", "gar", "mund", "ulf", "ald", "vald", "ir", "jof", "ar", "nir", "vard", "olf", "en"},
            {"ius", "an", "us", "or", "ian", "o", "ilius", "eus"},
            {"uin", "rice", "ard", "ixte", "on", "aine", "bry", "ec"},
            {"ren", "ir", "ad", "aan", "zir", "had", "ul"},
            {"olemar", "ano", "indil", "dil", "aril", "celin", "dore", "ndil"},
            {"dal", "lin", "oth", "ewen", "ion", "ar"},
            {"is", "nus", "vas", "eth", "dyn", "ril", "oryn", "vys", "os"},
            {"zargo", "saad", "dar", "jirr", "zhar", "kesh", "rash"},
            {"leel", "rava", "esi", "tra", "zish", "lix", "teius"},
            {"kh", "bash", "ak", "gol", "nag", "uz", "rog", "ash", "ul"}
    };
    private static final String[][] FEMALE_PRE = {
            {"Hil", "Sig", "Ing", "Ast", "Fri", "Gun", "Ra", "Ys", "Hul", "Bry", "Ey", "Tho", "Sva", "Ol", "Ag", "Lyd", "Jor"},
            {"Cam", "Adri", "Liv", "Ser", "Vel", "Clau", "Jul", "Octa", "Ama", "Lu", "Aure"},
            {"Mar", "Eli", "Ade", "Bri", "Ys", "Ca", "Gem", "Del", "Ane"},
            {"Sa", "Ne", "Aza", "Ka", "Ira", "Ra", "Na"},
            {"Ela", "Ary", "Faa", "Ilm", "Nel", "Ess", "Cir"},
            {"Ann", "Gwi", "Elv", "Ind", "Ari", "Fae"},
            {"Ery", "Dra", "Bra", "Ili", "Ade", "Sen", "Ves", "Ala"},
            {"Ri'", "Ka'", "Ja'", "S'", "Za'", "Dar'"},
            {"Dee", "Kee", "Shah", "Wu", "Nee", "Mee"},
            {"Gha", "Mor", "Yat", "Bor", "Sha", "Ur", "Lash"}
    };
    private static final String[][] FEMALE_SUF = {
            {"da", "rid", "run", "hild", "a", "ja", "dis", "fina", "olda", "nja", "ve", "ia", "unn"},
            {"illa", "anne", "ia", "ina", "a", "ette", "ilia"},
            {"elle", "ane", "ette", "ise", "ina", "ra", "ma"},
            {"ma", "ira", "adi", "ira", "isha", "ana"},
            {"nwe", "rie", "ime", "wen", "endil", "ara"},
            {"ekke", "sa", "ewen", "ara", "inda"},
            {"sa", "ni", "vyn", "ra", "nde", "dra", "ila"},
            {"rassa", "ini", "ressa", "sha", "dara", "ri"},
            {"ja", "rava", "esi", "jeeta", "na", "rei"},
            {"sha", "gul", "ak", "bura", "ga", "dush", "ra"}
    };
    /** Argonians are often called by what they did when they hatched. */
    private static final String[] ARGONIAN_VERBS = {"Swims", "Hides", "Walks", "Sees", "Reads", "Waits", "Sings", "Talks"};
    private static final String[] ARGONIAN_NOUNS = {"in-Shadow", "the-Rain", "in-Reeds", "the-Mud", "Stars", "the-Tide", "in-Dusk"};

    /** A few familiar names, mixed in now and then (male, female). */
    private static final String[][] CLASSIC = {
            {"Lucan", "Faendal", "Sven", "Alvor", "Hod", "Embry", "Bjorn", "Erik", "Brenuin", "Idolaf", "Lod", "Wilhelm", "Fastred", "Lars",
                    "Ralis", "Gunding", "Hroki", "Leifnarr", "Torolf", "Sigurd", "Arngeir"},
            {"Ysolda", "Hilde", "Gerdur", "Hulda", "Olfina", "Temba", "Narri", "Gemma", "Mjoll", "Uthgerd", "Brina", "Sigrid",
                    "Dorthe", "Frida", "Camilla", "Adrianne", "Annekke", "Svana", "Grelka", "Lydia", "Jordis"}
    };

    private NpcNames() {}

    /** The likely race for a role (Thalmor are High Elves, Stormcloaks Nords, Forsworn Reachmen...). */
    public static Race raceFor(NpcRole role, RandomSource r) {
        return switch (role) {
            case THALMOR -> Race.ALTMER;
            case STORMCLOAK_SOLDIER, JARL, HOUSECARL -> r.nextInt(8) == 0 ? Race.IMPERIAL : Race.NORD;
            case IMPERIAL_SOLDIER -> r.nextBoolean() ? Race.IMPERIAL : (r.nextInt(3) == 0 ? Race.REDGUARD : Race.NORD);
            case FORSWORN -> Race.BRETON;
            case MAGE -> pick(r, Race.BRETON, Race.ALTMER, Race.DUNMER, Race.IMPERIAL, Race.NORD);
            case ASSASSIN -> pick(r, Race.DUNMER, Race.KHAJIIT, Race.IMPERIAL, Race.NORD, Race.REDGUARD);
            case THIEF -> pick(r, Race.KHAJIIT, Race.ARGONIAN, Race.BOSMER, Race.NORD, Race.IMPERIAL);
            case HUNTER -> pick(r, Race.BOSMER, Race.NORD, Race.NORD, Race.ORC);
            default -> {
                int roll = r.nextInt(100);
                if (roll < 50) yield Race.NORD;
                if (roll < 63) yield Race.IMPERIAL;
                if (roll < 72) yield Race.BRETON;
                if (roll < 79) yield Race.REDGUARD;
                if (roll < 86) yield Race.DUNMER;
                if (roll < 91) yield Race.BOSMER;
                if (roll < 94) yield Race.KHAJIIT;
                if (roll < 97) yield Race.ARGONIAN;
                yield Race.ORC;
            }
        };
    }

    private static Race pick(RandomSource r, Race... races) {
        return races[r.nextInt(races.length)];
    }

    public static String generate(Race race, boolean female, RandomSource r) {
        if ((race == Race.NORD || race == Race.IMPERIAL || race == Race.BRETON) && r.nextInt(5) == 0) {
            String[] list = CLASSIC[female ? 1 : 0];
            return list[r.nextInt(list.length)];
        }
        if (race == Race.ARGONIAN && r.nextInt(3) == 0) {
            return ARGONIAN_VERBS[r.nextInt(ARGONIAN_VERBS.length)] + "-" + ARGONIAN_NOUNS[r.nextInt(ARGONIAN_NOUNS.length)];
        }
        int i = race.ordinal();
        String[] pre = female ? FEMALE_PRE[i] : MALE_PRE[i];
        String[] suf = female ? FEMALE_SUF[i] : MALE_SUF[i];
        String a = pre[r.nextInt(pre.length)];
        String b = suf[r.nextInt(suf.length)];
        // avoid doubled vowels/consonants at the joint ("Hildda")
        if (!a.isEmpty() && !b.isEmpty() && Character.toLowerCase(a.charAt(a.length() - 1)) == b.charAt(0)) b = b.substring(1);
        if (b.isEmpty()) b = suf[0];
        return a + b;
    }
}
