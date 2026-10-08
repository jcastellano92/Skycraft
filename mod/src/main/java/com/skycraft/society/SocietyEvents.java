package com.skycraft.society;

import com.skycraft.Skycraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Per-player server ticking of the society systems, spread over different ticks of each second. */
@Mod.EventBusSubscriber(modid = Skycraft.MODID)
public final class SocietyEvents {
    private SocietyEvents() {}

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || !player.isAlive()) return;
        int phase = player.tickCount % 20;
        try {
            switch (phase) {
                case 3 -> {
                    ReputationEvents.recover(player);
                    HitSquads.tick(player);
                }
                case 9 -> Encounters.tick(player);
                case 15 -> {
                    int slot = (player.tickCount / 20) % 5;
                    if (slot == 0) ReputationEvents.scan(player);
                    else if (slot == 2) Settlers.tick(player);
                    else if (slot == 4) Cosmetics.validate(player);
                }
                default -> {
                }
            }
        } catch (RuntimeException e) {
            Skycraft.LOGGER.warn("Skycraft society: player tick failed", e);
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        SocietyCommands.register(event.getDispatcher());
    }
}
