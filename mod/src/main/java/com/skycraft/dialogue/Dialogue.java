package com.skycraft.dialogue;

import com.skycraft.Skycraft;
import com.skycraft.network.SkyNetwork;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Skyrim-style conversations. Right-clicking (not sneaking) a villager, wandering trader or any entity in
 * {@code #skycraft:talkers} opens a dialogue menu. Feature modules contribute topics with
 * {@link #registerProvider(DialogueProvider)} (barter, training, work/quests, factions, bounty...).
 *
 * <p>The "Chat" topic hands the interaction back to the entity's own behaviour (vanilla trading UI, or MCA
 * Reborn's interaction menu when that mod is installed).</p>
 */
@Mod.EventBusSubscriber(modid = Skycraft.MODID)
public final class Dialogue {
    public static final TagKey<EntityType<?>> TALKERS = TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(Skycraft.MODID, "talkers"));
    public static final TagKey<EntityType<?>> NO_DIALOGUE = TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(Skycraft.MODID, "no_dialogue"));

    @FunctionalInterface
    public interface DialogueProvider {
        /** Adds the topics this module offers for {@code npc}. Called server-side every time the menu is built. */
        void addOptions(ServerPlayer player, LivingEntity npc, List<DialogueOption> out);
    }

    private static final List<DialogueProvider> PROVIDERS = new CopyOnWriteArrayList<>();
    /** Entity ID -> player UUID currently in conversation with that entity. */
    private static final Map<Integer, UUID> TALKING = new ConcurrentHashMap<>();

    private Dialogue() {}

    public static void registerProvider(DialogueProvider provider) {
        PROVIDERS.add(provider);
    }

    public static boolean canTalk(LivingEntity entity) {
        if (entity.getType().is(NO_DIALOGUE) || !entity.isAlive()) return false;
        return entity instanceof AbstractVillager || entity.getType().is(TALKERS);
    }

    public static List<DialogueOption> collect(ServerPlayer player, LivingEntity npc) {
        List<DialogueOption> out = new ArrayList<>();
        for (DialogueProvider p : PROVIDERS) {
            try {
                p.addOptions(player, npc, out);
            } catch (Exception e) {
                Skycraft.LOGGER.error("Dialogue provider failed", e);
            }
        }
        out.add(new DialogueOption("core.goodbye", Component.translatable("dialogue.skycraft.goodbye"), 1000, Dialogue::onGoodbye));
        out.sort(Comparator.comparingInt(DialogueOption::order));
        return out;
    }

    private static void onGoodbye(ServerPlayer player, LivingEntity npc) {
        endConversation(player, npc.getId());
    }

    /** Opens (or refreshes) the dialogue menu for {@code npc}, with an optional line spoken by the NPC. */
    public static void open(ServerPlayer player, LivingEntity npc, Component greeting) {
        UUID partner = TALKING.get(npc.getId());
        if (partner != null && !partner.equals(player.getUUID())) {
            ServerPlayer currentPartner = player.server.getPlayerList().getPlayer(partner);
            if (currentPartner != null && currentPartner.isAlive() && currentPartner.distanceToSqr(npc) <= 64) {
                com.skycraft.core.Notifier.message(player, Component.translatable("society.skycraft.busy"));
                return;
            }
        }
        TALKING.put(npc.getId(), player.getUUID());
        if (npc instanceof com.skycraft.society.entity.NpcEntity n) {
            n.setConversationPartner(player.getId());
            n.getNavigation().stop();
            n.getLookControl().setLookAt(player, 30.0F, 30.0F);
        }
        List<DialogueOption> options = collect(player, npc);
        List<DialoguePackets.Line> lines = new ArrayList<>();
        for (DialogueOption o : options) lines.add(new DialoguePackets.Line(o.id(), o.label()));
        SkyNetwork.sendToPlayer(player, new DialoguePackets.OpenDialogue(npc.getId(), npc.getDisplayName(), greeting, lines));
    }

    public static void endConversation(ServerPlayer player, int entityId) {
        UUID partner = TALKING.get(entityId);
        if (partner != null && partner.equals(player.getUUID())) {
            TALKING.remove(entityId);
            if (player.level().getEntity(entityId) instanceof com.skycraft.society.entity.NpcEntity n) {
                n.setConversationPartner(-1);
            }
        }
    }

    static void choose(ServerPlayer player, int entityId, String optionId) {
        if (!(player.level().getEntity(entityId) instanceof LivingEntity npc) || player.distanceToSqr(npc) > 64) {
            endConversation(player, entityId);
            return;
        }
        for (DialogueOption option : collect(player, npc)) {
            if (option.id().equals(optionId)) {
                option.action().accept(player, npc);
                return;
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.isCanceled() || event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getTarget() instanceof LivingEntity npc) || !canTalk(npc)) return;
        if (event.getEntity().isShiftKeyDown()) return; // sneaking is reserved for pickpocketing
        if (event.getEntity() instanceof ServerPlayer player) {
            UUID current = TALKING.get(npc.getId());
            if (current != null && !current.equals(player.getUUID())) {
                ServerPlayer sp = player.server.getPlayerList().getPlayer(current);
                if (sp != null && sp.isAlive() && sp.distanceToSqr(npc) <= 64) {
                    com.skycraft.core.Notifier.message(player, Component.translatable("society.skycraft.busy"));
                    event.setCancellationResult(InteractionResult.SUCCESS);
                    event.setCanceled(true);
                    return;
                }
            }
            open(player, npc, Component.empty());
        }
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }
}
