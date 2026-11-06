package arcanum.engine.decision

import arcanum.api.CardType
import arcanum.api.Color
import arcanum.api.Cost
import arcanum.api.ManaCost
import arcanum.api.ObjectFilter
import arcanum.api.Subtype
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TargetRef
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Names one decision; an [arcanum.engine.input.Input.Answer] quotes it. Serialised as the plain string. It depends only
 * on what is asked, of whom, and how many inputs the game has had before it on its line
 * ([arcanum.engine.state.GameState.inputCount]): state-based actions, triggers, timestamps and a host's reorder of a
 * library never change it, so the same question asked again after an undo is the same decision on every host, and
 * any input in between makes it another.
 */
@Serializable(with = DecisionIdSerializer::class)
data class DecisionId(val value: String) {
    override fun toString(): String = value
}

/**
 * Something a player has to decide before the engine can go on (D58): data only, never a callback, so it can wait
 * for days, cross devices or be answered by another player (P7, D11). [player] is who decides by the rules; [source]
 * the object the decision is about, when there is one. Each kind is answered with the [Answer] of the same name.
 */
@Serializable
sealed interface Decision {
    val id: DecisionId
    val player: PlayerId
    val source: ObjectId?

    /**
     * The decision's kind in words ("Divide", "Targets"), the same on every platform, for keys a host keeps or sends.
     * Written out, not the class's runtime name: a shrunk Android build may name a nested class "Decision$Divide" where
     * the JVM says "Divide".
     */
    val kind: String get() = when (this) {
        is Targets -> "Targets"
        is Modes -> "Modes"
        is Announce -> "Announce"
        is ManaPayment -> "ManaPayment"
        is May -> "May"
        is ChooseObjects -> "ChooseObjects"
        is ChooseOption -> "ChooseOption"
        is ChooseColor -> "ChooseColor"
        is ChooseSubtype -> "ChooseSubtype"
        is ChooseCardType -> "ChooseCardType"
        is ChooseCardName -> "ChooseCardName"
        is ChooseNumber -> "ChooseNumber"
        is ChoosePlayer -> "ChoosePlayer"
        is Divide -> "Divide"
        is OrderTriggers -> "OrderTriggers"
        is ChooseReplacement -> "ChooseReplacement"
        is LegendRule -> "LegendRule"
        is Random -> "Random"
        is HostFill -> "HostFill"
        is OtherView -> "OtherView"
        is Manual -> "Manual"
    }

    /** Choose targets for slot [slot] among [options] (CR 601.2c, 603.3d): [min] to [max] (null: any number). */
    @Serializable @SerialName("targets")
    data class Targets(
        override val id: DecisionId, override val player: PlayerId, override val source: ObjectId?,
        val slot: String, val options: List<TargetRef>, val min: Int, val max: Int?,
    ) : Decision

    /** Choose modes (CR 700.2, 601.2b): [min] to [max] of [options], by index; the same one again if [repeatable]. */
    @Serializable @SerialName("modes")
    data class Modes(
        override val id: DecisionId, override val player: PlayerId, override val source: ObjectId?,
        val options: List<ModeOption>, val min: Int, val max: Int, val repeatable: Boolean = false,
        /** blb: the chosen modes' pawprints add up to at most this (CR 700.2i). */
        val pawprintLimit: Int? = null,
    ) : Decision

    /** Announce the value of [name] ("X", CR 107.3a, 601.2b): at least [min], at most [max] when known. */
    @Serializable @SerialName("announce")
    data class Announce(
        override val id: DecisionId, override val player: PlayerId, override val source: ObjectId?,
        val name: String, val min: Int, val max: Int? = null,
    ) : Decision

    /** Pay [cost] (CR 601.2g–h, 605.3): which mana in the pool, which mana abilities, which other ways ([options]);
     * [suggestion] is the engine's suggested payment (§7.8), empty when it found none. */
    @Serializable @SerialName("mana-payment")
    data class ManaPayment(
        override val id: DecisionId, override val player: PlayerId, override val source: ObjectId?,
        val cost: ManaCost, val options: List<ManaUse>, val suggestion: List<ManaUse>,
    ) : Decision

    /** Yes or no: "you may …" (CR 608.2d), or pay [cost] on resolution (CR 118.12, D40) when given. Not [payable]: the
     * cost can't be paid and only "no" is accepted; asked rather than skipped because the player's "Skip when I have no
     * choice" is off and the skip would depend on cards only they may see (I-D18). */
    @Serializable @SerialName("may")
    data class May(
        override val id: DecisionId, override val player: PlayerId, override val source: ObjectId?,
        val text: String, val cost: Cost? = null, val payable: Boolean = true,
    ) : Decision

    /** Choose [min] to [max] (null: any number) of [options], not targeting (CR 608.2d, 701.x): a card to discard, a
     * creature to sacrifice, cards from among them. [text] says what for. */
    @Serializable @SerialName("objects")
    data class ChooseObjects(
        override val id: DecisionId, override val player: PlayerId, override val source: ObjectId?,
        val options: List<ObjectId>, val min: Int, val max: Int?, val text: String,
    ) : Decision

    /** Choose one of named [options] ("odd or even"). [text], when given, says what the choice is about where neither a
     * source nor the options do: the engine's own questions, such as the loop choice ([LoopStop]), or how to pay a cost
     * with two ways (forage, CR 701.61a). [naming]: the object an
     * option names (a mulligan action's card, CR 103.5b), so answers naming a card hidden from a player aren't listed to them
     * (D60, P7) and a host shows such an option only to the players who may see that card. */
    @Serializable @SerialName("option")
    data class ChooseOption(
        override val id: DecisionId, override val player: PlayerId, override val source: ObjectId?,
        val options: List<String>,
        val text: String? = null,
        val naming: Map<String, ObjectId> = emptyMap(),
    ) : Decision

    /** Choose a colour among [among]. */
    @Serializable @SerialName("color")
    data class ChooseColor(
        override val id: DecisionId, override val player: PlayerId, override val source: ObjectId?,
        val among: List<Color>,
    ) : Decision

    /** Choose a subtype: a creature type when [among] is null (CR 205.3e), else one of [among] (basic land types). */
    @Serializable @SerialName("subtype")
    data class ChooseSubtype(
        override val id: DecisionId, override val player: PlayerId, override val source: ObjectId?,
        val among: List<Subtype>? = null,
    ) : Decision

    /** Choose a card type among [among]. */
    @Serializable @SerialName("card-type")
    data class ChooseCardType(
        override val id: DecisionId, override val player: PlayerId, override val source: ObjectId?,
        val among: List<CardType>,
    ) : Decision

    /** Name a card matching [filter] (CR 201.3). */
    @Serializable @SerialName("card-name")
    data class ChooseCardName(
        override val id: DecisionId, override val player: PlayerId, override val source: ObjectId?,
        val filter: ObjectFilter,
    ) : Decision

    /** Choose a number from [min] to [max] (null: no upper limit). */
    @Serializable @SerialName("number")
    data class ChooseNumber(
        override val id: DecisionId, override val player: PlayerId, override val source: ObjectId?,
        val min: Int, val max: Int?,
    ) : Decision

    /** Choose a player among [options]. */
    @Serializable @SerialName("player")
    data class ChoosePlayer(
        override val id: DecisionId, override val player: PlayerId, override val source: ObjectId?,
        val options: List<PlayerId>,
    ) : Decision

    /** Divide [amount] among [among], at least [minEach] each (CR 601.2d); also assigning an attacker's combat damage
     * among its blockers (CR 510.1c–d), where [lethal] says what each needs before the next can get damage beyond it
     * and the defending player or planeswalker may be among [among] for trample; [suggested] is a legal division to
     * offer first (combat: lethal damage to each in turn, the rest to the defender with trample, else to the first). */
    @Serializable @SerialName("divide")
    data class Divide(
        override val id: DecisionId, override val player: PlayerId, override val source: ObjectId?,
        val amount: Int, val among: List<TargetRef>, val minEach: Int, val lethal: List<Share> = emptyList(),
        val suggested: List<Share> = emptyList(),
    ) : Decision

    /** Order triggered abilities that triggered at once (CR 603.3b): the first in [Answer.Order] goes on the Stack first. */
    @Serializable @SerialName("order-triggers")
    data class OrderTriggers(
        override val id: DecisionId, override val player: PlayerId, override val source: ObjectId? = null,
        val triggers: List<TriggerOption>,
    ) : Decision

    /** Choose which replacement effect applies first (CR 616.1, D36; one at a time, the rest are checked again). */
    @Serializable @SerialName("replacement")
    data class ChooseReplacement(
        override val id: DecisionId, override val player: PlayerId, override val source: ObjectId?,
        val options: List<ReplacementOption>,
    ) : Decision

    /** The legend rule (CR 704.5j): keep one of [among], which share the name [name]; the rest go to the graveyard. */
    @Serializable @SerialName("legend-rule")
    data class LegendRule(
        override val id: DecisionId, override val player: PlayerId, override val source: ObjectId? = null,
        val name: String, val among: List<ObjectId>,
    ) : Decision

    /** A random result the host answers at once with its own randomness (D25). */
    @Serializable @SerialName("random")
    data class Random(
        override val id: DecisionId, override val player: PlayerId, override val source: ObjectId?,
        val what: RandomKind,
    ) : Decision

    /**
     * The identities of unknown [objects], exactly the cards whose identity now matters or becomes visible (D60, spec
     * §7.0 "Hidden information"): searched, looked at, revealed, put into a public zone, a face-down card leaving or
     * turning face up, a card the viewer may look at. The host answers [Answer.Filled] for exactly these objects.
     * [public]: everyone sees them now (revealed, put into a graveyard, exile or the battlefield face up, CR 708.9);
     * else only [player] looks at them. In a viewer's state ([arcanum.engine.state.GameState.viewer]) a resolution
     * never asks another player's private fill: it asks [OtherView] instead.
     */
    @Serializable @SerialName("host-fill")
    data class HostFill(
        override val id: DecisionId, override val player: PlayerId, override val source: ObjectId?,
        val objects: List<ObjectId>, val reason: String, val public: Boolean = false,
        /** mh2: the card is the one with this name, not one in the game ("create a copy of the card with the chosen name"). */
        val named: String? = null,
    ) : Decision

    /**
     * In a viewer's state ([arcanum.engine.state.GameState.viewer], D60), a resolution reached cards only [player] may
     * look at (a search, scry, surveil, look or a choice among hidden cards; [reason] says which), so the rest of it is
     * worked out as in [player]'s view (D31): the host has [player] decide in their view and answers
     * [Answer.OtherView] with the identities of [objects] (each one this view doesn't know whose identity the rest of
     * the resolution needs so far, in the order it needed them) and the answers given in [player]'s view since the
     * first [answered] (decisions of everyone, random results; never host fills), in the order given there.
     *
     * The engine works the rest of the resolution out with those identities and keeps none of them: its changes say
     * only what this view may see (cards put into a public zone or revealed are filled in then; the others stay
     * unknown, also in the changes). While the answers don't reach the end of the resolution, or another card's
     * identity is needed, it asks again with the answers so far ([answered] grows) and [objects] as needed. Nothing
     * is kept between the calls but the answers, so the identities are sent with every answer.
     */
    @Serializable @SerialName("other-view")
    data class OtherView(
        override val id: DecisionId, override val player: PlayerId, override val source: ObjectId?,
        val objects: List<ObjectId>, val reason: String, val answered: Int = 0,
    ) : Decision

    /** Something to do by hand (D61): [text] is shown; answered [Answer.Done]. */
    @Serializable @SerialName("manual")
    data class Manual(
        override val id: DecisionId, override val player: PlayerId, override val source: ObjectId?,
        val text: String,
    ) : Decision
}

/** One mode of a modal spell or ability (CR 700.2), with its printed [text] when known. */
@Serializable
data class ModeOption(val index: Int, val text: String?, val available: Boolean = true, val pawprints: Int = 0)

/** [amount] for [target], in a division. */
@Serializable
data class Share(val target: TargetRef, val amount: Int)

/** One triggered ability waiting to go on the Stack: [index] into the waiting list, its [source], its printed [text] (a
 * keyword's name for a keyword's trigger) and which of its source's abilities it is ([origin], D23). */
@Serializable
data class TriggerOption(val index: Int, val source: ObjectId?, val text: String?, val origin: arcanum.engine.view.AbilityOrigin? = null)

/** One replacement effect that could apply ([index] for the answer), from [source], with its [text]. */
@Serializable
data class ReplacementOption(val index: Int, val source: ObjectId?, val text: String?)

/** What kind of random result a [Decision.Random] needs (D25). */
@Serializable
sealed interface RandomKind {
    @Serializable @SerialName("coin") data object CoinFlip : RandomKind
    /** [count] rolls of a [sides]-sided die (CR 706). */
    @Serializable @SerialName("die") data class Die(val sides: Int, val count: Int) : RandomKind
    /**
     * [count] of [among] at random, in random order, answered [Answer.RandomObjects] with distinct objects of [among]; a
     * shuffle asks for all of a library's cards this way (D25). [newIds]: the order is hidden from the state's viewer
     * (a shuffle or "in a random order" into a library in a viewer's state, D60): the answer is [count] new card
     * objects instead, ids no object in the state has, top first; the host keeps which card each one is, and the
     * engine knows them as unknown cards.
     *
     * [library]: for a shuffle, the whole library of the deciding player as it is when shuffled, top first ([among] and
     * the cards the shuffle leaves out, CR 701.24b); empty for other random picks. It may differ from the library of
     * the state the decision is asked in, because a step's changes are made once its decisions are answered: cards found
     * by a search earlier in the same step have left it ("search …, put them onto the battlefield, then shuffle").
     */
    @Serializable @SerialName("objects") data class Objects(val among: List<ObjectId>, val count: Int, val newIds: Boolean = false,
        val library: List<ObjectId> = emptyList()) : RandomKind
}

/**
 * One way to pay part of a mana cost (CR 601.2g–h): mana already in the pool, a mana ability to activate (with the
 * colour it should make when it makes a choice), objects used another way (convoke, delve, improvise), or life for a
 * Phyrexian symbol (CR 107.4f).
 */
@Serializable
sealed interface ManaUse {
    /** The mana at [index] of the payer's pool. */
    @Serializable @SerialName("pool") data class Pool(val index: Int) : ManaUse
    /** Activate mana ability [ability] of [source], choosing [color] when it makes a choice; for an ability that makes one
     * colour, [color] names it (telling a dual land's two abilities apart). */
    @Serializable @SerialName("ability") data class Ability(
        val source: ObjectId, val ability: arcanum.engine.view.AbilityOrigin, val color: Color? = null,
    ) : ManaUse
    /** Tap or exile [objects] another way ([arcanum.api.ManaPaymentWay]). */
    @Serializable @SerialName("special") data class Special(val way: arcanum.api.ManaPaymentWay, val objects: List<ObjectId>) : ManaUse
    /** Pay 2 life for the Phyrexian symbol at [symbol] (its index in the cost). */
    @Serializable @SerialName("life") data class Life(val symbol: Int) : ManaUse
}

internal object DecisionIdSerializer : KSerializer<DecisionId> {
    override val descriptor = PrimitiveSerialDescriptor("arcanum.engine.DecisionId", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: DecisionId) = encoder.encodeString(value.value)
    override fun deserialize(decoder: Decoder) = DecisionId(decoder.decodeString())
}
