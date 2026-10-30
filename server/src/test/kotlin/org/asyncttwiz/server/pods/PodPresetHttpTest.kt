package org.asyncttwiz.server.pods

import java.net.InetAddress
import java.net.ServerSocket
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
import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.OwnedPostgres
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PodPresetHttpTest {
    private val json = DeckJson.format
    private fun id() = UUID.randomUUID().toString()

    @Test fun `presets are saved listed renamed overwritten and deleted per account`() {
        OwnedPostgres().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            fun port() = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1")).use { it.localPort }
            val settings = ServiceSettings(db.settings, URI("https://synthetic.invalid"), InetAddress.getByName("127.0.0.1"), port(), InetAddress.getByName("127.0.0.1"), port())
            val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
            SpringService(settings).use { service ->
                service.start()
                val deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos()
                while (client.send(HttpRequest.newBuilder(URI("http://127.0.0.1:${settings.healthPort()}/health/ready")).build(), HttpResponse.BodyHandlers.ofString()).statusCode() != 200) {
                    check(System.nanoTime() < deadline); Thread.sleep(20)
                }
                fun request(token: String?, method: String, path: String, body: String? = null): HttpResponse<String> {
                    val builder = HttpRequest.newBuilder(URI("http://127.0.0.1:${settings.publicPort()}$path"))
                        .timeout(Duration.ofSeconds(30)).header("Content-Type", "application/json")
                        .method(method, body?.let(HttpRequest.BodyPublishers::ofString) ?: HttpRequest.BodyPublishers.noBody())
                    token?.let { builder.header("Authorization", "Bearer $it") }
                    return client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
                }
                fun account(name: String): String {
                    assertEquals(201, request(null, "POST", "/accounts", json.encodeToString(RegisterAccount(name, "$name@example.invalid", "password😀"))).statusCode())
                    return json.decodeFromString<SessionCreated>(request(null, "POST", "/sessions",
                        json.encodeToString(CreateSession("$name@example.invalid", "password😀"))).body()).token
                }
                fun save(token: String, input: SavePodPreset, status: Int): String {
                    val response = request(token, "POST", "/v1/pod-presets", json.encodeToString(input))
                    assertEquals(status, response.statusCode(), response.body()); return response.body()
                }
                fun list(token: String): List<PodPreset> {
                    val response = request(token, "GET", "/v1/pod-presets")
                    assertEquals(200, response.statusCode(), response.body())
                    return json.decodeFromString<PodPresetList>(response.body()).presets
                }
                fun code(response: HttpResponse<String>) = json.decodeFromString<PodError>(response.body()).code
                val a = account("preset-a"); val b = account("preset-b")
                val card = CardDefinition("Preset card", listOf(DefinitionFace(id(), "Preset card")))
                val definition = SubmittedDefinition(DeckJson.digest(card), card)
                assertEquals(200, request(a, "POST", "/v1/definitions", json.encodeToString(SubmitDefinitions(id(), listOf(definition)))).statusCode())
                val cube = json.decodeFromString<SavedCube>(request(a, "POST", "/v1/cubes",
                    json.encodeToString(CreateCube(id(), "Preset cube", listOf(SavedCubeEntry(definition.digest, 40))))).body()).cube

                assertEquals(401, request(null, "GET", "/v1/pod-presets").statusCode())
                assertEquals(emptyList<PodPreset>(), list(a))
                val glimpse = DraftSettings.glimpse().copy(librarians = 2, sideboardLimit = 15)
                // The Librarian snapshot is supplied again when a pod is created; a preset never stores it.
                val withLibrarian = glimpse.copy(librarianDefinition = definition)
                val cubePreset = json.decodeFromString<PodPreset>(save(a, SavePodPreset("Cube night", withLibrarian, cubeId = cube.id), 201))
                assertEquals(PodPreset(cubePreset.id, "Cube night", glimpse, cube.id, "Preset cube", false, updatedAt = cubePreset.updatedAt), cubePreset)
                val setPreset = json.decodeFromString<PodPreset>(save(a, SavePodPreset("Alpha grid", DraftSettings.grid().copy(grids = 12),
                    setCode = "DMU", setName = "Dominaria United"), 201))
                assertEquals("DMU", setPreset.setCode); assertNull(setPreset.cubeId)
                save(a, SavePodPreset("no source", DraftSettings()), 201)
                assertEquals(listOf("Alpha grid", "Cube night", "no source"), list(a).map { it.name })

                // Same name ignoring case: refused unless overwriting, which replaces contents and spelling.
                assertEquals("preset_exists", json.decodeFromString<PodError>(save(a, SavePodPreset("CUBE NIGHT", DraftSettings()), 409)).code)
                val replaced = json.decodeFromString<PodPreset>(save(a, SavePodPreset("CUBE NIGHT", DraftSettings.booster().copy(packsPerPlayer = 4),
                    setCode = "MOM", setName = "March of the Machine", overwrite = true), 200))
                assertEquals(cubePreset.id, replaced.id)
                assertEquals(listOf("CUBE NIGHT", 4, "MOM", null), listOf(replaced.name, replaced.settings.packsPerPlayer, replaced.setCode, replaced.cubeId))
                assertEquals(3, list(a).size)

                // Rename keeps the contents; a name another preset uses is refused, a case change of its own name is fine.
                val renamePath = "/v1/pod-presets/${setPreset.id}"
                assertEquals("preset_exists", code(request(a, "PUT", renamePath, json.encodeToString(RenamePodPreset("No Source")))))
                val renamed = request(a, "PUT", renamePath, json.encodeToString(RenamePodPreset("ALPHA grid")))
                assertEquals(200, renamed.statusCode(), renamed.body())
                assertEquals(setPreset.copy(name = "ALPHA grid", updatedAt = json.decodeFromString<PodPreset>(renamed.body()).updatedAt),
                    json.decodeFromString<PodPreset>(renamed.body()))
                assertEquals(400, request(a, "PUT", renamePath, json.encodeToString(RenamePodPreset(" padded "))).statusCode())
                assertEquals(400, request(a, "PUT", renamePath, json.encodeToString(RenamePodPreset("x".repeat(61)))).statusCode())

                // Other accounts neither see nor change the presets, nor can they reference another account's cube.
                assertEquals(emptyList<PodPreset>(), list(b))
                assertEquals(404, request(b, "PUT", renamePath, json.encodeToString(RenamePodPreset("Stolen"))).statusCode())
                assertEquals(404, request(b, "DELETE", renamePath).statusCode())
                assertEquals("cube_not_found", json.decodeFromString<PodError>(save(b, SavePodPreset("Theirs", DraftSettings(), cubeId = cube.id), 404)).code)
                save(b, SavePodPreset("Cube night", DraftSettings()), 201)
                assertEquals(3, list(a).size)

                // Validation.
                save(a, SavePodPreset("Both", DraftSettings(), cubeId = cube.id, setCode = "DMU", setName = "Dominaria United"), 400)
                save(a, SavePodPreset("Nameless set", DraftSettings(), setCode = "DMU"), 400)
                save(a, SavePodPreset("Bad", DraftSettings(keep = 0)), 400)
                save(a, SavePodPreset("", DraftSettings()), 400)
                save(a, SavePodPreset("Unknown cube", DraftSettings(), cubeId = id()), 404)
                assertEquals(415, client.send(HttpRequest.newBuilder(URI("http://127.0.0.1:${settings.publicPort()}/v1/pod-presets"))
                    .header("Authorization", "Bearer $a").header("Content-Type", "text/plain")
                    .POST(HttpRequest.BodyPublishers.ofString("{}")).build(), HttpResponse.BodyHandlers.ofString()).statusCode())
                assertEquals(405, request(a, "DELETE", "/v1/pod-presets").statusCode())
                assertEquals(405, request(a, "GET", renamePath).statusCode())
                assertEquals(404, request(a, "DELETE", "/v1/pod-presets/not-a-uuid").statusCode())

                // Deleting the cube keeps the preset and reports the cube missing.
                val second = json.decodeFromString<PodPreset>(save(a, SavePodPreset("Cube again", glimpse, cubeId = cube.id), 201))
                assertEquals(200, request(a, "DELETE", "/v1/cubes/${cube.id}", json.encodeToString(DeleteCube(id()))).statusCode())
                val missing = list(a).single { it.id == second.id }
                assertEquals(listOf(cube.id, null, true), listOf(missing.cubeId, missing.cubeName, missing.cubeMissing))
                assertEquals(glimpse, missing.settings)

                // Delete.
                val deleted = request(a, "DELETE", "/v1/pod-presets/${second.id}")
                assertEquals(200, deleted.statusCode(), deleted.body())
                assertEquals(PodPresetDeleted(second.id), json.decodeFromString<PodPresetDeleted>(deleted.body()))
                assertEquals(404, request(a, "DELETE", "/v1/pod-presets/${second.id}").statusCode())
                assertEquals(listOf("ALPHA grid", "CUBE NIGHT", "no source"), list(a).map { it.name })

                // Limit on the number of presets; overwriting an existing one stays possible at the limit.
                repeat(PodPresetValidation.MAX_PRESETS - 1) { save(b, SavePodPreset("Filler $it", DraftSettings()), 201) }
                assertEquals("too_many_presets", json.decodeFromString<PodError>(save(b, SavePodPreset("One more", DraftSettings()), 409)).code)
                save(b, SavePodPreset("filler 0", DraftSettings.grid(), overwrite = true), 200)
            }
        }
    }
}
