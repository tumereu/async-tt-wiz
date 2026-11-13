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

    /** A cast panel on [game]'s board as the table makes it: its flow session beside the game's device (web client step 4e: in
     * the worker for a game whose device is there, its answers delivered by [pump]; here at once otherwise); [sent]: the
     * complete cast's own inputs. */
    private fun panel(game: EngineGame, viewer: String, sent: (List<arcanum.engine.input.Input>) -> Unit) =
        CastController(viewer, { game.play() }, { }, engineSend = { inputs -> sent(inputs); null },
            port = TableCastPort(LocalCastPort(viewer) { work, done -> done(work()) }) { game })

    /** What the cast panel draws, compared across a session here and one in the worker, with the text ([codecOf]'s codec) of
     * the flow's state (the flow's own here; the page's copy of its board with the flow's changes in the worker's case). */
    private fun panelShape(c: CastController, codecOf: EngineGame) = listOf(c.flow?.value?.copy(changes = null), c.mana, c.paid,
        c.payment, c.pick?.options, c.ways, c.hand, c.choosing, c.picked, c.notice, c.busy, c.listedAnswers(),
        c.flow?.pending?.let { c.most(it) }, c.paySources(), c.payableSources(), c.targeting, c.open,
        c.flow?.state?.let { codecOf.codec.encodeModel(org.asyncttwiz.protocol.game.GameModel(it, org.asyncttwiz.protocol.game.TableState())) })

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

    // SYS-072, TRN-027, web e2e (WebMixedTest, WebSmokeTest, 2026-11-13): the table gives the page its token pictures on its
    // first composition, which with the device in the worker published from the page, where there is no device, and the
    // table failed; then the starting player keeps, the server echoes it, the other player's keep arrives through the
    // channel and her page reaches her first Main phase with the same boards as the device here
    @Test fun theOtherPlayersKeepAfterTheOwnReachesThePageOnceTheTableSetItsTokenPictures() {
        val (local, remote) = pair(otherKept = false)
        val art = org.asyncttwiz.automatum.TokenArt { null }
        local.tokenArt = art
        remote.tokenArt = art
        pump()
        assertEquals(shape(local.shown), shape(remote.shown), "the token pictures set on both")
        local.tick(1_000); remote.tick(1_000); pump()
        val kept = assertIs<GameDevice.Acted.Sent>(local.keep())
        var remoteKept: GameDevice.Acted? = null
        remote.keep { remoteKept = it }
        pump()
        assertEquals(kept.event.id, assertIs<GameDevice.Acted.Sent>(remoteKept).event.id)
        local.tick(2_000); remote.tick(2_000); pump()
        local.take(2_000)
        remote.takeNow(2_000) {}
        pump()
        assertIs<GameDevice.Status.Waiting>(remote.shown?.status, "she waits for Bob's keep")
        assertEquals(shape(local.shown), shape(remote.shown), "kept")
        // The server's echo of her keep, then Bob's keep on it, both through the channel
        val echo = f.revision(kept.event, 2, mapOf(f.a to kept.event.id, f.b to f.start.id))
        local.revision(echo); remote.revision(echo); pump()
        val model = f.codec.decodeModel(f.snapshot().state).also { it.apply(kept.event) }
        val bobKeep = org.asyncttwiz.protocol.game.EventBuilder(model, "bob-keeps", kept.event.id, f.b).run {
            perform(arcanum.engine.input.Input.SetupAnswer(arcanum.engine.state.PlayerId(f.b), arcanum.engine.decision.Answer.Option("Keep")))
            answerRandoms()
            build()
        }
        val bobs = f.revision(bobKeep, 3, mapOf(f.a to kept.event.id, f.b to bobKeep.id))
        local.revision(bobs); remote.revision(bobs); pump()
        var now = 3_000L
        repeat(10) {
            if (local.shown?.engine?.turn?.step == arcanum.api.TurnStep.PrecombatMain && local.shown?.status == GameDevice.Status.YourMove) return@repeat
            local.playToStop()
            fun stepRemote() { remote.step { if (it != null) stepRemote() } }
            stepRemote(); pump()
            local.tick(now); remote.tick(now); pump()
            now += 500
        }
        val shown = assertNotNull(remote.shown)
        assertEquals(arcanum.api.TurnStep.PrecombatMain, shown.engine.turn.step, "her page in her Main 1")
        assertEquals(f.a, shown.engine.turn.active.value)
        assertEquals(GameDevice.Status.YourMove, shown.status)
        assertEquals(shape(local.shown), shape(remote.shown), "her Main 1")
    }

    // SYS-072 across the boundary: a token picture the worker's device asks for before the table gave the page its card
    // records' lookup waits for it (the worker asks each once), then goes back with the picture found
    @Test fun aTokenPictureAskedBeforeTheTableSetItsLookupIsAnsweredOnceItDoes() {
        val (_, remote) = pair()
        val query = org.asyncttwiz.automatum.TokenArtQuery(org.asyncttwiz.protocol.DefinitionFace("t", "Ember Token", typeLine = "Token Creature — Elemental"))
        channel.received(DeviceMessage.answer(f.game.id, org.asyncttwiz.automatum.boundary.DeviceAnswer.TokenArt(query)))
        assertTrue(toWorker.isEmpty(), "not answered with no lookup yet")
        remote.tokenArt = org.asyncttwiz.automatum.TokenArt { if (it == query) "scryfall-ember" else null }
        val sent = toWorker.map { DeviceMessage.decode(it).request }
        val found = assertIs<org.asyncttwiz.automatum.boundary.DeviceRequest.TokenArtFound>(sent.single())
        assertEquals(query, found.query)
        assertEquals("scryfall-ember", found.scryfallId)
        pump()
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

        // Storm Spark at the blocker: the cast panel's flow session works its target and payment out where each side's device
        // is (web client step 4e: in the worker for the remote copy, its target bar drawn from the values it answers)
        val shapes = mutableMapOf<EngineGame, List<Any?>>()
        fun castInputs(game: EngineGame): List<arcanum.engine.input.Input> {
            var sent: List<arcanum.engine.input.Input>? = null
            val cast = panel(game, a) { sent = it }
            val s = game.shown!!.engine
            val spark = sc.cards(s, me, s.player(me).hand, "Storm Spark").first()
            assertTrue(cast.begin((spark as arcanum.engine.state.ObjectId.Card).card.value, arcanum.engine.input.Input.Cast(me, spark)))
            pump()
            shapes[game] = panelShape(cast, local)
            val pick = assertNotNull(cast.pick, "Storm Spark asks for its target")
            cast.answer(pick.answer(listOf(arcanum.engine.state.TargetRef.Object(blocked))))
            pump()
            return assertNotNull(sent, "the cast went out with its payment (Autotap)")
        }
        val inputs = castInputs(remote)
        assertEquals(castInputs(local), inputs, "the cast worked out in the worker's session is the same")
        assertEquals(shapes[local], shapes[remote], "the same target bar from the worker's values")
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

    /** Alice's game in her Main 1 with a Mountain on the battlefield and [cards] for her deck, opened twice from the same
     * snapshot: her device here and her device in the worker. */
    private fun mainWith(vararg cards: String): Pair<EngineGame, EngineGame> {
        val kit = EngineTableKit(EngineCombatScenario(EngineGameFixture(aliceCards = cards.toList())))
        kit.opening()
        kit.playLand()
        val f = kit.scenario.f
        val snapshot = kit.scenario.sim.snapshot(f.a)
        val local = EngineGame(f.game.id, f.a, f.base, snapshot, ids = counter(), worker = HostWorker)
        val remote = EngineGame(f.game.id, f.a, f.base, snapshot, device = channel)
        pump()
        assertEquals(shape(local.shown), shape(remote.shown), "both open in Alice's Main 1")
        return local to remote
    }

    /** [steps] of a cast panel on [game] (its session in the worker when the device is there): each panel it showed and the
     * cast's own inputs sent. */
    private fun panelSteps(game: EngineGame, codecOf: EngineGame, steps: List<(CastController) -> Boolean>): Pair<List<List<Any?>>, List<arcanum.engine.input.Input>?> {
        var sent: List<arcanum.engine.input.Input>? = null
        val cast = panel(game, game.me) { sent = it }
        val shown = mutableListOf<List<Any?>>()
        for (step in steps) {
            assertTrue(step(cast), "a step taken: ${cast.notice}")
            pump()
            shown += panelShape(cast, codecOf)
        }
        return shown to sent
    }

    private fun handCard(game: EngineGame, name: String): String {
        val s = game.shown!!.engine
        return s.player(arcanum.engine.state.PlayerId(game.me)).hand.first { s.obj(it)?.definition?.faces?.first()?.name == name }
            .let { (it as arcanum.engine.state.ObjectId.Card).card.value }
    }

    // Web client step 4e, STK-023: Ember Burst ({X}: X damage to any target) cast through a flow session in the worker beside
    // Alice's device: its X step with the most her one Mountain pays, its target bar, then its payment and send give the page
    // the same panels (the values the worker answers, the page's copy of the board with the flow's changes) and the same inputs
    // as a session here; the worker's session log reaches the page's app log
    @Test fun anXSpellThroughTheWorkersSessionGivesTheSamePanels() {
        val (local, remote) = mainWith(EngineGameFixture.Choices.BURST, "Mountain")
        val bob = arcanum.engine.state.TargetRef.Player(arcanum.engine.state.PlayerId(f.b))
        fun steps(game: EngineGame): List<(CastController) -> Boolean> = listOf(
            { c -> c.start(handCard(game, EngineGameFixture.Choices.BURST)) },
            { c -> c.answer(arcanum.engine.decision.Answer.Number(1)) },
            { c -> c.answer(assertNotNull(c.pick, "X answered, the target is next").answer(listOf(bob))) })
        val (localPanels, localSent) = panelSteps(local, local, steps(local))
        val (remotePanels, remoteSent) = panelSteps(remote, local, steps(remote))
        assertIs<arcanum.engine.decision.Decision.Announce>((localPanels.first()[0] as org.asyncttwiz.automatum.boundary.CastFlowValue).pending)
        assertEquals(1, localPanels.first()[12], "X up to what one Mountain pays")
        assertEquals(localPanels, remotePanels)
        assertEquals(assertNotNull(localSent, "sent with its payment (Autotap)"), remoteSent)
        val log = AppLog.text().takeLast(20_000)
        assertTrue("[worker] Flow session" in log && "opened: cast" in log, "the worker's session log reached the page's log")
    }

    // Web client step 4e, STK-030: Cinder Charm (choose one: 1 damage to any target, or gain 2 life) cast through a flow
    // session in the worker: its modes, then (life chosen) straight to the send, as a session here gives on the same board
    @Test fun aModalSpellThroughTheWorkersSessionGivesTheSamePanels() {
        val (local, remote) = mainWith(EngineGameFixture.Choices.CHARM, "Mountain")
        fun steps(game: EngineGame): List<(CastController) -> Boolean> = listOf(
            { c -> c.start(handCard(game, EngineGameFixture.Choices.CHARM)) },
            { c -> c.answer(arcanum.engine.decision.Answer.Modes(listOf(1))) })
        val (localPanels, localSent) = panelSteps(local, local, steps(local))
        val (remotePanels, remoteSent) = panelSteps(remote, local, steps(remote))
        assertIs<arcanum.engine.decision.Decision.Modes>((localPanels.first()[0] as org.asyncttwiz.automatum.boundary.CastFlowValue).pending)
        assertEquals(localPanels, remotePanels)
        assertEquals(assertNotNull(localSent, "life chosen: sent at once"), remoteSent)
    }

    // Web client step 4e: a step for a session the worker doesn't hold (closed, or never opened) is refused as stale, a step on a
    // board it no longer holds drops the flow, and a closed session's step is refused; the page's cast stays as it was
    @Test fun aStepForAFlowThePageNoLongerShowsIsRefused() {
        val (_, remote) = mainWith(EngineGameFixture.Choices.CHARM, "Mountain")
        val answers = mutableListOf<org.asyncttwiz.automatum.boundary.CastAnswer>()
        val change = org.asyncttwiz.automatum.boundary.CastOp.Update(org.asyncttwiz.automatum.boundary.CastChange.Back(0),
            org.asyncttwiz.automatum.boundary.CastChoices())
        remote.castStep(org.asyncttwiz.automatum.boundary.CastRequest(424242, 1, null, change)) { answers += it }
        remote.castStep(org.asyncttwiz.automatum.boundary.CastRequest(424243, null, 987654321,
            org.asyncttwiz.automatum.boundary.CastOp.Start(handCard(remote, EngineGameFixture.Choices.CHARM)))) { answers += it }
        pump()
        assertEquals(org.asyncttwiz.automatum.boundary.CastAnswer.Refuse(org.asyncttwiz.automatum.boundary.FlowSessions.STALE), answers[0])
        assertEquals(org.asyncttwiz.automatum.boundary.CastAnswer.Drop(CastController.TABLE_CHANGED), answers[1])
        // A session opened and dropped by the page (Undo) ends in the worker too, logged there
        val cast = panel(remote, f.a) { }
        assertTrue(cast.start(handCard(remote, EngineGameFixture.Choices.CHARM)))
        pump()
        assertNotNull(cast.flow, "the modes asked")
        cast.cancel("undone at 4e")
        pump()
        assertTrue(!cast.open)
        assertTrue("closed: undone at 4e" in AppLog.text().takeLast(20_000), "the worker's session closed")
    }
}
