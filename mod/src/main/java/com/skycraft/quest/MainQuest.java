package com.skycraft.quest;

import com.skycraft.core.Notifier;
import com.skycraft.core.PlayerData;
import com.skycraft.core.SkyData;
import com.skycraft.quest.QuestSpawner.Spawn;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;

import java.util.UUID;

/**
 * "The Dragonborn": the scripted main quest, started for every player once they chose a race.
 * <ol>
 *     <li>Unbound — reach the nearest village.</li>
 *     <li>Before the Storm — tell a guard you need to speak to the Jarl.</li>
 *     <li>Dragon Rising — slay the dragon attacking the watchtower.</li>
 *     <li>The Way of the Voice — learn a Word of Power.</li>
 *     <li>Alduin's Bane — enter Sovngarde (the End) and slay Alduin, World-Eater (the Ender Dragon).</li>
 * </ol>
 * The current stage is stored in {@code module("quest").main_stage} (0 = not started, 6 = finished).
 */
public final class MainQuest {
    public static final String STAGE = "main_stage";
    public static final int FINAL_STAGE = 5;

    private MainQuest() {}

    public static int stage(PlayerData data) {
        return data.module("quest").getInt(STAGE);
    }

    private static void setStage(ServerPlayer player, int stage) {
        PlayerData data = SkyData.get(player);
        data.module("quest").putInt(STAGE, stage);
        data.markDirty();
    }

    /** Called every few seconds per player: starts the main quest, renames Alduin. */
    public static void tick(ServerPlayer player) {
        if (!QuestConfig.MAIN_QUEST.get()) return;
        PlayerData data = SkyData.get(player);
        if (data.getRace() == null) return;
        int stage = stage(data);
        if (stage == 0) {
            startStage(player, 1);
            return;
        }
        if (stage >= 1 && stage <= FINAL_STAGE && !Quests.hasActiveKind(player.server, player.getUUID(), "main_" + stage)
                && !completedStage(player, stage)) {
            // the stage quest went missing (e.g. data reset): restart it
            startStage(player, stage);
        }
        if (stage == FINAL_STAGE && player.level().dimension() == Level.END && player.level() instanceof ServerLevel end) {
            for (EnderDragon dragon : end.getDragons()) {
                if (!dragon.hasCustomName()) dragon.setCustomName(Component.translatable("entity.skycraft.alduin"));
            }
        }
    }

    private static boolean completedStage(ServerPlayer player, int stage) {
        for (Quest q : QuestStore.get(player.server).quests(Quests.personalKey(player.getUUID()))) {
            if (q.kind.equals("main_" + stage) && q.status == Quest.Status.COMPLETED) return true;
        }
        return false;
    }

    private static Quest quest(int stage) {
        String kind = "main_" + stage;
        Quest q = new Quest(kind, Quest.Category.MAIN, Component.translatable("quest.skycraft." + kind + ".title"),
                Component.translatable("quest.skycraft." + kind + ".desc"));
        q.giverName = Component.translatable("quest.skycraft.main.giver");
        q.id = kind;
        return q;
    }

    public static void startStage(ServerPlayer player, int stage) {
        ServerLevel level = (ServerLevel) player.level();
        String dim = level.dimension().location().toString();
        BlockPos at = player.blockPosition();
        Quest q = quest(stage);
        q.hold = com.skycraft.core.Holds.holdAt(level, at);
        switch (stage) {
            case 1 -> {
                BlockPos village = level.dimension() == Level.OVERWORLD ? Locate.nearestVillage(level, at) : null;
                // Guided starter steps: basic gear through steps rather than a chest, ending at nearest town
                BlockPos p1 = at.offset(6, 0, 6);
                Objective o1 = new Objective(Objective.Type.GO_TO, Component.translatable("quest.skycraft.obj.main_1a"))
                        .at(p1, dim, 12).guessY().label("Camp Wreckage");
                o1.onComplete = "give:minecraft:leather_chestplate,give:minecraft:leather_boots";

                BlockPos p2 = village != null ? at.offset((village.getX() - at.getX()) * 1 / 8, 0, (village.getZ() - at.getZ()) * 1 / 8) : at.offset(14, 0, 14);
                Objective o2 = new Objective(Objective.Type.GO_TO, Component.translatable("quest.skycraft.obj.main_1b"))
                        .at(p2, dim, 14).guessY().label("Weapon Cache");
                o2.onComplete = "give:minecraft:iron_sword";

                BlockPos p3 = village != null ? at.offset((village.getX() - at.getX()) * 2 / 8, 0, (village.getZ() - at.getZ()) * 2 / 8) : at.offset(24, 0, 24);
                Objective o3 = new Objective(Objective.Type.GO_TO, Component.translatable("quest.skycraft.obj.main_1c"))
                        .at(p3, dim, 16).guessY().label("Traveler Cache");
                o3.onComplete = "give:minecraft:shield,give:skycraft:spell_tome_flames";

                BlockPos p4 = village != null ? at.offset((village.getX() - at.getX()) * 3 / 8, 0, (village.getZ() - at.getZ()) * 3 / 8) : at.offset(36, 0, 36);
                Objective o4 = new Objective(Objective.Type.GO_TO, Component.translatable("quest.skycraft.obj.main_1d"))
                        .at(p4, dim, 18).guessY().label("Provisions");
                o4.onComplete = "give:minecraft:bread*4,give:minecraft:cooked_beef*2";

                Objective o5 = new Objective(Objective.Type.GO_TO, Component.translatable("quest.skycraft.obj.main_1e"));
                if (village != null) {
                    o5.at(village, "minecraft:overworld", 48).guessY().label("Village");
                    q.extra.putLong("village", village.asLong());
                } else {
                    o5.target("near:villager");
                }

                q.add(o1).add(o2).add(o3).add(o4).add(o5).reward(100);
            }
            case 2 -> {
                Objective o = new Objective(Objective.Type.TALK_TO, Component.translatable("quest.skycraft.obj.main_2"))
                        .filter("guard").topic(Component.translatable("dialogue.skycraft.quest.jarl"),
                                Component.translatable("quest.skycraft.reply.jarl"));
                q.add(o).reward(150);
            }
            case 3 -> {
                BlockPos spot = Locate.randomSpot(level, player.getRandom(), at, 180, 230);
                String name = "Mirmulnir";
                q.description = Component.translatable("quest.skycraft.main_3.desc", Names.direction(at, spot));
                Objective o = new Objective(Objective.Type.KILL_TARGET, Component.translatable("quest.skycraft.obj.main_3"))
                        .at(spot, dim, 0).guessY().placement("surface").label("Western Watchtower")
                        .spawn(Spawn.of("skycraft:dragon", 1).name(Component.literal(name)).target().boss().build());
                ItemStack axe = new ItemStack(Items.IRON_AXE);
                axe.setHoverName(Component.translatable("item.skycraft.reward.axe_of_whiterun"));
                axe.enchant(Enchantments.SHARPNESS, 2);
                q.add(o).reward(600, axe);
            }
            case 4 -> {
                q.add(new Objective(Objective.Type.CONDITION, Component.translatable("quest.skycraft.obj.main_4")).target("word"));
                q.reward(300);
            }
            default -> {
                q.add(new Objective(Objective.Type.GO_TO, Component.translatable("quest.skycraft.obj.main_5a")).target("dimension")
                        .at(BlockPos.ZERO, "minecraft:the_end", 0));
                q.objectives.get(0).hasPos = false;
                q.add(new Objective(Objective.Type.KILL_TYPE_COUNT, Component.translatable("quest.skycraft.obj.main_5b"))
                        .target("minecraft:ender_dragon").count(1).at(new BlockPos(0, 70, 0), "minecraft:the_end", 0).label("Alduin"));
                ItemStack dragonbane = new ItemStack(Items.NETHERITE_SWORD);
                dragonbane.setHoverName(Component.translatable("item.skycraft.reward.dragonbane"));
                dragonbane.enchant(Enchantments.SHARPNESS, 5);
                q.reward(5000, dragonbane, new ItemStack(Items.ELYTRA));
            }
        }
        // stage quests are unique per player: drop a stale copy with the same id
        QuestStore.get(player.server).quests(Quests.personalKey(player.getUUID())).removeIf(old -> old.id.equals(q.id));
        setStage(player, stage);
        Quests.start(player, q);
    }

    /** A stage was completed: start the next one, or celebrate. */
    public static void onStageCompleted(MinecraftServer server, String key, Quest q) {
        int stage;
        try {
            stage = Integer.parseInt(q.kind.substring("main_".length()));
        } catch (NumberFormatException e) {
            return;
        }
        if (!key.startsWith("p:")) return;
        ServerPlayer player;
        try {
            player = server.getPlayerList().getPlayer(UUID.fromString(key.substring(2)));
        } catch (IllegalArgumentException e) {
            return;
        }
        if (player == null) return;
        if (stage >= FINAL_STAGE) {
            setStage(player, FINAL_STAGE + 1);
            PlayerData data = SkyData.get(player);
            CompoundTag mod = data.module("quest");
            mod.putString("title", "dragonborn");
            data.markDirty();
            Notifier.title(player, Component.translatable("quest.skycraft.main.victory_title"), Component.translatable("quest.skycraft.main.victory_sub"));
            player.server.getPlayerList().broadcastSystemMessage(
                    Component.translatable("quest.skycraft.main.victory_broadcast", player.getDisplayName()), false);
            return;
        }
        startStage(player, stage + 1);
    }
}
