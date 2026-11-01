package arcanum.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Card types (CR 300). Only the types of traditional Magic cards; others are added if ever needed. */
@Serializable
sealed interface CardType {
    @Serializable @SerialName("artifact") data object Artifact : CardType
    @Serializable @SerialName("battle") data object Battle : CardType
    @Serializable @SerialName("creature") data object Creature : CardType
    @Serializable @SerialName("enchantment") data object Enchantment : CardType
    @Serializable @SerialName("instant") data object Instant : CardType
    @Serializable @SerialName("kindred") data object Kindred : CardType
    @Serializable @SerialName("land") data object Land : CardType
    @Serializable @SerialName("planeswalker") data object Planeswalker : CardType
    @Serializable @SerialName("sorcery") data object Sorcery : CardType
}

/** Supertypes (CR 205.4). */
@Serializable
sealed interface Supertype {
    @Serializable @SerialName("basic") data object Basic : Supertype
    @Serializable @SerialName("legendary") data object Legendary : Supertype
    @Serializable @SerialName("snow") data object Snow : Supertype
    @Serializable @SerialName("world") data object World : Supertype
}

/** A subtype as printed (CR 205.3): creature types, land types, "Equipment", "Aura", "Saga", … There are hundreds and
 * new ones appear with sets, so they are names, not types. */
@Serializable
data class Subtype(val name: String)

/** A printed P/T, loyalty or defense value (CR 208): a number, or `*` with an optional addend ("1+*"), whose value a
 * characteristic-defining ability sets. */
@Serializable
sealed interface PrintedNumber {
    @Serializable @SerialName("fixed") data class Fixed(val value: Int) : PrintedNumber
    @Serializable @SerialName("star") data class Star(val plus: Int = 0) : PrintedNumber
    /** "X" (a planeswalker's starting loyalty set by X, CR 107.3). */
    @Serializable @SerialName("x") data object X : PrintedNumber
}

/** Zones (CR 400). */
@Serializable
sealed interface Zone {
    @Serializable @SerialName("battlefield") data object Battlefield : Zone
    @Serializable @SerialName("hand") data object Hand : Zone
    @Serializable @SerialName("library") data object Library : Zone
    @Serializable @SerialName("graveyard") data object Graveyard : Zone
    @Serializable @SerialName("exile") data object Exile : Zone
    @Serializable @SerialName("stack") data object Stack : Zone
    @Serializable @SerialName("command") data object Command : Zone
    /** Outside the game (sideboard, companion). Not a zone in the rules (CR 400.11); listed so abilities and moves can
     * name it. */
    @Serializable @SerialName("outside") data object Outside : Zone
}

/** Steps of a turn (CR 500–514). Main phases are listed as steps for simplicity. */
@Serializable
sealed interface TurnStep {
    @Serializable @SerialName("untap") data object Untap : TurnStep
    @Serializable @SerialName("upkeep") data object Upkeep : TurnStep
    @Serializable @SerialName("draw") data object Draw : TurnStep
    @Serializable @SerialName("precombat-main") data object PrecombatMain : TurnStep
    @Serializable @SerialName("beginning-of-combat") data object BeginningOfCombat : TurnStep
    @Serializable @SerialName("declare-attackers") data object DeclareAttackers : TurnStep
    @Serializable @SerialName("declare-blockers") data object DeclareBlockers : TurnStep
    @Serializable @SerialName("combat-damage") data object CombatDamage : TurnStep
    @Serializable @SerialName("end-of-combat") data object EndOfCombat : TurnStep
    @Serializable @SerialName("postcombat-main") data object PostcombatMain : TurnStep
    @Serializable @SerialName("end") data object End : TurnStep
    @Serializable @SerialName("cleanup") data object Cleanup : TurnStep
}

/** Counter kinds (CR 122). The ones the rules give meaning to are their own types; any other is [Named]. */
@Serializable
sealed interface CounterKind {
    /** A +X/+Y counter: +1/+1, -1/-1, +1/+0, -0/-1, … (CR 122.1a, 613.4c). */
    @Serializable @SerialName("pt") data class PowerToughness(val power: Int, val toughness: Int) : CounterKind
    /** A keyword counter: the object has that keyword while it has the counter (CR 122.1b, 613.1f). */
    @Serializable @SerialName("keyword") data class Keyword(
        @Serializable(with = KeywordKindSerializer::class) val keyword: KeywordKind,
    ) : CounterKind
    /** Prevents the next damage or destruction (CR 122.1c). */
    @Serializable @SerialName("shield") data object Shield : CounterKind
    /** Stops the next untap (CR 122.1d). */
    @Serializable @SerialName("stun") data object Stun : CounterKind
    /** Exiled instead of going to the graveyard (CR 122.1h). */
    @Serializable @SerialName("finality") data object Finality : CounterKind
    @Serializable @SerialName("rad") data object Rad : CounterKind
    @Serializable @SerialName("hone") data object Hone : CounterKind
    @Serializable @SerialName("time") data object Time : CounterKind
    /** Level counters of a leveler (CR 702.87). */
    @Serializable @SerialName("level") data object Level : CounterKind
    @Serializable @SerialName("loyalty") data object Loyalty : CounterKind
    @Serializable @SerialName("lore") data object Lore : CounterKind
    @Serializable @SerialName("defense") data object Defense : CounterKind
    @Serializable @SerialName("poison") data object Poison : CounterKind
    @Serializable @SerialName("energy") data object Energy : CounterKind
    /** Any other counter, without rules meaning of its own: "charge", "oil", … */
    @Serializable @SerialName("named") data class Named(val name: String) : CounterKind
}

/** States and designations an object can have (CR 110.5, 506.4, 701, 702). */
@Serializable
sealed interface Status {
    @Serializable @SerialName("tapped") data object Tapped : Status
    @Serializable @SerialName("face-down") data object FaceDown : Status
    @Serializable @SerialName("transformed") data object Transformed : Status
    @Serializable @SerialName("flipped") data object Flipped : Status
    @Serializable @SerialName("phased-out") data object PhasedOut : Status
    @Serializable @SerialName("attacking") data object Attacking : Status
    @Serializable @SerialName("blocking") data object Blocking : Status
    @Serializable @SerialName("blocked") data object Blocked : Status
    @Serializable @SerialName("unblocked") data object Unblocked : Status
    @Serializable @SerialName("goaded") data object Goaded : Status
    /** Has [mark] ("monstrous", "suspected"). */
    @Serializable @SerialName("marked") data class Marked(val mark: ObjectMark) : Status
}

/** A mark an effect puts on a permanent and nothing else sets ([Effect.Mark]); tapping, turning face down and the
 * like have their own effects. */
@Serializable
sealed interface ObjectMark {
    @Serializable @SerialName("monstrous") data object Monstrous : ObjectMark
    @Serializable @SerialName("renowned") data object Renowned : ObjectMark
    @Serializable @SerialName("suspected") data object Suspected : ObjectMark
    @Serializable @SerialName("saddled") data object Saddled : ObjectMark
    /** A solved Case (CR 719.3b). */
    @Serializable @SerialName("solved") data object Solved : ObjectMark
    /** Any other mark by name. */
    @Serializable @SerialName("named") data class Named(val name: String) : ObjectMark
}

/** Player designations: the monarch (CR 725), the initiative (CR 726), the city's blessing (CR 702.131c). */
@Serializable
sealed interface Designation {
    @Serializable @SerialName("monarch") data object Monarch : Designation
    @Serializable @SerialName("initiative") data object Initiative : Designation
    @Serializable @SerialName("citys-blessing") data object CitysBlessing : Designation
}

/** Comparisons used by filters and conditions. */
@Serializable
sealed interface Comparison {
    @Serializable @SerialName("eq") data object Equal : Comparison
    @Serializable @SerialName("ne") data object NotEqual : Comparison
    @Serializable @SerialName("lt") data object Less : Comparison
    @Serializable @SerialName("le") data object AtMost : Comparison
    @Serializable @SerialName("gt") data object Greater : Comparison
    @Serializable @SerialName("ge") data object AtLeast : Comparison
}
