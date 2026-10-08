package com.skycraft.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * Every Skycraft key binding. Modules read these in their own client tick handlers; each key is consumed by
 * exactly one module:
 * <ul>
 *     <li>core: SKILLS, POWER_ATTACK, BLOCK, RACIAL_POWER</li>
 *     <li>magic: MAGIC_MENU, CAST, SHOUT</li>
 *     <li>quest: JOURNAL, PARTY</li>
 *     <li>world: WAIT, MAP</li>
 *     <li>crime: (uses vanilla use key while sneaking for pickpocketing)</li>
 * </ul>
 */
public final class SkyKeys {
    public static final String CATEGORY = "key.categories.skycraft";

    public static final KeyMapping SKILLS = key("skills", GLFW.GLFW_KEY_K);
    public static final KeyMapping HUB = key("hub", GLFW.GLFW_KEY_TAB);
    public static final KeyMapping JOURNAL = key("journal", GLFW.GLFW_KEY_J);
    public static final KeyMapping MAGIC_MENU = key("magic_menu", GLFW.GLFW_KEY_P);
    public static final KeyMapping SHOUT = key("shout", GLFW.GLFW_KEY_Z);
    public static final KeyMapping WAIT = key("wait", GLFW.GLFW_KEY_I);
    public static final KeyMapping MAP = key("map", GLFW.GLFW_KEY_M);

    // Compatibility aliases for keys transitioning to mouse/hold/journal controls
    public static final KeyMapping CAST = key("cast", InputConstants.UNKNOWN.getValue());
    public static final KeyMapping POWER_ATTACK = key("power_attack", InputConstants.UNKNOWN.getValue());
    public static final KeyMapping BLOCK = key("block", InputConstants.UNKNOWN.getValue());
    public static final KeyMapping RACIAL_POWER = SHOUT;
    public static final KeyMapping PARTY = JOURNAL;

    public static final KeyMapping[] ALL = {SKILLS, HUB, JOURNAL, MAGIC_MENU, SHOUT, WAIT, MAP};

    private SkyKeys() {}

    private static KeyMapping key(String name, int code) {
        return new KeyMapping("key.skycraft." + name, KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, code, CATEGORY);
    }
}
