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
import org.asyncttwiz.server.lobby.LobbyService
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Adversarial review of the switch-over to engine-state games (I-D56 step D2, 2026-11-04,
 * `~/projects/atw-cache/switch-review-2026-11-04.md`): two seats through the real game service, from the lobby's Ready
 * to the notifications. Each finding's reproduction is disabled with its number until fixed, then kept as its test.
 */
class SwitchReviewTest {
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
            val scope = if (action is GameAction.Submit || action.isEngine || action is GameAction.SendChat)
                GameScope(game.id, playId = games.head(actor, game.id).summary.playId)
                else GameScope(game.id, game.preparationRound, if (action == GameAction.AcceptInvitation) game.invitationId else null)
            return GameCommand(UUID.randomUUID().toString(), scope, action, stream.streamId, stream.generation)
        }
        fun execute(a: GameAction): GameResult {
            val c = command(a)
            games.admit(actor, hello, connection, c); assertTrue(games.executeOne())
            return games.results(actor, game.id, listOf(c.requestId)).statuses.single().result!!
        }
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

    /** A lobby game of Alice (inviter, the starting player) and Bob, started by both players' Ready (Bob's last). */
    /** [card]: the test cards' catalog's card the decks hold instead of its Forest. */
    private inner class Fixture(val runtime: ServiceRuntime, seam: Boolean = true, card: String = "Forest") {
        val a = account(runtime, "SwitchA"); val b = account(runtime, "SwitchB")
        /** The result of the Ready that starts the game (Bob's). */
        val started: GameResult
        val games = GameService(runtime)
        val game: GameSummary
        val owner: Stream; val recipient: Stream
        /** The deck choices' results, Alice's then Bob's. */
        val selections = mutableListOf<GameResult>()
        init {
            val lobby = LobbyService(runtime); lobby.add(a, AddContact(id(), b.account.accountId))
            game = GameWire.json.decodeFromString(lobby.create(a, CreateInvitation(id(), b.account.accountId, "Switch Spark")).body)
            owner = Stream(games, a, game); recipient = Stream(games, b, game)
            recipient.execute(GameAction.AcceptInvitation)
            for ((actor, stream) in listOf(a to owner, b to recipient)) {
                val service = DeckService(runtime)
                val deck = GameWire.json.decodeFromString<SavedDeck>(service.create(actor, CreateDeck(id(), "Switch deck",
                    listOf(org.asyncttwiz.server.TestPrintings.entry(card, 40)))).body)
                selections += stream.execute(GameAction.SelectDeck(deck.deck.id, deck.deck.revision))
            }
            owner.execute(GameAction.SetStarter(StarterChoice(a.account.accountId)))
            val seats = listOf(a, b).map { p -> PlayerId(p.account.accountId) }.map { p ->
                Seat(p, names.mapIndexed { i, n -> DeckCard(CardId("${p.value}.$i"), Cards.definition(n)) })
            }
            engineSeats = if (seam) { _ -> seats } else null
            try { owner.execute(GameAction.Ready); started = recipient.execute(GameAction.Ready) } finally { engineSeats = null }
        }

        fun snapshot(actor: AccountService.Principal = a): EngineSnapshot = games.head(actor, game.id).engine!!
        fun codec() = EngineJson(EngineJson.decodeDefinitions(snapshot().definitions))
        fun device(actor: AccountService.Principal): GameSession {
            val snapshot = snapshot(actor)
            return GameSession(codec().decodeModel(snapshot.state), snapshot.headId, PlayerId(actor.account.accountId))
        }
        fun attention(actor: AccountService.Principal) = games.head(actor, game.id).summary.attentionHolder
        fun revisions(actor: AccountService.Principal, after: String) = games.revisions(actor, game.id, after).revisions
        /** [owner]'s Game activity summary row of this game (null when never notified). */
        fun activity(owner: AccountService.Principal) = runtime.transaction { c ->
            DSL.using(c, SQLDialect.POSTGRES).selectFrom(ACTIVITY_SUMMARY).where(ACTIVITY_SUMMARY.ACCOUNT_ID.eq(UUID.fromString(owner.account.accountId)))
                .and(ACTIVITY_SUMMARY.GAME_ID.eq(UUID.fromString(game.id))).and(ACTIVITY_SUMMARY.KIND.eq("GAME")).fetchOne()
        }
        val alice get() = PlayerId(a.account.accountId)
        val bob get() = PlayerId(b.account.accountId)

        /** Alice keeps for real at the start (her open mulligan decision); Bob's declaration is then the only move. */
        fun aliceKeeps(): GameSession {
            val alice = device(a)
            assertEquals("accepted", owner.execute(GameAction.Submit(alice.act(id(), Input.Answer(alice.model.engine.pending!!.decision.id,
                Answer.Option("Keep"))))).outcome)
            return alice
        }
    }

    // switchreview #2, I-D56 §7, LOB-022, LOB-024, LOB-072: every revision that isn't an engine action (chat here) carries
    // the summary of `LobbyService.summary(sql, id)` without a viewer, whose move read from the play's starting
    // `PlayState` (its turn's player: the starter, forever). Bob's app takes that summary as the game's (the move chip and
    // game order), and Bob's chat notification says it isn't his move although he alone must act.
    @Test fun `a chat revision keeps whose move it is per viewer and the chat notice knows it is Bob's move`() = withService { runtime ->
        val f = Fixture(runtime)
        f.aliceKeeps()
        // Bob's declaration is open; Alice waits for it: Bob's move for both viewers
        assertEquals(f.b.account.accountId, f.attention(f.b))
        assertEquals(f.b.account.accountId, f.attention(f.a))
        val before = f.games.head(f.b, f.game.id).summary.revision
        assertEquals("accepted", f.owner.execute(GameAction.SendChat("good luck")).outcome)
        val chat = f.revisions(f.b, before).single()
        assertNotNull(chat.chatMessage)
        assertEquals(f.b.account.accountId, chat.summary.attentionHolder, "the chat revision's summary says whose move it is for Bob")
        assertEquals(true, f.activity(f.b)!!.yourMove, "Bob's 'new message' notice says it is his move")
    }

    // switchreview #5, TRN-027, LOB-022, LOB-073, async-play.md §19 choice 7: at the start both players' mulligans are open at
    // once and the device lets the non-starting player declare at once (their move), but the server's next stop only
    // reads the engine's open decision (the starter's): Bob's game list says "Alice's move" and he is never told it is
    // his move until Alice keeps. The start revision Bob's app installs says the same (from the starting board).
    @Test fun `at the start the non-starting player's own mulligan is their move too`() = withService { runtime ->
        val f = Fixture(runtime)
        assertEquals(f.b.account.accountId, f.attention(f.b), "Bob's own mulligan is his move")
        val start = f.revisions(f.b, "0").last { it.play != null }
        assertEquals(f.b.account.accountId, start.summary.attentionHolder,
            "the start revision Bob's app installs says his mulligan is his move")
    }

    // switchreview #3, LOB-075, LOB-076: Alice assumes Bob's keep (he is away). Bob comes back and confirms the same keep:
    // his move is over (Alice's first turn), but nothing recorded it: Bob then got "<Alice> is waiting for you" five
    // minutes later, and the app's own fallback (LOB-076) kept listing the game. Since I-D57 (async-play.md §24, LOB-072)
    // the assumed keep itself leaves nothing waiting for Bob: the notice of Alice's keep is withdrawn, no reminder armed.
    @Test fun `confirming the assumed keep ends Bob's reminder stretch`() = withService { runtime ->
        val f = Fixture(runtime)
        val alice = f.aliceKeeps()
        val bob = f.device(f.b)
        val open = alice.model.engine.pending!!.decision
        assertEquals("accepted", f.owner.execute(GameAction.Submit(alice.act(id(), Input.Answer(open.id, Answer.Option("Keep")), assumed = true))).outcome)
        val notified = requireNotNull(f.activity(f.b)) { "Bob was told of Alice's keep" }
        assertFalse(notified.yourMove!!, "I-D57: Alice plays on the assumed keep: nothing waits for Bob")
        assertNull(notified.reminderMoveAt, "no waiting reminder for Bob")
        // Bob confirms with the same answer at his local head
        assertEquals("accepted", f.recipient.execute(GameAction.Submit(bob.act(id(), Input.Answer(open.id, Answer.Option("Keep"))))).outcome)
        assertEquals(f.a.account.accountId, f.attention(f.b), "now it is Alice's move for Bob")
        val later = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).plusMinutes(10)
        runtime.transaction { c -> ActivityService.remindDue(DSL.using(c, SQLDialect.POSTGRES), later) }
        val row = requireNotNull(f.activity(f.b))
        assertFalse(row.yourMove!!, "Bob's notice no longer says it is his move")
        assertNull(row.reminderActivityId, "no '<Alice> is waiting for you' reminder for a move Bob already made")
    }

    // switchreview #9, I-D51/I-D52, SYS-055, LOB-124: a deck holding a card Automatum can't read never starts a game as a
    // dead card nobody can play: the card catalog's marks (the same parse) keep it from being chosen, so nothing starts.
    @Test fun `a deck with a card the server can't read doesn't start`() = withService { runtime ->
        // Odd Relic's text Automatum can't read: the catalog doesn't mark it supported
        val f = Fixture(runtime, seam = false, card = "Odd Relic")
        assertEquals(listOf("automatumUnsupported", "automatumUnsupported"), f.selections.map { it.reason })
        assertNull(f.games.head(f.a, f.game.id).play, "nothing started")
    }

    // switchreview #8, LOB-111: an agreed result must be recorded as the suggestion's, not as the rules' result, so the notice
    // reads "<Alice> accepted the draw" ("… your win", "… your loss") instead of "Game over · Draw". TRN-065 (owner
    // 2026-11-07): accepting ends the play at once.
    @Test fun `an agreed result is recorded as the suggestion's, not the rules'`() = withService { runtime ->
        val f = Fixture(runtime)
        val draw = arcanum.engine.setup.GameResult.Draw
        val alice = f.device(f.a)
        val suggestion = org.asyncttwiz.protocol.game.ResultSuggestion(f.alice, draw)
        assertEquals("accepted", f.owner.execute(GameAction.Submit(alice.act(id(), null,
            listOf(org.asyncttwiz.protocol.game.TableChange.Suggestion(null, suggestion))))).outcome)
        val bob = f.device(f.b)
        assertEquals("accepted", f.recipient.execute(GameAction.Submit(bob.act(id(), Input.AgreedResult(draw),
            listOf(org.asyncttwiz.protocol.game.TableChange.Suggestion(suggestion, null))))).outcome)
        assertEquals(GameLifecycle.LOBBY, f.games.head(f.a, f.game.id).summary.lifecycle, "the play ended")
        val plays = org.asyncttwiz.server.generated.tables.GamePlay.GAME_PLAY
        val recorded = runtime.transaction { c -> DSL.using(c, SQLDialect.POSTGRES).select(plays.PAYLOAD).from(plays)
            .where(plays.GAME_ID.eq(UUID.fromString(f.game.id))).and(plays.ENDED_REQUEST_ID.isNotNull).fetchSingle().value1()!! }
            .let { GameWire.json.decodeFromString<PlayState>(it).board.resultSuggestion!! }
        assertFalse(recorded.byRules, "Alice suggested the draw and Bob agreed: not the rules' result")
        assertEquals(f.a.account.accountId, recorded.author)
        assertEquals(SuggestedResult.DRAW, recorded.outcome)
    }

    // switchreview #12 (decided for the owner 2026-11-04, to confirm), LOB-072, HIS-100: an undo request is the other
    // player's move: listed and notified like one ("Your move"), so a request made while they are away doesn't freeze the
    // game unseen; the requester waits meanwhile, and cancelling it ends the other player's move again.
    @Test fun `an undo request is the other player's move until it is cancelled`() = withService { runtime ->
        val f = Fixture(runtime)
        f.aliceKeeps()
        val target = f.snapshot().headId
        val bob = f.device(f.b)
        assertEquals("accepted", f.recipient.execute(GameAction.Submit(bob.act(id(), Input.Answer(bob.model.engine.pending!!.decision.id,
            Answer.Option("Keep"))))).outcome)
        assertEquals(f.a.account.accountId, f.attention(f.b), "Alice's first turn")
        assertEquals("requestNeeded", f.owner.execute(GameAction.EngineRewind(target)).reason)
        assertEquals("accepted", f.owner.execute(GameAction.EngineRequestRewind(target, "misclick")).outcome)
        val asked = requireNotNull(f.activity(f.b)) { "Bob is told of the request" }
        assertTrue(asked.yourMove!!, "answering the request is Bob's move")
        assertNotNull(asked.reminderMoveAt, "his waiting reminder is armed")
        assertEquals(f.b.account.accountId, f.attention(f.b))
        assertEquals(f.b.account.accountId, f.attention(f.a), "Alice waits for Bob's answer")
        assertEquals("accepted", f.owner.execute(GameAction.EngineCancelRewind).outcome)
        assertFalse(f.activity(f.b)!!.yourMove!!, "the cancelled request is no longer Bob's move")
        assertEquals(f.a.account.accountId, f.attention(f.b))
    }

    // switchreview #7, LOB-114 (also async-play.md §23, found while building): Alice keeps while Bob's opening is still
    // Mulliganing; Bob's notice should read "<Alice> is ready". The engine path never sets it.
    @Test fun `Bob's notice says Alice is ready when she keeps while he is still Mulliganing`() = withService { runtime ->
        val f = Fixture(runtime)
        f.aliceKeeps()
        val row = requireNotNull(f.activity(f.b)) { "Bob is notified of Alice's keep (his next stop is new)" }
        assertTrue(row.opponentReady!!, "LOB-114: '<Alice> is ready'")
    }
}
