package com.skycraft.dungeons.world;

import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;

/**
 * Surface entrances at the top of the stair tunnel: the Nordic arch set into a burial mound, the bronze Dwemer
 * gatehouse, a rocky cave mouth (dressed for bandits, beasts, vampires, necromancers or hagravens) and a timbered
 * mine adit with rails running out of it.
 */
public final class Portals {
    private Portals() {}

    /** Paints with tunnel-frame coordinates (t along the tunnel, lat sideways). */
    private record Frame(Painter p, DungeonPiece c) {
        int x(int t, int lat) {
            return c.frameX(t, lat);
        }

        int z(int t, int lat) {
            return c.frameZ(t, lat);
        }

        void set(int t, int y, int lat, BlockState s) {
            p.set(x(t, lat), y, z(t, lat), s);
        }

        void setIfOpen(int t, int y, int lat, BlockState s) {
            p.setIfOpen(x(t, lat), y, z(t, lat), s);
        }

        void air(int t, int y, int lat) {
            p.air(x(t, lat), y, z(t, lat));
        }

        void foundation(int t, int topY, int lat, BlockState s) {
            p.foundation(x(t, lat), topY, z(t, lat), s, 9);
        }

        void clear(int t, int fromY, int lat, int toY) {
            p.clear(x(t, lat), fromY, z(t, lat), toY);
        }
    }

    public static void build(Painter p, DungeonPiece c, RandomSource r) {
        Frame f = new Frame(p, c);
        switch (c.theme) {
            case BARROW -> barrow(f, c);
            case DWEMER -> dwemer(f, c);
            case MINE -> {
                outcrop(f, c);
                mine(f, c);
            }
            default -> {
                outcrop(f, c);
                caveMouth(f, c);
            }
        }
    }

    // ------------------------------------------------------------------ shared

    /** Clears a 5-wide approach in front of the entrance and gives it a floor. */
    private static void approach(Frame f, DungeonPiece c, int halfWidth, int length, BlockState floor, boolean pave) {
        int T = c.tunnelLength(), y = c.aux;
        for (int t = T + 1; t <= T + length; t++) {
            for (int lat = -halfWidth; lat <= halfWidth; lat++) {
                f.clear(t, y, lat, y + 5);
                if (pave) {
                    f.set(t, y - 1, lat, floor);
                    f.foundation(t, y - 2, lat, Blocks.COBBLESTONE.defaultBlockState());
                } else {
                    f.setIfOpen(t, y - 1, lat, floor);
                    f.foundation(t, y - 2, lat, Blocks.DIRT.defaultBlockState());
                }
            }
        }
    }

    /** A burial mound / hill over the last stretch of the tunnel so the entrance sits in the ground. */
    private static void mound(Frame f, DungeonPiece c, boolean stone) {
        int T = c.tunnelLength(), y = c.aux;
        for (int t = T - 3; t <= T; t++) {
            int ft = c.tunnelFloor(t);
            for (int lat = -5; lat <= 5; lat++) {
                int top = y + 4 - Math.max(0, Math.abs(lat) - 2) - Math.max(0, t - (T - 1));
                for (int yy = y - 1; yy <= top; yy++) {
                    if (Math.abs(lat) <= 2 && yy <= ft + 3) continue;
                    BlockState s = stone ? (f.p().chance(f.x(t, lat), yy, f.z(t, lat), 300, 3) ? Blocks.ANDESITE.defaultBlockState() : Blocks.STONE.defaultBlockState())
                            : yy == top ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.DIRT.defaultBlockState();
                    f.setIfOpen(t, yy, lat, s);
                }
                if (Math.abs(lat) > 2) f.foundation(t, y - 2, lat, Blocks.DIRT.defaultBlockState());
            }
        }
    }

    // ------------------------------------------------------------------ Nordic barrow

    private static void barrow(Frame f, DungeonPiece c) {
        int T = c.tunnelLength(), y = c.aux;
        Painter p = f.p();
        Direction out = c.facing(), right = c.right();
        mound(f, c, false);
        // weathered stone platform
        for (int t = T + 1; t <= T + 6; t++) {
            for (int lat = -4; lat <= 4; lat++) {
                f.clear(t, y, lat, y + 7);
                f.set(t, y - 1, lat, BuiltBuilder.floor(p, Theme.FORT, f.x(t, lat), y - 1, f.z(t, lat)));
                f.foundation(t, y - 2, lat, Blocks.COBBLESTONE.defaultBlockState());
            }
        }
        // the arch
        int at = T + 1;
        for (int lat = -3; lat <= 3; lat++) {
            int a = Math.abs(lat);
            for (int yy = y; yy <= y + 7; yy++) {
                boolean opening = a <= 1 && yy <= y + 2;
                if (opening) {
                    f.air(at, yy, lat);
                    continue;
                }
                int peak = y + 7 - a;
                if (yy > peak) continue;
                BlockState s;
                if (yy == peak) s = a == 0 ? Deco.slab(Blocks.STONE_BRICK_SLAB, false) : Deco.stair(Blocks.STONE_BRICK_STAIRS, lat < 0 ? right : right.getOpposite(), false);
                else if (a == 3 || (a <= 1 && yy == y + 3)) s = Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
                else s = BuiltBuilder.wall(p, Theme.BARROW, f.x(at, lat), yy, f.z(at, lat), 1);
                f.set(at, yy, lat, s);
            }
        }
        // dragon-horn finials curling out of the gable
        for (int s : new int[]{-1, 1}) {
            Direction side = s < 0 ? right.getOpposite() : right;
            f.set(at, y + 5, 4 * s, Deco.stair(Blocks.STONE_BRICK_STAIRS, side.getOpposite(), true));
            f.set(at, y + 6, 4 * s, Deco.stair(Blocks.STONE_BRICK_STAIRS, side, false));
            f.set(at + 1, y + 4, 4 * s, Deco.stair(Blocks.STONE_BRICK_STAIRS, out.getOpposite(), true));
        }
        // braziers and urns
        for (int s : new int[]{-4, 4}) {
            f.set(T + 5, y, s, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
            f.set(T + 5, y + 1, s, Deco.campfire(false, true));
            f.set(T + 2, y, s - Integer.signum(s), Blocks.DECORATED_POT.defaultBlockState());
        }
        // standing stones along the path
        for (int s : new int[]{-3, 3}) {
            for (int yy = y; yy <= y + 1; yy++) f.set(T + 7, yy, s, Blocks.MOSSY_STONE_BRICKS.defaultBlockState());
            f.set(T + 7, y + 2, s, Deco.slab(Blocks.MOSSY_STONE_BRICK_SLAB, false));
        }
    }

    // ------------------------------------------------------------------ Dwemer gatehouse

    private static void dwemer(Frame f, DungeonPiece c) {
        int T = c.tunnelLength(), y = c.aux;
        Painter p = f.p();
        Direction right = c.right();
        mound(f, c, true);
        for (int t = T + 1; t <= T + 6; t++) {
            for (int lat = -4; lat <= 4; lat++) {
                f.clear(t, y, lat, y + 8);
                boolean border = Math.abs(lat) == 4 || t == T + 6;
                f.set(t, y - 1, lat, border ? Blocks.WAXED_CUT_COPPER.defaultBlockState() : Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
                f.foundation(t, y - 2, lat, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
            }
        }
        int at = T + 1;
        for (int lat = -3; lat <= 3; lat++) {
            int a = Math.abs(lat);
            for (int yy = y; yy <= y + 6; yy++) {
                if (a <= 1 && yy <= y + 2) {
                    f.air(at, yy, lat);
                    continue;
                }
                BlockState s;
                if (a >= 2 && yy <= y + 4) s = a == 3 ? Blocks.WAXED_COPPER_BLOCK.defaultBlockState() : Blocks.WAXED_EXPOSED_CUT_COPPER.defaultBlockState();
                else if (yy == y + 3) s = Blocks.GILDED_BLACKSTONE.defaultBlockState();
                else if (yy == y + 5) s = a <= 2 ? Blocks.WAXED_WEATHERED_CUT_COPPER.defaultBlockState() : Deco.slab(Blocks.WAXED_CUT_COPPER_SLAB, false);
                else if (yy == y + 6) s = a == 0 ? Blocks.OCHRE_FROGLIGHT.defaultBlockState() : a == 1 ? Deco.slab(Blocks.WAXED_CUT_COPPER_SLAB, false) : Blocks.AIR.defaultBlockState();
                else s = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
                f.set(at, yy, lat, s);
            }
        }
        for (int s : new int[]{-3, 3}) {
            f.set(at, y + 5, s, Blocks.WAXED_COPPER_BLOCK.defaultBlockState());
            f.set(at, y + 6, s, DwemerRooms.rod());
            f.set(at, y + 7, s, DwemerRooms.rod());
            f.set(at + 1, y + 2, s, Deco.lantern(false, false));
            f.set(at + 1, y + 1, s, Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState());
            f.set(at + 1, y, s, Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState());
        }
        // steam vents on the forecourt
        for (int s : new int[]{-3, 3}) {
            f.set(T + 4, y - 1, s, Deco.campfire(false, true));
            f.set(T + 4, y, s, Deco.trapdoor(Blocks.IRON_TRAPDOOR, right, false, false));
        }
        // a broken pipe column
        f.set(T + 6, y, -2, Blocks.WAXED_EXPOSED_COPPER.defaultBlockState());
        f.set(T + 6, y + 1, -2, DwemerRooms.rod());
        p.spawn(Mobs.DWARVEN_SPIDER, f.x(T + 3, 0), y, f.z(T + 3, 0), Painter.yaw(c.facing()), null);
    }

    // ------------------------------------------------------------------ caves and lairs

    /** A rocky knoll the cave mouth opens out of. */
    private static void outcrop(Frame f, DungeonPiece c) {
        int T = c.tunnelLength(), y = c.aux;
        Painter p = f.p();
        double ct = T - 0.5;
        for (int t = T - 3; t <= T + 2; t++) {
            for (int lat = -6; lat <= 6; lat++) {
                double dt = (t - ct) / 3.6, dl = lat / 5.8;
                double d = dt * dt + dl * dl;
                if (d >= 1.0) continue;
                int x = f.x(t, lat), z = f.z(t, lat);
                int h = y + (int) Math.round((1.0 - d) * 6.0 + p.noise(x, y, z, 3.0, 310) * 1.5);
                for (int yy = y - 1; yy <= h; yy++) {
                    if (Math.abs(lat) <= 1 && yy <= y + 2) continue;
                    if (Math.abs(lat) <= 1 && t <= T && yy <= c.tunnelFloor(t) + 2) continue;
                    int roll = p.roll(x, yy, z, 311, 10);
                    BlockState s = yy == h && roll < 4 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState()
                            : roll < 2 ? Blocks.COBBLESTONE.defaultBlockState() : roll < 3 ? Blocks.ANDESITE.defaultBlockState()
                            : roll < 4 ? Blocks.TUFF.defaultBlockState() : Blocks.STONE.defaultBlockState();
                    f.setIfOpen(t, yy, lat, s);
                }
                f.foundation(t, y - 2, lat, Blocks.STONE.defaultBlockState());
            }
        }
        // keep the mouth itself open
        for (int t = T - 1; t <= T + 3; t++) {
            for (int lat = -1; lat <= 1; lat++) f.clear(t, y, lat, y + 2);
        }
        approach(f, c, 1, 5, Blocks.COARSE_DIRT.defaultBlockState(), false);
    }

    private static void caveMouth(Frame f, DungeonPiece c) {
        int T = c.tunnelLength(), y = c.aux;
        Painter p = f.p();
        switch (c.theme) {
            case VAMPIRE_LAIR -> {
                f.set(T + 2, y, -2, Deco.candle(Blocks.RED_CANDLE, 3, true));
                f.set(T + 2, y, 2, Deco.skull(Blocks.SKELETON_SKULL, 0));
            }
            case NECROMANCER_LAIR -> {
                for (int s : new int[]{-2, 2}) {
                    f.set(T + 2, y, s, Blocks.DARK_OAK_FENCE.defaultBlockState());
                    f.set(T + 2, y + 1, s, Deco.skull(Blocks.SKELETON_SKULL, Deco.skullRotation(c.facing())));
                }
                f.set(T + 1, y + 2, 0, Deco.lantern(true, true));
            }
            case HAGRAVEN_LAIR -> {
                f.set(T + 2, y, -2, Deco.pillar(Blocks.BONE_BLOCK, Direction.Axis.Y));
                f.set(T + 2, y + 1, -2, Blocks.WHITE_CARPET.defaultBlockState());
                f.set(T + 3, y, 2, Blocks.MANGROVE_ROOTS.defaultBlockState());
            }
            default -> {
                if (c.flavor % 2 == 0) {
                    // bandit lookout: torches and a sentry
                    for (int s : new int[]{-2, 2}) {
                        f.set(T + 2, y, s, Blocks.SPRUCE_FENCE.defaultBlockState());
                        f.set(T + 2, y + 1, s, Blocks.TORCH.defaultBlockState());
                    }
                    p.spawn(Mobs.bandit(), f.x(T + 3, 0), y, f.z(T + 3, 0), Painter.yaw(c.facing()), null);
                } else {
                    f.set(T + 2, y, 2, Deco.pillar(Blocks.BONE_BLOCK, Direction.Axis.X));
                    f.set(T + 3, y, -2, Deco.skull(Blocks.SKELETON_SKULL, 6));
                }
            }
        }
    }

    // ------------------------------------------------------------------ mine adit

    private static void mine(Frame f, DungeonPiece c) {
        int T = c.tunnelLength(), y = c.aux;
        Painter p = f.p();
        Direction out = c.facing(), right = c.right();
        // timber portal
        int at = T + 1;
        for (int s : new int[]{-2, 2}) {
            for (int yy = y; yy <= y + 2; yy++) f.set(at, yy, s, Deco.pillar(Blocks.SPRUCE_LOG, Direction.Axis.Y));
        }
        for (int lat = -3; lat <= 3; lat++) f.set(at, y + 3, lat, Deco.pillar(Blocks.STRIPPED_SPRUCE_LOG, right.getAxis()));
        for (int lat = -2; lat <= 2; lat++) f.set(at, y + 4, lat, Deco.slab(Blocks.SPRUCE_SLAB, false));
        f.set(at, y + 2, 0, Deco.lantern(false, true));
        // rails running out onto the spoil heap
        RailShape shape = out.getAxis() == Direction.Axis.X ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH;
        for (int t = T + 1; t <= T + 5; t++) f.set(t, y, 0, Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, shape));
        p.chestMinecart(f.x(T + 4, 0), y, f.z(T + 4, 0), Deco.LOOT_MINE);
        f.set(T + 3, y, 2, Blocks.BARREL.defaultBlockState());
        f.set(T + 4, y, 2, p.chance(f.x(T, 0), y, f.z(T, 0), 320, 2) ? Blocks.RAW_IRON_BLOCK.defaultBlockState() : Blocks.COAL_BLOCK.defaultBlockState());
        f.set(T + 3, y, -2, Blocks.TUFF.defaultBlockState());
        f.set(T + 4, y, -2, Blocks.COBBLESTONE.defaultBlockState());
        f.set(T + 4, y + 1, -2, Deco.slab(Blocks.COBBLESTONE_SLAB, false));
    }
}
