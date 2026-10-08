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
        g.fillGradient(0, height - 80, width, height, 0x00000000, 0xB0000000);
        // NPC name and line
        g.pose().pushPose();
        g.pose().translate(width / 2f, height - 64, 0);
        g.pose().scale(1.4f, 1.4f, 1f);
        g.drawCenteredString(font, dialogue.npcName(), 0, 0, 0xFFF5EBC8);
        g.pose().popPose();
        int gy = height - 46;
        for (FormattedCharSequence line : font.split(dialogue.greeting(), Math.min(400, width - 40))) {
            g.drawCenteredString(font, line, width / 2, gy, 0xFFD8D0B8);
            gy += 10;
        }
        hovered = -1;
        List<DialoguePackets.Line> lines = dialogue.lines();
        for (int i = 0; i < lines.size(); i++) {
            int y = lineY(i);
            int x = listX();
            int w = font.width(lines.get(i).label());
            if (mouseX >= x - 4 && mouseX <= x + w + 4 && mouseY >= y - 2 && mouseY <= y + 10) hovered = i;
            boolean hi = hovered == i || hovered < 0 && selected == i;
            if (hi) {
                g.fill(x - 6, y - 2, x + w + 6, y + 10, 0x40FFFFFF);
                g.fill(x - 6, y - 2, x - 5, y + 10, 0xFFC8BC9A);
            }
            g.drawString(font, lines.get(i).label(), x, y, hi ? 0xFFFFFFFF : 0xFFA8A090, true);
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
        DialoguePackets.Line line = dialogue.lines().get(index);
        com.skycraft.client.DialogueCamera.stop();
        super.onClose();
        SkyNetwork.sendToServer(new DialoguePackets.ChooseOption(dialogue.entityId(), line.id()));
    }
}
