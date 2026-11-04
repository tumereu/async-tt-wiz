package org.asyncttwiz.android

import arcanum.api.TurnStep
import arcanum.engine.input.Input
import org.asyncttwiz.protocol.*
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.PlaySettings
import org.asyncttwiz.protocol.game.ResponseMode
import org.junit.Ignore
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Adversarial review of the switch-over to engine-state games (I-D56 step D2, 2026-11-04,
 * `~/projects/atw-cache/switch-review-2026-11-04.md`): the app's session and engine thread. Each finding's reproduction
 * is ignored with its number until fixed.
 */
class SwitchReviewTest {
    private val f = EngineGameFixture()

    // ---- The session (GameSession) as the app wires it: a tree cache, and a server without the old baseline endpoints

    private class Wire : GameConnection {
        lateinit var listener: GameConnection.Listener
        val frames = mutableListOf<GameClientMessage>()
        var disconnected = 0
        var treeTransfers = 0
        override fun connect(token: String, listener: GameConnection.Listener) { this.listener = listener }
        override fun send(message: GameClientMessage): Boolean { frames.add(message); return true }
        override fun disconnect() { disconnected++ }
        override fun close() = Unit
        /** As the server after step D2 answers: `/v1/games/{id}/baselines` is gone (404 "not_found"), which the socket
         * connection reads as a failed transfer (complete with null), not a refusal. */
        override fun treeTransfer(gameId: String, requestIds: List<String>, rejected: (String) -> Unit,
            complete: (GameplayTreeTransfer?) -> Unit): Boolean { treeTransfers++; complete(null); return true }
    }

    private inner class Session(me: String = f.a) {
        val wire = Wire()
        val posts = ArrayDeque<() -> Unit>()
        val session = GameSession(wire, { "A".repeat(43) }, { posts.add(it) }, {}, {}, { _, _ -> }, {},
            accountId = { me }, treeCache = { _, _, _ -> null })
        fun drain() { while (posts.isNotEmpty()) posts.removeFirst()() }
        fun message(message: GameServerMessage) { wire.listener.message(message); drain() }
        fun connect(game: GameSummary) {
            session.watch(listOf(game)); wire.listener.opened(); drain()
            message(GameWelcome(gameplayRulesVersion = CURRENT_GAMEPLAY_RULES_VERSION))
            val stream = wire.frames.mapNotNull { (it as? OpenGameStream)?.streamId ?: (it as? ResumeGameStream)?.streamId }.last()
            message(GameStreamReady(game.id, stream, "1"))
        }
    }

    /** The fixture's game, in the lobby (accepted, both preparing) at revision 1, and as the start made it active. */
    private val lobby = f.game.copy(preparationRound = f.base.preparationRound, lifecycle = GameLifecycle.LOBBY, playId = null, revision = "1")
    private val active = lobby.copy(lifecycle = GameLifecycle.ACTIVE, playId = f.base.id, revision = "2")

    // switchreview #1, SYS-052, I-D56 step D2: the Ready that starts a game is an ordinary revision carrying the play (no
    // engine part). The session hands it to the old model's catch-up (TreeCatchUp), which has no replica for it and
    // starts a tree recovery from the old baseline endpoint, deleted in step D2: the transfer fails and the session drops
    // the whole connection ("treeManifestFailed"), and only the reconnect's head loads the engine game. Every game start
    // seen live (and every other way into a recovery for an engine game) costs a reconnect of every open stream.
    @Test fun aGameStartLoadsTheEngineGameWithoutAnOldTreeRecovery() {
        val s = Session()
        try {
            s.connect(lobby)
            s.message(GameHead(lobby.id, lobby.revision, lobby))
            val start = GameRevision(lobby.id, "1", "2", DeviceRandom.uuid(), f.b, GameScope(lobby.id, lobby.preparationRound), active, play = f.base)
            // (the session's first tick resets the connection once before it connects)
            val before = s.wire.disconnected
            s.message(start)
            assertEquals(before, s.wire.disconnected, "the start doesn't drop the connection")
            assertEquals(0, s.wire.treeTransfers, "an engine play has no old history tree to download")
            assertTrue(s.wire.frames.any { it is SubscribeGame && it.gameId == lobby.id && it.revision == "2" },
                "the session asks for the engine head of the new play")
        } finally { s.session.close() }
    }

    // switchreview #10, SYS-012: a resubscription replays at most 100 revisions after the device's revision, then sends the
    // head. An engine game that "follows exactly" ignores a head (GameSession.engineHead), yet the session records the
    // head's revision as accepted; so when more than 100 revisions were missed (playback reports and chat count too), the
    // device stays on an older board with no event coming to tell it, and the missed revisions are fenced as old.
    // Expected: a head past what the device holds loads it (or the session pages the revisions).
    @Test fun aHeadPastTheDevicesEventsLoadsTheEngineGame() {
        val s = Session()
        try {
            s.connect(active)
            s.message(GameHead(active.id, active.revision, active, play = f.base, engine = f.snapshot()))
            val game = assertNotNull(s.session.engineGame(active.id), "the engine game loaded")
            // Bob keeps (his own declaration while Alice's is open); the revision carrying it was not replayed
            // (beyond the 100 a resubscription sends), only the head after it arrives
            val keep = org.asyncttwiz.protocol.game.EventBuilder(f.model.copy(), DeviceRandom.uuid(), f.start.id, f.b).run {
                perform(Input.SetupAnswer(arcanum.engine.state.PlayerId(f.b), arcanum.engine.decision.Answer.Option("Keep"))); answerRandoms(); build()
            }
            val after = f.model.copy().also { it.apply(keep) } // f.model is the state after the start event
            val newer = active.copy(revision = "150")
            val head = org.asyncttwiz.protocol.EngineSnapshot(f.codec.encodeDefinitions(), f.snapshot().rootId, keep.id,
                f.codec.encodeModel(after), localHeads = mapOf(f.a to f.start.id, f.b to keep.id),
                baseId = f.start.id, base = f.codec.encodeModel(f.model), events = listOf(f.codec.encodeEvent(keep)))
            s.message(GameHead(active.id, newer.revision, newer, play = f.base, engine = head))
            val now = assertNotNull(s.session.engineGame(active.id))
            assertTrue(now.read { d -> d.session.events.any { it.id == keep.id } }, "the device holds the main line up to the head it was sent")
        } finally { s.session.close() }
    }

    // ---- The engine thread (EngineGame on EngineWorker.Background)

    /**
     * The app's engine thread as it orders work, with the main thread's queue held by the test: [run] queues engine
     * work, [call] runs everything queued before it and then its own work (as a single-thread executor does), and what
     * the engine thread posts to the main thread waits until [drainMain] (the table hasn't recomposed yet).
     */
    private class LaggingWorker : EngineWorker {
        private val engine = ArrayDeque<() -> Unit>()
        private val main = ArrayDeque<() -> Unit>()
        private var onEngine = false
        private fun <T> onEngine(work: () -> T): T { val was = onEngine; onEngine = true; try { return work() } finally { onEngine = was } }
        override fun run(work: () -> Unit) { engine.add(work) }
        override fun <T> call(work: () -> T): T { if (onEngine) return work(); drainEngine(); return onEngine(work) }
        override fun main(work: () -> Unit) { main.add(work) }
        fun drainEngine() { while (engine.isNotEmpty()) { val w = engine.removeFirst(); onEngine(w) } }
        fun drainMain() { while (main.isNotEmpty()) main.removeFirst()() }
        fun drain() { do { drainEngine(); drainMain() } while (engine.isNotEmpty() || main.isNotEmpty()) }
    }

    // switchreview #6, SYS-025, TRN-057, I-D56 step D2: the player's action waits for its own local result, but the board
    // that shows it is published only after the device's own steps that follow (passes, assumptions, the projection).
    // A second tap made on the board still shown (a double tap, or a tap while nothing seems to have happened yet on a
    // slow phone) acts on the device's newer state: here Alice's second Next passes her priority in beginning of combat,
    // a point her table never showed. Expected: an action made from a board that is no longer the device's (its shown
    // version) is refused, or the action's own board is published before the call returns.
    @Ignore("switchreview #6: an action from a stale shown board acts at a newer point the player never saw")
    @Test fun aSecondTapOnTheStillShownBoardDoesNotActAtAPointNeverShown() {
        val worker = LaggingWorker()
        val game = EngineGame(f.game.id, f.a, f.base, f.snapshot(), worker = worker)
        worker.drain()
        game.tick(live = false, now = 1_000); worker.drain()
        assertIs<GameDevice.Acted.Sent>(game.keep(emptyList())); worker.drain()
        game.tick(live = false, now = 2_000); worker.drain()
        // Full control: her device stops wherever she gets priority (so beginning of combat too)
        game.settings(PlaySettings(mode = ResponseMode.FullControl)); worker.drain()
        val shown = assertNotNull(game.shown)
        assertEquals(TurnStep.PrecombatMain, shown.engine.turn.step, "Alice's first main phase, with priority")
        assertEquals(GameDevice.Status.YourMove, shown.status)
        fun ownPasses() = game.read { d -> d.session.pending.flatMap { e -> e.engine?.steps.orEmpty() }
            .count { it.input == Input.PassPriority(arcanum.engine.state.PlayerId(f.a)) && it.assumedBy == null } }
        val before = ownPasses()
        // First tap: her pass in main 1 (its local result exists when the call returns)
        assertIs<GameDevice.Acted.Sent>(game.pass())
        worker.drainEngine() // the device's own steps after it run on the engine thread (Bob's assumed pass, …)
        assertEquals(shown.version, game.shown!!.version, "the table still shows main 1: the new board hasn't reached it")
        // Second tap on that same board
        val second = game.pass()
        worker.drain()
        assertTrue(second is GameDevice.Acted.Refused || ownPasses() - before <= 1,
            "the second Next acted on a board Alice never saw: $second, now at ${game.shown!!.engine.turn.step}")
    }
}
