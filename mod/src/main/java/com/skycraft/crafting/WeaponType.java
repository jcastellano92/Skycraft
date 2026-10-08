package com.skycraft.crafting;

/**
 * Skyrim weapon families. {@code damage} is added to the tier's damage bonus (+1 player base): an Iron Dagger deals 4,
 * an Iron Sword 6, an Iron Warhammer 11. {@code speed} is the attack speed modifier (player base 4.0).
 */
public enum WeaponType {
    DAGGER("dagger", 1, -1.6f, 0.4f, 0.8f, 1, 1),
    SWORD("sword", 3, -2.4f, 1.0f, 1.0f, 2, 1),
    WAR_AXE("war_axe", 4, -2.7f, 1.2f, 1.0f, 2, 1),
    MACE("mace", 5, -2.8f, 1.4f, 1.1f, 3, 1),
    GREATSWORD("greatsword", 6, -3.0f, 1.8f, 1.25f, 4, 2),
    BATTLEAXE("battleaxe", 7, -3.1f, 2.0f, 1.25f, 4, 2),
    WARHAMMER("warhammer", 8, -3.3f, 2.2f, 1.4f, 5, 2),
    BOW("bow", 0, 0f, 1.9f, 1.0f, 2, 1);

    public final String id;
    public final int damage;
    public final float speed;
    public final float valueMult;
    public final float durabilityMult;
    /** Main material ingots needed at the forge. */
    public final int materialCount;
    /** Leather strips needed at the forge. */
    public final int strips;

    WeaponType(String id, int damage, float speed, float valueMult, float durabilityMult, int materialCount, int strips) {
        this.id = id;
        this.damage = damage;
        this.speed = speed;
        this.valueMult = valueMult;
        this.durabilityMult = durabilityMult;
        this.materialCount = materialCount;
        this.strips = strips;
    }

    public boolean twoHanded() {
        return this == GREATSWORD || this == BATTLEAXE || this == WARHAMMER;
    }

    public boolean axe() {
        return this == WAR_AXE || this == BATTLEAXE;
    }

    public String itemId(SmithingTier tier) {
        return tier.id + "_" + id;
    }
}
