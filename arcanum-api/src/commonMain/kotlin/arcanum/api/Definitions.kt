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
     * [abilityWord] is an italic ability word ("Spell mastery"), which has no rules meaning (CR 207.2c).
     */
    @Serializable @SerialName("spell")
    data class Spell(
        val instructions: Instructions,
        val abilityWord: String? = null,
        val text: String? = null,
    ) : Ability

    /** "[Cost]: [Effect]" (CR 602). It is a mana ability (CR 605.1a) when it could add mana, has no target and isn't a
     * loyalty ability; a [CostPart.Loyalty] cost makes it a loyalty ability, activated only as a sorcery and only once
     * each turn for that permanent's loyalty abilities (CR 606.3). */
    @Serializable @SerialName("activated")
    data class Activated(
        val cost: Cost,
        val instructions: Instructions,
        val rules: ActivationRules = ActivationRules(),
        /** Zones it works in, only when the text states them (CR 113.6b, c). Null: the CR 113.6 default,
         * which the engine works out from what the ability does (battlefield; cost and casting rules of the card itself
         * on the Stack and where it can be cast from; "enters tapped/with counters" as it enters, CR 113.6d–j, 614.12;
         * costs or effects that move it out of a zone work in that zone; characteristic-defining everywhere; CR 113.6k, m). */
        val functionsIn: List<Zone>? = null,
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
        /** Zones it works in, only when the text states them (CR 113.6b, c). Null: the CR 113.6 default,
         * which the engine works out from what the ability does (battlefield; cost and casting rules of the card itself
         * on the Stack and where it can be cast from; "enters tapped/with counters" as it enters, CR 113.6d–j, 614.12;
         * costs or effects that move it out of a zone work in that zone; characteristic-defining everywhere; CR 113.6k, m). */
        val functionsIn: List<Zone>? = null,
        val abilityWord: String? = null,
        val text: String? = null,
    ) : Ability

    /**
     * A continuous rule (CR 604): [mechanics] apply to [affects] while [condition] holds. [characteristicDefining]
     * marks a characteristic-defining ability (CR 604.3), which works in all zones and applies first in its layer.
     */
    @Serializable @SerialName("static")
    data class Static(
        val mechanics: List<Mechanic>,
        val affects: Affected = Affected.Objects(Objects.Self),
        val condition: Condition? = null,
        val characteristicDefining: Boolean = false,
        /** Zones it works in, only when the text states them (CR 113.6b, c). Null: the CR 113.6 default,
         * which the engine works out from what the ability does (battlefield; cost and casting rules of the card itself
         * on the Stack and where it can be cast from; "enters tapped/with counters" as it enters, CR 113.6d–j, 614.12;
         * costs or effects that move it out of a zone work in that zone; characteristic-defining everywhere; CR 113.6k, m). */
        val functionsIn: List<Zone>? = null,
        val abilityWord: String? = null,
        val text: String? = null,
    ) : Ability

    /** A special action (CR 116.2): turning a face-down permanent face up (morph, disguise), foretelling, plotting,
     * suspending, unlocking a door. Doesn't use the Stack; [timing] says when it may be taken. */
    @Serializable @SerialName("special-action")
    data class SpecialAction(
        val cost: Cost,
        val instructions: Instructions,
        val timing: Timing = Timing.Any,
        val functionsIn: List<Zone>? = null,
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
    /** The game as a whole ("Spells can't be countered", "Players can't gain life" written as game rules). */
    @Serializable @SerialName("game") data object Game : Affected
    /** The next [count] spells matching [spells] cast after the effect is created ("the next instant or sorcery spell
     * you cast this turn costs {2} less", CR 611.2f); used up as they are cast. */
    @Serializable @SerialName("next-spells") data class NextSpells(val spells: ObjectFilter, val count: Int = 1) : Affected
}

/** Activation rules of an activated ability (CR 602.5). */
@Serializable
data class ActivationRules(
    val timing: Timing = Timing.Any,
    /** "Activate only once each turn" = 1. */
    val perTurn: Int? = null,
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
    @Serializable @SerialName("your-turn") data object YourTurn : Timing
    @Serializable @SerialName("combat") data object DuringCombat : Timing
    @Serializable @SerialName("upkeep") data object YourUpkeep : Timing
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
)

/** "Choose one —" (CR 700.2). */
@Serializable
data class Modes(
    val options: List<Mode>,
    val min: Int = 1,
    val max: Int = 1,
    /** The same mode may be chosen more than once. */
    val repeatable: Boolean = false,
    /** Who chooses ("target opponent chooses one —"). */
    val chooser: Players = Players.You,
)

/** One mode. [extraCost] is paid for choosing it (spree, CR 702.172). */
@Serializable
data class Mode(val instructions: Instructions, val extraCost: Cost? = null, val text: String? = null)

/** A named target (CR 115): what can be chosen and how many ([max] null: any number; "X target creatures": a value).
 * [divided] splits an amount among the chosen targets (CR 601.2d), read with [Value.DividedShare]. [distinctFrom]
 * names slots whose choices it must differ from ("another target creature"). */
@Serializable
data class TargetSlot(
    val name: String,
    val what: TargetKind,
    val min: Value = Value.Fixed(1),
    val max: Value? = Value.Fixed(1),
    val divided: Value? = null,
    val distinctFrom: List<String> = emptyList(),
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
 * repetition. Effects name the objects they produce themselves (see [Effect]).
 */
@Serializable
sealed interface Step {
    @Serializable @SerialName("do") data class Do(val effect: Effect) : Step
    /** "You may pay [cost]. If you do, …": [payer] may pay (mana abilities allowed, CR 608.2g); [bind] names whether it
     * was paid, read with [Condition.Did]. */
    @Serializable @SerialName("pay") data class Pay(val cost: Cost, val payer: Players = Players.You, val bind: String? = null) : Step
    /** "[steps] unless [payer] pays [cost]" ("Counter target spell unless its controller pays {2}"). */
    @Serializable @SerialName("unless") data class Unless(val payer: Players, val cost: Cost, val steps: List<Step>) : Step
    /** "You may …" / "Target player may …" (CR 608.2d): [player] decides whether [steps] happen. */
    @Serializable @SerialName("may") data class May(val steps: List<Step>, val player: Players = Players.You, val bind: String? = null) : Step
    /** "If …, … Otherwise, …". "If you do" is [Condition.Did]. */
    @Serializable @SerialName("if") data class If(val condition: Condition, val then: List<Step>, val otherwise: List<Step> = emptyList()) : Step
    /** "For each …": [steps] run once per object or player in [each], bound to [bind]. [together]: every player's
     * choices are made first (in turn order), then the effects happen as one event ("Each opponent sacrifices a
     * creature", CR 608.2e), so "one or more" triggers trigger once. */
    @Serializable @SerialName("for-each") data class ForEach(
        val each: Each, val bind: String, val steps: List<Step>, val together: Boolean = false,
    ) : Step
    @Serializable @SerialName("repeat") data class Repeat(val times: Value, val steps: List<Step>) : Step
    /** A choice made during resolution (colour, creature type, number, card name, player, objects, option),
     * named [bind]. With [remember], it is kept on the source as card memory (CR 607 linked abilities, "As ~ enters,
     * choose a colour"). [hidden] is chosen secretly. */
    @Serializable @SerialName("choose") data class Choose(
        val choice: Choice, val bind: String, val chooser: Players = Players.You,
        val remember: Boolean = false, val hidden: Boolean = false,
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
    @Serializable @SerialName("color") data class Color(val among: List<arcanum.api.Color>? = null) : Choice
    @Serializable @SerialName("creature-type") data object CreatureType : Choice
    @Serializable @SerialName("card-type") data object CardType : Choice
    @Serializable @SerialName("card-name") data class CardName(val filter: ObjectFilter = ObjectFilter.Any) : Choice
    @Serializable @SerialName("number") data class Number(val min: Value, val max: Value? = null) : Choice
    @Serializable @SerialName("player") data class Player(val filter: PlayerFilter) : Choice
    /** Choose [min]..[max] objects matching [filter] (not targeting), from [from] when given ("from among them"). */
    @Serializable @SerialName("objects") data class Objects(
        val filter: ObjectFilter, val min: Value, val max: Value, val from: arcanum.api.Objects? = null,
    ) : Choice
    /** Choose one of named options ("odd or even", "left or right"). */
    @Serializable @SerialName("option") data class Option(val options: List<String>) : Choice
}
