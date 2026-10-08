package com.skycraft.combat;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.skycraft.arsenal.LeveledGear;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/** Loot function that generates a Fallout-4 style randomized legendary item (Contract 4). */
public class LegendaryItemFunction extends LootItemConditionalFunction {
    protected LegendaryItemFunction(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    public LootItemFunctionType getType() {
        return LeveledGear.LEGENDARY_ITEM.get();
    }

    @Override
    protected ItemStack run(ItemStack stack, LootContext context) {
        return LegendaryItem.generate(context);
    }

    public static class Serializer extends LootItemConditionalFunction.Serializer<LegendaryItemFunction> {
        @Override
        public void serialize(JsonObject json, LegendaryItemFunction fn, JsonSerializationContext ctx) {
            super.serialize(json, fn, ctx);
        }

        @Override
        public LegendaryItemFunction deserialize(JsonObject json, JsonDeserializationContext ctx, LootItemCondition[] conditions) {
            return new LegendaryItemFunction(conditions);
        }
    }
}

