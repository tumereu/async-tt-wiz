package arcanum.engine.casting

import arcanum.api.Zone
import arcanum.engine.Progress
import arcanum.engine.change.Change
import arcanum.engine.change.Recorder
import arcanum.engine.decision.DecisionId
import arcanum.engine.query.Board
import arcanum.engine.query.Context
import arcanum.engine.query.Evaluator
import arcanum.engine.state.Bindings
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.ObjectSnapshot
import arcanum.engine.state.PlayerId
import arcanum.engine.state.StackItem
import arcanum.engine.view.Layers
import arcanum.engine.view.View

/** The evaluator's board over a finished [View]: characteristics after all layers, controllers after layer 2. Off the
 * battlefield and the Stack the owner answers for a card (CR 108.4a); an ability on the Stack answers with its
 * controller. */
internal class ViewBoard(override val state: GameState, val view: View) : Board {
    /** An object that has left its zone reads as its last known information (CR 608.2h): "the same name as that
     * creature" after it was returned to its owner's hand. */
    override fun characteristics(id: ObjectId) =
        view[id]?.characteristics ?: if (state.obj(id) == null) snapshot(id)?.characteristics else null

    override fun controller(id: ObjectId): PlayerId? {
        val o = state.obj(id)
        if (o == null) return state.stackItems[id]?.controller ?: view[id]?.controller
        return if (o.zone == Zone.Battlefield || o.zone == Zone.Stack) view[id]?.controller ?: o.controller ?: o.owner else o.owner
    }
}

/** A state and its view, worked out once for a step of a flow. */
internal class Snapshot(val state: GameState, private val viewOf: () -> View = { Layers.view(state) }) {
    val view: View by lazy { viewOf() }
    val board: ViewBoard by lazy { ViewBoard(state, view) }

    fun evaluator(
        you: PlayerId, self: ObjectId? = null, names: Bindings = Bindings.EMPTY, stackItem: StackItem? = null,
        tested: ObjectId? = null, slotPlayer: PlayerId? = null, holder: PlayerId? = null,
    ) = Evaluator(board, Context(you = you, self = self, names = names, stackItem = stackItem, tested = tested,
        slotPlayer = slotPlayer, holder = holder))

    /** Last known information of [id] as everyone can see it now (CR 608.2h, D33). */
    fun snapshot(id: ObjectId): ObjectSnapshot? {
        val o = state.obj(id) ?: return null
        return ObjectSnapshot(
            id = id, owner = o.owner, controller = board.controller(id), zone = o.zone,
            characteristics = view[id]?.characteristics, counters = o.counters, damage = o.damage, tapped = o.tapped,
            token = o.token, attachedTo = o.attachedTo, facts = o.facts, memory = o.memory,
            attacking = state.combat?.attackers.orEmpty().any { it.id == id },
        )
    }
}

internal fun Recorder.snapshot(): Snapshot = Snapshot(state)

/** A decision id that names what is asked and where the game is, so an answer to an old decision is refused. */
internal fun decisionId(state: GameState, what: String): DecisionId =
    DecisionId("$what@${state.timestampCounter}.${state.log.size}")

/** The changes recorded since [from] that a cancel would take back: everything but the open decision's bookkeeping. */
internal fun Recorder.madeSince(from: Int): List<Change> =
    changes.subList(from, changes.size).filterNot { it is Change.PendingSet }

/** Shorthand for a finished flow. */
internal val done: Progress get() = Progress.Done
