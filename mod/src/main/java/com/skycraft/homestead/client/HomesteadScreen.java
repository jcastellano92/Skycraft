package com.skycraft.homestead.client;

import com.skycraft.homestead.HomesteadClaim;
import com.skycraft.homestead.HomesteadMenu;
import com.skycraft.homestead.HomesteadPackets;
import com.skycraft.network.SkyNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

public class HomesteadScreen extends AbstractContainerScreen<HomesteadMenu> {
    private static final int TEXT = 0xFFF5EBC8;
    private static final int DIM = 0xFFBDB59E;
    private static final int GOLD = 0xFFE0B040;
    private static final int BORDER = 0xFF8A7F66;
    private static final int PANEL = 0xE8100E0B;
    private static final int INSET = 0x60000000;

    private Button doorBtn;
    private Button containerBtn;
    private Button buildBtn;

    private record Blueprint(String name, ItemStack icon, String cost) {}
    private final List<Blueprint> blueprints = new ArrayList<>();

    public HomesteadScreen(HomesteadMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 220;
        this.imageHeight = 222;

        blueprints.add(new Blueprint("Iron Nails (x10)", new ItemStack(Items.IRON_NUGGET), "1 Iron Ingot"));
        blueprints.add(new Blueprint("Sawn Timber (x4)", new ItemStack(Items.OAK_PLANKS), "1 Oak Log"));
        blueprints.add(new Blueprint("Hewn Stone (x4)", new ItemStack(Items.STONE_BRICKS), "4 Cobblestone"));
        blueprints.add(new Blueprint("Nordic Door", new ItemStack(Items.OAK_DOOR), "6 Planks"));
        blueprints.add(new Blueprint("Nordic Chest", new ItemStack(Items.CHEST), "8 Planks"));
        blueprints.add(new Blueprint("Hearth Fireplace", new ItemStack(Items.CAMPFIRE), "6 Cobblestone"));
        blueprints.add(new Blueprint("Glass Panes (x16)", new ItemStack(Items.GLASS_PANE), "4 Glass"));
    }

    @Override
    protected void init() {
        super.init();
        int x = this.leftPos;
        int y = this.topPos;

        doorBtn = Button.builder(doorText(), b -> {
            menu.doorPerm = (menu.doorPerm + 1) % 3;
            doorBtn.setMessage(doorText());
            sendPerms();
        }).bounds(x + 10, y + 36, 62, 18).build();

        containerBtn = Button.builder(containerText(), b -> {
            menu.containerPerm = (menu.containerPerm + 1) % 2;
            containerBtn.setMessage(containerText());
            sendPerms();
        }).bounds(x + 76, y + 36, 66, 18).build();

        buildBtn = Button.builder(buildText(), b -> {
            menu.buildPerm = (menu.buildPerm + 1) % 2;
            buildBtn.setMessage(buildText());
            sendPerms();
        }).bounds(x + 146, y + 36, 64, 18).build();

        addRenderableWidget(doorBtn);
        addRenderableWidget(containerBtn);
        addRenderableWidget(buildBtn);

        // Blueprint Craft buttons (7 small items)
        for (int i = 0; i < blueprints.size(); i++) {
            final int idx = i;
            int bx = x + 10 + (i % 4) * 50;
            int by = y + 78 + (i / 4) * 26;
            addRenderableWidget(Button.builder(Component.literal("Craft"), b -> {
                SkyNetwork.sendToServer(new HomesteadPackets.CraftBlueprint(idx));
            }).bounds(bx + 18, by + 1, 30, 16).build());
        }
    }

    private Component doorText() {
        return switch (menu.doorPerm) {
            case HomesteadClaim.PERM_PUBLIC -> Component.literal("Doors: Public");
            case HomesteadClaim.PERM_PARTY -> Component.literal("Doors: Party");
            default -> Component.literal("Doors: Owner");
        };
    }

    private Component containerText() {
        return switch (menu.containerPerm) {
            case HomesteadClaim.PERM_PARTY -> Component.literal("Chests: Party");
            default -> Component.literal("Chests: Owner");
        };
    }

    private Component buildText() {
        return switch (menu.buildPerm) {
            case HomesteadClaim.PERM_PARTY -> Component.literal("Build: Party");
            default -> Component.literal("Build: Owner");
        };
    }

    private void sendPerms() {
        SkyNetwork.sendToServer(new HomesteadPackets.UpdatePerms(menu.pos, menu.doorPerm, menu.containerPerm, menu.buildPerm));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);

        // Tooltips for blueprints
        int x = this.leftPos;
        int y = this.topPos;
        for (int i = 0; i < blueprints.size(); i++) {
            Blueprint bp = blueprints.get(i);
            int bx = x + 10 + (i % 4) * 50;
            int by = y + 78 + (i / 4) * 26;
            if (mouseX >= bx && mouseX <= bx + 16 && mouseY >= by && mouseY <= by + 16) {
                g.renderTooltip(font, List.of(
                        Component.literal(bp.name).withStyle(s -> s.withColor(GOLD)),
                        Component.literal("Cost: " + bp.cost).withStyle(s -> s.withColor(DIM))
                ), java.util.Optional.empty(), mouseX, mouseY);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // Labels rendered manually in renderBg
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x0 = leftPos, y0 = topPos, x1 = leftPos + imageWidth, y1 = topPos + imageHeight;
        g.fill(x0, y0, x1, y1, PANEL);
        frame(g, x0, y0, x1, y1, BORDER);
        frame(g, x0 + 2, y0 + 2, x1 - 2, y1 - 2, 0x408A7F66);

        // Header
        g.drawCenteredString(font, Component.literal("HOMESTEAD DRAFTING TABLE"), (x0 + x1) / 2, y0 + 7, TEXT);
        g.fill(x0 + 8, y0 + 18, x1 - 8, y0 + 19, 0x808A7F66);

        // Subheader Info
        String info = "Plot: " + menu.radius + "m Radius | Foundation: " + menu.minDepth + "m Depth Limit";
        g.drawString(font, info, x0 + 10, y0 + 23, DIM, false);

        // Blueprint Header
        g.drawString(font, "HEARTHFIRE BLUEPRINTS", x0 + 10, y0 + 64, GOLD, false);
        g.fill(x0 + 8, y0 + 74, x1 - 8, y0 + 75, 0x408A7F66);

        // Render Blueprint Icons
        for (int i = 0; i < blueprints.size(); i++) {
            Blueprint bp = blueprints.get(i);
            int bx = x0 + 10 + (i % 4) * 50;
            int by = y0 + 78 + (i / 4) * 26;
            g.fill(bx, by, bx + 18, by + 18, INSET);
            frame(g, bx, by, bx + 18, by + 18, 0x808A7F66);
            g.renderItem(bp.icon, bx + 1, by + 1);
        }

        // Inventory Header
        g.drawString(font, "INVENTORY", x0 + 10, y0 + 130, DIM, false);
        g.fill(x0 + 8, y0 + 138, x1 - 8, y0 + 139, 0x408A7F66);
    }

    private void frame(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        g.fill(x0, y0, x1, y0 + 1, color);
        g.fill(x0, y1 - 1, x1, y1, color);
        g.fill(x0, y0, x0 + 1, y1, color);
        g.fill(x1 - 1, y0, x1, y1, color);
    }
}
