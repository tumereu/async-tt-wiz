package arcanum.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Overview of the model (docs/specs/arcanum-automatum.md §5):
 * - [CardDefinition]: a layout and one or more [FaceDefinition]s.
 * - A face has its characteristics (name, mana cost, colour indicator, types, P/T, loyalty, defense) and [Ability]s.
 * - An ability is a container of one of the CR kinds: [Ability.Spell], [Ability.Activated], [Ability.Triggered],
 *   [Ability.Static]; or a [Keyword], which a shared table expands into such abilities; or [Ability.Unparsed].
 * - Spell, activated and triggered abilities carry [Instructions]: targets, modes and [Step]s of one-shot [Effect]s.
 * - Static abilities carry [Mechanic]s: continuous rules the engine applies.
 * - Shared building blocks: [Cost], [Trigger]/[EventPattern], [ObjectFilter]/[Objects], [PlayerFilter]/[Players],
 *   [Value], [Condition], [Duration].
 * - Defaults (D51): a field defaults only to an empty or absent value (`null`, `false`, an empty list, `0` meaning
 *   none); every other field, such as who acts, how many and which objects, is written out.
 */
@Serializable
data class CardDefinition(val layout: Layout, val faces: List<FaceDefinition>)

/** How a card's faces are arranged (CR 709–712, 715). */
@Serializable
sealed interface Layout {
    @Serializable @SerialName("normal") data object Normal : Layout
    /** Two halves on one face, cast separately or fused (CR 709). An aftermath card is a split card whose second half
     * has the aftermath keyword (CR 702.127). */
    @Serializable @SerialName("split") data object Split : Layout
    /** Flip card (CR 710). */
    @Serializable @SerialName("flip") data object Flip : Layout
    /** Double-faced card that transforms (CR 712). */
    @Serializable @SerialName("transform") data object Transform : Layout
    /** Modal double-faced card: either face can be played (CR 712). */
    @Serializable @SerialName("modal-dfc") data object ModalDoubleFaced : Layout
    /** Meld card: melds with [partner] into a combined back face (CR 712, 701.42). */
    @Serializable @SerialName("meld") data class Meld(val partner: String) : Layout
    /** Adventurer card: the second face is the Adventure spell (CR 715). */
    @Serializable @SerialName("adventure") data object Adventure : Layout
    /** Omen card: the inset face is an Omen spell; cast that way it is shuffled into its owner's library as it resolves
     * (CR 720). */
    @Serializable @SerialName("omen") data object Omen : Layout
}

/** One face's characteristics (CR 109.3) and its abilities in printed order. Also used for tokens. A land's mana
 * abilities from its basic land types ("{T}: Add {G}" of a Forest) are not listed: the engine gives them from the
 * land's current types (CR 305.6, 305.7), and the parser drops their reminder text. */
@Serializable
data class FaceDefinition(
    val name: String,
    val manaCost: ManaCost? = null,
    /** Colour indicator (CR 204); without it, colour comes from the mana cost (CR 202.2). */
    val colorIndicator: List<Color>? = null,
    val supertypes: List<Supertype> = emptyList(),
    val types: List<CardType> = emptyList(),
    val subtypes: List<Subtype> = emptyList(),
    val power: PrintedNumber? = null,
    val toughness: PrintedNumber? = null,
    val loyalty: PrintedNumber? = null,
    val defense: PrintedNumber? = null,
    val abilities: List<Ability> = emptyList(),
)

/** One ability of a face (CR 113). */
@Serializable
sealed interface Ability {
    /**
     * Instructions of an instant or sorcery (CR 113.3a).
     * [abilityWord] is an italic ability word ("Spell mastery") or flavor word ("Magical Tinkering"), which has no rules
     * meaning (CR 207.2c, 207.2d); the same field on the other ability kinds.
     */
    @Serializable @SerialName("spell")
    data class Spell(
        val instructions: Instructions,
        val abilityWord: String? = null,
        val text: String? = null,
    ) : Ability

    /** "[Cost]: [Effect]" (CR 602). It is a mana ability (CR 605.1a) when it could add mana, has no target, isn't a
     * loyalty ability, and its cost and effect don't move a card to or from a library ([isManaAbility]); a
     * [CostPart.Loyalty] cost makes it
     * a loyalty ability, activated only as a sorcery and only once
     * each turn for that permanent's loyalty abilities (CR 606.3). */
    @Serializable @SerialName("activated")
    data class Activated(
        val cost: Cost,
        val instructions: Instructions,
        val rules: ActivationRules,
        /** Zones it works in, only when the text states them (CR 113.6b, c). Null: the CR 113.6 default,
         * which the engine works out from what the ability does (battlefield; cost and casting rules of the card itself
         * on the Stack and where it can be cast from; "enters tapped/with counters" as it enters, CR 113.6d–j, 614.12;
         * costs or effects that move it out of a zone work in that zone; characteristic-defining everywhere; CR 113.6k, m). */
        val functionsIn: List<Zone>? = null,
        /** A keyword that adds rules to this ability (exhaust, boast, max speed, solved, power-up), kept so other
         * cards can refer to it ("an exhaust ability"); [KeywordExpansions.withLabelRules] adds those rules. */
        val label: AbilityLabel? = null,
        val abilityWord: String? = null,
        val text: String? = null,
    ) : Ability

    /** "When/Whenever/At [trigger], [effect]" (CR 603). [interveningIf] is checked on trigger and on resolution
     * (CR 603.4). A "you may" is a [Step.May] decided on resolution; the trigger goes on the Stack either way
     * (CR 603.5). */
    @Serializable @SerialName("triggered")
    data class Triggered(
        val trigger: Trigger,
        val instructions: Instructions,
        val interveningIf: Condition? = null,
        /** "This ability triggers only once each turn" = 1. */
        val perTurn: Int? = null,
        /** "… Do this only once each turn": triggers only if its source's controller hasn't yet taken that action this
         * turn (CR 603.2h). */
        val actionOncePerTurn: Boolean = false,
        /** A condition checked only when it would trigger, not again on resolution ("This ability triggers only if this
         * Case is solved", CR 702.169c), unlike [interveningIf]. */
        val triggersOnlyIf: Condition? = null,
        /** Zones it works in, only when the text states them (CR 113.6b, c). Null: the CR 113.6 default,
         * which the engine works out from what the ability does (battlefield; cost and casting rules of the card itself
         * on the Stack and where it can be cast from; "enters tapped/with counters" as it enters, CR 113.6d–j, 614.12;
         * costs or effects that move it out of a zone work in that zone; characteristic-defining everywhere; CR 113.6k, m). */
        val functionsIn: List<Zone>? = null,
        /** A keyword that adds rules to this ability (exhaust, boast, max speed, solved, power-up), kept so other
         * cards can refer to it ("an exhaust ability"); [KeywordExpansions.withLabelRules] adds those rules. */
        val label: AbilityLabel? = null,
        val abilityWord: String? = null,
        val text: String? = null,
    ) : Ability

    /**
     * A continuous rule (CR 604): [mechanics] apply to [affects] while [condition] holds. [characteristicDefining]
     * marks a characteristic-defining ability (CR 604.3): it works in all zones, affects only its own object, has no
     * condition, and its P/T applies in layer 7a.
     *
     * Mechanics scoped by an event or filter of their own ([Mechanic.Replacement], [Mechanic.Prevention],
     * [Mechanic.CostChange], [Mechanic.TriggersAdditionalTime], [Mechanic.DamageCantBePrevented]) don't use [affects];
     * it is `Affected.Objects(Objects.Self)`. Inside [mechanics] and [condition], [Objects.Affected] / [Players.Affected] mean each
     * affected object or player in turn ("each creature gets +1/+1 for each other creature that shares a type with it").
     */
    @Serializable @SerialName("static")
    data class Static(
        val mechanics: List<Mechanic>,
        val affects: Affected,
        val condition: Condition? = null,
        val characteristicDefining: Boolean = false,
        /** Zones it works in, only when the text states them (CR 113.6b, c). Null: the CR 113.6 default,
         * which the engine works out from what the ability does (battlefield; cost and casting rules of the card itself
         * on the Stack and where it can be cast from; "enters tapped/with counters" as it enters, CR 113.6d–j, 614.12;
         * costs or effects that move it out of a zone work in that zone; characteristic-defining everywhere; CR 113.6k, m). */
        val functionsIn: List<Zone>? = null,
        /** A keyword that adds rules to this ability (exhaust, boast, max speed, solved, power-up), kept so other
         * cards can refer to it ("an exhaust ability"); [KeywordExpansions.withLabelRules] adds those rules. */
        val label: AbilityLabel? = null,
        val abilityWord: String? = null,
        val text: String? = null,
    ) : Ability

    /** A special action (CR 116.2): turning a face-down permanent face up (morph, disguise), foretelling, plotting,
     * suspending, unlocking a door. Doesn't use the Stack; [timing] says when it may be taken. */
    @Serializable @SerialName("special-action")
    data class SpecialAction(
        val cost: Cost,
        val instructions: Instructions,
        val timing: Timing,
        val functionsIn: List<Zone>? = null,
        /** Only once each game (companion, CR 116.2g). */
        val oncePerGame: Boolean = false,
        /** Only while this holds ("turn a face-down permanent face up", CR 702.37e; "the companion you revealed"). */
        val condition: Condition? = null,
        val text: String? = null,
    ) : Ability

    /** Text the parser could not read, kept exactly as printed (reminder text included) so it can be shown and
     * handled by hand. */
    @Serializable @SerialName("unparsed")
    data class Unparsed(val text: String) : Ability
}

/** What a static ability's mechanics apply to. */
@Serializable
sealed interface Affected {
    @Serializable @SerialName("objects") data class Objects(val objects: arcanum.api.Objects) : Affected
    @Serializable @SerialName("players") data class Players(val players: arcanum.api.Players) : Affected
    /** The next [count] spells matching [spells] cast after the effect is created ("the next instant or sorcery spell
     * you cast this turn costs {2} less", CR 611.2f); used up as they are cast. */
    @Serializable @SerialName("next-spells") data class NextSpells(val spells: ObjectFilter, val count: Int) : Affected
}

/** Activation rules of an activated ability (CR 602.5). */
@Serializable
data class ActivationRules(
    val timing: Timing,
    /** Who may activate it: [PlayerFilter.You] for its controller; "any player may activate this ability" (CR 602.2). */
    val activatedBy: PlayerFilter,
    /** "Activate only once each turn" = 1. */
    val perTurn: Int? = null,
    /** "Activate only once": once for as long as the object exists (exhaust, CR 702.177a). */
    val once: Boolean = false,
    /** "This ability costs {1} less to activate for each …" (CR 601.2f, 118.7). */
    val costChange: CostChange? = null,
    /** "Activate only if …". */
    val condition: Condition? = null,
)

/** When something may be cast or activated. */
@Serializable
sealed interface Timing {
    /** Any time its controller has priority. */
    @Serializable @SerialName("any") data object Any : Timing
    /** Only as a sorcery (CR 307.1): own main phase, empty Stack. */
    @Serializable @SerialName("sorcery") data object Sorcery : Timing
    /** "Activate only as an instant": only when its controller has priority, which for a mana ability removes the
     * usual "while paying" timing (CR 602.5e, 605.3a). */
    @Serializable @SerialName("instant") data object Instant : Timing
    /** When this card could be cast (suspend: "if you could begin to cast this card", CR 702.62a). */
    @Serializable @SerialName("as-this-card") data object AsThisCard : Timing
}

/** Keywords that add rules to the ability they label (CR 702.142, 702.169, 702.177, 702.178, 702.193). Other timing
 * ("only during your turn", "only during combat") is an activation condition. */
@Serializable
sealed interface AbilityLabel {
    /** "Activate only if this creature attacked this turn and only once each turn" (CR 702.142a). */
    @Serializable @SerialName("boast") data object Boast : AbilityLabel
    /** Works only while this Case is solved (CR 702.169b–d). */
    @Serializable @SerialName("solved") data object Solved : AbilityLabel
    /** "Activate only once" (CR 702.177a). */
    @Serializable @SerialName("exhaust") data object Exhaust : AbilityLabel
    /** "As long as your speed is 4, this object has [the ability]" (CR 702.178a). */
    @Serializable @SerialName("max-speed") data object MaxSpeed : AbilityLabel
    /** "If this permanent entered this turn, this ability's cost is reduced by its mana cost. Activate only once"
     * (CR 702.193a). */
    @Serializable @SerialName("power-up") data object PowerUp : AbilityLabel
}

/**
 * What a spell, activated or triggered ability does: the targets chosen when it is put on the Stack (CR 601.2c),
 * optional modes (CR 700.2), and the [steps] carried out on resolution (CR 608.2). With modes, each mode has its own
 * instructions and [steps] holds anything done after the chosen modes.
 */
@Serializable
data class Instructions(
    val targets: List<TargetSlot> = emptyList(),
    val modes: Modes? = null,
    val steps: List<Step> = emptyList(),
    /** Values announced when cast or activated (X), by name. */
    val announced: List<String> = emptyList(),
    /** Replacements of this spell's or ability's own effects, applied only while it resolves and chosen before other
     * replacements ("Counter target spell. If that spell is countered this way, exile it instead", CR 614.15, 616.1a). */
    val replacements: List<Mechanic.Replacement> = emptyList(),
)

/** "Choose one —" (CR 700.2). */
@Serializable
data class Modes(
    val options: List<Mode>,
    val min: Int,
    val max: Int,
    /** "… If [condition], you may choose [maxIf] instead" (e.g. "you may choose both instead"). */
    val maxIf: Condition? = null,
    val maxIfCount: Int? = null,
    /** "Choose one that hasn't been chosen" (for this object). */
    val onlyUnchosen: Boolean = false,
    /** "Choose one that hasn't been chosen this turn". */
    val onlyUnchosenThisTurn: Boolean = false,
    /** The same mode may be chosen more than once. */
    val repeatable: Boolean = false,
    /** "Choose up to five {P} worth of modes" (CR 700.2i): any modes whose pawprints ([Mode.pawprints]) add up to at
     * most this many. */
    val pawprintLimit: Int? = null,
    /** Who chooses ("target opponent chooses one —"). */
    val chooser: Players,
)

/** One mode. [extraCost] is paid for choosing it (spree, CR 702.172). A target slot of a mode not chosen is empty: the
 * spell is treated as though it doesn't have that target (CR 700.2c). */
@Serializable
data class Mode(
    val instructions: Instructions, val extraCost: Cost? = null, val text: String? = null,
    /** The pawprint symbols ({P}) listed for it (CR 107.18, 700.2i). */
    val pawprints: Int = 0,
)

/** A named target (CR 115): what can be chosen and how many ([max] null: any number; "X target creatures": a value).
 * [divided] splits an amount among the chosen targets (CR 601.2d), read with [Value.DividedShare]. [distinctFrom]
 * names slots whose choices it must differ from ("another target creature"). */
@Serializable
data class TargetSlot(
    val name: String,
    val what: TargetKind,
    val min: Value,
    val max: Value?,
    val divided: Value? = null,
    val distinctFrom: List<String> = emptyList(),
    /** One such choice for each player matching it ("for each opponent, up to one target creature that player
     * controls"); [Players.SlotPlayer] in [what] is that player. Chosen on casting (CR 601.2c). */
    val perPlayer: PlayerFilter? = null,
    /** The slot exists only if this holds as the targets are chosen, after the optional costs are (CR 601.2b, c): a part
     * that has its effect only if the gift was promised has its targets only then (CR 702.174m). Otherwise the spell is
     * treated as though it doesn't have that target. */
    val onlyIf: Condition? = null,
)

/** What a target can be. "Any target" is [Either] of creatures, planeswalkers and battles, and players (CR 115.4). */
@Serializable
sealed interface TargetKind {
    @Serializable @SerialName("objects") data class Objects(val filter: ObjectFilter) : TargetKind
    @Serializable @SerialName("players") data class Players(val filter: PlayerFilter) : TargetKind
    @Serializable @SerialName("either") data class Either(val objects: ObjectFilter, val players: PlayerFilter) : TargetKind
}

/**
 * One step of resolution. Steps run in order; [Do] performs an [Effect]; the others add choice, condition and
 * repetition. Effects name the objects they produce themselves (see [Effect]). Effects inside [May] or [ForEach] are
 * done by that player (they put things onto the battlefield under their control, they cast).
 */
@Serializable
sealed interface Step {
    @Serializable @SerialName("do") data class Do(val effect: Effect) : Step
    /** [steps] happen as one event ("simultaneously sacrifices … and returns …", CR 608.2c). */
    @Serializable @SerialName("simultaneously") data class Simultaneously(val steps: List<Step>) : Step
    /** Name [objects] as [bind], fixed now ("that creature" for a delayed trigger created by this ability: "destroy that
     * creature at end of combat", CR 603.7c). No choice, no event. */
    @Serializable @SerialName("bind") data class Bind(val objects: Objects, val bind: String) : Step
    /** An action that is a cost paid on resolution (CR 118.12): "You may [do X]. If you do, …", or [mandatory], "[Do X].
     * If you can't, …". X is [cost] when it is something costs can say (sacrifice, discard, exile, tap, return, reveal,
     * pay life or mana), otherwise [steps] (draw, put onto the battlefield, cast, …); exactly one is given. A cost can be
     * chosen only if it can be paid in full (CR 601.2h, 118.3); a draw can always be chosen, even from an empty library
     * (CR 121.3); mana abilities are allowed (CR 608.2g). [bind] names the outcome: paid, declined or couldn't pay, read
     * with [Condition.Did] and [Condition.CouldNot]. Any "you may X. If you do" is a Pay; [May] is for "you may X"
     * alone. "[Do Y] unless [player] pays [cost]" (CR 118.12a) and "you may pay [cost]. If you don't, [do Y]" are both
     * a Pay by that player followed by `If(Not(Did(bind)), then = [Y])`: one shape for both. */
    @Serializable @SerialName("pay") data class Pay(
        val cost: Cost? = null, val steps: List<Step> = emptyList(), val payer: Players,
        val mandatory: Boolean = false, val bind: String? = null,
    ) : Step
    /** Only inside [Modification.AsEnters]: change how the object enters (tapped, with counters, with abilities, as a
     * copy). Entering tapped is not becoming tapped (CR 110.5b, 603.2e). */
    @Serializable @SerialName("modify") data class Modify(val modification: Modification) : Step
    /** "You may …" / "Target player may …" with nothing depending on it (CR 608.2d): [player] decides whether [steps]
     * happen. With a following "if you do", it is a [Pay]; "you may put a card from among them …" is a [Choose] of zero
     * or one. */
    @Serializable @SerialName("may") data class May(val steps: List<Step>, val player: Players) : Step
    /** "If …, … Otherwise, …". "If you do" is [Condition.Did]. A conditional "If [condition], [do Y] instead" in a spell
     * is [otherwise]/[then]; an "instead" that replaces an event is [Instructions.replacements]. */
    @Serializable @SerialName("if") data class If(val condition: Condition, val then: List<Step>, val otherwise: List<Step> = emptyList()) : Step
    /** "For each …": [steps] run once per object or player in [each], bound to [bind]. [together] (CR 608.2e): step by
     * step, all players make that step's choices in turn order, then the step happens for all of them at once ("Each
     * opponent sacrifices a creature": one event, so "one or more" triggers trigger once); use it for every "each
     * player/opponent [does something]" (CR 101.4). A name bound inside is one per player and, for the joint action and
     * after the loop, the group of all of them. [startingWith]: turn order starting with that player ("starting with
     * you"). */
    @Serializable @SerialName("for-each") data class ForEach(
        val each: Each, val bind: String, val steps: List<Step>, val together: Boolean = false, val startingWith: Players? = null,
    ) : Step
    @Serializable @SerialName("repeat") data class Repeat(val times: Value, val steps: List<Step>) : Step
    /** A choice made during resolution (colour, creature type, number, card name, player, objects, option),
     * named [bind]. With [remember], it is kept on the source as card memory (CR 607 linked abilities, "As ~ enters,
     * choose a colour"). Secret choices stay manual (D25). */
    @Serializable @SerialName("choose") data class Choose(
        val choice: Choice, val bind: String, val chooser: Players, val remember: Boolean = false,
    ) : Step
    /** Part of the text the definition can't express: shown to players as a reminder to do by hand. */
    @Serializable @SerialName("manual") data class Manual(val text: String) : Step
}

/** What [Step.ForEach] repeats over. */
@Serializable
sealed interface Each {
    @Serializable @SerialName("objects") data class Objects(val objects: arcanum.api.Objects) : Each
    @Serializable @SerialName("players") data class Players(val players: arcanum.api.Players) : Each
}

/** Kinds of choice made during resolution. */
@Serializable
sealed interface Choice {
    @Serializable @SerialName("color") data class Color(val among: List<ColorRef>? = null) : Choice
    @Serializable @SerialName("creature-type") data object CreatureType : Choice
    @Serializable @SerialName("basic-land-type") data object BasicLandType : Choice
    /** A card type, one of [among] when given ("Choose artifact, creature, or land"). */
    @Serializable @SerialName("card-type") data class CardType(val among: List<arcanum.api.CardType>? = null) : Choice
    @Serializable @SerialName("card-name") data class CardName(val filter: ObjectFilter) : Choice
    @Serializable @SerialName("number") data class Number(val min: Value, val max: Value? = null) : Choice
    @Serializable @SerialName("player") data class Player(val filter: PlayerFilter) : Choice
    /** Choose [min]..[max] objects matching [filter] (not targeting; [max] null: any number), from [from] when given
     * ("from among them"). With fewer available than [min], all available are chosen (CR 609.3). */
    @Serializable @SerialName("objects") data class Objects(
        val filter: ObjectFilter, val min: Value, val max: Value?, val from: arcanum.api.Objects? = null,
    ) : Choice
    /** Choose one of named options ("odd or even", "left or right"). */
    @Serializable @SerialName("option") data class Option(val options: List<String>) : Choice
}
