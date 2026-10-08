package com.skycraft.world;

import com.skycraft.network.SkyNetwork;
import com.skycraft.world.client.WorldClient;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Packets of the world module. Registration order is fixed (ids must match on both sides). */
public final class WorldPackets {
    private WorldPackets() {}

    static void register() {
        SkyNetwork.register(Cue.class, NetworkDirection.PLAY_TO_CLIENT, Cue::encode, Cue::decode, Cue::handle);
        SkyNetwork.register(RestFade.class, NetworkDirection.PLAY_TO_CLIENT, RestFade::encode, RestFade::decode, RestFade::handle);
        SkyNetwork.register(Rest.class, NetworkDirection.PLAY_TO_SERVER, Rest::encode, Rest::decode, Rest::handle);
        SkyNetwork.register(FastTravel.class, NetworkDirection.PLAY_TO_SERVER, FastTravel::encode, FastTravel::decode, FastTravel::handle);
    }

    // ------------------------------------------------------------------ S2C

    /** A musical cue on the client (discovery sting). */
    public record Cue(int cue) {
        public static final int DISCOVERY = 0;

        static void encode(Cue m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.cue);
        }

        static Cue decode(FriendlyByteBuf buf) {
            return new Cue(buf.readVarInt());
        }

        static void handle(Cue m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> WorldClient.cue(m.cue));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Fade to black after waiting, sleeping or fast travelling ("8 hours later..."). */
    public record RestFade(int hours, int kind) {
        public static final int WAIT = 0;
        public static final int SLEEP = 1;
        public static final int TRAVEL = 2;

        static void encode(RestFade m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.hours);
            buf.writeVarInt(m.kind);
        }

        static RestFade decode(FriendlyByteBuf buf) {
            return new RestFade(buf.readVarInt(), buf.readVarInt());
        }

        static void handle(RestFade m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> WorldClient.fade(m.hours, m.kind));
            ctx.get().setPacketHandled(true);
        }
    }

    // ------------------------------------------------------------------ C2S

    /** Request to wait or sleep {@code hours} (1-24). {@code bed} is only used when sleeping. */
    public record Rest(int hours, boolean sleep, BlockPos bed) {
        static void encode(Rest m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.hours);
            buf.writeBoolean(m.sleep);
            buf.writeBlockPos(m.bed);
        }

        static Rest decode(FriendlyByteBuf buf) {
            return new Rest(buf.readVarInt(), buf.readBoolean(), buf.readBlockPos());
        }

        static void handle(Rest m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) RestManager.request(player, m.hours, m.sleep, m.bed);
            ctx.get().setPacketHandled(true);
        }
    }

    /** Fast travel to a discovered location by id. */
    public record FastTravel(String id) {
        static void encode(FastTravel m, FriendlyByteBuf buf) {
            buf.writeUtf(m.id, 512);
        }

        static FastTravel decode(FriendlyByteBuf buf) {
            return new FastTravel(buf.readUtf(512));
        }

        static void handle(FastTravel m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) FastTravelHandler.travel(player, m.id);
            ctx.get().setPacketHandled(true);
        }
    }
}
