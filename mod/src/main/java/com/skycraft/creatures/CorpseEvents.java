package com.skycraft.creatures;

import com.skycraft.Skycraft;
import com.skycraft.creatures.entity.CorpseEntity;
import com.skycraft.creatures.entity.DragonEntity;
import com.skycraft.creatures.entity.SkeeverEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
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

    /** Persistent-data key where the arsenal module records arrows stuck in a creature (contract 27). */
    public static final String ARROWS_KEY = "skycraft_arrows";

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDrops(LivingDropsEvent event) {
        if (event.isCanceled()) return;
        LivingEntity dead = event.getEntity();
        if (!(dead.level() instanceof ServerLevel level)) return;
        List<ItemStack> arrows = takeArrows(dead);

        boolean corpseAllowed = CreaturesConfig.CORPSES.get() && dead instanceof Mob && !(dead instanceof Slime)
                && !dead.getType().is(CreatureTags.NO_CORPSE)
                && (dead.getBbHeight() >= 0.35f || dead instanceof SkeeverEntity);
        if (!corpseAllowed) {
            for (ItemStack arrow : arrows) event.getDrops().add(new ItemEntity(level, dead.getX(), dead.getY() + 0.5, dead.getZ(), arrow));
            return;
        }

        CorpseEntity corpse = ModEntities.CORPSE.get().create(level);
        if (corpse == null) return;

        // Ensure all worn armor and weapons are placed in the corpse container
        corpse.populateEquipment(dead);

        List<ItemEntity> stored = new ArrayList<>();
        for (ItemEntity drop : event.getDrops()) {
            ItemStack rest = corpse.addLoot(drop.getItem().copy());
            if (rest.isEmpty()) {
                stored.add(drop);
            } else {
                drop.setItem(rest);
            }
        }
        event.getDrops().removeAll(stored);
        for (ItemStack arrow : arrows) {
            ItemStack rest = corpse.addLoot(arrow);
            if (!rest.isEmpty()) event.getDrops().add(new ItemEntity(level, dead.getX(), dead.getY() + 0.5, dead.getZ(), rest));
        }

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
        corpse.setAppearDelay(0);
        level.addFreshEntity(corpse);
        dead.discard();
    }

    /** Reads (and clears) the arrows the arsenal module recorded on the creature. */
    private static List<ItemStack> takeArrows(LivingEntity dead) {
        List<ItemStack> out = new ArrayList<>();
        CompoundTag data = dead.getPersistentData();
        if (!data.contains(ARROWS_KEY, Tag.TAG_LIST)) return out;
        ListTag list = data.getList(ARROWS_KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            ItemStack stack = ItemStack.of(list.getCompound(i));
            if (!stack.isEmpty()) out.add(stack);
        }
        data.remove(ARROWS_KEY);
        return out;
    }
}
