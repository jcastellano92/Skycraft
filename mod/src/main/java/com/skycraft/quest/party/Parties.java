package com.skycraft.quest.party;

import com.skycraft.network.SkyNetwork;
import com.skycraft.quest.QuestConfig;
import com.skycraft.quest.QuestPackets;
import com.skycraft.quest.Quests;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Party rules: create, invite, accept, decline, leave, kick, promote, party chat, and the once-a-second sync of
 * member health to every member's HUD. Used by both {@code /party} and the Party screen.
 */
public final class Parties {
    /** Invites expire after two minutes. */
    private static final long INVITE_TICKS = 20L * 120;
    private static final Map<UUID, List<Invite>> INVITES = new HashMap<>();
    /** Players who were last sent a non-empty party state (so they get one "empty" update when it ends). */
    private static final Set<UUID> SYNCED = new HashSet<>();

    public record Invite(UUID partyId, UUID inviter, String inviterName, long expires) {
    }

    private Parties() {}

    private static MutableComponent msg(String key, Object... args) {
        return Component.translatable("party.skycraft." + key, args).withStyle(ChatFormatting.GOLD);
    }

    private static MutableComponent err(String key, Object... args) {
        return Component.translatable("party.skycraft." + key, args).withStyle(ChatFormatting.RED);
    }

    @Nullable
    public static Party partyOf(ServerPlayer player) {
        return PartyManager.get(player.server).partyOf(player.getUUID());
    }

    public static boolean sameParty(MinecraftServer server, ServerPlayer a, ServerPlayer b) {
        PartyManager m = PartyManager.get(server);
        Party pa = m.partyOf(a.getUUID());
        return pa != null && pa.has(b.getUUID());
    }

    public static List<ServerPlayer> onlineMembers(MinecraftServer server, Party party) {
        List<ServerPlayer> out = new ArrayList<>();
        for (UUID id : party.memberIds()) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) out.add(p);
        }
        return out;
    }

    public static void broadcast(MinecraftServer server, Party party, Component message) {
        for (ServerPlayer p : onlineMembers(server, party)) p.sendSystemMessage(message);
    }

    // ------------------------------------------------------------------ actions

    public static boolean create(ServerPlayer player) {
        PartyManager m = PartyManager.get(player.server);
        if (m.partyOf(player.getUUID()) != null) {
            player.sendSystemMessage(err("already_in_party"));
            return false;
        }
        m.create(player.getUUID(), player.getGameProfile().getName());
        player.sendSystemMessage(msg("created"));
        Quests.onPartyChanged(player.server, player.getUUID());
        sync(player);
        return true;
    }

    public static boolean invite(ServerPlayer from, ServerPlayer to) {
        MinecraftServer server = from.server;
        PartyManager m = PartyManager.get(server);
        if (from == to) {
            from.sendSystemMessage(err("self"));
            return false;
        }
        Party party = m.partyOf(from.getUUID());
        if (party == null) {
            party = m.create(from.getUUID(), from.getGameProfile().getName());
            from.sendSystemMessage(msg("created"));
            Quests.onPartyChanged(server, from.getUUID());
        } else if (!party.leader.equals(from.getUUID())) {
            from.sendSystemMessage(err("not_leader"));
            return false;
        }
        if (party.has(to.getUUID()) || m.partyOf(to.getUUID()) != null) {
            from.sendSystemMessage(err("target_in_party", to.getDisplayName()));
            return false;
        }
        if (party.members.size() >= QuestConfig.MAX_PARTY_SIZE.get()) {
            from.sendSystemMessage(err("full"));
            return false;
        }
        List<Invite> list = INVITES.computeIfAbsent(to.getUUID(), k -> new ArrayList<>());
        final UUID partyId = party.id;
        list.removeIf(i -> i.partyId().equals(partyId));
        list.add(new Invite(party.id, from.getUUID(), from.getGameProfile().getName(), server.getTickCount() + INVITE_TICKS));
        from.sendSystemMessage(msg("invited", to.getDisplayName()));
        to.sendSystemMessage(msg("invite_received", from.getDisplayName()));
        sync(from);
        sync(to);
        return true;
    }

    public static List<Invite> invitesFor(MinecraftServer server, UUID player) {
        List<Invite> list = INVITES.get(player);
        if (list == null) return List.of();
        long now = server.getTickCount();
        list.removeIf(i -> i.expires() < now || PartyManager.get(server).byId(i.partyId()) == null);
        if (list.isEmpty()) {
            INVITES.remove(player);
            return List.of();
        }
        return list;
    }

    /** Accepts the invite from {@code partyId}, or the most recent one if null. */
    public static boolean accept(ServerPlayer player, @Nullable UUID partyId) {
        MinecraftServer server = player.server;
        PartyManager m = PartyManager.get(server);
        if (m.partyOf(player.getUUID()) != null) {
            player.sendSystemMessage(err("already_in_party"));
            return false;
        }
        List<Invite> invites = invitesFor(server, player.getUUID());
        Invite chosen = null;
        for (Invite i : invites) {
            if (partyId == null || i.partyId().equals(partyId)) chosen = i;
        }
        if (chosen == null) {
            player.sendSystemMessage(err("no_invite"));
            return false;
        }
        Party party = m.byId(chosen.partyId());
        if (party == null) {
            player.sendSystemMessage(err("no_invite"));
            return false;
        }
        if (party.members.size() >= QuestConfig.MAX_PARTY_SIZE.get()) {
            player.sendSystemMessage(err("full"));
            return false;
        }
        INVITES.remove(player.getUUID());
        m.addMember(party, player.getUUID(), player.getGameProfile().getName());
        broadcast(server, party, msg("joined", player.getDisplayName()));
        Quests.onPartyChanged(server, player.getUUID());
        syncParty(server, party);
        return true;
    }

    public static boolean decline(ServerPlayer player, @Nullable UUID partyId) {
        MinecraftServer server = player.server;
        List<Invite> invites = invitesFor(server, player.getUUID());
        if (invites.isEmpty()) {
            player.sendSystemMessage(err("no_invite"));
            return false;
        }
        Iterator<Invite> it = invites.iterator();
        Invite last = invites.get(invites.size() - 1);
        while (it.hasNext()) {
            Invite i = it.next();
            if (partyId == null ? i == last : i.partyId().equals(partyId)) {
                it.remove();
                ServerPlayer inviter = server.getPlayerList().getPlayer(i.inviter());
                if (inviter != null) inviter.sendSystemMessage(err("declined", player.getDisplayName()));
            }
        }
        player.sendSystemMessage(msg("you_declined"));
        sync(player);
        return true;
    }

    public static boolean leave(ServerPlayer player) {
        MinecraftServer server = player.server;
        PartyManager m = PartyManager.get(server);
        Party party = m.partyOf(player.getUUID());
        if (party == null) {
            player.sendSystemMessage(err("not_in_party"));
            return false;
        }
        removeFrom(server, party, player.getUUID(), player.getGameProfile().getName(), false);
        player.sendSystemMessage(msg("you_left"));
        return true;
    }

    public static boolean kick(ServerPlayer leader, UUID target) {
        MinecraftServer server = leader.server;
        Party party = PartyManager.get(server).partyOf(leader.getUUID());
        if (party == null) {
            leader.sendSystemMessage(err("not_in_party"));
            return false;
        }
        if (!party.leader.equals(leader.getUUID())) {
            leader.sendSystemMessage(err("not_leader"));
            return false;
        }
        if (!party.has(target) || target.equals(leader.getUUID())) {
            leader.sendSystemMessage(err("not_member"));
            return false;
        }
        String name = party.nameOf(target);
        removeFrom(server, party, target, name, true);
        ServerPlayer kicked = server.getPlayerList().getPlayer(target);
        if (kicked != null) kicked.sendSystemMessage(err("you_were_kicked"));
        return true;
    }

    public static boolean promote(ServerPlayer leader, UUID target) {
        MinecraftServer server = leader.server;
        PartyManager m = PartyManager.get(server);
        Party party = m.partyOf(leader.getUUID());
        if (party == null || !party.leader.equals(leader.getUUID())) {
            leader.sendSystemMessage(err("not_leader"));
            return false;
        }
        if (!party.has(target)) {
            leader.sendSystemMessage(err("not_member"));
            return false;
        }
        party.leader = target;
        m.setDirty();
        broadcast(server, party, msg("leader", party.nameOf(target)));
        syncParty(server, party);
        return true;
    }

    private static void removeFrom(MinecraftServer server, Party party, UUID player, String name, boolean kicked) {
        PartyManager m = PartyManager.get(server);
        UUID oldLeader = party.leader;
        boolean last = party.members.size() <= 1;
        if (last) {
            // the last member keeps the party's quests
            Quests.onPartyDisband(server, party, player);
        }
        boolean deleted = m.removeMember(party, player);
        if (!deleted) {
            broadcast(server, party, msg(kicked ? "kicked" : "left", name));
            if (!oldLeader.equals(party.leader)) broadcast(server, party, msg("leader", party.nameOf(party.leader)));
            syncParty(server, party);
        }
        Quests.onPartyChanged(server, player);
        ServerPlayer p = server.getPlayerList().getPlayer(player);
        if (p != null) sync(p);
    }

    public static void chat(ServerPlayer player, String text) {
        Party party = partyOf(player);
        if (party == null) {
            player.sendSystemMessage(err("not_in_party"));
            return;
        }
        Component line = Component.translatable("party.skycraft.chat", player.getDisplayName(), text).withStyle(ChatFormatting.AQUA);
        broadcast(player.server, party, line);
    }

    public static void onLogin(ServerPlayer player) {
        PartyManager.get(player.server).rename(player.getUUID(), player.getGameProfile().getName());
        Party party = partyOf(player);
        if (party != null) {
            for (ServerPlayer other : onlineMembers(player.server, party)) {
                if (other != player) other.sendSystemMessage(msg("member_online", player.getDisplayName()));
            }
        }
        sync(player);
    }

    public static void onLogout(ServerPlayer player) {
        SYNCED.remove(player.getUUID());
    }

    /** Clears transient state when the server stops (integrated servers reuse the JVM). */
    public static void reset() {
        INVITES.clear();
        SYNCED.clear();
    }

    // ------------------------------------------------------------------ sync

    /** Sends the party state (members, health, invites) to everyone who needs it. Called once a second. */
    public static void tick(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) sync(p);
    }

    public static void syncParty(MinecraftServer server, Party party) {
        for (ServerPlayer p : onlineMembers(server, party)) sync(p);
    }

    public static void sync(ServerPlayer player) {
        MinecraftServer server = player.server;
        Party party = PartyManager.get(server).partyOf(player.getUUID());
        List<Invite> invites = invitesFor(server, player.getUUID());
        if (party == null && invites.isEmpty()) {
            if (SYNCED.remove(player.getUUID())) SkyNetwork.sendToPlayer(player, new QuestPackets.SyncParty(new CompoundTag()));
            return;
        }
        SYNCED.add(player.getUUID());
        SkyNetwork.sendToPlayer(player, new QuestPackets.SyncParty(state(server, party, invites)));
    }

    private static CompoundTag state(MinecraftServer server, @Nullable Party party, List<Invite> invites) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("max", QuestConfig.MAX_PARTY_SIZE.get());
        if (party != null) {
            tag.putBoolean("inParty", true);
            tag.putUUID("id", party.id);
            tag.putUUID("leader", party.leader);
            ListTag members = new ListTag();
            party.members.forEach((uuid, name) -> {
                CompoundTag m = new CompoundTag();
                m.putUUID("uuid", uuid);
                m.putString("name", name);
                ServerPlayer p = server.getPlayerList().getPlayer(uuid);
                m.putBoolean("online", p != null);
                if (p != null) {
                    m.putFloat("health", p.getHealth());
                    m.putFloat("max", p.getMaxHealth());
                    m.putString("dim", p.level().dimension().location().toString());
                }
                members.add(m);
            });
            tag.put("members", members);
        }
        ListTag inv = new ListTag();
        for (Invite i : invites) {
            CompoundTag t = new CompoundTag();
            t.putUUID("party", i.partyId());
            t.putString("from", i.inviterName());
            inv.add(t);
        }
        tag.put("invites", inv);
        return tag;
    }
}
