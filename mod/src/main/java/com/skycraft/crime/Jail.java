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
import net.minecraft.world.entity.player.Player;
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
    }

    private static void teleportToCell(ServerPlayer player, ServerLevel jail) {
        int slot = JailData.get(player.server).slotFor(player.getUUID());
        BlockPos origin = cellOrigin(slot);
        JailData data = JailData.get(player.server);
        if (!data.built.contains(slot) || jail.getBlockState(origin.offset(3, 0, 3)).isAir()) {
            buildCell(jail, origin);
            data.built.add(slot);
            data.setDirty();
        }
        player.teleportTo(jail, origin.getX() + 4.5, CELL_Y + 1, origin.getZ() + 3.5, 90f, 0f);
    }

    static BlockPos cellOrigin(int slot) {
        return new BlockPos(slot * CELL_SPACING, CELL_Y, 0);
    }

    /** A 5x3x5 stone-brick cell with a barred window, a bed and a torch. Origin is the floor's corner. */
    private static void buildCell(ServerLevel level, BlockPos o) {
        BlockState wall = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = 0; x <= 6; x++) {
            for (int y = 0; y <= 4; y++) {
                for (int z = 0; z <= 6; z++) {
                    boolean shell = x == 0 || x == 6 || y == 0 || y == 4 || z == 0 || z == 6;
                    BlockState s = shell ? (y == 0 && (x + z) % 3 == 0 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState()
                            : y > 0 && y < 4 && (x * 7 + z * 3 + y) % 5 == 0 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : wall) : air;
                    level.setBlock(o.offset(x, y, z), s, 2);
                }
            }
        }
        // barred window on the west wall
        BlockState bars = Blocks.IRON_BARS.defaultBlockState().setValue(IronBarsBlock.NORTH, true).setValue(IronBarsBlock.SOUTH, true);
        for (int z = 2; z <= 4; z++) level.setBlock(o.offset(0, 2, z), bars, 2);
        // bed in the north-east corner, head against the north wall
        BlockPos foot = o.offset(5, 1, 2);
        BlockPos head = foot.north();
        level.setBlock(head, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.HEAD), 2);
        level.setBlock(foot, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH).setValue(BedBlock.PART, BedPart.FOOT), 2);
        level.setBlock(o.offset(1, 1, 5), Blocks.TORCH.defaultBlockState(), 2);
        level.setBlock(o.offset(1, 1, 1), Blocks.CAULDRON.defaultBlockState(), 2);
        level.setBlock(o.offset(3, 3, 3), Blocks.LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true), 2);
    }

    // ------------------------------------------------------------------ serving the sentence

    /** Every second. */
    static void tick(ServerPlayer player) {
        if (!isJailed(player)) return;
        CompoundTag j = Bounty.state(player).getCompound("jail");
        if (!isJailDimension(player.level())) {
            if (!player.isAlive()) return;
            if (player.isCreative() || player.isSpectator()) {
                // an operator walked out: that's a jailbreak
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
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
        if (event.getEntity() instanceof ServerPlayer player && level.getBlockState(event.getPos()).getBlock() instanceof BedBlock
                && isJailed(player) && event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND) {
            release(player, true);
        }
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
