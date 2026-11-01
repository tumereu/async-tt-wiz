package arcanum.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A kind of game event, used three ways: what a [Trigger] waits for (CR 603), what a [Mechanic.Replacement] replaces
 * (CR 614), and what happened this turn ([Condition.HappenedThisTurn]). The [EventRole]s each pattern has are named in
 * its KDoc ([EventRole.Object] unless said otherwise); [Players.EventPlayer] and [Value.EventAmount] read its player
 * and amount.
 */
@Serializable
sealed interface EventPattern {
    /** An object matching [objects] moves from [from] to [to] (null = any zone). "Enters" = to battlefield; "dies" =
     * put into a graveyard from the battlefield (CR 700.4). Roles [EventRole.Before] (last known information) and
     * [EventRole.After] (the new object). Trigger conditions look back in time for leaving the battlefield, sacrifice,
     * leaving a graveyard, and an object all players can see put into a hand or library (CR 603.10a). Playing a land
     * is also [PlayLand]. */
    @Serializable @SerialName("zone-change") data class ZoneChange(
        val objects: ObjectFilter = ObjectFilter.Any, val from: Zone? = null, val to: Zone? = null,
    ) : EventPattern
    /** A spell matching [spell] is cast by a player matching [by], from [from] when given ("whenever you cast a spell
     * from exile"). Role object = the spell. */
    @Serializable @SerialName("cast") data class Cast(
        val spell: ObjectFilter = ObjectFilter.Any, val by: PlayerFilter = PlayerFilter.Any, val from: Zone? = null,
    ) : EventPattern
    /** An ability of an object matching [source] is activated; [manaAbilities] whether mana abilities count. */
    @Serializable @SerialName("activate") data class Activate(val source: ObjectFilter = ObjectFilter.Any, val by: PlayerFilter = PlayerFilter.Any, val manaAbilities: Boolean = false) : EventPattern
    /** Damage from [source] to [to]; [combat] true = combat damage only, false = noncombat only, null = any; [excess]:
     * only damage over lethal (CR 120.4a). Roles source, object (the recipient); amount = the damage. */
    @Serializable @SerialName("damage") data class Damage(
        val source: ObjectFilter = ObjectFilter.Any, val to: RecipientFilter = RecipientFilter(), val combat: Boolean? = null,
        val excess: Boolean = false,
    ) : EventPattern
    @Serializable @SerialName("gain-life") data class GainLife(val players: PlayerFilter = PlayerFilter.Any) : EventPattern
    @Serializable @SerialName("lose-life") data class LoseLife(val players: PlayerFilter = PlayerFilter.Any) : EventPattern
    /** A player draws a card ("your second card each turn" is [Trigger.On.nthThisTurn]). */
    @Serializable @SerialName("draw") data class Draw(val players: PlayerFilter = PlayerFilter.Any) : EventPattern
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
    /** A creature attacks; [alone] for "attacks alone"; [target] narrows what it attacks. */
    @Serializable @SerialName("attack") data class Attack(val attacker: ObjectFilter = ObjectFilter.Any, val alone: Boolean = false, val target: RecipientFilter? = null) : EventPattern
    /** A creature blocks [attacker]. Roles object (the blocker), attacker. */
    /** "Whenever [blocker] blocks": once per combat even if it blocks several; [perAttacker] for "blocks a creature",
     * once per attacker blocked (CR 509.3a, c). Roles object (the blocker), other combatant (the attacker). */
    @Serializable @SerialName("block") data class Block(
        val blocker: ObjectFilter = ObjectFilter.Any, val attacker: ObjectFilter = ObjectFilter.Any, val perAttacker: Boolean = false,
    ) : EventPattern
    /** "Whenever [attacker] becomes blocked": once per combat; [perBlocker] for "becomes blocked by a creature", once per
     * blocker matching [blocker] (CR 509.3d). Roles object (the attacker), other combatant (the blocker). */
    @Serializable @SerialName("becomes-blocked") data class BecomesBlocked(
        val attacker: ObjectFilter = ObjectFilter.Any, val blocker: ObjectFilter = ObjectFilter.Any, val perBlocker: Boolean = false,
    ) : EventPattern
    @Serializable @SerialName("unblocked") data class AttacksUnblocked(val attacker: ObjectFilter = ObjectFilter.Any) : EventPattern
    /** An object or player becomes the target of a spell or ability matching [by] (heroic, ward). Roles object,
     * source (the spell or ability). */
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
    /** A spell matching [spell] is copied by a player matching [by] ("whenever you copy a spell"). */
    @Serializable @SerialName("copy") data class Copy(val spell: ObjectFilter = ObjectFilter.Any, val by: PlayerFilter = PlayerFilter.Any) : EventPattern
    @Serializable @SerialName("scry") data class Scry(val players: PlayerFilter = PlayerFilter.Any) : EventPattern
    @Serializable @SerialName("surveil") data class Surveil(val players: PlayerFilter = PlayerFilter.Any) : EventPattern
    /** A player commits a crime: targets an opponent or their objects (CR 700.13). */
    @Serializable @SerialName("crime") data class CommitCrime(val players: PlayerFilter = PlayerFilter.Any) : EventPattern
    /** A player plays a land (a special action, CR 116.2a, 305.1). */
    @Serializable @SerialName("play-land") data class PlayLand(val lands: ObjectFilter = ObjectFilter.Any, val players: PlayerFilter = PlayerFilter.Any) : EventPattern
    /** An Aura, Equipment or Fortification matching [objects] becomes attached to something matching [to]. Role object
     * (the attachment). */
    @Serializable @SerialName("attach") data class Attach(val objects: ObjectFilter = ObjectFilter.Any, val to: ObjectFilter = ObjectFilter.Any) : EventPattern
    @Serializable @SerialName("roll-die") data class RollDie(val players: PlayerFilter = PlayerFilter.Any) : EventPattern
    @Serializable @SerialName("flip-coin") data class FlipCoin(val players: PlayerFilter = PlayerFilter.Any) : EventPattern
    /** A Vehicle matching [objects] becomes crewed (CR 702.122). Roles object (the Vehicle), other combatant unused. */
    @Serializable @SerialName("crewed") data class Crewed(val objects: ObjectFilter = ObjectFilter.Any) : EventPattern
    /** A Mount matching [objects] becomes saddled (CR 702.171). */
    @Serializable @SerialName("saddled") data class Saddled(val objects: ObjectFilter = ObjectFilter.Any) : EventPattern
    /** A player expends [amount]: reaches [amount] total mana spent to cast spells this turn (CR 700.14, "Whenever you expend 4"). */
    @Serializable @SerialName("expend") data class Expend(val amount: Int, val players: PlayerFilter = PlayerFilter.You) : EventPattern
}

/** Who received damage or is attacked: objects matching [objects], players matching [players]. Both null = any. */
@Serializable
data class RecipientFilter(val objects: ObjectFilter? = null, val players: PlayerFilter? = null)

/** What a triggered ability waits for (CR 603). */
@Serializable
sealed interface Trigger {
    /** "When/Whenever [events]": one ability with one or more trigger events ("enters or attacks"), so it stays one
     * ability for "counter target ability" or "loses this ability". [oneOrMore]: triggers once for a batch of
     * simultaneous events ("one or more creatures", CR 603.2c), and only when the batch has at least [atLeast]
     * ("whenever you attack with three or more creatures"). [nthThisTurn]: only the nth such event for that player this
     * turn ("your second spell each turn", "for the first time each turn"), counted within [during] when given ("the
     * first card you draw during each of your draw steps"). */
    @Serializable @SerialName("on") data class On(
        val events: List<EventPattern>, val oneOrMore: Boolean = false, val atLeast: Int? = null,
        val nthThisTurn: Int? = null, val during: TurnStep? = null,
    ) : Trigger {
        constructor(event: EventPattern, oneOrMore: Boolean = false) : this(listOf(event), oneOrMore)
    }
    /** A state trigger (CR 603.8): triggers whenever [condition] becomes true, once until it resolves. */
    @Serializable @SerialName("state") data class State(val condition: Condition) : Trigger
    /** Saga chapter abilities (CR 714.2): lore counters reach one of [chapters]. "I, II —" is written once but is one
     * chapter ability per listed chapter (CR 714.2c). */
    @Serializable @SerialName("chapter") data class Chapter(val chapters: List<Int>) : Trigger
}
