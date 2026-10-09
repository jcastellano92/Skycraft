package com.skycraft.crime;

import com.skycraft.Skycraft;
import com.skycraft.core.Notifier;
import com.skycraft.core.PlayerData;
import com.skycraft.core.SkyData;
import com.skycraft.core.Skill;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The jail dimension {@code skycraft:jail}: each prisoner gets their own stone cell (no exits) and serves
 * 30 seconds per 100 gold of bounty (30 s .. 10 min). Sleeping in the cell bed serves the rest at once. On release
 * the hold's bounty is cleared, stolen items stay confiscated and some skill progress is lost.
 *
 * <p>State while jailed: {@code crime.jail = {hold, bounty, remaining (seconds), rdim, rx, ry, rz, ryaw, rpitch}}.</p>
 */
@Mod.EventBusSubscriber(modid = Skycraft.MODID)
public final class Jail {
    public static final ResourceKey<Level> JAIL = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(Skycraft.MODID, "jail"));
    public static final int SECONDS_PER_100 = 30;
    public static final int MIN_SECONDS = 30;
    public static final int MAX_SECONDS = 600;
    public static final int CELL_SPACING = 32;
    public static final int CELL_Y = 64;

    private Jail() {}

    public static boolean isJailDimension(Level level) {
        return level.dimension() == JAIL;
    }

    public static boolean isJailed(Player player) {
        return Bounty.state(player).contains("jail", Tag.TAG_COMPOUND);
    }

    /** Seconds of sentence left (0 when not jailed). Works on both sides. */
    public static int remaining(Player player) {
        return isJailed(player) ? Bounty.state(player).getCompound("jail").getInt("remaining") : 0;
    }

    public static int sentenceSeconds(int bounty) {
        return Math.max(MIN_SECONDS, Math.min(MAX_SECONDS, bounty * SECONDS_PER_100 / 100));
    }

    // ------------------------------------------------------------------ going to jail

    /** "I'll come quietly": serve the sentence for {@code hold}'s bounty. */
    public static void send(ServerPlayer player, String hold) {
        if (isJailed(player)) return;
        MinecraftServer server = player.getServer();
        ServerLevel jail = server == null ? null : server.getLevel(JAIL);
        if (jail == null) {
            Skycraft.LOGGER.error("Jail dimension {} is missing; paying the bounty instead is impossible", JAIL.location());
            return;
        }
        int bounty = Bounty.get(player, hold);
        PlayerData data = SkyData.get(player);
        CompoundTag j = new CompoundTag();
        j.putString("hold", hold);
        j.putInt("bounty", bounty);
        j.putInt("remaining", sentenceSeconds(bounty));
        j.putInt("served", 0);
        j.putString("rdim", player.level().dimension().location().toString());
        j.putDouble("rx", player.getX());
        j.putDouble("ry", player.getY());
        j.putDouble("rz", player.getZ());
        j.putFloat("ryaw", player.getYRot());
        j.putFloat("rpitch", player.getXRot());
        Bounty.state(player).put("jail", j);
        Bounty.state(player).remove("hostile_until");
        data.markDirty();

        Guards.pacify(player);
        player.closeContainer();
        player.stopRiding();
        Bounty.confiscate(player);
        Notifier.title(player, Component.translatable("crime.skycraft.jail.title"),
                Component.translatable("crime.skycraft.jail.sentence", formatTime(sentenceSeconds(bounty))));
        teleportToCell(player, jail);

        // Move possessions to Evidence Chest, keeping up to 2 lockpicks if skilled
        int slot = JailData.get(player.server).slotFor(player.getUUID());
        BlockPos origin = cellOrigin(slot);
        BlockPos chestPos = origin.offset(2, 1, 1);
        if (jail.getBlockEntity(chestPos) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest) {
            chest.clearContent();
            var inv = player.getInventory();
            int lockpickCount = 0;
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack s = inv.getItem(i);
                if (s.is(CrimeItems.LOCKPICK.get())) lockpickCount += s.getCount();
            }
            int keep = (data.getSkill(Skill.LOCKPICKING) >= 20 || data.getSkill(Skill.SNEAK) >= 20 || data.hasPerk("lockpicking.novice"))
                    ? Math.min(2, lockpickCount) : (lockpickCount > 0 ? 1 : 0);
            int kept = 0;
            int chestSlot = 0;
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack s = inv.getItem(i);
                if (s.isEmpty()) continue;
                if (s.is(CrimeItems.LOCKPICK.get()) && kept < keep) {
                    int take = Math.min(s.getCount(), keep - kept);
                    kept += take;
                    if (s.getCount() > take) {
                        ItemStack remainder = s.split(s.getCount() - take);
                        if (chestSlot < chest.getContainerSize()) chest.setItem(chestSlot++, remainder);
                    }
                    continue;
                }
                if (chestSlot < chest.getContainerSize()) {
                    chest.setItem(chestSlot++, s.copy());
                }
                inv.setItem(i, ItemStack.EMPTY);
            }
            chest.setChanged();
            if (kept > 0) {
                Notifier.message(player, Component.literal("§aYou managed to keep " + kept + " lockpick" + (kept > 1 ? "s" : "") + " concealed in your boot."));
            }
        }
    }

    private static void teleportToCell(ServerPlayer player, ServerLevel jail) {
        int slot = JailData.get(player.server).slotFor(player.getUUID());
        BlockPos origin = cellOrigin(slot);
        JailData data = JailData.get(player.server);
        if (!data.built.contains(slot) || jail.getBlockState(origin.offset(7, 1, 4)).isAir()) {
            buildCell(jail, origin);
            data.built.add(slot);
            data.setDirty();
        }
        player.teleportTo(jail, origin.getX() + 7.5, CELL_Y + 1, origin.getZ() + 4.5, 90f, 0f);
    }

    static BlockPos cellOrigin(int slot) {
        return new BlockPos(slot * CELL_SPACING, CELL_Y, 0);
    }

    /** A hold jail with central corridor, evidence chest, guard patrol, and multiple cells with NPCs. */
    private static void buildCell(ServerLevel level, BlockPos o) {
        BlockState wall = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = 0; x <= 11; x++) {
            for (int y = 0; y <= 4; y++) {
                for (int z = 0; z <= 25; z++) {
                    boolean shell = x == 0 || x == 11 || y == 0 || y == 4 || z == 0 || z == 25;
                    BlockState s = shell ? (y == 0 && (x + z) % 3 == 0 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState()
                            : y > 0 && y < 4 && (x * 7 + z * 3 + y) % 5 == 0 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : wall) : air;
                    level.setBlock(o.offset(x, y, z), s, 2);
                }
            }
        }

        // Inner partition separating corridor (x: 1..4) from cells (x: 6..10)
        for (int z = 1; z <= 24; z++) {
            for (int y = 1; y <= 3; y++) {
                level.setBlock(o.offset(5, y, z), wall, 2);
            }
        }
        // Dividers between cells
        for (int x = 6; x <= 10; x++) {
            for (int y = 1; y <= 3; y++) {
                level.setBlock(o.offset(x, y, 8), wall, 2);
                level.setBlock(o.offset(x, y, 16), wall, 2);
            }
        }

        // Corridor lanterns
        level.setBlock(o.offset(2, 3, 4), Blocks.LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true), 2);
        level.setBlock(o.offset(2, 3, 12), Blocks.LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true), 2);
        level.setBlock(o.offset(2, 3, 20), Blocks.LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true), 2);

        // North end: Evidence Chest
        BlockPos chestPos = o.offset(2, 1, 1);
        level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3);
        Locks.setLock(level, chestPos, Locks.MASTER);

        // South end: Old sewer grate / Escape hatch
        BlockPos escapePos = o.offset(2, 1, 23);
        level.setBlock(escapePos, Blocks.IRON_TRAPDOOR.defaultBlockState(), 3);

        // Setup Cell 1 (Player's cell: z: 2..7)
        setupCell(level, o, 4, 3, true);
        // Setup Cell 2 (NPC Outlaw: z: 9..15)
        setupCell(level, o, 12, 11, false);
        // Setup Cell 3 (NPC Thief: z: 17..23)
        setupCell(level, o, 20, 19, false);

        // Spawn prisoners in Cells 2 and 3
        try {
            var prisoner1 = net.minecraft.world.entity.EntityType.VILLAGER.create(level);
            if (prisoner1 != null) {
                prisoner1.setCustomName(Component.literal("Imprisoned Outlaw"));
                prisoner1.setCustomNameVisible(true);
                prisoner1.setPos(o.getX() + 7.5, CELL_Y + 1, o.getZ() + 12.5);
                level.addFreshEntity(prisoner1);
            }
            var prisoner2 = net.minecraft.world.entity.EntityType.VILLAGER.create(level);
            if (prisoner2 != null) {
                prisoner2.setCustomName(Component.literal("Captured Thief"));
                prisoner2.setCustomNameVisible(true);
                prisoner2.setPos(o.getX() + 7.5, CELL_Y + 1, o.getZ() + 20.5);
                level.addFreshEntity(prisoner2);
            }
            // Spawn patrolling Guard in corridor
            var guard = com.skycraft.creatures.ModEntities.GUARD.get().create(level);
            if (guard != null) {
                guard.setCustomName(Component.literal("Jail Guard"));
                guard.setCustomNameVisible(true);
                guard.setPos(o.getX() + 2.5, CELL_Y + 1, o.getZ() + 12.5);
                guard.restrictTo(o.offset(2, 1, 12), 10);
                level.addFreshEntity(guard);
            }
        } catch (Exception ignored) {}
    }

    private static void setupCell(ServerLevel level, BlockPos o, int doorZ, int bedZ, boolean playerCell) {
        // Iron Door facing corridor
        BlockPos doorBottom = o.offset(5, 1, doorZ);
        BlockPos doorTop = o.offset(5, 2, doorZ);
        level.setBlock(doorBottom, Blocks.IRON_DOOR.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER).setValue(net.minecraft.world.level.block.DoorBlock.FACING, Direction.WEST), 2);
        level.setBlock(doorTop, Blocks.IRON_DOOR.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER).setValue(net.minecraft.world.level.block.DoorBlock.FACING, Direction.WEST), 2);
        Locks.setLock(level, doorBottom, Locks.MASTER);

        // Iron bars beside door
        BlockState bars = Blocks.IRON_BARS.defaultBlockState().setValue(IronBarsBlock.NORTH, true).setValue(IronBarsBlock.SOUTH, true);
        for (int dz = -2; dz <= 2; dz++) {
            if (dz == 0) continue;
            level.setBlock(o.offset(5, 1, doorZ + dz), bars, 2);
            level.setBlock(o.offset(5, 2, doorZ + dz), bars, 2);
        }

        // Bed inside cell
        BlockPos head = o.offset(9, 1, bedZ);
        BlockPos foot = o.offset(9, 1, bedZ + 1);
        level.setBlock(head, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.HEAD), 2);
        level.setBlock(foot, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT), 2);

        // Cell furnishings
        level.setBlock(o.offset(9, 1, doorZ + 2), Blocks.CAULDRON.defaultBlockState(), 2);
        level.setBlock(o.offset(7, 3, doorZ), Blocks.LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true), 2);
    }

    // ------------------------------------------------------------------ serving the sentence

    /** Every second. */
    static void tick(ServerPlayer player) {
        if (!isJailed(player)) return;
        CompoundTag j = Bounty.state(player).getCompound("jail");
        if (!isJailDimension(player.level())) {
            if (!player.isAlive()) return;
            if (player.isCreative() || player.isSpectator()) {
                String hold = j.getString("hold");
                Bounty.state(player).remove("jail");
                SkyData.get(player).markDirty();
                Bounty.add(player, hold, Bounty.ESCAPE);
                return;
            }
            ServerLevel jail = player.server.getLevel(JAIL);
            if (jail != null) teleportToCell(player, jail);
            return;
        }
        j.putInt("served", j.getInt("served") + 1);
        int left = j.getInt("remaining") - 1;
        if (left <= 0) {
            release(player, false);
        } else {
            j.putInt("remaining", left);
            SkyData.get(player).markDirty();
        }
    }

    static void release(ServerPlayer player, boolean slept) {
        CompoundTag state = Bounty.state(player);
        if (!state.contains("jail", Tag.TAG_COMPOUND)) return;
        CompoundTag j = state.getCompound("jail");
        state.remove("jail");
        String hold = j.getString("hold");
        int bounty = Math.max(j.getInt("bounty"), 1);
        Bounty.clear(player, hold);

        // Restore non-stolen possessions from Evidence Chest
        int slot = JailData.get(player.server).slotFor(player.getUUID());
        BlockPos origin = cellOrigin(slot);
        BlockPos chestPos = origin.offset(2, 1, 1);
        ServerLevel jail = player.server.getLevel(JAIL);
        if (jail != null && jail.getBlockEntity(chestPos) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest) {
            for (int i = 0; i < chest.getContainerSize(); i++) {
                ItemStack s = chest.getItem(i);
                if (!s.isEmpty()) {
                    if (!Bounty.isStolen(s)) {
                        if (!player.getInventory().add(s)) player.drop(s, false);
                    }
                    chest.setItem(i, ItemStack.EMPTY);
                }
            }
            chest.setChanged();
        }

        List<Skill> lost = deteriorate(player, Math.max(1, bounty / 100));
        MutableComponent skills = Component.empty();
        for (int i = 0; i < lost.size(); i++) {
            if (i > 0) skills.append(", ");
            skills.append(lost.get(i).displayName());
        }
        Notifier.title(player, Component.translatable(slept ? "crime.skycraft.jail.slept" : "crime.skycraft.jail.released"),
                Component.translatable("crime.skycraft.jail.deteriorate"));
        if (!lost.isEmpty()) Notifier.message(player, Component.translatable("crime.skycraft.jail.lost", skills));

        ServerLevel target = null;
        ResourceLocation dimId = ResourceLocation.tryParse(j.getString("rdim"));
        if (dimId != null) target = player.server.getLevel(ResourceKey.create(Registries.DIMENSION, dimId));
        if (target == null || target.dimension() == JAIL) {
            target = player.server.overworld();
            BlockPos spawn = target.getSharedSpawnPos();
            player.teleportTo(target, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0f, 0f);
        } else {
            player.teleportTo(target, j.getDouble("rx"), j.getDouble("ry"), j.getDouble("rz"), j.getFloat("ryaw"), j.getFloat("rpitch"));
        }
        SkyData.get(player).markDirty();
    }

    public static void escape(ServerPlayer player) {
        if (!isJailed(player)) return;
        boolean detected = false;
        for (Mob m : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(16),
                e -> Crimes.isGuard(e) && e.isAlive())) {
            if (m.getTarget() == player || m.hasLineOfSight(player)) {
                detected = true;
                break;
            }
        }
        CompoundTag j = Bounty.state(player).getCompound("jail");
        String hold = j.getString("hold");
        Bounty.state(player).remove("jail");
        SkyData.get(player).markDirty();

        if (detected) {
            Bounty.add(player, hold, Bounty.ESCAPE);
            Notifier.title(player, Component.literal("§cEscaped from Jail!"), Component.literal("§cGuards sounded the alarm! Bounty added."));
        } else {
            Notifier.title(player, Component.literal("§aEscaped from Jail!"), Component.literal("§aYou slipped away into the night unnoticed."));
        }

        ServerLevel target = null;
        ResourceLocation dimId = ResourceLocation.tryParse(j.getString("rdim"));
        if (dimId != null) target = player.server.getLevel(ResourceKey.create(Registries.DIMENSION, dimId));
        if (target == null || target.dimension() == JAIL) target = player.server.overworld();

        player.teleportTo(target, j.getDouble("rx"), j.getDouble("ry"), j.getDouble("rz"), j.getFloat("ryaw"), j.getFloat("rpitch"));
    }

    /** Skyrim: time in jail erodes skill progress. Picks {@code times} skills weighted by level and resets their progress. */
    private static List<Skill> deteriorate(ServerPlayer player, int times) {
        PlayerData data = SkyData.get(player);
        List<Skill> lost = new ArrayList<>();
        for (int t = 0; t < times; t++) {
            int total = 0;
            for (Skill s : Skill.VALUES) {
                if (!lost.contains(s) && data.getSkillXp(s) > 0) total += Math.max(1, data.getSkill(s));
            }
            if (total <= 0) break;
            int roll = player.getRandom().nextInt(total);
            for (Skill s : Skill.VALUES) {
                if (lost.contains(s) || data.getSkillXp(s) <= 0) continue;
                roll -= Math.max(1, data.getSkill(s));
                if (roll < 0) {
                    data.setSkillXp(s, 0);
                    lost.add(s);
                    break;
                }
            }
        }
        return lost;
    }

    public static String formatTime(int seconds) {
        return seconds / 60 + ":" + String.format("%02d", seconds % 60);
    }

    // ------------------------------------------------------------------ events

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (!isJailDimension(level) || event.getEntity().isCreative()) return;
        BlockState state = level.getBlockState(event.getPos());
        BlockPos pos = event.getPos();

        if (event.getEntity() instanceof ServerPlayer player && isJailed(player) && event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND) {
            int slot = JailData.get(player.server).slotFor(player.getUUID());
            BlockPos origin = cellOrigin(slot);

            if (state.getBlock() instanceof BedBlock) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
                CompoundTag j = Bounty.state(player).getCompound("jail");
                int served = j.getInt("served");
                if (served < 60) {
                    int remaining = 60 - served;
                    Notifier.message(player, Component.literal("§cYou must serve at least 1 minute of your sentence before resting (" + remaining + "s remaining)."));
                } else {
                    release(player, true);
                }
                return;
            }

            if (pos.equals(origin.offset(2, 1, 23))) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
                escape(player);
                return;
            }
        }

        // Allow doors, trapdoors, and container block entities to be interacted with (for lockpicking and looting)
        if (state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock
                || state.getBlock() instanceof net.minecraft.world.level.block.TrapDoorBlock
                || level.getBlockEntity(pos) != null) {
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (isJail(event.getLevel()) && !event.getPlayer().isCreative()) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (isJail(event.getLevel()) && !(event.getEntity() instanceof Player p && p.isCreative())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (isJailDimension(event.getLevel()) && !event.getEntity().isCreative()) event.setCanceled(true);
    }

    private static boolean isJail(LevelAccessor level) {
        return level instanceof Level l && isJailDimension(l);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isJailed(player) && !isJailDimension(player.level())) {
            ServerLevel jail = player.server.getLevel(JAIL);
            if (jail != null && !player.isCreative()) teleportToCell(player, jail);
        }
    }

    // ------------------------------------------------------------------ saved data

    /** Which cell belongs to which player, and which cells are built. Stored with the overworld. */
    public static class JailData extends SavedData {
        private static final String NAME = "skycraft_jail";
        final Map<UUID, Integer> slots = new HashMap<>();
        final Set<Integer> built = new HashSet<>();
        int next = 0;

        static JailData get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(JailData::load, JailData::new, NAME);
        }

        int slotFor(UUID player) {
            Integer slot = slots.get(player);
            if (slot == null) {
                slot = next++;
                slots.put(player, slot);
                setDirty();
            }
            return slot;
        }

        static JailData load(CompoundTag tag) {
            JailData data = new JailData();
            data.next = tag.getInt("next");
            CompoundTag slots = tag.getCompound("slots");
            for (String k : slots.getAllKeys()) {
                try {
                    data.slots.put(UUID.fromString(k), slots.getInt(k));
                } catch (IllegalArgumentException ignored) {
                    // corrupt entry
                }
            }
            for (int b : tag.getIntArray("built")) data.built.add(b);
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            tag.putInt("next", next);
            CompoundTag s = new CompoundTag();
            slots.forEach((k, v) -> s.putInt(k.toString(), v));
            tag.put("slots", s);
            tag.put("built", new IntArrayTag(new ArrayList<>(built)));
            return tag;
        }
    }
}
