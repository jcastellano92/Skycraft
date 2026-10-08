package com.skycraft.atmosphere.client;

import net.minecraft.Util;

import java.lang.reflect.Method;

/**
 * Detects an active Oculus/Iris shader pack through the public Iris API
 * ({@code net.irisshaders.iris.api.v0.IrisApi.getInstance().isShaderPackInUse()}) by reflection, so there is no
 * compile or runtime dependency. Any failure means "no shaders". The answer is cached for a second.
 */
final class ShaderCompat {
    private static boolean unavailable;
    private static Object api;
    private static Method inUse;
    private static long lastCheck = Long.MIN_VALUE;
    private static boolean cached;

    private ShaderCompat() {}

    static boolean shadersActive() {
        if (unavailable) return false;
        long now = Util.getMillis();
        if (now - lastCheck < 1000L) return cached;
        lastCheck = now;
        cached = query();
        return cached;
    }

    private static boolean query() {
        try {
            if (api == null) {
                Class<?> c = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                api = c.getMethod("getInstance").invoke(null);
                inUse = c.getMethod("isShaderPackInUse");
                if (api == null) {
                    unavailable = true;
                    return false;
                }
            }
            return Boolean.TRUE.equals(inUse.invoke(api));
        } catch (Throwable t) {
            unavailable = true;
            return false;
        }
    }
}
