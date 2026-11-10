package org.asyncttwiz.server.lobby

import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.input.Input
import arcanum.engine.setup.DeckCard
import arcanum.engine.setup.Seat
import arcanum.engine.state.CardId
import arcanum.engine.state.PlayerId
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.asyncttwiz.protocol.*
import org.asyncttwiz.protocol.game.EngineJson
import org.asyncttwiz.protocol.game.GameSession
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.accounts.AccountService
import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.FreshDatabase
import org.asyncttwiz.server.decks.DeckService
import org.asyncttwiz.server.game.GameService
import org.asyncttwiz.server.game.concession
import org.asyncttwiz.server.game.engineSeats
import org.jooq.ExecuteContext
import org.jooq.ExecuteListener
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Switch-review #11 (owner, 2026-11-04): the games list is read as lightweight summaries, "changed since" a token from
 * the previous read, in a constant number of queries; a game in the list (not the open one) gets summary heads and
 * revisions without its play or engine state.
 */
class GameListChangesTest {
    private fun id() = UUID.randomUUID().toString()
    private fun account(runtime: ServiceRuntime, name: String): AccountService.Principal {
        val accounts = AccountService(runtime); accounts.register(name, "$name@example.invalid", "synthetic password")
        return accounts.authenticate(accounts.login("$name@example.invalid", "synthetic password")!!.token)!!
    }
    private class Stream(val games: GameService, val actor: AccountService.Principal, val game: GameSummary) {
        val hello = GameHello(UUID.randomUUID().toString(), UUID.randomUUID().toString())
        val connection = UUID.randomUUID()
        val stream = games.open(actor, hello, connection, game.id, UUID.randomUUID().toString())
        fun execute(action: GameAction): GameResult {
            val scope = if (action is GameAction.Submit || action.isEngine || action is GameAction.SendChat)
                GameScope(game.id, playId = games.head(actor, game.id).summary.playId)
                else GameScope(game.id, game.preparationRound, if (action == GameAction.AcceptInvitation || action == GameAction.DeclineInvitation) game.invitationId else null)
            val c = GameCommand(UUID.randomUUID().toString(), scope, action, stream.streamId, stream.generation)
            games.admit(actor, hello, connection, c); assertTrue(games.executeOne())
            return games.results(actor, game.id, listOf(c.requestId)).statuses.single().result!!
        }
    }

    private fun <T> withService(test: (ServiceRuntime) -> T): T = FreshDatabase().start().use { db ->
        FoundationMigrations.migrate(db.settings)
        ServiceRuntime(db.settings).use { runtime ->
            runtime.start(); val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
            while (!runtime.ready()) { assertTrue(System.nanoTime() < end); Thread.sleep(20) }
            test(runtime)
        }
    }

    private val names = List(16) { if (it % 2 == 0) "Forest" else "Mountain" } +
        List(24) { listOf("Elvish Seer", "Storm Spark", "Thicketwood Elves")[it % 3] }

    /** Alice and Bob with [count] accepted lobby games of Alice's invitations. */
    private inner class Players(val runtime: ServiceRuntime, count: Int) {
        val a = account(runtime, "ListA${id().take(6)}"); val b = account(runtime, "ListB${id().take(6)}")
        val lobby = LobbyService(runtime)
        val games = GameService(runtime)
        val created: List<GameSummary>
        val streams = mutableMapOf<String, Pair<Stream, Stream>>()
        init {
            lobby.add(a, AddContact(id(), b.account.accountId))
            created = List(count) { n ->
                val game = GameWire.json.decodeFromString<GameSummary>(lobby.create(a, CreateInvitation(id(), b.account.accountId, "Game $n")).body)
                val pair = Stream(games, a, game) to Stream(games, b, game)
                pair.second.execute(GameAction.AcceptInvitation)
                streams[game.id] = pair
                game
            }
        }
        fun list(actor: AccountService.Principal, since: String? = null, invitations: Boolean = false, queries: AtomicInteger? = null): GameList {
            val listener = queries?.let { counter -> object : ExecuteListener { override fun executeStart(ctx: ExecuteContext) { counter.incrementAndGet() } } }
            val result = lobby.list(actor, null, invitations, since, listener)
            assertEquals(200, result.status, result.body)
            return GameWire.json.decodeFromString(result.body)
        }
        /** Starts [game] on engine state (both decks, Alice first, both Ready). */
        fun start(game: GameSummary) {
            val (owner, recipient) = streams.getValue(game.id)
            for ((actor, stream) in listOf(a to owner, b to recipient)) {
                val service = DeckService(runtime)
                val saved = service.create(actor, CreateDeck(id(), "List deck", listOf(org.asyncttwiz.server.TestPrintings.entry("Forest", 40))))
                val deck = GameWire.json.decodeFromString<SavedDeck>(saved.body)
                stream.execute(GameAction.SelectDeck(deck.deck.id, deck.deck.revision))
            }
            owner.execute(GameAction.SetStarter(StarterChoice(a.account.accountId)))
            val seats = listOf(a, b).map { p -> PlayerId(p.account.accountId) }.map { p ->
                Seat(p, names.mapIndexed { i, n -> DeckCard(CardId("${p.value}.$i"), Cards.definition(n)) })
            }
            engineSeats = { _ -> seats }
            try { owner.execute(GameAction.Ready); assertEquals("accepted", recipient.execute(GameAction.Ready).outcome) } finally { engineSeats = null }
        }
        fun device(actor: AccountService.Principal, game: GameSummary): GameSession {
            val snapshot = games.head(actor, game.id).engine!!
            val codec = EngineJson(EngineJson.decodeDefinitions(snapshot.definitions))
            return GameSession(codec.decodeModel(snapshot.state), snapshot.headId, PlayerId(actor.account.accountId))
        }
    }

    // Switch-review #11: a list read since a token answers only the games changed after it, each read answers the token
    // for the next one, and the summaries take the same number of queries for 1 game as for 6 (no query per game)
    @Test fun `changed since answers only changed games in a constant number of queries`() = withService { runtime ->
        val one = Players(runtime, 1)
        val few = AtomicInteger(); one.list(one.a, queries = few)
        val p = Players(runtime, 6)
        val many = AtomicInteger()
        val full = p.list(p.a, queries = many)
        assertEquals(p.created.map { it.id }.toSet(), full.games.map { it.id }.toSet())
        assertEquals(few.get(), many.get(), "the same queries for 1 game as for 6")
        val token = requireNotNull(full.token)
        assertTrue(p.list(p.a, since = token).games.isEmpty(), "nothing changed")
        // One game changes (Alice picks the starter): only it, with its new revision
        val changed = p.created[2]
        p.streams.getValue(changed.id).first.execute(GameAction.SetStarter(StarterChoice(p.a.account.accountId)))
        val delta = p.list(p.a, since = token)
        assertEquals(listOf(changed.id), delta.games.map { it.id })
        assertTrue(GameWire.ordinal(delta.games.single().revision) > GameWire.ordinal(changed.revision))
        assertTrue(delta.removed.isEmpty())
        // The same for Bob, and nothing new since the delta's own token
        assertEquals(listOf(changed.id), p.list(p.b, since = token).games.map { it.id })
        assertTrue(p.list(p.a, since = requireNotNull(delta.token)).games.isEmpty())
        // A bad token is refused, not read as everything
        assertEquals(400, p.lobby.list(p.a, null, false, "x").status)
        println("games list of 6 games: full ${GameWire.json.encodeToString(full).length} bytes, " +
            "delta of 1 ${GameWire.json.encodeToString(delta).length} bytes, empty delta " +
            "${GameWire.json.encodeToString(p.list(p.a, since = delta.token)).length} bytes; ${many.get()} queries per read")
    }

    // Switch-review #11: an engine play's moves, an undo and its end each show in the next read since: whose move it is
    // per viewer, and the ended game as removed from the list
    @Test fun `engine moves undo and results show in the changes`() = withService { runtime ->
        val p = Players(runtime, 2)
        val game = p.created[0]
        p.start(game)
        var token: String = requireNotNull(p.list(p.a).token)
        fun since(actor: AccountService.Principal = p.a): GameList = p.list(actor, since = token)
        // Alice keeps (her move at the start): the game changes; whose move it is now comes from each viewer's seat
        val alice = p.device(p.a, game)
        val start = alice.head
        val (owner, _) = p.streams.getValue(game.id)
        assertEquals("accepted", owner.execute(GameAction.Submit(alice.act(id(), Input.Answer(alice.model.engine.pending!!.decision.id,
            Answer.Option("Keep"))))).outcome)
        val kept = since()
        assertEquals(listOf(game.id), kept.games.map { it.id })
        assertEquals(p.games.head(p.a, game.id).summary.attentionHolder, kept.games.single().attentionHolder)
        assertEquals(p.games.head(p.b, game.id).summary.attentionHolder, since(p.b).games.single().attentionHolder)
        token = kept.token!!
        // Her undo (a rewind, a new line) is a change too
        assertEquals("accepted", owner.execute(GameAction.EngineRewind(start)).outcome)
        assertEquals(listOf(game.id), since().games.map { it.id })
        token = since().token!!
        // A concession ends the play: back in the lobby for the next round, still listed, with no play
        assertEquals("accepted", owner.execute(p.games.concession(p.a, game.id)).outcome)
        val ended = since()
        assertEquals(listOf(game.id), ended.games.map { it.id } + ended.removed)
        assertNotEquals(GameLifecycle.ACTIVE, ended.games.singleOrNull()?.lifecycle)
        // The other game never changed
        assertTrue(ended.games.none { it.id == p.created[1].id })
    }

    // Switch-review #11: a declined invitation leaves the recipient's invitations as removed
    @Test fun `a game leaving a list is answered as removed`() = withService { runtime ->
        val p = Players(runtime, 0)
        val game = GameWire.json.decodeFromString<GameSummary>(p.lobby.create(p.a, CreateInvitation(id(), p.b.account.accountId, "Declined")).body)
        val invites = p.list(p.b, invitations = true)
        assertEquals(listOf(game.id), invites.games.map { it.id })
        Stream(p.games, p.b, game).execute(GameAction.DeclineInvitation)
        val delta = p.list(p.b, since = invites.token, invitations = true)
        assertTrue(delta.games.isEmpty())
        assertEquals(listOf(game.id), delta.removed)
    }

    // Switch-review #11: a game in the list (not the open one) has summary heads, and its revisions carry no play or
    // engine state; the open one's carry both
    @Test fun `a game in the list gets summaries only`() = withService { runtime ->
        val p = Players(runtime, 1)
        val game = p.created[0]
        p.start(game)
        val summary = p.games.subscriptionHead(p.b, game.id, presence = true, engine = false)
        assertNull(summary.play); assertNull(summary.engine)
        assertEquals(GameLifecycle.ACTIVE, summary.summary.lifecycle)
        val full = p.games.subscriptionHead(p.b, game.id, presence = true)
        assertNotNull(full.play); assertNotNull(full.engine)
        println("head of a started game: full ${GameWire.encode(full).length} bytes, summary ${GameWire.encode(summary).length} bytes")
        val alice = p.device(p.a, game)
        val (owner, _) = p.streams.getValue(game.id)
        owner.execute(GameAction.Submit(alice.act(id(), Input.Answer(alice.model.engine.pending!!.decision.id, Answer.Option("Keep")))))
        val revision = p.games.revisions(p.b, game.id, summary.revision).revisions.single()
        assertNotNull(revision.engineEvent)
        val listed = p.games.publicationState(p.b, revision, engine = false) as GameRevision
        assertNull(listed.engineEvent); assertNull(listed.engine); assertNull(listed.play)
        assertEquals(revision.summary, listed.summary)
        assertEquals(revision, p.games.publicationState(p.b, revision))
    }
}
