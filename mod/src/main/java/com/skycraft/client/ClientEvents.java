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

        // BotW-style climbing: holding Space against a wall
        boolean jumping = mc.options.keyJump.isDown();
        if (jumping && mc.player.horizontalCollision && !mc.player.isInWater() && !mc.player.isPassenger()
                && !mc.player.isCreative() && !mc.player.isSpectator() && !mc.player.onClimbable()) {
            if (!ClientState.has(CorePackets.SyncVitals.EXHAUSTED)) {
                double ySpeed = 0.16;
                if (Perks.has(mc.player, "athletics.climber")) ySpeed += 0.05;
                Vec3 v = mc.player.getDeltaMovement();
                mc.player.setDeltaMovement(v.x * 0.4, ySpeed, v.z * 0.4);
                mc.player.resetFallDistance();
                if (climbResend-- <= 0) {
                    SkyNetwork.sendToServer(new CorePackets.Action(CorePackets.Action.CLIMB_TICK, 0));
                    climbResend = 4;
                }
            }
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

        // Weapon blocking: dual-wielding has no block
        ItemStack main = mc.player.getMainHandItem();
        ItemStack off = mc.player.getOffhandItem();
        boolean dual = !main.isEmpty() && !off.isEmpty()
                && WeaponClass.of(main).skill == Skill.ONE_HANDED
                && WeaponClass.of(off).skill == Skill.ONE_HANDED;
        boolean blocking = SkyKeys.BLOCK.isDown() && mc.screen == null && !dual;
        if (blocking != wasBlocking) {
            SkyNetwork.sendToServer(new CorePackets.Action(blocking ? CorePackets.Action.BLOCK_START : CorePackets.Action.BLOCK_STOP, 0));
            wasBlocking = blocking;
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
                || id.equals(VanillaGuiOverlay.HOTBAR.id())) {
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

    /** Intercept screens: prevent MCA destiny/editor screen, redirect death and title screen if desired. */
    @SubscribeEvent
    public static void onScreenOpen(ScreenEvent.Opening event) {
        net.minecraft.client.gui.screens.Screen screen = event.getNewScreen();
        if (screen == null) return;
        String name = screen.getClass().getName().toLowerCase(java.util.Locale.ROOT);
        if (name.contains("destiny") || name.contains("editor") && name.contains("mca")) {
            event.setCanceled(true);
        }
    }
}

