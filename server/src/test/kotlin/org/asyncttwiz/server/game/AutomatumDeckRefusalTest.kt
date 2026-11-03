package org.asyncttwiz.server.game

import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.serialization.encodeToString
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.accounts.AccountService
import org.asyncttwiz.server.cubes.CubeService
import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.FreshDatabase
import org.asyncttwiz.server.decks.DeckService
import org.asyncttwiz.server.lobby.LobbyService
import org.asyncttwiz.server.pods.PodService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** I-D52 (LOB-115, DRF-161): a deck or cube its owner's device found Automatum can't play fully can't start a game or pod. */
class AutomatumDeckRefusalTest {
    private val json = GameWire.json
    private fun id() = UUID.randomUUID().toString()
    private fun ready(runtime: ServiceRuntime) {
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        while (!runtime.ready()) { assertTrue(System.nanoTime() < end); Thread.sleep(20) }
    }
    private fun account(runtime: ServiceRuntime, name: String): AccountService.Principal {
        val accounts = AccountService(runtime)
        accounts.register(name, "$name@example.invalid", "synthetic password")
        return accounts.authenticate(accounts.login("$name@example.invalid", "synthetic password")!!.token)!!
    }

    // LOB-115, DRF-161
    @Test fun `unsupported decks can't be chosen and unsupported cubes can't make a pod`() {
        FreshDatabase().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            ServiceRuntime(db.settings).use { runtime ->
                runtime.start(); ready(runtime)
                val a = account(runtime, "RefuseA"); val b = account(runtime, "RefuseB")
                val games = GameService(runtime); val lobby = LobbyService(runtime); val decks = DeckService(runtime)
                lobby.add(a, AddContact(id(), b.account.accountId))
                val game = json.decodeFromString<GameSummary>(lobby.create(a, CreateInvitation(id(), b.account.accountId, "Odd Spark")).body)
                val hello = GameHello(id(), id(), gameplayRulesVersion = CURRENT_GAMEPLAY_RULES_VERSION)
                val connection = UUID.randomUUID()
                val stream = games.open(a, hello, connection, game.id, id())
                fun select(deck: SavedDeck): GameResult {
                    val command = GameCommand(id(), GameScope(game.id, game.preparationRound), GameAction.SelectDeck(deck.deck.id, deck.deck.revision),
                        stream.streamId, stream.generation)
                    assertTrue(games.admit(a, hello, connection, command) is GameReceipt); assertTrue(games.executeOne())
                    return games.results(a, game.id, listOf(command.requestId)).statuses.single().result!!
                }
                val card = CardDefinition("Odd Bear", listOf(DefinitionFace(id(), "Odd Bear", rulesText = "Some odd wording.")))
                val submitted = SubmittedDefinition(DeckJson.digest(card), card)
                assertEquals(200, decks.submit(a, SubmitDefinitions(id(), listOf(submitted))).status)
                fun deck(support: CardListSupport?): SavedDeck {
                    val saved = json.decodeFromString<SavedDeck>(decks.create(a, CreateDeck(id(), "Bears",
                        listOf(SavedDeckEntry(SavedDeckSection.MAINBOARD, submitted.digest, 40)))).body)
                    if (support != null) assertEquals(200, decks.setSupport(a, saved.deck.id, SetCardListSupport(saved.deck.revision, support)).status)
                    return saved
                }
                assertEquals("automatumUnsupported", select(deck(CardListSupport(21, listOf("Odd Bear")))).reason)
                assertEquals("accepted", select(deck(CardListSupport(21))).outcome)
                assertEquals("accepted", select(deck(null)).outcome, "not worked out yet: the device decides")

                val cubes = CubeService(runtime)
                val cube = json.decodeFromString<SavedCube>(cubes.create(a, CreateCube(id(), "Odd cube", listOf(SavedCubeEntry(submitted.digest, 1)))).body)
                assertEquals(200, cubes.setSupport(a, cube.cube.id, SetCardListSupport(cube.cube.revision, CardListSupport(21, listOf("Odd Bear")))).status)
                val refused = PodService(runtime).create(a, CreatePod(id(), "Odd pod", cube.cube.id))
                assertEquals(409, refused.status)
                assertTrue(refused.body.contains("automatum_unsupported"), refused.body)
            }
        }
    }
}
