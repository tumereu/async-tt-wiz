package org.asyncttwiz.android

import arcanum.engine.Host
import arcanum.engine.change.Change
import arcanum.engine.decision.Answer
import arcanum.engine.input.Input
import arcanum.engine.state.GameState
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.StackItemKind
import org.asyncttwiz.protocol.HistoryIcon
import org.asyncttwiz.protocol.PlayZone
import org.asyncttwiz.protocol.game.GameEvent
import org.asyncttwiz.protocol.game.MainPress
import org.asyncttwiz.protocol.game.TableChange as GameTableChange
import org.asyncttwiz.appui.bigIntegerOf
import org.asyncttwiz.appui.minus
import org.asyncttwiz.appui.toBigIntegerOrNull

/**
 * HIS-239: one line of the game log: a timeline [event] and its text in [pieces], each name with whose it is (HIS-240);
 * under it the smaller, indented lines of what it caused ([consequences], HIS-246), and under the turn and step label
 * before it the triggers of the events the log shows no line for ([labelConsequences], HIS-248). [opens] is the event
 * timeline mode opens at when the line is tapped (HIS-241): its own, or for a log-only combat damage line (HIS-247) the
 * nearest one on the timeline.
 */
data class GameLogLine(val event: HistoryTimelineEvent, val pieces: List<StackTextPiece>,
    val consequences: List<List<StackTextPiece>> = emptyList(), val labelConsequences: List<List<StackTextPiece>> = emptyList(),
    val opens: String = event.id)

/**
 * The timeline of a game on engine state (HIS-001–HIS-005, HIS-012 on engine events): the main line the device holds,
 * up to the viewer's local head, and their own pending events after it; nothing past the local head (I-D56 §7). Older
 * history loaded from the server comes first, back to the game's first event (owner 2026-11-07). One event
 * per acting point (HIS-092), only those [EngineEventText.onTimeline] keeps; between two of them, where the turn or step
 * changed, a labelled separator (HIS-005). Texts are worked out once per event, with the state shown when they are first
 * asked for; the whole list once per published line.
 */
class EngineTimeline {
    private val texts = HashMap<String, String>()
    private var last: Pair<Triple<List<GameEvent>, List<GameEvent>, Map<String, String>>, List<HistoryTimelineEvent>>? = null
    /** HIS-005: the timeline's part of the older history ([EngineGame.Shown.older]), worked out once per page loaded. */
    private var olderPart: Part? = null

    /** A run of the timeline worked out: its events, and the turn and step it ended at (and of its last event shown). */
    private class Part(val from: List<GameEvent>, val names: Map<String, String>, val events: List<HistoryTimelineEvent>,
        val at: arcanum.engine.state.TurnState, val previous: arcanum.engine.state.TurnState?)

    /** HIS-240: each event's text in pieces with whose each name is, by event id (with the text they were cut from). */
    private val logPieces = HashMap<String, Pair<String, List<StackTextPiece>>>()
    /** HIS-239: the game log worked out last, for the timeline's events it was made from. */
    private var lastLog: Pair<List<HistoryTimelineEvent>, List<GameLogLine>>? = null

    fun clear() { texts.clear(); last = null; olderPart = null; logPieces.clear(); lastLog = null; consequences.clear(); combatLines.clear() }

    /** HIS-246, HIS-248: each event's indented lines (all, or only its triggers), by event id, for the names they were made with. */
    private val consequences = HashMap<Pair<String, EngineConsequences.Part>, Pair<Map<String, String>, List<List<StackTextPiece>>>>()
    /** HIS-247: each event's log-only combat damage line text, or none, by event id. */
    private val combatLines = HashMap<String, String?>()

    /**
     * HIS-239, HIS-240: the game log's lines, oldest first: the timeline's events ([events]) with their texts, each
     * name in it marked with whose it is ([EngineEventText.marks]), and between them a log-only line for each event that
     * dealt combat damage (HIS-247); under each line what it caused (HIS-246), under a turn and step label the triggers of
     * the events in between (HIS-248). Worked out once per event.
     */
    fun log(game: EngineGame, names: Map<String, String>): List<GameLogLine> {
        val events = events(game, names)
        lastLog?.let { (from, lines) -> if (from === events) return lines }
        val published = game.shown ?: return emptyList()
        val all = ArrayList<GameEvent>(published.older.size + published.line.size)
        all += published.older; all += published.line
        val sight = EngineEventText.Sight(published.engine, game.me)
        // HIS-246: what was made now, logged once per log worked out (the newest few in full, so opening a long game's log
        // doesn't push the rest of the app log out)
        val fresh = ArrayList<String>()
        fun after(e: GameEvent, part: EngineConsequences.Part): List<List<StackTextPiece>> {
            val key = e.id to part
            consequences[key]?.takeIf { it.first == names }?.let { return it.second }
            val made = runCatching { EngineConsequences.of(e, published.engine, game.me, names, sight, part) }
                .onFailure { AppLog.warn("game", "Game log: the lines under ${e.id.take(8)} couldn't be made", it) }.getOrDefault(emptyList())
                .map { line -> line.pieces.map { (text, owner) -> StackTextPiece(text, owner) } }
            if (made.isNotEmpty()) fresh += "${e.id.take(8)} ($part): ${made.joinToString(" | ") { it.plain() }}"
            consequences[key] = names to made
            return made
        }
        val lines = assemble(all, events, { e, shown ->
            logPieces[shown.id]?.takeIf { it.first == shown.description }?.second ?: run {
                val marks = EngineEventText.marks(e, published.engine, game.me, names, sight)
                EngineEventText.pieces(shown.description, marks).map { (text, owner) -> StackTextPiece(text, owner) }
                    .also { logPieces[shown.id] = shown.description to it }
            }
        }, ::after) { e -> if (e.id in combatLines) combatLines[e.id] else EngineConsequences.combatDamage(e).also { combatLines[e.id] = it } }
        val onTimeline = events.mapTo(HashSet()) { it.id }
        if (fresh.isNotEmpty()) AppLog.info("game", "Game log for ${game.me.take(8)}: ${lines.size} lines (${lines.count { it.event.id !in onTimeline }} " +
            "combat damage), indented lines made for ${fresh.size} events; the newest: ${fresh.takeLast(5).joinToString(" ; ")}")
        lastLog = events to lines
        return lines
    }

    /** The events shown to the viewer, oldest first, with the turn each was made in. */
    fun shown(game: EngineGame): List<GameEvent> = game.shown?.line.orEmpty()

    fun events(game: EngineGame, names: Map<String, String>): List<HistoryTimelineEvent> {
        val published = game.shown ?: return emptyList()
        val line = published.line
        val older = published.older
        last?.let { (key, events) -> if (key.first === line && key.second === older && key.third == names) return events }
        val state = published.engine
        // The turn and step along the line: before the line's first step change, where that change started (HIS-005: the
        // older history loaded comes first)
        fun firstStep(events: List<GameEvent>) =
            events.firstNotNullOfOrNull { e -> e.engine?.changes?.firstOrNull { it is Change.Step } as Change.Step? }?.before
        val start = firstStep(older) ?: firstStep(line) ?: state.turn
        val part = olderPart?.takeIf { it.from === older && it.names == names }
            ?: walk(game, older, start, null, published, names).also { olderPart = it }
        val events = part.events + walk(game, line, part.at, part.previous, published, names).events
        last = Triple(line, older, names) to events
        return events
    }

    /** The timeline's events of [line] from the turn and step [start] (and [previous], that of the event shown before it). */
    private fun walk(game: EngineGame, line: List<GameEvent>, start: arcanum.engine.state.TurnState,
        previous: arcanum.engine.state.TurnState?, published: EngineGame.Shown, names: Map<String, String>): Part {
        val state = published.engine
        // What the viewer sees in the state the new texts are made from, worked out once for all of them
        val sight = EngineEventText.Sight(state, game.me)
        val players = state.players.map { it.id.value }
        var at = start
        var shownBefore = previous
        val events = ArrayList<HistoryTimelineEvent>()
        for (e in line) {
            val before = at
            e.engine?.changes?.lastOrNull { it is Change.Step }?.let { at = (it as Change.Step).after }
            if (!EngineEventText.onTimeline(e, game.me)) continue
            // HIS-005: the turn and step this event was made in, when the game moved on since the event before it
            val separator = shownBefore?.takeIf { it.number != before.number || it.step != before.step }?.let {
                TimelineSeparator(EngineEventText.separatorLabel(before), it.number != before.number, before.active.value) }
            shownBefore = before
            val text = if (EngineEventText.ownPress(e, game.me) != null) EngineEventText.timelineText(e, state, game.me, names)
                else texts.getOrPut(e.id) { EngineEventText.of(e, state, game.me, names, sight) }
            events += HistoryTimelineEvent(e.id, EngineEventText.icon(e), text, EngineEventText.colourOf(e, players), separator)
        }
        return Part(line, names, events, at, shownBefore)
    }

    /** The text of [event] (cached). */
    fun text(game: EngineGame, event: GameEvent, names: Map<String, String>): String =
        texts.getOrPut(event.id) { EngineEventText.of(event, requireNotNull(game.shown).engine, game.me, names) }

    /** The timeline's text of [event] (HIS-076 in timeline mode): as [events] names it. */
    fun reviewText(game: EngineGame, event: GameEvent, names: Map<String, String>): String {
        return if (EngineEventText.ownPress(event, game.me) != null) EngineEventText.timelineText(event, requireNotNull(game.shown).engine, game.me, names)
            else text(game, event, names)
    }
    companion object {
        /**
         * HIS-239, HIS-246–HIS-248: the game log's lines from the events [all] (oldest first) and the timeline's events among
         * them ([timeline]): a line per timeline event with its text ([pieces]) and what it caused ([after]); a log-only line
         * for an event the timeline doesn't show that dealt combat damage ([combat]'s text), opening timeline mode at the next
         * timeline event (the one before while none follows); the triggers of the other events under the next line's turn and
         * step label, or under the line before when the next has none.
         */
        fun assemble(all: List<GameEvent>, timeline: List<HistoryTimelineEvent>, pieces: (GameEvent, HistoryTimelineEvent) -> List<StackTextPiece>,
            after: (GameEvent, EngineConsequences.Part) -> List<List<StackTextPiece>>, combat: (GameEvent) -> String?): List<GameLogLine> {
            val byId = HashMap<String, HistoryTimelineEvent>(timeline.size * 2)
            for (t in timeline) byId[t.id] = t
            val lines = ArrayList<GameLogLine>(timeline.size)
            // HIS-248: the triggers of events between two lines, for the next line's label (or the line before when it has none)
            var waiting = ArrayList<List<StackTextPiece>>()
            // HIS-247: combat damage lines waiting for the next timeline event, the one they open timeline mode at
            val opensNext = ArrayList<Int>()
            fun toLast() {
                if (waiting.isEmpty() || lines.isEmpty()) return
                lines[lines.size - 1] = lines.last().let { it.copy(consequences = it.consequences + waiting) }
                waiting = ArrayList()
            }
            for (e in all) {
                val shown = byId[e.id]
                if (shown != null) {
                    var label = emptyList<List<StackTextPiece>>()
                    if (shown.separator != null || lines.isEmpty()) { label = waiting; waiting = ArrayList() } else toLast()
                    for (i in opensNext) lines[i] = lines[i].copy(opens = shown.id)
                    opensNext.clear()
                    // HIS-247: an event of the timeline that went on to deal combat damage ("Bob: no blocks") is followed by
                    // the combat damage line, with what came from the damage on under it
                    val damage = combat(e)
                    if (damage == null) { lines += GameLogLine(shown, pieces(e, shown), after(e, EngineConsequences.Part.All), label); continue }
                    lines += GameLogLine(shown, pieces(e, shown), after(e, EngineConsequences.Part.BeforeCombatDamage), label)
                    lines += GameLogLine(HistoryTimelineEvent("${e.id}/combat", HistoryIcon.SWORDS, damage, null), listOf(StackTextPiece(damage)),
                        after(e, EngineConsequences.Part.CombatDamage), opens = e.id)
                    continue
                }
                val text = combat(e)
                if (text != null) {
                    toLast()
                    val opens = lines.lastOrNull()?.opens ?: e.id
                    opensNext += lines.size
                    lines += GameLogLine(HistoryTimelineEvent(e.id, HistoryIcon.SWORDS, text, null), listOf(StackTextPiece(text)), after(e, EngineConsequences.Part.All), opens = opens)
                    continue
                }
                waiting.addAll(after(e, EngineConsequences.Part.Triggers))
            }
            return lines
        }
    }
}
