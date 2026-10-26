package org.asyncttwiz.android

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.UUID
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import kotlin.test.*

class PodChallengeLobbyTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun id() = UUID.randomUUID().toString()
    private val a = AccountIdentity(id(), "A"); private val b = AccountIdentity(id(), "B")
    private val pod = id()
    private val game = GameSummary(id(), "Limited game", "2026-10-26T00:00:00Z", a, b, id(), id(), InvitationState.PENDING,
        podId = pod, podName = "Limited pod")
    private val definitions = BasicLand.entries.map { land ->
        val card = CardDefinition(land.cardName, listOf(DefinitionFace(id(), land.cardName, typeLine = "Basic Land — ${land.cardName}")))
        SubmittedDefinition(DeckJson.digest(card), card)
    }
    private class Worker : AbstractExecutorService() {
        val queue = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) { queue.add(command) }
        fun drain() { while (queue.isNotEmpty()) queue.removeFirst().run() }
        override fun shutdown() = Unit
        override fun shutdownNow() = mutableListOf<Runnable>()
        override fun isShutdown() = false
        override fun isTerminated() = false
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = true
    }
    private class Wire : GameConnection {
        lateinit var listener: GameConnection.Listener
        val frames = mutableListOf<GameClientMessage>()
        override fun connect(token: String, listener: GameConnection.Listener) { this.listener = listener }
        override fun send(message: GameClientMessage): Boolean { frames.add(message); return true }
        override fun disconnect() = Unit
        override fun close() = Unit
    }
    private inner class Fixture : AutoCloseable {
        val worker = Worker(); val wire = Wire(); val calls = mutableListOf<String>()
        val challenges = mutableListOf<ChallengeFromPod>(); val submissions = mutableListOf<SubmitDefinitions>()
        var fail = false; var resolutions = 0; var opens = 0; var resolve: () -> List<SubmittedDefinition> = { resolutions++; definitions }
        val decks = object : Decks {
            override suspend fun submit(token: String, value: SubmitDefinitions): DefinitionsAccepted {
                calls.add("definitions"); submissions.add(value); return DefinitionsAccepted(value.definitions.map { it.digest })
            }
            override suspend fun list(token: String, after: String?) = DeckList(emptyList())
            override suspend fun read(token: String, id: String): SavedDeck = error("No saved decks")
            override suspend fun definition(token: String, id: String, digest: String): SubmittedDefinition = error("No saved decks")
            override suspend fun create(token: String, value: CreateDeck): SavedDeck = error("No saved decks")
            override suspend fun update(token: String, id: String, value: UpdateDeck): SavedDeck = error("No saved decks")
            override suspend fun delete(token: String, id: String, value: DeleteDeck): DeckDeleted = error("No saved decks")
            override fun close() = Unit
        }
        val api = object : Lobbies {
            override suspend fun challengeFromPod(token: String, podId: String, input: ChallengeFromPod): GameSummary {
                assertEquals(pod, podId); calls.add("challenge"); challenges.add(input)
                if (fail) throw LobbyFailure("invalid_response")
                return game
            }
            override suspend fun contacts(token: String, after: String?) = ContactList(emptyList())
            override suspend fun search(token: String, query: String) = UserSearch(emptyList())
            override suspend fun add(token: String, input: AddContact) = b
            override suspend fun create(token: String, input: CreateInvitation): GameSummary = error("Not a contact invitation")
            override suspend fun games(token: String, invitations: Boolean, after: String?) = GameList(if (invitations) emptyList() else listOf(game))
            override suspend fun head(token: String, id: String) = game
            override fun close() = Unit
        }
        val controller = LobbyController(api, { "token" }, worker, { it() }, openGame = { opens++ }, gameConnection = wire,
            accountId = { a.accountId }, deckApi = decks, basicLandDefinitions = { resolve() })
        fun challenge() { controller.challengeFromPod(pod, b.accountId, game.gameName); worker.drain() }
        fun head(preparation: LobbyPreparation) {
            wire.listener.opened(); wire.listener.message(GameWelcome())
            val open = wire.frames.filterIsInstance<OpenGameStream>().last()
            wire.listener.message(GameStreamReady(game.id, open.streamId, "1"))
            wire.listener.message(GameHead(game.id, "0", game, preparation = preparation))
        }
        override fun close() { controller.close(); worker.drain() }
    }

    @Test fun resolvesAndSubmitsBeforeChallengeThenOpensOrdinaryLobbyAndRetriesExactOriginal() = Fixture().use { f ->
        f.fail = true; f.challenge()
        assertEquals(listOf("definitions", "challenge"), f.calls)
        assertNull(f.controller.state.selected); assertNotNull(f.controller.state.error)
        f.fail = false; f.challenge()
        assertEquals(1, f.resolutions); assertEquals(f.challenges[0], f.challenges[1]); assertEquals(f.submissions[0], f.submissions[1])
        assertEquals(definitions, f.challenges.last().basicDefinitions)
        assertEquals(game.id, f.controller.state.selected); assertEquals(1, f.opens)
        f.controller.clear(); f.challenge()
        assertEquals(2, f.resolutions); assertNotEquals(f.challenges[0].requestId, f.challenges.last().requestId)
    }

    @Test fun missingBasicsAndClearedAccountNeverCreateChallenges() = Fixture().use { f ->
        f.resolve = { throw LobbyFailure("basic_definitions_unavailable") }
        f.challenge(); assertTrue(f.calls.isEmpty()); assertTrue(f.controller.state.error!!.contains("M21"))
        f.resolve = { definitions }
        f.controller.challengeFromPod(pod, b.accountId, game.gameName)
        f.controller.clear(); f.worker.drain()
        assertTrue(f.calls.isEmpty()); assertNull(f.controller.state.selected)
    }

    @Test fun basicsUseServerCommandOwnReadyClearsAndSavedPickerIsUnavailable() = Fixture().use { f ->
        f.challenge()
        val selection = id()
        val deck = PreparedDeck(id(), pod, "1", "Limited pod", null, selection,
            listOf(PreparedEntry(definitions[0].digest, SavedDeckSection.MAINBOARD, "0", 40, BasicLand.PLAINS)),
            PodGamePreparation.basicDefinitions(definitions)!!, "40")
        val preparation = LobbyPreparation(game.id, game.preparationRound, listOf(PlayerPreparation(a.accountId, true, deck),
            PlayerPreparation(b.accountId, true, deck.copy(id = id()))), definitions, podRules = PodGameRules(1))
        f.head(preparation)
        f.controller.chooseDeck(); assertNull(f.controller.state.picker)
        assertFalse(f.controller.changePodBasic(BasicLand.FOREST, -1))
        assertTrue(f.controller.changePodBasic(BasicLand.PLAINS, -1))
        val command = f.wire.frames.filterIsInstance<GameCommand>().single()
        assertIs<GameAction.SetPodGameBasics>(command.action); assertTrue(GameWire.valid(command))
        assertEquals(39, (command.action as GameAction.SetPodGameBasics).basics.plains)
        assertEquals(preparation, f.controller.state.acceptedPreparation)
        assertFalse(f.controller.state.preparation!!.players[0].ready); assertTrue(f.controller.state.preparation!!.players[1].ready)
        assertTrue(readyReason(f.controller.state.preparation, a.accountId)!!.contains("40"))
        f.wire.listener.message(GameResult(command.requestId, command.scope, "rejected", "invalidBasics", "1"))
        assertEquals(preparation, f.controller.state.preparation)
        f.wire.listener.disconnected(); assertFalse(f.controller.changePodBasic(BasicLand.ISLAND, 1))
    }

    @Test fun fixedM21EnglishLowestCollectorNumberIgnoresOtherSetsPromosAndCatalogBuildDate() {
        val repository = CatalogRepository(temporary.newFolder("catalog")) { JdbcSqliteDriver("jdbc:sqlite:${it.absolutePath}") }
        val chosen = mutableListOf<String>()
        val records = BasicLand.entries.zip(listOf("W", "U", "B", "R", "G")).flatMap { (land, mana) ->
            fun printing(set: String, number: String, language: String = "English", promo: Boolean = false): CatalogPrinting {
                val uuid = id()
                return CatalogPrinting(uuid, land.cardName, set, number, language, "", id(), false, emptyList(),
                    """{"name":"${land.cardName}","type":"Basic Land — ${land.cardName}","text":"({T}: Add {$mana}.)","isPromo":$promo,"availability":["paper"]}""")
            }
            val regular = printing("M21", "309").also { chosen.add(it.id) }
            listOf(printing("NEW", "1"), printing("M21", "1", promo = true), printing("M21", "2", language = "German"),
                printing("M21", "310"), regular)
        }
        repository.stage { index -> index.addBatch(records); CatalogMetadata("2026-10-26", "5.3.0") }
        val first = podBasicDefinitions(repository)
        assertEquals(chosen, first.map { it.definition.printingId }); assertNotNull(PodGamePreparation.basicDefinitions(first))
        assertEquals(listOf("W", "U", "B", "R", "G").map { "({T}: Add {$it}.)" }, first.map { it.definition.faces.single().rulesText })
        repository.stage { index -> index.addBatch(records.reversed()); CatalogMetadata("2026-10-27", "5.3.0") }
        assertEquals(first, podBasicDefinitions(repository))
    }
}
