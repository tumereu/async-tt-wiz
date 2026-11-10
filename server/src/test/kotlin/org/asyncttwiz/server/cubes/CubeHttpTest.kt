package org.asyncttwiz.server.cubes

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID
import kotlinx.serialization.encodeToString
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.ServiceSettings
import org.asyncttwiz.server.SpringService
import org.asyncttwiz.server.EphemeralPorts
import org.asyncttwiz.server.TestPrintings
import org.asyncttwiz.server.startBound
import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.FreshDatabase
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CubeHttpTest {
    private val json = DeckJson.format
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
    private fun id() = UUID.randomUUID().toString()
    private fun request(settings: ServiceSettings, method: String, path: String, body: String? = null, token: String? = null): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI("http://127.0.0.1:${settings.publicPort()}$path"))
            .timeout(Duration.ofSeconds(25)).header("Content-Type", "application/json")
            .method(method, body?.let(HttpRequest.BodyPublishers::ofString) ?: HttpRequest.BodyPublishers.noBody())
        if (token != null) builder.header("Authorization", "Bearer $token")
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }
    private fun account(settings: ServiceSettings, name: String): SessionCreated {
        assertEquals(201, request(settings, "POST", "/accounts", json.encodeToString(RegisterAccount(name, "$name@example.invalid", "password😀"))).statusCode())
        return json.decodeFromString(request(settings, "POST", "/sessions", json.encodeToString(CreateSession("$name@example.invalid", "password😀"))).body())
    }
    // Owner 2026-11-10 (card data from the server's catalog): a cube names its cards by printing and name, and the phone
    // builds them from its card records: there is no read of their card data
    @Test fun `a cube names its cards by printing and has no card data read`() {
        FreshDatabase().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            val config = EphemeralPorts.settings(db.settings)
            lateinit var settings: ServiceSettings
            SpringService(config).use { service ->
                settings = service.startBound(config)
                val deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos()
                while (client.send(HttpRequest.newBuilder(URI("http://127.0.0.1:${settings.healthPort()}/health/ready")).build(), HttpResponse.BodyHandlers.ofString()).statusCode() != 200) {
                    check(System.nanoTime() < deadline); Thread.sleep(20)
                }
                val owner = account(settings, "batch-owner"); val foreign = account(settings, "batch-foreign")
                val names = TestPrintings.names("E2E")
                val create = CreateCube(id(), "Batch cube", names.map { TestPrintings.entry(it, 1) })
                val created = request(settings, "POST", "/v1/cubes", json.encodeToString(create), owner.token)
                assertEquals(201, created.statusCode(), created.body())
                val saved = json.decodeFromString<SavedCube>(created.body())
                assertEquals(names.map { SavedCubeEntry(TestPrintings.definition(it).printingId, it, 1) }, saved.entries)
                assertEquals(404, request(settings, "POST", "/v1/cubes/${saved.cube.id}/definitions", "{}", owner.token).statusCode())
                assertEquals(404, request(settings, "GET", "/v1/cubes/${saved.cube.id}", token = foreign.token).statusCode())
            }
        }
    }
    // DRF-007
    @Test fun `cube CRUD ownership validation and retained retry outcomes over real HTTP`() {
        FreshDatabase().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            val config = EphemeralPorts.settings(db.settings)
            lateinit var settings: ServiceSettings
            SpringService(config).use { service ->
                settings = service.startBound(config)
                val deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos()
                while (client.send(HttpRequest.newBuilder(URI("http://127.0.0.1:${settings.healthPort()}/health/ready")).build(), HttpResponse.BodyHandlers.ofString()).statusCode() != 200) {
                    check(System.nanoTime() < deadline); Thread.sleep(20)
                }
                val owner = account(settings, "cube-owner"); val foreign = account(settings, "cube-foreign")
                val card = TestPrintings.definition("Storm Spark")
                val definition = TestPrintings.submitted("Storm Spark")
                fun entry(quantity: Int) = TestPrintings.entry("Storm Spark", quantity)
                val create = CreateCube(id(), "Synergy", listOf(entry(3000)), "synergy")
                val created = request(settings, "POST", "/v1/cubes", json.encodeToString(create), owner.token)
                assertEquals(201, created.statusCode(), created.body())
                val saved = json.decodeFromString<SavedCube>(created.body()); val path = "/v1/cubes/${saved.cube.id}"
                assertEquals(3000L, saved.cube.totalCopies); assertEquals("synergy", saved.cube.cubeCobraId)
                assertEquals(created.body(), request(settings, "POST", "/v1/cubes", json.encodeToString(create), owner.token).body())
                assertEquals(409, request(settings, "POST", "/v1/cubes", json.encodeToString(create.copy(name = "Changed")), owner.token).statusCode())
                assertEquals(409, request(settings, "POST", "/v1/cubes", json.encodeToString(create), foreign.token).statusCode())
                assertEquals(401, request(settings, "GET", path).statusCode())
                assertEquals(saved, json.decodeFromString<SavedCube>(request(settings, "GET", path, token = owner.token).body()))
                assertEquals(listOf(saved.cube), json.decodeFromString<CubeList>(request(settings, "GET", "/v1/cubes", token = owner.token).body()).cubes)
                assertTrue(json.decodeFromString<CubeList>(request(settings, "GET", "/v1/cubes", token = foreign.token).body()).cubes.isEmpty())
                val denied = request(settings, "GET", path, token = foreign.token)
                assertEquals(404, denied.statusCode()); assertFalse(denied.body().contains(card.name))
                assertEquals(404, request(settings, "PUT", path, json.encodeToString(UpdateCube(id(), name = "Stolen")), foreign.token).statusCode())
                assertEquals(404, request(settings, "DELETE", path, json.encodeToString(DeleteCube(id())), foreign.token).statusCode())
                // A new cube holds no card without a printing
                assertEquals(400, request(settings, "POST", "/v1/cubes", json.encodeToString(create.copy(requestId = id(),
                    entries = listOf(CardListEntry(SavedDeckSection.MAINBOARD, null, 1, definition.definition.name)))), foreign.token).statusCode())
                for (bad in listOf(create.copy(requestId = id(), name = " "), create.copy(requestId = id(), entries = emptyList()),
                    create.copy(requestId = id(), entries = create.entries + create.entries),
                    create.copy(requestId = id(), entries = listOf(entry(3001))),
                    create.copy(requestId = id(), entries = listOf(entry(0))),
                    create.copy(requestId = id(), cubeCobraId = "../bad"))) {
                    val rejected = request(settings, "POST", "/v1/cubes", json.encodeToString(bad), owner.token)
                    assertEquals(400, rejected.statusCode())
                    assertEquals(rejected.body(), request(settings, "POST", "/v1/cubes", json.encodeToString(bad), owner.token).body())
                }
                val update = UpdateCube(id(), "Renamed", listOf(entry(540)))
                val updated = request(settings, "PUT", path, json.encodeToString(update), owner.token)
                assertEquals(200, updated.statusCode(), updated.body())
                val next = json.decodeFromString<SavedCube>(updated.body())
                assertEquals("2", next.cube.revision); assertEquals("Renamed", next.cube.name); assertEquals(540L, next.cube.totalCopies)
                assertEquals(saved.cube.createdAt, next.cube.createdAt); assertNotEquals(saved.cube.updatedAt, next.cube.updatedAt)
                assertEquals(updated.body(), request(settings, "PUT", path, json.encodeToString(update), owner.token).body())
                assertEquals(400, request(settings, "PUT", path, json.encodeToString(UpdateCube(id(), entries = emptyList())), owner.token).statusCode())
                assertEquals(next, json.decodeFromString<SavedCube>(request(settings, "GET", path, token = owner.token).body()))
                val rename = UpdateCube(id(), name = "Only rename")
                val renamed = request(settings, "PUT", path, json.encodeToString(rename), owner.token)
                assertEquals(next.entries, json.decodeFromString<SavedCube>(renamed.body()).entries)
                assertEquals(created.body(), request(settings, "POST", "/v1/cubes", json.encodeToString(create), owner.token).body())
                val delete = DeleteCube(id())
                val deleted = request(settings, "DELETE", path, json.encodeToString(delete), owner.token)
                assertEquals(200, deleted.statusCode()); assertEquals(deleted.body(), request(settings, "DELETE", path, json.encodeToString(delete), owner.token).body())
                assertEquals(404, request(settings, "GET", path, token = owner.token).statusCode())
                assertTrue(json.decodeFromString<CubeList>(request(settings, "GET", "/v1/cubes", token = owner.token).body()).cubes.isEmpty())
            }
        }
    }
    // DRF-007, DRF-050: a cube has Main and Maybe; the limit counts both, only Main is drafted.
    @Test fun `cube Main and Maybe sections round trip count toward the limit and only Main reaches a pod`() {
        FreshDatabase().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            val config = EphemeralPorts.settings(db.settings)
            SpringService(config).use { service ->
                val settings = service.startBound(config)
                val deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos()
                while (client.send(HttpRequest.newBuilder(URI("http://127.0.0.1:${settings.healthPort()}/health/ready")).build(), HttpResponse.BodyHandlers.ofString()).statusCode() != 200) {
                    check(System.nanoTime() < deadline); Thread.sleep(20)
                }
                val owner = account(settings, "maybe-owner")
                val cards = listOf("Storm Spark", "Ember Cub")
                // The same card may be in both sections.
                val entries = listOf(TestPrintings.entry(cards[0], 1999), TestPrintings.entry(cards[1], 1),
                    TestPrintings.entry(cards[0], 1000, SavedDeckSection.MAYBEBOARD))
                val created = request(settings, "POST", "/v1/cubes", json.encodeToString(CreateCube(id(), "Sections", entries)), owner.token)
                assertEquals(201, created.statusCode(), created.body())
                val saved = json.decodeFromString<SavedCube>(created.body())
                assertEquals(entries.map { SavedCubeEntry(it.printingId, cards[if (it.printingId == TestPrintings.id(cards[0])) 0 else 1], it.quantity, it.section) }, saved.entries)
                assertEquals(2000L, saved.cube.totalCopies); assertEquals(1000L, saved.cube.maybeCopies)
                assertEquals(saved, json.decodeFromString<SavedCube>(request(settings, "GET", "/v1/cubes/${saved.cube.id}", token = owner.token).body()))
                for (bad in listOf(entries + TestPrintings.entry(cards[1], 1, SavedDeckSection.MAYBEBOARD),
                    listOf(TestPrintings.entry(cards[0], 1, SavedDeckSection.SIDEBOARD)),
                    entries + entries.last())) {
                    assertEquals(400, request(settings, "POST", "/v1/cubes", json.encodeToString(CreateCube(id(), "Bad", bad)), owner.token).statusCode())
                }
                val pod = request(settings, "POST", "/v1/pods", json.encodeToString(CreatePod(id(), "Sections pod", saved.cube.id)), owner.token)
                assertEquals(201, pod.statusCode(), pod.body())
                assertEquals(2000L, json.decodeFromString<PodDetail>(pod.body()).source.totalCopies)
            }
        }
    }
}
