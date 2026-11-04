package org.asyncttwiz.server.game

import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.input.Input
import arcanum.engine.setup.DeckCard
import arcanum.engine.setup.Seat
import arcanum.engine.state.CardId
import arcanum.engine.state.PlayerId
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.asyncttwiz.protocol.*
import org.asyncttwiz.protocol.game.EarlyAnswer
import org.asyncttwiz.protocol.game.EngineJson
import org.asyncttwiz.protocol.game.GameSession
import org.asyncttwiz.protocol.game.TableChange
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.accounts.AccountService
import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.FreshDatabase
import org.asyncttwiz.server.decks.DeckService
import org.asyncttwiz.server.lobby.LobbyService
import org.jooq.ExecuteContext
import org.jooq.impl.DSL
import org.jooq.impl.DefaultExecuteListener
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Adversarial review of I-D54 + I-D56 steps A and B (2026-11-04, `~/projects/atw-cache/ab-review-2026-11-04.md`): the
 * server-side findings' reproductions, disabled with their numbers until fixed.
 */
class EngineAbReviewTest {
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
            val scope = if (action is GameAction.Submit || action.isEngine) GameScope(game.id, playId = games.head(actor, game.id).summary.playId)
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

    private fun <T> withService(test: (ServiceRuntime) -> T): T = FreshDatabase().start().use { db ->
        FoundationMigrations.migrate(db.settings)
        ServiceRuntime(db.settings).use { runtime ->
            runtime.start(); val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
            while (!runtime.ready()) { assertTrue(System.nanoTime() < end); Thread.sleep(20) }
            test(runtime)
        }
    }

    /** A started game played as engine state (the test seam); Alice (the owner) starts. */
    private inner class Fixture(val runtime: ServiceRuntime, tag: String) {
        val a = account(runtime, "ReviewA$tag"); val b = account(runtime, "ReviewB$tag")
        val games = GameService(runtime)
        val game: GameSummary
        val owner: Stream; val recipient: Stream
        init {
            val lobby = LobbyService(runtime); lobby.add(a, AddContact(id(), b.account.accountId))
            game = GameWire.json.decodeFromString(lobby.create(a, CreateInvitation(id(), b.account.accountId, "Review")).body)
            owner = Stream(games, a, game); recipient = Stream(games, b, game)
            recipient.execute(GameAction.AcceptInvitation)
            for ((actor, stream) in listOf(a to owner, b to recipient)) {
                val service = DeckService(runtime)
                val definition = CardDefinition("Forest", listOf(DefinitionFace(id(), "Forest", typeLine = "Basic Land — Forest")))
                val submitted = SubmittedDefinition(DeckJson.digest(definition), definition)
                assertEquals(200, service.submit(actor, SubmitDefinitions(id(), listOf(submitted))).status)
                val saved = service.create(actor, CreateDeck(id(), "Review deck", listOf(SavedDeckEntry(SavedDeckSection.MAINBOARD, submitted.digest, 40))))
                val deck = GameWire.json.decodeFromString<SavedDeck>(saved.body)
                stream.execute(GameAction.SelectDeck(deck.deck.id, deck.deck.revision))
            }
            owner.execute(GameAction.SetStarter(StarterChoice(a.account.accountId)))
            val seats = listOf(a, b).map { p -> PlayerId(p.account.accountId) }.map { p ->
                Seat(p, names.mapIndexed { i, n -> DeckCard(CardId("${p.value}.$i"), Cards.definition(n)) })
            }
            engineSeats = { _ -> seats }
            try { owner.execute(GameAction.Ready); recipient.execute(GameAction.Ready) } finally { engineSeats = null }
        }

        fun snapshot(actor: AccountService.Principal = a): EngineSnapshot = games.head(actor, game.id).engine!!
        fun device(actor: AccountService.Principal): GameSession {
            val snapshot = snapshot(actor)
            val codec = EngineJson(EngineJson.decodeDefinitions(snapshot.definitions))
            return GameSession(codec.decodeModel(snapshot.state), snapshot.headId, PlayerId(actor.account.accountId))
        }
        val alice get() = PlayerId(a.account.accountId)
        fun <T> sql(query: (org.jooq.DSLContext) -> T): T = runtime.transaction { c -> query(DSL.using(c, org.jooq.SQLDialect.POSTGRES)) }

        /** Alice keeps for real: the head then waits for Bob's declaration. */
        fun aliceKeeps(alice: GameSession) = assertEquals("accepted", owner.execute(GameAction.Submit(alice.act(id(),
            Input.Answer(alice.model.engine.pending!!.decision.id, Answer.Option("Keep"))))).outcome)

        /** One table-only event of Alice's at the head (an early answer of hers): it shows Bob nothing. */
        fun aliceTableEvent(alice: GameSession, n: Int): GameCommand = owner.command(GameAction.Submit(alice.act(id(), null,
            listOf(TableChange.EarlyAnswered("k$n", null, EarlyAnswer(this.alice, "k$n", Answer.YesNo(true)))))))
    }

    // abreview #6 (I-D56 §7, §18.11; LOB-073, LOB-075): the head waits for Bob's real input; while Alice waits she
    // moves things on her side (table-only events). Bob's wait is the same one, so he is notified once and its time
    // stays that of the event it began at. Today each table-only event moves his next stop to the new head event: a
    // new "your move" notification each time and the move time (reminders) reset
    @Test fun `a table-only event of the waiting player doesn't make the other player's wait new`() = withService { runtime ->
        val f = Fixture(runtime, "n")
        val alice = f.device(f.a)
        f.aliceKeeps(alice)
        val bobId = UUID.fromString(f.b.account.accountId)
        fun notified() = f.sql { it.fetchCount(DSL.table("account_activity"), DSL.field("account_id").eq(bobId)
            .and(DSL.field("game_id").eq(UUID.fromString(f.game.id)))) }
        fun moveTime() = f.sql { it.fetchValue("SELECT next_stop_at FROM engine_seat WHERE account_id = ?", bobId) }
        val before = notified()
        val time = moveTime()
        assertNotNull(time, "the head waits for Bob")
        repeat(3) { n -> assertEquals("accepted", f.owner.execute(f.aliceTableEvent(alice, n)).outcome) }
        assertEquals(before, notified(), "Bob was notified again of the same wait")
        assertEquals(time, moveTime(), "the move time moved")
    }

    // abreview #7 (I-D56 §7: "status and last-activity time … come from the local head and next stop … nothing about
    // the line beyond your next stop (its length, events, timing or result) reaches you"): the game list's last-activity
    // time (GameSummary.updatedAt, the revision's summary) is the game row's, which every event of Alice's tentative line
    // moves, so Bob's list shows her playing on past his next stop
    @Test fun `the last-activity time a player sees doesn't move with events beyond their next stop`() = withService { runtime ->
        val f = Fixture(runtime, "t")
        val alice = f.device(f.a)
        f.aliceKeeps(alice)
        // Alice assumes Bob's keep (he is away): his next stop is that event
        assertEquals("accepted", f.owner.execute(GameAction.Submit(alice.act(id(),
            Input.Answer(alice.model.engine.pending!!.decision.id, Answer.Option("Keep")), assumed = true))).outcome)
        val seen = f.games.head(f.b, f.game.id).summary.updatedAt
        // She plays on in her first turn (any event past his next stop does it)
        repeat(2) { n -> assertEquals("accepted", f.owner.execute(f.aliceTableEvent(alice, n)).outcome) }
        assertEquals(seen, f.games.head(f.b, f.game.id).summary.updatedAt)
    }

    // abreview #8 (I-D54 §4, performance): each request opens the history store afresh, and MainLine.after walks from the
    // head to a local head one event (one SELECT and one JSON decode) at a time, for every seat on every submission,
    // report and settings change; the work per request grows with how far the other player is behind (a long turn,
    // or table-only events while they are away). One recursive query (as GameHistory.stateAt uses) keeps it flat
    @Test fun `a submission's statements don't grow with how far the other player is behind`() = withService { runtime ->
        val f = Fixture(runtime, "p")
        val alice = f.device(f.a)
        f.aliceKeeps(alice)
        var statements = 0
        val listener = object : DefaultExecuteListener() { override fun executeEnd(ctx: ExecuteContext) { statements++ } }
        var n = 0
        fun measured(): Int {
            val command = f.aliceTableEvent(alice, n++)
            f.owner.admit(command)
            statements = 0
            assertTrue(f.games.executeOne(listener))
            assertEquals("accepted", f.owner.result(command).outcome)
            return statements
        }
        repeat(2) { assertEquals("accepted", f.owner.execute(f.aliceTableEvent(alice, n++)).outcome) }
        val near = measured()
        repeat(40) { assertEquals("accepted", f.owner.execute(f.aliceTableEvent(alice, n++)).outcome) }
        val far = measured()
        assertTrue(far <= near + 3, "$near statements 3 events behind, $far statements 44 events behind")
    }
}
