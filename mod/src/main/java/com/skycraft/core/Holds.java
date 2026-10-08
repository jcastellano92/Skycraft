package com.skycraft.core;

import com.skycraft.SkyConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

/**
 * The world is divided into square "holds" (default 2048 blocks), each named after one of Skyrim's nine holds.
 * Bounties, Jarls, guards and radiant quests are per hold. Holds in other dimensions use the dimension name.
 */
public final class Holds {
    public static final String[] NAMES = {
            "whiterun", "the_rift", "eastmarch", "the_pale", "winterhold", "haafingar", "hjaalmarch", "the_reach", "falkreath"
    };

    private Holds() {}

    /** Stable hold id for a position, e.g. {@code "whiterun"} or {@code "whiterun_2"} for repeated names far away. */
    public static String holdAt(Level level, BlockPos pos) {
        if (level.dimension() != Level.OVERWORLD) return level.dimension().location().getPath();
        int size = SkyConfig.HOLD_SIZE.get();
        int rx = Math.floorDiv(pos.getX(), size);
        int rz = Math.floorDiv(pos.getZ(), size);
        // 3x3 blocks of holds tile the world; the tile index makes names unique across the infinite world.
        int ix = Math.floorMod(rx, 3);
        int iz = Math.floorMod(rz, 3);
        String base = NAMES[iz * 3 + ix];
        int tx = Math.floorDiv(rx, 3);
        int tz = Math.floorDiv(rz, 3);
        return tx == 0 && tz == 0 ? base : base + "_" + (Math.abs(tx * 31 + tz) % 97 + 2);
    }

    public static Component displayName(String holdId) {
        int underscore = holdId.lastIndexOf('_');
        String base = holdId;
        String suffix = "";
        if (underscore > 0 && holdId.substring(underscore + 1).chars().allMatch(Character::isDigit)) {
            base = holdId.substring(0, underscore);
            suffix = " " + toRoman(Integer.parseInt(holdId.substring(underscore + 1)));
        }
        return Component.translatable("hold.skycraft." + base).append(suffix);
    }

    private static String toRoman(int n) {
        String[] r = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        int[] v = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < v.length; i++) {
            while (n >= v[i]) {
                sb.append(r[i]);
                n -= v[i];
            }
        }
        return sb.toString();
    }
}
