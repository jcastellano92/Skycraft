package com.skycraft.homestead;

import com.skycraft.crafting.menu.CraftingMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkHooks;

public class HomesteadMenu extends AbstractContainerMenu {
    public final BlockPos pos;
    public final String ownerName;
    public int doorPerm;
    public int containerPerm;
    public int buildPerm;
    public final int radius;
    public final int minDepth;
    public final int maxHeight;

    public HomesteadMenu(int id, Inventory inventory, BlockPos pos, String ownerName,
                         int doorPerm, int containerPerm, int buildPerm,
                         int radius, int minDepth, int maxHeight) {
        super(CraftingMenus.HOMESTEAD.get(), id);
        this.pos = pos;
        this.ownerName = ownerName;
        this.doorPerm = doorPerm;
        this.containerPerm = containerPerm;
        this.buildPerm = buildPerm;
        this.radius = radius;
        this.minDepth = minDepth;
        this.maxHeight = maxHeight;

        // Inventory slots
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 140 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inventory, col, 8 + col * 18, 198));
        }
    }

    public static HomesteadMenu fromNetwork(int id, Inventory inventory, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        String name = buf.readUtf();
        int door = buf.readInt();
        int cont = buf.readInt();
        int build = buf.readInt();
        int rad = buf.readInt();
        int depth = buf.readInt();
        int height = buf.readInt();
        return new HomesteadMenu(id, inventory, pos, name, door, cont, build, rad, depth, height);
    }

    public static void open(ServerPlayer player, HomesteadClaim claim, BlockPos pos) {
        NetworkHooks.openScreen(player,
                new SimpleMenuProvider((id, inv, p) -> new HomesteadMenu(id, inv, pos,
                        claim.getOwnerName(), claim.getDoorPerm(), claim.getContainerPerm(), claim.getBuildPerm(),
                        claim.getRadius(), claim.getMinDepth(), claim.getMaxHeight()),
                        Component.translatable("block.skycraft.drafting_table")),
                buf -> {
                    buf.writeBlockPos(pos);
                    buf.writeUtf(claim.getOwnerName());
                    buf.writeInt(claim.getDoorPerm());
                    buf.writeInt(claim.getContainerPerm());
                    buf.writeInt(claim.getBuildPerm());
                    buf.writeInt(claim.getRadius());
                    buf.writeInt(claim.getMinDepth());
                    buf.writeInt(claim.getMaxHeight());
                });
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack current = slot.getItem();
            itemstack = current.copy();
            if (index < 27) {
                if (!this.moveItemStackTo(current, 27, 36, false)) return ItemStack.EMPTY;
            } else if (!this.moveItemStackTo(current, 0, 27, false)) {
                return ItemStack.EMPTY;
            }
            if (current.isEmpty()) slot.set(ItemStack.EMPTY);
            else slot.setChanged();
        }
        return itemstack;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }
}
