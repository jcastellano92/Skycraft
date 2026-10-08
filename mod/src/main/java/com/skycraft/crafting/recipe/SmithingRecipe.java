package com.skycraft.crafting.recipe;

import com.skycraft.crafting.StationType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/**
 * A station recipe (forge, smelter, tanning rack). {@code value} is the gold value of the whole result stack and is
 * the Smithing XP "use value" (Skyrim: smithing XP depends on the value of what you make).
 */
public record SmithingRecipe(String id, StationType station, String category, Supplier<? extends ItemLike> result, int count,
                             List<Cost> costs, @Nullable String perk, int value) {

    public ItemStack resultStack() {
        return new ItemStack(result.get(), count);
    }

    public boolean hasMaterials(Inventory inv) {
        for (Cost cost : costs) {
            if (cost.countIn(inv, -1) < cost.count()) return false;
        }
        return true;
    }

    public void consume(Inventory inv) {
        for (Cost cost : costs) cost.removeFrom(inv, cost.count(), -1);
    }
}
