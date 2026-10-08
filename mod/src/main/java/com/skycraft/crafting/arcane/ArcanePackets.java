package com.skycraft.crafting.arcane;

import com.skycraft.crafting.arcane.alchemy.Alchemy;
import com.skycraft.crafting.arcane.client.ArcaneClientHandlers;
import com.skycraft.crafting.arcane.enchant.Enchanting;
import com.skycraft.network.SkyNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Arcane packets: the server opens station screens; the client asks for (validated) station actions. */
public final class ArcanePackets {
    private ArcanePackets() {}

    public static void register() {
        SkyNetwork.register(OpenStation.class, NetworkDirection.PLAY_TO_CLIENT, OpenStation::encode, OpenStation::decode, OpenStation::handle);
        SkyNetwork.register(Disenchant.class, NetworkDirection.PLAY_TO_SERVER, Disenchant::encode, Disenchant::decode, Disenchant::handle);
        SkyNetwork.register(Enchant.class, NetworkDirection.PLAY_TO_SERVER, Enchant::encode, Enchant::decode, Enchant::handle);
        SkyNetwork.register(Combine.class, NetworkDirection.PLAY_TO_SERVER, Combine::encode, Combine::decode, Combine::handle);
        SkyNetwork.register(Taste.class, NetworkDirection.PLAY_TO_SERVER, Taste::encode, Taste::decode, Taste::handle);
    }

    // ------------------------------------------------------------------ S2C

    /** Opens the Arcane Enchanter ({@code kind} 0) or Alchemy Lab (1) screen for the station at {@code pos}. */
    public record OpenStation(int kind, BlockPos pos) {
        static void encode(OpenStation m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.kind);
            buf.writeBlockPos(m.pos);
        }

        static OpenStation decode(FriendlyByteBuf buf) {
            return new OpenStation(buf.readVarInt(), buf.readBlockPos());
        }

        static void handle(OpenStation m, Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ArcaneClientHandlers.openStation(m.kind, m.pos));
            ctx.get().setPacketHandled(true);
        }
    }

    // ------------------------------------------------------------------ C2S

    /** Destroy the item in an inventory slot and learn its enchantments. */
    public record Disenchant(BlockPos pos, int slot) {
        static void encode(Disenchant m, FriendlyByteBuf buf) {
            buf.writeBlockPos(m.pos);
            buf.writeVarInt(m.slot);
        }

        static Disenchant decode(FriendlyByteBuf buf) {
            return new Disenchant(buf.readBlockPos(), buf.readVarInt());
        }

        static void handle(Disenchant m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) Enchanting.disenchant(player, m.pos, m.slot);
            ctx.get().setPacketHandled(true);
        }
    }

    /** Enchant the item in {@code itemSlot} with a known enchantment, powered by the soul gem in {@code gemSlot}. */
    public record Enchant(BlockPos pos, int itemSlot, String enchantment, int gemSlot) {
        static void encode(Enchant m, FriendlyByteBuf buf) {
            buf.writeBlockPos(m.pos);
            buf.writeVarInt(m.itemSlot);
            buf.writeUtf(m.enchantment, 256);
            buf.writeVarInt(m.gemSlot);
        }

        static Enchant decode(FriendlyByteBuf buf) {
            return new Enchant(buf.readBlockPos(), buf.readVarInt(), buf.readUtf(256), buf.readVarInt());
        }

        static void handle(Enchant m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) Enchanting.enchant(player, m.pos, m.itemSlot, m.enchantment, m.gemSlot);
            ctx.get().setPacketHandled(true);
        }
    }

    /** Combine the ingredients in 2-3 inventory slots into a potion or poison. */
    public record Combine(BlockPos pos, int[] slots) {
        static void encode(Combine m, FriendlyByteBuf buf) {
            buf.writeBlockPos(m.pos);
            buf.writeVarInt(m.slots.length);
            for (int s : m.slots) buf.writeVarInt(s);
        }

        static Combine decode(FriendlyByteBuf buf) {
            BlockPos pos = buf.readBlockPos();
            int n = Math.max(0, Math.min(8, buf.readVarInt()));
            int[] slots = new int[n];
            for (int i = 0; i < n; i++) slots[i] = buf.readVarInt();
            return new Combine(pos, slots);
        }

        static void handle(Combine m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) Alchemy.combine(player, m.pos, m.slots);
            ctx.get().setPacketHandled(true);
        }
    }

    /** Taste (eat) one ingredient to learn its first unknown effect(s). */
    public record Taste(BlockPos pos, int slot) {
        static void encode(Taste m, FriendlyByteBuf buf) {
            buf.writeBlockPos(m.pos);
            buf.writeVarInt(m.slot);
        }

        static Taste decode(FriendlyByteBuf buf) {
            return new Taste(buf.readBlockPos(), buf.readVarInt());
        }

        static void handle(Taste m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) Alchemy.taste(player, m.pos, m.slot);
            ctx.get().setPacketHandled(true);
        }
    }
}
