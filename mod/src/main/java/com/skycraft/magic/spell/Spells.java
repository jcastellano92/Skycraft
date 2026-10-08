package com.skycraft.magic.spell;

import com.skycraft.magic.bound.BoundWeapons;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every spell in the game, Skyrim names and base costs. Damage is Skyrim damage divided by 5.
 * Spell ids are stable: they are saved in player data ({@code magic.spells}) and used by the tome item ids.
 */
public final class Spells {
    private static final Map<String, Spell> BY_ID = new LinkedHashMap<>();
    private static final Map<School, List<Spell>> BY_SCHOOL = new EnumMap<>(School.class);

    private Spells() {}

    @Nullable
    public static Spell byId(String id) {
        return id == null ? null : BY_ID.get(id);
    }

    public static Collection<Spell> all() {
        return Collections.unmodifiableCollection(BY_ID.values());
    }

    public static List<Spell> of(School school) {
        return BY_SCHOOL.getOrDefault(school, List.of());
    }

    private static void add(Spell.Builder builder) {
        Spell spell = builder.build();
        BY_ID.put(spell.id, spell);
        BY_SCHOOL.computeIfAbsent(spell.school, k -> new ArrayList<>()).add(spell);
    }

    private static Spell.Builder s(String id, School school, Tier tier) {
        return Spell.builder(id, school, tier);
    }

    static {
        School D = School.DESTRUCTION, R = School.RESTORATION, A = School.ALTERATION, C = School.CONJURATION, I = School.ILLUSION;
        Tier NOV = Tier.NOVICE, APP = Tier.APPRENTICE, ADE = Tier.ADEPT, EXP = Tier.EXPERT, MAS = Tier.MASTER;

        // ------------------------------------------------------------------ Destruction
        add(s("flames", D, NOV).conc(14).mag(1.6f).radius(12).element(Element.FIRE).hostile().action(SpellEffects::beam));
        add(s("frostbite", D, NOV).conc(13).mag(1.6f).radius(12).element(Element.FROST).hostile().action(SpellEffects::beam));
        add(s("sparks", D, NOV).conc(16).mag(1.6f).radius(12).element(Element.SHOCK).hostile().action(SpellEffects::beam));
        add(s("firebolt", D, APP).ff(41).mag(5).element(Element.FIRE).hostile().projectile(2.0f, SpellEffects::elementalHit));
        add(s("ice_spike", D, APP).ff(48).mag(5).element(Element.FROST).hostile().projectile(2.2f, SpellEffects::elementalHit));
        add(s("lightning_bolt", D, APP).ff(61).mag(5).element(Element.SHOCK).hostile().projectile(4.0f, SpellEffects::elementalHit));
        add(s("fireball", D, ADE).ff(133).mag(8).radius(4).element(Element.FIRE).hostile().projectile(1.6f, SpellEffects::explosion));
        add(s("ice_storm", D, ADE).ff(117).mag(8).radius(2.5f).seconds(4).element(Element.FROST).hostile()
                .projectile(0.45f, SpellEffects::stormEnd).flight(SpellEffects::stormFlight).piercing());
        add(s("chain_lightning", D, ADE).ff(140).mag(8).radius(7).element(Element.SHOCK).hostile().projectile(4.0f, SpellEffects::chainLightning));
        add(s("incinerate", D, EXP).ff(451).mag(12).element(Element.FIRE).hostile().projectile(3.0f, SpellEffects::elementalHit));
        add(s("icy_spear", D, EXP).ff(480).mag(12).element(Element.FROST).hostile().projectile(3.0f, SpellEffects::elementalHit));
        add(s("thunderbolt", D, EXP).ff(534).mag(12).element(Element.SHOCK).hostile().projectile(5.0f, SpellEffects::elementalHit));
        add(s("fire_storm", D, MAS).ff(1426).mag(20).radius(10).charge(40).element(Element.FIRE).hostile().action(SpellEffects::fireStorm));
        add(s("blizzard", D, MAS).ff(1438).mag(5).radius(9).seconds(12).charge(40).element(Element.FROST).hostile().action(SpellEffects::blizzard));
        add(s("lightning_storm", D, MAS).ff(1290).mag(30).radius(4.5f).charge(40).element(Element.SHOCK).hostile().action(SpellEffects::lightningStorm));

        // ------------------------------------------------------------------ Restoration
        add(s("healing", R, NOV).conc(12).mag(2f).element(Element.HOLY).action(SpellEffects::healingConcentration));
        add(s("lesser_ward", R, NOV).conc(34).mag(8f).element(Element.HOLY).action(SpellEffects::ward));
        add(s("turn_lesser_undead", R, NOV).ff(84).seconds(30).element(Element.HOLY).projectile(2.0f, Illusion::turnUndeadHit));
        add(s("fast_healing", R, APP).ff(68).mag(10f).element(Element.HOLY).action(SpellEffects::healSelf));
        add(s("heal_other", R, APP).conc(26).mag(3f).radius(16).element(Element.HOLY).action(SpellEffects::healOther));
        add(s("close_wounds", R, ADE).ff(207).mag(20f).element(Element.HOLY).action(SpellEffects::healSelf));
        add(s("grand_healing", R, EXP).ff(287).mag(40f).radius(8).element(Element.HOLY).action(SpellEffects::grandHealing));
        add(s("guardian_circle", R, MAS).ff(1043).mag(4f).radius(6).seconds(30).charge(40).element(Element.HOLY).action(SpellEffects::guardianCircle));

        // ------------------------------------------------------------------ Alteration
        add(s("candlelight", A, NOV).ff(21).seconds(60).element(Element.ARCANE).action(Candlelight::cast));
        add(s("oakflesh", A, NOV).ff(98).mag(4).seconds(60).element(Element.NATURE).action(SpellEffects::flesh));
        add(s("stoneflesh", A, APP).ff(194).mag(6).seconds(60).element(Element.NATURE).action(SpellEffects::flesh));
        add(s("detect_life", A, APP).ff(114).radius(48).seconds(15).element(Element.ARCANE).action(SpellEffects::detectLife));
        add(s("waterbreathing", A, APP).ff(134).seconds(60).element(Element.ARCANE).action(SpellEffects::waterbreathing));
        add(s("ironflesh", A, ADE).ff(286).mag(8).seconds(60).element(Element.NATURE).action(SpellEffects::flesh));
        add(s("transmute", A, ADE).ff(142).element(Element.ARCANE).action(SpellEffects::transmute));
        add(s("ebonyflesh", A, EXP).ff(335).mag(10).seconds(60).element(Element.NATURE).action(SpellEffects::flesh));
        add(s("paralyze", A, EXP).ff(444).seconds(10).element(Element.ARCANE).hostile().projectile(2.5f, SpellEffects::paralyzeHit));
        add(s("mass_paralysis", A, MAS).ff(1100).seconds(15).radius(6).charge(40).element(Element.ARCANE).hostile()
                .projectile(2.0f, SpellEffects::massParalysisHit));

        // ------------------------------------------------------------------ Conjuration
        add(s("conjure_familiar", C, NOV).ff(105).seconds(60).element(Element.CONJURE).action(Summons::familiar));
        add(s("bound_sword", C, NOV).ff(105).seconds(120).element(Element.CONJURE).action(BoundWeapons::sword));
        add(s("raise_zombie", C, NOV).ff(80).seconds(60).element(Element.SOUL).action(Summons::zombie));
        add(s("soul_trap", C, NOV).ff(98).seconds(60).element(Element.SOUL).hostile().projectile(2.0f, SpellEffects::soulTrapHit));
        add(s("conjure_flame_atronach", C, APP).ff(122).seconds(60).element(Element.FIRE).action(Summons::flameAtronach));
        add(s("bound_bow", C, APP).ff(145).seconds(120).element(Element.CONJURE).action(BoundWeapons::bow));
        add(s("conjure_frost_atronach", C, ADE).ff(158).seconds(60).element(Element.FROST).action(Summons::frostAtronach));
        add(s("bound_battleaxe", C, ADE).ff(140).seconds(120).element(Element.CONJURE).action(BoundWeapons::battleaxe));
        add(s("conjure_storm_atronach", C, EXP).ff(223).seconds(60).element(Element.SHOCK).action(Summons::stormAtronach));
        add(s("conjure_dremora_lord", C, MAS).ff(600).seconds(60).charge(40).element(Element.CONJURE).action(Summons::dremoraLord));

        // ------------------------------------------------------------------ Illusion
        add(s("fury", I, NOV).ff(47).seconds(30).element(Element.MIND).projectile(2.0f, Illusion::furyHit));
        add(s("courage", I, NOV).ff(39).seconds(60).radius(24).element(Element.MIND).action(Illusion::courage));
        add(s("calm", I, APP).ff(85).seconds(30).element(Element.MIND).projectile(2.0f, Illusion::calmHit));
        add(s("fear", I, APP).ff(95).seconds(30).element(Element.MIND).projectile(2.0f, Illusion::fearHit));
        add(s("muffle", I, APP).ff(144).seconds(180).element(Element.MIND).action(Illusion::muffle));
        add(s("pacify", I, ADE).ff(166).seconds(60).radius(7).element(Element.MIND).projectile(1.8f, Illusion::pacifyHit));
        add(s("rally", I, ADE).ff(140).seconds(60).radius(12).element(Element.MIND).action(Illusion::rally));
        add(s("frenzy", I, ADE).ff(165).seconds(60).radius(7).element(Element.MIND).projectile(1.8f, Illusion::frenzyHit));
        add(s("invisibility", I, EXP).ff(334).seconds(30).element(Element.MIND).action(Illusion::invisibility));
        add(s("harmony", I, MAS).ff(1078).seconds(60).radius(24).charge(40).element(Element.MIND).action(Illusion::harmony));
        add(s("mayhem", I, MAS).ff(1146).seconds(60).radius(24).charge(40).element(Element.MIND).action(Illusion::mayhem));
    }
}
