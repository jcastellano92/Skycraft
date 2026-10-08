package com.skycraft.combat;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Contract 9 (docs/PLAYTEST-1.md): the player's rest point (last bed, inn or owned house). Respawn goes there,
 * otherwise to the world spawn.
 *
 * <p>STUB: workstream D replaces the body; the signature is fixed.
 */
public final class RespawnPoints {
    private RespawnPoints() {}

    public static void set(ServerPlayer player, ResourceKey<Level> dim, BlockPos pos) {
        player.setRespawnPosition(dim, pos, player.getYRot(), true, false);
    }
}
