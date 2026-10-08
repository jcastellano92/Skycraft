package com.skycraft.combat;

import net.minecraft.world.entity.player.Player;

/**
 * Contract 8 (docs/PLAYTEST-1.md): whether a player's weapon is sheathed. Synced, so it works on both sides.
 * Guards treat a sheathed or empty-handed player as yielding.
 *
 * <p>STUB: workstream D replaces the body; the signature is fixed.
 */
public final class Sheathe {
    private Sheathe() {}

    public static boolean isSheathed(Player player) {
        return player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty();
    }
}
