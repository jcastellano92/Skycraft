package com.skycraft.society;

import com.skycraft.core.SkyData;
import com.skycraft.crime.Crimes;
import com.skycraft.society.entity.NpcEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

/**
 * Public NPC API (contract 22) and the shared NPC rules: creation, names, gear, stats and who is hostile to whom.
 */
public final class Npcs {
    private static final NpcRole[] RANDOM_CIVILIANS = {
            NpcRole.HUNTER, NpcRole.MINER, NpcRole.LUMBERJACK, NpcRole.BARD, NpcRole.PRIEST, NpcRole.BEGGAR, NpcRole.MAGE,
            NpcRole.ADVENTURER, NpcRole.COURIER, NpcRole.INNKEEPER, NpcRole.HOUSECARL
    };
    private static final NpcRole[] RANDOM_FIGHTERS = {
            NpcRole.THALMOR, NpcRole.IMPERIAL_SOLDIER, NpcRole.STORMCLOAK_SOLDIER, NpcRole.FORSWORN, NpcRole.VAMPIRE,
            NpcRole.NECROMANCER, NpcRole.ASSASSIN, NpcRole.THUG
    };

    private Npcs() {}

    // ------------------------------------------------------------------ public API

    /**
     * Spawns a persistent NPC with {@code role} (an {@link NpcRole} id such as {@code "innkeeper"} or {@code "thalmor"})
     * standing at {@code pos}. Returns the entity, or null if the role is unknown or the spawn failed.
     */
    @Nullable
    public static Mob spawn(ServerLevel level, BlockPos pos, String role) {
        NpcRole r = NpcRole.byId(role);
        if (r == null) return null;
        NpcEntity npc = create(level, pos, r);
        if (npc == null) return null;
        npc.setPersistenceRequired();
        return level.addFreshEntity(npc) ? npc : null;
    }

    /** Creates, positions and outfits an NPC without adding it to the level (callers tag it, then add it). */
    @Nullable
    public static NpcEntity create(ServerLevel level, BlockPos pos, NpcRole role) {
        NpcEntity npc = NpcEntities.typeFor(role).create(level);
        if (npc == null) return null;
        float yaw = level.getRandom().nextFloat() * 360f;
        npc.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0f);
        npc.setYHeadRot(yaw);
        npc.setRole(role);
        npc.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null, null);
        return npc;
    }

    public static NpcRole randomRole(RandomSource random, boolean fighter) {
        NpcRole[] pool = fighter ? RANDOM_FIGHTERS : RANDOM_CIVILIANS;
        return pool[random.nextInt(pool.length)];
    }

    // ------------------------------------------------------------------ outfit

    /** Name, gear and stats for a freshly initialized NPC. */
    public static void outfit(NpcEntity npc) {
        RandomSource r = npc.getRandom();
        NpcRole role = npc.role();
        if (!npc.hasCustomName()) npc.setCustomName(name(role, npc.isFemale(), r));
        equip(npc, role, r);
        stats(npc, role);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            npc.setDropChance(slot, slot.getType() == EquipmentSlot.Type.HAND ? 0.06f : 0.0f);
        }
    }

    public static Component name(NpcRole role, boolean female, RandomSource r) {
        if (role.named()) {
            String first = NpcNames.generate(NpcNames.raceFor(role, r), female, r);
            return Component.translatable("society.skycraft.npc_name." + role.id, first);
        }
        return Component.translatable("society.skycraft.title." + role.id);
    }

    private static void equip(NpcEntity npc, NpcRole role, RandomSource r) {
        switch (role) {
            case HUNTER -> {
                hand(npc, bow("skycraft:iron_bow"));
                armor(npc, EquipmentSlot.CHEST, dyed(Items.LEATHER_CHESTPLATE, 0x5A4630));
            }
            case MINER -> hand(npc, new ItemStack(Items.IRON_PICKAXE));
            case LUMBERJACK -> hand(npc, item("skycraft:iron_war_axe", Items.IRON_AXE));
            case ADVENTURER -> {
                int kit = r.nextInt(3);
                if (kit == 0) {
                    hand(npc, item("skycraft:steel_sword", Items.IRON_SWORD));
                    npc.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
                } else if (kit == 1) {
                    hand(npc, item("skycraft:steel_greatsword", Items.IRON_SWORD));
                } else {
                    hand(npc, bow("skycraft:steel_bow"));
                }
                armor(npc, EquipmentSlot.CHEST, item("skycraft:hide_chestplate", Items.CHAINMAIL_CHESTPLATE));
                if (r.nextBoolean()) armor(npc, EquipmentSlot.FEET, item("skycraft:hide_boots", Items.LEATHER_BOOTS));
            }
            case THALMOR -> hand(npc, item("skycraft:elven_sword", Items.GOLDEN_SWORD));
            case IMPERIAL_SOLDIER -> {
                hand(npc, item("skycraft:steel_sword", Items.IRON_SWORD));
                if (r.nextInt(3) > 0) npc.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
                armor(npc, EquipmentSlot.HEAD, item("skycraft:steel_helmet", Items.IRON_HELMET));
            }
            case STORMCLOAK_SOLDIER -> {
                hand(npc, r.nextBoolean() ? item("skycraft:iron_war_axe", Items.IRON_AXE) : item("skycraft:iron_sword", Items.IRON_SWORD));
                if (r.nextBoolean()) npc.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
            }
            case FORSWORN -> {
                if (r.nextInt(3) == 0) hand(npc, new ItemStack(Items.BOW));
                else hand(npc, r.nextBoolean() ? new ItemStack(Items.STONE_AXE) : item("skycraft:iron_war_axe", Items.IRON_AXE));
            }
            case VAMPIRE -> {
                if (r.nextBoolean()) hand(npc, item("skycraft:iron_dagger", Items.IRON_SWORD));
            }
            case ASSASSIN -> hand(npc, item("skycraft:steel_dagger", Items.IRON_SWORD));
            case THUG -> {
                hand(npc, r.nextBoolean() ? item("skycraft:iron_mace", Items.IRON_AXE) : item("skycraft:iron_sword", Items.IRON_SWORD));
                armor(npc, EquipmentSlot.CHEST, item("skycraft:hide_chestplate", Items.LEATHER_CHESTPLATE));
            }
            case HOUSECARL -> {
                hand(npc, item("skycraft:steel_sword", Items.IRON_SWORD));
                npc.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
                armor(npc, EquipmentSlot.CHEST, item("skycraft:steel_chestplate", Items.IRON_CHESTPLATE));
            }
            case JARL -> hand(npc, item("skycraft:steel_sword", Items.IRON_SWORD));
            default -> {
            }
        }
    }

    private static void stats(NpcEntity npc, NpcRole role) {
        double health;
        double damage;
        double armor;
        switch (role) {
            case HUNTER, MINER -> { health = 24; damage = 3; armor = 0; }
            case LUMBERJACK -> { health = 26; damage = 3; armor = 0; }
            case MAGE -> { health = 24; damage = 2; armor = 0; }
            case ADVENTURER -> { health = 30; damage = 4; armor = 2; }
            case THALMOR -> { health = 34; damage = 5; armor = 6; }
            case IMPERIAL_SOLDIER -> { health = 32; damage = 5; armor = 6; }
            case STORMCLOAK_SOLDIER -> { health = 32; damage = 5; armor = 4; }
            case FORSWORN -> { health = 26; damage = 4; armor = 2; }
            case VAMPIRE -> { health = 36; damage = 5; armor = 2; }
            case NECROMANCER -> { health = 26; damage = 2; armor = 0; }
            case ASSASSIN -> { health = 30; damage = 6; armor = 2; }
            case THUG -> { health = 30; damage = 4; armor = 2; }
            case JARL -> { health = 30; damage = 4; armor = 2; }
            case HOUSECARL -> { health = 40; damage = 5; armor = 4; }
            case THIEF -> { health = 18; damage = 2; armor = 0; }
            default -> { health = 20; damage = 2; armor = 0; }
        }
        base(npc, Attributes.MAX_HEALTH, health);
        base(npc, Attributes.ATTACK_DAMAGE, damage);
        base(npc, Attributes.ARMOR, armor);
        if (role == NpcRole.THIEF || role == NpcRole.COURIER) base(npc, Attributes.MOVEMENT_SPEED, 0.33);
        npc.setHealth(npc.getMaxHealth());
    }

    private static void base(LivingEntity e, net.minecraft.world.entity.ai.attributes.Attribute attribute, double value) {
        AttributeInstance inst = e.getAttribute(attribute);
        if (inst != null) inst.setBaseValue(value);
    }

    private static void hand(NpcEntity npc, ItemStack stack) {
        npc.setItemSlot(EquipmentSlot.MAINHAND, stack);
    }

    private static void armor(NpcEntity npc, EquipmentSlot slot, ItemStack stack) {
        npc.setItemSlot(slot, stack);
    }

    /** A registered item by id (e.g. a Skycraft smithing item), or the vanilla fallback. */
    public static ItemStack item(String id, Item fallback) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
        return new ItemStack(item == null || item == Items.AIR ? fallback : item);
    }

    private static ItemStack bow(String id) {
        ItemStack s = item(id, Items.BOW);
        return s.getItem() instanceof BowItem ? s : new ItemStack(Items.BOW);
    }

    private static ItemStack dyed(Item item, int color) {
        ItemStack s = new ItemStack(item);
        if (item instanceof DyeableLeatherItem dyeable) dyeable.setColor(s, color);
        return s;
    }

    // ------------------------------------------------------------------ hostility

    public static boolean isMember(Player player, String faction) {
        return SkyData.get(player).module("quest").getCompound("factions").contains(faction);
    }

    public static boolean isNight(Level level) {
        long time = level.getDayTime() % 24000L;
        return time >= 13000 && time <= 23000;
    }

    /** Whether NPCs of {@code role} attack this player on sight (reputation, civil war side, time of day). */
    public static boolean hostileToPlayer(NpcRole role, Player p) {
        return switch (role) {
            case THALMOR -> Reputation.hostile(p, Reputation.THALMOR) || isMember(p, Reputation.STORMCLOAKS);
            case IMPERIAL_SOLDIER -> Reputation.hostile(p, Reputation.IMPERIAL_LEGION) || isMember(p, Reputation.STORMCLOAKS);
            case STORMCLOAK_SOLDIER -> Reputation.hostile(p, Reputation.STORMCLOAKS) || isMember(p, Reputation.IMPERIAL_LEGION);
            case FORSWORN -> Reputation.get(p, Reputation.FORSWORN) < 40;
            case VAMPIRE -> isNight(p.level()) || Reputation.hostile(p, Reputation.VAMPIRES);
            case NECROMANCER -> true;
            case ASSASSIN -> !isMember(p, Reputation.DARK_BROTHERHOOD) && Reputation.hostile(p, Reputation.DARK_BROTHERHOOD);
            case HOUSECARL, ADVENTURER -> Reputation.get(p, Reputation.TOWNSFOLK) <= -90;
            default -> false;
        };
    }

    /** Guards near a villain who attacks a player come to help. */
    public static void alertGuards(NpcEntity villain, Player victim) {
        for (Mob guard : villain.level().getEntitiesOfClass(Mob.class, villain.getBoundingBox().inflate(24),
                m -> m.isAlive() && Crimes.isGuard(m) && m.getTarget() == null)) {
            guard.setTarget(villain);
        }
    }

    /** Friends of a hurt NPC join the fight; protectors defend civilians. */
    public static void rallyAllies(NpcEntity npc, LivingEntity attacker) {
        if (attacker instanceof Player p && (p.isCreative() || p.isSpectator())) return;
        for (NpcEntity o : npc.level().getEntitiesOfClass(NpcEntity.class, npc.getBoundingBox().inflate(16),
                x -> x != npc && x.isAlive() && x.canFight() && x.getTarget() == null && !x.isAlliedTo(attacker))) {
            boolean friend = o.isAlliedTo(npc) || (npc.role().civilian && o.role().protector);
            if (friend && o.hasLineOfSight(npc)) o.setTarget(attacker);
        }
    }

    /** A necromancer raises two skeletons that fight for a minute and a half. */
    public static void raiseDead(NpcEntity necro, LivingEntity target) {
        if (!(necro.level() instanceof ServerLevel level)) return;
        level.playSound(null, necro.blockPosition(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.HOSTILE, 1.0f, 0.7f);
        for (int i = 0; i < 2; i++) {
            int x = necro.getBlockX() + necro.getRandom().nextInt(5) - 2;
            int z = necro.getBlockZ() + necro.getRandom().nextInt(5) - 2;
            if (!level.isLoaded(new BlockPos(x, necro.getBlockY(), z))) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (Math.abs(y - necro.getBlockY()) > 4) y = necro.getBlockY();
            Skeleton skeleton = EntityType.SKELETON.create(level);
            if (skeleton == null) continue;
            BlockPos pos = new BlockPos(x, y, z);
            skeleton.moveTo(x + 0.5, y, z + 0.5, necro.getRandom().nextFloat() * 360f, 0f);
            skeleton.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.MOB_SUMMONED, null, null);
            skeleton.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET)); // keeps them alive in daylight
            skeleton.setDropChance(EquipmentSlot.HEAD, 0f);
            skeleton.setDropChance(EquipmentSlot.MAINHAND, 0f);
            Encounters.tagSummon(skeleton, level.getGameTime() + 1800);
            if (level.addFreshEntity(skeleton)) {
                skeleton.setTarget(target);
                level.sendParticles(ParticleTypes.SOUL, x + 0.5, y + 0.5, z + 0.5, 12, 0.3, 0.5, 0.3, 0.02);
            }
        }
    }

    /** A courier (or other seeker) reached its player. */
    public static void arrive(NpcEntity npc, Player player) {
        npc.setSeekTarget(null);
        if (npc.role() == NpcRole.COURIER && npc.getLetter() >= 0 && player instanceof net.minecraft.server.level.ServerPlayer sp) {
            Letters.deliver(npc, sp);
        }
    }

    /** Persistent data helper used by several classes: a sub-compound created on demand. */
    static CompoundTag sub(CompoundTag root, String key) {
        if (!root.contains(key, net.minecraft.nbt.Tag.TAG_COMPOUND)) root.put(key, new CompoundTag());
        return root.getCompound(key);
    }
}
