package com.skycraft.fauna;

import com.skycraft.core.Currency;
import com.skycraft.core.Holds;
import com.skycraft.core.Notifier;
import com.skycraft.core.SkyData;
import com.skycraft.dialogue.Dialogue;
import com.skycraft.dialogue.DialogueOption;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

import java.util.List;

/**
 * Skyrim stables: stable masters in holds and settlements sell horses of different breeds and stats.
 */
public final class Stables {
    private Stables() {}

    public static void register() {
        Dialogue.registerProvider(Stables::addOptions);
    }

    public static boolean isStableMaster(LivingEntity npc) {
        if (npc == null || !npc.isAlive()) return false;
        if (npc instanceof Villager v && !v.isBaby()) {
            VillagerProfession p = v.getVillagerData().getProfession();
            if (p == VillagerProfession.LEATHERWORKER || p == VillagerProfession.SHEPHERD || p == VillagerProfession.FARMER) {
                return true;
            }
        }
        return npc.getType().getDescriptionId().contains("stable") || npc.getPersistentData().getBoolean("skycraft_stable_master");
    }

    private static void addOptions(ServerPlayer player, LivingEntity npc, List<DialogueOption> out) {
        if (!isStableMaster(npc)) return;

        String hold = Holds.holdAt(player.level(), npc.blockPosition());
        HorseBreed breed = HorseBreed.breedForHold(hold);

        out.add(new DialogueOption(
                "fauna.buy_horse",
                Component.translatable("dialogue.skycraft.stables.buy_horse", breed.displayName(), breed.cost),
                180,
                (pl, n) -> buyHorse(pl, n, breed)
        ));
    }

    private static void buyHorse(ServerPlayer player, LivingEntity npc, HorseBreed breed) {
        if (!Currency.take(player, breed.cost)) {
            Dialogue.open(player, npc, Component.translatable("dialogue.skycraft.stables.no_gold", breed.cost));
            return;
        }

        ServerLevel level = player.serverLevel();
        BlockPos spawnPos = npc.blockPosition().relative(npc.getDirection(), 2);
        if (!level.getBlockState(spawnPos).isAir()) spawnPos = player.blockPosition();

        Horse horse = EntityType.HORSE.create(level);
        if (horse != null) {
            horse.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, player.getYRot(), 0.0f);
            breed.apply(horse);
            horse.tameWithName(player);
            horse.equipSaddle(SoundSource.NEUTRAL);

            // Mark player ownership & skycraft owner
            horse.getPersistentData().putString("skycraft_owner", "player:" + player.getStringUUID());
            horse.getPersistentData().putBoolean(Livestock.PLAYER_OWNED, true);

            level.addFreshEntity(horse);

            // Register as player's active horse
            HorseManager.setPlayerHorse(player, horse, breed);

            level.playSound(null, spawnPos, SoundEvents.HORSE_SADDLE, SoundSource.NEUTRAL, 1.0f, 1.0f);
            Notifier.message(player, Component.translatable("fauna.skycraft.horse.bought", breed.displayName()));
            Dialogue.open(player, npc, Component.translatable("dialogue.skycraft.stables.bought", breed.displayName()));
        }
    }
}

