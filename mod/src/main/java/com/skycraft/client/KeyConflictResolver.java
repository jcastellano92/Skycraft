package com.skycraft.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.skycraft.Skycraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.Set;

/**
 * Good default controls in a big modpack: on first launch, any Skycraft key that still has its default binding
 * but collides with another in-game key (vanilla or another mod) is moved to a free key. Players' own
 * rebinds are never touched: once a key has been moved it is no longer at its default, so it is left alone.
 * Covers every mapping named {@code key.skycraft.*}, including the ones feature modules register.
 */
@Mod.EventBusSubscriber(modid = Skycraft.MODID, value = Dist.CLIENT)
public final class KeyConflictResolver {
    private static final int[] FALLBACKS = {
            GLFW.GLFW_KEY_G, GLFW.GLFW_KEY_J, GLFW.GLFW_KEY_K, GLFW.GLFW_KEY_N, GLFW.GLFW_KEY_O,
            GLFW.GLFW_KEY_P, GLFW.GLFW_KEY_U, GLFW.GLFW_KEY_I, GLFW.GLFW_KEY_Y, GLFW.GLFW_KEY_Z, GLFW.GLFW_KEY_X,
            GLFW.GLFW_KEY_B, GLFW.GLFW_KEY_V, GLFW.GLFW_KEY_R, GLFW.GLFW_KEY_C, GLFW.GLFW_KEY_COMMA, GLFW.GLFW_KEY_PERIOD,
            GLFW.GLFW_KEY_SEMICOLON, GLFW.GLFW_KEY_APOSTROPHE, GLFW.GLFW_KEY_LEFT_BRACKET, GLFW.GLFW_KEY_RIGHT_BRACKET,
            GLFW.GLFW_KEY_BACKSLASH, GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_EQUAL, GLFW.GLFW_KEY_CAPS_LOCK,
            GLFW.GLFW_KEY_KP_0, GLFW.GLFW_KEY_KP_1, GLFW.GLFW_KEY_KP_2, GLFW.GLFW_KEY_KP_3
    };
    private static boolean done;

    private KeyConflictResolver() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null) return;
        done = true;
        try {
            resolve(mc);
        } catch (Exception e) {
            Skycraft.LOGGER.warn("Could not resolve key conflicts", e);
        }
    }

    private static void resolve(Minecraft mc) {
        KeyMapping[] all = mc.options.keyMappings;
        boolean changed = false;
        // Silence intrusive keys from other mods & vanilla conflicting with Skyrim controls
        for (KeyMapping k : all) {
            String name = k.getName().toLowerCase(java.util.Locale.ROOT);
            if (name.equals("key.drop") || name.equals("key.swapoffhand") || name.equals("key.advancements")) {
                k.setKey(InputConstants.UNKNOWN);
                changed = true;
            } else if (name.contains("treechop") || name.contains("essential") || name.contains("emote")) {
                k.setKey(InputConstants.UNKNOWN);
                changed = true;
            } else if (name.equals("key.chat") && (k.getKey().getValue() == GLFW.GLFW_KEY_Z || k.getKey().getValue() == GLFW.GLFW_KEY_R)) {
                k.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_ENTER));
                changed = true;
            }
        }

        for (KeyMapping ours : all) {
            if (!ours.getName().startsWith("key.skycraft.") || !ours.isDefault() || ours.isUnbound()) continue;
            if (!conflicts(ours, ours.getKey(), all)) continue;
            Set<Integer> used = usedKeys(all, ours);
            for (int code : FALLBACKS) {
                InputConstants.Key candidate = InputConstants.Type.KEYSYM.getOrCreate(code);
                if (used.contains(code) || conflicts(ours, candidate, all)) continue;
                Skycraft.LOGGER.info("Rebinding {} from {} to {} to avoid a key conflict", ours.getName(),
                        ours.getKey().getName(), candidate.getName());
                ours.setKey(candidate);
                changed = true;
                break;
            }
        }
        if (changed) {
            KeyMapping.resetMapping();
            mc.options.save();
        }
    }

    private static Set<Integer> usedKeys(KeyMapping[] all, KeyMapping self) {
        Set<Integer> used = new HashSet<>();
        for (KeyMapping k : all) {
            if (k != self && !k.isUnbound() && k.getKey().getType() == InputConstants.Type.KEYSYM) used.add(k.getKey().getValue());
        }
        return used;
    }

    /** True if another mapping that can be active in game uses {@code key} without a modifier. */
    private static boolean conflicts(KeyMapping self, InputConstants.Key key, KeyMapping[] all) {
        for (KeyMapping other : all) {
            if (other == self || other.isUnbound()) continue;
            if (!other.getKey().equals(key)) continue;
            if (self.getKeyConflictContext().conflicts(other.getKeyConflictContext())) return true;
        }
        return false;
    }
}
