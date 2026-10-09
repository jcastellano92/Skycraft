package com.skycraft.society;

import com.skycraft.Skycraft;
import com.skycraft.core.Buffs;
import com.skycraft.core.Currency;
import com.skycraft.core.Notifier;
import com.skycraft.core.PlayerData;
import com.skycraft.core.SkyData;
import com.skycraft.dialogue.Dialogue;
import com.skycraft.dialogue.DialogueOption;
import com.skycraft.society.entity.NpcEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * Conversations with society NPCs. Right-clicking one opens the shared dialogue menu with a role greeting (hostile
 * NPCs don't talk). Topics: the beggar's coin, the bard's song, the priest's healing, the innkeeper's mead, the
 * soldiers' war news, the Jarl's opinion of you, freeing prisoners, and "what do you do?" for everyone. Rooms, rumors
 * and disease cures are offered by the survival module, which recognizes our NPCs by persistent {@code skycraft_role}.
 */
@Mod.EventBusSubscriber(modid = Skycraft.MODID)
public final class NpcDialogue {
    public static final int DRINK_PRICE = 5;

    private NpcDialogue() {}

    static void register() {
        Dialogue.registerProvider(NpcDialogue::addOptions);
    }

    /** Handles the click ourselves (both sides) so every NPC type gets a role greeting and hostile NPCs stay silent. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.isCanceled() || event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getTarget() instanceof NpcEntity npc) || !npc.isAlive()) return;
        Player player = event.getEntity();
        if (player.isShiftKeyDown()) return; // pickpocketing (crime module)
        ItemStack held = event.getItemStack();
        if (held.is(Items.NAME_TAG) || held.is(Items.LEAD)) return;
        if (player instanceof ServerPlayer sp) {
            if (npc.getTarget() == sp || npc.isHostileTo(sp)) {
                event.setCancellationResult(InteractionResult.FAIL);
                event.setCanceled(true);
                return;
            }
            if (npc.isConversing() && npc.getConversationPartner() != sp.getId()) {
                Entity current = sp.serverLevel().getEntity(npc.getConversationPartner());
                if (current != null && current.isAlive() && npc.distanceToSqr(current) <= 64) {
                    Notifier.message(sp, Component.translatable("society.skycraft.busy"));
                    event.setCancellationResult(InteractionResult.SUCCESS);
                    event.setCanceled(true);
                    return;
                }
            }
            npc.getNavigation().stop();
            npc.getLookControl().setLookAt(sp, 30f, 30f);
            Dialogue.open(sp, npc, greeting(sp, npc));
        }
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    static Component greeting(ServerPlayer player, NpcEntity npc) {
        NpcRole role = npc.role();
        if (npc.isPrisoner()) return line("prisoner");
        int townsfolk = Reputation.get(player, Reputation.TOWNSFOLK);
        if (role.civilian && townsfolk <= -40) return line("cold");
        if (role == NpcRole.JARL && townsfolk >= 50) return line("jarl_honored");
        if (role == NpcRole.THALMOR) return line("thalmor");
        return Component.translatable("society.skycraft.greet." + role.id + "." + npc.getRandom().nextInt(2), player.getName());
    }

    private static Component line(String key) {
        return Component.translatable("society.skycraft.greet." + key);
    }

    private static Component say(String key, Object... args) {
        return Component.translatable("society.skycraft.say." + key, args);
    }

    private static Component opt(String key, Object... args) {
        return Component.translatable("dialogue.skycraft.society." + key, args);
    }

    // ------------------------------------------------------------------ topics

    static void addOptions(ServerPlayer player, LivingEntity entity, List<DialogueOption> out) {
        if (!(entity instanceof NpcEntity npc) || npc.isHostileTo(player)) return;
        String hold = com.skycraft.crime.Holds.holdAt(player.level(), player.blockPosition());
        if (com.skycraft.crime.Crimes.isGuard(npc) && com.skycraft.crime.Bounty.get(player, hold) > 0) return;
        NpcRole role = npc.role();
        if (npc.isPrisoner()) {
            out.add(new DialogueOption("society.free", opt("free"), 10, NpcDialogue::freePrisoner));
            return;
        }
        out.add(new DialogueOption("society.job", opt("job"), 600,
                (p, n) -> Dialogue.open(p, n, say("job." + role.id))));

        switch (role) {
            case BEGGAR -> out.add(new DialogueOption("society.coin", opt("coin"), 10, NpcDialogue::giveCoin));
            case BARD -> {
                boolean atInn = isNearInn(npc);
                out.add(new DialogueOption("society.song", opt("song"), 10, (p, n) -> {
                    if (atInn) {
                        if (n instanceof NpcEntity b) b.requestSong();
                        Dialogue.open(p, n, say("song"));
                    } else {
                        Dialogue.open(p, n, Component.literal("I only perform at taverns and inns, traveler. Look for me there."));
                    }
                }));
            }
            // room, rumors and disease cures come from the survival module (it recognizes persistent "skycraft_role")
            case PRIEST -> out.add(new DialogueOption("society.heal", opt("heal"), 10, NpcDialogue::heal));
            case INNKEEPER -> out.add(new DialogueOption("society.drink", opt("drink", DRINK_PRICE), 97, NpcDialogue::buyDrink));
            case IMPERIAL_SOLDIER, STORMCLOAK_SOLDIER -> out.add(new DialogueOption("society.war", opt("war"), 10,
                    (p, n) -> Dialogue.open(p, n, say("war." + role.id + "." + p.getRandom().nextInt(2)))));
            case JARL -> out.add(new DialogueOption("society.standing", opt("standing"), 10, NpcDialogue::standing));
            case HUNTER -> out.add(new DialogueOption("society.game", opt("game"), 10,
                    (p, n) -> Dialogue.open(p, n, say("game." + p.getRandom().nextInt(2)))));
            default -> {
            }
        }
    }

    private static void giveCoin(ServerPlayer player, LivingEntity npc) {
        if (!Currency.take(player, 1)) {
            Notifier.message(player, Component.translatable("message.skycraft.not_enough_gold"));
            return;
        }
        PlayerData data = SkyData.get(player);
        CompoundTag mod = data.module(Reputation.MODULE);
        long day = player.level().getDayTime() / 24000L;
        if (mod.getLong("charity_day") != day) {
            mod.putLong("charity_day", day);
            mod.putInt("charity", 0);
        }
        int given = mod.getInt("charity");
        mod.putInt("charity", given + 1);
        data.markDirty();
        if (given < 3) Reputation.add(player, Reputation.TOWNSFOLK, 1);
        Buffs.apply(player, "gift_of_charity", 20 * 60 * 2);
        if (npc instanceof NpcEntity n) Barks.sayLineNow(n, "beggar_thanks");
        Notifier.message(player, Component.translatable("society.skycraft.charity"));
        Dialogue.open(player, npc, say("coin"));
    }

    private static void heal(ServerPlayer player, LivingEntity npc) {
        player.heal(player.getMaxHealth());
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0));
        if (npc instanceof NpcEntity n) Barks.sayLineNow(n, "priest_heal");
        Dialogue.open(player, npc, say("heal"));
    }

    private static void buyDrink(ServerPlayer player, LivingEntity npc) {
        if (!Currency.take(player, DRINK_PRICE)) {
            Notifier.message(player, Component.translatable("message.skycraft.not_enough_gold"));
            return;
        }
        ItemStack mead = new ItemStack(Items.HONEY_BOTTLE);
        mead.setHoverName(Component.translatable("society.skycraft.item.mead"));
        if (!player.getInventory().add(mead)) player.drop(mead, false);
        Dialogue.open(player, npc, say("drink"));
    }

    private static void standing(ServerPlayer player, LivingEntity npc) {
        int rep = Reputation.get(player, Reputation.TOWNSFOLK);
        Reputation.Tier tier = Reputation.Tier.of(rep);
        Dialogue.open(player, npc, say("standing." + tier.name().toLowerCase(java.util.Locale.ROOT)));
    }

    private static void freePrisoner(ServerPlayer player, LivingEntity entity) {
        if (!(entity instanceof NpcEntity prisoner) || !prisoner.isPrisoner()) return;
        java.util.UUID leaderId = prisoner.getLeader();
        prisoner.setPrisoner(false);
        prisoner.setLeader(null);
        Barks.sayLineNow(prisoner, "prisoner_freed");
        Reputation.add(player, prisoner.role().faction == null || prisoner.role().civilian ? Reputation.TOWNSFOLK : prisoner.role().faction, 6);
        // the escort doesn't take kindly to this
        String captors = null;
        for (NpcEntity e : prisoner.level().getEntitiesOfClass(NpcEntity.class, prisoner.getBoundingBox().inflate(32),
                x -> x != prisoner && x.isAlive() && !x.isPrisoner())) {
            boolean escort = leaderId != null && (leaderId.equals(e.getUUID()) || leaderId.equals(e.getLeader()));
            if (!escort) continue;
            captors = e.role().faction;
            e.setTarget(player);
        }
        if (captors != null) Reputation.add(player, captors, -8);
        // the freed prisoner runs for it
        double dx = prisoner.getX() - player.getX();
        double dz = prisoner.getZ() - player.getZ();
        double len = Math.max(1, Math.sqrt(dx * dx + dz * dz));
        prisoner.setDestination(net.minecraft.core.BlockPos.containing(prisoner.getX() + dx / len * 60, prisoner.getY(), prisoner.getZ() + dz / len * 60));
        Dialogue.open(player, prisoner, say("freed"));
    }

    private static boolean isNearInn(LivingEntity npc) {
        return !npc.level().getEntitiesOfClass(LivingEntity.class, npc.getBoundingBox().inflate(32),
                e -> e != npc && com.skycraft.survival.inn.Innkeepers.isInnkeeper(e)).isEmpty();
    }
}

