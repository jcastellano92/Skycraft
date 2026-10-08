package com.skycraft.roads.client;

import com.skycraft.Skycraft;
import com.skycraft.client.hud.CompassMarkers;
import com.skycraft.roads.RoadsPackets;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/** Client side of the roads module: compass markers for nearby, not yet discovered settlements. */
@Mod.EventBusSubscriber(modid = Skycraft.MODID, value = Dist.CLIENT)
public final class RoadsClient {
    public static final String SOURCE = "roads";
    private static final int COLOR = 0xC8B48A;

    private RoadsClient() {}

    public static void setMarkers(int range, List<RoadsPackets.Entry> entries) {
        if (entries.isEmpty()) {
            CompassMarkers.clear(SOURCE);
            return;
        }
        List<CompassMarkers.Marker> markers = new ArrayList<>(entries.size());
        for (RoadsPackets.Entry e : entries) {
            markers.add(new CompassMarkers.Marker(new Vec3(e.x() + 0.5, e.y(), e.z() + 0.5),
                    CompassMarkers.Shape.LOCATION, COLOR, e.name(), range));
        }
        CompassMarkers.set(SOURCE, markers);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        CompassMarkers.clear(SOURCE);
    }
}
