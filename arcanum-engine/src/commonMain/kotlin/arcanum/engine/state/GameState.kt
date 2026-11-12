package arcanum.engine.state

import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.Pending
import arcanum.engine.change.Applier
import arcanum.engine.change.Change
import arcanum.engine.decision.WaitingTrigger
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One game in MTG terms, with full information (D56; every card is known, what a player may see is
 * [arcanum.engine.Host.visible]): players in turn order, every object by id, the shared zones, the Stack with its
 * items, effect objects, delayed triggers, turn and priority, the game's statuses and the open continuation; inside, the
 * history (this and last turn's tagged events, card-defined ledgers) and the engine's bookkeeping.
 *
 * Read-only from outside the engine: its fields can be read, and only [apply] and [revert] change them (D56, D59).
 * Inside, the collections are mutable for speed, and [arcanum.engine.Engine] never changes the state it is given: it works
 * on [deepCopy] and returns the changes. Hosts start a game with [arcanum.engine.Engine.newGame]; tests in other modules
 * build boards with the [ArcanumTestSetup] API ([GameState.of], [GameState.start], [setup]).
 */
@ConsistentCopyVisibility
@Serializable
data class GameState internal constructor(
    @SerialName("players") internal var _players: MutableList<PlayerState>,
    @SerialName("objects") internal var _objects: MutableMap<ObjectId, GameObject> = mutableMapOf(),
    @SerialName("battlefield") internal var _battlefield: MutableList<ObjectId> = mutableListOf(),
    @SerialName("stack") internal var _stack: MutableList<ObjectId> = mutableListOf(),
    @SerialName("exile") internal var _exile: MutableList<ObjectId> = mutableListOf(),
    @SerialName("command") internal var _command: MutableList<ObjectId> = mutableListOf(),
    @SerialName("stackItems") internal var _stackItems: MutableMap<ObjectId, StackItem> = mutableMapOf(),
    @SerialName("effects") internal var _effects: MutableMap<EffectId, EffectObject> = mutableMapOf(),
    @SerialName("delayedTriggers") internal var _delayedTriggers: MutableMap<EffectId, DelayedTrigger> = mutableMapOf(),
    @SerialName("turn") internal var _turn: TurnState,
    @SerialName("priority") internal var _priority: PriorityState = PriorityState(null),
    @SerialName("combat") internal var _combat: CombatState? = null,
    @SerialName("statuses") internal var _statuses: List<GameStatus> = emptyList(),
    @SerialName("pending") internal var _pending: Pending? = null,
    @SerialName("skipFirstDraw") internal var _skipFirstDraw: Boolean = true,
    // ---- History: tagged events (D33) and card-defined ledgers (D22)
    @SerialName("log") internal var _log: MutableList<LoggedChange> = mutableListOf(),
    @SerialName("ledgers") internal var _ledgers: Map<PlayerId, Map<String, Int>> = emptyMap(),
    // ---- Bookkeeping: the engine's own
    @SerialName("timestampCounter") internal var _timestampCounter: Long = 0,
    @SerialName("inputCount") internal var _inputCount: Int = 0,
    @SerialName("triggered") internal var _triggered: MutableList<WaitingTrigger> = mutableListOf(),
    @SerialName("libraryPlaces") internal var _libraryPlaces: Map<PlayerId, Map<ObjectId, Int>> = emptyMap(),
    @SerialName("emptyDraws") internal var _emptyDraws: List<PlayerId> = emptyList(),
    @SerialName("spent") internal var _spent: Map<PlayerId, SpentOnSpells> = emptyMap(),
) {
    /** In turn order (CR 101.4, D61). */
    val players: List<PlayerState> get() = _players
    /** Every card and token object, in any zone, by its current id. */
    val objects: Map<ObjectId, GameObject> get() = _objects
    val battlefield: List<ObjectId> get() = _battlefield
    /** Bottom first: the last is the top of the Stack (CR 405.2). Spells' cards and [StackItem]-only objects. */
    val stack: List<ObjectId> get() = _stack
    val exile: List<ObjectId> get() = _exile
    /** Emblems and other command-zone objects (CR 408). */
    val command: List<ObjectId> get() = _command
    /** The choices of every spell and ability on the Stack, by id. */
    val stackItems: Map<ObjectId, StackItem> get() = _stackItems
    /** Effects of resolved spells and abilities still in force. */
    val effects: Map<EffectId, EffectObject> get() = _effects
    val delayedTriggers: Map<EffectId, DelayedTrigger> get() = _delayedTriggers
    val turn: TurnState get() = _turn
    val priority: PriorityState get() = _priority
    val combat: CombatState? get() = _combat
    /** The states of the game the engine codes by hand ([GameStatus]: day and night), in key order. */
    val statuses: List<GameStatus> get() = _statuses
    /** Tagged events (D33): every applied event change with its turn number. Questions about "this turn" and "last
     * turn" read it; the host may drop entries older than last turn ([forgetOlderTurns]). A host reads them with
     * [arcanum.engine.Inspect.events]. */
    internal val log: List<LoggedChange> get() = _log
    /** Card-defined ledgers (D22, "a spell with that name resolved earlier this game"): per player, counts by name. */
    internal val ledgers: Map<PlayerId, Map<String, Int>> get() = _ledgers
    /** The open decision and the flow waiting for it (D58); while set, only its answer (or Cancel) is accepted. */
    val pending: Pending? get() = _pending
    /** The game-wide timestamp counter (CR 613.7, D34): the highest timestamp given so far. Never goes back, also not
     * on [revert]. */
    val timestampCounter: Long get() = _timestampCounter
    /** How many inputs the game has had on its line so far ([arcanum.engine.input.Input]s performed, every player's;
     * [arcanum.engine.change.Change.InputCounted] each, taken back with the input): the engine names each decision by
     * what it asks, for whom, and this count, so the same question asked again after an undo has the same id and any
     * input in between gives it another. Nothing else moves it (state-based actions, triggers, a host's reorder). */
    internal val inputCount: Int get() = _inputCount
    /** Triggered abilities that triggered and wait to be put on the Stack the next time a player would get priority
     * (CR 603.3, D42), in the order they triggered. */
    internal val triggered: List<WaitingTrigger> get() = _triggered
    /**
     * Per player, the library cards whose place some player knows since the library was last shuffled (CR 401.2: the order is
     * otherwise unknown): 0 for a card whose exact place is known (put on the top, the bottom or Nth from the top, looked
     * at or revealed where it lies, put back in a chosen order), a group number above 0 for cards put in together in a
     * random order (their places are known as a group, not which is where). Cards without an entry are the unknown part.
     * Entries of cards that left the library are dropped when it next changes. Kept by the engine
     * ([arcanum.engine.change.Change.LibraryPlaces]); a host reorders only unknown parts ([arcanum.engine.Host.unknownOrder]).
     */
    internal fun libraryPlaces(player: PlayerId): Map<ObjectId, Int> = _libraryPlaces[player].orEmpty()
    /** Players who attempted to draw from an empty library since the last state-based action check (CR 704.5b). */
    internal val emptyDraws: List<PlayerId> get() = _emptyDraws
    /** The mana each player spent to cast spells this turn (CR 700.14), by the turn it was counted in. */
    internal val spent: Map<PlayerId, SpentOnSpells> get() = _spent
    /** The starting player skips the draw step of turn 1 (CR 103.8a, two players); the host sets false where nobody
     * skips it (CR 103.8c). A setting of the game, not changed by play. */
    val skipFirstDraw: Boolean get() = _skipFirstDraw

    /** The engine's cached view of its own working copy (never set on a host's state). */
    @kotlinx.serialization.Transient
    internal var viewCache: arcanum.engine.view.ViewCache? = null

    // ---- Players

    fun player(id: PlayerId): PlayerState = players.firstOrNull { it.id == id } ?: error("No player $id")

    internal fun updatePlayer(id: PlayerId, change: (PlayerState) -> PlayerState) {
        val index = players.indexOfFirst { it.id == id }
        require(index >= 0) { "No player $id" }
        _players[index] = change(_players[index])
    }

    /** Players still in the game, in turn order starting with [first] (APNAP from the active player, CR 101.4); when
     * [first] has left the game, from the next player after them (CR 800.4j). */
    fun playersFrom(first: PlayerId): List<PlayerId> {
        val ids = players.map { it.id }
        val start = ids.indexOf(first).coerceAtLeast(0)
        return (ids.drop(start) + ids.take(start)).filter { id -> players.first { it.id == id }.lost.not() }
    }

    /** Active player first, then the others in turn order (CR 101.4). */
    fun apnap(): List<PlayerId> = playersFrom(turn.active)

    /** Every other player still in the game (CR 102.2, 102.3: no teams). */
    fun opponents(of: PlayerId): List<PlayerId> = players.filter { it.id != of && !it.lost }.map { it.id }

    // ---- Objects and zones

    fun obj(id: ObjectId): GameObject? = objects[id]

    fun objOrThrow(id: ObjectId): GameObject = objects[id] ?: error("No object $id")

    /** The current object of [card], in whatever zone it is. */
    fun current(card: CardId): GameObject? = objects.values.firstOrNull { it.card == card }

    /** The ordered ids of a zone: a player's own zone for library, hand, graveyard and outside, else the shared one. */
    fun zone(zone: Zone, owner: PlayerId?): List<ObjectId> = when (zone) {
        Zone.Battlefield -> battlefield
        Zone.Stack -> stack
        Zone.Exile -> exile
        Zone.Command -> command
        Zone.Library -> player(requireNotNull(owner) { "library needs an owner" }).library
        Zone.Hand -> player(requireNotNull(owner) { "hand needs an owner" }).hand
        Zone.Graveyard -> player(requireNotNull(owner) { "graveyard needs an owner" }).graveyard
        Zone.Outside -> player(requireNotNull(owner) { "outside needs an owner" }).outside
    }

    /** The objects (cards, tokens, Stack-only items excluded) of a zone, in its order. */
    fun objectsIn(zone: Zone, owner: PlayerId? = null): List<GameObject> =
        if (owner == null && zone.perPlayer) players.flatMap { p -> zone(zone, p.id).mapNotNull { objects[it] } }
        else zone(zone, owner).mapNotNull { objects[it] }

    internal fun setZone(zone: Zone, owner: PlayerId?, ids: List<ObjectId>) {
        when (zone) {
            Zone.Battlefield -> _battlefield = ids.toMutableList()
            Zone.Stack -> _stack = ids.toMutableList()
            Zone.Exile -> _exile = ids.toMutableList()
            Zone.Command -> _command = ids.toMutableList()
            Zone.Library -> updatePlayer(owner!!) { it.copy(library = ids) }
            Zone.Hand -> updatePlayer(owner!!) { it.copy(hand = ids) }
            Zone.Graveyard -> updatePlayer(owner!!) { it.copy(graveyard = ids) }
            Zone.Outside -> updatePlayer(owner!!) { it.copy(outside = ids) }
        }
    }

    /** Position of [id] in its zone (library: 0 is the top; Stack: 0 is the bottom); -1 when not in a zone list. */
    fun positionOf(id: ObjectId): Int {
        val o = objects[id]
        return if (o != null) zone(o.zone, o.owner).indexOf(id) else stack.indexOf(id)
    }

    /** The top of the Stack, or null when it's empty. */
    fun topOfStack(): ObjectId? = stack.lastOrNull()

    /** The timestamp the next stamped object or effect gets (CR 613.7, D34). */
    fun nextTimestamp(): Long = timestampCounter + 1

    // ---- Changes (the only mutators, D56)

    /** Applies [change] (D59): checks its before-values against this state, sets its after-values, and logs it if it
     * is an event (D33). */
    fun apply(change: Change) = Applier.apply(this, change)

    /** Takes [change] back exactly (D59): removes its logged event and restores its before-values. The timestamp
     * counter stays where it is (D34). Changes are taken back last first; an event whose entry the host dropped
     * ([forgetOlderTurns]) still reverts, the log then being empty. */
    fun revert(change: Change) = Applier.revert(this, change)

    // ---- History

    /** Tagged events of turn [turnNumber] (D33). */
    fun eventsOf(turnNumber: Int): List<Change> = log.filter { it.turn == turnNumber }.map { it.change }

    /** Drops logged events older than last turn and returns them: housekeeping for hosts, not a change (D33 keeps this and last turn).
     * Undo still reaches the turns dropped ([revert]), but "this turn" questions there see none of their events: a host
     * that lets players undo that far keeps the log. */
    fun forgetOlderTurns(): List<LoggedChange> {
        val keepFrom = turn.number - 1
        val dropped = _log.takeWhile { it.turn < keepFrom }
        if (dropped.isNotEmpty()) _log = _log.filter { it.turn >= keepFrom }.toMutableList()
        return dropped
    }

    /** Puts back [entries] that [forgetOlderTurns] dropped, before every entry kept: housekeeping for a host that takes
     * back what it did after dropping them, not a change. */
    fun rememberOlderTurns(entries: List<LoggedChange>) {
        _log.addAll(0, entries)
    }

    /** Sets the timestamp counter back to [counter] (D34: [revert] leaves it where it is): housekeeping for a host that has
     * taken back every change made since the counter was [counter], so that play goes on from there with the same
     * timestamps; not a change. */
    fun rewindTimestampCounter(counter: Long) {
        _timestampCounter = counter
    }

    /** [arcanum.engine.Host.samePosition]. */
    internal fun samePosition(o: GameState): Boolean {
        fun players(s: GameState) = s.players.map { it.copy(library = it.library.sortedBy(ObjectId::toString)) }
        return players(this) == players(o) && objects == o.objects && battlefield == o.battlefield && stack == o.stack &&
            exile == o.exile && command == o.command && stackItems == o.stackItems && effects == o.effects &&
            delayedTriggers == o.delayedTriggers && turn == o.turn && priority == o.priority && combat == o.combat &&
            statuses == o.statuses && pending == o.pending && skipFirstDraw == o.skipFirstDraw && ledgers == o.ledgers &&
            inputCount == o.inputCount && triggered == o.triggered && _libraryPlaces == o._libraryPlaces &&
            emptyDraws == o.emptyDraws && spent == o.spent
    }

    /** An independent copy: the engine's own working state (D56). Objects inside are immutable and shared. */
    fun deepCopy(): GameState {
        arcanum.engine.EngineProbes.statesCopied?.invoke()
        return copied()
    }

    private fun copied(): GameState = copy(
        _players = _players.toMutableList(),
        _objects = LinkedHashMap(_objects),
        _battlefield = _battlefield.toMutableList(),
        _stack = _stack.toMutableList(),
        _exile = _exile.toMutableList(),
        _command = _command.toMutableList(),
        _stackItems = LinkedHashMap(_stackItems),
        _effects = LinkedHashMap(_effects),
        _delayedTriggers = LinkedHashMap(_delayedTriggers),
        _log = _log.toMutableList(),
        _triggered = _triggered.toMutableList(),
    )

    /** Holds the test setup's [GameState.of] and [GameState.start] ([ArcanumTestSetup]). */
    companion object
}

/** [amount] mana spent to cast spells in turn [turn] (CR 700.14). */
@Serializable
data class SpentOnSpells(val turn: Int, val amount: Int)

/** One tagged event (D33) of turn [turn]. */
@Serializable
data class LoggedChange(val turn: Int, val change: Change)

/** Library, hand, graveyard and outside the game belong to a player (CR 400.1, 400.11). */
val Zone.perPlayer: Boolean
    get() = this == Zone.Library || this == Zone.Hand || this == Zone.Graveyard || this == Zone.Outside
