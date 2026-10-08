package com.skycraft.survival;

import com.skycraft.network.SkyNetwork;
import com.skycraft.survival.cooking.Cooking;
import com.skycraft.survival.cooking.CookingMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Survival packets (registered in this order). */
public final class SurvivalPackets {
    private SurvivalPackets() {}

    public static void register() {
        SkyNetwork.register(Cook.class, NetworkDirection.PLAY_TO_SERVER, Cook::encode, Cook::decode, Cook::handle);
    }

    /** C2S: cook {@code times} of a cooking pot recipe at the open cooking pot. */
    public record Cook(String recipeId, int times) {
        static void encode(Cook m, FriendlyByteBuf buf) {
            buf.writeUtf(m.recipeId, 128);
            buf.writeVarInt(m.times);
        }

        static Cook decode(FriendlyByteBuf buf) {
            return new Cook(buf.readUtf(128), buf.readVarInt());
        }

        static void handle(Cook m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && player.containerMenu instanceof CookingMenu menu && menu.stillValid(player)) {
                Cooking.cook(player, menu, m.recipeId, m.times);
            }
            ctx.get().setPacketHandled(true);
        }
    }
}
