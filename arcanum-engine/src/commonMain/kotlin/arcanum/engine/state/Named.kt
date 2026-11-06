package arcanum.engine.state

import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.Subtype
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A value kept under a name: what a step bound ("the exiled card", "the chosen colour", "the damage dealt this way"),
 * what a Stack item chose (targets by slot, X), what an effect object froze when it was created (D37), or card memory
 * on an object (CR 607). One shape for all of them, so the evaluator reads names the same way everywhere.
 */
@Serializable
sealed interface NamedValue {
    @Serializable @SerialName("objects") data class Objects(val ids: List<ObjectId>) : NamedValue
    @Serializable @SerialName("players") data class Players(val ids: List<PlayerId>) : NamedValue
    /** Objects and players together ("any target"). */
    @Serializable @SerialName("targets") data class Targets(val refs: List<TargetRef>) : NamedValue
    @Serializable @SerialName("number") data class Number(val value: Int) : NamedValue
    @Serializable @SerialName("colors") data class Colors(val colors: List<Color>) : NamedValue
    @Serializable @SerialName("subtype") data class SubtypeValue(val subtype: Subtype) : NamedValue
    /** mh2: card names chosen one after another ("a card name that hasn't been chosen"). */
    @Serializable @SerialName("names") data class Names(val names: List<String>) : NamedValue
    /** mh2: the subtypes several players chose in one loop ("a type chosen this way", CR 608.2c). */
    @Serializable @SerialName("subtypes") data class Subtypes(val subtypes: List<Subtype>) : NamedValue
    @Serializable @SerialName("card-type") data class CardTypeValue(val cardType: CardType) : NamedValue
    /** A card name ("choose a card name"). */
    @Serializable @SerialName("name") data class Name(val name: String) : NamedValue
    /** One of a choice's named options ("odd or even"). */
    @Serializable @SerialName("option") data class Option(val option: String) : NamedValue
    /** The outcome of a [arcanum.api.Step.Pay] (CR 118.12): paid, declined or couldn't pay (D40). */
    @Serializable @SerialName("payment") data class Payment(val outcome: PayOutcome) : NamedValue
    /** Whether a coin flip was won (D25). */
    @Serializable @SerialName("flip") data class Flip(val won: Boolean) : NamedValue
    /** mh2: several coins flipped at once, how many were won and lost (CR 705). */
    @Serializable @SerialName("flips") data class Flips(val won: Int, val lost: Int) : NamedValue
    /** Die results, in rolling order. */
    @Serializable @SerialName("rolls") data class Rolls(val results: List<Int>) : NamedValue
    /** A spell as it last was on the Stack, with its choices and what it looked like ([StackItem.snapshots]): epic copies it each
     * upkeep after it has left (CR 702.50a). Read as objects, it is its id. */
    @Serializable @SerialName("spell") data class Spell(val item: StackItem) : NamedValue
}

/** What became of a [arcanum.api.Step.Pay] (D40). */
@Serializable
sealed interface PayOutcome {
    @Serializable @SerialName("paid") data object Paid : PayOutcome
    @Serializable @SerialName("declined") data object Declined : PayOutcome
    @Serializable @SerialName("could-not-pay") data object CouldNotPay : PayOutcome
}

/** Named values by name. Immutable: adding a name gives a new [Bindings]. */
@Serializable
data class Bindings(val values: Map<String, NamedValue> = emptyMap()) {
    operator fun get(name: String): NamedValue? = values[name]
    fun with(name: String, value: NamedValue): Bindings = Bindings(values + (name to value))
    fun with(other: Bindings): Bindings = Bindings(values + other.values)

    companion object {
        val EMPTY = Bindings()
    }
}
