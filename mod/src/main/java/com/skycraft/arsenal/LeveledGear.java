package com.skycraft.arsenal;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.skycraft.Skycraft;
import com.skycraft.arsenal.item.ArrowKind;
import com.skycraft.core.SkyData;
import com.skycraft.crafting.CraftingItems;
import com.skycraft.crafting.SkyArmorMaterial;
import com.skycraft.crafting.SmithingTier;
import com.skycraft.crafting.Tempering;
import com.skycraft.crafting.WeaponType;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.util.Locale;

/**
 * Skyrim leveled lists. The loot function {@code skycraft:leveled_gear} replaces the rolled item with a Skyrim
 * weapon, armor piece, bow or arrows whose material follows the looting player's character level:
 *
 * <pre>{"function": "skycraft:leveled_gear", "kind": "weapon", "enchant_chance": 0.15, "quality_chance": 0.2}</pre>
 *
 * <p>kind is {@code weapon}, {@code armor}, {@code bow} or {@code arrow} (arrows keep the incoming stack count).
 * The player is the context's THIS_ENTITY if it's a player (chests opened by a player, including Lootr's per-player
 * rolls), else the killer / last damaging player, else the nearest player within 64 blocks of the origin, else
 * level 1. Material bands (approximate Skyrim):</p>
 * <pre>
 *  level  1-5   iron        | iron / hide
 *  level  6-11  steel       | steel / leather
 *  level 12-18  orcish/elven| dwarven / elven
 *  level 19-26  dwarven/elven| orcish / elven
 *  level 27-35  glass/dwarven| orcish / glass
 *  level 36-45  ebony/glass | ebony / glass
 *  level 46+    daedric/dragonbone | daedric, dragonplate / dragonscale
 * </pre>
 * Like Skyrim's lists, lower bands still appear: 60% current band, 30% one below, 10% two below.
 */
public class LeveledGear extends LootItemConditionalFunction {
    public static final DeferredRegister<LootItemFunctionType> FUNCTIONS = DeferredRegister.create(Registries.LOOT_FUNCTION_TYPE, Skycraft.MODID);
    public static final RegistryObject<LootItemFunctionType> LEVELED_GEAR = FUNCTIONS.register("leveled_gear",
            () -> new LootItemFunctionType(new Serializer()));

    public enum Kind { WEAPON, ARMOR, BOW, ARROW }

    private static final int[] BAND_START = {1, 6, 12, 19, 27, 36, 46};

    private final Kind kind;
    private final float enchantChance;
    private final float qualityChance;

    protected LeveledGear(LootItemCondition[] conditions, Kind kind, float enchantChance, float qualityChance) {
        super(conditions);
        this.kind = kind;
        this.enchantChance = enchantChance;
        this.qualityChance = qualityChance;
    }

    @Override
    public LootItemFunctionType getType() {
        return LEVELED_GEAR.get();
    }

    @Override
    protected ItemStack run(ItemStack stack, LootContext context) {
        int level = levelOf(context);
        RandomSource random = context.getRandom();
        ItemStack out = roll(kind, level, random);
        if (out.isEmpty()) return stack;
        if (kind == Kind.ARROW) {
            out.setCount(Math.max(1, Math.min(out.getMaxStackSize(), stack.getCount() > 1 ? stack.getCount() : 4 + random.nextInt(9))));
            return out;
        }
        decorate(out, level, random, enchantChance, qualityChance);
        return out;
    }

    // ------------------------------------------------------------------ level lookup

    /** Character level of the player this loot is for (see class doc). */
    public static int levelOf(LootContext context) {
        Player player = null;
        Entity self = context.getParamOrNull(LootContextParams.THIS_ENTITY);
        if (self instanceof Player p) player = p;
        if (player == null && context.getParamOrNull(LootContextParams.KILLER_ENTITY) instanceof Player p) player = p;
        if (player == null) player = context.getParamOrNull(LootContextParams.LAST_DAMAGE_PLAYER);
        if (player == null) {
            Vec3 origin = context.getParamOrNull(LootContextParams.ORIGIN);
            if (origin == null && self != null) origin = self.position();
            if (origin != null) player = context.getLevel().getNearestPlayer(origin.x, origin.y, origin.z, 64, false);
        }
        return player == null ? 1 : Math.max(1, SkyData.get(player).getLevel());
    }

    // ------------------------------------------------------------------ rolls

    /** Skyrim leveled-list band 0..6 for a character level. */
    public static int band(int level) {
        int band = 0;
        for (int i = 0; i < BAND_START.length; i++) {
            if (level >= BAND_START[i]) band = i;
        }
        return band;
    }

    /** The current band most of the time, sometimes one or two lower. */
    public static int rollBand(int level, RandomSource random) {
        int band = band(level);
        float f = random.nextFloat();
        if (f < 0.10f) band -= 2;
        else if (f < 0.40f) band -= 1;
        return Math.max(0, band);
    }

    public static ItemStack roll(Kind kind, int level, RandomSource random) {
        return switch (kind) {
            case WEAPON -> weapon(level, random);
            case ARMOR -> armor(level, random);
            case BOW -> bow(level, random);
            case ARROW -> arrows(level, random);
        };
    }

    public static SmithingTier weaponTier(int band, RandomSource random) {
        boolean alt = random.nextBoolean();
        return switch (band) {
            case 0 -> SmithingTier.IRON;
            case 1 -> SmithingTier.STEEL;
            case 2 -> alt ? SmithingTier.ORCISH : SmithingTier.ELVEN;
            case 3 -> alt ? SmithingTier.DWARVEN : SmithingTier.ELVEN;
            case 4 -> alt ? SmithingTier.GLASS : SmithingTier.DWARVEN;
            case 5 -> alt ? SmithingTier.EBONY : SmithingTier.GLASS;
            default -> alt ? SmithingTier.DAEDRIC : SmithingTier.DRAGONBONE;
        };
    }

    private static final WeaponType[] MELEE = {WeaponType.DAGGER, WeaponType.SWORD, WeaponType.WAR_AXE, WeaponType.MACE,
            WeaponType.GREATSWORD, WeaponType.BATTLEAXE, WeaponType.WARHAMMER};
    private static final int[] MELEE_WEIGHT = {15, 25, 16, 12, 12, 10, 10};

    public static ItemStack weapon(int level, RandomSource random) {
        SmithingTier tier = weaponTier(rollBand(level, random), random);
        int total = 0;
        for (int w : MELEE_WEIGHT) total += w;
        int pick = random.nextInt(total);
        WeaponType type = MELEE[0];
        for (int i = 0; i < MELEE.length; i++) {
            pick -= MELEE_WEIGHT[i];
            if (pick < 0) {
                type = MELEE[i];
                break;
            }
        }
        return new ItemStack(CraftingItems.weaponItem(tier, type));
    }

    public static ItemStack bow(int level, RandomSource random) {
        SmithingTier tier = weaponTier(rollBand(level, random), random);
        return new ItemStack(CraftingItems.weaponItem(tier, WeaponType.BOW));
    }

    public static ItemStack armor(int level, RandomSource random) {
        int band = rollBand(level, random);
        ArmorItem.Type piece = CraftingItems.ARMOR_TYPES[random.nextInt(CraftingItems.ARMOR_TYPES.length)];
        boolean heavy = random.nextBoolean();
        if (band == 0) {
            return new ItemStack(heavy ? vanilla(piece, Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS)
                    : CraftingItems.armor(SkyArmorMaterial.HIDE, piece));
        }
        if (band == 1 && !heavy) {
            return new ItemStack(vanilla(piece, Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS));
        }
        SkyArmorMaterial mat = switch (band) {
            case 1 -> SkyArmorMaterial.STEEL;
            case 2 -> heavy ? SkyArmorMaterial.DWARVEN : SkyArmorMaterial.ELVEN;
            case 3 -> heavy ? SkyArmorMaterial.ORCISH : SkyArmorMaterial.ELVEN;
            case 4 -> heavy ? SkyArmorMaterial.ORCISH : SkyArmorMaterial.GLASS;
            case 5 -> heavy ? SkyArmorMaterial.EBONY : SkyArmorMaterial.GLASS;
            default -> heavy ? (random.nextBoolean() ? SkyArmorMaterial.DAEDRIC : SkyArmorMaterial.DRAGONPLATE) : SkyArmorMaterial.DRAGONSCALE;
        };
        return new ItemStack(CraftingItems.armor(mat, piece));
    }

    private static Item vanilla(ArmorItem.Type piece, Item helmet, Item chest, Item legs, Item boots) {
        return switch (piece) {
            case HELMET -> helmet;
            case CHESTPLATE -> chest;
            case LEGGINGS -> legs;
            default -> boots;
        };
    }

    public static ItemStack arrows(int level, RandomSource random) {
        int band = rollBand(level, random);
        boolean alt = random.nextBoolean();
        ArrowKind kind = switch (band) {
            case 0 -> ArrowKind.IRON;
            case 1 -> ArrowKind.STEEL;
            case 2 -> ArrowKind.ORCISH;
            case 3 -> ArrowKind.DWARVEN;
            case 4 -> alt ? ArrowKind.ELVEN : ArrowKind.GLASS;
            case 5 -> alt ? ArrowKind.GLASS : ArrowKind.EBONY;
            default -> alt ? ArrowKind.DAEDRIC : ArrowKind.DRAGONBONE;
        };
        if (band >= 2 && random.nextFloat() < 0.08f) {
            kind = switch (random.nextInt(3)) {
                case 0 -> ArrowKind.FIRE;
                case 1 -> ArrowKind.FROST;
                default -> ArrowKind.SHOCK;
            };
        }
        return new ItemStack(ArsenalItems.arrow(kind), 4 + random.nextInt(9));
    }

    /** Optional enchantment (scales with level) and tempering quality ({@code skycraft_quality}). */
    public static void decorate(ItemStack stack, int level, RandomSource random, float enchantChance, float qualityChance) {
        if (enchantChance > 0 && random.nextFloat() < enchantChance && stack.isEnchantable()) {
            int power = Math.min(30, 5 + level / 2 + random.nextInt(6));
            EnchantmentHelper.enchantItem(random, stack, power, false);
        }
        if (qualityChance > 0 && random.nextFloat() < qualityChance && Tempering.kind(stack) != Tempering.Kind.NONE) {
            int q = 1 + random.nextInt(Math.max(1, Math.min(5, 1 + level / 12)));
            Tempering.setQuality(stack, Math.min(5, q));
        }
    }

    // ------------------------------------------------------------------ JSON

    public static class Serializer extends LootItemConditionalFunction.Serializer<LeveledGear> {
        @Override
        public void serialize(JsonObject json, LeveledGear fn, JsonSerializationContext ctx) {
            super.serialize(json, fn, ctx);
            json.addProperty("kind", fn.kind.name().toLowerCase(Locale.ROOT));
            if (fn.enchantChance > 0) json.addProperty("enchant_chance", fn.enchantChance);
            if (fn.qualityChance > 0) json.addProperty("quality_chance", fn.qualityChance);
        }

        @Override
        public LeveledGear deserialize(JsonObject json, JsonDeserializationContext ctx, LootItemCondition[] conditions) {
            String kindName = GsonHelper.getAsString(json, "kind", "weapon").toUpperCase(Locale.ROOT);
            Kind kind;
            try {
                kind = Kind.valueOf(kindName);
            } catch (IllegalArgumentException e) {
                throw new com.google.gson.JsonSyntaxException("Unknown leveled_gear kind '" + kindName.toLowerCase(Locale.ROOT)
                        + "' (expected weapon, armor, bow or arrow)");
            }
            float enchant = GsonHelper.getAsFloat(json, "enchant_chance", 0f);
            float quality = GsonHelper.getAsFloat(json, "quality_chance", 0f);
            return new LeveledGear(conditions, kind, enchant, quality);
        }
    }
}
