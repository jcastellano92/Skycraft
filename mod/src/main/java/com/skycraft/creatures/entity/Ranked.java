package com.skycraft.creatures.entity;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** A creature with Skyrim-style leveled rank names ("Bandit Thug", "Draugr Wight"...). */
public interface Ranked {
    /** The rank name for a creature of this level, or null to keep the plain type name. */
    @Nullable
    Component rankName(int level);
}
