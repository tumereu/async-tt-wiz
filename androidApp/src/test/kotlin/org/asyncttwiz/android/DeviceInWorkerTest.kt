package org.asyncttwiz.android

import org.asyncttwiz.automatum.boundary.DeviceMessage
import org.asyncttwiz.protocol.GameAction
import org.asyncttwiz.protocol.game.GameDevice
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Web client step 4c part 3 (docs/specs/web-client.md §1 item 3): a game's device in the engine's Web Worker ([DeviceHost])
 * behind its [EngineGame] on the page ([DeviceChannel], [RemoteDevice]), every message crossing as a JSON string and
 * delivered later, as the browser delivers it. The page gets the same boards, notes and results as a device on the page
 * (Android's in-process one, here inline) given the same actions and server events, in the same order: an own action's
 * early board before its result (SYS-025), a tap on a board the device has moved on from refused as stale (SYS-011,
 * SYS-012), the worker's log lines in the page's app log.
 */
class DeviceInWorkerTest {
    private val f = EngineGameFixture()

    /** Messages on their way, in order, both ways (the browser's postMessage queues). */
    private val toWorker = ArrayDeque<String>()
    private val toPage = ArrayDeque<String>()
    /** Both devices give their own events the same ids, in order, so their boards compare. */
    private fun counter(): () -> String { var n = 0; return { "00000000-0000-4000-8000-${(++n).toString().padStart(12, '0')}" } }
    private val host = DeviceHost({ toPage.addLast(it) }, ids = { counter() }).also { it.forwardLogs() }
    private val channel = DeviceChannel({ toWorker.addLast(it) })

    @After fun tearDown() { AppLog.forward = null }

    /** Delivers every message on its way, both ways, until none is left. */
    private fun pump() {
        while (toWorker.isNotEmpty() || toPage.isNotEmpty()) {
            while (toWorker.isNotEmpty()) host.handle(toWorker.removeFirst())
            while (toPage.isNotEmpty()) channel.received(toPage.removeFirst())
        }
    }

    private fun pair(otherKept: Boolean = true): Pair<EngineGame, EngineGame> {
        val local = EngineGame(f.game.id, f.a, f.base, f.snapshot(otherKept = otherKept), ids = counter())
        val remote = EngineGame(f.game.id, f.a, f.base, f.snapshot(otherKept = otherKept), device = channel)
        pump()
        return local to remote
    }

    /** What the table reads of a published board, compared across the two. */
    private fun shape(s: EngineGame.Shown?) = s?.let {
        listOf(it.status::class.simpleName, it.locked, it.behind, it.tip, it.localHead, it.drafting, it.line.map { e -> e.id },
            it.held.size, it.board.board, it.board.nodeId, it.board.definitions, it.engine.turn, it.request, it.undoTarget,
            it.transition?.forward?.map { e -> e.id }, it.transition?.steps?.size, it.ownStepHeld)
    }

    // Web client step 4c part 3: the board the worker's device publishes on opening is the page's, as the device here makes it
    @Test fun openingPublishesTheSameBoard() {
        val (local, remote) = pair()
        assertTrue(remote.inWorker)
        assertNotNull(remote.shown, "the worker's first board arrived")
        assertEquals(shape(local.shown), shape(remote.shown))
        assertTrue(toWorker.isEmpty() && toPage.isEmpty())
    }

    // SYS-025 across the boundary: the keep's result comes after its board; both devices send the same and agree after
    // the server's echoes
    @Test fun anOwnActionsResultComesAfterItsBoardAndTheDevicesAgree() {
        val (local, remote) = pair()
        local.tick(1_000); remote.tick(1_000)
        assertIs<GameDevice.Acted.Sent>(local.keep())
        var result: GameDevice.Acted? = null
        var versionAtResult = -1L
        remote.keep { result = it; versionAtResult = remote.version }
        assertNull(result, "the page doesn't wait for the worker")
        pump()
        assertNotNull(result)
        assertTrue(versionAtResult > 1, "the keep's board arrived before its result")
        local.tick(2_000); remote.tick(2_000)
        pump()
        val sentLocal = local.take(2_000)
        var sentRemote: List<GameAction>? = null
        remote.takeNow(2_000) { sentRemote = it }
        pump()
        assertEquals(sentLocal.map { it::class }, assertNotNull(sentRemote).map { it::class })
        assertEquals(shape(local.shown), shape(remote.shown))
        // The server's echoes confirm the pending events on both
        val pending = local.device.session.pending
        pending.forEachIndexed { n, event ->
            val revision = f.revision(event, n + 2, mapOf(f.a to event.id, f.b to f.keep.id))
            local.revision(revision); remote.revision(revision)
        }
        pump()
        assertEquals(shape(local.shown), shape(remote.shown))
    }

    // SYS-011, SYS-012 across the boundary: a press made on a board the worker's device has moved on from (the page hadn't
    // shown its newer board yet) is checked against the board the page showed, and refused as stale with its note
    @Test fun aTapOnABoardTheDeviceMovedOnFromIsStale() {
        val (_, remote) = pair()
        remote.tick(1_000)
        pump()
        val shownBefore = remote.version
        // The keep goes first (its board not delivered yet), then a pass the player tapped on the board before it
        var kept: GameDevice.Acted? = null
        remote.keep { kept = it }
        var passed: GameDevice.Acted? = null
        remote.pass { passed = it }
        assertEquals(shownBefore, remote.version, "nothing delivered yet")
        pump()
        assertIs<GameDevice.Acted.Sent>(kept)
        val refused = assertIs<GameDevice.Acted.Refused>(passed)
        assertEquals("stale", refused.reason)
        assertTrue(remote.notes.any { it is GameDevice.Note.Refused }, "the stale tap's note reached the page")
    }

    // Web client step 4c part 3: the worker's own log lines reach the page's app log (problem reports carry them)
    @Test fun theWorkersLogReachesThePagesAppLog() {
        val (_, remote) = pair()
        AppLog.forward = null
        val before = AppLog.text().length
        host.forwardLogs()
        remote.logBoard()
        pump()
        AppLog.forward = null
        assertTrue("[worker]" in AppLog.text().drop(before), "the worker's board log reached the page's log")
    }

    // Web client step 4c part 3: a request for a game the worker doesn't hold fails, and its callback still runs; a game
    // closed on the page drops what the worker still sends
    @Test fun aClosedGamesAnswersAreDroppedAndItsCallsEnd() {
        val (_, remote) = pair()
        var result: GameDevice.Acted? = null
        remote.pass { result = it }
        remote.close()
        assertIs<GameDevice.Acted.Refused>(result, "a waiting call ends with a refusal when its game closes")
        pump()
        assertTrue(toPage.isEmpty())
        assertTrue(DeviceMessage.isDevice(DeviceMessage.request("g", org.asyncttwiz.automatum.boundary.DeviceRequest.Pause(1))))
    }

    // Web client step 4c part 3: timeline mode's board and the undo request's reads come back across the boundary, with
    // the same board the device here makes
    @Test fun readsComeBackAsTheSameBoards() {
        val (local, remote) = pair()
        local.tick(1_000); remote.tick(1_000)
        local.keep(); remote.keep { }
        pump()
        val id = local.shown!!.line.last().id
        val expected = local.reviewPlay(id)
        var got: TableBoard? = null
        var answered = false
        remote.reviewPlay(id) { got = it; answered = true }
        pump()
        assertTrue(answered)
        assertEquals(expected?.board, got?.board)
        assertEquals(expected?.engine?.state?.turn, got?.engine?.state?.turn)
        var repro: org.asyncttwiz.protocol.game.GameRepro? = null
        remote.repro { repro = it }
        pump()
        assertEquals(local.repro()?.events?.size, repro?.events?.size)
    }
}
