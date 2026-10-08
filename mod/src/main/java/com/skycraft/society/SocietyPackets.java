package com.skycraft.society;

import com.skycraft.network.SkyNetwork;
import com.skycraft.society.client.SocietyClientPackets;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** Network messages of the society module (registered in this fixed order). */
public final class SocietyPackets {
    private SocietyPackets() {}

    public static void register() {
        SkyNetwork.register(Bark.class, NetworkDirection.PLAY_TO_CLIENT, Bark::encode, Bark::decode, Bark::handle);
        SkyNetwork.register(CosmeticSync.class, NetworkDirection.PLAY_TO_CLIENT, CosmeticSync::encode, CosmeticSync::decode, CosmeticSync::handle);
        SkyNetwork.register(ChooseCosmetic.class, NetworkDirection.PLAY_TO_SERVER, ChooseCosmetic::encode, ChooseCosmetic::decode, ChooseCosmetic::handle);
    }

    /** S2C: show {@code text} above entity {@code entityId} for {@code ticks}. */
    public record Bark(int entityId, Component text, int ticks) {
        static void encode(Bark m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.entityId);
            buf.writeComponent(m.text);
            buf.writeVarInt(m.ticks);
        }

        static Bark decode(FriendlyByteBuf buf) {
            return new Bark(buf.readVarInt(), buf.readComponent(), buf.readVarInt());
        }

        static void handle(Bark m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> SocietyClientPackets.bark(m.entityId, m.text, m.ticks));
            ctx.get().setPacketHandled(true);
        }
    }

    /** S2C: the faction cosmetic a player wears ("" = none). */
    public record CosmeticSync(UUID player, String cosmetic) {
        static void encode(CosmeticSync m, FriendlyByteBuf buf) {
            buf.writeUUID(m.player);
            buf.writeUtf(m.cosmetic, 64);
        }

        static CosmeticSync decode(FriendlyByteBuf buf) {
            return new CosmeticSync(buf.readUUID(), buf.readUtf(64));
        }

        static void handle(CosmeticSync m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> SocietyClientPackets.cosmetic(m.player, m.cosmetic));
            ctx.get().setPacketHandled(true);
        }
    }

    /** C2S: the player picked a cosmetic in the reputation screen ("" = none). */
    public record ChooseCosmetic(String cosmetic) {
        static void encode(ChooseCosmetic m, FriendlyByteBuf buf) {
            buf.writeUtf(m.cosmetic, 64);
        }

        static ChooseCosmetic decode(FriendlyByteBuf buf) {
            return new ChooseCosmetic(buf.readUtf(64));
        }

        static void handle(ChooseCosmetic m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) Cosmetics.choose(player, m.cosmetic);
            ctx.get().setPacketHandled(true);
        }
    }
}
