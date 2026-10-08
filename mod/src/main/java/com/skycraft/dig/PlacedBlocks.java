package com.skycraft.dig;

import com.skycraft.Skycraft;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Remembers which blocks in a chunk were placed by players, so builders can always remove their own blocks even
 * while terrain digging is still locked behind Mining perks.
 */
@Mod.EventBusSubscriber(modid = Skycraft.MODID)
public final class PlacedBlocks {
    public static final Capability<Store> CAP = CapabilityManager.get(new CapabilityToken<>() {});

    private PlacedBlocks() {}

    public static boolean isPlayerPlaced(Level level, BlockPos pos) {
        LevelChunk chunk = level.getChunkAt(pos);
        return chunk.getCapability(CAP).map(s -> s.positions.contains(pos.asLong())).orElse(false);
    }

    public static void mark(Level level, BlockPos pos, boolean placed) {
        LevelChunk chunk = level.getChunkAt(pos);
        chunk.getCapability(CAP).ifPresent(s -> {
            boolean changed = placed ? s.positions.add(pos.asLong()) : s.positions.remove(pos.asLong());
            if (changed) chunk.setUnsaved(true);
        });
    }

    @SubscribeEvent
    public static void attach(AttachCapabilitiesEvent<LevelChunk> event) {
        event.addCapability(new ResourceLocation(Skycraft.MODID, "placed_blocks"), new Store());
    }

    public static class Store implements ICapabilitySerializable<Tag> {
        final LongOpenHashSet positions = new LongOpenHashSet();
        private final LazyOptional<Store> optional = LazyOptional.of(() -> this);

        @Override
        public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
            return CAP.orEmpty(cap, optional);
        }

        @Override
        public Tag serializeNBT() {
            return new LongArrayTag(positions.toLongArray());
        }

        @Override
        public void deserializeNBT(Tag nbt) {
            positions.clear();
            if (nbt instanceof LongArrayTag arr) {
                for (long l : arr.getAsLongArray()) positions.add(l);
            }
        }
    }
}
