package org.asyncttwiz.protocol.game

import org.asyncttwiz.protocol.EngineHistoryPage

/**
 * HIS-110, HIS-099 (owner 2026-11-07: the timeline and the Undo dialog reach the game's first event, with no limit on how
 * far back): what a device holds of a game's main line beyond its [GameSession]'s, loaded from the server in pages on
 * demand ([EngineHistoryPage]).
 *
 * - [events]: the main line before the session's line, oldest first, ending at the session's [GameSession.lineStart];
 *   grown one page at a time towards the game's first event ([through] names the next page, [add] takes it in; [complete]
 *   once the first event is in), and by the events the session trims while it is held ([trimmed]).
 * - States the device can't reach by going back from what it shows (more than [GameModel.KEPT_DROPS] turns back, or before
 *   the session's line): from a page's checkpoint with at most a page of events applied ([stateAt]), never a replay from
 *   the game's start (owner rule). The last [keptPages] pages are kept (their states are large); an event no kept page
 *   holds asks for the page ending at it again.
 *
 * Events and their ids never change, so a kept page stays right whatever happens to the main line later; only [events]
 * follows the main line (a rewind before it, or a reload, starts it again: [carriedTo]).
 */
class OlderHistory(private val codec: EngineJson, private val keptPages: Int = 8) {
    /** The main line's events before the session's line, oldest first; the last is the session's line start. */
    var events: List<GameEvent> = emptyList()
        private set

    /** Whether [events] begin with the game's first event: there is nothing older. */
    var complete: Boolean = false
        private set

    /** Events applied to work out states so far (tests check the work per state stays within a page). */
    var applied: Int = 0
        private set

    private class Page(val baseId: String, val base: String, val events: List<GameEvent>) {
        val index: Map<String, Int> = events.withIndex().associate { it.value.id to it.index }
    }

    /** The kept pages, least recently used first. */
    private val pages = LinkedHashMap<String, Page>()

    /** The last state worked out: from it the next one in the same page goes on forwards (timeline playback). */
    private var last: Triple<Page, Int, GameModel>? = null

    /** The event the next older page ends at (the parent of the first of [events], else [lineStart]), or null when
     * there is nothing older. */
    fun through(lineStart: String, rootId: String?): String? = when {
        complete -> null
        events.isEmpty() -> lineStart.takeIf { it != rootId }
        else -> events.first().parentId.takeIf { it != rootId }
    }

    /** Whether [id] is one of [events]. */
    fun holds(id: String): Boolean = events.any { it.id == id }

    /**
     * Takes in [page] (its events decoded with this history's codec): kept for states, and, when it is the next older page
     * of the line ending at [lineStart] ([through]), put before [events]. False when it doesn't fit together (its events
     * aren't one line from its base to [EngineHistoryPage.through]).
     */
    fun add(page: EngineHistoryPage, lineStart: String, rootId: String?): Boolean {
        val decoded = page.events.map(codec::decodeEvent)
        if (decoded.isEmpty() || decoded.first().parentId != page.baseId || decoded.last().id != page.through) return false
        if (decoded.zipWithNext().any { (a, b) -> b.parentId != a.id }) return false
        keep(Page(page.baseId, page.base, decoded))
        if (page.through == through(lineStart, rootId)) {
            events = decoded + events
            if (page.fromStart) complete = true
        }
        return true
    }

    private fun keep(page: Page) {
        pages.remove(page.events.last().id)
        pages[page.events.last().id] = page
        while (pages.size > keptPages) pages.remove(pages.keys.first())
    }

    /**
     * The state at [id] from a kept page holding it: the page's checkpoint and its events up to [id] applied (or the last
     * state worked out in that page, moved on); null when no kept page holds [id] (ask for the page ending at it).
     */
    fun stateAt(id: String): GameModel? {
        val page = pages.values.lastOrNull { id in it.index } ?: return null
        keep(page)
        val at = page.index.getValue(id)
        val kept = last?.takeIf { it.first === page && it.second <= at }
        val model = kept?.third?.copy() ?: codec.decodeModel(page.base)
        for (i in (kept?.second?.plus(1) ?: 0)..at) { model.apply(page.events[i]); applied++ }
        if (model.stale) return null
        last = Triple(page, at, model.copy())
        return model
    }

    /**
     * The session trimmed [moved] (its oldest events, up to its new line start) from the line that started at
     * [oldLineStart]: they join [events] when those end there, so what was loaded stays reachable; otherwise nothing was
     * loaded and they go.
     */
    fun trimmed(oldLineStart: String, moved: List<GameEvent>) {
        if (moved.isEmpty() || events.isEmpty()) return
        events = if (events.last().id == oldLineStart) events + moved else emptyList()
        if (events.isEmpty()) complete = false
    }

    /**
     * This history for a session loaded anew (a reload) whose line starts at [lineStart], from what the old device held:
     * [line] is the old main line from its oldest held event (these [events], then the old session's). Kept pages stay;
     * [events] keep the old main line up to [lineStart] when it is on it (its events before it are its ancestors, still
     * the main line), else start again.
     */
    fun carriedTo(lineStart: String, line: List<GameEvent>): OlderHistory {
        val next = OlderHistory(codec, keptPages)
        next.pages.putAll(pages)
        val at = line.indexOfFirst { it.id == lineStart }
        if (events.isNotEmpty() && at >= 0) {
            next.events = line.subList(0, at + 1).toList()
            next.complete = complete
        }
        return next
    }
}
