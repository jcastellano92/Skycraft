package com.skycraft.society;

import com.skycraft.core.Currency;
import com.skycraft.core.Notifier;
import com.skycraft.core.PlayerData;
import com.skycraft.core.Skill;
import com.skycraft.core.SkyData;
import com.skycraft.creatures.Leveling;
import com.skycraft.dialogue.Dialogue;
import com.skycraft.dialogue.DialogueOption;
import com.skycraft.society.entity.NpcEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Contract 11 (docs/PLAYTEST-1.md): follower relationships.
 *
 * <p>An NPC follows a player only after a relationship is built: a favor quest, enough gold for hirelings
 * (500 Septims), a successful Speech check, or a quest reward (Housecarl). Followers obey commands (wait,
 * follow, trade items, dismiss), use their gear, and level with the region.</p>
 */
public final class Followers {
    public static final String FOLLOWER_KEY = "skycraft_follower";
    public static final String INVENTORY_KEY = "skycraft_follower_inv";
    public static final int INVENTORY_SIZE = 27;
    public static final int HIRELING_COST = 500;

    /** In-memory cache of player UUID -> follower entity UUID */
    private static final Map<UUID, UUID> PLAYER_FOLLOWER = new ConcurrentHashMap<>();

    private Followers() {}

    public static void register() {
        Dialogue.registerProvider(Followers::addOptions);
    }

    /**
     * Contract 11: checks whether {@code npc} is an active follower of {@code player}.
     */
    public static boolean isFollowerOf(@Nullable Entity npc, @Nullable Player player) {
        if (npc == null || player == null) return false;
        CompoundTag pd = npc.getPersistentData();
        if (!pd.contains(FOLLOWER_KEY, Tag.TAG_COMPOUND)) return false;
        CompoundTag f = pd.getCompound(FOLLOWER_KEY);
        return player.getStringUUID().equals(f.getString("owner"));
    }

    public static boolean isFollower(Entity npc) {
        if (npc == null) return false;
        return npc.getPersistentData().contains(FOLLOWER_KEY, Tag.TAG_COMPOUND);
    }

    @Nullable
    public static UUID getOwnerId(Entity npc) {
        if (npc == null) return null;
        CompoundTag pd = npc.getPersistentData();
        if (!pd.contains(FOLLOWER_KEY, Tag.TAG_COMPOUND)) return null;
        try {
            return UUID.fromString(pd.getCompound(FOLLOWER_KEY).getString("owner"));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static boolean isWaiting(Entity npc) {
        if (npc == null) return false;
        CompoundTag pd = npc.getPersistentData();
        if (!pd.contains(FOLLOWER_KEY, Tag.TAG_COMPOUND)) return false;
        return "WAIT".equals(pd.getCompound(FOLLOWER_KEY).getString("state"));
    }

    public static void setWaiting(LivingEntity npc, boolean wait) {
        CompoundTag pd = npc.getPersistentData();
        if (!pd.contains(FOLLOWER_KEY, Tag.TAG_COMPOUND)) return;
        CompoundTag f = pd.getCompound(FOLLOWER_KEY);
        f.putString("state", wait ? "WAIT" : "FOLLOW");
        pd.put(FOLLOWER_KEY, f);
    }

    public static void recruit(ServerPlayer player, LivingEntity npc) {
        // Dismiss any existing follower
        UUID existing = PLAYER_FOLLOWER.get(player.getUUID());
        if (existing != null && player.level() instanceof ServerLevel sl) {
            Entity old = sl.getEntity(existing);
            if (old instanceof LivingEntity livingOld && isFollowerOf(livingOld, player)) {
                dismiss(player, livingOld);
            }
        }

        CompoundTag f = new CompoundTag();
        f.putString("owner", player.getStringUUID());
        f.putString("owner_name", player.getName().getString());
        f.putString("state", "FOLLOW");
        npc.getPersistentData().put(FOLLOWER_KEY, f);

        PLAYER_FOLLOWER.put(player.getUUID(), npc.getUUID());

        if (npc instanceof NpcEntity n) {
            n.setLeader(player.getUUID());
            n.setPersistenceRequired();
        }

        if (player.level() instanceof ServerLevel sl) {
            syncFollowerStats(npc, sl);
        }

        Notifier.message(player, Component.translatable("society.skycraft.follower.recruited", npc.getDisplayName()));
        player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.3f);
    }

    public static void dismiss(ServerPlayer player, LivingEntity npc) {
        npc.getPersistentData().remove(FOLLOWER_KEY);
        PLAYER_FOLLOWER.remove(player.getUUID());

        if (npc instanceof NpcEntity n) {
            n.setLeader(null);
            if (n.getHome() != null) {
                n.setDestination(n.getHome());
            }
        }

        Notifier.message(player, Component.translatable("society.skycraft.follower.dismissed", npc.getDisplayName()));
    }

    public static void syncFollowerStats(LivingEntity npc, ServerLevel level) {
        int rLevel = Leveling.regionLevel(level, npc.blockPosition());
        double baseHp = 30.0 + rLevel * 1.5;
        double baseDmg = 4.0 + rLevel * 0.2;
        double baseArmor = 2.0 + rLevel * 0.3;

        AttributeInstance hp = npc.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) hp.setBaseValue(baseHp);

        AttributeInstance dmg = npc.getAttribute(Attributes.ATTACK_DAMAGE);
        if (dmg != null) dmg.setBaseValue(baseDmg);

        AttributeInstance arm = npc.getAttribute(Attributes.ARMOR);
        if (arm != null) arm.setBaseValue(baseArmor);

        npc.setHealth((float) baseHp);
    }

    public static SimpleContainer getFollowerInventory(LivingEntity npc) {
        SimpleContainer container = new SimpleContainer(INVENTORY_SIZE);
        CompoundTag pd = npc.getPersistentData();
        if (pd.contains(INVENTORY_KEY, Tag.TAG_LIST)) {
            ListTag list = pd.getList(INVENTORY_KEY, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag itemTag = list.getCompound(i);
                int slot = itemTag.getInt("Slot");
                if (slot >= 0 && slot < INVENTORY_SIZE) {
                    container.setItem(slot, ItemStack.of(itemTag));
                }
            }
        }
        return container;
    }

    public static void saveFollowerInventory(LivingEntity npc, Container container) {
        ListTag list = new ListTag();
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty()) {
                CompoundTag itemTag = new CompoundTag();
                itemTag.putInt("Slot", i);
                stack.save(itemTag);
                list.add(itemTag);
            }
        }
        npc.getPersistentData().put(INVENTORY_KEY, list);
        autoEquipBestGear(npc, container);
    }

    private static void autoEquipBestGear(LivingEntity npc, Container container) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) continue;
            if (stack.getItem() instanceof ArmorItem armor) {
                EquipmentSlot slot = armor.getEquipmentSlot();
                ItemStack current = npc.getItemBySlot(slot);
                if (current.isEmpty()) {
                    npc.setItemSlot(slot, stack.copy());
                }
            } else if (stack.getItem() instanceof SwordItem) {
                ItemStack current = npc.getMainHandItem();
                if (current.isEmpty()) {
                    npc.setItemSlot(EquipmentSlot.MAINHAND, stack.copy());
                }
            }
        }
    }

    public static void openTrade(ServerPlayer player, LivingEntity npc) {
        SimpleContainer container = getFollowerInventory(npc);
        container.addListener(c -> saveFollowerInventory(npc, c));
        player.openMenu(new SimpleMenuProvider(
                (containerId, playerInv, p) -> new ChestMenu(MenuType.GENERIC_9x3, containerId, playerInv, container, 3) {
                    @Override
                    public void removed(Player pl) {
                        super.removed(pl);
                        saveFollowerInventory(npc, container);
                    }
                },
                Component.translatable("society.skycraft.follower.trade_title", npc.getDisplayName())
        ));
    }

    // ------------------------------------------------------------------ Dialogue

    static void addOptions(ServerPlayer player, LivingEntity entity, List<DialogueOption> out) {
        if (!(entity instanceof NpcEntity npc) || !npc.isAlive() || npc.isPrisoner() || npc.isHostileTo(player)) return;

        if (isFollowerOf(npc, player)) {
            boolean wait = isWaiting(npc);
            if (wait) {
                out.add(new DialogueOption("follower.follow", Component.translatable("dialogue.skycraft.follower.follow"), 50, (p, n) -> {
                    setWaiting((LivingEntity) n, false);
                    Dialogue.open(p, (LivingEntity) n, Component.translatable("society.skycraft.say.follower_follow"));
                }));
            } else {
                out.add(new DialogueOption("follower.wait", Component.translatable("dialogue.skycraft.follower.wait"), 50, (p, n) -> {
                    setWaiting((LivingEntity) n, true);
                    Dialogue.open(p, (LivingEntity) n, Component.translatable("society.skycraft.say.follower_wait"));
                }));
            }
            out.add(new DialogueOption("follower.trade", Component.translatable("dialogue.skycraft.follower.trade"), 51, (p, n) -> {
                openTrade(p, (LivingEntity) n);
            }));
            out.add(new DialogueOption("follower.dismiss", Component.translatable("dialogue.skycraft.follower.dismiss"), 52, (p, n) -> {
                dismiss(p, (LivingEntity) n);
                Dialogue.open(p, (LivingEntity) n, Component.translatable("society.skycraft.say.follower_dismiss"));
            }));
            return;
        }

        // Potential recruitment
        NpcRole role = npc.role();
        if (role == NpcRole.ADVENTURER) {
            out.add(new DialogueOption("follower.hire", Component.translatable("dialogue.skycraft.follower.hire", HIRELING_COST), 60, (p, n) -> {
                if (!Currency.take(p, HIRELING_COST)) {
                    Dialogue.open(p, (LivingEntity) n, Component.translatable("message.skycraft.not_enough_gold"));
                    return;
                }
                recruit(p, (LivingEntity) n);
                Dialogue.open(p, (LivingEntity) n, Component.translatable("society.skycraft.say.follower_accept"));
            }));
            out.add(new DialogueOption("follower.persuade", Component.translatable("dialogue.skycraft.follower.persuade"), 61, (p, n) -> {
                if (SpeechChecks.checkPersuade(p, 45)) {
                    recruit(p, (LivingEntity) n);
                    Dialogue.open(p, (LivingEntity) n, Component.translatable("society.skycraft.say.follower_accept"));
                } else {
                    Dialogue.open(p, (LivingEntity) n, Component.translatable("society.skycraft.say.follower_refuse"));
                }
            }));
        } else if (role == NpcRole.HOUSECARL) {
            int rep = Reputation.get(player, Reputation.GUARDS);
            int townRep = Reputation.get(player, Reputation.TOWNSFOLK);
            if (rep >= 20 || townRep >= 30) {
                out.add(new DialogueOption("follower.housecarl", Component.translatable("dialogue.skycraft.follower.housecarl"), 60, (p, n) -> {
                    recruit(p, (LivingEntity) n);
                    Dialogue.open(p, (LivingEntity) n, Component.translatable("society.skycraft.say.housecarl_accept"));
                }));
            }
        } else if (role == NpcRole.HUNTER || role == NpcRole.MAGE) {
            int townRep = Reputation.get(player, Reputation.TOWNSFOLK);
            if (townRep >= 30) {
                out.add(new DialogueOption("follower.friend", Component.translatable("dialogue.skycraft.follower.friend"), 60, (p, n) -> {
                    recruit(p, (LivingEntity) n);
                    Dialogue.open(p, (LivingEntity) n, Component.translatable("society.skycraft.say.follower_accept"));
                }));
            } else {
                out.add(new DialogueOption("follower.persuade_friend", Component.translatable("dialogue.skycraft.follower.persuade"), 61, (p, n) -> {
                    if (SpeechChecks.checkPersuade(p, 40)) {
                        recruit(p, (LivingEntity) n);
                        Dialogue.open(p, (LivingEntity) n, Component.translatable("society.skycraft.say.follower_accept"));
                    } else {
                        Dialogue.open(p, (LivingEntity) n, Component.translatable("society.skycraft.say.follower_refuse"));
                    }
                }));
            }
        }
    }
}
