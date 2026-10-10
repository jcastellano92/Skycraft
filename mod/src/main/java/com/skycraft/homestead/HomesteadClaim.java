package com.skycraft.homestead;

import com.skycraft.quest.party.Parties;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * Represents a claimed homestead plot founded by placing a Drafting Table.
 *
 * <p>Boundaries:
 * <ul>
 *     <li>Horizontal radius: 28 blocks around center</li>
 *     <li>Vertical floor: 10 blocks below drafting table (depth limit)</li>
 *     <li>Vertical ceiling: 24 blocks above drafting table</li>
 * </ul>
 */
public class HomesteadClaim {
    public static final int DEFAULT_RADIUS = 28;
    public static final int DEFAULT_MIN_DEPTH = 10;
    public static final int DEFAULT_MAX_HEIGHT = 24;

    public static final int PERM_OWNER = 0;
    public static final int PERM_PARTY = 1;
    public static final int PERM_PUBLIC = 2;

    private final UUID ownerId;
    private String ownerName;
    private final BlockPos center;
    private final String dimension;
    private final int radius;
    private final int minDepth;
    private final int maxHeight;

    private int doorPerm;
    private int containerPerm;
    private int buildPerm;

    public HomesteadClaim(UUID ownerId, String ownerName, BlockPos center, String dimension) {
        this(ownerId, ownerName, center, dimension, DEFAULT_RADIUS, DEFAULT_MIN_DEPTH, DEFAULT_MAX_HEIGHT,
                PERM_PARTY, PERM_PARTY, PERM_PARTY);
    }

    public HomesteadClaim(UUID ownerId, String ownerName, BlockPos center, String dimension,
                          int radius, int minDepth, int maxHeight,
                          int doorPerm, int containerPerm, int buildPerm) {
        this.ownerId = ownerId;
        this.ownerName = ownerName;
        this.center = center;
        this.dimension = dimension;
        this.radius = radius;
        this.minDepth = minDepth;
        this.maxHeight = maxHeight;
        this.doorPerm = doorPerm;
        this.containerPerm = containerPerm;
        this.buildPerm = buildPerm;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String name) {
        this.ownerName = name;
    }

    public BlockPos getCenter() {
        return center;
    }

    public String getDimension() {
        return dimension;
    }

    public int getRadius() {
        return radius;
    }

    public int getMinDepth() {
        return minDepth;
    }

    public int getMaxHeight() {
        return maxHeight;
    }

    public int getDoorPerm() {
        return doorPerm;
    }

    public void setDoorPerm(int doorPerm) {
        this.doorPerm = doorPerm;
    }

    public int getContainerPerm() {
        return containerPerm;
    }

    public void setContainerPerm(int containerPerm) {
        this.containerPerm = containerPerm;
    }

    public int getBuildPerm() {
        return buildPerm;
    }

    public void setBuildPerm(int buildPerm) {
        this.buildPerm = buildPerm;
    }

    public boolean isInside(Level level, BlockPos pos) {
        if (!level.dimension().location().toString().equals(dimension)) return false;
        int dx = pos.getX() - center.getX();
        int dz = pos.getZ() - center.getZ();
        if (dx * dx + dz * dz > radius * radius) return false;
        return pos.getY() >= center.getY() - minDepth && pos.getY() <= center.getY() + maxHeight;
    }

    public boolean isHorizontallyInside(Level level, BlockPos pos) {
        if (!level.dimension().location().toString().equals(dimension)) return false;
        int dx = pos.getX() - center.getX();
        int dz = pos.getZ() - center.getZ();
        return dx * dx + dz * dz <= radius * radius;
    }

    public boolean isOwner(ServerPlayer player) {
        return player.getUUID().equals(ownerId) || player.isCreative();
    }

    public boolean isPartyMember(ServerPlayer player) {
        if (isOwner(player)) return true;
        var party = Parties.partyOf(player);
        return party != null && party.has(ownerId);
    }

    public boolean canBuild(ServerPlayer player) {
        if (isOwner(player)) return true;
        if (buildPerm == PERM_PARTY && isPartyMember(player)) return true;
        return false;
    }

    public boolean canAccessDoor(ServerPlayer player) {
        if (doorPerm == PERM_PUBLIC) return true;
        if (isOwner(player)) return true;
        if (doorPerm == PERM_PARTY && isPartyMember(player)) return true;
        return false;
    }

    public boolean canAccessContainer(ServerPlayer player) {
        if (isOwner(player)) return true;
        if (containerPerm == PERM_PARTY && isPartyMember(player)) return true;
        return false;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("owner", ownerId);
        tag.putString("name", ownerName);
        tag.put("center", NbtUtils.writeBlockPos(center));
        tag.putString("dim", dimension);
        tag.putInt("radius", radius);
        tag.putInt("minDepth", minDepth);
        tag.putInt("maxHeight", maxHeight);
        tag.putInt("doorPerm", doorPerm);
        tag.putInt("contPerm", containerPerm);
        tag.putInt("buildPerm", buildPerm);
        return tag;
    }

    public static HomesteadClaim load(CompoundTag tag) {
        UUID owner = tag.getUUID("owner");
        String name = tag.getString("name");
        BlockPos center = NbtUtils.readBlockPos(tag.getCompound("center"));
        String dim = tag.getString("dim");
        int rad = tag.contains("radius") ? tag.getInt("radius") : DEFAULT_RADIUS;
        int depth = tag.contains("minDepth") ? tag.getInt("minDepth") : DEFAULT_MIN_DEPTH;
        int height = tag.contains("maxHeight") ? tag.getInt("maxHeight") : DEFAULT_MAX_HEIGHT;
        int door = tag.getInt("doorPerm");
        int cont = tag.getInt("contPerm");
        int build = tag.getInt("buildPerm");
        return new HomesteadClaim(owner, name, center, dim, rad, depth, height, door, cont, build);
    }
}
