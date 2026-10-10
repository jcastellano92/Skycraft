package com.skycraft.inventory.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Contract 7 entry point (docs/PLAYTEST-1.md). Owned by workstream B; the signature is fixed. */
@OnlyIn(Dist.CLIENT)
public final class InventoryClient {
    private InventoryClient() {}

    public static void open() {
        Minecraft.getInstance().setScreen(new SkyrimInventoryScreen());
    }
}
