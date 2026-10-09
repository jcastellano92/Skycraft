package com.skycraft.client;

import com.skycraft.Skycraft;
import com.skycraft.client.screen.RaceScreen;
import com.skycraft.client.screen.SkillsScreen;
import com.skycraft.combat.WeaponClass;
import com.skycraft.core.Skill;
import com.skycraft.core.SkyData;
import net.minecraft.network.chat.Component;
import com.skycraft.network.CorePackets;
import com.skycraft.network.SkyNetwork;
import com.skycraft.perk.Perks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Forge-bus client events for the core: keys, sprint exhaustion, climbing, dodge, sheathing, hiding vanilla HUD bars, bow zoom. */
@Mod.EventBusSubscriber(modid = Skycraft.MODID, value = Dist.CLIENT)
public final class ClientEvents {
    private static boolean wasBlocking;
    private static int powerAttackResend;
    private static int climbResend;
    private static boolean isWallClinging;
    private static net.minecraft.core.Direction wallFacing;

    private ClientEvents() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        while (SkyKeys.HUB.consumeClick()) {
            if (mc.screen == null) mc.setScreen(new com.skycraft.client.screen.HubScreen());
        }
        while (SkyKeys.SKILLS.consumeClick()) {
            if (SkyData.get(mc.player).getRace() == null) mc.setScreen(new RaceScreen());
            else mc.setScreen(new SkillsScreen());
        }
        while (SkyKeys.SHOUT.consumeClick()) {
            SkyNetwork.sendToServer(new CorePackets.Action(CorePackets.Action.USE_POWER, 0));
        }
        while (SkyKeys.SHEATHE.consumeClick()) {
            SkyNetwork.sendToServer(new CorePackets.Action(CorePackets.Action.SHEATHE_TOGGLE, 0));
        }

        // Dodge roll on Left Alt
        while (SkyKeys.DODGE.consumeClick()) {
            if (Perks.has(mc.player, "athletics.dodge_roll") && !ClientState.has(CorePackets.SyncVitals.EXHAUSTED)) {
                SkyNetwork.sendToServer(new CorePackets.Action(CorePackets.Action.DODGE_ROLL, 0));
                Vec3 look = mc.player.getViewVector(1.0f);
                Vec3 horiz = new Vec3(look.x, 0, look.z).normalize();
                float fwd = mc.player.input.jumping ? 0 : (mc.player.input.up ? 1 : (mc.player.input.down ? -1 : 0));
                float strafe = mc.player.input.left ? 1 : (mc.player.input.right ? -1 : 0);
                Vec3 move;
                if (fwd == 0 && strafe == 0) {
                    move = horiz.scale(0.7);
                } else {
                    Vec3 side = new Vec3(-horiz.z, 0, horiz.x);
                    move = horiz.scale(fwd).add(side.scale(strafe)).normalize().scale(0.7);
                }
                mc.player.setDeltaMovement(move.x, 0.2, move.z);
            }
        }

        // Smooth step assist over blocks without constant jumping
        if (mc.player.maxUpStep() < 1.0625f) {
            mc.player.setMaxUpStep(1.0625f);
        }

        // BotW / AC-style wall climbing & sticking:
        // Transition ON: intentionally crouch (Shift) and jump towards a wall so you don't stick automatically
        boolean crouching = mc.options.keyShift.isDown();
        boolean jumping = mc.options.keyJump.isDown();
        boolean movingBack = mc.options.keyDown.isDown();
        boolean movingUp = mc.options.keyUp.isDown() || jumping;
        boolean exhausted = ClientState.has(CorePackets.SyncVitals.EXHAUSTED);
        boolean canClimb = !mc.player.isInWater() && !mc.player.isPassenger()
                && !mc.player.isCreative() && !mc.player.isSpectator() && !mc.player.onClimbable();

        Direction playerFace = mc.player.getDirection();
        BlockPos playerPos = mc.player.blockPosition();

        // Check if facing or touching a solid wall block
        boolean touchingWall = mc.player.horizontalCollision;
        if (mc.level != null) {
            BlockPos front = playerPos.relative(playerFace);
            BlockPos headFront = playerPos.above().relative(playerFace);
            if (mc.level.getBlockState(front).isSolid() || mc.level.getBlockState(headFront).isSolid()) {
                touchingWall = true;
            }
        }

        if (!isWallClinging) {
            // Intentional mount: crouch + jump while physically touching a solid wall
            if (canClimb && !exhausted && crouching && jumping && touchingWall) {
                isWallClinging = true;
                wallFacing = playerFace;
            }
        } else {
            // Dismount conditions:
            boolean wallInFront = false;
            if (mc.level != null && wallFacing != null) {
                BlockPos front = playerPos.relative(wallFacing);
                BlockPos headFront = playerPos.above().relative(wallFacing);
                BlockPos aboveFront = playerPos.above(2).relative(wallFacing);
                if (mc.level.getBlockState(front).isSolid() || mc.level.getBlockState(headFront).isSolid() || mc.level.getBlockState(aboveFront).isSolid()) {
                    wallInFront = true;
                }
            }

            if (!canClimb || exhausted || !wallInFront) {
                isWallClinging = false;
            } else if (movingBack && jumping) {
                // Leap backward off the wall!
                isWallClinging = false;
                Vec3 leap = new Vec3(-wallFacing.getStepX() * 0.45, 0.35, -wallFacing.getStepZ() * 0.45);
                mc.player.setDeltaMovement(leap);
            } else if (mc.player.onGround() && !movingUp) {
                // Landed on solid ground
                isWallClinging = false;
            }
        }

        if (isWallClinging && canClimb && !exhausted) {
            mc.player.resetFallDistance();

            // Continuously drain stamina to hold or climb
            if (climbResend-- <= 0) {
                SkyNetwork.sendToServer(new CorePackets.Action(CorePackets.Action.CLIMB_TICK, 0));
                climbResend = 4;
            }

            double climbSpeed = Perks.has(mc.player, "athletics.climber") ? 0.22 : 0.16;
            double vy = 0.0; // Sticking to wall in place if no vertical input!

            if (movingUp) {
                vy = climbSpeed;
            } else if (movingBack) {
                vy = -climbSpeed;
            }

            // Horizontal strafe along wall face
            double vx = wallFacing.getStepX() * 0.05; // Lightly stick into wall surface
            double vz = wallFacing.getStepZ() * 0.05;

            if (mc.options.keyLeft.isDown()) {
                Direction leftDir = wallFacing.getCounterClockWise();
                vx += leftDir.getStepX() * 0.12;
                vz += leftDir.getStepZ() * 0.12;
            } else if (mc.options.keyRight.isDown()) {
                Direction rightDir = wallFacing.getClockWise();
                vx += rightDir.getStepX() * 0.12;
                vz += rightDir.getStepZ() * 0.12;
            }

            // Underhang & 1-block outward overhang handling:
            if (movingUp && mc.level != null) {
                BlockPos head = playerPos.above(2);
                boolean ceilingAbove = mc.level.getBlockState(head).isSolid();
                if (ceilingAbove) {
                    // 1-block overhang / eave ceiling directly above head
                    BlockPos behindHead = head.relative(wallFacing.getOpposite());
                    if (!mc.level.getBlockState(behindHead).isSolid()) {
                        // Nudge slightly outward away from wall around the lip
                        vx -= wallFacing.getStepX() * 0.22;
                        vz -= wallFacing.getStepZ() * 0.22;
                        vy = 0.18;
                    }
                } else {
                    // Check if reached top of wall/ledge to mantle onto it
                    BlockPos ledge = playerPos.above().relative(wallFacing);
                    BlockPos aboveLedge = ledge.above();
                    if (mc.level.getBlockState(ledge).isSolid() && !mc.level.getBlockState(aboveLedge).isSolid()
                            && !mc.level.getBlockState(aboveLedge.above()).isSolid()) {
                        // Mantle onto ledge top!
                        vx += wallFacing.getStepX() * 0.25;
                        vz += wallFacing.getStepZ() * 0.25;
                        vy = 0.28;
                    }
                }
            }

            mc.player.setDeltaMovement(vx, vy, vz);
        } else {
            climbResend = 0;
        }

        // Power attack: while the key is held, the server treats swings as power attacks.
        if (SkyKeys.POWER_ATTACK.isDown()) {
            if (powerAttackResend-- <= 0) {
                SkyNetwork.sendToServer(new CorePackets.Action(CorePackets.Action.POWER_ATTACK, 0));
                powerAttackResend = 5;
            }
        } else {
            powerAttackResend = 0;
        }

        // Weapon & unarmed blocking: dual-wielding has no block.
        // Can block with SkyKeys.BLOCK, or RMB (keyUse) when offhand has shield/weapon or when two-handed/unarmed.
        ItemStack main = mc.player.getMainHandItem();
        ItemStack off = mc.player.getOffhandItem();
        boolean dual = !main.isEmpty() && !off.isEmpty()
                && WeaponClass.of(main).skill == Skill.ONE_HANDED
                && WeaponClass.of(off).skill == Skill.ONE_HANDED;
        boolean rmbBlock = mc.options.keyUse.isDown() && mc.screen == null && !dual
                && (mc.hitResult == null || mc.hitResult.getType() == net.minecraft.world.phys.HitResult.Type.MISS)
                && (off.getItem() instanceof net.minecraft.world.item.ShieldItem || off.isEmpty() || WeaponClass.of(main).twoHanded());
        boolean blocking = (SkyKeys.BLOCK.isDown() || rmbBlock) && mc.screen == null && !dual;
        if (blocking != wasBlocking) {
            SkyNetwork.sendToServer(new CorePackets.Action(blocking ? CorePackets.Action.BLOCK_START : CorePackets.Action.BLOCK_STOP, 0));
            wasBlocking = blocking;
            if (blocking) {
                if (off.getItem() instanceof net.minecraft.world.item.ShieldItem) {
                    mc.player.startUsingItem(net.minecraft.world.InteractionHand.OFF_HAND);
                } else if (!main.isEmpty()) {
                    mc.player.startUsingItem(net.minecraft.world.InteractionHand.MAIN_HAND);
                }
            } else {
                mc.player.stopUsingItem();
            }
        }

        // Out of stamina: no sprinting until it recovers.
        if (ClientState.has(CorePackets.SyncVitals.EXHAUSTED) && !mc.player.isCreative()) {
            mc.player.setSprinting(false);
            mc.options.keySprint.setDown(false);
        }
    }

    @SubscribeEvent
    public static void hideVanillaBars(RenderGuiOverlayEvent.Pre event) {
        var id = event.getOverlay().id();
        if (id.equals(VanillaGuiOverlay.PLAYER_HEALTH.id()) || id.equals(VanillaGuiOverlay.FOOD_LEVEL.id())
                || id.equals(VanillaGuiOverlay.ARMOR_LEVEL.id()) || id.equals(VanillaGuiOverlay.EXPERIENCE_BAR.id())
                || id.equals(VanillaGuiOverlay.HOTBAR.id()) || id.equals(VanillaGuiOverlay.BOSS_EVENT_PROGRESS.id())) {
            event.setCanceled(true);
        }
    }

    /** Archery "Eagle Eye": sneak while drawing a bow to zoom in. */
    @SubscribeEvent
    public static void fov(ComputeFovModifierEvent event) {
        var player = event.getPlayer();
        if (player.isUsingItem() && player.getUseItem().getItem() instanceof BowItem && player.isCrouching()
                && Perks.has(player, "archery.eagle_eye")) {
            event.setNewFovModifier(event.getNewFovModifier() * 0.6f);
        }
    }

    /** No vanilla tutorials/toasts: clear toasts on every HUD render. */
    @SubscribeEvent
    public static void clearToasts(net.minecraftforge.client.event.RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getToasts() != null) {
            mc.getToasts().clear();
        }
    }

    /** Pause screen Unstuck button. */
    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof PauseScreen pause) {
            int w = 105;
            int h = 20;
            int x = pause.width - w - 8;
            int y = 6;
            event.addListener(Button.builder(
                    Component.translatable("menu.skycraft.unstuck"),
                    btn -> {
                        if (ClientState.has(CorePackets.SyncVitals.IN_COMBAT)) {
                            Minecraft.getInstance().gui.setOverlayMessage(
                                    Component.translatable("message.skycraft.unstuck_combat"), false);
                            return;
                        }
                        SkyNetwork.sendToServer(new CorePackets.Action(CorePackets.Action.UNSTUCK, 0));
                        pause.onClose();
                    }
            ).bounds(x, y, w, h).build());
        }
    }

    /** Intercept screens: replace vanilla title, create world, options, and load screens with Skyrim suite. */
    @SubscribeEvent
    public static void onScreenOpen(ScreenEvent.Opening event) {
        net.minecraft.client.gui.screens.Screen screen = event.getNewScreen();
        if (screen == null) return;
        String name = screen.getClass().getName().toLowerCase(java.util.Locale.ROOT);
        if (name.contains("destiny") || name.contains("editor") && name.contains("mca")) {
            event.setCanceled(true);
            return;
        }

        if (screen instanceof net.minecraft.client.gui.screens.TitleScreen && !(screen instanceof com.skycraft.client.screen.title.SkyTitleScreen)) {
            event.setNewScreen(new com.skycraft.client.screen.title.SkyTitleScreen());
            return;
        }

        if (screen instanceof net.minecraft.client.gui.screens.worldselection.CreateWorldScreen cws && !(screen instanceof com.skycraft.client.screen.title.SkyCreateWorldScreen)) {
            event.setNewScreen(new com.skycraft.client.screen.title.SkyCreateWorldScreen(cws));
            return;
        }

        if (screen instanceof net.minecraft.client.gui.screens.worldselection.SelectWorldScreen && !(screen instanceof com.skycraft.client.screen.title.SkyLoadWorldScreen)) {
            event.setNewScreen(new com.skycraft.client.screen.title.SkyLoadWorldScreen(new com.skycraft.client.screen.title.SkyTitleScreen()));
            return;
        }

        if (screen instanceof net.minecraft.client.gui.screens.OptionsScreen && !(screen instanceof com.skycraft.client.screen.title.SkyOptionsScreen)) {
            event.setNewScreen(new com.skycraft.client.screen.title.SkyOptionsScreen(Minecraft.getInstance().screen, Minecraft.getInstance().options));
        }
    }
}

