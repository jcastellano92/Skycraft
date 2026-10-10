package com.skycraft.homestead;

import com.skycraft.core.Notifier;
import com.skycraft.network.SkyNetwork;
import com.skycraft.vitals.ActionHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class HomesteadPackets {
    private HomesteadPackets() {}

    public static void register() {
        SkyNetwork.register(UpdatePerms.class, NetworkDirection.PLAY_TO_SERVER, UpdatePerms::encode, UpdatePerms::decode, UpdatePerms::handle);
        SkyNetwork.register(CraftBlueprint.class, NetworkDirection.PLAY_TO_SERVER, CraftBlueprint::encode, CraftBlueprint::decode, CraftBlueprint::handle);
    }

    public record UpdatePerms(BlockPos pos, int doorPerm, int containerPerm, int buildPerm) {
        static void encode(UpdatePerms m, FriendlyByteBuf buf) {
            buf.writeBlockPos(m.pos);
            buf.writeVarInt(m.doorPerm);
            buf.writeVarInt(m.containerPerm);
            buf.writeVarInt(m.buildPerm);
        }

        static UpdatePerms decode(FriendlyByteBuf buf) {
            return new UpdatePerms(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
        }

        static void handle(UpdatePerms m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && player.containerMenu instanceof HomesteadMenu menu && menu.stillValid(player)) {
                HomesteadData data = HomesteadData.get(player.server);
                HomesteadClaim claim = data.getClaim(player.getUUID());
                if (claim != null && claim.getCenter().equals(m.pos)) {
                    claim.setDoorPerm(m.doorPerm);
                    claim.setContainerPerm(m.containerPerm);
                    claim.setBuildPerm(m.buildPerm);
                    data.setDirty();
                    menu.doorPerm = m.doorPerm;
                    menu.containerPerm = m.containerPerm;
                    menu.buildPerm = m.buildPerm;
                    Notifier.message(player, Component.translatable("message.skycraft.homestead.perms_updated"));
                }
            }
            ctx.get().setPacketHandled(true);
        }
    }

    public record CraftBlueprint(int index) {
        static void encode(CraftBlueprint m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.index);
        }

        static CraftBlueprint decode(FriendlyByteBuf buf) {
            return new CraftBlueprint(buf.readVarInt());
        }

        static void handle(CraftBlueprint m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && player.containerMenu instanceof HomesteadMenu menu && menu.stillValid(player)) {
                executeCraft(player, m.index);
            }
            ctx.get().setPacketHandled(true);
        }
    }

    private static void executeCraft(ServerPlayer player, int index) {
        // Blueprint index:
        // 0: Nails & Fittings (1 Iron Ingot -> 10 Iron Nuggets)
        // 1: Sawn Timber (1 Oak Log -> 4 Oak Planks)
        // 2: Hewn Stone (4 Cobblestone -> 4 Stone Bricks)
        // 3: Wood Door (6 Planks -> 1 Wooden Door)
        // 4: Nordic Chest (8 Planks -> 1 Chest)
        // 5: Hearth Fireplace (6 Cobblestone + 1 Coal -> 1 Campfire)
        // 6: Glass Panes (4 Glass -> 16 Glass Panes)

        ItemStack required;
        ItemStack output;
        int reqCount;

        switch (index) {
            case 0 -> {
                required = new ItemStack(Items.IRON_INGOT);
                reqCount = 1;
                output = new ItemStack(Items.IRON_NUGGET, 10);
            }
            case 1 -> {
                required = new ItemStack(Items.OAK_LOG);
                reqCount = 1;
                output = new ItemStack(Items.OAK_PLANKS, 4);
            }
            case 2 -> {
                required = new ItemStack(Items.COBBLESTONE);
                reqCount = 4;
                output = new ItemStack(Items.STONE_BRICKS, 4);
            }
            case 3 -> {
                required = new ItemStack(Items.OAK_PLANKS);
                reqCount = 6;
                output = new ItemStack(Items.OAK_DOOR, 1);
            }
            case 4 -> {
                required = new ItemStack(Items.OAK_PLANKS);
                reqCount = 8;
                output = new ItemStack(Items.CHEST, 1);
            }
            case 5 -> {
                required = new ItemStack(Items.COBBLESTONE);
                reqCount = 6;
                output = new ItemStack(Items.CAMPFIRE, 1);
            }
            case 6 -> {
                required = new ItemStack(Items.GLASS);
                reqCount = 4;
                output = new ItemStack(Items.GLASS_PANE, 16);
            }
            default -> {
                return;
            }
        }

        // Check if player has required items
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.is(required.getItem())) {
                count += stack.getCount();
            }
        }

        if (count >= reqCount) {
            // Deduct
            int toRemove = reqCount;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (!stack.isEmpty() && stack.is(required.getItem())) {
                    int rem = Math.min(stack.getCount(), toRemove);
                    stack.shrink(rem);
                    toRemove -= rem;
                    if (toRemove <= 0) break;
                }
            }
            // Give
            if (!ActionHandler.addToBags(player, output)) {
                player.drop(output, false);
            }
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.WOOD_PLACE, SoundSource.PLAYERS, 0.8f, 1.2f);
            Notifier.message(player, Component.translatable("message.skycraft.homestead.crafted", output.getHoverName()));
        } else {
            Notifier.message(player, Component.translatable("message.skycraft.homestead.missing_mats", required.getHoverName()));
        }
    }
}
