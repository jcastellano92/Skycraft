package com.skycraft.quest.party;

import com.skycraft.Skycraft;
import com.skycraft.quest.QuestConfig;
import com.skycraft.quest.Quests;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** No friendly fire between party members (or their pets and summons) unless enabled in the config. */
@Mod.EventBusSubscriber(modid = Skycraft.MODID)
public final class PartyEvents {
    private PartyEvents() {}

    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer target) || QuestConfig.FRIENDLY_FIRE.get()) return;
        ServerPlayer attacker = Quests.asPlayer(target.server, event.getSource().getEntity());
        if (attacker == null || attacker == target) return;
        if (Parties.sameParty(target.server, attacker, target)) event.setCanceled(true);
    }
}
