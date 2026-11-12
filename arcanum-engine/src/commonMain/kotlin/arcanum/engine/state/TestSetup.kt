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
    statuses: List<GameStatus> = emptyList(),
    log: List<LoggedChange> = emptyList(),
    pending: Pending? = null,
    timestampCounter: Long = 0,
    inputCount: Int = 0,
    triggered: List<WaitingTrigger> = emptyList(),
    skipFirstDraw: Boolean = true,
): GameState = GameState(
    _players = players.toMutableList(), _objects = LinkedHashMap(objects), _battlefield = battlefield.toMutableList(),
    _stack = stack.toMutableList(), _exile = exile.toMutableList(), _command = command.toMutableList(),
    _stackItems = LinkedHashMap(stackItems), _effects = LinkedHashMap(effects), _delayedTriggers = LinkedHashMap(delayedTriggers),
    _turn = turn, _priority = priority, _combat = combat, _statuses = statuses.sortedBy { it.key }, _pending = pending,
    _skipFirstDraw = skipFirstDraw, _log = log.toMutableList(), _timestampCounter = timestampCounter, _inputCount = inputCount,
    _triggered = triggered.toMutableList(),
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
    val log: MutableList<LoggedChange> get() = state._log
    val triggered: MutableList<WaitingTrigger> get() = state._triggered
    var statuses: List<GameStatus>
        get() = state._statuses
        set(value) { state._statuses = value.sortedBy { it.key } }
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

    /** [player]'s library places (the engine's bookkeeping of which library cards' places are known). */
    fun libraryPlaces(player: PlayerId): Map<ObjectId, Int> = state.libraryPlaces(player)

    /** Sets [player]'s library places. */
    fun setLibraryPlaces(player: PlayerId, places: Map<ObjectId, Int>) {
        state._libraryPlaces = if (places.isEmpty()) state._libraryPlaces - player else state._libraryPlaces + (player to places)
    }

    /** Players who drew from an empty library since the last state-based action check. */
    var emptyDraws: List<PlayerId>
        get() = state._emptyDraws
        set(value) { state._emptyDraws = value }

    /** [player]'s card-defined ledger (D22), by name. */
    fun ledger(player: PlayerId): Map<String, Int> = state.ledgers[player].orEmpty()

    /** Sets [player]'s card-defined ledger. */
    fun setLedger(player: PlayerId, ledger: Map<String, Int>) {
        state._ledgers = if (ledger.isEmpty()) state._ledgers - player else state._ledgers + (player to ledger)
    }
}
