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
                // fra: a copy of a card cast as a permanent spell becomes a token as it resolves (CR 111.13)
                token = before.token || (before.copyOfCard && before.zone == Zone.Stack && to == Zone.Battlefield),
                copyOfCard = before.copyOfCard,
                prepareSpellOf = before.prepareSpellOf,
                tapped = tapped,
                faceDown = faceDown,
                // One entry per kind: counters of one kind it enters with add up (CR 122.6)
                counters = counters.groupBy({ it.first }, { it.second }).map { (kind, n) -> CounterEntry(kind, n.sum(), stamp) }
                    .filter { it.count > 0 },
                timestamp = stamp,
                controlSince = if (to == Zone.Battlefield) ControlSince(newController!!, state.turn.number) else null,
                facts = if (keepsCastFacts) before.facts.copy(activations = emptyList(), resolutions = emptyList(), triggers = emptyList())
                    else ObjectFacts(),
            )
            val fromIndex = scratch.zone(before.zone, before.owner).indexOf(id)
            // Positions count without the object itself: it leaves its zone first (to the bottom of its own library)
            val target = scratch.zone(to, after.owner).filter { it != id }
            val toIndex = when (position) {
                Position.Top -> if (to == Zone.Library) 0 else target.size
                Position.Bottom -> target.size
                is Position.At -> position.index.coerceIn(0, target.size)
            }
            val parts = mergedParts(scratch, before, after, toIndex)
            val move = Move(before, after, fromIndex, toIndex, (Meanings.implied(before.zone, to) + meanings).distinct(), parts = parts)
            scratch.apply(Change.ZoneChange(listOf(move)))
            move
        }
        return Change.ZoneChange(moves)
    }

    /**
     * CR 730.3, 730.3a: as a merged permanent leaves the battlefield, each of its other components is put into the new zone as its
     * own new object: in [after]'s zone of its owner, right after [after] (above it on a library, CR 730.3a: the plainest order,
     * the components topmost first).
     */
    private fun mergedParts(scratch: GameState, before: GameObject, after: GameObject, toIndex: Int): List<Placed> {
        if (!before.merged) return emptyList()
        val placed = mutableMapOf<PlayerId, Int>()
        return (before.mergedAbove + before.mergedBelow).map { p ->
            val k = placed[p.owner] ?: 0
            placed[p.owner] = k + 1
            val index = if (p.owner == after.owner) toIndex + 1 + k else scratch.zone(after.zone, p.owner).size + k
            Placed(GameObject(id = p.id.next(), owner = p.owner, zone = after.zone, definition = p.definition, token = p.token,
                copyOfCard = p.copyOfCard, faceDown = after.faceDown, timestamp = after.timestamp), index)
        }
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

    /** Sets [player]'s "Skip when I have no choice" setting (I-D18); a host makes this change. */
    fun skipWhenNoChoice(state: GameState, player: PlayerId, on: Boolean): Change.SkipWhenNoChoice =
        Change.SkipWhenNoChoice(player, state.player(player).skipWhenNoChoice, on)

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
 * CR 603.2), looking back in time where CR 603.10 says, and recorded as waiting; then what the event took out of combat
 * is removed from it ([arcanum.engine.combat.Combat.removeWhatLeft], CR 506.4). */
internal class Recorder(val state: GameState, view: arcanum.engine.view.View? = null) {
    val changes = mutableListOf<Change>()

    /** The rounds of state-based actions and triggers made in this call so far ([arcanum.engine.turn.Loops.capped]). */
    var rounds = 0

    init {
        // The recorder's state is its own (D56): its view is kept until a change can make it differ. [view], when given,
        // is the view of [state] as it is.
        state.viewCache = arcanum.engine.view.ViewCache(view)
    }

    /** What the instructions being run now are (their changes carry it, [Change.causedBy]); set by
     * [arcanum.engine.resolution.Steps.run]. */
    internal var by: CausedBy? = null

    fun record(made: Change) {
        val change = by?.let { made.causedBy(it) } ?: made
        val lookBack = change.isEvent && arcanum.engine.trigger.Triggers.looksBack(change)
        val before = if (lookBack) state.deepCopy() else null
        val viewBefore = if (lookBack) state.viewCache?.view else null
        state.apply(change)
        changes += change
        if (change.isEvent) arcanum.engine.trigger.Triggers.detect(this, change, before, viewBefore)
        // CR 506.4: what an event takes out of combat leaves it at once (combat changes set combat as they mean it)
        if (change.isEvent && change !is Change.Combat) arcanum.engine.combat.Combat.removeWhatLeft(this)
    }

    /** Applies and keeps [change] as it is, finding no triggers: for changes already worked out with their triggers
     * ([arcanum.engine.action.Knowledge.forgetHidden]). */
    fun append(change: Change) {
        state.apply(change)
        changes += change
    }

    /** Takes back every change after the first [size] (a step that stopped for a decision is redone from its start
     * once answered, D29). */
    fun rollback(size: Int) {
        while (changes.size > size) state.revert(changes.removeAt(changes.lastIndex))
    }
}
