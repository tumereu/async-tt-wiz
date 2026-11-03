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

/** I-D51 (LOB-115): a deck's or cube's Automatum support is stored for the revision it was worked out for. */
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
    private fun card(name: String): SubmittedDefinition {
        val card = CardDefinition(name, listOf(DefinitionFace(id(), name, typeLine = "Creature — Bear", rulesText = "Some odd wording.")))
        return SubmittedDefinition(DeckJson.digest(card), card)
    }

    // LOB-115, DRF-161
    @Test fun `support is stored per revision, kept by renames and copies and dropped when the cards change`() {
        FreshDatabase().start().use { db ->
            FoundationMigrations.migrate(db.settings)
            val config = EphemeralPorts.settings(db.settings)
            SpringService(config).use { service ->
                val settings = service.startBound(config)
                val deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos()
                while (client.send(HttpRequest.newBuilder(URI("http://127.0.0.1:${settings.healthPort()}/health/ready")).build(), HttpResponse.BodyHandlers.ofString()).statusCode() != 200) {
                    check(System.nanoTime() < deadline); Thread.sleep(20)
                }
                val owner = account(settings, "support-owner"); val foreign = account(settings, "support-foreign")
                val bear = card("Odd Bear"); val wolf = card("Odd Wolf")
                assertEquals(200, request(settings, "POST", "/v1/definitions", json.encodeToString(SubmitDefinitions(id(), listOf(bear, wolf))), owner.token).statusCode())
                val support = CardListSupport(21, listOf("Odd Bear"))

                // Decks
                val created = json.decodeFromString<SavedDeck>(request(settings, "POST", "/v1/decks", json.encodeToString(
                    CreateDeck(id(), "Bears", listOf(SavedDeckEntry(SavedDeckSection.MAINBOARD, bear.digest, 4)))), owner.token).body())
                assertNull(created.deck.automatum, "an old or new list has none until a device works it out")
                val deckPath = "/v1/decks/${created.deck.id}"
                val stale = request(settings, "PUT", "$deckPath/automatum", json.encodeToString(SetCardListSupport("99", support)), owner.token)
                assertEquals(CardListSupportStored(false), json.decodeFromString<CardListSupportStored>(stale.body()))
                assertNull(json.decodeFromString<SavedDeck>(request(settings, "GET", deckPath, token = owner.token).body()).deck.automatum)
                assertEquals(404, request(settings, "PUT", "$deckPath/automatum", json.encodeToString(SetCardListSupport(created.deck.revision, support)), foreign.token).statusCode())
                assertEquals(400, request(settings, "PUT", "$deckPath/automatum", json.encodeToString(SetCardListSupport(created.deck.revision,
                    CardListSupport(21, listOf("Odd Bear", "Odd Bear")))), owner.token).statusCode())
                val stored = request(settings, "PUT", "$deckPath/automatum", json.encodeToString(SetCardListSupport(created.deck.revision, support)), owner.token)
                assertEquals(CardListSupportStored(true), json.decodeFromString<CardListSupportStored>(stored.body()), stored.body())
                val read = json.decodeFromString<SavedDeck>(request(settings, "GET", deckPath, token = owner.token).body())
                assertEquals(support, read.deck.automatum)
                assertEquals(created.deck.revision, read.deck.revision, "storing it is not an edit")
                assertEquals(support, json.decodeFromString<DeckList>(request(settings, "GET", "/v1/decks", token = owner.token).body()).decks.single().automatum)
                val renamed = json.decodeFromString<SavedDeck>(request(settings, "PUT", deckPath, json.encodeToString(UpdateDeck(id(), name = "Big bears")), owner.token).body())
                assertEquals(support, renamed.deck.automatum)
                val copy = json.decodeFromString<SavedDeck>(request(settings, "POST", "$deckPath/copy", json.encodeToString(CopyDeck(id())), owner.token).body())
                assertEquals(support, copy.deck.automatum)
                val changed = json.decodeFromString<SavedDeck>(request(settings, "PUT", deckPath, json.encodeToString(UpdateDeck(id(),
                    contents = DeckContentsImport(listOf(SavedDeckEntry(SavedDeckSection.MAINBOARD, wolf.digest, 4))))), owner.token).body())
                assertNull(changed.deck.automatum)

                // Cubes
                val cube = json.decodeFromString<SavedCube>(request(settings, "POST", "/v1/cubes", json.encodeToString(
                    CreateCube(id(), "Odd cube", listOf(SavedCubeEntry(bear.digest, 1)))), owner.token).body())
                val cubePath = "/v1/cubes/${cube.cube.id}"
                assertNull(cube.cube.automatum)
                assertEquals(CardListSupportStored(true), json.decodeFromString<CardListSupportStored>(request(settings, "PUT", "$cubePath/automatum",
                    json.encodeToString(SetCardListSupport(cube.cube.revision, support)), owner.token).body()))
                assertEquals(support, json.decodeFromString<CubeList>(request(settings, "GET", "/v1/cubes", token = owner.token).body()).cubes.single().automatum)
                assertEquals(support, json.decodeFromString<SavedCube>(request(settings, "PUT", cubePath, json.encodeToString(UpdateCube(id(), name = "Odder")), owner.token).body()).cube.automatum)
                val recubed = json.decodeFromString<SavedCube>(request(settings, "PUT", cubePath, json.encodeToString(UpdateCube(id(),
                    entries = listOf(SavedCubeEntry(wolf.digest, 1)))), owner.token).body())
                assertNull(recubed.cube.automatum)
                assertEquals(CardListSupportStored(false), json.decodeFromString<CardListSupportStored>(request(settings, "PUT", "$cubePath/automatum",
                    json.encodeToString(SetCardListSupport(cube.cube.revision, support)), owner.token).body()))
                assertEquals(404, request(settings, "PUT", "$cubePath/automatum", json.encodeToString(SetCardListSupport(recubed.cube.revision, support)), foreign.token).statusCode())
            }
        }
    }
}
