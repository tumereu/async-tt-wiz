package arcanum.engine

import arcanum.engine.change.Change
import arcanum.engine.decision.Continuation
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.state.GameState
import arcanum.engine.state.PlayerId
import arcanum.engine.view.Layers
import arcanum.engine.view.View
import kotlinx.serialization.Serializable

/**
 * The rules engine's API (docs/specs/arcanum-automatum.md §7.0, D56–D61). Stateless: everything it needs is in the
 * [GameState] it is given, and everything it decides comes back as [Change]s (P2). It never writes the state it
 * reads; callers apply the changes with [GameState.apply].
 */
object Engine {
    /** The characteristics of every object after continuous effects (CR 613), the rules that apply to objects and
     * players, and the game rules in force (P3). A pure function of [state]. */
    fun view(state: GameState): View = Layers.view(state)

    /** What [player] may do now: play a land, cast, activate, pass, declare, or answer the open decision. */
    fun options(state: GameState, player: PlayerId): List<Input> = Options.of(state, player)

    /**
     * Performs [input] on a copy of [state] and returns what changed, up to the next decision (D58): after every input
     * the engine applies state-based actions, puts triggered abilities on the Stack and gives priority (P5). While
     * [GameState.pending] is set, only its [Input.Answer] (or [Input.Cancel] for a cast) is accepted.
     */
    fun perform(state: GameState, input: Input): Outcome = Perform.run(state, input)
}

/**
 * The result of [Engine.perform]: [changes] to apply in order (one host batch, D58), and the decision the engine waits
 * for, if any. [pending] is also set in the state by the last change ([Change.PendingSet]).
 */
@Serializable
data class Outcome(val changes: List<Change>, val pending: Pending?)

/** A [decision] and the [continuation] that goes on once it is answered (D58). The host stores both as they are. */
@Serializable
data class Pending(val decision: Decision, val continuation: Continuation)
