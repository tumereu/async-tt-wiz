package org.asyncttwiz.automatum.boundary

import arcanum.engine.input.Input
import arcanum.engine.setup.GameResult
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import org.asyncttwiz.protocol.BoardState
import org.asyncttwiz.protocol.MatchEnding
import org.asyncttwiz.protocol.PlayCard
import org.asyncttwiz.protocol.SubmittedDefinition
import org.asyncttwiz.protocol.game.EngineJson
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.GameEvent

/*
 * Web client step 4c: the parts of the boards the engine side publishes ([org.asyncttwiz.android.EngineGame.Shown]) as the
 * page receives them, each sent once and referred to after: events by id ([WireEventsOut], [WireEventsIn]), card data by
 * digest ([WireDefinitionsOut], [WireDefinitionsIn]), a board's cards as the ones of the board sent before it that stayed
 * ([BoardStateWire]), lists as what changed from the same list sent before ([ListDelta]); and the device's values
 * that carry events ([StatusWire], [NoteWire]).
 */

/** The events the page holds, as the engine side knows them: each sent once ([ref]) until the page lets it go ([retain],
 * which both sides do alike after each published board). */
class WireEventsOut(private val codec: EngineJson) {
    private val held = HashMap<String, GameEvent>()
    private var out = LinkedHashMap<String, JsonElement>()

    /** [event]'s id, the event written into the message when the page doesn't hold it (or holds another of that id). */
    fun ref(event: GameEvent): String {
        val had = held[event.id]
        if (had == null || (had !== event && had != event)) {
            out[event.id] = codec.encodeElement(GameEvent.serializer(), event)
            held[event.id] = event
        }
        return event.id
    }

    /** The events written since the last call, for the message. */
    fun take(): Map<String, JsonElement> = out.also { out = LinkedHashMap() }

    /** The page keeps only [ids] now. */
    fun retain(ids: Set<String>) { held.keys.retainAll(ids) }

    /** The page lost everything (a new channel or a reload). */
    fun clear() { held.clear(); out = LinkedHashMap() }

    val size: Int get() = held.size
}

/** The page's events by id, as the engine side sent them. */
class WireEventsIn(private val codec: EngineJson) {
    private val store = HashMap<String, GameEvent>()

    /** The events of a message. */
    fun add(events: Map<String, JsonElement>) { for ((id, e) in events) store[id] = codec.decodeElement(GameEvent.serializer(), e) }

    operator fun get(id: String): GameEvent = store[id] ?: throw IllegalStateException("The engine side referred to event $id the page doesn't hold")

    fun retain(ids: Set<String>) { store.keys.retainAll(ids) }

    val size: Int get() = store.size
}

/** The card data the page holds, by digest: each sent once per game. */
class WireDefinitionsOut {
    private val held = HashSet<String>()
    private var out = LinkedHashMap<String, SubmittedDefinition>()
    private var last: List<SubmittedDefinition>? = null

    /** [definitions]' digests, null when they are the list sent last (the same board's card data). */
    fun ref(definitions: List<SubmittedDefinition>): List<String>? {
        if (last === definitions || last == definitions) return null
        last = definitions
        return definitions.map { d -> d.digest.also { if (held.add(it)) out[it] = d } }
    }

    fun take(): Map<String, SubmittedDefinition> = out.also { out = LinkedHashMap() }
}

/** The page's card data by digest. */
class WireDefinitionsIn {
    private val store = HashMap<String, SubmittedDefinition>()
    private var last: List<SubmittedDefinition> = emptyList()

    fun add(definitions: Map<String, SubmittedDefinition>) { store.putAll(definitions) }

    /** The list [digests] names, or the last one when null. */
    fun of(digests: List<String>?): List<SubmittedDefinition> {
        if (digests == null) return last
        return digests.map { store[it] ?: throw IllegalStateException("The engine side referred to card data $it the page doesn't hold") }
            .also { last = it }
    }
}

/**
 * A list as the one sent before in its place changed: [keep] items from index [from] of the list before, then [add]. Most
 * boards' lines are the line before with an event or two more, or a few dropped at its start.
 */
@Serializable
data class ListDelta<T>(val from: Int = 0, val keep: Int = 0, val add: List<T> = emptyList()) {
    fun applied(before: List<T>): List<T> = if (keep == 0) add else before.subList(from, from + keep) + add

    companion object {
        fun <T> of(before: List<T>, now: List<T>): ListDelta<T> {
            if (now.isEmpty()) return ListDelta()
            val from = before.indexOf(now[0])
            if (from < 0) return ListDelta(add = now.toList())
            var keep = 0
            while (from + keep < before.size && keep < now.size && before[from + keep] == now[keep]) keep++
            return ListDelta(from, keep, now.subList(keep, now.size).toList())
        }
    }
}

/**
 * A [BoardState] as the page receives it: [board] without its cards, then its cards as [cards]: each either the index of
 * a card of the board sent before it (the same card, unchanged) or, from -1 down, one of [added]; and [move] (never
 * serialized with the board).
 */
@Serializable
class BoardStateWire(val board: BoardState, val cards: List<Int>, val added: List<PlayCard> = emptyList(), val move: String? = null)

/** The engine side's boards in the order the page decodes them: each board's cards as those of the one before. */
class BoardStatesOut {
    private var last: List<PlayCard> = emptyList()
    private var index: Map<PlayCard, Int> = emptyMap()

    fun wire(board: BoardState): BoardStateWire {
        if (board.cards !== last) {
            val before = index
            val added = ArrayList<PlayCard>()
            val cards = board.cards.map { card -> before[card] ?: run { added += card; -added.size } }
            last = board.cards
            index = board.cards.withIndex().associate { (i, c) -> c to i }
            return BoardStateWire(board.copy(cards = emptyList()), cards, added, board.move)
        }
        return BoardStateWire(board.copy(cards = emptyList()), board.cards.indices.toList(), emptyList(), board.move)
    }

    fun clear() { last = emptyList(); index = emptyMap() }
}

/** The page's side of [BoardStatesOut]. */
class BoardStatesIn {
    private var last: List<PlayCard> = emptyList()

    fun board(wire: BoardStateWire): BoardState {
        val before = last
        val cards = wire.cards.map { i -> if (i >= 0) before[i] else wire.added[-i - 1] }
        last = cards
        return wire.board.copy(cards = cards, move = wire.move)
    }
}

/** [GameDevice.Status] as the page receives it, its events by id. */
@Serializable
sealed interface StatusWire {
    @Serializable @SerialName("reload") data object Reload : StatusWire
    @Serializable @SerialName("catch-up") data object CatchUp : StatusWire
    @Serializable @SerialName("playing") data class Playing(val next: String) : StatusWire
    @Serializable @SerialName("stopped") data class Stopped(val event: String, val assumed: Input, val paused: Boolean = false) : StatusWire
    @Serializable @SerialName("drafting") data object Drafting : StatusWire
    @Serializable @SerialName("your-move") data object YourMove : StatusWire
    @Serializable @SerialName("waiting") data class Waiting(val reason: GameDevice.Wait, val offer: GameDevice.Offer? = null) : StatusWire
    @Serializable @SerialName("over") data class Over(val result: GameResult, val confirmable: Boolean, val final: Boolean,
        val ending: MatchEnding = MatchEnding.ByRules, val endedBy: PlayerId? = null, val ownOk: Boolean = false) : StatusWire
    @Serializable @SerialName("idle") data object Idle : StatusWire

    companion object {
        fun of(status: GameDevice.Status, ref: (GameEvent) -> String): StatusWire = when (status) {
            GameDevice.Status.Reload -> Reload
            GameDevice.Status.CatchUp -> CatchUp
            is GameDevice.Status.Playing -> Playing(ref(status.next))
            is GameDevice.Status.Stopped -> Stopped(ref(status.event), status.assumed, status.paused)
            GameDevice.Status.Drafting -> Drafting
            GameDevice.Status.YourMove -> YourMove
            is GameDevice.Status.Waiting -> Waiting(status.reason, status.offer)
            is GameDevice.Status.Over -> Over(status.result, status.confirmable, status.final, status.ending, status.endedBy, status.ownOk)
            GameDevice.Status.Idle -> Idle
        }
    }

    fun restored(event: (String) -> GameEvent): GameDevice.Status = when (this) {
        Reload -> GameDevice.Status.Reload
        CatchUp -> GameDevice.Status.CatchUp
        is Playing -> GameDevice.Status.Playing(event(next))
        is Stopped -> GameDevice.Status.Stopped(event(this.event), assumed, paused)
        Drafting -> GameDevice.Status.Drafting
        YourMove -> GameDevice.Status.YourMove
        is Waiting -> GameDevice.Status.Waiting(reason, offer)
        is Over -> GameDevice.Status.Over(result, confirmable, final, ending, endedBy, ownOk)
        Idle -> GameDevice.Status.Idle
    }
}

/** [GameDevice.Note] as the page receives it, its events by id. */
@Serializable
sealed interface NoteWire {
    @Serializable @SerialName("branched")
    data class Branched(val by: PlayerId, val discarded: String, val takenBack: List<String>, val actions: List<String> = emptyList()) : NoteWire
    @Serializable @SerialName("taken-back") data class TakenBack(val by: PlayerId, val takenBack: List<String>) : NoteWire
    @Serializable @SerialName("request-needed") data class RequestNeeded(val target: String) : NoteWire
    @Serializable @SerialName("refused") data class Refused(val reason: String, val on: ObjectId? = null) : NoteWire

    companion object {
        fun of(note: GameDevice.Note, ref: (GameEvent) -> String): NoteWire = when (note) {
            is GameDevice.Note.Branched -> Branched(note.by, ref(note.discarded), note.takenBack.map(ref), note.actions.map(ref))
            is GameDevice.Note.TakenBack -> TakenBack(note.by, note.takenBack.map(ref))
            is GameDevice.Note.RequestNeeded -> RequestNeeded(note.target)
            is GameDevice.Note.Refused -> Refused(note.reason, note.on)
        }
    }

    fun restored(event: (String) -> GameEvent): GameDevice.Note = when (this) {
        is Branched -> GameDevice.Note.Branched(by, event(discarded), takenBack.map(event), actions.map(event))
        is TakenBack -> GameDevice.Note.TakenBack(by, takenBack.map(event))
        is RequestNeeded -> GameDevice.Note.RequestNeeded(target)
        is Refused -> GameDevice.Note.Refused(reason, on)
    }
}
