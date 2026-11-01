package arcanum.engine.state

import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.Pending
import arcanum.engine.change.Applier
import arcanum.engine.change.Change
import kotlinx.serialization.Serializable

/**
 * One game in MTG terms, as one caller sees it (D56, D60): players in turn order, every object by id, the shared
 * zones, the Stack with its items, effect objects, delayed triggers, turn and priority, ledgers, this and last turn's
 * tagged events, and the open continuation.
 *
 * Mutable for speed, but only [apply] and [revert] change it (D56, D59). [arcanum.engine.Engine] never changes the
 * state it is given: it works on [deepCopy] and returns the changes.
 */
@Serializable
data class GameState(
    /** In turn order (CR 101.4, D61). */
    var players: MutableList<PlayerState>,
    /** Every card and token object, in any zone, by its current id. */
    var objects: MutableMap<ObjectId, GameObject> = mutableMapOf(),
    var battlefield: MutableList<ObjectId> = mutableListOf(),
    /** Bottom first: the last is the top of the Stack (CR 405.2). Spells' cards and [StackItem]-only objects. */
    var stack: MutableList<ObjectId> = mutableListOf(),
    var exile: MutableList<ObjectId> = mutableListOf(),
    /** Emblems and other command-zone objects (CR 408). */
    var command: MutableList<ObjectId> = mutableListOf(),
    /** The choices of every spell and ability on the Stack, by id. */
    var stackItems: MutableMap<ObjectId, StackItem> = mutableMapOf(),
    var effects: MutableMap<EffectId, EffectObject> = mutableMapOf(),
    var delayedTriggers: MutableMap<EffectId, DelayedTrigger> = mutableMapOf(),
    var turn: TurnState,
    var priority: PriorityState = PriorityState(null),
    var combat: CombatState? = null,
    /** Game-scoped ledger entries (D22), by key. */
    var ledger: MutableMap<String, Int> = mutableMapOf(),
    /** Tagged events (D33): every applied event change with its turn number. Questions about "this turn" and "last
     * turn" read it; the host may drop entries older than last turn ([forgetOlderTurns]). */
    var log: MutableList<LoggedChange> = mutableListOf(),
    /** The open decision and the flow waiting for it (D58); while set, only its answer (or Cancel) is accepted. */
    var pending: Pending? = null,
    /** The game-wide timestamp counter (CR 613.7, D34): the highest timestamp given so far. Never goes back, also not
     * on [revert]. */
    var timestampCounter: Long = 0,
) {
    // ---- Players

    fun player(id: PlayerId): PlayerState = players.firstOrNull { it.id == id } ?: error("No player $id")

    internal fun updatePlayer(id: PlayerId, change: (PlayerState) -> PlayerState) {
        val index = players.indexOfFirst { it.id == id }
        require(index >= 0) { "No player $id" }
        players[index] = change(players[index])
    }

    /** Players still in the game, in turn order starting with [first] (APNAP from the active player, CR 101.4). */
    fun playersFrom(first: PlayerId): List<PlayerId> {
        val ids = players.filterNot { it.lost }.map { it.id }
        val start = ids.indexOf(first).coerceAtLeast(0)
        return ids.drop(start) + ids.take(start)
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
            Zone.Battlefield -> battlefield = ids.toMutableList()
            Zone.Stack -> stack = ids.toMutableList()
            Zone.Exile -> exile = ids.toMutableList()
            Zone.Command -> command = ids.toMutableList()
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
     * counter stays where it is (D34). */
    fun revert(change: Change) = Applier.revert(this, change)

    // ---- History

    /** Tagged events of turn [turnNumber] (D33). */
    fun eventsOf(turnNumber: Int): List<Change> = log.filter { it.turn == turnNumber }.map { it.change }

    /** Drops logged events older than last turn: housekeeping for hosts, not a change (D33 keeps this and last turn). */
    fun forgetOlderTurns() {
        log = log.filter { it.turn >= turn.number - 1 }.toMutableList()
    }

    /** An independent copy: the engine's own working state (D56). Objects inside are immutable and shared. */
    fun deepCopy(): GameState = copy(
        players = players.toMutableList(),
        objects = LinkedHashMap(objects),
        battlefield = battlefield.toMutableList(),
        stack = stack.toMutableList(),
        exile = exile.toMutableList(),
        command = command.toMutableList(),
        stackItems = LinkedHashMap(stackItems),
        effects = LinkedHashMap(effects),
        delayedTriggers = LinkedHashMap(delayedTriggers),
        ledger = LinkedHashMap(ledger),
        log = log.toMutableList(),
    )

    companion object {
        /** A game with [players] (in turn order) at 20 life, turn 1 of the first player in [step]. */
        fun start(players: List<PlayerId>, life: Int = 20, step: TurnStep = TurnStep.PrecombatMain): GameState =
            GameState(
                players = players.map { PlayerState(it, life, startingLife = life) }.toMutableList(),
                turn = TurnState(1, players.first(), step, turnStarted = mapOf(players.first() to 1)),
                priority = PriorityState(players.first()),
            )
    }
}

/** One tagged event (D33) of turn [turn]. */
@Serializable
data class LoggedChange(val turn: Int, val change: Change)

/** Library, hand, graveyard and outside the game belong to a player (CR 400.1, 400.11). */
val Zone.perPlayer: Boolean
    get() = this == Zone.Library || this == Zone.Hand || this == Zone.Graveyard || this == Zone.Outside
