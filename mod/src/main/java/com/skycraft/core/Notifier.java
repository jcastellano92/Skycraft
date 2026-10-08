package com.skycraft.core;

import com.skycraft.network.CorePackets;
import com.skycraft.network.NotifyKind;
import com.skycraft.network.SkyNetwork;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Server-side helper for sending Skyrim-style HUD notifications. */
public final class Notifier {
    private Notifier() {}

    public static void send(ServerPlayer player, NotifyKind kind, Component title, Component subtitle) {
        SkyNetwork.sendToPlayer(player, new CorePackets.Notify(kind, title, subtitle, 0, 0f));
    }

    public static void send(ServerPlayer player, NotifyKind kind, Component title, Component subtitle, int value, float progress) {
        SkyNetwork.sendToPlayer(player, new CorePackets.Notify(kind, title, subtitle, value, progress));
    }

    /** Small top-left message ("You need a pickaxe to mine this"). */
    public static void message(ServerPlayer player, Component text) {
        send(player, NotifyKind.MESSAGE, text, Component.empty());
    }

    /** Big centered title ("DRAGON SOUL ABSORBED"). */
    public static void title(ServerPlayer player, Component title, Component subtitle) {
        send(player, NotifyKind.BIG_TITLE, title, subtitle);
    }
}
