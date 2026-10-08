package com.skycraft.atmosphere.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;

import java.util.Random;

/**
 * Static sky meshes, built once on the render thread and drawn every frame: two star layers (twinkling in
 * counter-phase), the galaxy band, and the aurora curtains (several keyframes per band, cross-faded at draw time
 * so the folds drift without rebuilding geometry). Everything lies on a sphere of radius {@link #R} around the
 * camera, like the vanilla sky.
 */
final class SkyGeometry {
    static final float R = 100.0F;
    static final int AURORA_BANDS = 4;
    static final int AURORA_KEYS = 6;
    private static final int AURORA_SEGMENTS = 112;

    /** Galaxy plane normal in the celestial frame (the frame vanilla rotates the stars in). */
    private static final double[] GALAXY_N = normalize(0.28, 0.42, 0.86);

    static VertexBuffer starsA;
    static VertexBuffer starsB;
    static VertexBuffer galaxy;
    static final VertexBuffer[][] AURORA = new VertexBuffer[AURORA_BANDS][AURORA_KEYS];
    private static boolean built;

    private SkyGeometry() {}

    static void ensureBuilt() {
        if (built) return;
        built = true;
        starsA = upload(buildStars(new Random(0x5EC0DAL), 1700));
        starsB = upload(buildStars(new Random(0x3A55E1L), 1700));
        galaxy = upload(buildGalaxy());
        Random r = new Random(0xA0C0BAL);
        for (int b = 0; b < AURORA_BANDS; b++) {
            AuroraBand band = new AuroraBand(r, b);
            for (int k = 0; k < AURORA_KEYS; k++) {
                AURORA[b][k] = upload(band.build(k));
            }
        }
    }

    private static VertexBuffer upload(BufferBuilder.RenderedBuffer rendered) {
        VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
        vb.bind();
        vb.upload(rendered);
        VertexBuffer.unbind();
        return vb;
    }

    // ------------------------------------------------------------------ stars

    private static BufferBuilder.RenderedBuffer buildStars(Random r, int count) {
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        double[] a = perpendicular(GALAXY_N);
        double[] b = cross(GALAXY_N, a);
        for (int i = 0; i < count; i++) {
            double[] d;
            if (r.nextFloat() < 0.35F) {
                // crowd a third of the stars along the galaxy band
                double th = r.nextDouble() * Math.PI * 2;
                double off = r.nextGaussian() * 0.12;
                d = normalize(a[0] * Math.cos(th) + b[0] * Math.sin(th) + GALAXY_N[0] * off,
                        a[1] * Math.cos(th) + b[1] * Math.sin(th) + GALAXY_N[1] * off,
                        a[2] * Math.cos(th) + b[2] * Math.sin(th) + GALAXY_N[2] * off);
            } else {
                double z = r.nextDouble() * 2 - 1;
                double phi = r.nextDouble() * Math.PI * 2;
                double rr = Math.sqrt(1 - z * z);
                d = new double[]{rr * Math.cos(phi), z, rr * Math.sin(phi)};
            }
            float size = 0.10F + (float) Math.pow(r.nextFloat(), 7) * 0.42F;
            float alpha = 0.30F + 0.70F * r.nextFloat() * r.nextFloat();
            if (size > 0.3F) alpha = 1.0F;
            float cr, cg, cb;
            float t = r.nextFloat();
            if (t < 0.22F) { cr = 0.72F; cg = 0.82F; cb = 1.0F; }        // blue-white
            else if (t < 0.70F) { cr = 0.95F; cg = 0.96F; cb = 1.0F; }   // white
            else if (t < 0.90F) { cr = 1.0F; cg = 0.92F; cb = 0.74F; }   // yellow
            else { cr = 1.0F; cg = 0.72F; cb = 0.55F; }                  // orange giant
            quad(bb, d, size, cr, cg, cb, alpha);
        }
        return bb.end();
    }

    private static void quad(BufferBuilder bb, double[] d, float size, float r, float g, float b, float a) {
        double[] u = perpendicular(d);
        double[] v = cross(d, u);
        double cx = d[0] * R, cy = d[1] * R, cz = d[2] * R;
        double[][] corners = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
        for (double[] c : corners) {
            bb.vertex(cx + (u[0] * c[0] + v[0] * c[1]) * size,
                            cy + (u[1] * c[0] + v[1] * c[1]) * size,
                            cz + (u[2] * c[0] + v[2] * c[1]) * size)
                    .color(r, g, b, a).endVertex();
        }
    }

    // ------------------------------------------------------------------ galaxy

    private static BufferBuilder.RenderedBuffer buildGalaxy() {
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        double[] a = perpendicular(GALAXY_N);
        double[] b = cross(GALAXY_N, a);
        int seg = 96;
        int rows = 4;
        double halfWidth = 0.20;  // radians-ish, ~11 degrees each side
        float repeat = 3.0F;      // texture repeats around the circle (the texture tiles horizontally)
        for (int i = 0; i < seg; i++) {
            for (int j = 0; j < rows; j++) {
                double[][] pts = {
                        {i, j}, {i + 1, j}, {i + 1, j + 1}, {i, j + 1}
                };
                for (double[] p : pts) {
                    double th = p[0] / seg * Math.PI * 2;
                    double w = (p[1] / rows * 2 - 1) * halfWidth * (1.0 + 0.25 * Math.sin(th * 3 + 1.0));
                    double[] d = normalize(a[0] * Math.cos(th) + b[0] * Math.sin(th) + GALAXY_N[0] * w,
                            a[1] * Math.cos(th) + b[1] * Math.sin(th) + GALAXY_N[1] * w,
                            a[2] * Math.cos(th) + b[2] * Math.sin(th) + GALAXY_N[2] * w);
                    bb.vertex(d[0] * R, d[1] * R, d[2] * R).uv((float) (p[0] / seg) * repeat, (float) (p[1] / rows)).endVertex();
                }
            }
        }
        return bb.end();
    }

    // ------------------------------------------------------------------ aurora

    /** One curtain in the northern sky (Minecraft north is -Z); keyframes loop seamlessly. */
    private static final class AuroraBand {
        final double center, half, baseElev, height, foldAmp, foldFreq, hue;
        final double[] rayFreq = new double[4];
        final double[] rayPhase = new double[4];
        final int[] raySpeed = new int[4];
        final int foldSpeed;

        AuroraBand(Random r, int index) {
            center = Math.toRadians(-35 + 70 * r.nextDouble() + (index - 1.5) * 12);
            half = Math.toRadians(45 + 30 * r.nextDouble());
            baseElev = Math.toRadians(16 + 18 * r.nextDouble());
            height = Math.toRadians(16 + 20 * r.nextDouble());
            foldAmp = Math.toRadians(3 + 5 * r.nextDouble());
            foldFreq = 1.5 + 2.5 * r.nextDouble();
            hue = r.nextDouble();
            foldSpeed = r.nextBoolean() ? 1 : -1;
            for (int i = 0; i < 4; i++) {
                rayFreq[i] = 9 + r.nextDouble() * (20 + i * 25);
                rayPhase[i] = r.nextDouble() * Math.PI * 2;
                raySpeed[i] = (r.nextBoolean() ? 1 : -1) * (1 + r.nextInt(2));
            }
        }

        BufferBuilder.RenderedBuffer build(int key) {
            BufferBuilder bb = Tesselator.getInstance().getBuilder();
            bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            double kp = Math.PI * 2 * key / AURORA_KEYS;
            float[][] rowColor = {
                    {0.25F, 1.0F, 0.55F},                                           // bottom edge (transparent)
                    {0.30F, 1.0F, 0.50F},                                           // bright green hem
                    {(float) (0.10 + 0.2 * hue), 0.85F, (float) (0.65 + 0.2 * hue)}, // teal body
                    {0.55F, 0.30F, 0.95F}                                           // violet fringe (transparent)
            };
            float[] rowAlpha = {0.0F, 0.85F, 0.40F, 0.0F};
            double[] rowFrac = {0.0, 0.10, 0.45, 1.0};
            for (int i = 0; i < AURORA_SEGMENTS; i++) {
                for (int row = 0; row < 3; row++) {
                    int[][] pts = {{i, row}, {i, row + 1}, {i + 1, row + 1}, {i + 1, row}};
                    for (int[] p : pts) {
                        double s = (double) p[0] / AURORA_SEGMENTS;
                        double az = center - half + 2 * half * s + foldAmp * Math.sin(foldFreq * s * Math.PI * 2 + foldSpeed * kp);
                        double ends = Math.pow(Math.sin(Math.PI * s), 0.7);
                        double rays = 0.55;
                        for (int q = 0; q < 4; q++) {
                            rays += 0.45 / (q + 1) * Math.sin(rayFreq[q] * s + rayPhase[q] + raySpeed[q] * kp);
                        }
                        rays = Math.max(0.05, Math.min(1.0, rays));
                        double h = height * (0.75 + 0.35 * rays);
                        double el = baseElev + 0.03 * Math.sin(s * 5 + kp) + h * rowFrac[p[1]];
                        double x = Math.sin(az) * Math.cos(el);
                        double y = Math.sin(el);
                        double z = -Math.cos(az) * Math.cos(el);
                        float[] c = rowColor[p[1]];
                        float alpha = (float) (rowAlpha[p[1]] * ends * rays);
                        bb.vertex(x * R, y * R, z * R).color(c[0], c[1], c[2], alpha).endVertex();
                    }
                }
            }
            return bb.end();
        }
    }

    // ------------------------------------------------------------------ math

    private static double[] normalize(double x, double y, double z) {
        double l = Math.sqrt(x * x + y * y + z * z);
        return new double[]{x / l, y / l, z / l};
    }

    private static double[] cross(double[] a, double[] b) {
        return normalize(a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]);
    }

    private static double[] perpendicular(double[] d) {
        double[] up = Math.abs(d[1]) < 0.9 ? new double[]{0, 1, 0} : new double[]{1, 0, 0};
        return cross(d, up);
    }
}
