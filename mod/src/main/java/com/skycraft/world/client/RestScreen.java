package com.skycraft.world.client;

import com.skycraft.client.SkyKeys;
import com.skycraft.network.SkyNetwork;
import com.skycraft.world.SkyrimCalendar;
import com.skycraft.world.WorldPackets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/** Skyrim's "Wait" / "Sleep" dial: pick 1-24 hours. */
public class RestScreen extends Screen {
    private static int lastWaitHours = 1;
    private static int lastSleepHours = 8;

    private final boolean sleep;
    private final BlockPos bed;
    private int hours;

    public RestScreen(boolean sleep, BlockPos bed) {
        super(Component.translatable(sleep ? "screen.skycraft.sleep" : "screen.skycraft.wait"));
        this.sleep = sleep;
        this.bed = bed == null ? BlockPos.ZERO : bed;
        this.hours = sleep ? lastSleepHours : lastWaitHours;
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int cy = height / 2;
        addRenderableWidget(new HoursSlider(cx - 100, cy + 4, 200, 20));
        addRenderableWidget(Button.builder(Component.translatable(sleep ? "world.skycraft.rest.do_sleep" : "world.skycraft.rest.do_wait"),
                b -> confirm()).bounds(cx - 102, cy + 34, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(cx + 2, cy + 34, 100, 20).build());
    }

    private void confirm() {
        if (sleep) lastSleepHours = hours;
        else lastWaitHours = hours;
        SkyNetwork.sendToServer(new WorldPackets.Rest(hours, sleep, bed));
        onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            confirm();
            return true;
        }
        if (SkyKeys.WAIT.matches(key, scan)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int cx = width / 2;
        int cy = height / 2;
        // panel
        g.fillGradient(cx - 130, cy - 70, cx + 130, cy + 66, 0xD0100C08, 0xD0201810);
        g.fill(cx - 130, cy - 70, cx + 130, cy - 69, 0xFF8A7F66);
        g.fill(cx - 130, cy + 65, cx + 130, cy + 66, 0xFF8A7F66);
        g.fill(cx - 90, cy - 44, cx + 90, cy - 43, 0x808A7F66);

        g.pose().pushPose();
        g.pose().translate(cx, cy - 62, 0);
        g.pose().scale(1.6f, 1.6f, 1f);
        g.drawCenteredString(font, title, 0, 0, 0xFFF5EBC8);
        g.pose().popPose();

        long dayTime = minecraft != null && minecraft.level != null ? minecraft.level.getDayTime() : 0;
        g.drawCenteredString(font, SkyrimCalendar.date(dayTime), cx, cy - 38, 0xFFD0C8B0);
        g.drawCenteredString(font, SkyrimCalendar.time(dayTime), cx, cy - 26, 0xFFE8C060);
        Component until = Component.translatable("world.skycraft.rest.until", SkyrimCalendar.time(dayTime + hours * 1000L));
        g.drawCenteredString(font, until, cx, cy - 12, 0xFFA89F86);
        if (sleep) g.drawCenteredString(font, Component.translatable("world.skycraft.rest.vanilla_hint"), cx, cy + 72, 0xFF8A7F66);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private Component sliderLabel() {
        return Component.translatable(hours == 1 ? "world.skycraft.rest.hour" : "world.skycraft.rest.hours", hours);
    }

    private class HoursSlider extends AbstractSliderButton {
        HoursSlider(int x, int y, int w, int h) {
            super(x, y, w, h, Component.empty(), (hours - 1) / 23.0);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(sliderLabel());
        }

        @Override
        protected void applyValue() {
            hours = Mth.clamp(1 + (int) Math.round(value * 23), 1, 24);
        }
    }
}
