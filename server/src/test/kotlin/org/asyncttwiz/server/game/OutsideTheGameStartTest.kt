package org.asyncttwiz.server.game

import java.util.UUID
import java.util.concurrent.TimeUnit
import org.asyncttwiz.protocol.*
import org.asyncttwiz.protocol.game.EngineJson
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.accounts.AccountService
import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.FreshDatabase
import org.asyncttwiz.server.decks.DeckService
import org.asyncttwiz.server.lobby.LobbyService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * LOB-141: in a constructed game a player's cards outside the game (what wishes, learn and the like can fetch) are their
 * deck's Side and Maybe, never in the library; a Maybe card the server can't read keeps the deck out of games as a Side
 * card does (LOB-118). Invented cards only.
 */
class OutsideTheGameStartTest {
    private fun id() = UUID.randomUUID().toString()
    private fun account(runtime: ServiceRuntime, name: String): AccountService.Principal {
        val accounts = AccountService(runtime); accounts.register(name, "$name@example.invalid", "synthetic password")
        return accounts.authenticate(accounts.login("$name@example.invalid", "synthetic password")!!.token)!!
    }
    private fun <T> withService(test: (ServiceRuntime) -> T): T = FreshDatabase().start().use { db ->
        FoundationMigrations.migrate(db.settings)
        ServiceRuntime(db.settings).use { runtime ->
            runtime.start(); val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
            while (!runtime.ready()) { assertTrue(System.nanoTime() < end); Thread.sleep(20) }
            test(runtime)
        }
    }
    // Cards of the test cards' catalog, by name
    private val forest = "Forest"
    private val wish = "Ember Wish"
    private val hound = "Cinder Hound"
    private val herald = "Ember Herald"

    /** [selected]: what Alice's deck choice comes to (accepted, or the reason it is refused). */
    private inner class Fixture(runtime: ServiceRuntime, tag: String, aliceDeck: List<Pair<SavedDeckSection, Pair<String, Int>>>,
        selected: String = "accepted") {
        val a = account(runtime, "OutsideA$tag"); val b = account(runtime, "OutsideB$tag")
        val games = GameService(runtime)
        val game: GameSummary
        val hello = GameHello(id(), id())
        private val connection = UUID.randomUUID()
        private val streams = mutableMapOf<AccountService.Principal, GameStreamReady>()
        init {
            val lobby = LobbyService(runtime); lobby.add(a, AddContact(id(), b.account.accountId))
            game = GameWire.json.decodeFromString(lobby.create(a, CreateInvitation(id(), b.account.accountId, "Outside game")).body)
            execute(b, GameAction.AcceptInvitation)
            val bobDeck = listOf(SavedDeckSection.MAINBOARD to (forest to 40))
            for ((actor, deck) in listOf(a to aliceDeck, b to bobDeck)) {
                val saved = GameWire.json.decodeFromString<SavedDeck>(DeckService(runtime).create(actor, CreateDeck(id(), "Outside deck",
                    deck.map { (section, entry) -> org.asyncttwiz.server.TestPrintings.entry(entry.first, entry.second, section) })).body)
                val result = execute(actor, GameAction.SelectDeck(saved.deck.id, saved.deck.revision))
                assertEquals(if (actor == a) selected else "accepted", if (result.outcome == "accepted") result.outcome else result.reason)
            }
            assertEquals("accepted", execute(a, GameAction.SetStarter(StarterChoice(a.account.accountId))).outcome)
        }
        fun execute(actor: AccountService.Principal, action: GameAction): GameResult {
            val stream = streams.getOrPut(actor) { games.open(actor, hello, connection, game.id, id()) }
            val current = games.head(actor, game.id).summary
            val scope = GameScope(game.id, current.preparationRound, if (action == GameAction.AcceptInvitation) current.invitationId else null)
            val command = GameCommand(id(), scope, action, stream.streamId, stream.generation)
            games.admit(actor, hello, connection, command)
            assertTrue(games.executeOne())
            return games.results(actor, game.id, listOf(command.requestId)).statuses.single().result!!
        }
    }

    @Test fun `a constructed game's cards outside the game are the deck's Side and Maybe`() = withService { runtime ->
        val f = Fixture(runtime, "s", listOf(SavedDeckSection.MAINBOARD to (forest to 36), SavedDeckSection.MAINBOARD to (wish to 4),
            SavedDeckSection.SIDEBOARD to (hound to 2), SavedDeckSection.MAYBEBOARD to (herald to 1)))
        assertEquals("accepted", f.execute(f.a, GameAction.Ready).outcome)
        assertEquals("accepted", f.execute(f.b, GameAction.Ready).outcome)
        val head = f.games.head(f.a, f.game.id)
        assertEquals(GameLifecycle.ACTIVE, head.summary.lifecycle)
        val snapshot = head.engine!!
        val model = EngineJson(EngineJson.decodeDefinitions(snapshot.definitions)).decodeModel(snapshot.state)
        val alice = model.engine.player(arcanum.engine.state.PlayerId(f.a.account.accountId))
        fun name(id: arcanum.engine.state.ObjectId) = model.engine.obj(id)!!.definition!!.faces.single().name
        assertEquals(listOf(hound, hound, herald), alice.outside.map(::name))
        assertEquals(40, alice.library.size + alice.hand.size)
        assertTrue((alice.library + alice.hand).map(::name).none { it == herald || it == hound })
        assertTrue(model.engine.player(arcanum.engine.state.PlayerId(f.b.account.accountId)).outside.isEmpty())
        // The play's card data holds the Maybe card's (for the choice that fetches it and the hand it goes to)
        assertTrue(head.play!!.definitions.any { it.definition.name == herald })
        // The wish reads as Automatum plays it: a choice among its owner's cards outside the game
        assertNotNull(org.asyncttwiz.protocol.game.GameCards.parse(org.asyncttwiz.server.TestPrintings.definition(wish)).definition)
    }

    // LOB-141, LOB-118: a Maybe card Automatum can't play (the catalog's marks, LOB-124) keeps the deck out of the game
    @Test fun `a Maybe card Automatum can't play keeps the deck out of the game`() = withService { runtime ->
        val f = Fixture(runtime, "u", listOf(SavedDeckSection.MAINBOARD to (forest to 40), SavedDeckSection.MAYBEBOARD to ("Odd Relic" to 1)),
            selected = "automatumUnsupported")
        assertEquals("deckRequired", f.execute(f.a, GameAction.Ready).reason)
        assertEquals(GameLifecycle.LOBBY, f.games.head(f.a, f.game.id).summary.lifecycle)
    }
}
