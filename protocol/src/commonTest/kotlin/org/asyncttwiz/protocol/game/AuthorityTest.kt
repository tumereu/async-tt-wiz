package org.asyncttwiz.protocol.game

import arcanum.engine.Engine
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.setup.GameResult
import arcanum.engine.setup.StartOptions
import arcanum.engine.state.PlayerId
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * I-D54 phases 3–4: the server takes devices' submissions in arrival order on the live line and makes the same events
 * the devices made (the engine is deterministic, random results come with the inputs); a device shows its own events at
 * once and resyncs to the server's when its own are refused.
 */
class AuthorityTest {
    /** A server holding one game in memory, as the server keeps active games warm. */
    private class Server(val games: EngineGames) {
        val store = InMemoryHistory("root", games.codec.encodeModel(games.model()))
        val history = GameHistory(store, games.codec, every = 16)
        var head = "root"
        var warm: LiveState? = null
        private var ids = 0

        /** Accepts [submission] by [author]: the stored event (as broadcast), or the refusal's reason. */
        fun submit(submission: Submission, author: PlayerId, branch: Boolean = false): Result<GameEvent> = try {
            val accepted = Acceptance.accept(history, head, warm, submission, author, branch)
            val event = accepted.event
            store.add(event, history.checkpointFor(store.depthOfParent(event) + 1, accepted.live.model))
            warm = accepted.live
            head = event.id
            // As broadcast: in the stored format and back
            Result.success(games.codec.decodeEvent(games.codec.encodeEvent(event)))
        } catch (refusal: Refusal) {
            warm = null
            Result.failure(refusal)
        }

        /** The game's start, made by the server itself with its own random results. */
        fun start(options: StartOptions): GameEvent {
            val model = history.stateAt("root")
            val builder = EventBuilder(model, "s${++ids}", "root", games.alice.value)
            builder.perform(Input.StartGame(options))
            builder.answerRandoms(model, Random(7))
            val event = builder.build()
            store.add(event, history.checkpointFor(1, model))
            head = event.id
            warm = LiveState(event.id, model)
            return event
        }

        /** The state at the live line's last event. */
        fun state(): GameModel = warm?.model ?: history.stateAt(head)

        fun device(player: PlayerId, seed: Long) = GameSession(history.stateAt(head), head, player, Random(seed))
    }

    private val start = StartOptions(startingPlayer = PlayerId("alice"), simultaneousMulligans = true)

    @Test
    fun aDevicesEventIsTheServersEvent() {
        val server = Server(EngineGames(1))
        server.start(start)
        val alice = server.device(server.games.alice, 1)
        val bob = server.device(server.games.bob, 2)
        // Both declare at once: Alice's declaration is open, Bob's goes ahead (simultaneous mulligans, §8)
        val bobs = bob.act("b1", Input.SetupAnswer(bob.me, Answer.Option("Keep")))
        val accepted = server.submit(bobs, bob.me).getOrThrow()
        assertEquals(bob.pending.single(), accepted)
        assertEquals(GameSession.Received.Confirmed, bob.received(accepted))
        assertIs<GameSession.Received.Applied>(alice.received(accepted))
        val open = alice.model.engine.pending!!.decision as Decision.ChooseOption
        val alices = alice.act("a1", Input.Answer(open.id, Answer.Option("Keep")))
        val second = server.submit(alices, alice.me).getOrThrow()
        alice.received(second); bob.received(second)
        assertTrue(alice.model.sameAs(server.state()) && bob.model.sameAs(server.state()))
        assertTrue(alice.model.engine.turn.number == 1, "the first turn began")
    }

    @Test
    fun aStaleSubmissionIsRefusedAndTheDeviceResyncs() {
        val server = Server(EngineGames(2))
        server.start(start)
        val alice = server.device(server.games.alice, 1)
        val bob = server.device(server.games.bob, 2)
        // Both act on the same event; Alice's arrives first
        val open = alice.model.engine.pending!!.decision
        val alices = alice.act("a1", Input.Answer(open.id, Answer.Option("Mulligan")))
        val bobs = bob.act("b1", Input.SetupAnswer(bob.me, Answer.Option("Keep")))
        val accepted = server.submit(alices, alice.me).getOrThrow()
        val refused = server.submit(bobs, bob.me)
        assertEquals("stale", refused.exceptionOrNull()?.message)
        // Bob's device sees Alice's event: his own is dropped and the board updates
        val got = bob.received(accepted)
        assertIs<GameSession.Received.Applied>(got)
        assertEquals(listOf("b1"), got.dropped.map { it.id })
        assertEquals(GameSession.Received.Applied(emptyList(), false), bob.refused("b1"))
        assertTrue(bob.model.sameAs(server.state()))
        assertEquals(GameSession.Received.Confirmed, alice.received(accepted))
        // He can act again on the new state
        val again = server.submit(bob.act("b2", Input.SetupAnswer(bob.me, Answer.Option("Keep"))), bob.me).getOrThrow()
        assertEquals(GameSession.Received.Confirmed, bob.received(again))
    }

    @Test
    fun theServerRefusesWhatIsNotPossible() {
        val server = Server(EngineGames(3))
        server.start(start)
        val head = server.head
        val alice = server.games.alice; val bob = server.games.bob
        // An input the engine refuses, a concession for the other player, a result nobody suggested
        assertTrue(server.submit(Submission("x1", head, listOf(Act.Perform(Input.PassPriority(alice)))), alice).isFailure)
        assertTrue(server.submit(Submission("x2", head, listOf(Act.Perform(Input.Concede(bob)))), alice).isFailure)
        assertTrue(server.submit(Submission("x3", head, listOf(Act.Perform(Input.AgreedResult(GameResult.Draw)))), alice).isFailure)
        // Another player's settings
        val settings = TableChange.Settings(bob, PlaySettings(), PlaySettings(fullControl = true))
        assertTrue(server.submit(Submission("x4", head, table = listOf(settings)), alice).isFailure)
        assertEquals(head, server.head)
        // The state is as it was: an allowed submission still applies
        assertTrue(server.submit(Submission("x5", head, table = listOf(settings)), bob).isSuccess)
    }

    // I-D55: each player acts only for themselves: Bob can't answer Alice's open declaration, nor declare for her
    @Test
    fun aPlayerActsOnlyForThemselves() {
        val server = Server(EngineGames(8))
        server.start(start)
        val alice = server.games.alice; val bob = server.games.bob
        val open = server.state().engine.pending!!.decision
        assertEquals(alice, open.player)
        val answer = Input.Answer(open.id, Answer.Option("Keep"))
        assertTrue(Input.SetupAnswer(alice, Answer.Option("Keep")).let { !Authority.mayMake(server.state().engine, it, bob) })
        assertEquals(false, Authority.mayMake(server.state().engine, answer, bob))
        assertTrue(server.submit(Submission("o1", server.head, listOf(Act.Perform(answer))), bob).isFailure)
        // Alice answers it herself
        assertTrue(server.submit(Submission("o2", server.head, listOf(Act.Perform(answer))), alice).isSuccess)
    }

    @Test
    fun aSuggestedResultIsAgreedByTheOtherPlayer() {
        val server = Server(EngineGames(4))
        server.start(start)
        val alice = server.games.alice; val bob = server.games.bob
        val suggestion = ResultSuggestion(alice, GameResult.Draw)
        server.submit(Submission("r1", server.head, table = listOf(TableChange.Suggestion(null, suggestion))), alice).getOrThrow()
        // Alice can't accept her own suggestion
        val agree = listOf(Act.Perform(Input.AgreedResult(GameResult.Draw)))
        val clear = listOf(TableChange.Suggestion(suggestion, null))
        assertTrue(server.submit(Submission("r2", server.head, agree, clear), alice).isFailure)
        server.submit(Submission("r3", server.head, agree, clear + TableChange.ResultConfirmed(null, bob)), bob).getOrThrow()
        assertEquals(GameResult.Draw, Engine.result(server.state().engine))
        assertNull(server.state().table.suggestion)
    }

    @Test
    fun aConcessionIsAnEngineInput() {
        val server = Server(EngineGames(5))
        server.start(start)
        val bob = server.games.bob
        server.submit(Submission("c1", server.head, listOf(Act.Perform(Input.Concede(bob)))), bob).getOrThrow()
        assertEquals(GameResult.Won(server.games.alice), Engine.result(server.state().engine))
    }

    @Test
    fun aBranchFromAnOlderEventIsDeliberate() {
        val server = Server(EngineGames(6))
        val first = server.start(start)
        val bob = server.games.bob
        val keep = { id: String, parent: String -> Submission(id, parent, listOf(Act.Perform(Input.SetupAnswer(bob, Answer.Option("Keep"))))) }
        server.submit(keep("k1", first.id), bob).getOrThrow()
        // Made on the start again: stale unless it is a branch
        assertEquals("stale", server.submit(keep("k2", first.id), bob).exceptionOrNull()?.message)
        val branched = server.submit(keep("k3", first.id), bob, branch = true).getOrThrow()
        assertEquals(first.id, branched.parentId)
    }

    // Seeded games played by two devices through the server: every accepted event is the one the device made, and all
    // three states stay equal
    @Test
    fun devicesAndTheServerStayInStep() {
        for (seed in 1L..3L) {
            val server = Server(EngineGames(seed))
            server.start(start)
            val devices = listOf(server.device(server.games.alice, seed * 10), server.device(server.games.bob, seed * 10 + 1))
            val rnd = Random(seed)
            var events = 0
            while (events < 150 && Engine.result(server.state().engine) == null) {
                val device = devices[rnd.nextInt(2)]
                val state = device.model.engine
                val listed = Engine.options(state, device.me).filter { it !is Input.Concede && Authority.mayMake(state, it, device.me) }
                val input = listed.randomOrNull(rnd) ?: continue
                val submission = device.act("e$seed-${++events}", input)
                val event = server.submit(submission, device.me).getOrThrow()
                assertEquals(device.pending.single(), event, "seed $seed: the server made another event")
                devices.forEach { it.received(event) }
                devices.forEach { assertTrue(it.model.sameAs(server.state()), "seed $seed event $events: out of step") }
            }
            assertTrue(events > 50, "seed $seed: $events events")
        }
    }

    @Test
    fun anOpenDraftIsSentAsOneSubmission() {
        val server = Server(EngineGames(7))
        server.start(start)
        val alice = server.device(server.games.alice, 1)
        val draft = alice.begin("d1")
        val open = alice.model.engine.pending!!.decision
        draft.perform(Input.Answer(open.id, Answer.Option("Keep")))
        assertFailsWith<IllegalStateException> { alice.begin("d2") }
        val submission = alice.send()
        assertEquals(1, submission.acts.size)
        assertEquals(GameSession.Received.Confirmed, alice.received(server.submit(submission, alice.me).getOrThrow()))
    }
}
