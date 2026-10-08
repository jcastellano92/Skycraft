package com.skycraft.magic.client;

import com.skycraft.Skycraft;
import com.skycraft.client.SkyKeys;
import com.skycraft.magic.MagicData;
import com.skycraft.magic.MagicPackets;
import com.skycraft.network.SkyNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Magic keys: B opens the magic menu, R casts (tap for fire-and-forget, hold for concentration and master spells),
 * Z shouts (hold longer for more words). Right-clicking with an empty main hand also casts.
 */
@Mod.EventBusSubscriber(modid = Skycraft.MODID, value = Dist.CLIENT)
public final class MagicClientEvents {
    /** Ticks needed to charge the 2nd and 3rd Word of Power. */
    public static final int WORD2_TICKS = 7;
    public static final int WORD3_TICKS = 14;

    private static boolean castDown;
    private static boolean rightClickCasting;
    /** Ticks the shout key has been held, or -1. Read by the HUD for the charge indicator. */
    public static int shoutHeld = -1;

    private MagicClientEvents() {}

    public static int wordsFor(int ticks) {
        return ticks >= WORD3_TICKS ? 3 : ticks >= WORD2_TICKS ? 2 : 1;
    }

    public static boolean isCasting() {
        return castDown || rightClickCasting;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ClientFx.tick();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            castDown = false;
            rightClickCasting = false;
            shoutHeld = -1;
            return;
        }

        while (SkyKeys.MAGIC_MENU.consumeClick()) {
            if (mc.screen == null) mc.setScreen(new MagicMenuScreen());
        }

        // Casting: the server is told when the key goes down and up.
        while (SkyKeys.CAST.consumeClick()) {
            // state is read through isDown(); drain the click queue
        }
        boolean cast = SkyKeys.CAST.isDown() && mc.screen == null;
        if (cast != castDown) {
            castDown = cast;
            if (!rightClickCasting) SkyNetwork.sendToServer(new MagicPackets.Cast(cast));
        }
        if (rightClickCasting && (!mc.options.keyUse.isDown() || mc.screen != null)) {
            rightClickCasting = false;
            if (!castDown) SkyNetwork.sendToServer(new MagicPackets.Cast(false));
        }

        // Shouting: hold to charge more words, release to shout.
        while (SkyKeys.SHOUT.consumeClick()) {
            // drained; handled through isDown()
        }
        if (mc.screen != null) {
            shoutHeld = -1;
        } else if (SkyKeys.SHOUT.isDown()) {
            shoutHeld = shoutHeld < 0 ? 0 : shoutHeld + 1;
        } else if (shoutHeld >= 0) {
            SkyNetwork.sendToServer(new MagicPackets.ShoutPacket(wordsFor(shoutHeld)));
            shoutHeld = -1;
        }
    }

    /** Right-click with an empty main hand casts the equipped spell (held for concentration spells). */
    @SubscribeEvent
    public static void onRightClickEmpty(PlayerInteractEvent.RightClickEmpty event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !event.getEntity().getMainHandItem().isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || castDown || rightClickCasting || event.getEntity() != mc.player) return;
        if (MagicData.selectedSpell(mc.player).isEmpty()) return;
        rightClickCasting = true;
        SkyNetwork.sendToServer(new MagicPackets.Cast(true));
    }
}
