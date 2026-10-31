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
    @Test fun `definition batches require ownership and attached digests and enforce the size cap`() {
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
                val definitions = List(251) { index ->
                    val card = CardDefinition("Batch card $index", listOf(DefinitionFace(id(), "Batch card $index")))
                    SubmittedDefinition(DeckJson.digest(card), card)
                }
                assertEquals(200, request(settings, "POST", "/v1/definitions", json.encodeToString(SubmitDefinitions(id(), definitions)), owner.token).statusCode())
                val attached = definitions.take(250)
                val create = CreateCube(id(), "Batch cube", attached.map { SavedCubeEntry(it.digest, 1) })
                val created = request(settings, "POST", "/v1/cubes", json.encodeToString(create), owner.token)
                assertEquals(201, created.statusCode(), created.body())
                val saved = json.decodeFromString<SavedCube>(created.body())
                val path = "/v1/cubes/${saved.cube.id}/definitions"
                val batch = ReadCubeDefinitions(attached.map { it.digest })
                val read = request(settings, "POST", path, json.encodeToString(batch), owner.token)
                assertEquals(200, read.statusCode(), read.body())
                assertEquals(SubmittedDefinitions(attached), json.decodeFromString<SubmittedDefinitions>(read.body()))
                assertEquals(401, request(settings, "POST", path, json.encodeToString(batch)).statusCode())
                // A grant to the definitions is not ownership of the cube.
                assertEquals(200, request(settings, "POST", "/v1/definitions", json.encodeToString(SubmitDefinitions(id(), attached.take(1))), foreign.token).statusCode())
                val denied = request(settings, "POST", path, json.encodeToString(ReadCubeDefinitions(listOf(attached.first().digest))), foreign.token)
                assertEquals(404, denied.statusCode()); assertFalse(denied.body().contains("Batch card"))
                val foreignCard = CardDefinition("Foreign card", listOf(DefinitionFace(id(), "Foreign card")))
                val foreignDefinition = SubmittedDefinition(DeckJson.digest(foreignCard), foreignCard)
                assertEquals(200, request(settings, "POST", "/v1/definitions", json.encodeToString(SubmitDefinitions(id(), listOf(foreignDefinition))), foreign.token).statusCode())
                for (unattached in listOf(definitions.last().digest, foreignDefinition.digest, "sha256:" + "f".repeat(64))) {
                    val rejected = request(settings, "POST", path, json.encodeToString(ReadCubeDefinitions(listOf(attached.first().digest, unattached))), owner.token)
                    assertEquals(404, rejected.statusCode()); assertFalse(rejected.body().contains("Batch card"))
                }
                for (invalid in listOf(emptyList(), definitions.map { it.digest }, List(251) { attached.first().digest }, listOf("invalid"))) {
                    assertEquals(400, request(settings, "POST", path, json.encodeToString(ReadCubeDefinitions(invalid)), owner.token).statusCode())
                }
                assertEquals(404, request(settings, "POST", "/v1/cubes/${id()}/definitions", json.encodeToString(batch), owner.token).statusCode())
                assertEquals(405, request(settings, "GET", path, token = owner.token).statusCode())
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
                val card = CardDefinition("Fixture card", listOf(DefinitionFace(id(), "Fixture card")))
                val definition = SubmittedDefinition(DeckJson.digest(card), card)
                assertEquals(200, request(settings, "POST", "/v1/definitions", json.encodeToString(SubmitDefinitions(id(), listOf(definition))), owner.token).statusCode())
                val create = CreateCube(id(), "Synergy", listOf(SavedCubeEntry(definition.digest, 3000)), "synergy")
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
                for (privatePath in listOf(path, "$path/definitions/${definition.digest}")) {
                    val denied = request(settings, "GET", privatePath, token = foreign.token)
                    assertEquals(404, denied.statusCode()); assertFalse(denied.body().contains(card.name))
                }
                assertEquals(definition, json.decodeFromString<SubmittedDefinition>(request(settings, "GET", "$path/definitions/${definition.digest}", token = owner.token).body()))
                assertEquals(404, request(settings, "PUT", path, json.encodeToString(UpdateCube(id(), name = "Stolen")), foreign.token).statusCode())
                assertEquals(404, request(settings, "DELETE", path, json.encodeToString(DeleteCube(id())), foreign.token).statusCode())
                assertEquals(400, request(settings, "POST", "/v1/cubes", json.encodeToString(create.copy(requestId = id())), foreign.token).statusCode())
                for (bad in listOf(create.copy(requestId = id(), name = " "), create.copy(requestId = id(), entries = emptyList()),
                    create.copy(requestId = id(), entries = create.entries + create.entries),
                    create.copy(requestId = id(), entries = listOf(SavedCubeEntry(definition.digest, 3001))),
                    create.copy(requestId = id(), entries = listOf(SavedCubeEntry(definition.digest, 0))),
                    create.copy(requestId = id(), cubeCobraId = "../bad"))) {
                    val rejected = request(settings, "POST", "/v1/cubes", json.encodeToString(bad), owner.token)
                    assertEquals(400, rejected.statusCode())
                    assertEquals(rejected.body(), request(settings, "POST", "/v1/cubes", json.encodeToString(bad), owner.token).body())
                }
                val update = UpdateCube(id(), "Renamed", listOf(SavedCubeEntry(definition.digest, 540)))
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
}
