package org.asyncttwiz.server.decks

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID
import kotlinx.serialization.encodeToString
import org.asyncttwiz.protocol.*
import org.asyncttwiz.server.EphemeralPorts
import org.asyncttwiz.server.ServiceSettings
import org.asyncttwiz.server.SpringService
import org.asyncttwiz.server.database.FoundationMigrations
import org.asyncttwiz.server.database.FreshDatabase
import org.asyncttwiz.server.startBound
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** I-D51 (LOB-124): the server works out a deck's or cube's Automatum support from its card catalog's marks on every read. */
class CardListSupportHttpTest {
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

    // LOB-124, DRF-161
    @Test fun `support is worked out from the catalog's marks whenever a list is read and nothing can store it`() {
        FreshDatabase().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            val config = EphemeralPorts.settings(db.settings)
            SpringService(config).use { service ->
                val settings = service.startBound(config)
                val deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos()
                while (client.send(HttpRequest.newBuilder(URI("http://127.0.0.1:${settings.healthPort()}/health/ready")).build(), HttpResponse.BodyHandlers.ofString()).statusCode() != 200) {
                    check(System.nanoTime() < deadline); Thread.sleep(20)
                }
                val owner = account(settings, "support-owner")
                val catalog = org.asyncttwiz.server.TestPrintings.catalog.catalog.header
                // Ash Pup is supported by the test cards' catalog's marks; Odd Relic's text Automatum can't read
                val pup = org.asyncttwiz.server.TestPrintings.entry("Ash Pup", 4)
                val relic = org.asyncttwiz.server.TestPrintings.entry("Odd Relic", 1, SavedDeckSection.MAYBEBOARD)
                val supported = CardListSupport(catalog.parserVersion, emptyList(), catalog.engineVersion)
                val unsupported = CardListSupport(catalog.parserVersion, listOf("Odd Relic"), catalog.engineVersion)

                // Decks: known at once, on every read, and following the cards
                val created = json.decodeFromString<SavedDeck>(request(settings, "POST", "/v1/decks", json.encodeToString(
                    CreateDeck(id(), "Pups", listOf(pup))), owner.token).body())
                assertEquals(supported, created.deck.automatum)
                val deckPath = "/v1/decks/${created.deck.id}"
                val changed = json.decodeFromString<SavedDeck>(request(settings, "PUT", deckPath, json.encodeToString(UpdateDeck(id(),
                    contents = DeckContentsImport(listOf(pup, relic)))), owner.token).body())
                assertEquals(unsupported, changed.deck.automatum, "a Maybe card counts too (LOB-141)")
                assertEquals(unsupported, json.decodeFromString<SavedDeck>(request(settings, "GET", deckPath, token = owner.token).body()).deck.automatum)
                assertEquals(unsupported, json.decodeFromString<DeckList>(request(settings, "GET", "/v1/decks", token = owner.token).body()).decks.single().automatum)
                assertEquals(unsupported, json.decodeFromString<SavedDeck>(request(settings, "POST", "$deckPath/copy", json.encodeToString(CopyDeck(id())), owner.token).body()).deck.automatum)
                val body = json.encodeToString(mapOf("revision" to changed.deck.revision))
                assertEquals(404, request(settings, "PUT", "$deckPath/automatum", body, owner.token).statusCode(), "devices no longer store it")

                // Cubes
                val cube = json.decodeFromString<SavedCube>(request(settings, "POST", "/v1/cubes", json.encodeToString(
                    CreateCube(id(), "Odd cube", listOf(pup, relic))), owner.token).body())
                val cubePath = "/v1/cubes/${cube.cube.id}"
                assertEquals(unsupported, cube.cube.automatum)
                assertEquals(unsupported, json.decodeFromString<CubeList>(request(settings, "GET", "/v1/cubes", token = owner.token).body()).cubes.single().automatum)
                val recubed = json.decodeFromString<SavedCube>(request(settings, "PUT", cubePath, json.encodeToString(UpdateCube(id(),
                    entries = listOf(pup))), owner.token).body())
                assertEquals(supported, recubed.cube.automatum)
                assertEquals(supported, json.decodeFromString<SavedCube>(request(settings, "GET", cubePath, token = owner.token).body()).cube.automatum)
                assertEquals(404, request(settings, "PUT", "$cubePath/automatum", body, owner.token).statusCode())
            }
        }
    }
}
