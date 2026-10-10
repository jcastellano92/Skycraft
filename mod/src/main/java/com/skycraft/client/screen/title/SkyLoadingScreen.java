package com.skycraft.client.screen.title;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Authentic Skyrim-style 3D interactive loading screen.
 * Replaces the vanilla level loading / receiving screen with:
 * - Interactive 3D artifact inspection with mouse drag and inertial spinning
 * - Procedural drifting atmospheric smoke / mist layers
 * - Cycling Skyrim gameplay tips and Elder Scrolls lore
 * - Minimalist gold-trimmed Nordic progress gauge
 */
@Mod.EventBusSubscriber(modid = com.skycraft.Skycraft.MODID, value = Dist.CLIENT)
public final class SkyLoadingScreen {
    private static final int COLOR_GOLD = 0xFFE8C060;
    private static final int COLOR_DIM = 0xFF9E9689;

    private static final List<String> LORE_TIPS = List.of(
            "The ancient Nordic ruins hold Word Walls carved in the Dragon Tongue. Absorbing them grants Words of Power for your Shouts.",
            "Thirteen Standing Stones are scattered throughout the realm. Each stone bestows a unique blessing upon those who commune with it.",
            "Bounties are tracked separately by each Hold. You may be an outlaw in Whiterun, yet walk freely in the streets of Solitude or Riften.",
            "Crouching activates Sneak. The reticle indicates detection: Hidden, Caution, or Detected.",
            "Carriage drivers outside major hold stables can ferry you quickly between settlements for a modest fee in Septims.",
            "Falmer dwell in the deepest subterranean caverns. Blinded by centuries underground, they hunt entirely by sound and vibration.",
            "A single shield equipped in your off-hand allows you to perform a Shield Bash to stagger oncoming foes.",
            "Hold guards will accept a surrender if you yield by sheathing your weapons when approached.",
            "Blacksmiths welcome travelers to use their anvils, grindstones, and forges freely throughout the daytime hours.",
            "Diseases contracted from beasts can be cured immediately by praying at any wayside Shrine of the Nine Divines.",
            "Sprint and power attacks consume Stamina. When your Stamina is depleted, your attacks deal reduced damage and you cannot run.",
            "Reading certain ancient tomes and skill books will permanently increase your mastery in that discipline.",
            "Hearthfire homestead plots can be established in the wilderness away from towns by placing a Drafting Table.",
            "Lockpicking requires a delicate touch. Rotate the lock gently with [F]; too much force against resistance will snap the pick.",
            "Trolls possess ferocious regenerative powers. Fire spells and flaming weapons halt their healing flesh.",
            "Sweetrolls are a traditional pastry cherished across Skyrim, though notorious for going missing."
    );

    private static final ItemStack[] ARTIFACTS = new ItemStack[]{
            new ItemStack(Items.IRON_HELMET),
            new ItemStack(Items.IRON_SWORD),
            new ItemStack(Items.SHIELD),
            new ItemStack(Items.BOW),
            new ItemStack(Items.GOLD_INGOT),
            new ItemStack(Items.COMPASS)
    };

    private static int activeArtifactIndex = 0;
    private static float rotX = 15.0f;
    private static float rotY = 45.0f;
    private static float rotXVel = 0.0f;
    private static float rotYVel = 0.6f;
    private static boolean isDragging = false;
    private static double lastMouseX = 0;
    private static double lastMouseY = 0;

    private static int currentTipIndex = 0;
    private static float tipAlpha = 1.0f;
    private static int tipTimer = 0;
    private static float globalAnim = 0.0f;

    private SkyLoadingScreen() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        globalAnim += 0.5f;

        // Apply inertial rotation
        if (!isDragging) {
            rotX += rotXVel;
            rotY += rotYVel;
            rotXVel *= 0.92f;
            rotYVel = Mth.lerp(0.04f, rotYVel, 0.45f); // Natural slow drift
            rotX = Mth.clamp(rotX, -60.0f, 60.0f);
        }

        // Cycle lore tips every 160 ticks (~8 seconds)
        tipTimer++;
        if (tipTimer > 160) {
            tipAlpha = Math.max(0.0f, tipAlpha - 0.05f);
            if (tipAlpha <= 0.0f) {
                currentTipIndex = (currentTipIndex + 1) % LORE_TIPS.size();
                activeArtifactIndex = (activeArtifactIndex + 1) % ARTIFACTS.length;
                tipTimer = 0;
            }
        } else if (tipAlpha < 1.0f) {
            tipAlpha = Math.min(1.0f, tipAlpha + 0.05f);
        }
    }

    @SubscribeEvent
    public static void onScreenRenderPre(ScreenEvent.Render.Pre event) {
        Screen screen = event.getScreen();
        if (isLoadingScreen(screen)) {
            event.setCanceled(true);
            renderSkyLoadingScreen(screen, event.getGuiGraphics(), event.getMouseX(), event.getMouseY(), event.getPartialTick());
        }
    }

    @SubscribeEvent
    public static void onMouseClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (isLoadingScreen(event.getScreen()) && event.getButton() == 0) {
            isDragging = true;
            lastMouseX = event.getMouseX();
            lastMouseY = event.getMouseY();
        }
    }

    @SubscribeEvent
    public static void onMouseRelease(ScreenEvent.MouseButtonReleased.Pre event) {
        if (isLoadingScreen(event.getScreen()) && event.getButton() == 0) {
            isDragging = false;
        }
    }

    @SubscribeEvent
    public static void onMouseDrag(ScreenEvent.MouseDragged.Pre event) {
        if (isLoadingScreen(event.getScreen()) && isDragging) {
            double dx = event.getMouseX() - lastMouseX;
            double dy = event.getMouseY() - lastMouseY;
            lastMouseX = event.getMouseX();
            lastMouseY = event.getMouseY();

            rotYVel = (float) dx * 1.5f;
            rotXVel = (float) dy * 1.5f;
            rotY += rotYVel;
            rotX += rotXVel;
            rotX = Mth.clamp(rotX, -60.0f, 60.0f);
        }
    }

    private static boolean isLoadingScreen(Screen screen) {
        return screen instanceof ReceivingLevelScreen || screen instanceof LevelLoadingScreen;
    }

    private static void renderSkyLoadingScreen(Screen screen, GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        int width = screen.width;
        int height = screen.height;
        Font font = mc.font;

        // 1. Deep slate Skyrim background with subtle radial vignette
        g.fill(0, 0, width, height, 0xFF0A0A0C);
        g.fillGradient(0, 0, width, height, 0x00000000, 0x80000000);

        // 2. Procedural drifting mist layers across bottom half
        float time = globalAnim * 0.4f;
        for (int layer = 0; layer < 5; layer++) {
            float speed = (layer + 1) * 0.35f;
            float wave = (float) Math.sin((time * speed * 0.04f) + layer * 1.3f);
            int mistY = height - 85 - layer * 22 + (int) (wave * 8);
            int alpha = 8 + layer * 7;
            int mistColor = (alpha << 24) | 0x1E1C1A;
            g.fillGradient(0, mistY, width, height, mistColor, 0x001E1C1A);
        }

        // 3. Interactive 3D Artifact in center-right
        int modelX = (int) (width * 0.68f);
        int modelY = (int) (height * 0.48f);
        float modelScale = Math.min(width, height) * 0.28f;

        ItemStack artifact = ARTIFACTS[activeArtifactIndex % ARTIFACTS.length];
        render3dArtifact(mc, g, artifact, modelX, modelY, modelScale);

        // Subtle model pedestal / reflection shadow
        g.fill(modelX - 45, modelY + (int) (modelScale * 0.55f), modelX + 45, modelY + (int) (modelScale * 0.57f), 0x30E8C060);

        // 4. Skyrim Header & Hold Lore
        int textX = 40;
        int textY = height / 5;
        g.drawString(font, "S K Y C R A F T", textX, textY, COLOR_GOLD, true);
        g.drawString(font, "THE ELDER SCROLLS IN MINECRAFT", textX, textY + 12, 0xFF7A7265, false);

        // Decorative separator line
        g.fill(textX, textY + 24, textX + 160, textY + 25, COLOR_GOLD);
        g.fill(textX + 160, textY + 24, textX + 185, textY + 25, 0x40E8C060);

        // 5. Skyrim Gameplay Tip & Lore
        int tipBoxY = height - 90;
        int tipBoxW = (int) (width * 0.55f);
        String tipText = LORE_TIPS.get(currentTipIndex % LORE_TIPS.size());

        int textAlphaInt = (int) (tipAlpha * 240);
        if (textAlphaInt > 10) {
            int tipColor = (textAlphaInt << 24) | (COLOR_DIM & 0x00FFFFFF);
            int goldTitleColor = (textAlphaInt << 24) | (COLOR_GOLD & 0x00FFFFFF);
            g.drawString(font, "DID YOU KNOW?", textX, tipBoxY - 14, goldTitleColor, false);

            List<Component> lines = wrapText(font, tipText, tipBoxW);
            for (int i = 0; i < lines.size(); i++) {
                g.drawString(font, lines.get(i), textX, tipBoxY + i * 11, tipColor, false);
            }
        }

        // 6. Minimalist Skyrim Progress / Experience Gauge at bottom
        int barW = Math.min(320, width - 80);
        int barX = (width - barW) / 2;
        int barY = height - 22;

        // Frame
        g.fill(barX - 1, barY - 1, barX + barW + 1, barY + 4, 0xFF2B2620);
        g.fill(barX, barY, barX + barW, barY + 3, 0xFF0D0B09);

        // Real progress from LevelLoadingScreen or smooth monotonic progression
        float realProg = getProgressRatio(screen);
        if (realProg >= 0.0f) {
            displayedProgress = Math.max(displayedProgress, realProg);
        } else {
            displayedProgress = Math.min(0.96f, displayedProgress + 0.003f);
        }
        int pct = Math.round(displayedProgress * 100.0f);
        int fillW = (int) (barW * displayedProgress);
        g.fill(barX, barY, barX + fillW, barY + 3, COLOR_GOLD);

        // Diamond pips on ends
        g.drawString(font, "◆", barX - 8, barY - 3, COLOR_GOLD, false);
        g.drawString(font, "◆", barX + barW + 2, barY - 3, COLOR_GOLD, false);

        // Progress percentage centered above bar
        String pctStr = pct + "%";
        g.drawString(font, pctStr, (width - font.width(pctStr)) / 2, barY - 11, COLOR_GOLD, false);

        // Interactive instruction hint
        g.drawString(font, "Drag mouse to inspect artifact", width - 180, height - 20, 0x70807870, false);
    }

    private static float displayedProgress = 0.05f;

    private static float getProgressRatio(Screen screen) {
        if (screen instanceof LevelLoadingScreen lls) {
            try {
                for (java.lang.reflect.Field f : LevelLoadingScreen.class.getDeclaredFields()) {
                    if (f.getType().getSimpleName().contains("ProgressListener")) {
                        f.setAccessible(true);
                        Object listener = f.get(lls);
                        if (listener != null) {
                            java.lang.reflect.Method m = listener.getClass().getMethod("getProgress");
                            Object res = m.invoke(listener);
                            if (res instanceof Number num) {
                                return Mth.clamp(num.intValue() / 100.0f, 0.0f, 1.0f);
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
        return -1f;
    }

    private static void render3dArtifact(Minecraft mc, GuiGraphics g, ItemStack stack, int x, int y, float scale) {
        if (stack.isEmpty()) return;
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 350.0);
        pose.scale(scale, -scale, scale);
        pose.mulPose(Axis.XP.rotationDegrees(rotX));
        pose.mulPose(Axis.YP.rotationDegrees(rotY));

        mc.getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, 0xF000F0, OverlayTexture.NO_OVERLAY, pose, g.bufferSource(), mc.level, 0);
        g.flush();
        pose.popPose();
    }

    private static List<Component> wrapText(Font font, String text, int maxW) {
        List<Component> list = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder cur = new StringBuilder();
        for (String w : words) {
            String test = cur.isEmpty() ? w : cur + " " + w;
            if (font.width(test) > maxW && !cur.isEmpty()) {
                list.add(Component.literal(cur.toString()));
                cur = new StringBuilder(w);
            } else {
                cur = new StringBuilder(test);
            }
        }
        if (!cur.isEmpty()) {
            list.add(Component.literal(cur.toString()));
        }
        return list;
    }
}

