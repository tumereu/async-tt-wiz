package arcanum.engine

import arcanum.engine.change.Change
import arcanum.engine.decision.Continuation
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.state.GameState
import arcanum.engine.state.PlayerId
import arcanum.engine.setup.GameResult
import arcanum.engine.setup.Seat
import arcanum.engine.setup.Setup
import arcanum.engine.setup.result
import arcanum.engine.view.Layers
import arcanum.engine.view.View
import kotlinx.serialization.Serializable

/**
 * The rules engine's core API (docs/specs/arcanum-automatum.md §7.0, D56–D61): start a game, read its view, list and
 * perform inputs, read its result. Stateless: everything it needs is in the [GameState] it is given, and everything it
 * decides comes back as [Change]s (P2). It never writes the state it reads; callers apply the changes with
 * [GameState.apply]. The rest of the API is grouped by who uses it: [CastScreen] (a host's cast and activation
 * screens), [CombatDeclarations], [Inspect], [Host] (what a host running games needs), [CardSupport] and
 * [EngineProbes] (tests and measurements).
 */
object Engine {
    /** The characteristics of every object after continuous effects (CR 613), the rules that apply to objects and
     * players, and the game rules in force (P3). A pure function of [state]. */
    fun view(state: GameState): View = Layers.view(state)

    /** What [player] may do now: play a land, cast, activate, pass, declare, or answer the open decision. [prefs]: the players'
     * preferences, the same on every host that performs for them. */
    fun options(state: GameState, player: PlayerId, prefs: Preferences = Preferences()): List<Input> =
        Options.of(state, player, prefs = prefs)

    /** [options] with [view], [state]'s view ([Engine.view] of [state] as it is now) when the caller already has it: the same
     * list without working the layers out again for the search. A view of another state gives wrong results. */
    fun options(state: GameState, player: PlayerId, view: View?, prefs: Preferences = Preferences()): List<Input> =
        Options.of(state, player, view, prefs)

    /**
     * Performs [input] on a copy of [state] and returns what changed, up to the next decision (D58): after every input
     * the engine applies state-based actions, puts triggered abilities on the Stack and gives priority (P5). While
     * [GameState.pending] is set, only its [Input.Answer] (or [Input.Cancel] for a cast) is accepted. A call that goes
     * round too often (an endless chain of mandatory triggers) stops with the "possible loop" choice
     * ([arcanum.engine.decision.LoopStop.Cap]) instead of hanging: continue, or end the game as a draw (CR 104.4b).
     *
     * [trace] gets one [TraceEntry] for the call (the input, what it did or why it was refused, how long it took) for a
     * host's logs and bug reports; the default records nothing and costs nothing. [prefs]: the players' preferences, which must
     * be the same on every host that performs for them (the device, the other players' devices, a server).
     *
     * The [Outcome] carries the view of the state after its changes when the engine has it ([Outcome.view]).
     */
    fun perform(state: GameState, input: Input, prefs: Preferences = Preferences(), trace: EngineTrace = EngineTrace.None): Outcome =
        Perform.run(state, input, prefs, trace = trace)

    /**
     * [perform] with [view], [state]'s view ([Engine.view] of [state] as it is now) when the caller already has it, so the engine
     * doesn't work it out again (null: worked out when needed, as [perform]). The same outcome; a view of another state
     * gives wrong results.
     */
    fun perform(state: GameState, input: Input, view: View?, prefs: Preferences = Preferences(), trace: EngineTrace = EngineTrace.None): Outcome =
        Perform.run(state, input, prefs, view, trace)

    /**
     * A game before it begins (CR 103): [seats] in seat order with their decks as libraries in the order given and their
     * sideboards outside the game, each at [startingLife] (CR 103.4). [skipFirstDraw]: the starting player skips the draw
     * of their first turn (CR 103.8a: two players; 103.8c: nobody in other multiplayer games). Nothing random happens
     * here: [Input.StartGame] then chooses the starting player, shuffles and deals opening hands (D25).
     */
    fun newGame(seats: List<Seat>, startingLife: Int = 20, skipFirstDraw: Boolean = seats.size == 2): GameState =
        Setup.newGame(seats, startingLife, skipFirstDraw)

    /** How the game ended (CR 104): the winner or a draw; null while it goes on. */
    fun result(state: GameState): GameResult? = state.result
}

/**
 * The result of [Engine.perform]: [changes] to apply in order (one host batch, D58), and the decision the engine waits
 * for, if any. [pending] is also set in the state by the last change ([Change.PendingSet]).
 */
@Serializable
data class Outcome(val changes: List<Change>, val pending: Pending?) {
    /**
     * The view ([Engine.view]) of the state after [changes] when the engine had it at the end of the call (its working
     * copy's), so a host that applies [changes] needn't work it out again; null when it hadn't (then work it out). Not
     * part of equality, not serialised, and dropped by [copy].
     */
    @kotlinx.serialization.Transient
    var view: View? = null
        internal set
}

/**
 * The players' preferences that change what the engine asks or passes for them, given to [Engine.options], [Engine.perform] and
 * [Host]'s pass checks. A host must give the same preferences on every host that performs for the same players (a device, the
 * other players' devices, a server), so that each works the game out the same way.
 */
@Serializable
data class Preferences(
    /**
     * The players who turned "Skip when I have no choice" off (I-D18; on by default): for them, a question that leaves nothing to
     * choose, or a pass, that depends on cards only they may see (their hand, face-down cards) is asked instead of skipped, so
     * how fast the game goes on tells the other players nothing about those cards. Skips that depend on nothing hidden stay
     * automatic either way (P5).
     */
    val noSkipWhenNoChoice: Set<PlayerId> = emptySet(),
) {
    /** Whether the engine skips [player]'s questions that leave nothing to choose ("Skip when I have no choice" on). */
    fun skipsWhenNoChoice(player: PlayerId): Boolean = player !in noSkipWhenNoChoice
}

/** [Host.passCheck]: whether a host passes priority for a player with the deciding player's "Skip when I have no
 * choice" as it is ([asSet]), and with the player's own setting on ([ownSkipOn]). */
data class PassCheck(val asSet: Boolean, val ownSkipOn: Boolean) {
    companion object {
        /** Neither: the player may do something, or doesn't hold priority. */
        val NONE = PassCheck(asSet = false, ownSkipOn = false)
    }
}

/** A [decision] and the [continuation] that goes on once it is answered (D58). The host stores both as they are. */
@Serializable
data class Pending(val decision: Decision, val continuation: Continuation)

/** What [CastScreen.selection] says of an open choice of targets or objects: the [candidates] that may be picked with what is picked
 * so far, and whether the picks so far are a selection that is [done]: legal and able to be paid. */
data class Selection(val candidates: List<arcanum.engine.state.TargetRef>, val done: Boolean)

/**
 * What [CastScreen.castCosts] says of an open cast or activation: [least] the lowest mana cost among the ways through its open choices
 * that can be paid now (the lowest of all when none can; null when it costs no mana), [several] whether it can have other costs or
 * the search couldn't finish, [asks] whether anything is asked before the payment, [castable] the same answer as
 * [CastScreen.finishable], [suggestion] Autotap's sources for [least], [answers] the answers leading to [least].
 */
data class CastCosts(
    val least: arcanum.api.ManaCost?, val several: Boolean, val asks: Boolean, val castable: Boolean,
    val suggestion: List<arcanum.engine.decision.ManaUse>, val answers: List<arcanum.engine.decision.Answer>,
)
