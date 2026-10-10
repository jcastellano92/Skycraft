package com.skycraft.loot;

import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.skycraft.Skycraft;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

/**
 * Global loot modifier {@code skycraft:add_table}: rolls an extra loot table into every loot table whose id path
 * starts with {@code prefix} (default {@code "chests/"}), with the given chance. Modules use it to put Skyrim loot
 * (gold, spell tomes, soul gems, ingredients, gear) into dungeon and village chests, including modded structures.
 *
 * <pre>{ "type": "skycraft:add_table", "conditions": [], "table": "skycraft:chests/gold", "prefix": "chests/", "chance": 0.8 }</pre>
 */
public class AddTableModifier extends LootModifier {
    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, Skycraft.MODID);

    public static final Supplier<Codec<AddTableModifier>> CODEC = Suppliers.memoize(() -> RecordCodecBuilder.create(inst -> codecStart(inst)
            .and(ResourceLocation.CODEC.fieldOf("table").forGetter(m -> m.table))
            .and(Codec.STRING.optionalFieldOf("prefix", "chests/").forGetter(m -> m.prefix))
            .and(Codec.FLOAT.optionalFieldOf("chance", 1f).forGetter(m -> m.chance))
            .apply(inst, AddTableModifier::new)));

    public static final RegistryObject<Codec<AddTableModifier>> ADD_TABLE = SERIALIZERS.register("add_table", CODEC);

    private final ResourceLocation table;
    private final String prefix;
    private final float chance;

    public AddTableModifier(LootItemCondition[] conditions, ResourceLocation table, String prefix, float chance) {
        super(conditions);
        this.table = table;
        this.prefix = prefix;
        this.chance = chance;
    }

    public static void init(IEventBus modBus) {
        SERIALIZERS.register(modBus);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        ResourceLocation id = context.getQueriedLootTableId();
        if (id == null || id.equals(table) || !id.getPath().startsWith(prefix)) return generatedLoot;

        sanitize(generatedLoot, id);

        if (id.getNamespace().equals(Skycraft.MODID) && id.getPath().startsWith("chests/")) {
            addDungeonLore(generatedLoot, id, context);
            return generatedLoot;
        }
        if (context.getRandom().nextFloat() > chance) return generatedLoot;
        LootTable extra = context.getResolver().getLootTable(table);
        extra.getRandomItemsRaw(context, generatedLoot::add);

        addDungeonLore(generatedLoot, id, context);
        sanitize(generatedLoot, id);
        return generatedLoot;
    }

    private static void addDungeonLore(ObjectArrayList<ItemStack> loot, ResourceLocation id, LootContext context) {
        String path = id.getPath();
        if (path.contains("dungeon") || path.contains("stronghold") || path.contains("pyramid")
                || path.contains("temple") || path.contains("ruin") || path.contains("fort")) {
            if (context.getRandom().nextFloat() < 0.45f) {
                com.skycraft.lore.LoreBooks.Book b = com.skycraft.lore.LoreBooks.random(context.getRandom(), book -> true);
                if (b != null) {
                    loot.add(com.skycraft.lore.LoreBookItem.create(b));
                }
            }
        }
    }

    private static void sanitize(ObjectArrayList<ItemStack> loot, ResourceLocation id) {
        loot.removeIf(stack -> {
            Item item = stack.getItem();
            if (item == Items.REDSTONE || item == Items.LAPIS_LAZULI || item == Items.EMERALD
                    || item == Items.MAP || item == Items.FILLED_MAP || item == Items.COMPASS) {
                return true;
            }
            if (id.getPath().contains("village") || id.getPath().contains("farm")) {
                ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
                if (key != null && key.getNamespace().equals(Skycraft.MODID)) {
                    String p = key.getPath();
                    if (p.startsWith("elven_") || p.startsWith("glass_") || p.startsWith("ebony_")
                            || p.startsWith("daedric_") || p.startsWith("dragon") || p.startsWith("orcish_")) {
                        return true;
                    }
                }
            }
            return false;
        });
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC.get();
    }
}
