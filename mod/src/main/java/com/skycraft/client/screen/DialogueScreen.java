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

    private int lineY(int i) {
        return height / 2 - dialogue.lines().size() * 7 + i * 14;
    }

    private int listX() {
        return width / 2 + 30;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fillGradient(width / 2, 0, width, height, 0x00000000, 0xA0000000);
        List<FormattedCharSequence> greetingLines = font.split(dialogue.greeting(), Math.min(460, width - 60));
        int totalGH = greetingLines.size() * 11;
        int gy = Math.max(height / 2 + 40, height - 16 - totalGH);
        g.fillGradient(0, gy - 32, width, height, 0x00000000, 0xC0000000);

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

        // NPC name and line
        g.pose().pushPose();
        g.pose().translate(width / 2f, gy - 18, 0);
        g.pose().scale(1.4f, 1.4f, 1f);
        g.drawCenteredString(font, dialogue.npcName(), 0, 0, 0xFFF5EBC8);
        g.pose().popPose();

        int curGy = gy;
        for (FormattedCharSequence line : greetingLines) {
            g.drawCenteredString(font, line, width / 2, curGy, 0xFFD8D0B8);
            curGy += 11;
        }

        hovered = -1;
        List<DialoguePackets.Line> lines = dialogue.lines();
        for (int i = 0; i < lines.size(); i++) {
            int y = lineY(i);
            int x = listX();
            int maxW = Math.max(100, width - x - 24);
            String text = lines.get(i).label().getString();
            if (font.width(text) > maxW) {
                text = font.plainSubstrByWidth(text, maxW - font.width("...")) + "...";
            }
            int w = font.width(text);
            if (mouseX >= x - 4 && mouseX <= x + w + 4 && mouseY >= y - 2 && mouseY <= y + 10) hovered = i;
            boolean hi = hovered == i || hovered < 0 && selected == i;
            if (hi) {
                g.fill(x - 6, y - 2, x + w + 6, y + 10, 0x40FFFFFF);
                g.fill(x - 6, y - 2, x - 5, y + 10, 0xFFC8BC9A);
            }
            g.drawString(font, text, x, y, hi ? 0xFFFFFFFF : 0xFFA8A090, true);
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
        com.skycraft.client.DialogueCamera.stop();
        super.onClose();
        SkyNetwork.sendToServer(new DialoguePackets.ChooseOption(dialogue.entityId(), line.id()));
    }
}
