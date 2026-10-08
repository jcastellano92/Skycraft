package com.skycraft.arsenal.client;

import com.skycraft.arsenal.ArsenalPackets.Fx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Draws staff and artifact visuals sent as {@link Fx} packets. */
public final class ArsenalFxClient {
    private ArsenalFxClient() {}

    private static DustParticleOptions dust(float r, float g, float b, float s) {
        return new DustParticleOptions(new Vector3f(r, g, b), s);
    }

    public static void play(byte kind, Vec3 a, Vec3 b) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        RandomSource r = level.random;
        switch (kind) {
            case Fx.FIRE_STREAM -> stream(level, r, a, b, ParticleTypes.FLAME, ParticleTypes.SMOKE);
            case Fx.FROST_STREAM -> stream(level, r, a, b, ParticleTypes.SNOWFLAKE, dust(0.75f, 0.9f, 1f, 1.2f));
            case Fx.SHOCK_STREAM -> {
                stream(level, r, a, b, ParticleTypes.ELECTRIC_SPARK, dust(0.7f, 0.8f, 1f, 0.9f));
                arc(level, r, a, b, dust(0.85f, 0.9f, 1f, 0.8f), 0.35);
            }
            case Fx.HEAL_BEAM -> beam(level, r, a, b, dust(1f, 0.92f, 0.55f, 1.1f), ParticleTypes.HAPPY_VILLAGER);
            case Fx.MAGNUS_BEAM -> beam(level, r, a, b, dust(0.45f, 0.6f, 1f, 1.3f), ParticleTypes.ENCHANT);
            case Fx.LIGHTNING_ARC -> {
                arc(level, r, a, b, dust(0.8f, 0.85f, 1f, 1.2f), 0.6);
                arc(level, r, a, b, ParticleTypes.ELECTRIC_SPARK, 0.5);
            }
            case Fx.FIRE_BURST -> burst(level, r, a, ParticleTypes.FLAME, 60, 0.35);
            case Fx.SUN_BURST -> {
                burst(level, r, a, dust(1f, 0.95f, 0.6f, 1.6f), 50, 0.3);
                burst(level, r, a, ParticleTypes.END_ROD, 25, 0.2);
                level.addParticle(ParticleTypes.FLASH, a.x, a.y, a.z, 0, 0, 0);
            }
            case Fx.FROST_BURST -> burst(level, r, a, ParticleTypes.SNOWFLAKE, 20, 0.15);
            case Fx.SOUL_BURST -> burst(level, r, a, ParticleTypes.SOUL, 20, 0.08);
            case Fx.WARD -> ward(level, r, a, b);
            default -> {
            }
        }
    }

    /** A cone of particles flying from a to b (flames, frostbite, sparks). */
    private static void stream(ClientLevel level, RandomSource r, Vec3 a, Vec3 b, ParticleOptions main, ParticleOptions extra) {
        Vec3 d = b.subtract(a);
        double len = Math.max(0.5, d.length());
        Vec3 dir = d.scale(1 / len);
        for (int i = 0; i < 10; i++) {
            double speed = 0.35 + r.nextDouble() * 0.35;
            double spread = 0.08;
            level.addParticle(main, a.x, a.y, a.z,
                    (dir.x + (r.nextDouble() - 0.5) * spread * 2) * speed * len / 4,
                    (dir.y + (r.nextDouble() - 0.5) * spread * 2) * speed * len / 4,
                    (dir.z + (r.nextDouble() - 0.5) * spread * 2) * speed * len / 4);
        }
        for (int i = 0; i < 3; i++) {
            double f = r.nextDouble();
            level.addParticle(extra, a.x + d.x * f, a.y + d.y * f, a.z + d.z * f, 0, 0.01, 0);
        }
    }

    /** A straight line with a sparkle at the end (healing, Magnus). */
    private static void beam(ClientLevel level, RandomSource r, Vec3 a, Vec3 b, ParticleOptions line, ParticleOptions end) {
        Vec3 d = b.subtract(a);
        int n = Math.max(2, (int) (d.length() * 4));
        for (int i = 0; i <= n; i++) {
            double f = i / (double) n;
            level.addParticle(line, a.x + d.x * f + (r.nextDouble() - 0.5) * 0.05, a.y + d.y * f + (r.nextDouble() - 0.5) * 0.05,
                    a.z + d.z * f + (r.nextDouble() - 0.5) * 0.05, 0, 0, 0);
        }
        for (int i = 0; i < 3; i++) level.addParticle(end, b.x + (r.nextDouble() - 0.5) * 0.4, b.y + (r.nextDouble() - 0.5) * 0.4,
                b.z + (r.nextDouble() - 0.5) * 0.4, 0, 0, 0);
    }

    /** A jagged lightning line. */
    private static void arc(ClientLevel level, RandomSource r, Vec3 a, Vec3 b, ParticleOptions p, double jag) {
        Vec3 d = b.subtract(a);
        int n = Math.max(3, (int) (d.length() * 3));
        Vec3 prev = a;
        for (int i = 1; i <= n; i++) {
            double f = i / (double) n;
            Vec3 next = i == n ? b : a.add(d.scale(f)).add((r.nextDouble() - 0.5) * jag, (r.nextDouble() - 0.5) * jag, (r.nextDouble() - 0.5) * jag);
            for (int k = 0; k < 3; k++) {
                double t = k / 3.0;
                level.addParticle(p, prev.x + (next.x - prev.x) * t, prev.y + (next.y - prev.y) * t, prev.z + (next.z - prev.z) * t, 0, 0, 0);
            }
            prev = next;
        }
    }

    private static void burst(ClientLevel level, RandomSource r, Vec3 c, ParticleOptions p, int count, double speed) {
        for (int i = 0; i < count; i++) {
            double x = r.nextGaussian(), y = r.nextGaussian(), z = r.nextGaussian();
            double l = Math.sqrt(x * x + y * y + z * z) + 1e-6;
            double s = speed * (0.5 + r.nextDouble() * 0.5);
            level.addParticle(p, c.x, c.y, c.z, x / l * s, y / l * s, z / l * s);
        }
    }

    /** A disc of light in front of the blocker ({@code normal} = facing). */
    private static void ward(ClientLevel level, RandomSource r, Vec3 c, Vec3 normal) {
        Vec3 n = normal.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : normal.normalize();
        Vec3 u = n.cross(new Vec3(0, 1, 0));
        if (u.lengthSqr() < 1e-6) u = new Vec3(1, 0, 0);
        u = u.normalize();
        Vec3 v = u.cross(n).normalize();
        for (int i = 0; i < 40; i++) {
            double ang = i / 40.0 * Math.PI * 2;
            double rad = 0.9;
            Vec3 p = c.add(u.scale(Math.cos(ang) * rad)).add(v.scale(Math.sin(ang) * rad));
            level.addParticle(dust(0.6f, 0.75f, 1f, 1f), p.x, p.y, p.z, 0, 0, 0);
        }
        for (int i = 0; i < 12; i++) {
            level.addParticle(ParticleTypes.ENCHANTED_HIT, c.x + (r.nextDouble() - 0.5), c.y + (r.nextDouble() - 0.5), c.z + (r.nextDouble() - 0.5),
                    n.x * 0.2, n.y * 0.2, n.z * 0.2);
        }
    }
}
