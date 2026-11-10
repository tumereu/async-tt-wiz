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

/** I-D52 (LOB-118, LOB-124, DRF-162): a deck or cube Automatum can't play fully, by the card catalog's marks, can't start a
 * game or pod. */
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

    // LOB-118, DRF-162
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
                fun deck(vararg entries: CardListEntry) = json.decodeFromString<SavedDeck>(decks.create(a, CreateDeck(id(), "Pups", entries.toList())).body)
                val pups = org.asyncttwiz.server.TestPrintings.entry("Ash Pup", 40)
                // Odd Relic's text Automatum can't read: the test cards' catalog doesn't mark it supported
                val relic = org.asyncttwiz.server.TestPrintings.entry("Odd Relic", 1, SavedDeckSection.MAYBEBOARD)
                assertEquals("automatumUnsupported", select(deck(pups, relic)).reason, "a Maybe card counts too (LOB-141)")
                assertEquals("accepted", select(deck(pups)).outcome)

                val cubes = CubeService(runtime)
                val cube = json.decodeFromString<SavedCube>(cubes.create(a, CreateCube(id(), "Odd cube",
                    listOf(org.asyncttwiz.server.TestPrintings.entry("Ash Pup", 1), relic))).body)
                val refused = PodService(runtime).create(a, CreatePod(id(), "Odd pod", cube.cube.id))
                assertEquals(409, refused.status)
                assertTrue(refused.body.contains("automatum_unsupported"), refused.body)
            }
        }
    }

    // LOB-118, LOB-124, DRF-162 (I-D52, pivot review #2): the server checks support again where a game or draft starts: a
    // prepared deck holding a card the catalog's marks don't support (a lobby kept from before, when a deck without a
    // stored support passed) can't be readied, and a pod whose kept source support names cards starts no draft
    @Test fun `support is checked again when a player is Ready and when a draft starts`() {
        FreshDatabase().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            ServiceRuntime(db.settings).use { runtime ->
                runtime.start(); ready(runtime)
                val a = account(runtime, "LaterA"); val b = account(runtime, "LaterB")
                val games = GameService(runtime); val lobby = LobbyService(runtime); val decks = DeckService(runtime)
                lobby.add(a, AddContact(id(), b.account.accountId))
                val game = json.decodeFromString<GameSummary>(lobby.create(a, CreateInvitation(id(), b.account.accountId, "Later Spark")).body)
                val hello = GameHello(id(), id(), gameplayRulesVersion = CURRENT_GAMEPLAY_RULES_VERSION)
                val connection = UUID.randomUUID()
                val stream = games.open(a, hello, connection, game.id, id())
                fun execute(action: GameAction): GameResult {
                    val command = GameCommand(id(), GameScope(game.id, game.preparationRound), action, stream.streamId, stream.generation)
                    assertTrue(games.admit(a, hello, connection, command) is GameReceipt); assertTrue(games.executeOne())
                    return games.results(a, game.id, listOf(command.requestId)).statuses.single().result!!
                }
                val deck = json.decodeFromString<SavedDeck>(decks.create(a, CreateDeck(id(), "Pups",
                    listOf(org.asyncttwiz.server.TestPrintings.entry("Ash Pup", 40)))).body)
                assertEquals("accepted", execute(GameAction.SelectDeck(deck.deck.id, deck.deck.revision)).outcome)
                // The lobby as an earlier server kept it: the prepared deck holds Odd Relic, unsupported by the marks
                val pup = org.asyncttwiz.server.TestPrintings.submitted("Ash Pup")
                val relic = org.asyncttwiz.server.TestPrintings.submitted("Odd Relic")
                db.connection().use { connection -> connection.createStatement().use { statement ->
                    statement.execute("INSERT INTO definition VALUES ('${relic.digest}', 1, '${DeckJson.canonical(relic.definition).replace("'", "''")}') ON CONFLICT DO NOTHING")
                    statement.execute("INSERT INTO game_definition VALUES ('${game.id}', '${relic.digest}')")
                    statement.execute("UPDATE player_preparation SET deck = replace(deck, '${pup.digest}', '${relic.digest}') " +
                        "WHERE game_id = '${game.id}' AND account_id = '${a.account.accountId}'")
                } }
                assertEquals("automatumUnsupported", execute(GameAction.Ready).reason)
                assertFalse(games.head(a, game.id).preparation!!.players.single { it.accountId == a.account.accountId }.ready)

                val cubes = CubeService(runtime); val pods = PodService(runtime)
                val cube = json.decodeFromString<SavedCube>(cubes.create(a, CreateCube(id(), "Later cube", listOf(org.asyncttwiz.server.TestPrintings.entry("Ash Pup", 90)))).body)
                val pod = json.decodeFromString<PodDetail>(pods.create(a, CreatePod(id(), "Later pod", cube.cube.id)).body).id
                assertEquals(200, pods.invite(a, pod, InviteToPod(id(), b.account.accountId)).status)
                assertEquals(200, pods.invitation(b, pod, PodRequest(id()), true).status)
                assertEquals(200, pods.update(a, pod, UpdatePod(id(), settings = DraftSettings(packsPerPlayer = 3, cardsPerPack = 1))).status)
                // The pod's source as stored with it (a set source as its creator's device worked it out)
                runtime.transaction { connection -> org.jooq.impl.DSL.using(connection, org.jooq.SQLDialect.POSTGRES)
                    .update(org.asyncttwiz.server.generated.tables.Pod.POD)
                    .set(org.asyncttwiz.server.generated.tables.Pod.POD.AUTOMATUM_SUPPORT, json.encodeToString(CardListSupport(21, listOf("Later Bear"), 2)))
                    .where(org.asyncttwiz.server.generated.tables.Pod.POD.ID.eq(UUID.fromString(pod))).execute() }
                val start = pods.start(a, pod, PodRequest(id()))
                assertEquals(409, start.status)
                assertTrue(start.body.contains("automatum_unsupported"), start.body)
            }
        }
    }
}
