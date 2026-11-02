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
     * ("becomes a Zombie" replaces creature types, not land types, CR 205.1a). When new basic land types replace a
     * land's old land types, it also loses all abilities from its rules text and old land types (CR 305.7, Blood Moon);
     * gaining a land type "in addition to its other types" keeps them. */
    @Serializable @SerialName("types") data class SetTypes(
        val add: List<CardType> = emptyList(), val addSubtypes: List<SubtypeRef> = emptyList(),
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
    @Serializable @SerialName("colors") data class SetColors(val colors: List<ColorRef>, val add: Boolean = false) : Mechanic {
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
     * that type, and sources not on the battlefield of that type (702.16a). "Protection from red and from blue" is two
     * keywords (702.16g). */
    @Serializable @SerialName("protection") data class Protection(val from: ObjectFilter) : KeywordOnly
    /** Can't be the target of spells or abilities matching [by], matched against the spell or ability itself (its
     * controller is the ability's controller), whose source matches [fromSource] when given (the spell itself, or the
     * object an ability came from). Hexproof: by = controlled by an opponent (CR 702.11b); hexproof from a quality adds
     * fromSource = that quality (702.11d); shroud: by = any (702.18a). */
    @Serializable @SerialName("cant-be-targeted") data class CantBeTargeted(val by: ObjectFilter, val fromSource: ObjectFilter? = null) : Mechanic
    @Serializable @SerialName("phasing") data object Phasing : KeywordOnly
    /** The affected objects can be the targets of spells and abilities as though they didn't have hexproof (CR 702.11b): the
     * targeting restriction of their hexproof keywords doesn't apply; only for spells and abilities matching [by] when given
     * ("spells and abilities you control", gtc chunk 13). */
    @Serializable @SerialName("targetable-despite-hexproof") data class TargetableDespiteHexproof(val by: ObjectFilter? = null) : Mechanic
    /** The ward abilities of the affected objects don't trigger (CR 702.21a). */
    @Serializable @SerialName("ward-doesnt-trigger") data object WardDoesntTrigger : Mechanic
    /** What this Aura can target as a spell and be attached to (enchant, CR 702.5, 303.4). */
    @Serializable @SerialName("can-enchant") data class CanEnchant(val what: TargetKind) : KeywordOnly
    /** What this Equipment or Fortification can be attached to besides the default (CR 301.5). */
    @Serializable @SerialName("can-attach-to") data class CanAttachTo(val what: ObjectFilter) : Mechanic

    // ---- Combat restrictions and requirements (CR 508, 509) ----
    /** Can't attack ([target]); [fromDefender]: this restriction is defender's, the one "as though it didn't have
     * defender" lifts (CR 702.3b). */
    @Serializable @SerialName("cant-attack") data class CantAttack(val target: Recipients? = null, val fromDefender: Boolean = false) : Mechanic
    @Serializable @SerialName("cant-block") data class CantBlock(val attackers: ObjectFilter) : Mechanic
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
    /** "You may [cost] as this creature attacks. When you do, [then]" (exert, CR 701.43d, 508.1g): an optional cost paid
     * while declaring it as an attacker, with a reflexive trigger. */
    @Serializable @SerialName("as-attacks") data class AsAttacks(val cost: Cost, val then: Instructions? = null) : Mechanic
    /** No more than [count] creatures can attack [target] (or at all) each combat. */
    @Serializable @SerialName("maximum-attackers") data class MaximumAttackers(val count: Int, val target: Recipients? = null) : Mechanic
    /** "Creatures can't attack [target] unless their controller pays [cost] for each" (CR 508.1h). The static ability's
     * `affects` is the creatures restricted (as for [MaximumAttackers] and [CantAttack]); [target] what they attack. */
    @Serializable @SerialName("attack-cost") data class AttackCost(val cost: Cost, val target: Recipients? = null) : Mechanic {
        override val stacking: Stacking get() = Stacking.Independent
    }
    /** Can block [count] additional creatures each combat. */
    @Serializable @SerialName("can-block-additional") data class CanBlockAdditional(val count: Int) : Mechanic {
        override val stacking: Stacking get() = Stacking.Additive
    }
    @Serializable @SerialName("can-block-any-number") data object CanBlockAnyNumber : Mechanic
    /** Can't attack alone: it can be declared as an attacker only with another attacker (CR 506.5, 508.1c). */
    @Serializable @SerialName("cant-attack-alone") data object CantAttackAlone : Mechanic
    /** Can't block alone: it can be declared as a blocker only with another blocker (CR 506.5, 509.1b). */
    @Serializable @SerialName("cant-block-alone") data object CantBlockAlone : Mechanic
    @Serializable @SerialName("must-attack") data class MustAttack(val target: Recipients? = null) : Mechanic
    @Serializable @SerialName("must-block") data class MustBlock(val attackers: ObjectFilter) : Mechanic
    /** "Must be blocked if able": a requirement that at least one creature blocks it, when one can (CR 509.1c). */
    @Serializable @SerialName("must-be-blocked") data object MustBeBlocked : Mechanic
    /** Assigns combat damage equal to its toughness rather than its power (CR 510.1). */
    @Serializable @SerialName("damage-by-toughness") data object AssignsDamageByToughness : Mechanic
    /** May assign its combat damage as though it weren't blocked (card text; no general rule). */
    @Serializable @SerialName("damage-as-unblocked") data object AssignsDamageAsThoughUnblocked : Mechanic
    /** Assigns no combat damage. */
    @Serializable @SerialName("no-combat-damage") data object AssignsNoCombatDamage : Mechanic

    // ---- Permissions, costs and casting (CR 601, 117, 118) ----
    /** May be cast (or activated) at any time it could cast an instant (flash, CR 702.8): the affected objects. */
    @Serializable @SerialName("instant-speed") data object InstantSpeed : Mechanic
    /** The affected players may cast spells matching [spells] as though they had flash, from wherever they could cast
     * them (Aluren-style); a permission of players, not of objects. */
    @Serializable @SerialName("cast-as-though-flash") data class CastAsThoughFlash(val spells: ObjectFilter) : Mechanic
    /**
     * The permission to play or cast cards from [from] ("you may cast ~ from your graveyard", "until end of turn, you may
     * play those cards", "you may look at and play the top card of your library"). [how]: play (lands too, using a land
     * play, CR 305.1) or cast (spells only, CR 601). Which cards: [Static.affects] or the effect's affected objects (for a
     * static ability on the card itself, the card); [resultingSpell] only conditions the spell it becomes ("if the
     * resulting spell is an instant or sorcery", CR 702.34a). [topOnly]: only the top card of a library; [cost] replaces
     * the mana cost (flashback, escape); [then] is where it goes when it leaves the Stack (flashback: exile);
     * [manaAsAnyColor]: mana may be spent as though it were mana of any colour for them. Timing is the card's own
     * ("as though it had flash" is [InstantSpeed]). The permission belongs to "you" (the ability's or effect's
     * controller), also for cards in an opponent's library or exile.
     */
    @Serializable @SerialName("may-play") data class MayPlay(
        val from: Zone, val how: PlayKind, val resultingSpell: ObjectFilter? = null, val topOnly: Boolean = false,
        val cost: Cost? = null, val withoutPaying: Boolean = false,
        /** At most this many cards this way each turn ("Once during each of your turns, you may cast …", Lurrus). */
        val perTurn: Int? = null,
        /** Paid in addition to its other costs ("by discarding a land card in addition to paying its other costs":
         * retrace, jump-start). */
        val additionalCost: Cost? = null,
        /** Cast transformed (disturb, CR 702.146a). */
        val transformed: Boolean = false,
        /** Where it goes when it leaves the Stack: instead of anywhere other than the battlefield (flashback: exile), or
         * only instead of [thenInsteadOf] ("if that spell would be put into your graveyard, exile it instead"). */
        val then: Destination? = null, val thenInsteadOf: Zone? = null, val manaAsAnyColor: Boolean = false,
        /** Who may play them: [PlayerFilter.You], or "any player may cast" (Aluren). */
        val holder: PlayerFilter,
        /** Only by its [viaAlternative] alternative cost ("cast it from your graveyard using its bestow ability"). */
        @Serializable(with = KeywordKindSerializer::class) val viaAlternative: KeywordKind? = null,
        /** What a spell cast with this permission and the permanent it becomes get ("If you cast a spell this way, that
         * creature enters with a finality counter on it", CR 400.7d); [Objects.Affected] in them is that spell (as
         * [Effect.SpellGets]). */
        val spellGets: List<Mechanic> = emptyList(),
    ) : Mechanic
    /** "You may pay [cost] rather than pay this spell's mana cost" (CR 118.9). [faceDown]: cast face down as a 2/2 for
     * {3} (morph, disguise, CR 702.37c); the keyword's own cost is paid in the special action that turns it face up.
     * In a static ability of another object, the affected spells get it when that ability's controller casts them ("You
     * may pay {W}{U}{B}{R}{G} rather than pay the mana cost for spells you cast": [ObjectFilter.WasCast]); a card is
     * tested as the spell it becomes as it is cast from where it is (CR 601.2a), so "a spell you cast from exile" is
     * `WasCast(Zone.Exile)`. [perTurn]: that player may use it only that many times each turn ("Once each turn, you may
     * pay {0} rather than pay the mana cost for a spell you cast from exile"), counted on the ability's object. */
    @Serializable @SerialName("alternative-cost") data class AlternativeCost(
        @Serializable(with = KeywordKindSerializer::class) val keyword: KeywordKind? = null,
        val cost: Cost, val faceDown: Boolean = false, val perTurn: Int? = null,
    ) : Mechanic
    /** "As an additional cost to cast this spell, …" (CR 601.2f); [optional]/[repeatable] for kicker/multikicker;
     * [times] for a cost paid a computed number of times (escalate: once per mode beyond the first). */
    @Serializable @SerialName("additional-cost") data class AdditionalCost(
        @Serializable(with = KeywordKindSerializer::class) val keyword: KeywordKind? = null,
        val cost: Cost, val optional: Boolean = false, val repeatable: Boolean = false, val times: Value? = null,
    ) : Mechanic
    /** Some of the spell's mana may be paid another [way] (delve, convoke, improvise; CR 702.66a, 702.51a). */
    @Serializable @SerialName("pay-mana-by") data class PayManaBy(val way: ManaPaymentWay) : Mechanic
    /** "You can't spend mana to cast this spell." */
    @Serializable @SerialName("cant-spend-mana") data object CantSpendMana : Mechanic
    /** "X can't be [lessThan − 1]" ("X can't be 0"). */
    @Serializable @SerialName("x-at-least") data class XAtLeast(val min: Int) : Mechanic
    /** Splice onto [onto] (CR 702.47a): as you cast such a spell you may reveal this card and pay [cost]; the spell gains
     * this card's instructions. Functions in the hand. */
    @Serializable @SerialName("splice-onto") data class SpliceOnto(val onto: ObjectFilter, val cost: Cost) : Mechanic
    /** Has the activated abilities of [objects] (layer 6), loyalty abilities too unless [exceptLoyalty]. */
    @Serializable @SerialName("gain-abilities-of") data class GainAbilitiesOf(val objects: Objects, val exceptLoyalty: Boolean = false) : Mechanic {
        override val layer: Layer get() = Layer.Ability
        override val stacking: Stacking get() = Stacking.Independent
    }
    /** [label] abilities of the affected objects may be activated [count] times each turn ("can boast twice each turn"). */
    @Serializable @SerialName("label-uses") data class LabelUsesPerTurn(val label: AbilityLabel, val count: Int) : Mechanic
    /** Spells or abilities matching [what] cost more or less by [change] (CR 601.2f, 118.7); abilities by keyword with
     * [ObjectFilter.HasKeyword] ("equip abilities cost {1} less"). [minimum]: the generic mana a reduction can't go
     * below (0: no minimum). */
    @Serializable @SerialName("cost-change") data class CostChange(
        val what: ObjectFilter, val change: arcanum.api.CostChange, val minimum: Int = 0,
    ) : Mechanic {
        override val stacking: Stacking get() = Stacking.Independent
    }
    @Serializable @SerialName("cant-be-countered") data object CantBeCountered : Mechanic
    /** Activated abilities of the affected objects can't be activated; [manaAbilities] false keeps mana abilities
     * ("unless they're mana abilities"). */
    @Serializable @SerialName("cant-be-activated") data class CantBeActivated(val manaAbilities: Boolean) : Mechanic
    /** The affected permanents can't be regenerated: regeneration shields don't apply to them (CR 701.19c). */
    @Serializable @SerialName("cant-be-regenerated") data object CantBeRegenerated : Mechanic
    /** The affected face-down permanents can't be turned face up: neither by a special action (CR 116.2b, 701.40b) nor by an
     * effect. */
    @Serializable @SerialName("cant-be-turned-face-up") data object CantBeTurnedFaceUp : Mechanic
    /** When the card itself may be cast ("Cast this spell only during combat", "only if …"); prohibitions on other spells
     * are [CantCast]. */
    @Serializable @SerialName("cast-restriction") data class CastRestriction(val timing: Timing? = null, val condition: Condition? = null) : Mechanic
    /** The affected players can't cast spells matching [spells] ("Your opponents can't cast noncreature spells", "can't
     * cast more than one spell each turn" with a condition on [Players.Affected]); [Objects.Tested] is the spell. */
    @Serializable @SerialName("cant-cast") data class CantCast(val spells: ObjectFilter) : Mechanic
    /** The affected permanents can't be sacrificed. */
    @Serializable @SerialName("cant-be-sacrificed") data object CantBeSacrificed : Mechanic
    /** The affected players can't draw more than [count] cards each turn. */
    @Serializable @SerialName("max-draws") data class MaxDrawsPerTurn(val count: Int) : Mechanic
    /** [count] additional land plays each turn; null: any number (CR 305.2). */
    @Serializable @SerialName("extra-land-plays") data class ExtraLandPlays(val count: Int?) : Mechanic {
        override val stacking: Stacking get() = Stacking.Additive
    }
    /** The affected players may spend mana as though it were mana of any colour (for everything they pay). */
    @Serializable @SerialName("spend-mana-as-any-color") data class SpendManaAsAnyColor(
        /** Only when paying for spells or abilities matching this ("to activate abilities of creatures you control"). */
        val onlyFor: ObjectFilter? = null,
    ) : Mechanic
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
    /** "If [event] causes a triggered ability of [sources] to trigger, it triggers an additional time" (CR 603.2d); [event]
     * null: whatever event ("If a triggered ability of a permanent you control triggers"). */
    @Serializable @SerialName("triggers-additional-time") data class TriggersAdditionalTime(
        val event: EventPattern?, val sources: ObjectFilter,
    ) : Mechanic {
        override val stacking: Stacking get() = Stacking.Independent
    }
    @Serializable @SerialName("doesnt-untap") data object DoesntUntap : Mechanic
    /** The affected permanents untap during each other player's untap step too (CR 502.3). */
    @Serializable @SerialName("untaps-during-other-untap-steps") data object UntapsDuringOtherUntapSteps : Mechanic
    /** The affected players don't lose the game for having 0 or less life (CR 704.5a doesn't apply to them). */
    @Serializable @SerialName("no-loss-for-zero-life") data object NoLossForZeroLife : Mechanic
    /** The affected players' maximum hand size is [value] (CR 402.2), instead of seven. */
    @Serializable @SerialName("maximum-hand-size-is") data class MaximumHandSize(val value: Value) : Mechanic {
        override val stacking: Stacking get() = Stacking.Independent
    }
    /** "If this card is in your opening hand, you may begin the game with it on the battlefield" (CR 103.6a): an
     * opening-hand action, taken after mulligans; works in the hand. */
    @Serializable @SerialName("may-begin-on-battlefield") data object MayBeginOnBattlefield : Mechanic

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
        /** A triggered ability linked to this replacement, triggering each time it is applied ("When this card is
         * exiled this way", madness, CR 702.35a, 603.11); [Objects.EventObject] in it is the replaced event's object. */
        val whenApplied: Instructions? = null,
    ) : Mechanic {
        override val stacking: Stacking get() = Stacking.Independent
    }
    /** Prevent all of the damage matching [event], or [each] of each such damage event ("prevent 1 of that damage",
     * CR 615). Shields with an amount that gets used up are [Effect.PreventDamage]. [additional]: what it also does,
     * immediately after the prevention (CR 615.5: "prevent that damage and each opponent mills that many cards"), with
     * the damage that would have been dealt as the event's amount ([Value.EventAmount]); for damage that can't be
     * prevented it still happens (CR 615.12). */
    @Serializable @SerialName("prevention") data class Prevention(
        val event: EventPattern, val each: Value? = null,
        // dsk gaps
        val additional: Instructions? = null,
    ) : Mechanic {
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
        val add: List<CardType> = emptyList(), val addSubtypes: List<SubtypeRef> = emptyList(),
        val addSupertypes: List<Supertype> = emptyList(), val removeSupertypes: List<Supertype> = emptyList(),
        /** Replaces the creature types ("except it's a Bird"). */
        val replaceSubtypes: Boolean = false,
    ) : CopyException
    /** Its colours become [colors], or also [colors] when [add] ("in addition to its other colors"). */
    @Serializable @SerialName("colors") data class Colors(val colors: List<ColorRef>, val add: Boolean = false) : CopyException
    @Serializable @SerialName("pt") data class PowerToughness(val power: Int, val toughness: Int) : CopyException
    @Serializable @SerialName("abilities") data class AddAbilities(val abilities: List<Ability>) : CopyException
    @Serializable @SerialName("mana-cost") data class ManaCostOf(val cost: ManaCost?) : CopyException
}

/** Other ways to pay a spell's mana. */
@Serializable
sealed interface ManaPaymentWay {
    /** Exile a card from your graveyard for each generic mana (delve, CR 702.66a). */
    @Serializable @SerialName("exile-from-graveyard") data object ExileFromGraveyard : ManaPaymentWay
    /** Tap an untapped creature you control for each generic mana, or for a coloured mana of its colour (convoke,
     * CR 702.51a). */
    @Serializable @SerialName("tap-creatures") data object TapCreatures : ManaPaymentWay
    /** Tap an untapped artifact you control for each generic mana (improvise, CR 702.126a). */
    @Serializable @SerialName("tap-artifacts") data object TapArtifacts : ManaPaymentWay
    // blb gaps 2
    /** Tap an untapped creature you control rather than pay one mana of any kind ("For each mana in this ability's
     * activation cost, you may tap an untapped creature you control rather than pay that mana"): unlike convoke (CR
     * 702.51a) the creature's colour doesn't matter. Used by [ActivationRules.payManaBy]. */
    @Serializable @SerialName("tap-creatures-for-any-mana") data object TapCreaturesForAnyMana : ManaPaymentWay
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
    @Serializable @SerialName("enters-as-copy") data class EntersAsCopy(val of: ObjectFilter, val except: List<CopyException> = emptyList(), val optional: Boolean) : Modification
    @Serializable @SerialName("multiply") data class MultiplyAmount(val factor: Int) : Modification
    @Serializable @SerialName("add") data class AddAmount(val amount: Value) : Modification
    /** "As ~ enters, …" (CR 614.12): [steps] happen as part of entering, not on the Stack: pay ("you may pay 2 life. If
     * you don't, it enters tapped"), choose ("choose a creature type"). Effects on [Objects.Self] apply to it as it
     * enters. */
    @Serializable @SerialName("as-enters") data class AsEnters(val steps: List<Step>) : Modification
    /** The damage is dealt to [to] instead ("is dealt to this creature instead"). */
    @Serializable @SerialName("redirect") data class ChangeRecipient(val to: Recipients) : Modification
    /** Also create [count] of [token] ("those tokens plus that many 1/1 Squirrel tokens"). */
    @Serializable @SerialName("also-create") data class AlsoCreate(val token: TokenSpec, val count: Value) : Modification
    /** "Enters under the control of [players]" (chosen among the first replacements, CR 616.1b). */
    @Serializable @SerialName("enters-under-control") data class EntersUnderControlOf(val players: Players) : Modification
    /** "Enters with [abilities]" ("If kicked, it enters with … and with trample"): part of the entering object. */
    @Serializable @SerialName("enters-with-abilities") data class EntersWithAbilities(val abilities: List<Ability>) : Modification
    // gtc chunk 12
    /** What the entering permanent is as it enters ("enters … as a Mutant in addition to its other types"): a continuous
     * effect on it that applies as it enters, so its enters triggers see it (CR 614.1c, 614.12, 611.2e). */
    @Serializable @SerialName("enters-as") data class EntersAs(val mechanics: List<Mechanic>) : Modification
    /** The event doesn't happen at all ("If you would draw a card, skip that draw instead"). Skipping a step is
     * [Mechanic.SkipStep]; preventing damage is [Mechanic.Prevention] or [Effect.PreventDamage] (CR 615, not 614). */
    @Serializable @SerialName("skip") data object Skip : Modification
}
