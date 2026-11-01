package arcanum.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A kind of game event, used three ways: what a [Trigger] waits for (CR 603), what a [Mechanic.Replacement] replaces
 * (CR 614), and what happened this turn ([Condition.HappenedThisTurn]). Roles that later references can use through
 * [Objects.EventObject] / [Players.EventPlayer] / [Value.EventAmount] are named in each KDoc.
 */
@Serializable
sealed interface EventPattern {
    /** An object matching [objects] moves from [from] to [to] (null = any zone). "Enters" = to battlefield; "dies" =
     * creature from battlefield to graveyard (CR 700.4). Role "object"; it is seen as it was before the move when it
     * leaves the battlefield (CR 603.10a). */
    @Serializable @SerialName("zone-change") data class ZoneChange(
        val objects: ObjectFilter = ObjectFilter.Any, val from: Zone? = null, val to: Zone? = null,
    ) : EventPattern
    /** A spell matching [spell] is cast by a player matching [by]. Roles "object" (the spell), "player". */
    @Serializable @SerialName("cast") data class Cast(val spell: ObjectFilter = ObjectFilter.Any, val by: PlayerFilter = PlayerFilter.Any) : EventPattern
    /** An ability of an object matching [source] is activated; [manaAbilities] whether mana abilities count. */
    @Serializable @SerialName("activate") data class Activate(val source: ObjectFilter = ObjectFilter.Any, val by: PlayerFilter = PlayerFilter.Any, val manaAbilities: Boolean = false) : EventPattern
    /** Damage from [source] to [to]; [combat] true = combat damage only, false = noncombat only, null = any.
     * Roles "source", "object"/"player" (the recipient); amount = the damage. */
    @Serializable @SerialName("damage") data class Damage(
        val source: ObjectFilter = ObjectFilter.Any, val to: RecipientFilter = RecipientFilter(), val combat: Boolean? = null,
    ) : EventPattern
    @Serializable @SerialName("gain-life") data class GainLife(val players: PlayerFilter = PlayerFilter.Any) : EventPattern
    @Serializable @SerialName("lose-life") data class LoseLife(val players: PlayerFilter = PlayerFilter.Any) : EventPattern
    /** A player draws; [nth] = only their nth card this turn ("your second card each turn"). */
    @Serializable @SerialName("draw") data class Draw(val players: PlayerFilter = PlayerFilter.Any, val nth: Int? = null) : EventPattern
    @Serializable @SerialName("discard") data class Discard(val cards: ObjectFilter = ObjectFilter.Any, val players: PlayerFilter = PlayerFilter.Any) : EventPattern
    @Serializable @SerialName("sacrifice") data class Sacrifice(val objects: ObjectFilter = ObjectFilter.Any) : EventPattern
    @Serializable @SerialName("counters") data class CountersPut(val objects: ObjectFilter = ObjectFilter.Any, val kind: CounterKind? = null) : EventPattern
    /** Counters of [kind] are removed from an object matching [objects]; [last]: only when that removal leaves none
     * ("when the last time counter is removed", CR 702.63a), part of the trigger event, not an intervening if. */
    @Serializable @SerialName("counters-removed") data class CountersRemoved(
        val objects: ObjectFilter = ObjectFilter.Any, val kind: CounterKind? = null, val last: Boolean = false,
    ) : EventPattern
    @Serializable @SerialName("player-counters") data class PlayerCountersPut(val players: PlayerFilter = PlayerFilter.Any, val kind: CounterKind? = null) : EventPattern
    @Serializable @SerialName("tap") data class Tap(val objects: ObjectFilter = ObjectFilter.Any, val forMana: Boolean? = null) : EventPattern
    @Serializable @SerialName("untap") data class Untap(val objects: ObjectFilter = ObjectFilter.Any) : EventPattern
    /** A creature attacks; [alone] for "attacks alone"; [target] narrows what it attacks. Role "object". */
    @Serializable @SerialName("attack") data class Attack(val attacker: ObjectFilter = ObjectFilter.Any, val alone: Boolean = false, val target: RecipientFilter? = null) : EventPattern
    /** A creature blocks [attacker]. Roles "object" (blocker), "attacker". */
    @Serializable @SerialName("block") data class Block(val blocker: ObjectFilter = ObjectFilter.Any, val attacker: ObjectFilter = ObjectFilter.Any) : EventPattern
    @Serializable @SerialName("becomes-blocked") data class BecomesBlocked(val attacker: ObjectFilter = ObjectFilter.Any) : EventPattern
    @Serializable @SerialName("unblocked") data class AttacksUnblocked(val attacker: ObjectFilter = ObjectFilter.Any) : EventPattern
    /** An object or player becomes the target of a spell or ability matching [by] (heroic, ward). Roles "object",
     * "source" (the spell or ability). */
    @Serializable @SerialName("becomes-target") data class BecomesTarget(val objects: ObjectFilter = ObjectFilter.Any, val by: ObjectFilter = ObjectFilter.Any) : EventPattern
    @Serializable @SerialName("cycle") data class Cycle(val cards: ObjectFilter = ObjectFilter.Any, val players: PlayerFilter = PlayerFilter.Any) : EventPattern
    @Serializable @SerialName("create-token") data class CreateToken(val tokens: ObjectFilter = ObjectFilter.Any) : EventPattern
    @Serializable @SerialName("transform") data class Transform(val objects: ObjectFilter = ObjectFilter.Any) : EventPattern
    @Serializable @SerialName("turned-face-up") data class TurnedFaceUp(val objects: ObjectFilter = ObjectFilter.Any) : EventPattern
    @Serializable @SerialName("search") data class Search(val players: PlayerFilter = PlayerFilter.Any) : EventPattern
    @Serializable @SerialName("countered") data class Countered(val objects: ObjectFilter = ObjectFilter.Any) : EventPattern
    @Serializable @SerialName("destroyed") data class Destroyed(val objects: ObjectFilter = ObjectFilter.Any) : EventPattern
    /** A step begins ("at the beginning of your upkeep"); [whose] is relative to the ability's controller. */
    @Serializable @SerialName("step-begins") data class StepBegins(val step: TurnStep, val whose: PlayerFilter = PlayerFilter.Any) : EventPattern
    @Serializable @SerialName("step-ends") data class StepEnds(val step: TurnStep, val whose: PlayerFilter = PlayerFilter.Any) : EventPattern
    @Serializable @SerialName("day-night") data class DayNightChanges(val toNight: Boolean? = null) : EventPattern
}

/** Who received damage or is attacked: objects matching [objects], players matching [players]. Both null = any. */
@Serializable
data class RecipientFilter(val objects: ObjectFilter? = null, val players: PlayerFilter? = null)

/** What a triggered ability waits for (CR 603). */
@Serializable
sealed interface Trigger {
    /** "When/Whenever [events]": one ability with one or more trigger events ("enters or attacks"), so it stays one
     * ability for "counter target ability" or "loses this ability". [oneOrMore]: triggers once for a batch of
     * simultaneous events ("one or more creatures", CR 603.2c). */
    @Serializable @SerialName("on") data class On(val events: List<EventPattern>, val oneOrMore: Boolean = false) : Trigger {
        constructor(event: EventPattern, oneOrMore: Boolean = false) : this(listOf(event), oneOrMore)
    }
    /** A state trigger (CR 603.8): triggers whenever [condition] becomes true, once until it resolves. */
    @Serializable @SerialName("state") data class State(val condition: Condition) : Trigger
    /** Saga chapter abilities (CR 714.2): lore counters reach one of [chapters]. */
    @Serializable @SerialName("chapter") data class Chapter(val chapters: List<Int>) : Trigger
}
