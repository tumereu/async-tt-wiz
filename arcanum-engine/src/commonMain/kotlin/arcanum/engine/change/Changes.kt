package arcanum.engine.change

import arcanum.api.CounterKind
import arcanum.api.Zone
import arcanum.engine.Pending
import arcanum.engine.state.ControlSince
import arcanum.engine.state.CounterEntry
import arcanum.engine.state.EffectObject
import arcanum.engine.state.GameObject
import arcanum.engine.state.GameState
import arcanum.engine.state.ManaUnit
import arcanum.engine.state.ObjectFacts
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.PriorityState
import arcanum.engine.state.StatusFlag
import arcanum.engine.state.TargetRef
import arcanum.engine.state.TurnState

/**
 * Builds [Change]s from the state they apply to, with their before-values read from it. Each builder reads [state]
 * as it is now: when one step makes several changes, apply each (e.g. with a [Recorder]) before building the next.
 */
object Changes {
    /** Where an object goes in a zone: the top of a library is position 0; elsewhere objects go last. */
    sealed interface Position {
        data object Top : Position
        data object Bottom : Position
        data class At(val index: Int) : Position
    }

    /**
     * Moves [ids] to [to] as one event (CR 400.7, D44): each becomes a new object with a new id and a new timestamp
     * (CR 613.7d, stamped in order, D34), untapped, without counters, damage or attachments, with fresh memory. A spell
     * becoming a permanent keeps its cast facts (CR 400.7d). [controller] is who it enters the battlefield or Stack
     * under (default its owner, CR 110.2).
     */
    fun move(
        state: GameState,
        ids: List<ObjectId>,
        to: Zone,
        position: Position = if (to == Zone.Library) Position.Top else Position.Bottom,
        controller: PlayerId? = null,
        tapped: Boolean = false,
        faceDown: Boolean = false,
        meanings: List<Meaning> = emptyList(),
        counters: List<Pair<CounterKind, Int>> = emptyList(),
    ): Change.ZoneChange {
        val scratch = state.deepCopy()
        val moves = ids.mapIndexed { i, id ->
            val before = scratch.objOrThrow(id)
            val stamp = state.nextTimestamp() + i
            val keepsCastFacts = before.zone == Zone.Stack && to == Zone.Battlefield
            val entersUnderControl = to == Zone.Battlefield || to == Zone.Stack
            val newController = if (entersUnderControl) controller ?: before.owner else null
            val after = GameObject(
                id = before.id.next(),
                owner = before.owner,
                controller = newController,
                zone = to,
                definition = before.definition,
                face = if (to == Zone.Battlefield || to == Zone.Stack) before.face else 0,
                token = before.token,
                tapped = tapped,
                faceDown = faceDown,
                counters = counters.map { (kind, n) -> CounterEntry(kind, n, stamp) },
                timestamp = stamp,
                controlSince = if (to == Zone.Battlefield) ControlSince(newController!!, state.turn.number) else null,
                facts = if (keepsCastFacts) before.facts.copy(activations = emptyList(), resolutions = emptyList(), triggers = emptyList())
                    else ObjectFacts(),
            )
            val fromIndex = scratch.zone(before.zone, before.owner).indexOf(id)
            // Within one zone (to the bottom of its own library) the card leaves before it is put back
            val target = scratch.zone(to, after.owner).filter { it != id }
            val toIndex = when (position) {
                Position.Top -> if (to == Zone.Library) 0 else target.size
                Position.Bottom -> target.size
                is Position.At -> position.index.coerceIn(0, target.size)
            }
            val move = Move(before, after, fromIndex, toIndex, (Meanings.implied(before.zone, to) + meanings).distinct())
            scratch.apply(Change.ZoneChange(listOf(move)))
            move
        }
        return Change.ZoneChange(moves)
    }

    fun move(state: GameState, id: ObjectId, to: Zone, meanings: List<Meaning> = emptyList()): Change.ZoneChange =
        move(state, listOf(id), to, meanings = meanings)

    fun tap(state: GameState, id: ObjectId, tapped: Boolean = true, meanings: List<Meaning> = emptyList()): Change.Status =
        Change.Status(id, StatusFlag.Tapped, state.objOrThrow(id).tapped, tapped, meanings)

    /** Adds [delta] (negative removes) counters of [kind]; adding gives the kind a new timestamp (CR 613.7c). */
    fun counters(state: GameState, holder: TargetRef, kind: CounterKind, delta: Int, meanings: List<Meaning> = emptyList()): Change.Counters {
        val before = when (holder) {
            is TargetRef.Object -> state.objOrThrow(holder.id).counters.firstOrNull { it.kind == kind }
            is TargetRef.Player -> state.player(holder.id).counters.firstOrNull { it.kind == kind }
        }
        val count = ((before?.count ?: 0) + delta).coerceAtLeast(0)
        val after = when {
            count == 0 -> null
            delta > 0 -> CounterEntry(kind, count, state.nextTimestamp())
            else -> before!!.copy(count = count)
        }
        return Change.Counters(holder, kind, before, after, meanings)
    }

    fun life(state: GameState, player: PlayerId, delta: Int, meanings: List<Meaning> = emptyList()): Change.Life {
        val before = state.player(player).life
        return Change.Life(player, before, before + delta, meanings)
    }

    fun damageMarked(state: GameState, id: ObjectId, amount: Int, deathtouch: Boolean = false): Change.DamageMarked {
        val o = state.objOrThrow(id)
        return Change.DamageMarked(id, o.damage, o.damage + amount, o.deathtouchDamage, o.deathtouchDamage || (deathtouch && amount > 0))
    }

    fun manaPool(state: GameState, player: PlayerId, after: List<ManaUnit>, meanings: List<Meaning>): Change.ManaPool =
        Change.ManaPool(player, state.player(player).manaPool, after, meanings)

    fun step(state: GameState, after: TurnState, meanings: List<Meaning> = emptyList()): Change.Step =
        Change.Step(state.turn, after, meanings)

    fun priority(state: GameState, after: PriorityState, meanings: List<Meaning>): Change.Priority =
        Change.Priority(state.priority, after, meanings)

    fun pending(state: GameState, after: Pending?): Change.PendingSet = Change.PendingSet(state.pending, after)

    /** A new effect object stamped now (CR 613.7b). */
    fun effect(state: GameState, effect: (timestamp: Long) -> EffectObject): Change.EffectAdded =
        Change.EffectAdded(effect(state.nextTimestamp()))
}

/** What a zone change means by itself (D42): enough to tag a manual move. */
object Meanings {
    fun implied(from: Zone, to: Zone): List<Meaning> = buildList {
        if (to == Zone.Battlefield && from != Zone.Battlefield) add(Meaning.Entered)
        if (from == Zone.Battlefield && to != Zone.Battlefield) add(Meaning.LeftBattlefield)
        // CR 700.4: "dies" means put into a graveyard from the battlefield
        if (from == Zone.Battlefield && to == Zone.Graveyard) add(Meaning.Died)
        if (to == Zone.Graveyard && from != Zone.Graveyard) add(Meaning.PutIntoGraveyard)
        if (to == Zone.Exile && from != Zone.Exile) add(Meaning.Exiled)
    }
}

/** Applies changes to a working state as they are made and keeps them, in order: one engine call's result (D58).
 * After each event change, the triggered abilities it triggers are found ([arcanum.engine.trigger.Triggers.detect],
 * CR 603.2), looking back in time where CR 603.10 says, and recorded as waiting. */
internal class Recorder(val state: GameState) {
    val changes = mutableListOf<Change>()

    fun record(change: Change) {
        val before = if (change.isEvent && arcanum.engine.trigger.Triggers.looksBack(change)) state.deepCopy() else null
        state.apply(change)
        changes += change
        if (change.isEvent) arcanum.engine.trigger.Triggers.detect(this, change, before)
    }

    /** Takes back every change after the first [size] (a step that stopped for a decision is redone from its start
     * once answered, D29). */
    fun rollback(size: Int) {
        while (changes.size > size) state.revert(changes.removeAt(changes.lastIndex))
    }
}
