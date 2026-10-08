package com.skycraft.crime.client;

import com.skycraft.crime.CrimePackets;
import com.skycraft.network.SkyNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Skyrim pickpocket menu: the NPC's pockets with a success chance per item. Click an item to try to steal it. */
public class PickpocketScreen extends Screen {
    private static final int ROW = 20;
    private static final int PANEL_W = 230;

    private final int entityId;
    private List<CrimePackets.PocketEntry> entries;
    private int hovered = -1;
    private int selected = 0;
    private int cooldown;

    public PickpocketScreen(CrimePackets.OpenPickpocket m) {
        super(Component.translatable("screen.skycraft.pickpocket.title", m.name()));
        this.entityId = m.entityId();
        this.entries = m.entries();
    }

    public int entityId() {
        return entityId;
    }

    void update(CrimePackets.OpenPickpocket m) {
        this.entries = m.entries();
        this.cooldown = 0;
        if (selected >= entries.size()) selected = Math.max(0, entries.size() - 1);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        if (cooldown > 0) cooldown--;
        if (minecraft == null || minecraft.player == null || minecraft.level == null) return;
        Entity e = minecraft.level.getEntity(entityId);
        if (e == null || !e.isAlive() || e.distanceToSqr(minecraft.player) > 25) onClose();
    }

    private int panelH() {
        return 44 + Math.max(1, entries.size()) * ROW;
    }

    private int left() {
        return (width - PANEL_W) / 2;
    }

    private int top() {
        return (height - panelH()) / 2;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int x = left();
        int y = top();
        int h = panelH();
        g.fill(x - 1, y - 1, x + PANEL_W + 1, y + h + 1, 0xFF8A7F66);
        g.fill(x, y, x + PANEL_W, y + h, 0xE0101010);
        g.drawCenteredString(font, title, width / 2, y + 7, 0xFFF5EBC8);
        g.fill(x + 10, y + 19, x + PANEL_W - 10, y + 20, 0x808A7F66);

        hovered = -1;
        int rowY = y + 24;
        if (entries.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("screen.skycraft.pickpocket.empty"), width / 2, rowY + 6, 0xFFA8A090);
        }
        for (int i = 0; i < entries.size(); i++) {
            CrimePackets.PocketEntry entry = entries.get(i);
            int ry = rowY + i * ROW;
            boolean over = mouseX >= x + 4 && mouseX < x + PANEL_W - 4 && mouseY >= ry && mouseY < ry + ROW;
            if (over) hovered = i;
            boolean hi = over || hovered < 0 && selected == i;
            if (hi) g.fill(x + 4, ry, x + PANEL_W - 4, ry + ROW, 0x30FFFFFF);
            ItemStack stack = entry.stack();
            g.renderItem(stack, x + 8, ry + 2);
            g.renderItemDecorations(font, stack, x + 8, ry + 2);
            g.drawString(font, stack.getHoverName(), x + 30, ry + 6, hi ? 0xFFFFFFFF : 0xFFD8D0B8, true);
            int chance = entry.chance();
            int color = chance >= 70 ? 0xFF7FD07F : chance >= 40 ? 0xFFE8D070 : 0xFFE06060;
            String pct = chance + "%";
            g.drawString(font, pct, x + PANEL_W - 10 - font.width(pct), ry + 6, color, true);
        }
        g.drawCenteredString(font, Component.translatable("screen.skycraft.pickpocket.hint"), width / 2, y + h - 12, 0xFF8A8270);
        super.render(g, mouseX, mouseY, partialTick);
        if (hovered >= 0) {
            ItemStack stack = entries.get(hovered).stack();
            if (mouseX < x + 28) g.renderTooltip(font, stack, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (hovered >= 0 && button == 0) {
            steal(hovered);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        int n = entries.size();
        if (n > 0 && (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_W)) selected = (selected + n - 1) % n;
        else if (n > 0 && (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_S)) selected = (selected + 1) % n;
        else if (n > 0 && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_SPACE)) steal(selected);
        else if (minecraft != null && minecraft.options.keyInventory.matches(key, scan)) onClose();
        else return super.keyPressed(key, scan, mods);
        return true;
    }

    private void steal(int index) {
        if (cooldown > 0 || index < 0 || index >= entries.size()) return;
        cooldown = 5;
        SkyNetwork.sendToServer(new CrimePackets.PickpocketTake(entityId, index));
    }
}
