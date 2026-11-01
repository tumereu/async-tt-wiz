package arcanum.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** How several instances of the same mechanic on one object combine. */
@Serializable
sealed interface Stacking {
    /** A yes/no rule: more instances change nothing (lifelink, CR 702.15f). */
    @Serializable @SerialName("redundant") data object Redundant : Stacking
    /** Numbers add up (toxic's "total toxic value", CR 702.164b; extra land plays). */
    @Serializable @SerialName("additive") data object Additive : Stacking
    /** Each instance applies on its own, in timestamp order (two +1/+1 anthems give +2/+2; layer effects). */
    @Serializable @SerialName("independent") data object Independent : Stacking
}

/** Layers of continuous effects (CR 613.1); [Mechanic.layer] is null for mechanics that are not layer effects. */
@Serializable
sealed interface Layer {
    @Serializable @SerialName("1-copy") data object Copy : Layer
    @Serializable @SerialName("2-control") data object Control : Layer
    @Serializable @SerialName("3-text") data object Text : Layer
    @Serializable @SerialName("4-type") data object Type : Layer
    @Serializable @SerialName("5-color") data object Color : Layer
    @Serializable @SerialName("6-ability") data object Ability : Layer
    @Serializable @SerialName("7a-pt-defining") data object PowerToughnessDefining : Layer
    @Serializable @SerialName("7b-pt-set") data object PowerToughnessSet : Layer
    @Serializable @SerialName("7c-pt-modify") data object PowerToughnessModify : Layer
    @Serializable @SerialName("7d-pt-switch") data object PowerToughnessSwitch : Layer
}

/**
 * A continuous rule (CR 611): the engine's vocabulary for what static abilities and continuous effects do. Each
 * mechanic says which [layer] it applies in (if any) and how instances combine ([stacking]). Keywords map to
 * mechanics through the shared table: deathtouch to [Deathtouch] one to one, flying to the general [CantBeBlocked].
 *
 * Mechanics that are only the meaning of a keyword are [KeywordOnly]: they appear only in [KeywordExpansions], never
 * in a definition. "Has deathtouch" is a granted ability ([GainAbilities] with the keyword), so "loses all abilities"
 * removes it (CR 613.1f); other mechanics in a static ability or an effect are rules applied to an object that are
 * not abilities ("can't be blocked", CR 113.12). [DefinitionChecks] reports keyword-only mechanics in definitions.
 */
@Serializable
sealed interface Mechanic {
    val layer: Layer? get() = null
    val stacking: Stacking get() = Stacking.Redundant

    /** A mechanic that is only the meaning of a keyword; used only by [KeywordExpansions]. */
    sealed interface KeywordOnly : Mechanic

    // ---- Layer 1–7: characteristics (CR 613) ----
    @Serializable @SerialName("copy-of") data class CopyOf(val objects: Objects, val changes: List<Mechanic> = emptyList()) : Mechanic {
        override val layer: Layer get() = Layer.Copy
        override val stacking: Stacking get() = Stacking.Independent
    }
    @Serializable @SerialName("controlled-by") data class ControlledBy(val players: Players) : Mechanic {
        override val layer: Layer get() = Layer.Control
        override val stacking: Stacking get() = Stacking.Independent
    }
    /** Add or replace types. [replaceSubtypes]: "becomes a 2/2 Zombie" loses other creature types (CR 205.1b). */
    @Serializable @SerialName("types") data class SetTypes(
        val add: List<CardType> = emptyList(), val addSubtypes: List<Subtype> = emptyList(),
        val addSupertypes: List<Supertype> = emptyList(), val remove: List<CardType> = emptyList(),
        val replaceTypes: Boolean = false, val replaceSubtypes: Boolean = false,
    ) : Mechanic {
        override val layer: Layer get() = Layer.Type
        override val stacking: Stacking get() = Stacking.Independent
    }
    /** "Is every creature type" (changeling, CR 702.73). */
    @Serializable @SerialName("all-creature-types") data object AllCreatureTypes : Mechanic {
        override val layer: Layer get() = Layer.Type
    }
    @Serializable @SerialName("colors") data class SetColors(val colors: List<Color>, val add: Boolean = false) : Mechanic {
        override val layer: Layer get() = Layer.Color
        override val stacking: Stacking get() = Stacking.Independent
    }
    @Serializable @SerialName("gain-abilities") data class GainAbilities(val abilities: List<arcanum.api.Ability>) : Mechanic {
        override val layer: Layer get() = Layer.Ability
        override val stacking: Stacking get() = Stacking.Independent
    }
    /** Lose all abilities, or the keyword [keyword] ("loses flying"). */
    @Serializable @SerialName("lose-abilities") data class LoseAbilities(
        @Serializable(with = KeywordKindSerializer::class) val keyword: KeywordKind? = null,
    ) : Mechanic {
        override val layer: Layer get() = Layer.Ability
        override val stacking: Stacking get() = Stacking.Independent
    }
    @Serializable @SerialName("cant-have") data class CantHaveAbility(
        @Serializable(with = KeywordKindSerializer::class) val keyword: KeywordKind,
    ) : Mechanic {
        override val layer: Layer get() = Layer.Ability
    }
    /** Characteristic-defining P/T ("~'s power is equal to …", CR 604.3) or "has base P/T N/N" (layer 7b). */
    @Serializable @SerialName("set-pt") data class SetPowerToughness(val power: Value?, val toughness: Value?, val defining: Boolean = false) : Mechanic {
        override val layer: Layer get() = if (defining) Layer.PowerToughnessDefining else Layer.PowerToughnessSet
        override val stacking: Stacking get() = Stacking.Independent
    }
    @Serializable @SerialName("modify-pt") data class ModifyPowerToughness(val power: Value, val toughness: Value) : Mechanic {
        override val layer: Layer get() = Layer.PowerToughnessModify
        override val stacking: Stacking get() = Stacking.Independent
    }
    @Serializable @SerialName("switch-pt") data object SwitchPowerToughness : Mechanic {
        override val layer: Layer get() = Layer.PowerToughnessSwitch
        override val stacking: Stacking get() = Stacking.Independent
    }

    // ---- Rules the engine asks about (combat, damage, state-based actions) ----
    @Serializable @SerialName("deathtouch") data object Deathtouch : KeywordOnly
    @Serializable @SerialName("lifelink") data object Lifelink : KeywordOnly
    @Serializable @SerialName("first-strike") data object FirstStrike : KeywordOnly
    @Serializable @SerialName("double-strike") data object DoubleStrike : KeywordOnly
    /** Excess combat damage may go to the player or planeswalker it attacks (CR 702.19); [overPlaneswalkers] for
     * "trample over planeswalkers" (CR 702.19e). */
    @Serializable @SerialName("trample") data class Trample(val overPlaneswalkers: Boolean = false) : KeywordOnly
    @Serializable @SerialName("ignores-summoning-sickness") data object IgnoresSummoningSickness : Mechanic
    @Serializable @SerialName("attacks-without-tapping") data object AttacksWithoutTapping : Mechanic
    @Serializable @SerialName("indestructible") data object Indestructible : KeywordOnly
    @Serializable @SerialName("infect") data object Infect : KeywordOnly
    @Serializable @SerialName("wither") data object Wither : KeywordOnly
    @Serializable @SerialName("toxic") data class Toxic(val amount: Int) : KeywordOnly {
        override val stacking: Stacking get() = Stacking.Additive
    }
    /** Protection from objects matching [from] (CR 702.16: can't be damaged, enchanted/equipped, blocked or targeted
     * by them). */
    @Serializable @SerialName("protection") data class Protection(val from: ObjectFilter) : KeywordOnly
    /** Can't be the target of spells or abilities matching [by] (hexproof: controlled by opponents; shroud: any). */
    @Serializable @SerialName("cant-be-targeted") data class CantBeTargeted(val by: ObjectFilter) : Mechanic
    @Serializable @SerialName("phasing") data object Phasing : KeywordOnly
    /** What this Aura can target as a spell and be attached to (enchant, CR 702.5, 303.4). */
    @Serializable @SerialName("can-enchant") data class CanEnchant(val what: TargetKind) : KeywordOnly
    /** What this Equipment or Fortification can be attached to besides the default (CR 301.5). */
    @Serializable @SerialName("can-attach-to") data class CanAttachTo(val what: ObjectFilter) : Mechanic

    // ---- Combat restrictions and requirements (CR 508, 509) ----
    @Serializable @SerialName("cant-attack") data class CantAttack(val target: Recipients? = null) : Mechanic
    @Serializable @SerialName("cant-block") data class CantBlock(val attackers: ObjectFilter = ObjectFilter.Any) : Mechanic
    /**
     * Restricts blockers: can be blocked only by creatures matching [onlyBy] (flying: creatures with flying or reach),
     * never by those matching [notBy] (fear, intimidate), only by [minBlockers] or more (menace = 2), or not at all
     * (all null: "can't be blocked").
     */
    @Serializable @SerialName("cant-be-blocked") data class CantBeBlocked(
        val onlyBy: ObjectFilter? = null, val notBy: ObjectFilter? = null, val minBlockers: Int? = null,
    ) : Mechanic
    @Serializable @SerialName("can-block-only") data class CanBlockOnly(val attackers: ObjectFilter) : Mechanic
    @Serializable @SerialName("can-block-additional") data class CanBlockAdditional(val count: Int?) : Mechanic {
        override val stacking: Stacking get() = Stacking.Additive
    }
    @Serializable @SerialName("must-attack") data class MustAttack(val target: Recipients? = null) : Mechanic
    @Serializable @SerialName("must-block") data class MustBlock(val attackers: ObjectFilter = ObjectFilter.Any) : Mechanic
    /** Assigns combat damage as though it weren't blocked, or by toughness, … : named variants of damage rules. */
    @Serializable @SerialName("combat-damage-rule") data class CombatDamageRule(val rule: String) : Mechanic

    // ---- Permissions, costs and casting (CR 601, 117, 118) ----
    /** May be cast (or activated) at any time it could cast an instant (flash, CR 702.8). */
    @Serializable @SerialName("instant-speed") data object InstantSpeed : Mechanic
    /** May be cast or played from [zone] ("you may cast ~ from your graveyard"); [cost] replaces the mana cost when
     * given (flashback, escape, …); [then] is where it goes when it leaves the Stack (flashback: exile). */
    @Serializable @SerialName("may-cast-from") data class MayCastFrom(
        val zone: Zone, val cost: Cost? = null, val then: Destination? = null, val timing: Timing? = null,
    ) : Mechanic
    /** "You may pay [cost] rather than pay this spell's mana cost" (CR 118.9). [faceDown] for morph/disguise. */
    @Serializable @SerialName("alternative-cost") data class AlternativeCost(
        @Serializable(with = KeywordKindSerializer::class) val keyword: KeywordKind? = null,
        val cost: Cost, val faceDown: Boolean = false,
    ) : Mechanic
    /** "As an additional cost to cast this spell, …" (CR 601.2f); [optional]/[repeatable] for kicker/multikicker. */
    @Serializable @SerialName("additional-cost") data class AdditionalCost(
        @Serializable(with = KeywordKindSerializer::class) val keyword: KeywordKind? = null,
        val cost: Cost, val optional: Boolean = false, val repeatable: Boolean = false,
    ) : Mechanic
    /** Spells or abilities matching [what] cost [change] more (positive) or less (negative), generic mana. */
    @Serializable @SerialName("cost-change") data class CostChange(val what: ObjectFilter, val change: Value, val minimum: Int = 0) : Mechanic {
        override val stacking: Stacking get() = Stacking.Independent
    }
    @Serializable @SerialName("cant-be-countered") data object CantBeCountered : Mechanic
    @Serializable @SerialName("cast-restriction") data class CastRestriction(val timing: Timing? = null, val condition: Condition? = null) : Mechanic
    @Serializable @SerialName("extra-land-plays") data class ExtraLandPlays(val count: Int) : Mechanic {
        override val stacking: Stacking get() = Stacking.Additive
    }
    @Serializable @SerialName("spend-mana-as-any-color") data object SpendManaAsAnyColor : Mechanic
    @Serializable @SerialName("may-look-at") data class MayLookAt(val objects: Objects) : Mechanic
    @Serializable @SerialName("may-play-top-of-library") data class MayPlayTopOfLibrary(val reveal: Boolean = false) : Mechanic

    // ---- Player and game rules ----
    @Serializable @SerialName("cant-gain-life") data object CantGainLife : Mechanic
    @Serializable @SerialName("cant-lose-game") data object CantLoseGame : Mechanic
    @Serializable @SerialName("no-maximum-hand-size") data object NoMaximumHandSize : Mechanic
    @Serializable @SerialName("maximum-hand-size") data class MaximumHandSizeChange(val change: Int) : Mechanic {
        override val stacking: Stacking get() = Stacking.Additive
    }
    @Serializable @SerialName("skip-step") data class SkipStep(val step: TurnStep) : Mechanic
    @Serializable @SerialName("doesnt-untap") data object DoesntUntap : Mechanic

    // ---- Replacement and prevention (CR 614, 615) ----
    /**
     * "If [event] would happen, [instead]" (CR 614). [modify] changes the event instead of replacing it entirely
     * (enters tapped, enters with counters, double the amount). [optional] for "you may". [uses] limits a shield
     * (regeneration, "the next time …") to that many uses.
     */
    @Serializable @SerialName("replacement") data class Replacement(
        val event: EventPattern,
        val instead: Instructions? = null,
        val modify: List<Modification> = emptyList(),
        val optional: Boolean = false,
        val uses: Int? = null,
    ) : Mechanic {
        override val stacking: Stacking get() = Stacking.Independent
    }
    /** Prevent [amount] (null: all) of the damage matching [event] (CR 615). */
    @Serializable @SerialName("prevention") data class Prevention(val event: EventPattern, val amount: Value? = null, val uses: Int? = null) : Mechanic {
        override val stacking: Stacking get() = Stacking.Independent
    }
}

/** How a replacement effect changes an event (CR 614.1). */
@Serializable
sealed interface Modification {
    @Serializable @SerialName("destination") data class ChangeDestination(val to: Destination) : Modification
    @Serializable @SerialName("enters-tapped") data object EntersTapped : Modification
    @Serializable @SerialName("enters-with-counters") data class EntersWithCounters(val kind: CounterKind, val amount: Value) : Modification
    @Serializable @SerialName("enters-as-copy") data class EntersAsCopy(val of: ObjectFilter, val changes: List<Mechanic> = emptyList(), val optional: Boolean = true) : Modification
    @Serializable @SerialName("multiply") data class MultiplyAmount(val factor: Int) : Modification
    @Serializable @SerialName("add") data class AddAmount(val amount: Value) : Modification
    /** The event doesn't happen at all ("skip your draw", "prevent"). */
    @Serializable @SerialName("skip") data object Skip : Modification
}
