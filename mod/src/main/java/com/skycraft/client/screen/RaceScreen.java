package com.skycraft.client.screen;

import com.skycraft.core.Race;
import com.skycraft.core.Skill;
import com.skycraft.network.CorePackets;
import com.skycraft.network.SkyNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/** Character creation: choose one of the ten races of Tamriel. */
public class RaceScreen extends Screen {
    private Race selected = Race.NORD;

    public RaceScreen() {
        super(Component.translatable("screen.skycraft.race"));
    }

    @Override
    protected void init() {
        int y = 40;
        for (Race race : Race.VALUES) {
            addRenderableWidget(Button.builder(race.displayName(), b -> selected = race).bounds(20, y, 110, 18).build());
            y += 20;
        }
        addRenderableWidget(Button.builder(Component.translatable("screen.skycraft.race_confirm"), b -> {
            SkyNetwork.sendToServer(new CorePackets.ChooseRace(selected.id()));
            onClose();
        }).bounds(width - 130, height - 30, 110, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.skycraft.race_later"), b -> onClose())
                .bounds(width - 250, height - 30, 110, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fillGradient(0, 0, width, height, 0xF0050505, 0xF0181410);
        g.drawCenteredString(font, Component.translatable("screen.skycraft.race_header"), width / 2, 12, 0xFFF5EBC8);
        int x = 150;
        int w = width - x - 20;
        g.fill(x - 6, 36, x + w, height - 40, 0x90000000);
        g.pose().pushPose();
        g.pose().translate(x, 44, 0);
        g.pose().scale(1.6f, 1.6f, 1f);
        g.drawString(font, selected.displayName(), 0, 0, 0xFFF5EBC8, true);
        g.pose().popPose();
        int y = 66;
        for (FormattedCharSequence line : font.split(selected.description(), w - 10)) {
            g.drawString(font, line, x, y, 0xFFD8D0B8, false);
            y += 10;
        }
        y += 8;
        g.drawString(font, Component.translatable("screen.skycraft.race_skills"), x, y, 0xFFC8BC9A, false);
        y += 12;
        g.drawString(font, selected.major.displayName().copy().append(" +10"), x + 8, y, 0xFFE8E2D0, false);
        y += 10;
        for (Skill s : selected.minors) {
            g.drawString(font, s.displayName().copy().append(" +5"), x + 8, y, 0xFFE8E2D0, false);
            y += 10;
        }
        y += 8;
        g.drawString(font, Component.translatable("screen.skycraft.race_power"), x, y, 0xFFC8BC9A, false);
        y += 12;
        g.drawString(font, selected.powerName(), x + 8, y, 0xFFE8E2D0, false);
        y += 10;
        List<FormattedCharSequence> powerDesc = font.split(Component.translatable("power.skycraft." + selected.power + ".desc"), w - 20);
        for (FormattedCharSequence line : powerDesc) {
            g.drawString(font, line, x + 8, y, 0xFFBDB59E, false);
            y += 10;
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
