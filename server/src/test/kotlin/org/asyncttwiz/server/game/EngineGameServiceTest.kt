package org.asyncttwiz.server.game

import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.input.Input
import arcanum.engine.setup.DeckCard
import arcanum.engine.setup.GameResult as EngineResult
import arcanum.engine.setup.Seat
import arcanum.engine.state.CardId
import arcanum.engine.state.PlayerId
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import org.asyncttwiz.protocol.*
import org.asyncttwiz.protocol.game.EngineJson
import org.asyncttwiz.protocol.game.GameModel
import org.asyncttwiz.protocol.game.GameSession
import org.asyncttwiz.protocol.game.Submission
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.accounts.AccountService
import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.FreshDatabase
import org.asyncttwiz.server.decks.DeckService
import org.asyncttwiz.server.lobby.LobbyService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * I-D54 phase 3 through the game service: a play started as engine state (the test seam [engineSeats]) takes
 * [GameAction.Submit]s in arrival order, refuses a stale parent and an impossible input, stores and broadcasts each
 * event (the revision's [GameRevision.engineEvent]), and serves its state at the live line's last event in the head,
 * also after a restart (cold: checkpoint plus events).
 */
class EngineGameServiceTest {
    private fun id() = UUID.randomUUID().toString()
    private fun account(runtime: ServiceRuntime, name: String): AccountService.Principal {
        val accounts = AccountService(runtime); accounts.register(name, "$name@example.invalid", "synthetic password")
        return accounts.authenticate(accounts.login("$name@example.invalid", "synthetic password")!!.token)!!
    }
    private class Stream(val games: GameService, val actor: AccountService.Principal, val game: GameSummary) {
        val hello = GameHello(UUID.randomUUID().toString(), UUID.randomUUID().toString())
        val connection = UUID.randomUUID()
        val stream = games.open(actor, hello, connection, game.id, UUID.randomUUID().toString())
        fun command(action: GameAction): GameCommand {
            val scope = if (action is GameAction.Submit) GameScope(game.id, playId = games.head(actor, game.id).summary.playId)
                else GameScope(game.id, game.preparationRound, if (action == GameAction.AcceptInvitation) game.invitationId else null)
            return GameCommand(UUID.randomUUID().toString(), scope, action, stream.streamId, stream.generation)
        }
        fun admit(c: GameCommand) = games.admit(actor, hello, connection, c)
        fun result(c: GameCommand) = games.results(actor, game.id, listOf(c.requestId)).statuses.single().result!!
        fun execute(c: GameCommand): GameResult { admit(c); assertTrue(games.executeOne()); return result(c) }
        fun execute(a: GameAction) = execute(command(a))
    }

    private val names = List(16) { if (it % 2 == 0) "Forest" else "Mountain" } +
        List(24) { listOf("Elvish Seer", "Storm Spark", "Thicketwood Elves")[it % 3] }

    /** A started game; its play is engine state when [engine]. */
    private inner class Fixture(runtime: ServiceRuntime, engine: Boolean) {
        val a = account(runtime, "EngineA${if (engine) "" else "x"}"); val b = account(runtime, "EngineB${if (engine) "" else "x"}")
        val games = GameService(runtime)
        val game: GameSummary
        val owner: Stream; val recipient: Stream
        init {
            val lobby = LobbyService(runtime); lobby.add(a, AddContact(id(), b.account.accountId))
            game = GameWire.json.decodeFromString(lobby.create(a, CreateInvitation(id(), b.account.accountId, "Engine Spark")).body)
            owner = Stream(games, a, game); recipient = Stream(games, b, game)
            recipient.execute(GameAction.AcceptInvitation)
            for ((actor, stream) in listOf(a to owner, b to recipient)) {
                val decks = DeckService(runtime)
                val definition = CardDefinition("Engine fixture", listOf(DefinitionFace(id(), "Forest")))
                val submitted = SubmittedDefinition(DeckJson.digest(definition), definition)
                assertEquals(200, decks.submit(actor, SubmitDefinitions(id(), listOf(submitted))).status)
                val saved = decks.create(actor, CreateDeck(id(), "Engine deck", listOf(SavedDeckEntry(SavedDeckSection.MAINBOARD, submitted.digest, 1))))
                val deck = GameWire.json.decodeFromString<SavedDeck>(saved.body)
                stream.execute(GameAction.SelectDeck(deck.deck.id, deck.deck.revision))
            }
            owner.execute(GameAction.SetStarter(StarterChoice(a.account.accountId)))
            val seats = listOf(a, b).map { p -> PlayerId(p.account.accountId) }.map { p ->
                Seat(p, names.mapIndexed { i, n -> DeckCard(CardId("${p.value}.$i"), Cards.definition(n)) })
            }
            engineSeats = if (engine) { _ -> seats } else null
            try {
                owner.execute(GameAction.Ready); recipient.execute(GameAction.Ready)
            } finally { engineSeats = null }
        }

        fun snapshot(actor: AccountService.Principal = a): EngineSnapshot = games.head(actor, game.id).engine!!
        fun device(actor: AccountService.Principal, seed: Long): Pair<GameSession, EngineJson> {
            val snapshot = snapshot(actor)
            val codec = EngineJson(EngineJson.decodeDefinitions(snapshot.definitions))
            return GameSession(codec.decodeModel(snapshot.state), snapshot.headId, PlayerId(actor.account.accountId), Random(seed)) to codec
        }
    }

    @Test fun `submissions are taken in arrival order, a stale parent is refused, and each event is broadcast`() {
        FreshDatabase().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            ServiceRuntime(db.settings).use { runtime ->
                runtime.start(); val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
                while (!runtime.ready()) { assertTrue(System.nanoTime() < end); Thread.sleep(20) }
                val f = Fixture(runtime, engine = true)
                val (alice, codec) = f.device(f.a, 1)
                val (bob, _) = f.device(f.b, 2)
                // The server started the game: Alice's mulligan declaration is open (starter as the lobby chose)
                assertEquals(PlayerId(f.a.account.accountId), alice.model.engine.pending!!.decision.player)
                assertEquals(f.a.account.accountId, f.games.head(f.b, f.game.id).summary.attentionHolder)
                val revision = f.games.head(f.a, f.game.id).summary.revision
                // Both act on the same event; Bob's arrives first (mulligans in any order, §8)
                val bobs = f.recipient.command(GameAction.Submit(bob.act(id(), Input.SetupAnswer(bob.me, Answer.Option("Keep")))))
                val open = alice.model.engine.pending!!.decision
                val alices = f.owner.command(GameAction.Submit(alice.act(id(), Input.Answer(open.id, Answer.Option("Keep")))))
                f.recipient.admit(bobs); f.owner.admit(alices)
                assertTrue(f.games.executeOne()); assertTrue(f.games.executeOne())
                assertEquals("accepted", f.recipient.result(bobs).outcome)
                val refused = f.owner.result(alices)
                assertEquals("rejected", refused.outcome); assertEquals("stale", refused.reason)
                // The broadcast event is Bob's own; Alice's device drops hers and updates
                val broadcast = f.games.revisions(f.a, f.game.id, revision).revisions.mapNotNull { it.engineEvent }.map(codec::decodeEvent)
                assertEquals(1, broadcast.size)
                assertEquals(GameSession.Received.Confirmed, bob.received(broadcast.single()))
                assertTrue(alice.received(broadcast.single()) is GameSession.Received.Applied)
                assertTrue(alice.model.sameAs(bob.model))
                // Alice acts again on the new state; an impossible input is refused
                val again = alice.model.engine.pending!!.decision
                val keep = f.owner.execute(GameAction.Submit(alice.act(id(), Input.Answer(again.id, Answer.Option("Keep")))))
                assertEquals("accepted", keep.outcome)
                val illegal = Submission(id(), alice.pending.single().id, listOf(org.asyncttwiz.protocol.game.Act.Perform(Input.PassPriority(bob.me))))
                assertEquals("refusedByEngine", f.recipient.execute(GameAction.Submit(illegal)).reason)
                // The head serves the state at the live line's last event, warm and after a restart (cold)
                val warm = f.snapshot()
                assertEquals(alice.pending.single().id, warm.headId)
                assertTrue(codec.decodeModel(warm.state).sameAs(alice.model))
                assertEquals(1, codec.decodeModel(warm.state).engine.turn.number)
                val cold = GameService(runtime).head(f.b, f.game.id).engine!!
                assertEquals(warm, cold)
                // Conceding is an engine input: only for oneself
                val concede = { by: PlayerId -> Submission(id(), warm.headId, listOf(org.asyncttwiz.protocol.game.Act.Perform(Input.Concede(by)))) }
                assertEquals("refusedByEngine", f.owner.execute(GameAction.Submit(concede(bob.me))).reason)
                assertEquals("accepted", f.recipient.execute(GameAction.Submit(concede(bob.me))).outcome)
                val over = codec.decodeModel(f.snapshot().state)
                assertEquals(EngineResult.Won(alice.me), Engine.result(over.engine))
                assertNull(f.games.head(f.a, f.game.id).summary.attentionHolder)
            }
        }
    }

    @Test fun `a play on today's model refuses engine submissions`() {
        FreshDatabase().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            ServiceRuntime(db.settings).use { runtime ->
                runtime.start(); val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
                while (!runtime.ready()) { assertTrue(System.nanoTime() < end); Thread.sleep(20) }
                val f = Fixture(runtime, engine = false)
                assertNull(f.games.head(f.a, f.game.id).engine)
                val submission = Submission(id(), id(), listOf(org.asyncttwiz.protocol.game.Act.Perform(Input.Concede(PlayerId(f.a.account.accountId)))))
                assertEquals("notEngineState", f.owner.execute(GameAction.Submit(submission)).reason)
            }
        }
    }
}
