package com.skycraft.registry;

import com.skycraft.Skycraft;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Shared status effects. */
public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, Skycraft.MODID);

    /** +10% skill XP after sleeping in a bed (Skyrim "Well Rested"). */
    public static final RegistryObject<MobEffect> WELL_RESTED = EFFECTS.register("well_rested",
            () -> new SimpleEffect(MobEffectCategory.BENEFICIAL, 0xE8D9A0));
    /** +15% skill XP after sleeping next to your spouse. */
    public static final RegistryObject<MobEffect> LOVERS_COMFORT = EFFECTS.register("lovers_comfort",
            () -> new SimpleEffect(MobEffectCategory.BENEFICIAL, 0xF08AB0));
    /** Cannot move or attack. */
    public static final RegistryObject<MobEffect> PARALYSIS = EFFECTS.register("paralysis",
            () -> new SimpleEffect(MobEffectCategory.HARMFUL, 0x6A6A6A)
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, "3c1b7c7e-7d5e-4b4d-9a54-7c1f0b5a8e01", -1.0, AttributeModifier.Operation.MULTIPLY_TOTAL)
                    .addAttributeModifier(Attributes.ATTACK_SPEED, "3c1b7c7e-7d5e-4b4d-9a54-7c1f0b5a8e02", -1.0, AttributeModifier.Operation.MULTIPLY_TOTAL));
    /** Staggered: briefly slowed and unable to attack (power attacks, shouts). */
    public static final RegistryObject<MobEffect> STAGGER = EFFECTS.register("stagger",
            () -> new SimpleEffect(MobEffectCategory.HARMFUL, 0xA0A0A0)
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, "3c1b7c7e-7d5e-4b4d-9a54-7c1f0b5a8e03", -0.6, AttributeModifier.Operation.MULTIPLY_TOTAL));
    /** Bleeding from axe perks: handled as a damage-over-time by the combat handler. */
    public static final RegistryObject<MobEffect> BLEEDING = EFFECTS.register("bleeding",
            () -> new SimpleEffect(MobEffectCategory.HARMFUL, 0x8A0303));

    private ModEffects() {}

    public static void init(IEventBus modBus) {
        EFFECTS.register(modBus);
    }

    public static class SimpleEffect extends MobEffect {
        public SimpleEffect(MobEffectCategory category, int color) {
            super(category, color);
        }

        @Override
        public void applyEffectTick(net.minecraft.world.entity.LivingEntity entity, int amplifier) {
            if (this == BLEEDING.get()) {
                entity.hurt(entity.damageSources().magic(), 0.5f * (amplifier + 1));
            }
        }

        @Override
        public boolean isDurationEffectTick(int duration, int amplifier) {
            return this == BLEEDING.get() && duration % 20 == 0;
        }
    }
}
