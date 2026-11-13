package org.asyncttwiz.protocol.game

import org.asyncttwiz.protocol.EngineHistoryPage

/**
 * HIS-111, HIS-099 (owner 2026-11-07: the timeline and the Undo dialog reach the game's first event, with no limit on how
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

    /**
     * The first page asked for while nothing is loaded yet ([asking]) ends at the line start of that moment: [waiting]. The
     * session may trim its line before the page arrives (the other player catching up moves the earliest local head on):
     * the events it trims meanwhile wait in [gap] (from [waiting] on, in order), so the page still joins the line ([add])
     * instead of being kept for states only, and [events] still end at the session's line start.
     */
    private var waiting: String? = null
    private var gap: List<GameEvent> = emptyList()

    /** The page to ask for next, as [through], remembering it when nothing is loaded yet (see [waiting]): while its answer
     * is awaited past a trim, the same page is asked for again. */
    fun asking(lineStart: String, rootId: String?): String? {
        if (complete || events.isNotEmpty()) return through(lineStart, rootId)
        val asked = waiting
        if (asked != null && (gap.isNotEmpty() || asked == lineStart)) return asked
        gap = emptyList()
        waiting = lineStart.takeIf { it != rootId }
        return waiting
    }

    /** The trimmed events the last page joined the line through ([waiting]), for the device's record; 0 when none. */
    var joined: Int = 0
        private set

    /** The trimmed events waiting for the first page ([waiting]), for the device's record. */
    val waitingTrimmed: Int get() = gap.size

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
            waiting = null; gap = emptyList(); joined = 0
        } else if (events.isEmpty() && page.through == waiting && gap.lastOrNull()?.id == lineStart) {
            // Asked for before the session trimmed its line: it joins the line through the events trimmed meanwhile
            events = decoded + gap
            if (page.fromStart) complete = true
            joined = gap.size
            waiting = null; gap = emptyList()
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
        if (moved.isEmpty()) return
        if (events.isEmpty()) {
            // The first page is on its way ([waiting]): what is trimmed meanwhile waits for it, in order (at most
            // [GAP_MOST], so a page that never comes doesn't keep a long game's line)
            val asked = waiting ?: return
            val follows = if (gap.isEmpty()) oldLineStart == asked else gap.last().id == oldLineStart
            gap = if (follows && gap.size + moved.size <= GAP_MOST) gap + moved else emptyList()
            if (gap.isEmpty()) waiting = null
            return
        }
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

    private companion object {
        /** The trimmed events that wait for the first page at most ([trimmed]). */
        const val GAP_MOST = 2000
    }
}
