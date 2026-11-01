package arcanum.engine.decision

import arcanum.api.CardDefinition
import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.Subtype
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TargetRef
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The answer to a [Decision], sent as [arcanum.engine.input.Input.Answer]; each kind answers the decision of the same
 * name. */
@Serializable
sealed interface Answer {
    /** [Decision.Targets]. */
    @Serializable @SerialName("targets") data class Targets(val chosen: List<TargetRef>) : Answer
    /** [Decision.Modes]: mode indices in the order chosen. */
    @Serializable @SerialName("modes") data class Modes(val chosen: List<Int>) : Answer
    /** [Decision.Announce] and [Decision.ChooseNumber]. */
    @Serializable @SerialName("number") data class Number(val value: Int) : Answer
    /** [Decision.ManaPayment]: the uses that pay the whole cost. */
    @Serializable @SerialName("mana-payment") data class ManaPayment(val uses: List<ManaUse>) : Answer
    /** [Decision.May]. */
    @Serializable @SerialName("yes-no") data class YesNo(val yes: Boolean) : Answer
    /** [Decision.ChooseObjects]. */
    @Serializable @SerialName("objects") data class Objects(val chosen: List<ObjectId>) : Answer
    /** [Decision.ChooseOption]. */
    @Serializable @SerialName("option") data class Option(val chosen: String) : Answer
    /** [Decision.ChooseColor]. */
    @Serializable @SerialName("color") data class ColorChoice(val color: Color) : Answer
    /** [Decision.ChooseSubtype]. */
    @Serializable @SerialName("subtype") data class SubtypeChoice(val subtype: Subtype) : Answer
    /** [Decision.ChooseCardType]. */
    @Serializable @SerialName("card-type") data class CardTypeChoice(val cardType: CardType) : Answer
    /** [Decision.ChooseCardName]. */
    @Serializable @SerialName("card-name") data class CardName(val name: String) : Answer
    /** [Decision.ChoosePlayer]. */
    @Serializable @SerialName("player") data class Player(val player: PlayerId) : Answer
    /** [Decision.Divide]: the amount for each. */
    @Serializable @SerialName("division") data class Division(val shares: List<Share>) : Answer
    /** [Decision.OrderTriggers]: [TriggerOption.index]es, first on the Stack first. */
    @Serializable @SerialName("order") data class Order(val order: List<Int>) : Answer
    /** [Decision.ChooseReplacement]: the [ReplacementOption.index] to apply. */
    @Serializable @SerialName("replacement") data class Replacement(val index: Int) : Answer
    /** [Decision.LegendRule]: the one to keep. */
    @Serializable @SerialName("keep") data class Keep(val kept: ObjectId) : Answer
    /** [Decision.Random] with [RandomKind.CoinFlip]. */
    @Serializable @SerialName("coin") data class CoinFlip(val won: Boolean) : Answer
    /** [Decision.Random] with [RandomKind.Die]: each roll's result. */
    @Serializable @SerialName("dice") data class DieRolls(val results: List<Int>) : Answer
    /** [Decision.Random] with [RandomKind.Objects]. */
    @Serializable @SerialName("random-objects") data class RandomObjects(val chosen: List<ObjectId>) : Answer
    /** [Decision.HostFill]: what each unknown object is. */
    @Serializable @SerialName("filled") data class Filled(val objects: List<FilledObject>) : Answer
    /** [Decision.Manual]: done by hand. */
    @Serializable @SerialName("done") data object Done : Answer
}

/** The identity of an unknown object (D60): its card's [definition]. */
@Serializable
data class FilledObject(val id: ObjectId, val definition: CardDefinition)
