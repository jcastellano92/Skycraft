package com.skycraft.world.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Contract 7 entry point (docs/PLAYTEST-1.md). Owned by workstream C; the signature is fixed. */
@OnlyIn(Dist.CLIENT)
public final class MapClient {
    private MapClient() {}

    public static void openMap() {
        Minecraft.getInstance().setScreen(new MapScreen());
    }
}
