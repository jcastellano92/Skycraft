package com.skycraft.client.screen;

import com.skycraft.dialogue.DialoguePackets;
import com.skycraft.network.SkyNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Skyrim conversation menu: NPC name and line on top, the player's topics listed on the right. */
public class DialogueScreen extends Screen {
    private final DialoguePackets.OpenDialogue dialogue;
    private int hovered = -1;
    private int selected = 0;

    public DialogueScreen(DialoguePackets.OpenDialogue dialogue) {
        super(dialogue.npcName());
        this.dialogue = dialogue;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int listX() {
        return width / 2 + 30;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fillGradient(width / 2, 0, width, height, 0x00000000, 0x90000000);

        // Player's Current Gold in top-right
        if (minecraft != null && minecraft.player != null) {
            long playerGold = com.skycraft.core.Currency.balance(minecraft.player);
            String goldLabel = "Gold: ";
            String goldCompact = com.skycraft.core.Currency.formatCompact(playerGold) + "g";
            String goldFull = playerGold >= 1000 ? " (" + com.skycraft.core.Currency.formatFull(playerGold) + ")" : "";
            int totalW = font.width(goldLabel) + font.width(goldCompact) + font.width(goldFull);
            int gx = width - totalW - 20;
            int gBoxY = 14;

            g.fill(gx - 8, gBoxY - 4, width - 12, gBoxY + 14, 0x90000000);
            g.fill(gx - 8, gBoxY - 4, gx - 6, gBoxY + 14, 0xFFE0B020);
            g.drawString(font, goldLabel, gx, gBoxY, 0xFFD8D0B8, true);
            g.drawString(font, goldCompact, gx + font.width(goldLabel), gBoxY, 0xFFE0B020, true);
            if (!goldFull.isEmpty()) {
                g.drawString(font, goldFull, gx + font.width(goldLabel) + font.width(goldCompact), gBoxY, 0xFFA09880, false);
            }
        }

        // Dedicated Skyrim conversation subtitle panel
        int plateW = Math.min(520, width - 60);
        int plateX = (width - plateW) / 2;
        List<FormattedCharSequence> greetingLines = font.split(dialogue.greeting(), plateW - 40);
        int nameH = 16;
        int textH = Math.max(12, greetingLines.size() * 12);
        int plateH = nameH + textH + 20;
        int plateY = height - plateH - 18;

        // Subtitle plate backing: dark slate parchment with gradient vignette
        g.fill(plateX, plateY, plateX + plateW, plateY + plateH, 0xEE0E0D0B);
        g.fillGradient(plateX, plateY, plateX + plateW, plateY + plateH, 0x15FFFFFF, 0x00000000);

        // Frame borders with Nordic gold accent
        g.fill(plateX - 1, plateY - 1, plateX + plateW + 1, plateY, 0xFF4A3E2D); // top
        g.fill(plateX - 1, plateY + plateH, plateX + plateW + 1, plateY + plateH + 1, 0xFF4A3E2D); // bottom
        g.fill(plateX - 1, plateY - 1, plateX, plateY + plateH + 1, 0xFF4A3E2D); // left
        g.fill(plateX + plateW, plateY - 1, plateX + plateW + 1, plateY + plateH + 1, 0xFF4A3E2D); // right

        // Gold corner pips
        g.fill(plateX - 2, plateY - 2, plateX + 2, plateY + 2, 0xFFE5B83B);
        g.fill(plateX + plateW - 2, plateY - 2, plateX + plateW + 2, plateY + 2, 0xFFE5B83B);
        g.fill(plateX - 2, plateY + plateH - 2, plateX + 2, plateY + plateH + 2, 0xFFE5B83B);
        g.fill(plateX + plateW - 2, plateY + plateH - 2, plateX + plateW + 2, plateY + plateH + 2, 0xFFE5B83B);

        // NPC Name Header
        int nameY = plateY + 7;
        g.drawCenteredString(font, dialogue.npcName(), plateX + plateW / 2, nameY, 0xFFF5EBC8);
        // Divider bar under NPC name
        int divW = Math.min(180, plateW / 2);
        int divX = plateX + (plateW - divW) / 2;
        int divY = nameY + 11;
        g.fill(divX, divY, divX + divW, divY + 1, 0x80E5B83B);
        g.fill(divX + divW / 2 - 3, divY - 1, divX + divW / 2 + 3, divY + 2, 0xFFE5B83B);

        // Speech dialogue text
        int curGy = divY + 6;
        for (FormattedCharSequence line : greetingLines) {
            g.drawCenteredString(font, line, plateX + plateW / 2, curGy, 0xFFE6DEC8);
            curGy += 12;
        }

        hovered = -1;
        List<DialoguePackets.Line> lines = dialogue.lines();
        int x = listX();
        int maxW = Math.max(120, width - x - 32);

        // Precompute wrapped lines and heights so choices wrap cleanly rather than truncating
        List<List<FormattedCharSequence>> wrappedList = new java.util.ArrayList<>();
        int totalChoicesH = 0;
        int gap = 4;
        for (DialoguePackets.Line line : lines) {
            List<FormattedCharSequence> wrapped = font.split(line.label(), maxW - 14);
            wrappedList.add(wrapped);
            totalChoicesH += wrapped.size() * 11 + gap;
        }

        int curY = Math.max(28, (plateY - 20 - totalChoicesH) / 2);
        for (int i = 0; i < lines.size(); i++) {
            List<FormattedCharSequence> wrapped = wrappedList.get(i);
            int entryH = wrapped.size() * 11 + gap;
            int boxY1 = curY - 2;
            int boxY2 = curY + entryH - 2;

            if (mouseX >= x - 12 && mouseX <= x + maxW && mouseY >= boxY1 && mouseY <= boxY2) {
                hovered = i;
            }
            boolean hi = (hovered == i || (hovered < 0 && selected == i));

            if (hi) {
                // Skyrim-style response focus: illuminated background banner and gold bar
                g.fillGradient(x - 12, boxY1, x + maxW + 4, boxY2, 0x40FFFFFF, 0x10FFFFFF);
                g.fill(x - 12, boxY1, x - 10, boxY2, 0xFFE5B83B);
                // Focused indicator arrow
                g.drawString(font, "▶", x - 7, curY, 0xFFE5B83B, true);
            }

            int textX = hi ? x + 4 : x;
            int textY = curY;
            int textColor = hi ? 0xFFFFFFFF : 0xFFA8A090;
            for (FormattedCharSequence lineSeq : wrapped) {
                g.drawString(font, lineSeq, textX, textY, textColor, true);
                textY += 11;
            }

            curY += entryH;
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (hovered >= 0) {
            choose(hovered);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        int n = dialogue.lines().size();
        if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_W) selected = (selected + n - 1) % n;
        else if (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_S) selected = (selected + 1) % n;
        else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_E) choose(selected);
        else return super.keyPressed(key, scan, mods);
        return true;
    }

    @Override
    public void onClose() {
        com.skycraft.client.DialogueCamera.stop();
        SkyNetwork.sendToServer(new DialoguePackets.CloseDialogue(dialogue.entityId()));
        super.onClose();
    }

    private void choose(int index) {
        if (minecraft != null) {
            minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(com.skycraft.world.WorldSounds.UI_MENU_CLICK.get(), 1.0f, 1.0f));
        }
        DialoguePackets.Line line = dialogue.lines().get(index);
        super.onClose();
        SkyNetwork.sendToServer(new DialoguePackets.ChooseOption(dialogue.entityId(), line.id()));
    }
}
