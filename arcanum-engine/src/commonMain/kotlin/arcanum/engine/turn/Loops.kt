package arcanum.engine.turn

import arcanum.api.CardDefinition
import arcanum.api.FaceDefinition
import arcanum.engine.Pending
import arcanum.engine.Progress
import arcanum.engine.casting.decisionId
import arcanum.engine.change.Change
import arcanum.engine.change.GameEvent
import arcanum.engine.change.Recorder
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Continuation
import arcanum.engine.decision.Decision
import arcanum.engine.decision.LoopStop
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectFacts
import arcanum.engine.state.ObjectId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/**
 * A game state with object ids, timestamps and what is recorded about the past left out ([arcanum.engine.Host.loopKey]):
 * two states with the same key differ only in those. A hash of the state's canonical form, so a host can keep many.
 */
@Serializable
data class LoopKey(val value: String) {
    override fun toString(): String = value
}

/**
 * Loops (CR 104.4b, 732.4): the safety cap of one [arcanum.engine.Engine.perform] call, and the draw a host reports for
 * a loop of mandatory actions.
 */
internal object Loops {
    /**
     * The most rounds of state-based actions and triggers ([arcanum.engine.Perform.checks]) one call makes before it
     * stops with "possible loop": the engine's and the integration's tests (the cube, Bloomburrow and Duskmourn
     * scenarios, long random games) never need more than 3 in one call.
     */
    const val MAX_ROUNDS = 200

    /** The most changes one call makes before it stops with "possible loop" (checked between rounds of state-based
     * actions and triggers): in the same tests a call has made at most about a hundred by then. */
    const val MAX_CHANGES = 20_000

    /**
     * Before another round of state-based actions and triggers: stops with the loop choice ([choice], "possible loop")
     * once the call made [MAX_ROUNDS] rounds or [MAX_CHANGES] changes, so a loop no player can stop (an unbounded chain
     * of mandatory triggers) doesn't hang the call; [LoopStop.CONTINUE] goes on with the next round in a new call,
     * [LoopStop.DRAW] ends the game as a draw (CR 104.4b). Null while under the cap.
     */
    fun capped(recorder: Recorder): Progress.Waiting? {
        val rounds = recorder.rounds
        val changes = recorder.changes.size
        if (rounds < MAX_ROUNDS && changes < MAX_CHANGES) return null
        val s = recorder.state
        val what = if (rounds >= MAX_ROUNDS) "$rounds rounds of state-based actions and triggered abilities" else "$changes changes"
        val text = "Possible loop: the game stopped after $what in one action. Continue, or end the game as a draw?"
        return choice(s, s.priority.holder ?: s.turn.active, text, LoopStop.Cap)
    }

    /** [arcanum.engine.input.Input.MandatoryLoop]: the host found a loop of mandatory actions; the loop choice ([choice])
     * says the game is in a loop nobody can stop, which makes it a draw (CR 104.4b). */
    fun report(recorder: Recorder): Progress.Waiting {
        val s = recorder.state
        val holder = requireNotNull(s.priority.holder) { "A loop is reported where a player has priority (CR 104.4b)" }
        val text = "The game is in a loop of actions nobody can stop, which makes it a draw (CR 104.4b). End it as a draw, " +
            "or continue?"
        return choice(s, holder, text, LoopStop.Mandatory(key(s)))
    }

    /** The loop choice: [player] (or another player for them) chooses [LoopStop.CONTINUE] or [LoopStop.DRAW], told why
     * by [text]. */
    private fun choice(s: GameState, player: arcanum.engine.state.PlayerId, text: String, stop: LoopStop) = Progress.Waiting(Pending(
        Decision.ChooseOption(decisionId(s, player, "loop"), player, null, listOf(LoopStop.CONTINUE, LoopStop.DRAW), text),
        Continuation.Loop(stop),
    ))

    /** After the loop choice ([answer], checked against the options): [LoopStop.CONTINUE] goes on; [LoopStop.DRAW] ends
     * the game as a draw (CR 104.4b). */
    fun resume(recorder: Recorder, answer: Answer): Progress {
        if ((answer as Answer.Option).chosen == LoopStop.DRAW) {
            val s = recorder.state
            recorder.record(Change.Happened(GameEvent.MandatoryLoop))
            // CR 104.4b: the game is a draw; the remaining players leave it together (GameResult.Draw)
            s.players.filterNot { it.lost }.map { it.id }.forEach { recorder.record(Change.Lost(it, false, true, arcanum.engine.setup.LossReason.Loop)) }
        }
        return Progress.Done
    }

    // ---- The key

    /** Per-turn counts of uses ("this turn" history) that a loop would raise each time round. */
    private fun ObjectFacts.withoutCounts() = copy(activations = emptyList(), resolutions = emptyList(), triggers = emptyList(), modeTurns = emptyList())

    /** A card's definition by its face names only: a card's definition doesn't change, so its names say which it is. */
    private fun named(d: CardDefinition) = CardDefinition(d.layout, d.faces.map { FaceDefinition(it.name) })

    private val json = Json

    private fun id(id: ObjectId): String = json.encodeToJsonElement(ObjectId.serializer(), id).jsonPrimitive.content

    /**
     * [arcanum.engine.Host.loopKey]: [state] in JSON without its log, ledgers, per-turn counts, input count and
     * timestamps, cards'
     * definitions by name, every object, Stack item, effect and delayed trigger id replaced by its place (zones in
     * order first: libraries, hands, graveyards, outside the game, the battlefield, the Stack, exile, the command zone;
     * the unordered zones by timestamp), then hashed.
     */
    fun key(state: GameState): LoopKey {
        // The battlefield, exile and the command zone have no order (CR 400.1): by timestamp, which a host may keep
        // in its own way
        fun byTime(ids: List<ObjectId>) = ids.sortedBy { state.obj(it)?.timestamp ?: 0L }.toMutableList()
        val light = state.copy(
            _battlefield = byTime(state.battlefield),
            _exile = byTime(state.exile),
            _command = byTime(state.command),
            _players = state.players.map { it.copy(ledger = emptyMap()) }.toMutableList(),
            _objects = state.objects.mapValuesTo(LinkedHashMap()) { (_, o) ->
                o.copy(definition = o.definition?.let(::named), facts = o.facts.withoutCounts())
            },
            _stackItems = state.stackItems.mapValuesTo(LinkedHashMap()) { (_, item) -> item.copy(facts = item.facts?.withoutCounts()) },
            _ledger = mutableMapOf(),
            _log = mutableListOf(),
            _timestampCounter = 0,
            _inputCount = 0,
        )
        val canon = Canon()
        state.players.forEach { p -> (p.library + p.hand + p.graveyard + p.outside).forEach { canon.known(id(it)) } }
        (light.battlefield + light.stack + light.exile + light.command).forEach { canon.known(id(it)) }
        state.objects.keys.forEach { canon.known(id(it)) }
        state.stackItems.keys.forEach { canon.known(id(it)) }
        state.effects.keys.forEach { canon.known(it.value) }
        state.delayedTriggers.keys.forEach { canon.known(it.value) }
        val text = canon.rewrite(json.encodeToJsonElement(GameState.serializer(), light)).toString()
        return LoopKey(hash(text))
    }

    /** Names ids by their place: those [known] first, in that order; ids of objects that have left (last known
     * information) as they are met. */
    private class Canon {
        private val names = LinkedHashMap<String, String>()
        private val known = HashSet<String>()

        fun known(id: String) {
            known += id
            name(id)
        }

        private fun name(id: String): String = names.getOrPut(id) { "#${names.size}" }

        private fun isId(s: String) = s in known || (s.startsWith("c:") && s.lastIndexOf('#') > 2) || s.startsWith("s:")

        fun rewrite(e: JsonElement): JsonElement = when (e) {
            is JsonObject -> {
                var renamed = false
                val entries = e.entries.filter { it.key != "timestamp" }.map { (k, v) ->
                    val key = if (isId(k)) name(k).also { renamed = true } else k
                    key to rewrite(v)
                }
                // Maps by id (objects, Stack items, effects): in the order of the names, not of insertion
                JsonObject(LinkedHashMap<String, JsonElement>().apply {
                    (if (renamed) entries.sortedBy { it.first } else entries).forEach { (k, v) -> put(k, v) }
                })
            }
            is JsonArray -> JsonArray(e.map(::rewrite))
            is JsonPrimitive -> if (e.isString && isId(e.content)) JsonPrimitive(name(e.content)) else e
        }
    }

    /** 128 bits: two 64-bit hashes of [text] (FNV-1a and a multiply-xorshift one). */
    private fun hash(text: String): String {
        var a = -0x340d631b7bdddcdbL // FNV-1a offset basis 0xcbf29ce484222325
        var b = 0x2545F4914F6CDD1DL
        for (c in text) {
            a = (a xor c.code.toLong()) * 0x100000001b3L
            b = (b xor c.code.toLong()) * -0x61c8864680b583ebL // 0x9E3779B97F4A7C15
            b = b xor (b ushr 29)
        }
        return a.toULong().toString(16).padStart(16, '0') + b.toULong().toString(16).padStart(16, '0')
    }
}

