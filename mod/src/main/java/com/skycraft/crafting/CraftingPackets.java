package com.skycraft.crafting;

import com.skycraft.crafting.menu.StationMenu;
import com.skycraft.network.SkyNetwork;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Smithing packets (all client -> server; the server validates the open station menu, perks and materials). */
public final class CraftingPackets {
    private CraftingPackets() {}

    public static void register() {
        SkyNetwork.register(Craft.class, NetworkDirection.PLAY_TO_SERVER, Craft::encode, Craft::decode, Craft::handle);
        SkyNetwork.register(Temper.class, NetworkDirection.PLAY_TO_SERVER, Temper::encode, Temper::decode, Temper::handle);
    }

    /** Make {@code times} of a station recipe. */
    public record Craft(String recipeId, int times) {
        static void encode(Craft m, FriendlyByteBuf buf) {
            buf.writeUtf(m.recipeId, 128);
            buf.writeVarInt(m.times);
        }

        static Craft decode(FriendlyByteBuf buf) {
            return new Craft(buf.readUtf(128), buf.readVarInt());
        }

        static void handle(Craft m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && player.containerMenu instanceof StationMenu menu && menu.stillValid(player)) {
                StationCrafting.craft(player, menu.type, m.recipeId, m.times);
            }
            ctx.get().setPacketHandled(true);
        }
    }

    /** Improve the item in player inventory slot {@code slot} at the open grindstone / armor workbench. */
    public record Temper(int slot) {
        static void encode(Temper m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.slot);
        }

        static Temper decode(FriendlyByteBuf buf) {
            return new Temper(buf.readVarInt());
        }

        static void handle(Temper m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && player.containerMenu instanceof StationMenu menu && menu.stillValid(player) && menu.type.tempering()) {
                Tempering.improve(player, menu.type, m.slot);
                menu.broadcastChanges();
            }
            ctx.get().setPacketHandled(true);
        }
    }
}
