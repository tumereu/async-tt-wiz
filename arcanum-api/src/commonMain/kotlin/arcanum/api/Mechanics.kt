package arcanum.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** How several instances of the same mechanic on one object combine. */
@Serializable
sealed interface Stacking {
    /** A yes/no rule: more **identical** instances change nothing (lifelink, CR 702.15f). Different instances of the
     * same mechanic all apply: protection from red and from blue (CR 702.16m). */
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
    /** Becomes a copy of [objects] (CR 707), with [except] as part of the copy (CR 707.9b). */
    @Serializable @SerialName("copy-of") data class CopyOf(val objects: Objects, val except: List<CopyException> = emptyList()) : Mechanic {
        override val layer: Layer get() = Layer.Copy
        override val stacking: Stacking get() = Stacking.Independent
    }
    @Serializable @SerialName("controlled-by") data class ControlledBy(val players: Players) : Mechanic {
        override val layer: Layer get() = Layer.Control
        override val stacking: Stacking get() = Stacking.Independent
    }
    /** Add or replace types. [replaceSubtypes]: the new subtypes replace the old ones **of the same kind** only
     * ("becomes a Zombie" replaces creature types, not land types, CR 205.1a). Setting a land's subtype to a basic land
     * type also removes all abilities from its rules text and old land types (CR 305.7, Blood Moon); the engine does
     * that for any such effect. */
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
    /** Gains [abilities] (layer 6). Inside a granted ability, "this creature"/[Objects.Self] is the object that has
     * it, not the source granting it, and that object's controller activates it (CR 113.10, 602.1). */
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
    /** "Has base power and toughness N/N" (layer 7b); in a characteristic-defining static ability ("~'s power is equal
     * to …", CR 604.3) it applies in layer 7a instead. */
    @Serializable @SerialName("set-pt") data class SetPowerToughness(val power: Value?, val toughness: Value?) : Mechanic {
        override val layer: Layer get() = Layer.PowerToughnessSet
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
     * "trample over planeswalkers" (CR 702.19c). */
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
     * by them). For an ability, [from] is matched against its source (CR 702.16b); a card type means permanents of
     * that type (702.16a). "Protection from red and from blue" is two keywords (702.16g). */
    @Serializable @SerialName("protection") data class Protection(val from: ObjectFilter) : KeywordOnly
    /** Can't be the target of spells matching [by], or of abilities whose source matches [by] (hexproof: controlled by
     * opponents; shroud: any; hexproof from a quality, CR 702.11d). */
    @Serializable @SerialName("cant-be-targeted") data class CantBeTargeted(val by: ObjectFilter) : Mechanic
    @Serializable @SerialName("phasing") data object Phasing : KeywordOnly
    /** What this Aura can target as a spell and be attached to (enchant, CR 702.5, 303.4). */
    @Serializable @SerialName("can-enchant") data class CanEnchant(val what: TargetKind) : KeywordOnly
    /** What this Equipment or Fortification can be attached to besides the default (CR 301.5). */
    @Serializable @SerialName("can-attach-to") data class CanAttachTo(val what: ObjectFilter) : Mechanic

    // ---- Combat restrictions and requirements (CR 508, 509) ----
    @Serializable @SerialName("cant-attack") data class CantAttack(val target: Recipients? = null) : Mechanic
    @Serializable @SerialName("cant-block") data class CantBlock(val attackers: ObjectFilter = ObjectFilter.Any) : Mechanic
    /** "Can't be blocked" (landwalk with its condition). */
    @Serializable @SerialName("cant-be-blocked") data object CantBeBlocked : Mechanic
    /** Can be blocked only by creatures matching [blockers] (flying: creatures with flying or reach; fear;
     * intimidate). */
    @Serializable @SerialName("blocked-only-by") data class CanBeBlockedOnlyBy(val blockers: ObjectFilter) : Mechanic
    /** Can't be blocked by creatures matching [blockers] ("can't be blocked by Walls"). */
    @Serializable @SerialName("cant-be-blocked-by") data class CantBeBlockedBy(val blockers: ObjectFilter) : Mechanic
    /** Can't be blocked except by [count] or more creatures (menace: 2, CR 702.111b). */
    @Serializable @SerialName("minimum-blockers") data class MinimumBlockers(val count: Int) : Mechanic
    @Serializable @SerialName("can-block-only") data class CanBlockOnly(val attackers: ObjectFilter) : Mechanic
    /** Can't be blocked by more than [count] creatures. */
    @Serializable @SerialName("maximum-blockers") data class MaximumBlockers(val count: Int) : Mechanic
    /** Can attack as though it didn't have defender. */
    @Serializable @SerialName("attacks-despite-defender") data object CanAttackDespiteDefender : Mechanic
    /** "Creatures can't attack [target] unless their controller pays [cost] for each" (CR 508.1g). */
    @Serializable @SerialName("attack-cost") data class AttackCost(val cost: Cost, val target: Recipients? = null) : Mechanic {
        override val stacking: Stacking get() = Stacking.Independent
    }
    /** Can block [count] additional creatures each combat. */
    @Serializable @SerialName("can-block-additional") data class CanBlockAdditional(val count: Int) : Mechanic {
        override val stacking: Stacking get() = Stacking.Additive
    }
    @Serializable @SerialName("can-block-any-number") data object CanBlockAnyNumber : Mechanic
    @Serializable @SerialName("must-attack") data class MustAttack(val target: Recipients? = null) : Mechanic
    @Serializable @SerialName("must-block") data class MustBlock(val attackers: ObjectFilter = ObjectFilter.Any) : Mechanic
    /** Assigns combat damage equal to its toughness rather than its power (CR 510.1). */
    @Serializable @SerialName("damage-by-toughness") data object AssignsDamageByToughness : Mechanic
    /** May assign its combat damage as though it weren't blocked (CR 510.1c). */
    @Serializable @SerialName("damage-as-unblocked") data object AssignsDamageAsThoughUnblocked : Mechanic
    /** Assigns no combat damage. */
    @Serializable @SerialName("no-combat-damage") data object AssignsNoCombatDamage : Mechanic

    // ---- Permissions, costs and casting (CR 601, 117, 118) ----
    /** May be cast (or activated) at any time it could cast an instant (flash, CR 702.8). */
    @Serializable @SerialName("instant-speed") data object InstantSpeed : Mechanic
    /**
     * The permission to play or cast cards from [from] ("you may cast ~ from your graveyard", "until end of turn, you may
     * play those cards", "you may look at and play the top card of your library"). [how]: play (lands too, using a land
     * play, CR 305.1) or cast (spells only, CR 601). [cards] limits which ("creature spells", "if the resulting spell is
     * an instant or sorcery", CR 702.34a); [topOnly]: only the top card of a library; [cost] replaces the mana cost
     * (flashback, escape); [then] is where it goes when it leaves the Stack (flashback: exile); [manaAsAnyColor]:
     * mana may be spent as though it were mana of any colour for them. The permission belongs to "you" (the ability's
     * or effect's controller), also for cards in an opponent's library or exile. The affected cards are [Static.affects]
     * or the effect's affected objects; for a static ability on the card itself, the card.
     */
    @Serializable @SerialName("may-play") data class MayPlay(
        val from: Zone, val how: PlayKind = PlayKind.Cast, val cards: ObjectFilter? = null, val topOnly: Boolean = false,
        val cost: Cost? = null, val then: Destination? = null, val timing: Timing? = null, val manaAsAnyColor: Boolean = false,
    ) : Mechanic
    /** "You may pay [cost] rather than pay this spell's mana cost" (CR 118.9). [faceDown]: cast face down as a 2/2 for
     * {3} (morph, disguise, CR 702.37c); the keyword's own cost is paid in the special action that turns it face up. */
    @Serializable @SerialName("alternative-cost") data class AlternativeCost(
        @Serializable(with = KeywordKindSerializer::class) val keyword: KeywordKind? = null,
        val cost: Cost, val faceDown: Boolean = false,
    ) : Mechanic
    /** "As an additional cost to cast this spell, …" (CR 601.2f); [optional]/[repeatable] for kicker/multikicker. */
    @Serializable @SerialName("additional-cost") data class AdditionalCost(
        @Serializable(with = KeywordKindSerializer::class) val keyword: KeywordKind? = null,
        val cost: Cost, val optional: Boolean = false, val repeatable: Boolean = false,
    ) : Mechanic
    /** Spells or abilities matching [what] cost [change] generic mana more (positive) or less (negative), or [mana]
     * less ("costs {W}{B} less", coloured reductions); abilities by keyword with [ObjectFilter.HasKeyword] ("equip
     * abilities cost {1} less"). */
    @Serializable @SerialName("cost-change") data class CostChange(
        val what: ObjectFilter, val change: Value = Value.Fixed(0), val mana: ManaCost? = null, val minimum: Int = 0,
    ) : Mechanic {
        override val stacking: Stacking get() = Stacking.Independent
    }
    @Serializable @SerialName("cant-be-countered") data object CantBeCountered : Mechanic
    /** Activated abilities of the affected objects can't be activated; [manaAbilities] false keeps mana abilities
     * ("unless they're mana abilities"). */
    @Serializable @SerialName("cant-be-activated") data class CantBeActivated(val manaAbilities: Boolean = true) : Mechanic
    /** The affected permanents can't be regenerated: regeneration shields don't apply to them (CR 701.19c). */
    @Serializable @SerialName("cant-be-regenerated") data object CantBeRegenerated : Mechanic
    @Serializable @SerialName("cast-restriction") data class CastRestriction(val timing: Timing? = null, val condition: Condition? = null) : Mechanic
    @Serializable @SerialName("extra-land-plays") data class ExtraLandPlays(val count: Int) : Mechanic {
        override val stacking: Stacking get() = Stacking.Additive
    }
    /** The affected players may spend mana as though it were mana of any colour (for everything they pay). */
    @Serializable @SerialName("spend-mana-as-any-color") data object SpendManaAsAnyColor : Mechanic
    /** May look at [objects] ("you may look at the top card of your library any time"); [reveal]: it's revealed to all
     * instead ("play with the top card of your library revealed"). */
    @Serializable @SerialName("may-look-at") data class MayLookAt(val objects: Objects, val reveal: Boolean = false) : Mechanic
    /** Damage matching [event] can't be prevented (CR 615.12); replacement effects still apply. */
    @Serializable @SerialName("damage-cant-be-prevented") data class DamageCantBePrevented(val event: EventPattern) : Mechanic

    // ---- Player and game rules ----
    @Serializable @SerialName("cant-gain-life") data object CantGainLife : Mechanic
    @Serializable @SerialName("cant-lose-game") data object CantLoseGame : Mechanic
    @Serializable @SerialName("no-maximum-hand-size") data object NoMaximumHandSize : Mechanic
    @Serializable @SerialName("maximum-hand-size") data class MaximumHandSizeChange(val change: Int) : Mechanic {
        override val stacking: Stacking get() = Stacking.Additive
    }
    @Serializable @SerialName("skip-step") data class SkipStep(val step: TurnStep) : Mechanic
    /** "If [event] causes a triggered ability of [sources] to trigger, it triggers an additional time" (CR 603.2d). */
    @Serializable @SerialName("triggers-additional-time") data class TriggersAdditionalTime(
        val event: EventPattern, val sources: ObjectFilter = ObjectFilter.Any,
    ) : Mechanic {
        override val stacking: Stacking get() = Stacking.Independent
    }
    @Serializable @SerialName("doesnt-untap") data object DoesntUntap : Mechanic

    // ---- Replacement and prevention (CR 614, 615) ----
    /**
     * "If [event] would happen, [instead]" (CR 614). [modify] changes the event instead of replacing it entirely
     * (enters tapped, enters with counters, double the amount). [optional] for "you may". A spell's or ability's
     * replacement of its own effects goes in [Instructions.replacements] (CR 614.15), not here.
     *
     * When several apply to one event (CR 616.1), the affected player (or controller of the affected object) chooses
     * one, it is applied, and the rest are checked again (616.1f): self-replacements are chosen among first, then
     * those that set who controls an entering permanent, then [Modification.EntersAsCopy], then entering transformed,
     * then any other. A shield that is used up ("the next time …", regeneration) is created by an effect with a
     * count ([Effect.ApplyMechanics.uses]); the count lives in the effect object, not here.
     */
    @Serializable @SerialName("replacement") data class Replacement(
        val event: EventPattern,
        val instead: Instructions? = null,
        val modify: List<Modification> = emptyList(),
        val optional: Boolean = false,
    ) : Mechanic {
        override val stacking: Stacking get() = Stacking.Independent
    }
    /** Prevent all of the damage matching [event], or [each] of each such damage event ("prevent 1 of that damage",
     * CR 615). Shields with an amount that gets used up are [Effect.PreventDamage]. */
    @Serializable @SerialName("prevention") data class Prevention(val event: EventPattern, val each: Value? = null) : Mechanic {
        override val stacking: Stacking get() = Stacking.Independent
    }
}

/**
 * An exception to a copy effect ("except it's a 1/1 Bird", "except it isn't legendary"). It becomes part of the copy's
 * copiable values (CR 707.9b), applied in layer 1, so copies of the copy carry it. Not a [Mechanic]: no later layer.
 */
@Serializable
sealed interface CopyException {
    @Serializable @SerialName("name") data class Name(val name: String) : CopyException
    @Serializable @SerialName("types") data class Types(
        val add: List<CardType> = emptyList(), val addSubtypes: List<Subtype> = emptyList(),
        val addSupertypes: List<Supertype> = emptyList(), val removeSupertypes: List<Supertype> = emptyList(),
        /** Replaces the creature types ("except it's a Bird"). */
        val replaceSubtypes: Boolean = false,
    ) : CopyException
    @Serializable @SerialName("colors") data class Colors(val colors: List<Color>) : CopyException
    @Serializable @SerialName("pt") data class PowerToughness(val power: Int, val toughness: Int) : CopyException
    @Serializable @SerialName("abilities") data class AddAbilities(val abilities: List<Ability>) : CopyException
    @Serializable @SerialName("mana-cost") data class ManaCostOf(val cost: ManaCost?) : CopyException
}

/** Whether a permission lets a card be played (lands included) or only cast. */
@Serializable
sealed interface PlayKind {
    @Serializable @SerialName("play") data object Play : PlayKind
    @Serializable @SerialName("cast") data object Cast : PlayKind
}

/** How a replacement effect changes an event (CR 614.1). */
@Serializable
sealed interface Modification {
    @Serializable @SerialName("destination") data class ChangeDestination(val to: Destination) : Modification
    @Serializable @SerialName("enters-tapped") data object EntersTapped : Modification
    @Serializable @SerialName("enters-with-counters") data class EntersWithCounters(val kind: CounterKind, val amount: Value) : Modification
    @Serializable @SerialName("enters-as-copy") data class EntersAsCopy(val of: ObjectFilter, val except: List<CopyException> = emptyList(), val optional: Boolean = true) : Modification
    @Serializable @SerialName("multiply") data class MultiplyAmount(val factor: Int) : Modification
    @Serializable @SerialName("add") data class AddAmount(val amount: Value) : Modification
    /** "As ~ enters, …" (CR 614.12): [steps] happen as part of entering, not on the Stack: pay ("you may pay 2 life. If
     * you don't, it enters tapped"), choose ("choose a creature type"). Effects on [Objects.Self] apply to it as it
     * enters. */
    @Serializable @SerialName("as-enters") data class AsEnters(val steps: List<Step>) : Modification
    /** The damage is dealt to [to] instead ("is dealt to this creature instead"). */
    @Serializable @SerialName("redirect") data class ChangeRecipient(val to: Recipients) : Modification
    /** "Enters with [abilities]" ("If kicked, it enters with … and with trample"): part of the entering object. */
    @Serializable @SerialName("enters-with-abilities") data class EntersWithAbilities(val abilities: List<Ability>) : Modification
    /** The event doesn't happen at all ("If you would draw a card, skip that draw instead"). Skipping a step is
     * [Mechanic.SkipStep]; preventing damage is [Mechanic.Prevention] or [Effect.PreventDamage] (CR 615, not 614). */
    @Serializable @SerialName("skip") data object Skip : Modification
}
