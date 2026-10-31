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
        val challengedPods = mutableListOf<String>()
        var failure: Exception? = null
        var submissionFailure: Exception? = null
        var fail = false; var resolutions = 0; var opens = 0; var resolve: () -> List<SubmittedDefinition> = { resolutions++; definitions }
        val decks = object : Decks {
            override suspend fun submit(token: String, value: SubmitDefinitions): DefinitionsAccepted {
                calls.add("definitions"); submissions.add(value)
                submissionFailure?.let { throw it }
                return DefinitionsAccepted(value.definitions.map { it.digest })
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
                challengedPods.add(podId); calls.add("challenge"); challenges.add(input)
                failure?.let { throw it }
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
            wire.listener.opened(); wire.listener.message(GameWelcome(gameplayRulesVersion = CURRENT_GAMEPLAY_RULES_VERSION))
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

    @Test fun freshSuggestedNameRetriesRetainedPodChallengeAndBothRequestIds() = Fixture().use { f ->
        f.fail = true; f.challenge()
        assertEquals(game.gameName, f.controller.state.retryPodChallengeName)
        f.fail = false
        f.controller.challengeFromPod(pod, b.accountId, "A fresh suggestion"); f.worker.drain()
        assertEquals(2, f.challenges.size)
        assertEquals(f.challenges.first(), f.challenges.last())
        assertEquals(f.submissions.first(), f.submissions.last())
        assertEquals(1, f.resolutions)
        assertNull(f.controller.state.retryPodChallengeName)
    }

    @Test fun retainedRetryIsAvailableAcrossPodsAndTerminalFailureUnblocksDifferentChallenge() = Fixture().use { f ->
        f.fail = true; f.challenge()
        val otherPod = id(); val opponent = id()
        f.controller.challengeFromPod(otherPod, opponent, "Another game"); f.worker.drain()
        assertEquals(1, f.challenges.size)
        assertNotNull(f.controller.state.retryPodChallengeName)
        f.failure = LobbyFailure("not_found")
        f.controller.retryPodChallenge(); f.worker.drain()
        assertEquals(f.challenges.first(), f.challenges.last())
        assertNull(f.controller.state.retryPodChallengeName)
        f.fail = false; f.failure = null
        f.controller.challengeFromPod(otherPod, opponent, "Another game"); f.worker.drain()
        assertEquals(listOf(pod, pod, otherPod), f.challengedPods)
        assertEquals(opponent, f.challenges.last().opponentId)
        assertNotEquals(f.challenges.first().requestId, f.challenges.last().requestId)
        assertEquals(game.id, f.controller.state.selected)
    }

    @Test fun terminalDefinitionFailureDoesNotRetainChallenge() = Fixture().use { f ->
        f.submissionFailure = DeckFailure("invalid_definition"); f.challenge()
        assertNull(f.controller.state.retryPodChallengeName)
        f.submissionFailure = null
        f.controller.challengeFromPod(id(), id(), "Another game"); f.worker.drain()
        assertEquals(1, f.challenges.size)
        assertNotEquals(f.submissions.first().requestId, f.submissions.last().requestId)
    }

    @Test fun missingBasicsAndClearedAccountNeverCreateChallenges() = Fixture().use { f ->
        f.resolve = { throw LobbyFailure("basic_definitions_unavailable") }
        f.challenge(); assertTrue(f.calls.isEmpty()); assertTrue(f.controller.state.error!!.contains("M21"))
        f.resolve = { definitions }
        f.controller.challengeFromPod(pod, b.accountId, game.gameName)
        f.controller.clear(); f.worker.drain()
        assertTrue(f.calls.isEmpty()); assertNull(f.controller.state.selected)
    }

    // DRF-124, LOB-052
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

    // LOB-049
    @Test fun sideBasicsAndTransfersUndoAsOrdinaryInverseRequestsUntilTheRoundChanges() = Fixture().use { f ->
        f.challenge()
        val card = CardDefinition("Pool card", listOf(DefinitionFace(id(), "Pool card")))
        val pool = SubmittedDefinition(DeckJson.digest(card), card)
        val deck = PreparedDeck(id(), pod, "1", "Limited pod", null, id(), listOf(
            PreparedEntry(definitions[0].digest, SavedDeckSection.MAINBOARD, "0", 40, BasicLand.PLAINS),
            PreparedEntry(pool.digest, SavedDeckSection.MAINBOARD, "40", 2)), PodGamePreparation.basicDefinitions(definitions)!!, "42")
        val preparation = LobbyPreparation(game.id, game.preparationRound, listOf(PlayerPreparation(a.accountId, deck = deck),
            PlayerPreparation(b.accountId, deck = deck.copy(id = id()))), definitions + pool, podRules = PodGameRules())
        f.head(preparation)
        assertNull(f.controller.preparationUndoStep())
        assertTrue(f.controller.changePodBasic(BasicLand.ISLAND, 1, SavedDeckSection.SIDEBOARD))
        val side = f.wire.frames.filterIsInstance<GameCommand>().single()
        assertEquals(SavedDeckSection.SIDEBOARD, (side.action as GameAction.SetPodGameBasics).section)
        assertEquals(PodDeckBasics(island = 1), (side.action as GameAction.SetPodGameBasics).basics)
        assertEquals(PreparationStep.Basics(SavedDeckSection.SIDEBOARD, PodDeckBasics()), f.controller.preparationUndoStep())
        f.wire.listener.message(GameResult(side.requestId, side.scope, "rejected", "invalidBasics", "1"))
        assertTrue(f.controller.undoPreparation()); assertNull(f.controller.preparationUndoStep())
        val undone = f.wire.frames.filterIsInstance<GameCommand>().last().action as GameAction.SetPodGameBasics
        assertEquals(SavedDeckSection.SIDEBOARD, undone.section); assertEquals(PodDeckBasics(), undone.basics)
        f.wire.listener.message(GameResult(f.wire.frames.filterIsInstance<GameCommand>().last().requestId, side.scope, "rejected", "invalidBasics", "1"))
        val captured = f.controller.captureTransfer(pool.digest, SavedDeckSection.MAINBOARD, false)!!
        assertTrue(f.controller.transfer(captured))
        assertEquals(PreparationStep.Moved(captured), f.controller.preparationUndoStep())
        assertTrue(f.controller.undoPreparation()); assertNull(f.controller.preparationUndoStep())
        val back = f.wire.frames.filterIsInstance<GameCommand>().map { it.action }.filterIsInstance<GameAction.TransferPreparedCard>()
        assertEquals(listOf(SavedDeckSection.MAINBOARD, SavedDeckSection.SIDEBOARD), back.map { it.source }.distinct())
        assertEquals(1, back.map { it.cardId }.distinct().size) // The same copy goes back.
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

    // DRF-122
    @Test fun buildingMembersCanBeChallengedOnceDraftedAndHeldChallengesWaitForTheirDeck() {
        val detail = PodDetail(pod, "Limited pod", PodPhase.DRAFTED, a, DraftSettings(), PodSource("Cube", 540), DraftSupply(90, 540),
            listOf(PodMember(a, submitted = true), PodMember(b, building = true)), emptyList(), updatedAt = "2026-10-26T00:00:00Z")
        assertTrue(podChallengeOffered(detail, detail.members[1], a.accountId))
        assertFalse(podChallengeOffered(detail, detail.members[0], a.accountId))
        assertFalse(podChallengeOffered(detail.copy(phase = PodPhase.DRAFTING), detail.members[1], a.accountId))
        assertEquals("Waiting for B's deck", gameStatus(game.copy(invitationHeld = true)))
        assertEquals("Invitation pending", gameStatus(game))
    }
}
