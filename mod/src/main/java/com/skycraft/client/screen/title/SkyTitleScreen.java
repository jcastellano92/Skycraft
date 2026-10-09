package com.skycraft.client.screen.title;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.LevelSummary;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Authentic Skyrim-themed Main Title Screen:
 * Dark Nordic slate aesthetic, gold dragon emblem styling,
 * Skyrim vertical menu (CONTINUE, NEW GAME, LOAD, SETTINGS, QUIT),
 * instant single-click continue of last save, and complete removal of vanilla Minecraft clutter.
 */
public class SkyTitleScreen extends Screen {
    private static final int COLOR_GOLD = 0xFFE8C060;
    private static final int COLOR_HOVER = 0xFFFFFFFF;
    private static final int COLOR_NORMAL = 0xFF9E9689;
    private static final int COLOR_DIM = 0xFF605A50;

    private enum MenuItem {
        CONTINUE("menu.skycraft.continue", "Continue Journey"),
        NEW_GAME("menu.skycraft.new_game", "New Game"),
        LOAD("menu.skycraft.load", "Load Game"),
        SETTINGS("menu.skycraft.settings", "Settings"),
        QUIT("menu.skycraft.quit", "Quit");

        final String key;
        final String fallback;

        MenuItem(String key, String fallback) {
            this.key = key;
            this.fallback = fallback;
        }

        Component getLabel() {
            return Component.translatableWithFallback(key, fallback);
        }
    }

    private final List<MenuItem> menuItems = new ArrayList<>();
    private int selectedIndex = 0;
    private LevelSummary mostRecentSave = null;
    private boolean savesLoaded = false;
    private float animTick = 0f;

    public SkyTitleScreen() {
        super(Component.literal("Skycraft"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    protected void init() {
        menuItems.clear();
        loadSavesAsync();
    }

    private void loadSavesAsync() {
        Minecraft mc = Minecraft.getInstance();
        LevelStorageSource source = mc.getLevelSource();
        try {
            LevelStorageSource.LevelCandidates candidates = source.findLevelCandidates();
            source.loadLevelSummaries(candidates).thenAccept(summaries -> {
                if (summaries != null && !summaries.isEmpty()) {
                    List<LevelSummary> sorted = new ArrayList<>(summaries);
                    sorted.sort((a, b) -> Long.compare(b.getLastPlayed(), a.getLastPlayed()));
                    this.mostRecentSave = sorted.get(0);
                } else {
                    this.mostRecentSave = null;
                }
                this.savesLoaded = true;
                rebuildMenuItems();
            }).exceptionally(ex -> {
                this.savesLoaded = true;
                this.mostRecentSave = null;
                rebuildMenuItems();
                return null;
            });
        } catch (Exception e) {
            this.savesLoaded = true;
            this.mostRecentSave = null;
            rebuildMenuItems();
        }
    }

    private void rebuildMenuItems() {
        menuItems.clear();
        if (mostRecentSave != null) {
            menuItems.add(MenuItem.CONTINUE);
        }
        menuItems.add(MenuItem.NEW_GAME);
        if (mostRecentSave != null) {
            menuItems.add(MenuItem.LOAD);
        }
        menuItems.add(MenuItem.SETTINGS);
        menuItems.add(MenuItem.QUIT);

        if (selectedIndex >= menuItems.size()) {
            selectedIndex = 0;
        }
    }

    @Override
    public void tick() {
        super.tick();
        animTick += 0.5f;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Deep Nordic slate background
        g.fillGradient(0, 0, width, height, 0xFF080808, 0xFF141210);

        // Subtle mountain / mist ambient layers at bottom
        for (int i = 0; i < 4; i++) {
            int alpha = (i + 1) * 8;
            int mistY = height - 40 - i * 16;
            g.fill(0, mistY, width, height, (alpha << 24) | 0x1E1A16);
        }

        // Title and Dragon Sigil styling
        int titleX = 40;
        int titleY = height / 5;

        // Skyrim Gold Title
        g.drawString(font, "S K Y C R A F T", titleX, titleY, COLOR_GOLD, true);
        g.drawString(font, "THE ELDER SCROLLS IN MINECRAFT", titleX, titleY + 12, 0xFF8A8272, false);

        // Decorative horizontal divider
        int divW = 160;
        g.fill(titleX, titleY + 25, titleX + divW, titleY + 26, COLOR_GOLD);
        g.fill(titleX + divW, titleY + 25, titleX + divW + 20, titleY + 26, 0x40E8C060);

        // Menu items layout
        int menuStartY = titleY + 42;
        int itemHeight = 22;

        if (menuItems.isEmpty()) {
            rebuildMenuItems();
        }

        for (int i = 0; i < menuItems.size(); i++) {
            MenuItem item = menuItems.get(i);
            int itemY = menuStartY + i * itemHeight;

            boolean isHovered = mouseX >= titleX - 10 && mouseX <= titleX + 180 && mouseY >= itemY && mouseY < itemY + itemHeight;
            if (isHovered && selectedIndex != i) {
                selectedIndex = i;
            }

            boolean isSelected = (selectedIndex == i);
            int textColor = isSelected ? COLOR_HOVER : COLOR_NORMAL;

            if (isSelected) {
                // Skyrim diamond / pip selection indicator
                g.drawString(font, "▶", titleX - 12, itemY + 2, COLOR_GOLD, false);
                g.fill(titleX - 2, itemY + itemHeight - 3, titleX + 130, itemY + itemHeight - 2, 0x50E8C060);
            }

            Component label = item.getLabel();
            if (item == MenuItem.CONTINUE && mostRecentSave != null) {
                String saveName = mostRecentSave.getLevelName();
                if (saveName.length() > 18) saveName = saveName.substring(0, 16) + "...";
                g.drawString(font, "CONTINUE", titleX, itemY + 1, textColor, isSelected);
                g.drawString(font, "(" + saveName + ")", titleX + font.width("CONTINUE") + 6, itemY + 2, COLOR_DIM, false);
            } else {
                g.drawString(font, label.getString().toUpperCase(java.util.Locale.ROOT), titleX, itemY + 1, textColor, isSelected);
            }
        }

        // Bottom version & lore footer
        g.drawString(font, "Skycraft v0.1.0 • Skyrim Adventure System", width - 210, height - 16, COLOR_DIM, false);

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            int titleX = 40;
            int menuStartY = height / 5 + 42;
            int itemHeight = 22;

            for (int i = 0; i < menuItems.size(); i++) {
                int itemY = menuStartY + i * itemHeight;
                if (mx >= titleX - 10 && mx <= titleX + 180 && my >= itemY && my < itemY + itemHeight) {
                    selectedIndex = i;
                    activateItem(menuItems.get(i));
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_W) {
            if (!menuItems.isEmpty()) {
                selectedIndex = Math.floorMod(selectedIndex - 1, menuItems.size());
                return true;
            }
        }
        if (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_S) {
            if (!menuItems.isEmpty()) {
                selectedIndex = Math.floorMod(selectedIndex + 1, menuItems.size());
                return true;
            }
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_SPACE) {
            if (selectedIndex >= 0 && selectedIndex < menuItems.size()) {
                activateItem(menuItems.get(selectedIndex));
                return true;
            }
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            confirmQuit();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    private void activateItem(MenuItem item) {
        Minecraft mc = Minecraft.getInstance();
        switch (item) {
            case CONTINUE -> {
                if (mostRecentSave != null) {
                    mc.createWorldOpenFlows().loadLevel(this, mostRecentSave.getLevelId());
                }
            }
            case NEW_GAME -> {
                CreateWorldScreen.openFresh(mc, this);
            }
            case LOAD -> {
                mc.setScreen(new SkyLoadWorldScreen(this));
            }
            case SETTINGS -> {
                mc.setScreen(new SkyOptionsScreen(this, mc.options));
            }
            case QUIT -> {
                confirmQuit();
            }
        }
    }

    private void confirmQuit() {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new ConfirmScreen(
                confirm -> {
                    if (confirm) {
                        mc.stop();
                    } else {
                        mc.setScreen(this);
                    }
                },
                Component.translatableWithFallback("menu.skycraft.quit_confirm_title", "Quit Skycraft?"),
                Component.translatableWithFallback("menu.skycraft.quit_confirm_msg", "Return to desktop?"),
                CommonComponents.GUI_PROCEED,
                CommonComponents.GUI_CANCEL
        ));
    }
}
