package com.skycraft.magic.spell;

import com.skycraft.magic.MagicRegistry;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * Status effects of the magic module. Most are markers whose behaviour lives in {@link com.skycraft.magic.MagicEvents}
 * and {@link Illusion}; Marked for Death also drains health.
 */
public class MagicEffect extends MobEffect {
    public MagicEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level().isClientSide) return;
        if (this == MagicRegistry.MARKED_FOR_DEATH.get() && entity.getHealth() > 1f) {
            // Skyrim: 1 point of health per second per word (here 0.2 health/s per level, every 2 seconds)
            entity.hurt(entity.damageSources().magic(), 0.4f * (amplifier + 1));
        }
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return this == MagicRegistry.MARKED_FOR_DEATH.get() && duration % 40 == 0;
    }
}
