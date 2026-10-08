package com.skycraft.quest.client;

import com.skycraft.client.hud.CompassMarkers;
import com.skycraft.core.SkyData;
import com.skycraft.quest.Objective;
import com.skycraft.quest.Quest;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Client-side copy of the player's quests and party state (filled by quest packets). */
public final class ClientQuestData {
    public static final int TRACKED_COLOR = 0xE8C060;
    public static final int OTHER_COLOR = 0x9A8E72;

    private static List<Quest> quests = List.of();
    private static CompoundTag party = new CompoundTag();

    private ClientQuestData() {}

    public record Member(UUID id, String name, boolean online, float health, float maxHealth, String dim) {
    }

    public record Invite(UUID party, String from) {
    }

    // ------------------------------------------------------------------ packets

    public static void onQuests(CompoundTag tag) {
        List<Quest> list = new ArrayList<>();
        for (Tag t : tag.getList("quests", Tag.TAG_COMPOUND)) list.add(Quest.load((CompoundTag) t));
        quests = list;
        refreshCompass();
    }

    public static void onParty(CompoundTag tag) {
        party = tag;
    }

    public static void clear() {
        quests = List.of();
        party = new CompoundTag();
        CompassMarkers.clear("quest");
    }

    // ------------------------------------------------------------------ quests

    public static List<Quest> quests() {
        return quests;
    }

    public static String tracked() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? "" : SkyData.get(mc.player).module("quest").getString("tracked");
    }

    /** The tracked quest, or the main quest if nothing is tracked. */
    public static boolean isTracked(Quest q) {
        String t = tracked();
        if (!t.isEmpty()) return q.id.equals(t);
        return q.isMain();
    }

    /** Current-objective markers of every active quest in this dimension; the tracked one highlighted. */
    public static void refreshCompass() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        String dim = mc.level.dimension().location().toString();
        List<CompassMarkers.Marker> others = new ArrayList<>();
        List<CompassMarkers.Marker> tracked = new ArrayList<>();
        for (Quest q : quests) {
            if (!q.isActive()) continue;
            Objective o = q.current();
            if (o == null || !o.hasPos || !o.dim.equals(dim)) continue;
            boolean t = isTracked(q);
            CompassMarkers.Marker m = new CompassMarkers.Marker(Vec3.atBottomCenterOf(o.pos), CompassMarkers.Shape.QUEST,
                    t ? TRACKED_COLOR : OTHER_COLOR, q.title.getString(), 0);
            (t ? tracked : others).add(m);
        }
        others.addAll(tracked); // tracked last so it's drawn on top
        CompassMarkers.set("quest", others);
    }

    // ------------------------------------------------------------------ party

    public static boolean inParty() {
        return party.getBoolean("inParty");
    }

    public static int maxSize() {
        return Math.max(2, party.getInt("max"));
    }

    public static UUID leader() {
        return party.hasUUID("leader") ? party.getUUID("leader") : null;
    }

    public static List<Member> members() {
        List<Member> out = new ArrayList<>();
        for (Tag t : party.getList("members", Tag.TAG_COMPOUND)) {
            CompoundTag m = (CompoundTag) t;
            out.add(new Member(m.getUUID("uuid"), m.getString("name"), m.getBoolean("online"),
                    m.getFloat("health"), m.getFloat("max"), m.getString("dim")));
        }
        return out;
    }

    public static boolean isMember(UUID id) {
        for (Member m : members()) if (m.id().equals(id)) return true;
        return false;
    }

    public static List<Invite> invites() {
        List<Invite> out = new ArrayList<>();
        for (Tag t : party.getList("invites", Tag.TAG_COMPOUND)) {
            CompoundTag i = (CompoundTag) t;
            out.add(new Invite(i.getUUID("party"), i.getString("from")));
        }
        return out;
    }

    /** A signature of the party state, so screens know when to rebuild their buttons. */
    public static String partySignature() {
        StringBuilder sb = new StringBuilder();
        sb.append(inParty()).append(leader());
        for (Member m : members()) sb.append(m.id()).append(m.online());
        for (Invite i : invites()) sb.append(i.party());
        return sb.toString();
    }
}
