package org.asyncttwiz.protocol.game

import arcanum.engine.input.Input
import arcanum.engine.state.PlayerId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * HIS-111, HIS-099 (owner 2026-11-07: the timeline and the Undo dialog reach the game's first event, with no limit on how
 * many events back): a device holds only a recent part of a long game's main line; older history comes from the server in
 * pages on demand, each a checkpoint and at most a checkpoint interval of events, so the state at any point costs bounded
 * work (never a replay from the game's start, owner rule).
 */
class OlderHistoryTest {
    private val every = 16

    /** Both players' decks of Forests: each plays one a turn and ends their turn, live, until the main line has at least
     * [events] events (well past what a device keeps, [GameDevice.KEPT_EVENTS]). */
    private fun longGame(events: Int): DeviceSim {
        val sim = DeviceSim(EngineGames(11, List(40) { "Forest" } to List(40) { "Forest" }), every)
        sim.start(sim.alice, ResponseMode.AutoSkip)
        for (p in listOf(sim.alice, sim.bob)) { sim.device(p).act(sim.keep(p)); sim.settle() }
        var turns = 0
        while (sim.server.store.size < events && turns++ < 80) {
            val p = sim.server.state().engine.turn.active
            sim.catchUp(p)
            val d = sim.device(p)
            d.model.engine.player(p).hand.firstOrNull()?.let { d.act(Input.PlayLand(p, it)) }
            sim.settle()
            d.endTurn(sim.now)
            sim.settle()
        }
        sim.catchUp(sim.alice); sim.catchUp(sim.bob)
        assertTrue(sim.server.store.size >= events, "a long game: ${sim.server.store.size} events")
        return sim
    }

    private fun DeviceSim.catchUp(p: PlayerId) {
        val d = device(p)
        d.play()
        while (d.session.behind) {
            d.playToStop()
            (d.status as? GameDevice.Status.Stopped)?.let { d.act(it.assumed) }
        }
        settle()
    }

    /** [d]'s older history loaded page by page to the game's first event, as scrolling the timeline back does; the
     * pages it took. */
    private fun DeviceSim.loadAll(d: GameDevice): Int {
        var pages = 0
        while (true) {
            val through = d.olderThrough() ?: return pages
            val page = assertNotNull(server.history.page(server.head, through), "the page to $through")
            assertTrue(page.events.size in 1..every, "a page holds at most a checkpoint interval: ${page.events.size}")
            assertTrue(d.addPage(page))
            pages++
        }
    }

    // HIS-111 (owner 2026-11-07): the timeline reaches the game's first event: older history comes in pages until the
    // device holds the whole main line, and the state at every event is the server's, each worked out from the shown
    // state or a page's checkpoint with at most a page of events applied (a page no longer kept is asked for again)
    @Test
    fun olderHistoryReachesTheGamesFirstEventWithBoundedWorkPerState() {
        val sim = longGame(events = 160)
        val d = sim.device(sim.alice)
        val line = sim.server.line()
        assertTrue(d.session.events.size < line.size - 1, "the device holds a recent part: ${d.session.events.size} of ${line.size}")
        assertFalse(d.olderComplete)
        val pages = sim.loadAll(d)
        assertTrue(pages >= (line.size - d.session.events.size) / every, "$pages pages")
        assertTrue(d.olderComplete)
        assertEquals(line.map { it.id }, (d.olderEvents + d.session.events).map { it.id }, "the whole main line, oldest first")
        assertEquals("start", d.olderEvents.first().id, "the game's first event")
        val older = d.older!!
        for (e in line) {
            var reached = d.reach(e.id)
            (reached as? GameDevice.Reach.Needs)?.let { needs ->
                assertTrue(d.addPage(assertNotNull(sim.server.history.page(sim.server.head, needs.through))))
                reached = d.reach(e.id)
            }
            val before = older.applied
            reached = d.reach(e.id)
            val state = assertIs<GameDevice.Reach.State>(reached, "the state at ${e.id}")
            assertTrue(older.applied - before <= every, "at most a page applied for ${e.id}: ${older.applied - before}")
            assertTrue(state.model.sameAs(sim.server.stateAt(e.id)), "the state at ${e.id}")
        }
        // A discarded or unknown event, and the root, have no page
        assertNull(sim.server.history.page(sim.server.head, "nope"))
        assertNull(sim.server.history.page(sim.server.head, "root"))
    }

    // HIS-111: what was loaded stays when the game goes on: the events the device trims from its line join it
    @Test
    fun loadedHistoryStaysWhenTheDeviceTrimsItsLine() {
        val sim = longGame(events = 120)
        val d = sim.device(sim.alice)
        sim.loadAll(d)
        val start = d.session.lineStart
        while (d.session.lineStart == start) {
            val p = sim.server.state().engine.turn.active
            sim.catchUp(p)
            sim.device(p).endTurn(sim.now)
            sim.settle()
            sim.catchUp(sim.alice); sim.catchUp(sim.bob)
        }
        assertTrue(d.olderComplete)
        assertEquals(sim.server.line().map { it.id }, (d.olderEvents + d.session.events).map { it.id })
    }

    // HIS-111, HIS-099, HIS-100 (owner 2026-11-07: no limit on how far back): Alice's Undo to an event before her device's line is
    // worked out from older history (a request: Bob's events come after it, not "unknown"); Bob's preview loads what it
    // needs and shows the board there as seen; his Accept takes the game back to it, and both devices load it again there
    @Test
    fun anUndoRequestReachesAnEarlyPointBeyondTheDevicesLine() {
        val sim = longGame(events = 160)
        val alices = sim.device(sim.alice)
        val bobs = sim.device(sim.bob)
        sim.loadAll(alices)
        val line = sim.server.line()
        val point = line[1]
        val target = point.parentId
        assertFalse(alices.session.knows(target), "before her device's line")
        val undo = assertIs<GameDevice.Undo.Request>(alices.undo(target))
        assertNotEquals("unknown", undo.reason)
        alices.requestUndo(target, "much earlier")
        sim.flush(sim.alice)
        sim.deliver(sim.bob)
        assertNull(bobs.requestPreview(), "nothing until its history is in")
        while (true) {
            val through = bobs.requestNeeds() ?: break
            assertTrue(bobs.addPage(assertNotNull(sim.server.history.page(sim.server.head, through))))
        }
        val preview = assertNotNull(bobs.requestPreview())
        assertFalse(preview.unseen)
        assertTrue(preview.board.sameAs(sim.server.stateAt(target)))
        bobs.answerRequest(true)
        sim.flush(sim.bob)
        assertEquals(target, sim.server.head)
        for (p in listOf(sim.alice, sim.bob)) {
            sim.deliver(p)
            assertTrue(sim.device(p).needsReload, "${p.value}: the point is before the device's line: it loads the game again")
            assertEquals(target, sim.load(p).session.head)
        }
    }
}
