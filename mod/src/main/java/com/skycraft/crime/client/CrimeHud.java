package com.skycraft.crime.client;

import com.skycraft.core.Holds;
import com.skycraft.crime.Bounty;
import com.skycraft.crime.CrimePackets;
import com.skycraft.crime.Jail;
import com.skycraft.crime.Locks;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/**
 * Crime HUD: the bounty in the current hold (small red text under the compass), the jail countdown, the
 * "resisting arrest" status, and subtle crosshair hints ("Steal", "Locked (Adept)", "Pickpocket").
 */
public final class CrimeHud {
    private static final int RED = 0xE04040;

    private CrimeHud() {}

    public static void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) return;
        Font font = mc.font;

        // ---- status line under the compass
        Component status = null;
        int color = RED;
        if (Jail.isJailed(mc.player)) {
            status = Component.translatable("hud.skycraft.crime.sentence", Jail.formatTime(Jail.remaining(mc.player)));
            color = 0xE0C080;
        } else {
            String hold = Holds.holdAt(mc.level, mc.player.blockPosition());
            int bounty = Bounty.get(mc.player, hold);
            if (bounty > 0) {
                MutableComponent line = Component.translatable("hud.skycraft.crime.bounty", bounty, Holds.displayName(hold));
                if (bounty >= Bounty.KILL_ON_SIGHT || Bounty.isHostile(mc.player)) {
                    line.append(Component.translatable(bounty >= Bounty.KILL_ON_SIGHT && !Bounty.isHostile(mc.player)
                            ? "hud.skycraft.crime.wanted" : "hud.skycraft.crime.resisting"));
                }
                status = line;
            }
        }
        if (status != null) drawSmall(g, font, status, width / 2, 23, 0.8f, 0xE0 << 24 | color);

        // ---- crosshair hints
        if (mc.screen != null) return;
        int cx = width / 2;
        int cy = height / 2 + 9;
        HitResult hit = mc.hitResult;
        CrimePackets.ContainerInfo info = CrimeClientHandlers.containerInfo;
        if (hit instanceof BlockHitResult bhr && hit.getType() == HitResult.Type.BLOCK && info != null
                && info.pos().equals(bhr.getBlockPos()) && Util.getMillis() - CrimeClientHandlers.containerInfoTime < 5000) {
            if (info.lock() != Locks.NOT_LOCKED && !info.unlocked()) {
                Component lock = Component.translatable("hud.skycraft.crime.locked",
                        Component.translatable("crime.skycraft.lock." + Locks.TIERS[Math.max(0, Math.min(4, info.lock()))]));
                drawSmall(g, font, lock, cx, cy, 0.75f, 0xD0D8C8A8);
                cy += 8;
            }
            if (info.owned()) drawSmall(g, font, Component.translatable("hud.skycraft.crime.steal"), cx, cy, 0.75f, 0xD0000000 | RED);
        } else if (mc.player.isShiftKeyDown() && mc.crosshairPickEntity instanceof AbstractVillager villager && villager.isAlive()) {
            drawSmall(g, font, Component.translatable("hud.skycraft.crime.pickpocket"), cx, cy, 0.75f, 0xC0C8C0B0);
        }
    }

    private static void drawSmall(GuiGraphics g, Font font, Component text, int x, int y, float scale, int argb) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1f);
        g.drawCenteredString(font, text, 0, 0, argb);
        g.pose().popPose();
    }
}
