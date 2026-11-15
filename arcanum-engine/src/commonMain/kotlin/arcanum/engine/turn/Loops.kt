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
import arcanum.engine.state.CardId
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
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
 * Equal keys are equal by [value] alone.
 *
 * A key worked out on a state (not one decoded) also keeps the place naming it used: [place] names an object by its
 * place in that state, [objectAt] gives the object at a place, so a host can find, for an object named in an input made on
 * one state, the one in the same place of another state with the same key.
 */
@Serializable
data class LoopKey(val value: String) {
    /** Object id (its serial form) to its place name, as worked out; empty for a decoded key. */
    @kotlinx.serialization.Transient
    internal var names: Map<String, String> = emptyMap()

    /** [id]'s place in the state this key was worked out on, or null when it had none there (hidden, or unknown). */
    fun place(id: ObjectId): String? = names[Json.encodeToJsonElement(ObjectId.serializer(), id).jsonPrimitive.content]

    /** The object at [place] in the state this key was worked out on, or null. */
    fun objectAt(place: String): ObjectId? = names.entries.firstOrNull { it.value == place && (it.key.startsWith("c:") || it.key.startsWith("s:")) }
        ?.let { Json.decodeFromJsonElement(ObjectId.serializer(), JsonPrimitive(it.key)) }

    override fun toString(): String = value
}

/**
 * Loops (CR 104.4b, 733.4): the safety cap of one [arcanum.engine.Engine.perform] call, and the draw a host reports for
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
            s.players.filterNot { it.lost }.map { it.id }.forEach { recorder.record(Change.Lost(it, null, arcanum.engine.setup.LossReason.Loop)) }
        }
        return Progress.Done
    }

    // ---- The key

    /** Per-turn counts of uses ("this turn" history) that a loop would raise each time round. */

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
            // The statuses that count what happens over a game (speed, the Ring, dungeons) and day and night, as ledgers did
            _players = state.players.map { p -> p.copy(statuses = p.statuses.filter { it is arcanum.engine.state.PlayerStatus.Designated }) }
                .toMutableList(),
            _statuses = emptyList(),
            _objects = state.objects.mapValuesTo(LinkedHashMap()) { (_, o) ->
                o.copy(definition = named(o.definition), uses = arcanum.engine.state.UseCounts.NONE)
            },
            _ledgers = emptyMap(),
            _emptyDraws = emptyList(),
            _spent = emptyMap(),
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
     * information) as they are met. An id [hidden] says is hidden is "#h" wherever it is met, and an object naming itself
     * by such an id ("id") is that name alone. */
    private class Canon(private val hidden: (String) -> Boolean = { false }) {
        private val names = LinkedHashMap<String, String>()
        private val known = HashSet<String>()

        fun known(id: String) {
            known += id
            name(id)
        }

        /** The names given so far, by id. */
        fun named(): Map<String, String> = names.toMap()

        private fun name(id: String): String = if (hidden(id)) HIDDEN else names.getOrPut(id) { "#${names.size}" }

        private fun isId(s: String) = s in known || (s.startsWith("c:") && s.lastIndexOf('#') > 2) || s.startsWith("s:")

        fun rewrite(e: JsonElement): JsonElement = when (e) {
            is JsonObject -> {
                val self = (e["id"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                if (self != null && isId(self) && hidden(self)) JsonPrimitive(HIDDEN)
                else rewriteObject(e)
            }
            is JsonArray -> JsonArray(e.map(::rewrite))
            is JsonPrimitive -> if (e.isString && isId(e.content)) JsonPrimitive(name(e.content)) else e
        }

        private fun rewriteObject(e: JsonObject): JsonElement {
            var renamed = false
            var entries = e.entries.filter { it.key != "timestamp" }.map { (k, v) ->
                val key = if (isId(k)) name(k).also { renamed = true } else k
                key to rewrite(v)
            }
            // A hidden card's place in a zone the viewer can't see (a move's index in a hand or library) tells how many
            // cards are there
            fun hiddenSide(side: String) = entries.any { it.first == side && it.second == JsonPrimitive(HIDDEN) }
            if (hiddenSide("before")) entries = entries.filter { it.first != "fromIndex" }
            if (hiddenSide("after")) entries = entries.filter { it.first != "toIndex" }
            // Maps by id (objects, Stack items, effects): in the order of the names, not of insertion
            return JsonObject(LinkedHashMap<String, JsonElement>().apply {
                (if (renamed) entries.sortedBy { it.first } else entries).forEach { (k, v) -> put(k, v) }
            })
        }

        companion object {
            const val HIDDEN = "#h"
        }
    }

    /**
     * [arcanum.engine.Host.loopKey] for [viewer]: the position as [viewer] could tell it. Like [key], with object ids,
     * Stack item, effect, delayed trigger and the open decision's ids replaced by their places and timestamps, the
     * timestamp counter and the input count left out (they shift with any change elsewhere); but:
     * - every card [viewer] may not see ([Knowledge.visible]) is masked: in a library, a hand or outside the game it is
     *   left out with its known place, so neither the contents, the order nor the number of the cards they can't see
     *   counts; elsewhere (a face-down card) it stays in its place with its identity (card and definition) left out;
     *   wherever such a card is named (the log, a decision, an effect) it is the one name "#h";
     * - the characteristics of what [viewer] may see are part of it ([Engine.view]: an effect counting hidden cards shows
     *   in what it changes), and so are this turn's logged changes (masked the same way), so two runs that end equal by
     *   different visible steps this turn differ;
     * - everything else is compared as it is, the ledgers, "this turn" counts and statuses included: nothing is left
     *   out for looking irrelevant.
     */
    fun key(state: GameState, viewer: PlayerId): LoopKey {
        val (text, names) = viewerText(state, viewer)
        return LoopKey(hash(text)).also { it.names = names }
    }

    /** [key]'s canonical text for [viewer], before it is hashed, and its place names (tests read it to say what differs). */
    internal fun viewerText(state: GameState, viewer: PlayerId): Pair<String, Map<String, String>> {
        val view = arcanum.engine.view.Layers.view(state)
        val visible = arcanum.engine.action.Knowledge.visible(state, view, viewer)
        val hiddenZones = setOf(arcanum.api.Zone.Library, arcanum.api.Zone.Hand, arcanum.api.Zone.Outside)
        // Left out: hidden cards in hidden zones; masked in place: hidden cards elsewhere (face down)
        val gone = state.objects.values.filter { it.id !in visible && it.zone in hiddenZones }.map { it.id }.toSet()
        val masked = state.objects.values.filter { it.id !in visible && it.zone !in hiddenZones }.map { it.id }.toSet()
        // A card that is hidden now is hidden in whatever it is named by (an earlier object of it, in the log)
        val hiddenCards = (gone + masked).map { it.card }.toSet()
        fun byTime(ids: List<ObjectId>) = ids.filter { it !in gone }.sortedBy { state.obj(it)?.timestamp ?: 0L }.toMutableList()
        val turn = state.turn.number
        val light = state.copy(
            _battlefield = byTime(state.battlefield),
            _exile = byTime(state.exile),
            _command = byTime(state.command),
            _stack = state.stack.filter { it !in gone }.toMutableList(),
            _players = state.players.map { p ->
                p.copy(library = p.library.filter { it !in gone }, hand = p.hand.filter { it !in gone },
                    graveyard = p.graveyard.filter { it !in gone }, outside = p.outside.filter { it !in gone })
            }.toMutableList(),
            _objects = state.objects.filterKeys { it !in gone }.mapValuesTo(LinkedHashMap()) { (id, o) ->
                if (id in masked) o.copy(definition = CardDefinition(arcanum.api.Layout.Normal, emptyList()))
                else o.copy(definition = named(o.definition))
            },
            _log = state.log.filter { it.turn == turn }.toMutableList(),
            _libraryPlaces = state._libraryPlaces.mapValues { (_, places) -> places.filterKeys { it !in gone } }.filterValues { it.isNotEmpty() },
            _timestampCounter = 0,
            _inputCount = 0,
        )
        // What the viewer may see of the layers' results (the objects themselves are in the state): not the rules and
        // abilities of cards they may not see (a hidden card's own replacement effects are listed wherever it is)
        val unseen = gone + masked
        fun seenOnly(list: List<arcanum.engine.view.AppliedMechanic>) = list.filter { it.source == null || it.source !in unseen }
        val seen = view.copy(
            objects = view.objects.filterKeys { it in visible && it !in masked }.mapValues { (_, o) -> o.copy(obj = null, mechanics = seenOnly(o.mechanics)) },
            players = view.players.mapValues { (_, p) -> p.copy(mechanics = seenOnly(p.mechanics)) },
            game = view.game.copy(rules = seenOnly(view.game.rules)),
        )
        val canon = Canon(hidden = { s -> s.startsWith("c:") && s.lastIndexOf('#') > 2 && CardId(s.substring(2, s.lastIndexOf('#'))) in hiddenCards })
        light.players.forEach { p -> (p.library + p.hand + p.graveyard + p.outside).forEach { canon.known(id(it)) } }
        (light.battlefield + light.stack + light.exile + light.command).forEach { canon.known(id(it)) }
        light.objects.keys.forEach { canon.known(id(it)) }
        state.stackItems.keys.filter { it !in gone }.forEach { canon.known(id(it)) }
        state.effects.keys.forEach { canon.known(it.value) }
        state.delayedTriggers.keys.forEach { canon.known(it.value) }
        // The open decision's id counts the game's inputs: by its place, as the one open decision; so does the id an open
        // cast or activation keeps for its Stack item (a timestamp)
        state.pending?.decision?.id?.let { canon.known(it.value) }
        when (val c = state.pending?.continuation) {
            is arcanum.engine.decision.Continuation.Announcing -> c.choices.item?.let(canon::known)
            else -> {}
        }
        val text = canon.rewrite(json.encodeToJsonElement(GameState.serializer(), light)).toString() + "\n" +
            canon.rewrite(json.encodeToJsonElement(arcanum.engine.view.View.serializer(), seen)).toString()
        return text to canon.named()
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

