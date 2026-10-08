package com.skycraft.crafting;

import com.skycraft.Skycraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/** Forge-bus hooks of the smithing module: tempering bonuses and animal hides. */
@Mod.EventBusSubscriber(modid = Skycraft.MODID)
public final class CraftingEvents {
    private static final UUID WEAPON_UUID = UUID.fromString("5b7d3a2e-1c4f-4e8b-9a61-2f0d8c3e7a10");
    private static final UUID[] ARMOR_UUIDS = {
            UUID.fromString("5b7d3a2e-1c4f-4e8b-9a61-2f0d8c3e7a11"), // feet
            UUID.fromString("5b7d3a2e-1c4f-4e8b-9a61-2f0d8c3e7a12"), // legs
            UUID.fromString("5b7d3a2e-1c4f-4e8b-9a61-2f0d8c3e7a13"), // chest
            UUID.fromString("5b7d3a2e-1c4f-4e8b-9a61-2f0d8c3e7a14")  // head
    };
    private static final String ARROW_FLAG = "skycraft_tempered_arrow";
    /** Animals that drop Skyrim hide when killed by a player. */
    public static final TagKey<EntityType<?>> DROPS_HIDE = TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(Skycraft.MODID, "drops_hide"));

    private CraftingEvents() {}

    /** Tempered quality: +0.5 attack damage per level (weapon in main hand), +0.5 armor per level (armor in its slot). */
    @SubscribeEvent
    public static void onAttributes(ItemAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        int quality = Tempering.quality(stack);
        if (quality <= 0) return;
        EquipmentSlot slot = event.getSlotType();
        if (stack.getItem() instanceof ArmorItem armor) {
            if (slot == armor.getEquipmentSlot() && slot.getType() == EquipmentSlot.Type.ARMOR) {
                event.addModifier(Attributes.ARMOR, new AttributeModifier(ARMOR_UUIDS[slot.getIndex()], "Skycraft tempering",
                        0.5 * quality, AttributeModifier.Operation.ADDITION));
            }
        } else if (slot == EquipmentSlot.MAINHAND && Tempering.kind(stack) == Tempering.Kind.WEAPON
                && !(stack.getItem() instanceof ProjectileWeaponItem)) {
            event.addModifier(Attributes.ATTACK_DAMAGE, new AttributeModifier(WEAPON_UUID, "Skycraft tempering",
                    0.5 * quality, AttributeModifier.Operation.ADDITION));
        }
    }

    /** Tempered bows and crossbows shoot harder arrows (+0.15 base damage per quality level, ~+0.45 at full draw). */
    @SubscribeEvent
    public static void onArrow(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof AbstractArrow arrow)) return;
        if (arrow.getPersistentData().getBoolean(ARROW_FLAG)) return;
        arrow.getPersistentData().putBoolean(ARROW_FLAG, true);
        if (!(arrow.getOwner() instanceof LivingEntity owner)) return;
        ItemStack weapon = owner.getUseItem();
        if (!(weapon.getItem() instanceof ProjectileWeaponItem)) weapon = owner.getMainHandItem();
        if (!(weapon.getItem() instanceof ProjectileWeaponItem)) weapon = owner.getOffhandItem();
        int quality = Tempering.quality(weapon);
        if (quality > 0) arrow.setBaseDamage(arrow.getBaseDamage() + 0.15 * quality);
    }

    /** Skyrim animals leave hides: player kills of {@code #skycraft:drops_hide} creatures drop Hide. */
    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(event.getSource().getEntity() instanceof Player) || victim.isBaby() || !victim.getType().is(DROPS_HIDE)) return;
        var random = victim.getRandom();
        if (random.nextFloat() >= 0.6f + 0.1f * event.getLootingLevel()) return;
        event.getDrops().add(new ItemEntity(victim.level(), victim.getX(), victim.getY() + 0.5, victim.getZ(),
                new ItemStack(CraftingItems.HIDE.get())));
    }
}
