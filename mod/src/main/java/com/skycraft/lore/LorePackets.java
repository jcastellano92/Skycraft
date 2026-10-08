package com.skycraft.lore;

import com.skycraft.lore.client.LoreClient;
import com.skycraft.network.SkyNetwork;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Lore module packets: the Standing Stone power key (C2S) and "open this book" (S2C). */
public final class LorePackets {
    private LorePackets() {}

    /** Fixed order; called from {@link LoreModule#registerPackets()}. */
    public static void register() {
        SkyNetwork.register(UsePower.class, NetworkDirection.PLAY_TO_SERVER, UsePower::encode, UsePower::decode, UsePower::handle);
        SkyNetwork.register(OpenBook.class, NetworkDirection.PLAY_TO_CLIENT, OpenBook::encode, OpenBook::decode, OpenBook::handle);
    }

    /** The player pressed the Standing Stone power key. */
    public record UsePower() {
        static void encode(UsePower m, FriendlyByteBuf buf) {
        }

        static UsePower decode(FriendlyByteBuf buf) {
            return new UsePower();
        }

        static void handle(UsePower m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) StandingStones.usePower(player);
            ctx.get().setPacketHandled(true);
        }
    }

    /** Opens the reading screen for a book id. */
    public record OpenBook(String id) {
        static void encode(OpenBook m, FriendlyByteBuf buf) {
            buf.writeUtf(m.id, 128);
        }

        static OpenBook decode(FriendlyByteBuf buf) {
            return new OpenBook(buf.readUtf(128));
        }

        static void handle(OpenBook m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> LoreClient.openBook(m.id));
            ctx.get().setPacketHandled(true);
        }
    }
}
