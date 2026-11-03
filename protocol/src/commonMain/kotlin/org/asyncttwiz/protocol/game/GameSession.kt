package org.asyncttwiz.protocol.game

import arcanum.engine.input.Input
import arcanum.engine.state.PlayerId


/**
 * A device's copy of one game (I-D54 §3, phase 4): it runs the same engine on the same events as the server.
 *
 * - [model] is what the player sees: the server's events up to [head], then the device's own events the server hasn't
 *   confirmed yet ([pending]), applied at once (provisional; latency hidden everywhere).
 * - The player's inputs go into a draft event ([begin]): a private flow (a cast being announced, I-D11) stays in the
 *   draft until it is complete and is then sent as one [Submission] ([send]); [abandon] takes it back.
 * - The server's events come in through [received]: the device's own next pending event is confirmed; another player's
 *   event on [head] drops whatever the device had pending (the server refuses it: its parent moved) and applies the
 *   server's, so the player sees the board update and can act again. A refusal ([refused]) drops the refused event and
 *   those made on it.
 * - An event elsewhere in the tree (a branch, a rewind) or a revert this model can't make exactly is [Received.Reload]:
 *   the caller takes the state at the event from its history ([GameHistory]) and starts a new session there.
 */
class GameSession(val model: GameModel, head: String, val me: PlayerId) {
    /** The last event the server confirmed on the line this device follows. */
    var head: String = head
        private set

    private val waiting = ArrayDeque<GameEvent>()
    private var draft: EventBuilder? = null
    private var draftId: String? = null

    /** The device's own events sent and not yet confirmed, oldest first. */
    val pending: List<GameEvent> get() = waiting.toList()

    /** The event a new draft is made on: the last pending event, else [head]. */
    val tip: String get() = waiting.lastOrNull()?.id ?: head

    /** Whether a draft is open ([begin]). */
    val drafting: Boolean get() = draft != null

    /** Opens the draft of a new event [id] on [tip]; its inputs and table changes apply to [model] as they are made. */
    fun begin(id: String): EventBuilder {
        check(draft == null) { "A draft is open" }
        return EventBuilder(model, id, tip, me.value).also { draft = it; draftId = id }
    }

    /** Ends the draft: it becomes a pending event, and the submission to send for it. */
    fun send(): Submission {
        val builder = checkNotNull(draft) { "No draft is open" }
        draft = null; draftId = null
        val event = builder.build()
        waiting.addLast(event)
        return Submission.of(event)
    }

    /** Takes the open draft back, if any. */
    fun abandon() {
        draft?.abandon()
        draft = null; draftId = null
    }

    /** One acting point at once: [input] (the other player's, assumed, when [assumed]: I-D56 §3), the random answers
     * from the line's stream that follow it, and [table] changes (I-D35). */
    fun act(id: String, input: Input?, table: List<TableChange> = emptyList(), assumed: Boolean = false): Submission {
        val builder = begin(id)
        try {
            if (input != null) {
                builder.perform(input, assumedBy = if (assumed) me else null)
                builder.answerRandoms()
            }
            table.forEach(builder::table)
        } catch (e: RuntimeException) {
            abandon()
            throw e
        }
        return send()
    }

    /** What [received] did. */
    sealed interface Received {
        /** The device's own pending event, as the server stored it. */
        data object Confirmed : Received
        /** Another event on [head], applied; [dropped] are the device's events (and draft) it replaced. */
        data class Applied(val dropped: List<GameEvent>, val draftDropped: Boolean) : Received
        /** Already known (a repeated delivery). */
        data object Known : Received
        /** Not on this line, or not reachable exactly from here: take the state from the history. */
        data object Reload : Received
    }

    /** The server's accepted [event]. */
    fun received(event: GameEvent): Received {
        if (event.id == head) return Received.Known
        val first = waiting.firstOrNull()
        if (first != null && first == event) {
            waiting.removeFirst()
            head = event.id
            return Received.Confirmed
        }
        if (event.parentId != head) return Received.Reload
        val hadDraft = draft != null
        abandon()
        val dropped = waiting.toList()
        for (e in dropped.asReversed()) {
            model.revert(e)
            if (model.stale) return Received.Reload
        }
        waiting.clear()
        model.apply(event)
        head = event.id
        return Received.Applied(dropped, hadDraft)
    }

    /** The server refused the device's event [id]: it and every pending event made on it are taken back (and the open
     * draft, made on them): [Received.Applied] with the events dropped (none when [id] was dropped already), after
     * which [model] is the state at the last event still pending, or at [head]; or [Received.Reload]. */
    fun refused(id: String): Received {
        val at = waiting.indexOfFirst { it.id == id }
        if (at < 0) return Received.Applied(emptyList(), false)
        val hadDraft = draft != null
        abandon()
        val dropped = waiting.drop(at)
        for (e in dropped.asReversed()) {
            model.revert(e)
            if (model.stale) return Received.Reload
        }
        repeat(dropped.size) { waiting.removeLast() }
        return Received.Applied(dropped, hadDraft)
    }
}
