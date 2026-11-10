package org.asyncttwiz.server.game

import arcanum.api.CardType
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.asyncttwiz.protocol.*
import org.asyncttwiz.protocol.game.EngineJson
import org.asyncttwiz.protocol.game.OutsideCards
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.accounts.AccountService
import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.FreshDatabase
import org.asyncttwiz.server.decks.DeckService
import org.asyncttwiz.server.lobby.LobbyService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * LOB-117, CR 309.2: a Ready brings its player's dungeon cards outside the game; the server keeps them with the prepared deck,
 * refuses a Ready whose deck ventures without them (or one bringing a card that isn't a dungeon), and starts the engine game with
 * them among the player's cards outside the game. Invented cards only.
 */
class DungeonStartTest {
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
    // The test cards' catalog: a venture card, a Forest, and its two dungeons (a plain one and an Undercity)
    private val map = "Dungeon Torch Map"
    private val forest = "Forest"
    private val dungeons get() = org.asyncttwiz.server.TestPrintings.catalog.dungeons
    private val crypt get() = dungeons.single { it.definition.name == "Dungeon Test Crypt" }
    private val sunken get() = dungeons.single { it.definition.name == "Dungeon Test Sunken City" }

    private inner class Fixture(runtime: ServiceRuntime, tag: String) {
        val a = account(runtime, "DungeonA$tag"); val b = account(runtime, "DungeonB$tag")
        val games = GameService(runtime)
        val game: GameSummary
        val hello = GameHello(id(), id())
        private val connection = UUID.randomUUID()
        private val streams = mutableMapOf<AccountService.Principal, GameStreamReady>()
        init {
            val lobby = LobbyService(runtime); lobby.add(a, AddContact(id(), b.account.accountId))
            game = GameWire.json.decodeFromString(lobby.create(a, CreateInvitation(id(), b.account.accountId, "Dungeon game")).body)
            execute(b, GameAction.AcceptInvitation)
            for ((actor, deck) in listOf(a to listOf(map to 40), b to listOf(forest to 40))) {
                val service = DeckService(runtime)
                val saved = GameWire.json.decodeFromString<SavedDeck>(service.create(actor, CreateDeck(id(), "Dungeon deck",
                    deck.map { (c, n) -> org.asyncttwiz.server.TestPrintings.entry(c, n) })).body)
                assertEquals("accepted", execute(actor, GameAction.SelectDeck(saved.deck.id, saved.deck.revision)).outcome)
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

    // LOB-117 (owner 2026-11-10: card data from the server's catalog): the server brings its catalog's dungeons with the Ready
    // of a deck that ventures; a deck needs the ones its cards ask for (a plain venture can't enter the Undercity)
    @Test fun `a deck that ventures needs its dungeons, and its Ready brings the catalog's`() = withService { runtime ->
        val f = Fixture(runtime, "n")
        assertEquals(listOf("Dungeon Test Crypt", "Dungeon Test Sunken City"), dungeons.map { it.definition.name })
        val preparation = f.games.head(f.a, f.game.id).preparation!!
        assertTrue(EngineDecks.ventures(preparation, f.a.account.accountId))
        assertFalse(EngineDecks.ventures(preparation, f.b.account.accountId))
        assertFalse(EngineDecks.outsideProvided(preparation, f.a.account.accountId, emptyList()))
        assertFalse(EngineDecks.outsideProvided(preparation, f.a.account.accountId, listOf(sunken)), "only the Undercity")
        assertTrue(EngineDecks.outsideProvided(preparation, f.a.account.accountId, listOf(crypt)))
        assertEquals("accepted", f.execute(f.a, GameAction.Ready).outcome)
        val prepared = f.games.head(f.a, f.game.id).preparation!!.players.single { it.accountId == f.a.account.accountId }.deck!!
        assertEquals(dungeons.map { it.digest }, prepared.outside)
    }

    @Test fun `the game starts with the dungeons outside it`() = withService { runtime ->
        val f = Fixture(runtime, "s")
        assertEquals("accepted", f.execute(f.a, GameAction.Ready).outcome)
        val prepared = f.games.head(f.a, f.game.id).preparation!!.players.single { it.accountId == f.a.account.accountId }.deck!!
        assertEquals(listOf(crypt.digest, sunken.digest), prepared.outside)
        // Bob's deck doesn't venture: his Ready brings nothing and needs nothing
        assertEquals("accepted", f.execute(f.b, GameAction.Ready).outcome)
        val head = f.games.head(f.a, f.game.id)
        assertEquals(GameLifecycle.ACTIVE, head.summary.lifecycle)
        val snapshot = head.engine!!
        val model = EngineJson(EngineJson.decodeDefinitions(snapshot.definitions)).decodeModel(snapshot.state)
        val alice = model.engine.player(arcanum.engine.state.PlayerId(f.a.account.accountId))
        val outside = alice.outside.map { model.engine.obj(it)!! }
        assertEquals(listOf("Dungeon Test Crypt", "Dungeon Test Sunken City"), outside.map { it.definition!!.faces.single().name })
        assertTrue(outside.all { CardType.Dungeon in it.definition!!.faces.single().types && it.definition!!.faces.single().rooms.isNotEmpty() })
        assertEquals(40, alice.library.size + alice.hand.size)
        assertTrue(model.engine.player(arcanum.engine.state.PlayerId(f.b.account.accountId)).outside.isEmpty())
        // The play's card data holds the dungeons' (for the table and the choice that brings one in)
        assertTrue(head.play!!.definitions.map { it.digest }.containsAll(listOf(crypt.digest, sunken.digest)))
    }
}
