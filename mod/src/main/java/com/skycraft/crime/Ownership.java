package com.skycraft.crime;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Contract 1 (docs/PLAYTEST-1.md): who owns a block or a placed world item. Owner ids are {@code npc:<uuid>},
 * {@code faction:<id>}, {@code player:<uuid>} or {@code public}. A placed {@code ItemEntity} is owned when its
 * persistent data holds the string {@code skycraft_owner}.
 *
 * <p>STUB: workstream G replaces the bodies; the signatures are fixed.
 */
public final class Ownership {
    public static final String ENTITY_OWNER_KEY = "skycraft_owner";

    private Ownership() {}

    /** True if using or taking from this block is a crime (or not allowed) for this player. Works on both sides. */
    public static boolean isOwnedByOther(Player player, Level level, BlockPos pos) {
        return false;
    }

    /** True if taking this placed world item is a crime for this player. */
    public static boolean isOwnedByOther(Player player, Entity entity) {
        return false;
    }

    public static void setOwner(ServerLevel level, BlockPos pos, String ownerId, Component ownerName) {
    }

    public static void setPlayerOwner(ServerLevel level, BlockPos pos, UUID player) {
    }

    @Nullable
    public static Component ownerName(Level level, BlockPos pos) {
        return null;
    }
}
