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
import org.asyncttwiz.protocol.game.EngineJson
import org.asyncttwiz.protocol.game.GameSession
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.accounts.AccountService
import org.asyncttwiz.server.activity.ActivityService
import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.FreshDatabase
import org.asyncttwiz.server.decks.DeckService
import org.asyncttwiz.server.generated.tables.ActivitySummary.ACTIVITY_SUMMARY
import org.asyncttwiz.server.generated.tables.EngineSeat.ENGINE_SEAT
import org.asyncttwiz.server.lobby.LobbyService
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * I-D57 (async-play.md §24): notifications by who is waiting for whom. Two seats through the real game service: a player
 * hears "Your move" only when the game waits for them, never while the other player plays on assumptions; the player who
 * played on and left is the one the app's reminders list; notices name the point the recipient would resume at and
 * their own deck.
 */
class NotificationWaitsTest {
    private fun id() = UUID.randomUUID().toString()
    private fun account(runtime: ServiceRuntime, name: String): AccountService.Principal {
        val accounts = AccountService(runtime); accounts.register(name, "$name@example.invalid", "synthetic password")
        return accounts.authenticate(accounts.login("$name@example.invalid", "synthetic password")!!.token)!!
    }
    private class Stream(val games: GameService, val actor: AccountService.Principal, val game: GameSummary) {
        val hello = GameHello(UUID.randomUUID().toString(), UUID.randomUUID().toString())
        val connection = UUID.randomUUID()
        val stream = games.open(actor, hello, connection, game.id, UUID.randomUUID().toString())
        fun execute(a: GameAction): GameResult {
            val scope = if (a is GameAction.Submit || a.isEngine || a is GameAction.SendChat)
                GameScope(game.id, playId = games.head(actor, game.id).summary.playId)
                else GameScope(game.id, game.preparationRound, if (a == GameAction.AcceptInvitation) game.invitationId else null)
            val c = GameCommand(UUID.randomUUID().toString(), scope, a, stream.streamId, stream.generation)
            games.admit(actor, hello, connection, c); assertTrue(games.executeOne())
            return games.results(actor, game.id, listOf(c.requestId)).statuses.single().result!!
        }
    }

    private val names = List(16) { if (it % 2 == 0) "Forest" else "Mountain" } +
        List(24) { listOf("Elvish Seer", "Storm Spark", "Thicketwood Elves")[it % 3] }

    private fun withService(test: (ServiceRuntime) -> Unit) = FreshDatabase().start().use { db ->
        FoundationMigrations.migrate(db.settings)
        ServiceRuntime(db.settings).use { runtime ->
            runtime.start(); val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
            while (!runtime.ready()) { assertTrue(System.nanoTime() < end); Thread.sleep(20) }
            test(runtime)
        }
    }

    /** A game of Alice (the starting player, deck "Alice's elves") and Bob (deck "Bob's sparks"), started by both
     * players' Ready; neither has the game open. */
    private inner class Fixture(val runtime: ServiceRuntime) {
        val a = account(runtime, "WaitsA"); val b = account(runtime, "WaitsB")
        val games = GameService(runtime)
        val activities = ActivityService(runtime)
        val game: GameSummary
        val owner: Stream; val recipient: Stream
        init {
            val lobby = LobbyService(runtime); lobby.add(a, AddContact(id(), b.account.accountId))
            game = GameWire.json.decodeFromString(lobby.create(a, CreateInvitation(id(), b.account.accountId, "Waiting game")).body)
            owner = Stream(games, a, game); recipient = Stream(games, b, game)
            recipient.execute(GameAction.AcceptInvitation)
            val card = CardDefinition("Forest", listOf(DefinitionFace(UUID.randomUUID().toString(), "Forest", typeLine = "Basic Land — Forest")))
            for ((actor, stream, deckName) in listOf(Triple(a, owner, "Alice's elves"), Triple(b, recipient, "Bob's sparks"))) {
                val service = DeckService(runtime)
                val submitted = SubmittedDefinition(DeckJson.digest(card), card)
                assertEquals(200, service.submit(actor, SubmitDefinitions(id(), listOf(submitted))).status)
                val saved = service.create(actor, CreateDeck(id(), deckName, listOf(SavedDeckEntry(SavedDeckSection.MAINBOARD, submitted.digest, 40))))
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
            return GameSession(EngineJson(EngineJson.decodeDefinitions(snapshot.definitions)).decodeModel(snapshot.state), snapshot.headId,
                PlayerId(actor.account.accountId))
        }
        fun activity(who: AccountService.Principal) = runtime.transaction { c ->
            DSL.using(c, SQLDialect.POSTGRES).selectFrom(ACTIVITY_SUMMARY).where(ACTIVITY_SUMMARY.ACCOUNT_ID.eq(UUID.fromString(who.account.accountId)))
                .and(ACTIVITY_SUMMARY.GAME_ID.eq(UUID.fromString(game.id))).and(ACTIVITY_SUMMARY.KIND.eq("GAME")).fetchOne()
        }
        /** The game notice [who]'s app would show now (LOB-072), if any. */
        fun notice(who: AccountService.Principal) = activities.page(who, null).readyNotices.orEmpty()
            .singleOrNull { it.kind == ActivityKind.GAME && it.gameId == game.id }?.notice
        /** The game in [who]'s waiting list (LOB-076's reminders), if listed. */
        fun waiting(who: AccountService.Principal) = activities.waiting(who).games.singleOrNull { it.gameId == game.id }?.notice

        /**
         * TRN-058, LOB-022, LOB-024: whose move the game is shown to [who]: the game head's summary, their games list's row
         * and the newest revision's summary all say the same (no source disagrees); that player's account id.
         */
        fun move(who: AccountService.Principal): String? {
            val head = games.head(who, game.id).summary
            val listed = GameWire.json.decodeFromString<GameList>(LobbyService(runtime).list(who, null, false).body)
                .games.single { it.id == game.id }
            val revision = games.revisions(who, game.id, (head.revision.toLong() - 1).toString()).revisions.last().summary
            assertEquals(head.attentionHolder, listed.attentionHolder, "the games list")
            assertEquals(head.attentionHolder, revision.attentionHolder, "the newest revision")
            return head.attentionHolder
        }
        /** Alice is shown [alice]'s move and Bob [bob]'s (null: nobody's), as the server's waiting record has it. */
        fun assertMoves(alice: AccountService.Principal?, bob: AccountService.Principal?, what: String) {
            assertEquals(alice?.account?.accountId, move(a), "$what: Alice is shown")
            assertEquals(bob?.account?.accountId, move(b), "$what: Bob is shown")
            assertEquals(alice == a, waited(a), "$what: the game waits for Alice")
            assertEquals(bob == b, waited(b), "$what: the game waits for Bob")
        }
        private fun waited(who: AccountService.Principal) = runtime.transaction { c -> DSL.using(c, SQLDialect.POSTGRES)
            .select(ENGINE_SEAT.WAITED).from(ENGINE_SEAT).where(ENGINE_SEAT.ACCOUNT_ID.eq(UUID.fromString(who.account.accountId)))
            .and(ENGINE_SEAT.PLAY_ID.eq(UUID.fromString(games.head(who, game.id).summary.playId))).fetchSingle().value1()!! }

        fun aliceKeeps(): GameSession {
            val alice = device(a)
            assertEquals("accepted", owner.execute(GameAction.Submit(alice.act(id(), Input.Answer(alice.model.engine.pending!!.decision.id,
                Answer.Option("Keep"))))).outcome)
            return alice
        }
        fun aliceAssumesBobsKeep(alice: GameSession) {
            val open = alice.model.engine.pending!!.decision
            assertEquals("accepted", owner.execute(GameAction.Submit(alice.act(id(), Input.Answer(open.id, Answer.Option("Keep")), assumed = true))).outcome)
        }
    }

    // I-D57, LOB-072, LOB-076, TRN-058: Alice kept for real and waits for Bob instead of assuming his keep: the game waits for
    // Bob, so he is told "Your move", named where he would resume (still Mulliganing) and with his own deck, and his app's
    // reminders list the game; Alice's don't.
    @Test fun `a real wait tells the player waited for, with where they would resume and their deck`() = withService { runtime ->
        val f = Fixture(runtime)
        f.aliceKeeps()
        val notice = requireNotNull(f.notice(f.b)) { "Bob is told" }
        assertTrue(notice.yourMove)
        assertEquals(ResumePoint(0, false), notice.resumeAt, "Bob would resume in the opening")
        assertEquals("Bob's sparks", notice.deckName)
        assertEquals("Waiting game", notice.gameName); assertEquals("WaitsA", notice.opponentName)
        val listed = requireNotNull(f.waiting(f.b)) { "Bob's reminders list the game" }
        assertTrue(listed.reminder && listed.yourMove)
        assertEquals("Bob's sparks", listed.deckName); assertEquals(ResumePoint(0, false), listed.resumeAt)
        assertNull(f.waiting(f.a), "nothing waits for Alice")
    }

    // I-D57, LOB-072, LOB-075, LOB-076: Alice plays on an assumed keep of Bob's: the game waits for nobody but Alice, so Bob
    // is told nothing (the notice of Alice's keep is withdrawn) and isn't reminded; Alice, who left with nothing waiting
    // for Bob, is the one her app's reminders list, named at her own first turn.
    @Test fun `playing on an assumption tells the other player nothing and reminds the player who played on`() = withService { runtime ->
        val f = Fixture(runtime)
        f.aliceAssumesBobsKeep(f.aliceKeeps())
        assertNull(f.notice(f.b), "Bob isn't told while Alice plays on an assumption about him")
        val row = requireNotNull(f.activity(f.b))
        assertFalse(row.yourMove!!); assertNull(row.reminderMoveAt, "no waiting reminder for Bob")
        assertNull(f.waiting(f.b), "Bob's reminders don't list the game")
        val alice = requireNotNull(f.waiting(f.a)) { "Alice's reminders list the game: it waits for her" }
        assertTrue(alice.reminder && alice.yourMove)
        assertEquals("WaitsB", alice.opponentName); assertEquals("Alice's elves", alice.deckName)
        assertEquals(1, alice.resumeAt!!.turn); assertTrue(alice.resumeAt!!.yours, "her own first turn")
    }

    // I-D57: who is waited for is worked out from the game as it now is, however it came about: Alice takes back her
    // assumption (a free undo, HIS-084) and the game waits for Bob again: he is told "Your move" and listed again.
    @Test fun `an undo that leaves the game waiting for the other player tells them again`() = withService { runtime ->
        val f = Fixture(runtime)
        val alice = f.aliceKeeps()
        val kept = f.snapshot().headId
        f.aliceAssumesBobsKeep(alice)
        assertNull(f.notice(f.b))
        assertEquals("accepted", f.owner.execute(GameAction.EngineRewind(kept)).outcome)
        val notice = requireNotNull(f.notice(f.b)) { "the game waits for Bob again" }
        assertTrue(notice.yourMove)
        assertNotNull(f.waiting(f.b)); assertNull(f.waiting(f.a))
    }

    // TRN-058, LOB-022, LOB-024, TRN-027 (I-D57, owner 2026-11-04): whose move it is follows the waiting record everywhere.
    // In the opening both mulligans wait at once (each player is shown their own move); Alice's real keep waits for Bob
    // (his move for both); Alice playing on an assumed keep of his waits for nobody but her: her move for both, although
    // Bob's playback would stop at the assumed keep; her undo of it waits for Bob again
    @Test fun `the move follows who the game really waits for through assumptions and an undo`() = withService { runtime ->
        val f = Fixture(runtime)
        f.assertMoves(f.a, f.b, "the opening")
        val alice = f.aliceKeeps()
        f.assertMoves(f.b, f.b, "Alice waits for Bob's keep")
        val kept = f.snapshot().headId
        f.aliceAssumesBobsKeep(alice)
        f.assertMoves(f.a, f.a, "Alice plays on an assumed keep")
        assertEquals("accepted", f.owner.execute(GameAction.EngineRewind(kept)).outcome)
        f.assertMoves(f.b, f.b, "Alice's undo")
    }

    // TRN-058, HIS-097 (I-D56 §8): Bob answers Alice's assumed keep differently (a mulligan): a branch; the new line's head
    // is his opening declaration again, so the game waits for him: his move for both
    @Test fun `a branch gives the move to whom the new line waits for`() = withService { runtime ->
        val f = Fixture(runtime)
        val bob = f.device(f.b)
        val alice = f.aliceKeeps()
        val aliceKeep = alice.pending.single()
        bob.received(aliceKeep); while (bob.behind) bob.showNext()
        f.aliceAssumesBobsKeep(alice)
        f.assertMoves(f.a, f.a, "Alice plays on")
        val open = bob.model.engine.pending!!.decision
        val builder = org.asyncttwiz.protocol.game.EventBuilder(bob.model, id(), aliceKeep.id, f.b.account.accountId)
        builder.startLine(f.snapshot().lines + 1)
        builder.perform(Input.Answer(open.id, Answer.Option("Mulligan")))
        builder.answerRandoms()
        assertEquals("accepted", f.recipient.execute(GameAction.Submit(org.asyncttwiz.protocol.game.Submission.of(builder.build()))).outcome)
        f.assertMoves(f.b, f.b, "after Bob's branch")
    }

    // TRN-058, HIS-099, switchreview #12: an open undo request waits for the other player's answer: their move for both;
    // cancelling it gives the move back
    @Test fun `an undo request is the answering player's move for both`() = withService { runtime ->
        val f = Fixture(runtime)
        f.aliceKeeps()
        val target = f.snapshot().headId
        val bob = f.device(f.b)
        assertEquals("accepted", f.recipient.execute(GameAction.Submit(bob.act(id(), Input.Answer(bob.model.engine.pending!!.decision.id,
            Answer.Option("Keep"))))).outcome)
        f.assertMoves(f.a, f.a, "Alice's first turn")
        assertEquals("accepted", f.owner.execute(GameAction.EngineRequestRewind(target, "misclick")).outcome)
        f.assertMoves(f.b, f.b, "Bob answers Alice's request")
        assertEquals("accepted", f.owner.execute(GameAction.EngineCancelRewind).outcome)
        f.assertMoves(f.a, f.a, "cancelled")
    }

    // TRN-058, TRN-047, TRN-049, TRN-065: a result suggestion waits for the other player's answer (their move for both);
    // the agreed result waits for Alice until she has seen it; then for nobody
    @Test fun `a suggestion and a result not seen yet are the move of the player who must answer or see it`() = withService { runtime ->
        val f = Fixture(runtime)
        val draw = arcanum.engine.setup.GameResult.Draw
        val alice = f.device(f.a)
        val suggestion = org.asyncttwiz.protocol.game.ResultSuggestion(PlayerId(f.a.account.accountId), draw)
        assertEquals("accepted", f.owner.execute(GameAction.Submit(alice.act(id(), null,
            listOf(org.asyncttwiz.protocol.game.TableChange.Suggestion(null, suggestion))))).outcome)
        f.assertMoves(f.b, f.b, "Bob answers Alice's suggestion")
        val bob = f.device(f.b)
        assertEquals("accepted", f.recipient.execute(GameAction.Submit(bob.act(id(), Input.AgreedResult(draw),
            listOf(org.asyncttwiz.protocol.game.TableChange.Suggestion(suggestion, null))))).outcome)
        f.assertMoves(f.a, f.a, "Alice hasn't seen the agreed draw")
        assertEquals("accepted", f.owner.execute(GameAction.EngineSeen(f.snapshot().headId)).outcome)
        f.assertMoves(null, null, "both have seen it")
    }
}
