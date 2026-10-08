package com.skycraft.inventory.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.Locale;

/** Skyrim menu drawing helpers shared by the inventory screens: dark translucent panels, thin gold lines. */
final class SkyUi {
    static final int TEXT = 0xFFE8E0C8;
    static final int BRIGHT = 0xFFFFFFFF;
    static final int DIM = 0xFF8A8478;
    static final int HEADER = 0xFFA89F86;
    static final int GOLD = 0xFFE8C060;
    static final int LINE = 0xFFC8BC9A;
    static final int TRIM = 0xFF8A7F66;
    static final int STOLEN = 0xFFD06050;
    static final int BAD = 0xFFE05050;
    static final int LEFT_HAND = 0xFF9FC4E8;

    private SkyUi() {}

    /** A dark translucent panel with faint gold lines on top and bottom. */
    static void panel(GuiGraphics g, int x1, int y1, int x2, int y2) {
        g.fillGradient(x1, y1, x2, y2, 0x90000000, 0xA8000000);
        hline(g, x1, x2, y1, 0x70);
        hline(g, x1, x2, y2 - 1, 0x70);
    }

    /** A thin gold line that fades out at both ends. */
    static void hline(GuiGraphics g, int x1, int x2, int y, int alpha) {
        int w = x2 - x1;
        if (w <= 0) return;
        int fade = Math.min(24, w / 4);
        int rgb = LINE & 0xFFFFFF;
        for (int i = 0; i < fade; i++) {
            int a = alpha * (i + 1) / (fade + 1);
            g.fill(x1 + i, y, x1 + i + 1, y + 1, a << 24 | rgb);
            g.fill(x2 - i - 1, y, x2 - i, y + 1, a << 24 | rgb);
        }
        g.fill(x1 + fade, y, x2 - fade, y + 1, alpha << 24 | rgb);
    }

    /** Divider with a small diamond in the middle (Skyrim menu ornament). */
    static void divider(GuiGraphics g, int cx, int y, int half) {
        hline(g, cx - half, cx + half, y, 0xB0);
        diamond(g, cx, y, 2, 0xFFC8BC9A);
    }

    static void diamond(GuiGraphics g, int cx, int cy, int r, int color) {
        for (int i = -r; i <= r; i++) {
            int w = r - Math.abs(i);
            g.fill(cx - w, cy + i, cx + w + 1, cy + i + 1, color);
        }
    }

    /** Hollow diamond (left-hand marker). */
    static void diamondOutline(GuiGraphics g, int cx, int cy, int r, int color) {
        for (int i = -r; i <= r; i++) {
            int w = r - Math.abs(i);
            g.fill(cx - w, cy + i, cx - w + 1, cy + i + 1, color);
            g.fill(cx + w, cy + i, cx + w + 1, cy + i + 1, color);
        }
    }

    private static final String[] STAR = {"..#..", "#####", ".###.", ".#.#.", "#...#"};

    /** 5x5 pixel star (favorite marker). */
    static void star(GuiGraphics g, int x, int y, int color) {
        for (int r = 0; r < 5; r++) {
            for (int c = 0; c < 5; c++) {
                if (STAR[r].charAt(c) == '#') g.fill(x + c, y + r, x + c + 1, y + r + 1, color);
            }
        }
    }

    /** Small triangle pointing up or down (sort direction). */
    static void triangle(GuiGraphics g, int cx, int y, boolean up, int color) {
        for (int i = 0; i < 3; i++) {
            int row = up ? i : 2 - i;
            g.fill(cx - row, y + i, cx + row + 1, y + i + 1, color);
        }
    }

    /** Upper-case header text ("small caps" look). */
    static String caps(Component c) {
        return c.getString().toUpperCase(Locale.ROOT);
    }

    static void caps(GuiGraphics g, Font font, Component c, int x, int y, int color) {
        g.drawString(font, caps(c), x, y, color, false);
    }

    static void capsCentered(GuiGraphics g, Font font, Component c, int cx, int y, int color) {
        String s = caps(c);
        g.drawString(font, s, cx - font.width(s) / 2, y, color, false);
    }

    /** Thin Skyrim vitals bar (fill shrinks toward the center). */
    static void bar(GuiGraphics g, int x, int y, int w, float fill, int color) {
        fill = Mth.clamp(fill, 0f, 1f);
        g.fill(x - 1, y - 1, x + w + 1, y + 4, 0xFF141414);
        g.fill(x, y, x + w, y + 3, 0xFF2A2A2A);
        int filled = Math.round(w * fill);
        int left = x + (w - filled) / 2;
        g.fill(left, y, left + filled, y + 3, 0xFF000000 | color);
        g.fill(x - 3, y, x - 1, y + 3, TRIM);
        g.fill(x + w + 1, y, x + w + 3, y + 3, TRIM);
    }

    static String ellipsize(Font font, String s, int width) {
        if (font.width(s) <= width) return s;
        if (width <= font.width("...")) return "";
        return font.plainSubstrByWidth(s, width - font.width("...")) + "...";
    }

    static boolean inside(double mx, double my, int x1, int y1, int x2, int y2) {
        return mx >= x1 && mx < x2 && my >= y1 && my < y2;
    }
}
