package com.skycraft.arsenal;

import com.skycraft.arsenal.client.ArsenalFxClient;
import com.skycraft.arsenal.client.KillCamClient;
import com.skycraft.network.SkyNetwork;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/** Arsenal packets: kill cams and staff visuals (S2C), Bloodskal swings (C2S). Registered in a fixed order. */
public final class ArsenalPackets {
    private ArsenalPackets() {}

    public static void register() {
        SkyNetwork.register(KillCam.class, NetworkDirection.PLAY_TO_CLIENT, KillCam::encode, KillCam::decode, KillCam::handle);
        SkyNetwork.register(Fx.class, NetworkDirection.PLAY_TO_CLIENT, Fx::encode, Fx::decode, Fx::handle);
        SkyNetwork.register(BladeSwing.class, NetworkDirection.PLAY_TO_SERVER, BladeSwing::encode, BladeSwing::decode, BladeSwing::handle);
    }

    // ------------------------------------------------------------------ S2C

    /**
     * Plays a kill cam on the killer's client. {@code kind} is {@link #MELEE} or {@link #ARROW}; for arrows
     * {@code arrowId} is the arrow entity (may already be gone) and {@code dir} its flight direction.
     */
    public record KillCam(int victimId, byte kind, int arrowId, float dx, float dy, float dz) {
        public static final byte MELEE = 0;
        public static final byte ARROW = 1;

        static void encode(KillCam m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.victimId);
            buf.writeByte(m.kind);
            buf.writeVarInt(m.arrowId);
            buf.writeFloat(m.dx);
            buf.writeFloat(m.dy);
            buf.writeFloat(m.dz);
        }

        static KillCam decode(FriendlyByteBuf buf) {
            return new KillCam(buf.readVarInt(), buf.readByte(), buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readFloat());
        }

        static void handle(KillCam m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> KillCamClient.start(m.victimId, m.kind, m.arrowId, new Vec3(m.dx, m.dy, m.dz)));
            ctx.get().setPacketHandled(true);
        }
    }

    /** A staff/artifact visual between two points (streams, beams, lightning arcs, bursts, wards). */
    public record Fx(byte kind, float ax, float ay, float az, float bx, float by, float bz) {
        public static final byte FIRE_STREAM = 0;
        public static final byte FROST_STREAM = 1;
        public static final byte SHOCK_STREAM = 2;
        public static final byte HEAL_BEAM = 3;
        public static final byte MAGNUS_BEAM = 4;
        public static final byte LIGHTNING_ARC = 5;
        public static final byte FIRE_BURST = 6;
        public static final byte SUN_BURST = 7;
        public static final byte FROST_BURST = 8;
        public static final byte WARD = 9;
        public static final byte SOUL_BURST = 10;

        public Fx(byte kind, Vec3 a, Vec3 b) {
            this(kind, (float) a.x, (float) a.y, (float) a.z, (float) b.x, (float) b.y, (float) b.z);
        }

        static void encode(Fx m, FriendlyByteBuf buf) {
            buf.writeByte(m.kind);
            buf.writeFloat(m.ax);
            buf.writeFloat(m.ay);
            buf.writeFloat(m.az);
            buf.writeFloat(m.bx);
            buf.writeFloat(m.by);
            buf.writeFloat(m.bz);
        }

        static Fx decode(FriendlyByteBuf buf) {
            return new Fx(buf.readByte(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat());
        }

        static void handle(Fx m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ArsenalFxClient.play(m.kind,
                    new Vec3(m.ax, m.ay, m.az), new Vec3(m.bx, m.by, m.bz)));
            ctx.get().setPacketHandled(true);
        }

        /** Sends the effect to every player within 64 blocks of {@code a}. */
        public static void send(ServerLevel level, byte kind, Vec3 a, Vec3 b) {
            SkyNetwork.CHANNEL.send(PacketDistributor.NEAR.with(PacketDistributor.TargetPoint.p(a.x, a.y, a.z, 64, level.dimension())),
                    new Fx(kind, a, b));
        }
    }

    // ------------------------------------------------------------------ C2S

    /** The client swung Bloodskal at full strength without hitting a creature (hits are handled server-side). */
    public record BladeSwing() {
        static void encode(BladeSwing m, FriendlyByteBuf buf) {
        }

        static BladeSwing decode(FriendlyByteBuf buf) {
            return new BladeSwing();
        }

        static void handle(BladeSwing m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) ArtifactEvents.bloodskalSwing(player);
            ctx.get().setPacketHandled(true);
        }
    }
}
