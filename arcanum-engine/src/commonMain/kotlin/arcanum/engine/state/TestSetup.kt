package arcanum.engine.state

import arcanum.api.TurnStep
import arcanum.engine.Pending
import arcanum.engine.decision.WaitingTrigger

/**
 * Marks the API that builds or changes a [GameState] directly, outside the rules: [GameState.of], [GameState.start] and
 * [GameState.setup]. Hosts start a game with [arcanum.engine.Engine.newGame] and change it only by performing inputs; tests set up
 * boards with this. Only test source sets may opt in: a build check fails when main code does.
 */
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "Builds or changes a game state outside the rules: for tests only. Opt in only in test source sets.",
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
annotation class ArcanumTestSetup

/** A test board's state with these fields (as [GameState] names them); the collections are copied. */
@ArcanumTestSetup
fun GameState.Companion.of(
    players: List<PlayerState>,
    objects: Map<ObjectId, GameObject> = emptyMap(),
    battlefield: List<ObjectId> = emptyList(),
    stack: List<ObjectId> = emptyList(),
    exile: List<ObjectId> = emptyList(),
    command: List<ObjectId> = emptyList(),
    stackItems: Map<ObjectId, StackItem> = emptyMap(),
    effects: Map<EffectId, EffectObject> = emptyMap(),
    delayedTriggers: Map<EffectId, DelayedTrigger> = emptyMap(),
    turn: TurnState,
    priority: PriorityState = PriorityState(null),
    combat: CombatState? = null,
    ledger: Map<String, Int> = emptyMap(),
    log: List<LoggedChange> = emptyList(),
    pending: Pending? = null,
    timestampCounter: Long = 0,
    inputCount: Int = 0,
    triggered: List<WaitingTrigger> = emptyList(),
    skipFirstDraw: Boolean = true,
): GameState = GameState(
    players.toMutableList(), LinkedHashMap(objects), battlefield.toMutableList(), stack.toMutableList(), exile.toMutableList(),
    command.toMutableList(), LinkedHashMap(stackItems), LinkedHashMap(effects), LinkedHashMap(delayedTriggers), turn, priority,
    combat, LinkedHashMap(ledger), log.toMutableList(), pending, timestampCounter, inputCount, triggered.toMutableList(),
    skipFirstDraw,
)

/** A test board with [players] (in turn order) at [life], turn 1 of the first player in [step], nothing in any zone. */
@ArcanumTestSetup
fun GameState.Companion.start(players: List<PlayerId>, life: Int = 20, step: TurnStep = TurnStep.PrecombatMain): GameState =
    GameState.started(players, life, step)

/** Today's game start for a test board (the engine's own tests reach it without opting in). */
internal fun GameState.Companion.started(players: List<PlayerId>, life: Int = 20, step: TurnStep = TurnStep.PrecombatMain): GameState =
    GameState(
        _players = players.map { PlayerState(it, life, startingLife = life) }.toMutableList(),
        _turn = TurnState(1, players.first(), step, turnStarted = mapOf(players.first() to 1)),
        _priority = PriorityState(players.first()),
    )

/** This state's fields, writable, for a test board: changed directly, outside the rules, and no change is recorded. */
@ArcanumTestSetup
val GameState.setup: StateSetup get() = StateSetup(this)

/** A [GameState]'s fields, writable, for a test board ([GameState.setup]). */
@ArcanumTestSetup
class StateSetup internal constructor(private val state: GameState) {
    val players: MutableList<PlayerState> get() = state._players
    val objects: MutableMap<ObjectId, GameObject> get() = state._objects
    val battlefield: MutableList<ObjectId> get() = state._battlefield
    val stack: MutableList<ObjectId> get() = state._stack
    val exile: MutableList<ObjectId> get() = state._exile
    val command: MutableList<ObjectId> get() = state._command
    val stackItems: MutableMap<ObjectId, StackItem> get() = state._stackItems
    val effects: MutableMap<EffectId, EffectObject> get() = state._effects
    val delayedTriggers: MutableMap<EffectId, DelayedTrigger> get() = state._delayedTriggers
    val ledger: MutableMap<String, Int> get() = state._ledger
    val log: MutableList<LoggedChange> get() = state._log
    val triggered: MutableList<WaitingTrigger> get() = state._triggered
    var turn: TurnState
        get() = state._turn
        set(value) { state._turn = value }
    var priority: PriorityState
        get() = state._priority
        set(value) { state._priority = value }
    var combat: CombatState?
        get() = state._combat
        set(value) { state._combat = value }
    var pending: Pending?
        get() = state._pending
        set(value) { state._pending = value }
    var timestampCounter: Long
        get() = state._timestampCounter
        set(value) { state._timestampCounter = value }
    var inputCount: Int
        get() = state._inputCount
        set(value) { state._inputCount = value }
    var skipFirstDraw: Boolean
        get() = state._skipFirstDraw
        set(value) { state._skipFirstDraw = value }

    /** Changes [id]'s player state. */
    fun player(id: PlayerId, change: (PlayerState) -> PlayerState) = state.updatePlayer(id, change)
}
