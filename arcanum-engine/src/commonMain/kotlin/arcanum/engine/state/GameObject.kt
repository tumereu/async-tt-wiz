package arcanum.engine.state

import arcanum.api.CardDefinition
import arcanum.api.Color
import arcanum.api.Cost
import arcanum.api.CounterKind
import arcanum.api.KeywordKind
import arcanum.api.KeywordKindSerializer
import arcanum.api.Layout
import arcanum.api.ObjectMark
import arcanum.api.Zone
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A card or token as an object in one zone (CR 109, 400.7): what the host knows about it, before any continuous
 * effect is applied. Its characteristics after layers come from the view ([arcanum.engine.Engine.view]).
 *
 * Immutable: a [arcanum.engine.change.Change] replaces it with a changed copy. Moving to another zone makes a new
 * object with a new [id] (D21).
 */
@Serializable
data class GameObject(
    val id: ObjectId.Card,
    val owner: PlayerId,
    /** The player it entered the battlefield (or was put on the Stack) under (CR 110.2); null in other zones, where
     * the owner answers for it (CR 108.4a). Control-changing effects apply in layer 2 of the view, not here. */
    val controller: PlayerId? = null,
    val zone: Zone,
    /** The card's definition (the state has full information: every card is known). */
    val definition: CardDefinition,
    /** Which face is up: 0 the front; 1 the back of a transformed or modal double-faced card, the flipped half of a
     * flip card (CR 710), or the second half / Adventure being cast (CR 709, 715). */
    val face: Int = 0,
    /** A token (CR 111); [definition] holds its characteristics as created. */
    val token: Boolean = false,
    // dsk gaps
    /** A copy of a card (CR 707.12), not a card; [definition] holds the copied card's characteristics. It ceases to exist
     * in any zone other than the Stack or the battlefield (CR 704.5e). */
    val copyOfCard: Boolean = false,
    // fra
    /** A copy of this prepared permanent's prepare spell (CR 722.3c): kept in exile while it stays prepared, and as it is cast
     * a prepare spell (722.3d). */
    val prepareSpellOf: ObjectId.Card? = null,
    val tapped: Boolean = false,
    /** Face down (CR 708): a 2/2 with no name, text, types other than creature, or mana cost (CR 708.2a). */
    val faceDown: Boolean = false,
    /** Phased out: treated as though it doesn't exist (CR 702.26b). */
    val phasedOut: Boolean = false,
    /** Marks only effects put on it: monstrous, suspected, saddled, … ([arcanum.api.Effect.Mark]). */
    val marks: List<ObjectMark> = emptyList(),
    val counters: List<CounterEntry> = emptyList(),
    /** Damage marked on it (CR 120.3e), removed in the cleanup step. */
    val damage: Int = 0,
    /** Dealt damage by a source with deathtouch since the last state-based action check (CR 704.5h). */
    val deathtouchDamage: Boolean = false,
    /** The object or player this Aura, Equipment or Fortification is attached to (CR 301.5, 303.4). */
    val attachedTo: TargetRef? = null,
    /** CR 613.7: set as it enters a zone, becomes attached, turns face up or down, or transforms (D34). */
    val timestamp: Long,
    /** Who has controlled it continuously since which turn: the summoning sickness rule (CR 302.6, D39). */
    val controlSince: ControlSince? = null,
    /** Card memory (CR 607): chosen colours and names, "exiled with", keyword-linked cards (D41). */
    val memory: Bindings = Bindings.EMPTY,
    /** What the engine remembers about this object instance (D22, D41): cast facts, activations per turn. */
    val facts: ObjectFacts = ObjectFacts(),
    /** Players who goaded it (CR 701.15). */
    val goadedBy: List<PlayerId> = emptyList(),
    /** A merged permanent's other components above its own card, topmost first (mutate, CR 730.2, 702.140c): the topmost
     * component gives its characteristics other than abilities (730.2a, 702.140e). */
    val mergedAbove: List<MergedPart> = emptyList(),
    /** A merged permanent's other components under its own card, topmost first (CR 730.2). */
    val mergedBelow: List<MergedPart> = emptyList(),
    /** A battle's protector (CR 310.9): chosen as it enters (310.9a, 310.12a) or by the state-based action of 310.11; it
     * stays when the permanent stops being a battle (310.9g). */
    val protector: PlayerId? = null,
    /** A permanent that isn't a flip card flipped while a copy of one (CR 707.3, 110.5c); its [face] stays what is up. */
    val flippedStatus: Boolean = false,
    /** A token's copiable abilities that are in none of its faces ([CopiableValues.gained], CR 707.9a, 702.140e). */
    val gainedInCopying: List<arcanum.api.Ability> = emptyList(),
    /** The copiable values each static copy effect that applies to it fixed as it first started to apply (CR 707.2c), kept while it
     * applies ([arcanum.engine.view.StaticCopies]). */
    val fixedCopies: List<FixedCopy> = emptyList(),
) {
    val card: CardId get() = id.card

    /** A transformed double-faced permanent (CR 712, 701.27g): its back face is up. What it is physically: a permanent
     * copying a double-faced permanent isn't double-faced (CR 712.9), a token copy of one is (CR 707.8a, its [definition]). */
    val transformed: Boolean get() = definition.layout == Layout.Transform && face == 1

    /** A bestowed Aura spell or permanent (CR 702.103b): cast bestowed, on the Stack or the battlefield, and not ceased
     * to be bestowed (702.103e, f, which clear [ObjectFacts.alternative]). */
    val bestowed: Boolean get() = facts.alternative?.keyword == arcanum.api.Keyword.Bestow.Kind && (zone == arcanum.api.Zone.Stack ||
        zone == arcanum.api.Zone.Battlefield)

    /** Flipped (CR 710), a status (CR 110.5) kept whatever it copies later (CR 110.5c): a flip card's is its [face]; any other
     * permanent's, flipped while a copy of a flip card, is [flippedStatus]. */
    val flipped: Boolean get() = if (definition.layout == Layout.Flip) face == 1 else flippedStatus

    fun counters(kind: CounterKind): Int = counters.firstOrNull { it.kind == kind }?.count ?: 0

    /** A merged permanent: represented by more than one card or token (CR 730.2). */
    val merged: Boolean get() = mergedAbove.isNotEmpty() || mergedBelow.isNotEmpty()

    /** A token (CR 111.1); a merged permanent only if its topmost component is one (CR 730.2d). */
    val isToken: Boolean get() = mergedAbove.firstOrNull()?.token ?: token
}

/** The copiable [values] the static copy effect of [source]'s printed ability number [ability] fixed for an object as it first
 * started to apply to it (CR 707.2c). */
@Serializable
data class FixedCopy(val source: ObjectId, val ability: Int, val values: CopiableValues)

/**
 * One card or token among those representing a merged permanent other than its own card (CR 730.2): the object it was as it
 * merged ([id]; as the merged permanent leaves the battlefield it becomes [ObjectId.Card.next] in the new zone, CR 730.3), with
 * its definition.
 */
@Serializable
data class MergedPart(
    val id: ObjectId.Card,
    val owner: PlayerId,
    val definition: CardDefinition,
    val face: Int = 0,
    val token: Boolean = false,
    val copyOfCard: Boolean = false,
    /** When it merged (CR 730.2a: the merge is a copy effect with this timestamp, applied in timestamp order among the
     * permanent's copy effects, CR 613.7); 0: before every copy effect. */
    val timestamp: Long = 0,
)

/** [count] counters of [kind], with the timestamp the kind last got (CR 613.7c). */
@Serializable
data class CounterEntry(val kind: CounterKind, val count: Int, val timestamp: Long)

/** [player] has controlled the object continuously since the start of turn [turn] (CR 302.6). */
@Serializable
data class ControlSince(val player: PlayerId, val turn: Int)

/**
 * Facts about one object instance (D22, D33, D41): how it was cast, what was paid, and how often its abilities were
 * used in which turn. A permanent keeps the cast facts of the spell it was (CR 400.7d); everything else starts fresh
 * with a new object.
 */
@Serializable
data class ObjectFacts(
    /** The zone it was cast from; null if it wasn't cast. */
    val castFrom: Zone? = null,
    /** The value of X chosen when cast (CR 107.3m). */
    val x: Int? = null,
    /** Mana spent to cast it, by kind. */
    val manaSpent: List<ManaSpent> = emptyList(),
    /** Optional and additional costs paid, with how often (kicker, CR 702.33). */
    val paid: List<PaidCost> = emptyList(),
    /** The alternative cost it was cast for, by keyword (evoke, dash, …), or [PaidCost.WITHOUT_KEYWORD]. */
    val alternative: PaidCost? = null,
    /** Mode indices ever chosen for it ("choose one that hasn't been chosen"). */
    val modesChosen: List<Int> = emptyList(),
    /** The player chosen by a "choose a player" cost (gift's opponent, CR 702.174a). */
    val chosenPlayer: PlayerId? = null,
    /** Objects used to pay its costs a special way (convoke, delve; CR 702.51c), by the way's name. */
    val paidWith: Bindings = Bindings.EMPTY,
    /** Activations of its abilities by turn (CR 602.5b, 606.3); key is the ability's origin key. */
    val activations: List<TurnCount> = emptyList(),
    /** Resolutions of its abilities by turn ("if this is the second time this ability has resolved this turn"). */
    val resolutions: List<TurnCount> = emptyList(),
    /** Triggers of its abilities by turn ("triggers only once each turn", CR 603.2h). */
    val triggers: List<TurnCount> = emptyList(),
    /** Modes chosen by turn ("choose one that hasn't been chosen this turn"); key is the mode's index. */
    val modeTurns: List<TurnCount> = emptyList(),
    // gtc
    /** Cast as a fused split spell (CR 702.102): on the Stack it has both halves' characteristics (CR 709.4d). */
    val fused: Boolean = false,
    // fra
    /** It has dealt combat damage since it became this object ("as long as they haven't dealt combat damage yet"). */
    val dealtCombatDamage: Boolean = false,
    // tdm
    /** It has dealt damage, combat or not, since it became this object ("as long as it hasn't dealt damage yet"). */
    val dealtDamage: Boolean = false,
    // hob
    /** The sources of the mana spent to cast it, one entry per mana that had a source ("if mana from a Treasure was spent",
     * CR 106.3); read as they last existed once they have left. */
    val manaSources: List<ObjectId> = emptyList(),
    /** How many Phyrexian mana symbols of its cost were paid with life as it was cast (compleated, CR 702.150a). */
    val phyrexianPaidWithLife: Int = 0,
    // mh2
    /** Phased out until another object leaves the battlefield (CR 702.26, 610.3): it doesn't phase in during untap steps. */
    val heldPhasedOut: Boolean = false,
    /** The marks it gained whose [arcanum.api.MarkRule.recordsTurn] asks to keep when, one entry per mark, the latest. */
    val marksGained: List<MarkGained> = emptyList(),
    /** It has been turned face up since it became this object: turned face down again by an effect that lists no characteristics,
     * it is a plain 2/2, not what the way it was cast face down gave it (a disguise's ward, CR 708.2a). */
    val turnedFaceUp: Boolean = false,
) {
    /** The record of when it gained [mark] (CR 702.143a, 702.170d), or null when none is kept. */
    fun gained(mark: ObjectMark): MarkGained? = marksGained.firstOrNull { it.mark == mark }

    /** These facts with [record] as the record of its mark. */
    fun gaining(record: MarkGained): ObjectFacts = copy(marksGained = marksGained.filter { it.mark != record.mark } + record)
}

/**
 * The object gained [mark] in turn [turn] (CR 702.143a, 702.170d). [costs]: the costs it had then of the keyword [mark]'s
 * [arcanum.api.MarkCost.OfKeyword] names, those another object gave it included (CR 702.143e).
 */
@Serializable
data class MarkGained(val mark: ObjectMark, val turn: Int, val costs: List<Cost> = emptyList())

/** Mana of one kind spent: a colour, or colourless when [color] is null. */
@Serializable
data class ManaSpent(val color: Color?, val amount: Int)

/** A cost paid [times] times: the cost of [keyword] (the [costIndex]th of its costs), or the spell's own additional
 * cost when [keyword] is null. */
@Serializable
data class PaidCost(
    @Serializable(with = KeywordKindSerializer::class) val keyword: KeywordKind?,
    val times: Int,
    val costIndex: Int? = null,
) {
    companion object {
        /** An alternative cost without a keyword ("you may pay … rather than pay this spell's mana cost"). */
        val WITHOUT_KEYWORD = PaidCost(null, 1)
    }
}

/** [count] uses of ability [key] in turn [turn] (D41: per turn number, so nothing resets at turn start). */
@Serializable
data class TurnCount(val key: String, val turn: Int, val count: Int)

/** A state of an object a [arcanum.engine.change.Change.Status] switches on or off. */
@Serializable
sealed interface StatusFlag {
    @Serializable @SerialName("tapped") data object Tapped : StatusFlag
    @Serializable @SerialName("face-down") data object FaceDown : StatusFlag
    @Serializable @SerialName("phased-out") data object PhasedOut : StatusFlag
    @Serializable @SerialName("marked") data class Marked(val mark: ObjectMark) : StatusFlag
    /** [GameObject.flippedStatus]. */
    @Serializable @SerialName("flipped") data object Flipped : StatusFlag
}
