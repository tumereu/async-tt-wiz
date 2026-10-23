package org.asyncttwiz.server.game

import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.zip.GZIPInputStream
import kotlinx.serialization.encodeToString
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.ServiceRuntime
import org.asyncttwiz.server.ServiceSettings
import org.asyncttwiz.server.SpringService
import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.OwnedPostgres
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GameplayReplicaIntegrationTest {
    private val json = GameWire.json
    private fun port() = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1")).use { it.localPort }

    @Test fun `one FIFO retains guarded later move manual tuples relational rows and coherent HTTP baseline`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            lateinit var accepted: GameHead
            lateinit var root: PlayState
            lateinit var token: String
            lateinit var results: List<GameResult>
            lateinit var mutations: List<GameplayMutation>
            ServiceRuntime(db.settings).use { runtime ->
                runtime.start(); gameplayAwait(runtime::ready)
                val f = GameplayFixture(runtime); root = f.root
                val library = root.board.containers.single { it.zone == PlayZone.LIBRARY && it.accountId == f.a.account.accountId }
                val hand = root.board.containers.single { it.zone == PlayZone.HAND && it.accountId == f.a.account.accountId }
                val first = library.cards[0]; val second = library.cards[1]
                val face = root.board.cards.single { it.id == second }.faceId
                val actions = listOf(
                    PlayAction.MoveCard(first, MoveSource(library.id), MoveDestination(library.id, MoveInsertion.BOTTOM)),
                    PlayAction.MoveCard(first, MoveSource(library.id, edge = MoveSourceEdge.TOP), MoveDestination(hand.id, MoveInsertion.UNORDERED)),
                    PlayAction.MoveCard(second, MoveSource(library.id, edge = MoveSourceEdge.TOP), MoveDestination(hand.id, MoveInsertion.UNORDERED)),
                    PlayAction.SetNote(second, " retained manual note "),
                    PlayAction.SetCounter(CounterTarget.PLAYER, f.a.account.accountId, CounterColor.BLUE, "2"),
                    PlayAction.SetCovered(second, false),
                    PlayAction.InspectFace(second, face, hand.id),
                    PlayAction.SetCorner(second, "8"), PlayAction.ResetCorner(second))
                val commands = actions.map(f.owner::command)
                val receipts = commands.map { f.owner.admit(it) as GameReceipt }
                assertEquals(receipts.map { it.ingressOrdinal.toLong() }.zipWithNext().map { it.second - it.first }, List(actions.size - 1) { 1L })
                repeat(commands.size) { assertTrue(f.games.executeOne()) }; assertFalse(f.games.executeOne())
                results = commands.map(f.owner::result)
                assertEquals(listOf("accepted", "rejected") + List(actions.size - 2) { "accepted" }, results.map { it.outcome })
                assertEquals("movementConflict", results[1].reason); assertNull(results[1].commitRevision); assertNull(results[1].mutation)
                mutations = results.mapNotNull { it.mutation }
                var prediction = root
                commands.forEachIndexed { index, command ->
                    val change = GameplayRules.change(prediction, f.a.account.accountId, command)
                    assertEquals(results[index].mutation, change.mutation)
                    if (change.state != null) prediction = change.state!!
                }
                accepted = f.games.head(f.a, f.game.id)
                assertEquals(prediction, accepted.play)
                assertEquals(hand.id, accepted.play!!.board.cards.single { it.id == second }.containerId)
                assertEquals("retained manual note", accepted.play!!.board.cards.single { it.id == second }.note)
                assertEquals(listOf(GameplayRead(second, GameplayField.FACE, face)), mutations.last().transition.reads)
                assertEquals("8", mutations.last().transition.fields.single().from)
                assertEquals("3/5", mutations.last().transition.fields.single().to)
                assertTrue(mutations.any { it.transition.disclosures.isNotEmpty() })
                assertTrue(mutations.any { it.transition.fields.any { field -> field.field == GameplayField.PLAYER_COUNTER } })
                val history = f.games.history(f.b, f.game.id, root.id, "0")
                assertEquals(mutations, history.mutations)
                var replay = root
                history.mutations.forEach { replay = GameplayHistory.replay(replay, it).state!! }
                assertEquals(accepted.play, replay)
                db.connection().use { connection ->
                    connection.createStatement().executeQuery("SELECT count(*) FROM gameplay_mutation").use {
                        assertTrue(it.next()); assertEquals(mutations.size, it.getInt(1)) }
                    connection.createStatement().executeQuery("SELECT count(*) FROM game_inbox WHERE result IS NULL").use {
                        assertTrue(it.next()); assertEquals(0, it.getInt(1)) }
                }
                val accounts = org.asyncttwiz.server.accounts.AccountService(runtime)
                token = accounts.login("PlayA@example.invalid", "synthetic password")!!.token
                assertEquals(results[2], f.owner.admit(commands[2]))
            }
            // A fresh runtime reads the same committed relational/state/revision/result bytes over real HTTP.
            val settings = ServiceSettings(db.settings, URI("https://synthetic.invalid"),
                InetAddress.getByName("127.0.0.1"), port(), InetAddress.getByName("127.0.0.1"), port())
            val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()
            fun request(path: String, body: String? = null): ByteArray {
                val input = HttpRequest.newBuilder(URI("http://127.0.0.1:${settings.publicPort()}$path"))
                    .timeout(Duration.ofSeconds(15)).header("Authorization", "Bearer $token")
                if (body != null) input.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body))
                val response = client.send(input.build(), HttpResponse.BodyHandlers.ofByteArray())
                assertEquals(200, response.statusCode(), response.body().toString(Charsets.UTF_8))
                assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(""))
                return response.body()
            }
            SpringService(settings).use { service ->
                service.start()
                gameplayAwait { client.send(HttpRequest.newBuilder(URI("http://127.0.0.1:${settings.healthPort()}/health/ready")).build(),
                    HttpResponse.BodyHandlers.discarding()).statusCode() == 200 }
                val lookup = json.encodeToString(LookupGameResults(results.map { it.requestId }))
                val manifest = json.decodeFromString<GameplayManifest>(request("/v1/games/${accepted.gameId}/baselines", lookup).toString(Charsets.UTF_8))
                GameplaySync.validate(manifest)
                assertEquals(accepted.revision, manifest.revision)
                assertEquals(results, manifest.statuses.map { it.result })
                assertEquals(manifest, json.decodeFromString<GameplayManifest>(request("/v1/baselines/${manifest.id}/manifest").toString(Charsets.UTF_8)))
                val bytes = ByteArrayOutputStream()
                manifest.chunks.forEach { chunk ->
                    val part = GZIPInputStream(request("/v1/baselines/${manifest.id}/chunks/${chunk.index}").inputStream()).use { it.readAllBytes() }
                    assertEquals(chunk.bytes, part.size); assertEquals(chunk.digest, GameplaySync.digest(part)); bytes.write(part)
                }
                val treeRecords = bytes.toString(Charsets.UTF_8).lineSequence().filter { it.isNotEmpty() }
                    .map { json.decodeFromString<TreeSyncRecord>(it) }.toList()
                assertEquals(manifest.recordCount, treeRecords.size)
                val snapshot = GameplaySnapshot(manifest.head!!, emptyList(), treeRecords)
                GameplaySync.validate(snapshot); assertEquals(accepted, snapshot.head)
                val tree = TreeSync.decode(treeRecords.asSequence(), accepted.gameId, root.id).tree
                assertEquals(mutations.associateBy { it.id }, tree.nodes)
                val history = json.decodeFromString<GameplayHistoryPage>(request(
                    "/v1/games/${accepted.gameId}/plays/${root.id}/mutations?after=0").toString(Charsets.UTF_8))
                assertEquals(mutations, history.mutations); assertEquals(manifest.revision, history.throughRevision)
                val incremental = json.decodeFromString<GameRevisions>(request(
                    "/v1/games/${accepted.gameId}/revisions?after=${accepted.revision.toLong() - mutations.size}").toString(Charsets.UTF_8))
                assertEquals(mutations, incremental.revisions.mapNotNull { it.mutation })
                var replayTree = TreeSync.generation(GameplayTree(root))
                incremental.revisions.forEach { replayTree = TreeSync.apply(replayTree, it.treeDelta!!) }
                assertEquals(tree, replayTree.tree)
            }
            println("PASS production FIFO + real PostgreSQL: failed captured TOP does not stop later valid TOP; shared prediction equals committed movement/manual/read/disclosure tuples; relational history, results, revisions and hashed HTTP baseline agree after runtime restart")
        }
    }
}
