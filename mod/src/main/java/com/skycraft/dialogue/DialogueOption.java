package com.skycraft.dialogue;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.function.BiConsumer;

/**
 * One line the player can choose in an NPC conversation.
 *
 * @param id      unique id within the conversation, e.g. {@code "economy.barter"}
 * @param label   what the player says, e.g. "What have you got for sale?"
 * @param order   sort order (lower first); Goodbye is always last
 * @param action  server-side effect when chosen. The dialogue screen closes first unless the action reopens it.
 */
public record DialogueOption(String id, Component label, int order, BiConsumer<ServerPlayer, LivingEntity> action) {
}
