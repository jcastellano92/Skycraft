package com.skycraft.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Contract 7 entry point (docs/PLAYTEST-1.md): opens the character and status page.
 *
 * <p>STUB: workstream A replaces the body; the signature is fixed.
 */
@OnlyIn(Dist.CLIENT)
public final class CharacterClient {
    private CharacterClient() {}

    public static void open() {
        net.minecraft.client.Minecraft.getInstance().setScreen(new com.skycraft.client.screen.CharacterStatusScreen());
    }
}
