package com.skycraft.fauna;

import com.skycraft.fauna.client.FaunaClientEvents;
import com.skycraft.network.SkyNetwork;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Network packets for the fauna module: horse calling, passenger ride requests, and taming balance mini-game.
 */
public final class FaunaPackets {
    private FaunaPackets() {}

    public static void register() {
        SkyNetwork.register(CallHorse.class, NetworkDirection.PLAY_TO_SERVER, CallHorse::encode, CallHorse::decode, CallHorse::handle);
        SkyNetwork.register(SteerBalance.class, NetworkDirection.PLAY_TO_SERVER, SteerBalance::encode, SteerBalance::decode, SteerBalance::handle);
        SkyNetwork.register(TamePrompt.class, NetworkDirection.PLAY_TO_CLIENT, TamePrompt::encode, TamePrompt::decode, TamePrompt::handle);
    }

    // ------------------------------------------------------------------ C2S Call Horse (H)
    public record CallHorse() {
        static void encode(CallHorse m, FriendlyByteBuf buf) {}
        static CallHorse decode(FriendlyByteBuf buf) { return new CallHorse(); }
        static void handle(CallHorse m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                HorseManager.callHorse(player);
            }
            ctx.get().setPacketHandled(true);
        }
    }

    // ------------------------------------------------------------------ C2S Steer Balance
    public record SteerBalance(int direction) {
        static void encode(SteerBalance m, FriendlyByteBuf buf) { buf.writeInt(m.direction); }
        static SteerBalance decode(FriendlyByteBuf buf) { return new SteerBalance(buf.readInt()); }
        static void handle(SteerBalance m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                WildHorseTaming.onClientSteer(player, m.direction);
            }
            ctx.get().setPacketHandled(true);
        }
    }

    // ------------------------------------------------------------------ S2C Tame Prompt
    public record TamePrompt(int direction, int ticks, int progress) {
        static void encode(TamePrompt m, FriendlyByteBuf buf) {
            buf.writeInt(m.direction);
            buf.writeInt(m.ticks);
            buf.writeInt(m.progress);
        }
        static TamePrompt decode(FriendlyByteBuf buf) {
            return new TamePrompt(buf.readInt(), buf.readInt(), buf.readInt());
        }
        static void handle(TamePrompt m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> FaunaClientEvents.handleTamePrompt(m)));
            ctx.get().setPacketHandled(true);
        }
    }
}

