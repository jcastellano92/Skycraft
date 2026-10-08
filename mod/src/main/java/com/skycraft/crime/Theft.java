package com.skycraft.crime;

import com.skycraft.Skycraft;
import com.skycraft.core.Currency;
import com.skycraft.dig.PlacedBlocks;
import com.skycraft.economy.ItemValues;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Stealing from containers that belong to someone: containers inside a village (a villager within 32 blocks or a
 * village meeting point within 48) that weren't placed by a player and aren't dungeon loot chests.
 *
 * <p>The container's contents and the player's inventory are snapshotted when the menu opens and compared when it
 * closes: items that moved from the container to the player are stolen ({@code skycraft_stolen}), and if anyone saw
 * it while the container was open, the hold adds a bounty of half the stolen value.</p>
 */
@Mod.EventBusSubscriber(modid = Skycraft.MODID)
public final class Theft {
    private static final Map<UUID, Click> LAST_CLICK = new HashMap<>();
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    /** Loot chests with these loot table prefixes still belong to the villagers (village houses). */
    private static final String VILLAGE_LOOT = "chests/village";

    private record Click(BlockPos pos, long tick, boolean villageLoot, boolean dungeonLoot) {}

    private static final class Session {
        final int containerId;
        final BlockPos pos;
        final List<Entry> container;
        final List<Entry> inventory;
        boolean seen;

        Session(int containerId, BlockPos pos, List<Entry> container, List<Entry> inventory) {
            this.containerId = containerId;
            this.pos = pos;
            this.container = container;
            this.inventory = inventory;
        }
    }

    /** One kind of item (item + NBT) and how many of it. */
    private static final class Entry {
        final ItemStack proto;
        int count;

        Entry(ItemStack proto, int count) {
            this.proto = proto;
            this.count = count;
        }
    }

    private Theft() {}

    // ------------------------------------------------------------------ ownership

    /** Whether the container at {@code pos} belongs to someone (stealing from it is a crime). */
    public static boolean isOwned(ServerLevel level, BlockPos pos) {
        if (Jail.isJailDimension(level) || PlacedBlocks.isPlayerPlaced(level, pos)) return false;
        BlockEntity be = level.getBlockEntity(pos);
        String loot = lootTable(be);
        if (loot != null && !loot.contains(VILLAGE_LOOT)) return false; // dungeon/structure loot: finders keepers
        return inVillage(level, pos);
    }

    public static boolean inVillage(ServerLevel level, BlockPos pos) {
        if (!level.getEntitiesOfClass(Villager.class, new AABB(pos).inflate(32), Villager::isAlive).isEmpty()) return true;
        return level.getPoiManager().findClosest((Holder<PoiType> h) -> h.is(PoiTypes.MEETING), pos, 48, PoiManager.Occupancy.ANY).isPresent();
    }

    /** The loot table a container still has to generate, or null. */
    static String lootTable(BlockEntity be) {
        if (!(be instanceof RandomizableContainerBlockEntity)) return null;
        CompoundTag tag = be.saveWithoutMetadata();
        return tag.contains("LootTable", Tag.TAG_STRING) ? tag.getString("LootTable") : null;
    }

    // ------------------------------------------------------------------ tracking which block a menu belongs to

    /** Remembers the block the player just used, so the menu opened in the same tick can be tied to it. */
    static void noteClick(ServerPlayer player, BlockPos pos) {
        BlockEntity be = player.level().getBlockEntity(pos);
        String loot = lootTable(be);
        LAST_CLICK.put(player.getUUID(), new Click(pos.immutable(), player.level().getGameTime(),
                loot != null && loot.contains(VILLAGE_LOOT), loot != null && !loot.contains(VILLAGE_LOOT)));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)) return;
        noteClick(player, event.getPos());
    }

    @SubscribeEvent
    public static void onOpen(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        SESSIONS.remove(player.getUUID());
        Click click = LAST_CLICK.remove(player.getUUID());
        if (click == null || player.level().getGameTime() - click.tick() > 2 || !Crimes.canCommitCrime(player)) return;
        AbstractContainerMenu menu = event.getContainer();
        if (menu == player.inventoryMenu) return;
        ServerLevel level = player.serverLevel();
        if (click.dungeonLoot()) return; // dungeon loot: finders keepers
        if (click.villageLoot()) {
            if (PlacedBlocks.isPlayerPlaced(level, click.pos()) || !inVillage(level, click.pos())) return;
        } else {
            BlockEntity be = level.getBlockEntity(click.pos());
            if (!(be instanceof Container) || !isOwned(level, click.pos())) return;
        }

        Session s = new Session(menu.containerId, click.pos(), countMenu(menu, player), countInventory(player));
        SESSIONS.put(player.getUUID(), s);
    }

    /** Every second while an owned container is open: did someone see the player take something? */
    static void tick(ServerPlayer player) {
        Session s = SESSIONS.get(player.getUUID());
        if (s == null || s.seen) return;
        if (player.containerMenu.containerId != s.containerId) {
            SESSIONS.remove(player.getUUID());
            return;
        }
        List<Entry> now = countMenu(player.containerMenu, player);
        boolean taken = false;
        for (Entry before : s.container) {
            if (before.count > countOf(now, before.proto)) {
                taken = true;
                break;
            }
        }
        if (taken && Crimes.witnessed(player, null)) s.seen = true;
    }

    @SubscribeEvent
    public static void onClose(PlayerContainerEvent.Close event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Session s = SESSIONS.remove(player.getUUID());
        if (s == null || event.getContainer().containerId != s.containerId) return;

        List<Entry> containerAfter = countMenu(event.getContainer(), player);
        List<Entry> inventoryAfter = countInventory(player);
        int stolenCount = 0;
        long stolenValue = 0;
        for (Entry before : s.container) {
            int removed = before.count - countOf(containerAfter, before.proto);
            if (removed <= 0) continue;
            int gained = countOf(inventoryAfter, before.proto) - countOf(s.inventory, before.proto);
            int stolen = Math.min(removed, gained);
            if (stolen <= 0) continue;
            int tagged = tagStolen(player, before.proto, stolen);
            stolenCount += tagged;
            stolenValue += unitValue(before.proto) * (long) tagged;
        }
        if (stolenCount <= 0) return;

        Bounty.increment(player, "items_stolen", stolenCount);
        if (s.seen || Crimes.witnessed(player, null)) {
            int bounty = (int) Math.max(Bounty.MIN_THEFT, Math.min(100000, stolenValue / 2));
            Crimes.report(player, s.pos, bounty, true, null);
        }
    }

    static long unitValue(ItemStack stack) {
        ItemStack one = stack.copy();
        one.setCount(1);
        long coins = Currency.valueOf(one);
        return coins > 0 ? coins : ItemValues.get(one);
    }

    /** Tags up to {@code amount} matching (not yet stolen) items in the player's inventory as stolen. */
    private static int tagStolen(ServerPlayer player, ItemStack proto, int amount) {
        // items that were already stolen (taken from an owned container again) still count
        if (Bounty.isStolen(proto)) return amount;
        Inventory inv = player.getInventory();
        int left = amount;
        List<ItemStack> split = new ArrayList<>();
        for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty() || Bounty.isStolen(stack) || !ItemStack.isSameItemSameTags(stack, proto)) continue;
            if (stack.getCount() <= left) {
                left -= stack.getCount();
                Bounty.markStolen(stack);
            } else {
                ItemStack part = stack.split(left);
                Bounty.markStolen(part);
                split.add(part);
                left = 0;
            }
        }
        for (ItemStack part : split) inv.placeItemBackInInventory(part);
        return amount - left;
    }

    // ------------------------------------------------------------------ counting

    private static List<Entry> countMenu(AbstractContainerMenu menu, Player player) {
        List<Entry> out = new ArrayList<>();
        for (Slot slot : menu.slots) {
            if (slot.container == player.getInventory()) continue;
            add(out, slot.getItem());
        }
        return out;
    }

    private static List<Entry> countInventory(Player player) {
        List<Entry> out = new ArrayList<>();
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) add(out, inv.getItem(i));
        return out;
    }

    private static void add(List<Entry> list, ItemStack stack) {
        if (stack.isEmpty()) return;
        for (Entry e : list) {
            if (ItemStack.isSameItemSameTags(e.proto, stack)) {
                e.count += stack.getCount();
                return;
            }
        }
        ItemStack proto = stack.copy();
        proto.setCount(1);
        list.add(new Entry(proto, stack.getCount()));
    }

    private static int countOf(List<Entry> list, ItemStack proto) {
        for (Entry e : list) {
            if (ItemStack.isSameItemSameTags(e.proto, proto)) return e.count;
        }
        return 0;
    }

    static void forget(UUID player) {
        LAST_CLICK.remove(player);
        SESSIONS.remove(player);
    }
}
