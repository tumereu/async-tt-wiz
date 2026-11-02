package arcanum.engine.change

import arcanum.api.CounterKind
import arcanum.api.Designation
import arcanum.api.Zone
import arcanum.engine.Pending
import arcanum.engine.state.CombatState
import arcanum.engine.state.CounterEntry
import arcanum.engine.state.DelayedTrigger
import arcanum.engine.state.EffectObject
import arcanum.engine.state.GameObject
import arcanum.engine.state.ManaUnit
import arcanum.engine.state.NamedValue
import arcanum.engine.state.ObjectFacts
import arcanum.engine.state.ObjectId
import arcanum.engine.state.ObjectSnapshot
import arcanum.engine.state.PlayerId
import arcanum.engine.state.PriorityState
import arcanum.engine.state.StackItem
import arcanum.engine.state.StatusFlag
import arcanum.engine.state.TargetRef
import arcanum.engine.state.TurnState
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One MTG-level change to a [arcanum.engine.state.GameState] (D56, D59): a zone change, a tap, counters, life, mana,
 * Stack items, effect objects, steps and priority, ledgers, the open continuation. Each carries its before- and
 * after-values, so [arcanum.engine.state.GameState.apply] and [arcanum.engine.state.GameState.revert] are mechanical. Event changes carry
 * their Magic meaning ([Meaning]: cast, died, sacrificed, drew, …) and are logged as the turn's tagged events (D33);
 * [isEvent] says which. [Reverted] takes a change back inside a batch (cancelling a cast, D58).
 *
 * Exceptions to exact reversal, by design (D59): the timestamp counter never goes back (D34).
 */
@Serializable
sealed interface Change {
    /** The change that undoes this one's effect on the state (logging aside: [arcanum.engine.state.GameState.revert] also
     * removes the logged event). Pure events ([Damage], [Happened]) change no state and are their own inverse. */
    fun inverse(): Change

    /** Logged as a tagged event of the turn (D33); bookkeeping (priority, ledgers, the continuation) is not. */
    val isEvent: Boolean get() = true

    /**
     * One simultaneous zone change of one or more objects (CR 400.7, D44: one tagged event). Each object leaves its
     * zone as [Move.before] and arrives as [Move.after], a new object with a new id.
     */
    @Serializable @SerialName("zone-change")
    data class ZoneChange(val moves: List<Move>) : Change {
        override fun inverse() = ZoneChange(moves.reversed().map { it.inverse() })
    }

    /** Objects come into existence: tokens (CR 111), emblems (CR 114); [Placed.index] is the position in the zone. */
    @Serializable @SerialName("created")
    data class Created(val objects: List<Placed>, val meanings: List<Meaning> = listOf(Meaning.TokenCreated)) : Change {
        override fun inverse() = Removed(objects.reversed(), meanings)
    }

    /** Objects cease to exist: a token that left the battlefield (CR 111.7, 704.5d). Inverse of [Created]. */
    @Serializable @SerialName("removed")
    data class Removed(val objects: List<Placed>, val meanings: List<Meaning> = listOf(Meaning.CeasedToExist)) : Change {
        override fun inverse() = Created(objects.reversed(), meanings)
    }

    /** A status of an object switches: tapped (CR 701.26), face down, phased out, a mark. */
    @Serializable @SerialName("status")
    data class Status(
        val id: ObjectId, val flag: StatusFlag, val before: Boolean, val after: Boolean,
        val meanings: List<Meaning> = emptyList(),
    ) : Change {
        override fun inverse() = copy(before = after, after = before)
    }

    /** The face that is up changes: transform (CR 701.28), flip (CR 710), turning a split card's half. */
    @Serializable @SerialName("face")
    data class Face(val id: ObjectId, val before: Int, val after: Int, val meanings: List<Meaning> = emptyList()) : Change {
        override fun inverse() = copy(before = after, after = before)
    }

    /** Counters of [kind] on an object or player change from [before] to [after] (null: none; CR 122). */
    @Serializable @SerialName("counters")
    data class Counters(
        val holder: TargetRef, val kind: CounterKind, val before: CounterEntry?, val after: CounterEntry?,
        val meanings: List<Meaning> = emptyList(),
    ) : Change {
        override fun inverse() = copy(before = after, after = before)
    }

    /**
     * One damage event (CR 120, D44): every source and recipient dealt damage at once. The results are their own
     * changes ([DamageMarked], [Life], [Counters]); this one is the event that triggers and is counted.
     */
    @Serializable @SerialName("damage")
    data class Damage(val dealt: List<DamageDealt>) : Change {
        override fun inverse() = this
    }

    /** Damage marked on a permanent changes (CR 120.3e, 120.6), with "dealt damage by deathtouch" (CR 704.5h). */
    @Serializable @SerialName("damage-marked")
    data class DamageMarked(
        val id: ObjectId, val before: Int, val after: Int,
        val deathtouchBefore: Boolean = false, val deathtouchAfter: Boolean = false,
    ) : Change {
        override fun inverse() = copy(before = after, after = before, deathtouchBefore = deathtouchAfter, deathtouchAfter = deathtouchBefore)
    }

    /** A player's life total changes (CR 119). */
    @Serializable @SerialName("life")
    data class Life(val player: PlayerId, val before: Int, val after: Int, val meanings: List<Meaning> = emptyList()) : Change {
        override fun inverse() = copy(before = after, after = before)
    }

    /** A player's mana pool changes: mana added, spent or emptied (CR 106.4). */
    @Serializable @SerialName("mana-pool")
    data class ManaPool(
        val player: PlayerId, val before: List<ManaUnit>, val after: List<ManaUnit>, val meanings: List<Meaning> = emptyList(),
    ) : Change {
        override fun inverse() = copy(before = after, after = before)
    }

    /** A spell's or ability's choices go on the Stack. [index] is the Stack position for an ability or copy (its id is
     * inserted there); null for a spell, whose card a [ZoneChange] put on the Stack. */
    @Serializable @SerialName("stack-item-added")
    data class StackItemAdded(val item: StackItem, val index: Int?, val meanings: List<Meaning> = emptyList()) : Change {
        override fun inverse() = StackItemRemoved(item, index, meanings)
    }

    /** A Stack item's record goes: it resolved, was countered, or its ability or copy left the Stack. */
    @Serializable @SerialName("stack-item-removed")
    data class StackItemRemoved(val item: StackItem, val index: Int?, val meanings: List<Meaning> = emptyList()) : Change {
        override fun inverse() = StackItemAdded(item, index, meanings)
    }

    /** A Stack item's choices change (new targets, CR 115.7; a new controller). */
    @Serializable @SerialName("stack-item-updated")
    data class StackItemUpdated(val before: StackItem, val after: StackItem, val meanings: List<Meaning> = emptyList()) : Change {
        override fun inverse() = copy(before = after, after = before)
    }

    /** An effect object is created (CR 611.2, D19). */
    @Serializable @SerialName("effect-added")
    data class EffectAdded(val effect: EffectObject) : Change {
        override fun inverse() = EffectRemoved(effect)
    }

    /** An effect object ends: its duration is over, or its shield is used up (D26). */
    @Serializable @SerialName("effect-removed")
    data class EffectRemoved(val effect: EffectObject, val meanings: List<Meaning> = listOf(Meaning.EffectEnded)) : Change {
        override fun inverse() = EffectAdded(effect)
    }

    /** An effect object changes: a shield's uses or amount go down (D26), its locked set follows a spell (CR 400.7d). */
    @Serializable @SerialName("effect-updated")
    data class EffectUpdated(val before: EffectObject, val after: EffectObject) : Change {
        override fun inverse() = copy(before = after, after = before)
        override val isEvent: Boolean get() = false
    }

    @Serializable @SerialName("delayed-trigger-added")
    data class DelayedTriggerAdded(val trigger: DelayedTrigger) : Change {
        override fun inverse() = DelayedTriggerRemoved(trigger)
    }

    /** A delayed trigger ends: it triggered (once), or its duration is over (CR 603.7b). */
    @Serializable @SerialName("delayed-trigger-removed")
    data class DelayedTriggerRemoved(val trigger: DelayedTrigger, val meanings: List<Meaning> = emptyList()) : Change {
        override fun inverse() = DelayedTriggerAdded(trigger)
    }

    /** An Aura, Equipment or Fortification becomes attached, moves or becomes unattached (CR 701.3); attaching gives
     * it a new timestamp (CR 613.7e). */
    @Serializable @SerialName("attached")
    data class Attached(
        val id: ObjectId, val before: TargetRef?, val after: TargetRef?,
        val timestampBefore: Long, val timestampAfter: Long,
    ) : Change {
        override fun inverse() = copy(before = after, after = before, timestampBefore = timestampAfter, timestampAfter = timestampBefore)
    }

    /** An object gets a new timestamp without moving: turned face up or down, transformed (CR 613.7f, g). */
    @Serializable @SerialName("restamped")
    data class Restamped(val id: ObjectId, val before: Long, val after: Long) : Change {
        override fun inverse() = copy(before = after, after = before)
        override val isEvent: Boolean get() = false
    }

    /** Card memory under [key] changes (CR 607): null is nothing kept. */
    @Serializable @SerialName("memory")
    data class Memory(val id: ObjectId, val key: String, val before: NamedValue?, val after: NamedValue?) : Change {
        override fun inverse() = copy(before = after, after = before)
        override val isEvent: Boolean get() = false
    }

    /** What the engine remembers about an object instance changes (D22, D41). */
    @Serializable @SerialName("facts")
    data class Facts(val id: ObjectId, val before: ObjectFacts, val after: ObjectFacts) : Change {
        override fun inverse() = copy(before = after, after = before)
        override val isEvent: Boolean get() = false
    }

    /** Who has controlled an object continuously since when changes (CR 302.6). */
    @Serializable @SerialName("control-start")
    data class ControlStart(
        val id: ObjectId, val before: arcanum.engine.state.ControlSince?, val after: arcanum.engine.state.ControlSince?,
    ) : Change {
        override fun inverse() = copy(before = after, after = before)
        override val isEvent: Boolean get() = false
    }

    /** Fields of an object that have no change of their own (goaded). Same id and zone before and after. */
    @Serializable @SerialName("object-updated")
    data class ObjectUpdated(val before: GameObject, val after: GameObject, val meanings: List<Meaning> = emptyList()) : Change {
        override fun inverse() = copy(before = after, after = before)
    }

    /** The turn, step or active player changes (CR 500); [meanings] say a turn or step began. */
    @Serializable @SerialName("step")
    data class Step(val before: TurnState, val after: TurnState, val meanings: List<Meaning> = emptyList()) : Change {
        override fun inverse() = copy(before = after, after = before)
    }

    /** Priority passes or is given (CR 117, D57). */
    @Serializable @SerialName("priority")
    data class Priority(val before: PriorityState, val after: PriorityState, val meanings: List<Meaning> = emptyList()) : Change {
        override fun inverse() = copy(before = after, after = before)
        override val isEvent: Boolean get() = false
    }

    /** Attackers or blockers are declared, or combat ends (CR 506–511). */
    @Serializable @SerialName("combat")
    data class Combat(val before: CombatState?, val after: CombatState?, val meanings: List<Meaning> = emptyList()) : Change {
        override fun inverse() = copy(before = after, after = before)
    }

    /** A ledger entry changes (D22): game-scoped when [player] is null. */
    @Serializable @SerialName("ledger")
    data class Ledger(val player: PlayerId?, val key: String, val before: Int, val after: Int) : Change {
        override fun inverse() = copy(before = after, after = before)
        override val isEvent: Boolean get() = false
    }

    /** A player loses the game (CR 104.3). */
    @Serializable @SerialName("lost")
    data class Lost(val player: PlayerId, val before: Boolean, val after: Boolean) : Change {
        override fun inverse() = copy(before = after, after = before)
    }

    /** A player gets or loses a designation: monarch, initiative, city's blessing (CR 724–726). */
    @Serializable @SerialName("designation")
    data class PlayerDesignation(val player: PlayerId, val designation: Designation, val before: Boolean, val after: Boolean) : Change {
        override fun inverse() = copy(before = after, after = before)
    }

    /** A library is shuffled (CR 701.24): its cards become new objects, except [Shuffle.kept] (D45). The host
     * decides the new order; the engine only asks. */
    @Serializable @SerialName("shuffle")
    data class Shuffle(val player: PlayerId, val before: List<GameObject>, val after: List<GameObject>) : Change {
        override fun inverse() = copy(before = after, after = before)
    }

    /** Objects are put back in another order where they are, keeping their identity (CR 401.4). */
    @Serializable @SerialName("reorder")
    data class Reorder(val zone: Zone, val owner: PlayerId?, val before: List<ObjectId>, val after: List<ObjectId>) : Change {
        override fun inverse() = copy(before = after, after = before)
    }

    /** The host fills in an unknown object's identity (D60). */
    @Serializable @SerialName("filled")
    data class Filled(val before: GameObject, val after: GameObject) : Change {
        override fun inverse() = copy(before = after, after = before)
        override val isEvent: Boolean get() = false
    }

    /** Something happened that changes no state but can trigger and be counted (D33): an ability was activated, a coin
     * flipped, cards revealed. */
    @Serializable @SerialName("happened")
    data class Happened(val event: GameEvent) : Change {
        override fun inverse() = this
    }

    /** Takes [change] back: applying it reverts [change] (and its logged event), reverting it applies [change] again.
     * Cancelling a cast returns the cast's changes, last first, wrapped in this (CR 601.2, D58). */
    @Serializable @SerialName("reverted")
    data class Reverted(val change: Change) : Change {
        override fun inverse() = change
        override val isEvent: Boolean get() = false
    }

    /** The triggered abilities waiting to be put on the Stack change (CR 603.3, D42): one triggered, or they were put
     * on the Stack. */
    @Serializable @SerialName("triggers-waiting")
    data class TriggersWaiting(
        val before: List<arcanum.engine.decision.WaitingTrigger>, val after: List<arcanum.engine.decision.WaitingTrigger>,
    ) : Change {
        override fun inverse() = copy(before = after, after = before)
        override val isEvent: Boolean get() = false
    }

    /** The open decision and its continuation are set or cleared (D58). */
    @Serializable @SerialName("pending")
    data class PendingSet(val before: Pending?, val after: Pending?) : Change {
        override fun inverse() = copy(before = after, after = before)
        override val isEvent: Boolean get() = false
    }
}

/** One object's part of a [Change.ZoneChange]: [before] leaves its zone at [fromIndex], [after] arrives at [toIndex]. */
@Serializable
data class Move(
    val before: GameObject,
    val after: GameObject,
    val fromIndex: Int,
    val toIndex: Int,
    val meanings: List<Meaning> = emptyList(),
    /** What everyone could see of it as it left (D33): characteristics after layers, for history and "dies" triggers. */
    val seen: ObjectSnapshot? = null,
) {
    fun inverse() = Move(after, before, toIndex, fromIndex, meanings, seen)
    val from: Zone get() = before.zone
    val to: Zone get() = after.zone
}

/** An object and its position in its zone. */
@Serializable
data class Placed(val obj: GameObject, val index: Int)

/** [amount] damage from [source] to [recipient] (CR 120); [combat] for combat damage (CR 510). */
@Serializable
data class DamageDealt(
    val source: ObjectId,
    val recipient: TargetRef,
    val amount: Int,
    val combat: Boolean = false,
    /** The source as it was when it dealt the damage (CR 120.3, 608.2h). */
    val sourceSeen: ObjectSnapshot? = null,
)

/** Events without state of their own ([Change.Happened]). */
@Serializable
sealed interface GameEvent {
    /** [player] activated an ability of [source] (CR 602); mana abilities too (CR 605). */
    @Serializable @SerialName("activated")
    data class Activated(val player: PlayerId, val source: ObjectId, val ability: String, val manaAbility: Boolean) : GameEvent
    /** [ids] were revealed to every player (CR 701.20). */
    @Serializable @SerialName("revealed") data class Revealed(val player: PlayerId, val ids: List<ObjectId>) : GameEvent
    /** [player] flipped a coin (CR 705). */
    @Serializable @SerialName("coin") data class CoinFlipped(val player: PlayerId, val won: Boolean) : GameEvent
    /** [player] rolled dice with [sides] sides (CR 706). */
    @Serializable @SerialName("die") data class DieRolled(val player: PlayerId, val sides: Int, val results: List<Int>) : GameEvent
    /** [player] searched [library]'s library (CR 701.23). */
    @Serializable @SerialName("searched") data class Searched(val player: PlayerId, val library: PlayerId) : GameEvent
    /** [player] scried [count] (CR 701.22). */
    @Serializable @SerialName("scried") data class Scried(val player: PlayerId, val count: Int) : GameEvent
    /** [player] surveilled [count] (CR 701.25). */
    @Serializable @SerialName("surveilled") data class Surveilled(val player: PlayerId, val count: Int) : GameEvent
    /** [player] copied the spell [copy] (CR 707.10). */
    @Serializable @SerialName("copied") data class Copied(val player: PlayerId, val copy: ObjectId) : GameEvent
    /** [player] is the starting player (CR 103.1), chosen by [chooser] (null: given). */
    @Serializable @SerialName("starting-player") data class StartingPlayer(val player: PlayerId, val chooser: PlayerId?) : GameEvent
    /** [player] took their [count]th mulligan (CR 103.5). */
    @Serializable @SerialName("mulliganed") data class Mulliganed(val player: PlayerId, val count: Int) : GameEvent
    /** [player] kept an opening hand of [cards] cards (CR 103.5). */
    @Serializable @SerialName("kept") data class Kept(val player: PlayerId, val cards: Int) : GameEvent
    /** [player] conceded the game (CR 104.3a). */
    @Serializable @SerialName("conceded") data class Conceded(val player: PlayerId) : GameEvent
}

/**
 * The Magic meaning of a change (D33): what history texts say and what "this turn" questions count. A manual move
 * carries only what the move itself defines (D42: died, entered, left the battlefield, put into a graveyard).
 */
@Serializable
sealed interface Meaning {
    @Serializable @SerialName("cast") data object Cast : Meaning
    @Serializable @SerialName("resolved") data object Resolved : Meaning
    @Serializable @SerialName("countered") data object Countered : Meaning
    /** Didn't resolve: all its targets were illegal (CR 608.2b). */
    @Serializable @SerialName("fizzled") data object Fizzled : Meaning
    @Serializable @SerialName("activated") data object Activated : Meaning
    @Serializable @SerialName("triggered") data object Triggered : Meaning
    /** Put into a graveyard from the battlefield (CR 700.4). */
    @Serializable @SerialName("died") data object Died : Meaning
    @Serializable @SerialName("destroyed") data object Destroyed : Meaning
    @Serializable @SerialName("sacrificed") data object Sacrificed : Meaning
    @Serializable @SerialName("discarded") data object Discarded : Meaning
    @Serializable @SerialName("drew") data object Drew : Meaning
    @Serializable @SerialName("milled") data object Milled : Meaning
    @Serializable @SerialName("exiled") data object Exiled : Meaning
    @Serializable @SerialName("returned-to-hand") data object ReturnedToHand : Meaning
    @Serializable @SerialName("put-into-library") data object PutIntoLibrary : Meaning
    @Serializable @SerialName("put-into-graveyard") data object PutIntoGraveyard : Meaning
    @Serializable @SerialName("entered") data object Entered : Meaning
    @Serializable @SerialName("left-battlefield") data object LeftBattlefield : Meaning
    @Serializable @SerialName("played-land") data object PlayedLand : Meaning
    @Serializable @SerialName("token-created") data object TokenCreated : Meaning
    @Serializable @SerialName("ceased-to-exist") data object CeasedToExist : Meaning
    @Serializable @SerialName("turned-face-up") data object TurnedFaceUp : Meaning
    @Serializable @SerialName("turned-face-down") data object TurnedFaceDown : Meaning
    @Serializable @SerialName("transformed") data object Transformed : Meaning
    @Serializable @SerialName("flipped") data object Flipped : Meaning
    @Serializable @SerialName("phased-out") data object PhasedOut : Meaning
    @Serializable @SerialName("phased-in") data object PhasedIn : Meaning
    @Serializable @SerialName("regenerated") data object Regenerated : Meaning
    @Serializable @SerialName("life-gained") data object LifeGained : Meaning
    @Serializable @SerialName("life-lost") data object LifeLost : Meaning
    @Serializable @SerialName("life-paid") data object LifePaid : Meaning
    @Serializable @SerialName("mana-added") data object ManaAdded : Meaning
    @Serializable @SerialName("mana-spent") data object ManaSpent : Meaning
    @Serializable @SerialName("mana-emptied") data object ManaEmptied : Meaning
    @Serializable @SerialName("paid") data object Paid : Meaning
    @Serializable @SerialName("priority-passed") data object PriorityPassed : Meaning
    @Serializable @SerialName("priority-granted") data object PriorityGranted : Meaning
    @Serializable @SerialName("turn-began") data object TurnBegan : Meaning
    @Serializable @SerialName("step-began") data object StepBegan : Meaning
    @Serializable @SerialName("attackers-declared") data object AttackersDeclared : Meaning
    @Serializable @SerialName("blockers-declared") data object BlockersDeclared : Meaning
    @Serializable @SerialName("combat-ended") data object CombatEnded : Meaning
    /** Attackers or blockers stopped being in combat before it ended (CR 506.4). */
    @Serializable @SerialName("removed-from-combat") data object RemovedFromCombat : Meaning
    @Serializable @SerialName("effect-ended") data object EffectEnded : Meaning
    @Serializable @SerialName("shield-used") data object ShieldUsed : Meaning
    /** Done by a state-based action (CR 704). */
    @Serializable @SerialName("state-based") data object StateBased : Meaning
    /** Made by hand, not by automation (D33). */
    @Serializable @SerialName("manual") data object Manual : Meaning
}
