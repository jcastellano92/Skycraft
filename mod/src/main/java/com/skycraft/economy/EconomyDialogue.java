package com.skycraft.economy;

import com.skycraft.core.Currency;
import com.skycraft.core.SkyData;
import com.skycraft.core.Skill;
import com.skycraft.dialogue.Dialogue;
import com.skycraft.dialogue.DialogueOption;
import com.skycraft.perk.Perks;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.VillagerProfession;

import java.util.List;

/** The economy's conversation topics: barter, invest, training and bulk selling of harvested goods. */
public final class EconomyDialogue {
    private EconomyDialogue() {}

    public static void register() {
        Dialogue.registerProvider(EconomyDialogue::addOptions);
    }

    private static void addOptions(ServerPlayer player, LivingEntity npc, List<DialogueOption> out) {
        if (Merchants.isMerchant(npc)) {
            out.add(new DialogueOption("economy.barter", Component.translatable("dialogue.skycraft.economy.barter"), 100, Barter::open));

            VillagerProfession p = Merchants.profession(npc);
            if (p == VillagerProfession.FARMER && Barter.hasAny(player, ShopType.CROPS)) {
                out.add(new DialogueOption("economy.sell_crops", Component.translatable("dialogue.skycraft.economy.sell_crops"), 110,
                        (pl, n) -> Barter.bulkSell(pl, n, ShopType.CROPS)));
            }
            if ((p == VillagerProfession.BUTCHER || p == VillagerProfession.LEATHERWORKER) && Barter.hasAny(player, ShopType.PELTS)) {
                out.add(new DialogueOption("economy.sell_pelts", Component.translatable("dialogue.skycraft.economy.sell_pelts"), 110,
                        (pl, n) -> Barter.bulkSell(pl, n, ShopType.PELTS)));
            }
            if ((p == VillagerProfession.WEAPONSMITH || p == VillagerProfession.ARMORER || p == VillagerProfession.TOOLSMITH)
                    && Barter.hasAny(player, ShopType.ORES)) {
                out.add(new DialogueOption("economy.sell_ore", Component.translatable("dialogue.skycraft.economy.sell_ore"), 110,
                        (pl, n) -> Barter.bulkSell(pl, n, ShopType.ORES)));
            }

            if (Perks.has(player, "speech.investor") && !Shop.of(npc).invested()) {
                out.add(new DialogueOption("economy.invest", Component.translatable("dialogue.skycraft.economy.invest", Shop.INVEST_COST), 150,
                        EconomyDialogue::invest));
            }
        }

        Skill skill = Trainers.skillFor(npc);
        if (skill != null) {
            int cost = Trainers.cost(SkyData.get(player).getSkill(skill));
            out.add(new DialogueOption("economy.train", Component.translatable("dialogue.skycraft.economy.train", skill.displayName(), cost), 200,
                    Trainers::train));
        }
    }

    private static void invest(ServerPlayer player, LivingEntity npc) {
        if (!Merchants.isMerchant(npc) || !Barter.canReach(player, npc) || !Perks.has(player, "speech.investor")) return;
        Shop shop = Shop.of(npc);
        if (shop.invested()) return;
        if (!Currency.take(player, Shop.INVEST_COST)) {
            Dialogue.open(player, npc, Component.translatable("dialogue.skycraft.economy.invest.no_gold"));
            return;
        }
        shop.invest();
        shop.save();
        SkyData.get(player).addStat("shops_invested", 1);
        Dialogue.open(player, npc, Component.translatable("dialogue.skycraft.economy.invest.done"));
    }
}
