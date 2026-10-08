package com.skycraft.crime.client;

import com.skycraft.core.Holds;
import com.skycraft.crime.Bounty;
import com.skycraft.crime.CrimePackets;
import com.skycraft.crime.Jail;
import com.skycraft.crime.Locks;
import com.skycraft.crime.Ownership;
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
        if (status != null) drawSmall(g, font, status, width / 2, 32, 0.8f, 0xE0 << 24 | color);

        // ---- crosshair hints (Skyrim interactive prompts; ordinary blocks show nothing)
        if (mc.screen != null) return;
        int cx = width / 2;
        int cy = height / 2 + 10;

        // Entities under crosshair
        if (mc.crosshairPickEntity != null) {
            if (mc.crosshairPickEntity instanceof net.minecraft.world.entity.item.ItemEntity item) {
                boolean owned = Ownership.isOwnedByOther(mc.player, item);
                Component prompt = Component.literal((owned ? "Steal  " : "Take  ") + item.getItem().getHoverName().getString());
                drawSmall(g, font, prompt, cx, cy, 0.75f, owned ? (0xD0000000 | RED) : 0xD0E8E2D0);
                return;
            }
            if (mc.crosshairPickEntity instanceof com.skycraft.creatures.entity.CorpseEntity corpse) {
                Component prompt = Component.literal("Search  " + corpse.getDisplayName().getString());
                drawSmall(g, font, prompt, cx, cy, 0.75f, 0xD0E8E2D0);
                return;
            }
            if (mc.crosshairPickEntity instanceof AbstractVillager villager && villager.isAlive()) {
                if (mc.player.isShiftKeyDown()) {
                    drawSmall(g, font, Component.literal("Pickpocket  " + villager.getDisplayName().getString()), cx, cy, 0.75f, 0xC0C8C0B0);
                } else {
                    drawSmall(g, font, Component.literal("E  Talk  " + villager.getDisplayName().getString()), cx, cy, 0.75f, 0xD0E8E2D0);
                }
                return;
            }
        }

        // Blocks under crosshair
        HitResult hit = mc.hitResult;
        if (hit instanceof BlockHitResult bhr && hit.getType() == HitResult.Type.BLOCK) {
            var bpos = bhr.getBlockPos();
            var bstate = mc.level.getBlockState(bpos);
            var block = bstate.getBlock();

            if (block instanceof net.minecraft.world.level.block.BedBlock) {
                boolean owned = Ownership.isOwnedByOther(mc.player, mc.level, bpos);
                boolean occ = bstate.hasProperty(net.minecraft.world.level.block.BedBlock.OCCUPIED) && bstate.getValue(net.minecraft.world.level.block.BedBlock.OCCUPIED);
                String t = occ ? "Bed (occupied)" : owned ? "Sleep  Bed (owned)" : "Sleep  Bed";
                drawSmall(g, font, Component.literal(t), cx, cy, 0.75f, (owned || occ) ? (0xD0000000 | RED) : 0xD0E8E2D0);
                return;
            }

            CrimePackets.ContainerInfo info = CrimeClientHandlers.containerInfo;
            boolean hasLock = info != null && info.pos().equals(bpos) && info.lock() != Locks.NOT_LOCKED && !info.unlocked();
            if (hasLock) {
                Component lock = Component.translatable("hud.skycraft.crime.locked",
                        Component.translatable("crime.skycraft.lock." + Locks.TIERS[Math.max(0, Math.min(4, info.lock()))]));
                drawSmall(g, font, lock, cx, cy, 0.75f, 0xD0D8C8A8);
                cy += 8;
            }

            boolean isContainer = block instanceof net.minecraft.world.level.block.ChestBlock
                    || block instanceof net.minecraft.world.level.block.BarrelBlock
                    || block instanceof net.minecraft.world.level.block.ShulkerBoxBlock;

            if (isContainer) {
                boolean owned = (info != null && info.pos().equals(bpos) && info.owned()) || Ownership.isOwnedByOther(mc.player, mc.level, bpos);
                String name = block.getName().getString();
                String action = owned ? "Steal  " : "Open  ";
                drawSmall(g, font, Component.literal(action + name), cx, cy, 0.75f, owned ? (0xD0000000 | RED) : 0xD0E8E2D0);
                return;
            }

            if (block instanceof net.minecraft.world.level.block.DoorBlock
                    || block instanceof net.minecraft.world.level.block.TrapDoorBlock
                    || block instanceof net.minecraft.world.level.block.FenceGateBlock) {
                boolean owned = Ownership.isOwnedByOther(mc.player, mc.level, bpos);
                String t = owned ? "Open  Door (owned)" : "Open  Door";
                drawSmall(g, font, Component.literal(t), cx, cy, 0.75f, owned ? (0xD0000000 | RED) : 0xD0E8E2D0);
                return;
            }

            if (block.getDescriptionId().contains("crafting") || block.getDescriptionId().contains("station")
                    || block.getDescriptionId().contains("furnace") || block.getDescriptionId().contains("anvil")
                    || block.getDescriptionId().contains("grindstone")) {
                drawSmall(g, font, Component.literal("Use  " + block.getName().getString()), cx, cy, 0.75f, 0xD0E8E2D0);
            }
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
