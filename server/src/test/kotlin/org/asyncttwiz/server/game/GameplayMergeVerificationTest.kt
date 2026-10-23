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
import org.asyncttwiz.server.accounts.AccountService
import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.OwnedPostgres
import org.flywaydb.core.api.exception.FlywayValidateException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Independent check of the unified relational/export store at an actual FIFO snapshot boundary. */
class GameplayMergeVerificationTest {
    private val json = GameWire.json
    private fun port() = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1")).use { it.localPort }
    private fun snapshot(manifest: GameplayManifest, chunk: (Int) -> ByteArray): GameplaySnapshot {
        GameplaySync.validate(manifest)
        val bytes = ByteArrayOutputStream()
        manifest.chunks.forEach {
            val decoded = GZIPInputStream(chunk(it.index).inputStream()).use { input -> input.readAllBytes() }
            assertEquals(it.bytes, decoded.size); assertEquals(it.digest, GameplaySync.digest(decoded)); bytes.write(decoded)
        }
        return json.decodeFromString<GameplaySnapshot>(bytes.toString(Charsets.UTF_8)).also(GameplaySync::validate)
    }

    @Test fun `pending export stays immutable while FIFO rejects earlier draw and commits later original with relational HTTP coherence`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            lateinit var head: GameHead
            lateinit var results: List<GameResult>
            lateinit var retained: GameplaySnapshot
            lateinit var token: String
            ServiceRuntime(db.settings).use { runtime ->
                runtime.start(); gameplayAwait(runtime::ready)
                val f = GameplayFixture(runtime)
                val library = f.root.board.containers.single { it.zone == PlayZone.LIBRARY && it.accountId == f.a.account.accountId }
                val hand = f.root.board.containers.single { it.zone == PlayZone.HAND && it.accountId == f.a.account.accountId }
                val first = library.cards[0]; val second = library.cards[1]
                fun draw(card: String) = PlayAction.MoveCard(card, MoveSource(library.id, edge = MoveSourceEdge.TOP),
                    MoveDestination(hand.id, MoveInsertion.UNORDERED))
                val commands = listOf(
                    PlayAction.MoveCard(first, MoveSource(library.id), MoveDestination(library.id, MoveInsertion.BOTTOM)),
                    draw(first), draw(second), PlayAction.SetNote(second, " boundary note "),
                    PlayAction.SetCounter(CounterTarget.PLAYER, f.a.account.accountId, CounterColor.RED, "4")
                ).map(f.owner::command)
                val receipts = commands.map { f.owner.admit(it) as GameReceipt }
                assertEquals(List(4) { 1L }, receipts.map { it.ingressOrdinal.toLong() }.zipWithNext { a, b -> b - a })
                assertTrue(f.games.executeOne()); assertTrue(f.games.executeOne())
                val ids = commands.map { it.requestId }
                val boundary = f.games.head(f.a, f.game.id)
                val export = f.games.baseline(f.a, f.game.id, ids)
                val oldChunks = export.chunks.map { f.games.baselineChunk(f.a, export.id, it.index).copyOf() }
                val oldSnapshot = snapshot(export) { oldChunks[it] }
                assertEquals(boundary, oldSnapshot.head)
                assertEquals(listOf("final", "final", "pending", "pending", "pending"), export.statuses.map { it.status })
                repeat(3) { assertTrue(f.games.executeOne()) }; assertFalse(f.games.executeOne())
                results = commands.map(f.owner::result)
                assertEquals(listOf("accepted", "rejected", "accepted", "accepted", "accepted"), results.map { it.outcome })
                assertEquals("movementConflict", results[1].reason)
                assertNull(results[1].mutation); assertNull(results[1].commitRevision)
                head = f.games.head(f.a, f.game.id)
                assertEquals(boundary.revision.toLong() + 3, head.revision.toLong())
                assertEquals(hand.id, head.play!!.board.cards.single { it.id == second }.containerId)
                assertEquals("boundary note", head.play!!.board.cards.single { it.id == second }.note)
                assertEquals(export, f.games.baselineManifest(f.a, export.id))
                oldChunks.forEachIndexed { index, bytes -> assertArrayEquals(bytes, f.games.baselineChunk(f.a, export.id, index)) }
                assertEquals(oldSnapshot, snapshot(export) { f.games.baselineChunk(f.a, export.id, it) })
                val current = f.games.baseline(f.a, f.game.id, ids)
                assertEquals(results, current.statuses.map { it.result })
                retained = snapshot(current) { f.games.baselineChunk(f.a, current.id, it) }
                assertEquals(head, retained.head)
                val mutations = results.mapNotNull { it.mutation }
                assertEquals(mutations, f.games.history(f.b, f.game.id, f.root.id, "0").mutations)
                assertEquals(mutations, retained.records.mapNotNull { it.mutation })
                var replay = f.root
                mutations.forEach { replay = GameplayHistory.replay(replay, it).state!! }
                assertEquals(head.play, replay)
                results.forEachIndexed { index, result ->
                    assertEquals(result, f.owner.admit(commands[index])) // Original IDs deduplicate, including rejection.
                    result.mutation?.let { assertEquals(CommandIds.uuidV5(result.requestId, "board-node"), it.id) }
                    db.connection().use { connection ->
                        connection.prepareStatement("""SELECT i.result, o.result, o.revision, m.payload
                            FROM game_inbox i JOIN game_outbox o ON o.request_id = i.request_id
                            LEFT JOIN gameplay_mutation m ON m.request_id = i.request_id
                            WHERE i.request_id = ?::uuid""").use { statement ->
                            statement.setString(1, result.requestId)
                            statement.executeQuery().use { rows ->
                                assertTrue(rows.next())
                                assertEquals(result, json.decodeFromString<GameResult>(rows.getString(1)))
                                assertEquals(result, json.decodeFromString<GameServerMessage>(rows.getString(2)))
                                val revision = rows.getString(3)?.let { json.decodeFromString<GameServerMessage>(it) as GameRevision }
                                assertEquals(result.commitRevision, revision?.resultRevision)
                                assertEquals(result.mutation, rows.getString(4)?.let { json.decodeFromString<GameplayMutation>(it) })
                                assertEquals(result.mutation, revision?.mutation)
                                if (revision != null) assertEquals(retained.records.single { it.requestId == result.requestId }, revision)
                                assertFalse(rows.next())
                            }
                        }
                    }
                }
                assertEquals(head, f.games.head(f.a, f.game.id)); assertFalse(f.games.executeOne())
                token = AccountService(runtime).login("PlayA@example.invalid", "synthetic password")!!.token
            }
            val settings = ServiceSettings(db.settings, URI("https://synthetic.invalid"),
                InetAddress.getByName("127.0.0.1"), port(), InetAddress.getByName("127.0.0.1"), port())
            val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()
            fun read(path: String, body: String? = null): ByteArray {
                val request = HttpRequest.newBuilder(URI("http://127.0.0.1:${settings.publicPort()}$path"))
                    .timeout(Duration.ofSeconds(15)).header("Authorization", "Bearer $token")
                if (body != null) request.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body))
                val response = client.send(request.build(), HttpResponse.BodyHandlers.ofByteArray())
                assertEquals(200, response.statusCode()); assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(""))
                return response.body()
            }
            SpringService(settings).use { service ->
                service.start()
                gameplayAwait { client.send(HttpRequest.newBuilder(URI("http://127.0.0.1:${settings.healthPort()}/health/ready")).build(),
                    HttpResponse.BodyHandlers.discarding()).statusCode() == 200 }
                val lookup = json.encodeToString(LookupGameResults(results.map { it.requestId }))
                val manifest = json.decodeFromString<GameplayManifest>(
                    read("/v1/games/${head.gameId}/baselines", lookup).toString(Charsets.UTF_8))
                assertEquals(head.revision, manifest.revision); assertEquals(results, manifest.statuses.map { it.result })
                assertEquals(retained, snapshot(manifest) { read("/v1/baselines/${manifest.id}/chunks/$it") })
                results.forEach { result ->
                    assertEquals(result, json.decodeFromString<GameRequestStatus>(read(
                        "/v1/games/${head.gameId}/results/${result.requestId}").toString(Charsets.UTF_8)).result)
                }
                val history = json.decodeFromString<GameplayHistoryPage>(read(
                    "/v1/games/${head.gameId}/plays/${head.play!!.id}/mutations?after=0").toString(Charsets.UTF_8))
                assertEquals(results.mapNotNull { it.mutation }, history.mutations)
                assertEquals(head.revision, history.throughRevision)
            }
            // A changed applied checksum is refused by the actual strict profile, never repaired/ignored.
            db.connection().use { it.createStatement().executeUpdate("UPDATE flyway_schema_history SET checksum = checksum + 1 WHERE version = '11'") }
            assertThrows(FlywayValidateException::class.java) { FoundationMigrations.flyway(db.settings).validate() }
            println("PASS immutable pending export, failed earlier TOP/later accepted original, relational mutation/result/outbox/revision identity, restarted real HTTP baseline/history/original coherence and strict checksum refusal")
        }
    }
}
