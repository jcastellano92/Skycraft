package com.skycraft.quest;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * A quest: who gave it, what to do (ordered {@link Objective}s) and what it pays. Quests live in {@link QuestStore}
 * under an owner key (a party, or a single player) so a whole party shares progress. The same class is used on the
 * client (journal) after being synced as NBT.
 */
public class Quest {
    public enum Status { ACTIVE, COMPLETED, FAILED }

    public enum Category { MAIN, SIDE, FACTION, BOUNTY, MISC }

    public String id = "";
    /** Generator kind, e.g. {@code villager_fetch}, {@code bounty_bandit}, {@code main_3}, {@code companions_contract}. */
    public String kind = "";
    public Category category = Category.MISC;
    public Component title = Component.empty();
    public Component description = Component.empty();
    public Component giverName = Component.empty();
    @Nullable
    public UUID giver;
    public BlockPos giverPos = BlockPos.ZERO;
    public String giverDim = "minecraft:overworld";
    public String hold = "";
    /** Faction id or empty. */
    public String faction = "";
    /** Completing this quest makes every party member join {@link #faction}. */
    public boolean factionJoin;
    public int factionPoints;
    public final List<Objective> objectives = new ArrayList<>();
    public long rewardGold;
    public final List<ItemStack> rewardItems = new ArrayList<>();
    public Status status = Status.ACTIVE;
    public long started;
    public long finished;
    /** Extra data: {@code give_on_start} (items), failure reason... */
    public CompoundTag extra = new CompoundTag();

    /** Client only: true if this quest belongs to the player's party rather than to the player alone. */
    public transient boolean shared;

    public Quest() {}

    public Quest(String kind, Category category, Component title, Component description) {
        this.kind = kind;
        this.category = category;
        this.title = title;
        this.description = description;
    }

    public Quest add(Objective o) {
        objectives.add(o);
        return this;
    }

    public Quest reward(long gold, ItemStack... items) {
        this.rewardGold = gold;
        for (ItemStack s : items) if (!s.isEmpty()) rewardItems.add(s);
        return this;
    }

    public Quest faction(String faction, int points, boolean join) {
        this.faction = faction;
        this.factionPoints = points;
        this.factionJoin = join;
        return this;
    }

    /** The first objective that isn't done yet, or null if all are done. */
    @Nullable
    public Objective current() {
        for (Objective o : objectives) if (!o.done) return o;
        return null;
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    public boolean isMain() {
        return category == Category.MAIN;
    }

    public boolean sneakBonus() {
        for (Objective o : objectives) if (o.sneakKill) return true;
        return false;
    }

    // ------------------------------------------------------------------ component json

    public static String writeComponent(Component c) {
        try {
            return Component.Serializer.toJson(c == null ? Component.empty() : c);
        } catch (Exception e) {
            return "\"\"";
        }
    }

    public static Component readComponent(String json) {
        if (json == null || json.isEmpty()) return Component.empty();
        try {
            MutableComponent c = Component.Serializer.fromJson(json);
            return c == null ? Component.empty() : c;
        } catch (Exception e) {
            return Component.literal(json);
        }
    }

    // ------------------------------------------------------------------ nbt

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("id", id);
        t.putString("kind", kind);
        t.putString("category", category.name());
        t.putString("title", writeComponent(title));
        t.putString("description", writeComponent(description));
        t.putString("giverName", writeComponent(giverName));
        if (giver != null) t.putUUID("giver", giver);
        t.putInt("gx", giverPos.getX());
        t.putInt("gy", giverPos.getY());
        t.putInt("gz", giverPos.getZ());
        t.putString("giverDim", giverDim);
        t.putString("hold", hold);
        t.putString("faction", faction);
        t.putBoolean("factionJoin", factionJoin);
        t.putInt("factionPoints", factionPoints);
        ListTag objs = new ListTag();
        for (Objective o : objectives) objs.add(o.save());
        t.put("objectives", objs);
        t.putLong("rewardGold", rewardGold);
        ListTag items = new ListTag();
        for (ItemStack s : rewardItems) items.add(s.save(new CompoundTag()));
        t.put("rewardItems", items);
        t.putString("status", status.name());
        t.putLong("started", started);
        t.putLong("finished", finished);
        t.put("extra", extra.copy());
        return t;
    }

    public static Quest load(CompoundTag t) {
        Quest q = new Quest();
        q.id = t.getString("id");
        q.kind = t.getString("kind");
        q.category = parse(Category.class, t.getString("category"), Category.MISC);
        q.title = readComponent(t.getString("title"));
        q.description = readComponent(t.getString("description"));
        q.giverName = readComponent(t.getString("giverName"));
        q.giver = t.hasUUID("giver") ? t.getUUID("giver") : null;
        q.giverPos = new BlockPos(t.getInt("gx"), t.getInt("gy"), t.getInt("gz"));
        q.giverDim = t.contains("giverDim") ? t.getString("giverDim") : "minecraft:overworld";
        q.hold = t.getString("hold");
        q.faction = t.getString("faction");
        q.factionJoin = t.getBoolean("factionJoin");
        q.factionPoints = t.getInt("factionPoints");
        for (Tag o : t.getList("objectives", Tag.TAG_COMPOUND)) q.objectives.add(Objective.load((CompoundTag) o));
        q.rewardGold = t.getLong("rewardGold");
        for (Tag s : t.getList("rewardItems", Tag.TAG_COMPOUND)) {
            ItemStack stack = ItemStack.of((CompoundTag) s);
            if (!stack.isEmpty()) q.rewardItems.add(stack);
        }
        q.status = parse(Status.class, t.getString("status"), Status.ACTIVE);
        q.started = t.getLong("started");
        q.finished = t.getLong("finished");
        q.extra = t.getCompound("extra").copy();
        q.shared = t.getBoolean("shared");
        return q;
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String name, E fallback) {
        try {
            return Enum.valueOf(type, name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
