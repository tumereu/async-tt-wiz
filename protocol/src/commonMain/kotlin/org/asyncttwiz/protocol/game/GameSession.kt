package org.asyncttwiz.protocol.game

import arcanum.engine.input.Input
import arcanum.engine.state.GameState
import arcanum.engine.state.PlayerId


/**
 * A device's copy of one game's main line (I-D54 §3, I-D56 §7, §13): it runs the same engine on the same events as the
 * server. The policy of what the device does with it (playback stops, its own passes, assumptions, undo) is
 * [GameDevice]'s; this is the data.
 *
 * - [events] is the main line as the server accepted it, from [lineStart] (a recent point: older events are trimmed) up
 *   to [head]. The player's **local head** ([localHead]) is the newest of them shown; the events after it are
 *   [buffered], received and not shown yet (playback shows them one at a time, [showNext]).
 * - [model] is what the player sees: the state at [localHead], then the device's own events the server hasn't confirmed
 *   yet ([pending]), applied at once (provisional; latency hidden everywhere). Pending events exist only while nothing
 *   is buffered (the device acts at the head) or as the one answer it gave at its local head (a branch, I-D56 §8).
 * - The player's inputs go into a draft event ([begin]): a private flow (a cast being announced) stays in the draft
 *   until it is complete and is then sent as one [Submission] ([send]); [abandon] takes it back. A draft at the local
 *   head while events are buffered starts a new line (a branch at a decision stop): sending it discards the buffered
 *   events.
 * - The server's events come in through [received]: the device's own next pending event is confirmed; another event on
 *   [head] drops whatever the device had pending (the server refuses it: its parent moved) and is buffered; an event on
 *   an earlier main-line event is a branch (the line after it is discarded, and the shown state goes back to it when it
 *   was past it). A rewind ([rewound]) deletes the events after its target. What the model can't follow exactly is
 *   [Received.Reload]: the caller loads the game again.
 */
class GameSession(base: GameModel, baseId: String, events: List<GameEvent>, localHead: String, val me: PlayerId) {
    /** Older constructor: a session at [head] with nothing buffered. */
    constructor(model: GameModel, head: String, me: PlayerId) : this(model, head, emptyList(), head, me)

    /** What the player sees: the state at [localHead] and the [pending] events on it. */
    val model: GameModel = base

    /** The event before the first of [events]: the oldest point the device can go back to without loading. */
    var lineStart: String = baseId
        private set

    private val line = ArrayList<GameEvent>(events)
    /** How many of [line] are applied to [model] (the rest are buffered). */
    private var shown = 0
    private val waiting = ArrayDeque<GameEvent>()
    private var draft: EventBuilder? = null
    private var draftAt: String? = null
    /** The events the device's own pending branch replaces, and those received on them since (ignored). */
    private val superseded = mutableSetOf<String>()

    /** Called with the state before each event played back ([showNext]), for tests' leak oracle. */
    var beforeShow: ((GameState, GameEvent) -> Unit)? = null
    /** Called with the state before each event the device sends ([sendEvent]), for tests' leak oracle. */
    var onSent: ((GameState, GameEvent) -> Unit)? = null

    init {
        val upTo = if (localHead == baseId) 0 else line.indexOfFirst { it.id == localHead } + 1
        require(localHead == baseId || upTo > 0) { "The local head $localHead isn't on the line" }
        repeat(upTo) { model.apply(line[shown++]) }
    }

    /** The newest main-line event shown to the player. */
    val localHead: String get() = if (shown == 0) lineStart else line[shown - 1].id

    /** The main line's newest event as this device knows it. */
    val head: String get() = line.lastOrNull()?.id ?: lineStart

    /** The main line after [lineStart] up to [head], oldest first. */
    val events: List<GameEvent> get() = line.toList()

    /** The received events past [localHead], not shown yet. */
    val buffered: List<GameEvent> get() = line.subList(shown, line.size).toList()

    /** Whether events past the local head are waiting to be shown. */
    val behind: Boolean get() = shown < line.size

    /** The device's own events sent and not yet confirmed, oldest first. */
    val pending: List<GameEvent> get() = waiting.toList()

    /** The event a new draft is made on: the last pending event, else [localHead]. */
    val tip: String get() = waiting.lastOrNull()?.id ?: localHead

    /** Whether a draft is open ([begin]). */
    val drafting: Boolean get() = draft != null

    /** Whether [id] is [lineStart] or on [events]. */
    fun knows(id: String): Boolean = id == lineStart || line.any { it.id == id }

    /** The index in [events] of [id] (−1 for [lineStart]), or null when unknown. */
    fun indexOf(id: String): Int? = if (id == lineStart) -1 else line.indexOfFirst { it.id == id }.takeIf { it >= 0 }

    /** The main-line events after [id] up to [head] (null when [id] isn't known). */
    fun after(id: String): List<GameEvent>? = indexOf(id)?.let { line.subList(it + 1, line.size).toList() }

    /**
     * Opens the draft of a new event [id] on [tip]; its inputs and table changes apply to [model] as they are made.
     * [newLine]: the draft is made at the local head while events are buffered (a branch, I-D56 §8): it starts that line
     * as its first step.
     */
    fun begin(id: String, newLine: Int? = null): EventBuilder {
        check(draft == null) { "A draft is open" }
        if (behind) {
            check(waiting.isEmpty() && newLine != null) { "Behind the head, only a branch at the local head is made" }
        }
        return EventBuilder(model, id, tip, me.value).also { b ->
            newLine?.let(b::startLine)
            draft = b; draftAt = tip
        }
    }

    /** Ends the draft: it becomes a pending event, and the submission to send for it. A branch discards the buffered
     * events (they are the line it replaces). */
    fun send(): Submission = Submission.of(sendEvent())

    /** [send], returning the event made. */
    fun sendEvent(): GameEvent {
        val builder = checkNotNull(draft) { "No draft is open" }
        draft = null; draftAt = null
        val event = builder.build()
        onSent?.let { hook -> hook(model.copy().also { it.revert(event) }.engine, event) }
        while (behind) superseded += line.removeLast().id
        waiting.addLast(event)
        return event
    }

    /** Takes the open draft back, if any. */
    fun abandon() {
        draft?.abandon()
        draft = null; draftAt = null
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

    /** Shows the next buffered event (playback): it is applied to [model] and becomes the local head. */
    fun showNext(): GameEvent {
        check(behind && draft == null && waiting.isEmpty()) { "Nothing to show" }
        val event = line[shown]
        beforeShow?.invoke(model.engine, event)
        model.apply(event)
        shown++
        return event
    }

    /** What [received], [refused] and [rewound] did. */
    sealed interface Received {
        /** The device's own pending event, as the server stored it. */
        data object Confirmed : Received
        /** Another event on [head], buffered; [dropped] are the device's events (and draft) it replaced. */
        data class Applied(val dropped: List<GameEvent>, val draftDropped: Boolean) : Received
        /**
         * The main line changed before [head] (a branch, or a rewind): [discarded] are the events no longer on it, oldest
         * first, [takenBack] those of them the player had been shown (the model went back to the point where the line
         * changed), [dropped] the device's own pending events.
         */
        data class Changed(val discarded: List<GameEvent>, val takenBack: List<GameEvent>, val dropped: List<GameEvent>) : Received
        /** Already known (a repeated delivery). */
        data object Known : Received
        /** Not on this line, or not reachable exactly from here: load the game again. */
        data object Reload : Received
    }

    /** The server's accepted [event]. */
    fun received(event: GameEvent): Received {
        if (knows(event.id) || event.id in superseded) return Received.Known
        // An event the server took before the device's own branch, on the line the branch replaces
        if (event.parentId in superseded) { superseded += event.id; return Received.Known }
        val first = waiting.firstOrNull()
        if (first != null && first.id == event.id) {
            if (first != event) return Received.Reload
            waiting.removeFirst()
            if (waiting.isEmpty()) superseded.clear()
            line += event
            shown++
            return Received.Confirmed
        }
        // Behind the head (no pending events), a new event is buffered; a draft at the local head stays
        if (event.parentId == head && behind) {
            line += event
            return Received.Applied(emptyList(), false)
        }
        if (event.parentId == head) {
            val hadDraft = draft != null
            val dropped = dropPending() ?: return Received.Reload
            line += event
            return Received.Applied(dropped, hadDraft)
        }
        // A branch at an earlier main-line event (I-D56 §8)
        val at = indexOf(event.parentId) ?: return Received.Reload
        val changed = cut(at + 1) ?: return Received.Reload
        line += event
        return changed
    }

    /** The server's rewind (I-D56 §10): the main line's events after [target] are deleted, then [events] (the new line's
     * first event and those added again after it) follow it. */
    fun rewound(target: String, events: List<GameEvent>): Received {
        val at = indexOf(target) ?: return Received.Reload
        val changed = cut(at + 1) ?: return Received.Reload
        for (event in events) {
            if (event.parentId != head) return Received.Reload
            line += event
        }
        return changed
    }

    /** Cuts the line to its first [keep] events, the model going back when it showed more: null when it can't exactly. */
    private fun cut(keep: Int): Received.Changed? {
        if (superseded.isNotEmpty()) return null
        abandon()
        val dropped = dropPending() ?: return null
        val discarded = line.subList(keep, line.size).toList()
        val takenBack = line.subList(keep, shown.coerceAtLeast(keep)).toList()
        for (e in takenBack.asReversed()) {
            model.revert(e)
            if (model.stale) return null
        }
        shown = minOf(shown, keep)
        repeat(line.size - keep) { line.removeLast() }
        return Received.Changed(discarded, takenBack, dropped)
    }

    /** Reverts and drops the pending events and the draft: the events dropped, or null when the model can't go back. */
    private fun dropPending(): List<GameEvent>? {
        abandon()
        val dropped = waiting.toList()
        for (e in dropped.asReversed()) {
            model.revert(e)
            if (model.stale) return null
        }
        waiting.clear()
        return dropped
    }

    /** The server refused the device's event [id]: it and every pending event made on it are taken back (and the open
     * draft, made on them): [Received.Applied] with the events dropped (none when [id] was dropped already), after
     * which [model] is the state at the last event still pending, or at [localHead]; or [Received.Reload]. */
    fun refused(id: String): Received {
        val at = waiting.indexOfFirst { it.id == id }
        if (at < 0) return Received.Applied(emptyList(), false)
        // A refused branch: the line it replaced is gone from this copy
        if (superseded.isNotEmpty()) return Received.Reload
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

    /**
     * The state at main-line event [id] (a copy; [model] is unchanged): from the shown state, taking back the pending
     * events and then reverting or applying along the line. Null when [id] isn't known or can't be reached exactly (more
     * than [GameModel.KEPT_DROPS] turns back: the server has it).
     */
    fun stateAt(id: String): GameModel? {
        check(draft == null) { "A draft is open" }
        val target = indexOf(id) ?: return null
        val copy = model.copy()
        for (e in waiting.reversed()) { copy.revert(e); if (copy.stale) return null }
        var at = shown - 1
        while (at > target) { copy.revert(line[at--]); if (copy.stale) return null }
        while (at < target) copy.apply(line[++at])
        return copy
    }

    /** The state at [head] (a copy): the shown state with the buffered events applied, or the pending ones at the tip. */
    fun headModel(): GameModel {
        check(draft == null) { "A draft is open" }
        val copy = model.copy()
        for (i in shown until line.size) copy.apply(line[i])
        return copy
    }

    /** Forgets the events up to [id] (shown, before the local head): [lineStart] moves to it. */
    fun trim(id: String) {
        val at = indexOf(id) ?: return
        if (at < 0 || at >= shown) return
        repeat(at + 1) { line.removeFirst() }
        shown -= at + 1
        lineStart = id
    }
}
