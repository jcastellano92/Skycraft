package com.skycraft.crime.client;

import com.skycraft.core.Holds;
import com.skycraft.crime.Bounty;
import com.skycraft.crime.CrimePackets;
import com.skycraft.crime.Crimes;
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

        // ---- Skyrim Sneak Detection Eye
        if (mc.player.isCrouching()) {
            renderSneakEye(g, font, mc.player, width, height);
        }

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
                if (Jail.isJailed(mc.player)) {
                    int served = Bounty.state(mc.player).getCompound("jail").getInt("served");
                    int left = Math.max(0, 60 - served);
                    if (left > 0) {
                        drawSmall(g, font, Component.literal("Rest  Serve Sentence (" + left + "s wait)"), cx, cy, 0.75f, 0xD0000000 | RED);
                    } else {
                        drawSmall(g, font, Component.literal("Sleep  Serve Sentence"), cx, cy, 0.75f, 0xD0E8E2D0);
                    }
                    return;
                }
                boolean owned = Ownership.isOwnedByOther(mc.player, mc.level, bpos);
                boolean occ = bstate.hasProperty(net.minecraft.world.level.block.BedBlock.OCCUPIED) && bstate.getValue(net.minecraft.world.level.block.BedBlock.OCCUPIED);
                String t = occ ? "Bed (occupied)" : owned ? "Sleep  Bed (owned)" : "Sleep  Bed";
                drawSmall(g, font, Component.literal(t), cx, cy, 0.75f, (owned || occ) ? (0xD0000000 | RED) : 0xD0E8E2D0);
                return;
            }

            if (Jail.isJailed(mc.player) && block instanceof net.minecraft.world.level.block.TrapDoorBlock) {
                drawSmall(g, font, Component.literal("Escape  Old Sewer Grate"), cx, cy, 0.75f, 0xD0E8E2D0);
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
                long dayTime = mc.level.getDayTime() % 24000L;
                boolean night = dayTime >= 13000L && dayTime <= 23000L;
                boolean locked = owned && night && (block instanceof net.minecraft.world.level.block.DoorBlock || block instanceof net.minecraft.world.level.block.TrapDoorBlock);
                String t = locked ? "Locked  Door" : (owned ? "Open  Door (owned)" : "Open  Door");
                drawSmall(g, font, Component.literal(t), cx, cy, 0.75f, (owned || locked) ? (0xD0000000 | RED) : 0xD0E8E2D0);
                return;
            }

            if (block.getDescriptionId().contains("shrine")) {
                drawSmall(g, font, Component.literal("Pray  " + block.getName().getString()), cx, cy, 0.75f, 0xD0E8E2D0);
                return;
            }

            if (block.getDescriptionId().contains("standing_stone")) {
                drawSmall(g, font, Component.literal("Activate  " + block.getName().getString()), cx, cy, 0.75f, 0xD0E8E2D0);
                return;
            }

            if (block.getDescriptionId().contains("word_wall")) {
                drawSmall(g, font, Component.literal("Read  Word Wall"), cx, cy, 0.75f, 0xD0E8E2D0);
                return;
            }

            if (block instanceof net.minecraft.world.level.block.CampfireBlock) {
                drawSmall(g, font, Component.literal("Rest  Campfire"), cx, cy, 0.75f, 0xD0E8E2D0);
                return;
            }

            if (block instanceof net.minecraft.world.level.block.CropBlock
                    || block instanceof net.minecraft.world.level.block.SweetBerryBushBlock
                    || block instanceof net.minecraft.world.level.block.FlowerBlock) {
                drawSmall(g, font, Component.literal("Harvest  " + block.getName().getString()), cx, cy, 0.75f, 0xD0E8E2D0);
                return;
            }

            if (block.getDescriptionId().contains("crafting") || block.getDescriptionId().contains("station")
                    || block.getDescriptionId().contains("furnace") || block.getDescriptionId().contains("anvil")
                    || block.getDescriptionId().contains("grindstone") || block.getDescriptionId().contains("cooking_pot")) {
                String action = block.getDescriptionId().contains("cooking") ? "Cook  " : "Use  ";
                drawSmall(g, font, Component.literal(action + block.getName().getString()), cx, cy, 0.75f, 0xD0E8E2D0);
            }
        }
    }

    private enum SneakState {
        HIDDEN, CAUTION, DETECTED
    }

    private static SneakState getSneakState(net.minecraft.world.entity.player.Player player) {
        var level = player.level();
        var box = player.getBoundingBox().inflate(24);
        var list = level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, box,
                e -> e != player && e.isAlive() && !(e instanceof net.minecraft.world.entity.player.Player));
        if (list.isEmpty()) return SneakState.HIDDEN;

        int sneakSkill = com.skycraft.core.SkyData.get(player).getSkill(com.skycraft.core.Skill.SNEAK);
        int light = level.getMaxLocalRawBrightness(player.blockPosition());
        boolean dark = light < 7;
        boolean moving = player.getDeltaMovement().horizontalDistanceSqr() > 0.0004;

        boolean anyCaution = false;
        for (net.minecraft.world.entity.LivingEntity e : list) {
            if (e instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() == player) {
                return SneakState.DETECTED;
            }
            boolean civilian = Crimes.isCivilian(e);
            boolean hostile = e instanceof net.minecraft.world.entity.monster.Enemy;
            if (!civilian && !hostile) continue;
            if (e.isSleeping()) continue;

            double dist = e.distanceTo(player);
            double baseVision = 20.0;
            double vision = baseVision * (1.0 - (sneakSkill * 0.005));
            if (dark) vision *= 0.6;
            if (moving) vision *= 1.3;

            if (e.hasLineOfSight(player)) {
                boolean facing = Crimes.facing(e, player);
                if (facing) {
                    if (dist < vision) {
                        return SneakState.DETECTED;
                    } else if (dist < vision * 1.4) {
                        anyCaution = true;
                    }
                } else {
                    if (dist < Math.max(3.0, vision * 0.35)) {
                        return SneakState.DETECTED;
                    } else if (dist < vision * 0.7) {
                        anyCaution = true;
                    }
                }
            } else {
                if (moving && dist < 6.0 && sneakSkill < 50) {
                    anyCaution = true;
                }
            }
        }
        return anyCaution ? SneakState.CAUTION : SneakState.HIDDEN;
    }

    private static void renderSneakEye(GuiGraphics g, Font font, net.minecraft.world.entity.player.Player player, int width, int height) {
        SneakState state = getSneakState(player);
        int cx = width / 2;
        int cy = height / 2 - 20;

        Component label;
        String eyeBrackets;
        int color;
        switch (state) {
            case HIDDEN -> {
                label = Component.literal("HIDDEN");
                eyeBrackets = "— —  •  — —";
                color = 0xD0E0E0E0;
            }
            case CAUTION -> {
                label = Component.literal("CAUTION");
                eyeBrackets = "< ( • ) >";
                color = 0xE0E0C050;
            }
            case DETECTED -> {
                label = Component.literal("DETECTED");
                eyeBrackets = "(   ●   )";
                color = 0xE0E04040;
            }
            default -> {
                return;
            }
        }

        drawSmall(g, font, Component.literal(eyeBrackets), cx, cy - 7, 1.0f, color);
        drawSmall(g, font, label, cx, cy + 4, 0.75f, color);
    }

    private static void drawSmall(GuiGraphics g, Font font, Component text, int x, int y, float scale, int argb) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1f);
        g.drawCenteredString(font, text, 0, 0, argb);
        g.pose().popPose();
    }
}
