package com.skycraft.creatures;

import com.skycraft.Skycraft;
import com.skycraft.creatures.entity.CorpseEntity;
import com.skycraft.creatures.entity.DragonEntity;
import com.skycraft.creatures.entity.SkeeverEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Skyrim "loot body": creatures killed by a player keep their drops on a {@link CorpseEntity} instead of
 * scattering them. Dragons hand their corpse to the dragon, which leaves it after its death animation.
 */
@Mod.EventBusSubscriber(modid = Skycraft.MODID)
public final class CorpseEvents {
    /** The vanilla death animation (falling over) lasts 20 ticks; the body appears right after. */
    private static final int APPEAR_DELAY = 19;

    private CorpseEvents() {}

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDrops(LivingDropsEvent event) {
        if (event.isCanceled()) return;
        LivingEntity dead = event.getEntity();
        if (!(dead.level() instanceof ServerLevel level) || !CreaturesConfig.CORPSES.get()) return;
        if (!(dead instanceof Mob) || dead instanceof Slime) return;
        if (dead.getType().is(CreatureTags.NO_CORPSE)) return;
        if (dead.getBbHeight() < 0.35f && !(dead instanceof SkeeverEntity)) return;
        if (!(event.getSource().getEntity() instanceof Player)) return;
        if (event.getDrops().isEmpty()) return;

        CorpseEntity corpse = ModEntities.CORPSE.get().create(level);
        if (corpse == null) return;
        List<ItemEntity> stored = new ArrayList<>();
        for (ItemEntity drop : event.getDrops()) {
            ItemStack rest = corpse.addLoot(drop.getItem().copy());
            if (rest.isEmpty()) {
                stored.add(drop);
            } else {
                drop.setItem(rest);
            }
        }
        if (corpse.isEmptyOfLoot()) return;
        event.getDrops().removeAll(stored);
        corpse.setBody(dead);

        if (dead instanceof DragonEntity dragon) {
            dragon.setPendingCorpse(corpse);
            return;
        }

        // Center the body where it will lie (it falls over sideways, like the vanilla death animation).
        float yaw = dead.yBodyRot;
        double a = Math.toRadians(180.0 - yaw);
        double half = dead.getBbHeight() * 0.5;
        Vec3 center = dead.position().add(-Mth.cos((float) a) * half, 0, Mth.sin((float) a) * half);
        corpse.moveTo(center.x, dead.getY(), center.z, yaw, 0);
        if (!level.noCollision(corpse)) {
            corpse.moveTo(dead.getX(), dead.getY(), dead.getZ(), yaw, 0);
            corpse.setCentered(false);
        }
        corpse.setAppearDelay(APPEAR_DELAY);
        level.addFreshEntity(corpse);
    }
}
