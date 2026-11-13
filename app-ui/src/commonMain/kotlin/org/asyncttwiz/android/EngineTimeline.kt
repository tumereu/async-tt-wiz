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

    fun clear() { texts.clear(); last = null; olderPart = null }

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
                else texts.getOrPut(e.id) { EngineEventText.of(e, state, game.me, names) }
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
}
