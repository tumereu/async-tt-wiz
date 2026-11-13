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

    // CMB-028, CMB-043, STK-024, web client step 4d: a combat with a cast in it, Alice's device in the worker beside her device
    // here, both given the same server messages: her attacker staged (the table's own staging, worked out where the device
    // is), her attack declared, Bob's block, her Storm Spark cast at the blocker (its target and payment worked out by the
    // cast panel on the page's copy of the board) and the combat damage: the page gets the same boards as the device here
    @Test fun aCombatWithACastThroughTheWorkerGivesTheSameBoards() {
        val sc = EngineCombatScenario()
        val sim = sc.sim
        val a = sc.f.a
        val me = arcanum.engine.state.PlayerId(a)
        val other = arcanum.engine.state.PlayerId(sc.f.b)
        sc.until { s -> sc.bothHaveCubs(s) && sc.cards(s, me, s.player(me).hand, "Storm Spark").isNotEmpty() &&
            sc.cards(s, me, s.battlefield, "Mountain").any { s.obj(it)?.tapped == false } }
        val snapshot = sim.snapshot(a)
        // Released at its performed point as the worker's device and Android's engine thread release an own action (SYS-025)
        val local = EngineGame(sc.f.game.id, a, sc.f.base, snapshot, ids = counter(), worker = HostWorker)
        sim.games[a] = local
        val remote = EngineGame(sc.f.game.id, a, sc.f.base, snapshot, device = channel)
        sim.tee = { p, d -> if (p == a) when (d) {
            is EngineAppSim.Delivery.Revision -> remote.revision(d.message)
            is EngineAppSim.Delivery.Refused -> remote.refused(d.action, d.reason)
        } }
        pump()
        assertEquals(shape(local.shown), shape(remote.shown), "both open at Alice's attack")
        /** Alice's outbox to the server (the remote copy's drained alike), the server's answers to both, Bob's own steps. */
        fun round(bob: (arcanum.engine.state.GameState) -> Unit = {}) {
            sim.flush(a); remote.take(sim.now) {}; pump()
            val b = sim.game(sc.f.b)
            sim.deliver(sc.f.b); b.playToStop(); b.tick(sim.now)
            if (b.shown?.status == GameDevice.Status.YourMove) bob(b.device.model.engine)
            (b.shown?.status as? GameDevice.Status.Stopped)?.let { b.act(it.assumed) }
            sim.flush(sc.f.b)
            sim.deliver(a)
            local.playToStop()
            fun stepRemote() { remote.step { if (it != null) stepRemote() } }
            stepRemote(); pump()
            local.tick(sim.now); remote.tick(sim.now); pump()
            sim.now += 500
            assertEquals(shape(local.shown), shape(remote.shown), "the same board after a round at ${local.shown?.engine?.turn}")
        }
        /** Alice's own move on both copies: a decision stop answered as assumed, else (with [pass]) her pass while she holds priority. */
        fun aliceMoves(pass: Boolean) {
            (local.shown?.status as? GameDevice.Status.Stopped)?.let { st -> local.act(st.assumed); remote.act(st.assumed) {}; pump(); return }
            if (pass && local.shown?.status == GameDevice.Status.YourMove && local.shown?.engine?.priority?.holder == me) {
                local.pass(); remote.pass {}; pump()
            }
        }

        // The attacker staged with the table's own staging: on the page it goes to the worker and is answered later
        val s0 = local.shown!!.engine
        val attackerObj = sc.creatures(s0, me, ready = true).first()
        val attacker = (attackerObj as arcanum.engine.state.ObjectId.Card).card.value
        val draft = EngineDraft(local.play(), a)
        assertTrue(draft.add(org.asyncttwiz.protocol.PlayAction.Combat(org.asyncttwiz.protocol.CombatAction.Open)))
        val area = assertNotNull(draft.state.board.combat)
        assertTrue(draft.add(org.asyncttwiz.protocol.PlayAction.Combat(org.asyncttwiz.protocol.CombatAction.Place(attacker,
            org.asyncttwiz.protocol.CombatDestination(area.id, org.asyncttwiz.protocol.CombatRole.ATTACKER,
                pairGap = org.asyncttwiz.protocol.IdentityGap(area.pairs.lastOrNull()?.id), attackTarget = null)))))
        var localStaged: Boolean? = null
        var remoteStaged: Boolean? = null
        local.stageOwn(local.play(), draft.actions, draft.requestId) { localStaged = it }
        remote.stageOwn(remote.play(), draft.actions, draft.requestId) { remoteStaged = it }
        assertEquals(true, localStaged)
        assertNull(remoteStaged, "the page doesn't wait for the worker")
        pump()
        assertEquals(true, remoteStaged)
        assertEquals(listOf(attacker), remote.play().board.combat?.pairs?.flatMap { p -> p.attackers.map { it.sourceId } })
        assertEquals(shape(local.shown), shape(remote.shown), "staged")
        round()

        // The attack declared on both, then Bob blocks with his untapped Cub
        val attacks = listOf(arcanum.engine.input.Attack(attackerObj, arcanum.engine.state.TargetRef.Player(other)))
        assertTrue(local.send(listOf(arcanum.engine.input.Input.DeclareAttackers(me, attacks))) !is GameDevice.Acted.Refused)
        var declared: GameDevice.Acted? = null
        remote.send(listOf(arcanum.engine.input.Input.DeclareAttackers(me, attacks))) { declared = it }
        pump()
        // The worker releases an own action at its performed point, as Android's engine thread does (SYS-025)
        assertTrue(declared is GameDevice.Acted.Sent || declared == GameDevice.Acted.Made, "declared in the worker: $declared")
        assertEquals(shape(local.shown), shape(remote.shown), "declared")
        var blocker: arcanum.engine.state.ObjectId? = null
        repeat(10) {
            if (blocker != null && local.shown?.engine?.combat?.blockers.orEmpty().isNotEmpty()) return@repeat
            aliceMoves(pass = blocker == null)
            round { s ->
                val options = arcanum.engine.Engine.options(s, other, org.asyncttwiz.protocol.game.EnginePreferences.of(s))
                if (options.any { it is arcanum.engine.input.Input.DeclareBlockers }) {
                    val cub = sc.creatures(s, other, ready = false).first { s.obj(it)?.tapped == false }
                    blocker = cub
                    sim.game(sc.f.b).send(listOf(arcanum.engine.input.Input.DeclareBlockers(other, listOf(arcanum.engine.input.Block(cub, attackerObj)))))
                }
            }
        }
        val blocked = assertNotNull(blocker, "Bob blocked")
        assertEquals(GameDevice.Status.YourMove, local.shown?.status, "Alice holds priority after the block")

        // Storm Spark at the blocker: the cast panel works its target and payment out on each side's board
        fun castInputs(game: EngineGame): List<arcanum.engine.input.Input> {
            var sent: List<arcanum.engine.input.Input>? = null
            val cast = CastController(a, { game.play() }, { }, engineSend = { inputs -> sent = inputs; null })
            val s = game.shown!!.engine
            val spark = sc.cards(s, me, s.player(me).hand, "Storm Spark").first()
            assertTrue(cast.begin((spark as arcanum.engine.state.ObjectId.Card).card.value, arcanum.engine.input.Input.Cast(me, spark)))
            val pick = assertNotNull(cast.pick, "Storm Spark asks for its target")
            cast.answer(pick.answer(listOf(arcanum.engine.state.TargetRef.Object(blocked))))
            return assertNotNull(sent, "the cast went out with its payment (Autotap)")
        }
        val inputs = castInputs(remote)
        assertEquals(castInputs(local), inputs, "the cast worked out on the page's copy of the board is the same")
        assertTrue(local.send(inputs) !is GameDevice.Acted.Refused)
        var cast: GameDevice.Acted? = null
        remote.send(inputs) { cast = it }
        pump()
        assertTrue(cast != null && cast !is GameDevice.Acted.Refused, "the cast was made in the worker: $cast")
        assertEquals(shape(local.shown), shape(remote.shown), "cast")

        // On to the combat damage: Alice's own passes on both
        repeat(20) {
            if (local.shown?.engine?.turn?.step == arcanum.api.TurnStep.PostcombatMain) return@repeat
            aliceMoves(pass = true)
            round { s -> if (s.priority.holder == other) sim.game(sc.f.b).pass() }
        }
        val after = remote.shown!!.engine
        assertTrue(blocked !in after.battlefield, "the Spark took the blocker")
        assertEquals(after.turn, local.shown!!.engine.turn)
        assertEquals(shape(local.shown), shape(remote.shown), "after the combat damage")
    }

    // Web client step 4d: a board published before its plays were worked out gets them from the device in the worker, which
    // holds the board (asked by its number when the table first reads them, nothing else sent)
    @Test fun aBoardsMissingPlaysComeFromTheWorkersDevice() {
        val (local, remote) = pair()
        local.tick(1_000); remote.tick(1_000)
        local.keep(); remote.keep { }
        pump()
        val values = assertNotNull(remote.play().values)
        val expected = assertNotNull(local.play().values).cardPlays()
        var woken = 0
        if (values.cardPlays == null) {
            assertTrue(values.awaitingPlays(hand = false) { woken++ }, "asked of the worker's device")
            assertTrue(toWorker.any { "\"plays\"" in it })
            pump()
            assertEquals(1, woken)
        }
        assertEquals(expected.byCard, assertNotNull(values.cardPlays).byCard)
        assertEquals(local.play().values?.handPlays()?.byCard, assertNotNull(values.handPlays).byCard)
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
