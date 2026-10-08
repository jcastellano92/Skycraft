package com.skycraft.economy.client;

import com.skycraft.economy.Barter;
import com.skycraft.economy.EconomyPackets;
import com.skycraft.economy.ItemCategory;
import com.skycraft.network.SkyNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Skyrim barter menu: the merchant's goods on the left, the player's sellable inventory on the right, category
 * tabs on top, gold totals in the header. Click trades one item, shift-click the whole stack. All prices are the
 * server's; every click is a request the server validates before answering with a fresh state.
 */
public class BarterScreen extends Screen {
    private static final int ROW = 18;
    private static final int TOP = 70;
    private static final int GOLD = 0xFFE8C060;
    private static final int TEXT = 0xFFE8E0C8;
    private static final int DIM = 0xFF77736A;
    private static final int STOLEN = 0xFFD06050;
    private static final int LINE = 0xFFC8BC9A;

    private EconomyPackets.BarterState state;
    private ItemCategory tab = ItemCategory.ALL;
    private List<EconomyPackets.Entry> left = List.of();
    private List<EconomyPackets.Entry> right = List.of();
    private int scrollLeft;
    private int scrollRight;
    private Component message = Component.empty();
    private long messageAt;

    // hover state computed during render
    private EconomyPackets.Entry hoveredEntry;
    private boolean hoveredBuy;
    private int hoveredTab = -1;

    public BarterScreen(EconomyPackets.BarterState state) {
        super(state.merchantName());
        update(state);
    }

    public int entityId() {
        return state.entityId();
    }

    public void update(EconomyPackets.BarterState state) {
        this.state = state;
        if (!state.message().getString().isEmpty()) {
            message = state.message();
            messageAt = Util.getMillis();
        }
        refilter();
    }

    private void refilter() {
        left = filter(state.merchant());
        right = filter(state.player());
        scrollLeft = clampScroll(scrollLeft, left.size());
        scrollRight = clampScroll(scrollRight, right.size());
    }

    private List<EconomyPackets.Entry> filter(List<EconomyPackets.Entry> entries) {
        List<EconomyPackets.Entry> out = new ArrayList<>();
        for (EconomyPackets.Entry e : entries) {
            if (tab == ItemCategory.ALL || e.category() == tab.ordinal()) out.add(e);
        }
        out.sort(Comparator.<EconomyPackets.Entry>comparingInt(e -> e.category())
                .thenComparing(e -> e.stack().getHoverName().getString())
                .thenComparingInt(EconomyPackets.Entry::index));
        return out;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ layout

    private int colWidth() {
        return Math.max(120, Math.min(230, (width - 48) / 2));
    }

    private int leftX() {
        return width / 2 - 8 - colWidth();
    }

    private int rightX() {
        return width / 2 + 8;
    }

    private int listBottom() {
        return height - 36;
    }

    private int visibleRows() {
        return Math.max(1, (listBottom() - TOP) / ROW);
    }

    private int clampScroll(int scroll, int size) {
        return Math.max(0, Math.min(scroll, size - visibleRows()));
    }

    private int tabsWidth() {
        int w = 0;
        for (ItemCategory c : ItemCategory.VALUES) w += font.width(c.displayName()) + 14;
        return w;
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fillGradient(0, 0, width, height, 0xC8080808, 0xE0080808);

        // header: merchant name and gold
        g.pose().pushPose();
        g.pose().translate(width / 2f, 8, 0);
        g.pose().scale(1.3f, 1.3f, 1f);
        g.drawCenteredString(font, state.merchantName(), 0, 0, 0xFFF5EBC8);
        g.pose().popPose();
        Component gold = Component.translatable("barter.skycraft.your_gold", state.playerGold())
                .append(Component.literal("   |   "))
                .append(Component.translatable("barter.skycraft.merchant_gold", state.merchantGold()));
        g.drawCenteredString(font, gold, width / 2, 25, GOLD);
        g.fill(width / 2 - 160, 37, width / 2 + 160, 38, 0x80C8BC9A);

        // category tabs
        hoveredTab = -1;
        int tx = width / 2 - tabsWidth() / 2;
        for (ItemCategory c : ItemCategory.VALUES) {
            int w = font.width(c.displayName()) + 14;
            boolean sel = c == tab;
            boolean hov = mouseX >= tx && mouseX < tx + w && mouseY >= 41 && mouseY < 55;
            if (hov) hoveredTab = c.ordinal();
            if (sel) {
                g.fill(tx, 41, tx + w, 55, 0x50FFFFFF);
                g.fill(tx, 54, tx + w, 55, LINE);
            } else if (hov) {
                g.fill(tx, 41, tx + w, 55, 0x28FFFFFF);
            }
            g.drawString(font, c.displayName(), tx + 7, 44, sel ? 0xFFFFFFFF : hov ? TEXT : 0xFFA8A090, false);
            tx += w;
        }

        // columns
        hoveredEntry = null;
        int lx = leftX(), rx = rightX(), cw = colWidth();
        drawColumn(g, lx, cw, state.merchantName(), left, scrollLeft, true, mouseX, mouseY);
        drawColumn(g, rx, cw, Component.translatable("barter.skycraft.inventory"), right, scrollRight, false, mouseX, mouseY);

        // footer: last message (fades) and controls hint
        long age = Util.getMillis() - messageAt;
        if (!message.getString().isEmpty() && age < 5000) {
            int alpha = age < 4000 ? 0xFF : (int) (0xFF * (5000 - age) / 1000f);
            g.drawCenteredString(font, message, width / 2, height - 28, (Math.max(8, alpha) << 24) | 0xF0E6C8);
        }
        g.drawCenteredString(font, Component.translatable("barter.skycraft.hint"), width / 2, height - 14, 0xFF8A8478);

        super.render(g, mouseX, mouseY, partialTick);

        if (hoveredEntry != null) {
            List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(minecraft, hoveredEntry.stack()));
            lines.add(Component.translatable(hoveredBuy ? "barter.skycraft.tooltip.buy" : "barter.skycraft.tooltip.sell",
                    hoveredEntry.price()).withStyle(ChatFormatting.GOLD));
            if (hoveredEntry.status() == Barter.STATUS_NOT_DEALT) {
                lines.add(Component.translatable("barter.skycraft.status.not_dealt").withStyle(ChatFormatting.RED));
            } else if (hoveredEntry.status() == Barter.STATUS_STOLEN) {
                lines.add(Component.translatable("barter.skycraft.status.stolen").withStyle(ChatFormatting.RED));
            }
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
    }

    private void drawColumn(GuiGraphics g, int x, int w, Component title, List<EconomyPackets.Entry> entries, int scroll,
                            boolean buy, int mouseX, int mouseY) {
        int bottom = listBottom();
        g.fill(x, TOP - 12, x + w, bottom + 2, 0x70000000);
        g.drawString(font, font.plainSubstrByWidth(title.getString(), w - 60), x + 4, TOP - 10, LINE, false);
        Component priceHeader = Component.translatable("barter.skycraft.price");
        g.drawString(font, priceHeader, x + w - 6 - font.width(priceHeader), TOP - 10, LINE, false);
        g.fill(x, TOP - 1, x + w, TOP, 0x80C8BC9A);

        if (entries.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("barter.skycraft.empty"), x + w / 2, TOP + 8, DIM);
            return;
        }
        int rows = visibleRows();
        for (int i = 0; i < rows && scroll + i < entries.size(); i++) {
            EconomyPackets.Entry e = entries.get(scroll + i);
            int y = TOP + 1 + i * ROW;
            boolean hov = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + ROW;
            if (hov) {
                hoveredEntry = e;
                hoveredBuy = buy;
                g.fill(x, y, x + w, y + ROW, 0x38FFFFFF);
                g.fill(x, y, x + 1, y + ROW, LINE);
            }
            ItemStack stack = e.stack();
            g.renderItem(stack, x + 3, y + 1);

            String price = String.valueOf(e.price());
            int priceW = font.width(price);
            boolean ok = e.status() == Barter.STATUS_OK;
            boolean affordable = !buy || state.playerGold() >= e.price();
            int nameColor = e.status() == Barter.STATUS_STOLEN ? STOLEN : ok ? (hov ? 0xFFFFFFFF : TEXT) : DIM;

            String count = stack.getCount() > 1 ? " (" + stack.getCount() + ")" : "";
            int nameSpace = w - 26 - priceW - 10 - font.width(count);
            String name = font.plainSubstrByWidth(stack.getHoverName().getString(), Math.max(10, nameSpace));
            g.drawString(font, name, x + 23, y + 5, nameColor, false);
            if (!count.isEmpty()) g.drawString(font, count, x + 23 + font.width(name), y + 5, DIM, false);
            g.drawString(font, price, x + w - 6 - priceW, y + 5, !ok ? DIM : affordable ? GOLD : 0xFFC04040, false);
        }

        // scrollbar
        if (entries.size() > rows) {
            int trackH = bottom - TOP;
            int barH = Math.max(10, trackH * rows / entries.size());
            int barY = TOP + (trackH - barH) * scroll / Math.max(1, entries.size() - rows);
            g.fill(x + w - 2, TOP, x + w, bottom, 0x40FFFFFF);
            g.fill(x + w - 2, barY, x + w, barY + barH, 0xC0C8BC9A);
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (hoveredTab >= 0) {
            setTab(ItemCategory.byOrdinal(hoveredTab));
            return true;
        }
        if (hoveredEntry != null && (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT)) {
            ItemStack stack = hoveredEntry.stack();
            SkyNetwork.sendToServer(new EconomyPackets.BarterAction(state.entityId(), hoveredBuy, hoveredEntry.index(),
                    hasShiftDown(), BuiltInRegistries.ITEM.getKey(stack.getItem())));
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int step = delta > 0 ? -1 : delta < 0 ? 1 : 0;
        if (mx < width / 2.0) scrollLeft = clampScroll(scrollLeft + step, left.size());
        else scrollRight = clampScroll(scrollRight + step, right.size());
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        int n = ItemCategory.VALUES.length;
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_A) {
            setTab(ItemCategory.byOrdinal((tab.ordinal() + n - 1) % n));
            return true;
        }
        if (key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_D) {
            setTab(ItemCategory.byOrdinal((tab.ordinal() + 1) % n));
            return true;
        }
        if (minecraft != null && minecraft.options.keyInventory.matches(key, scan)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    private void setTab(ItemCategory c) {
        if (c == tab) return;
        tab = c;
        scrollLeft = 0;
        scrollRight = 0;
        refilter();
    }
}
