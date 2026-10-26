package org.asyncttwiz.android

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.asyncttwiz.protocol.*
import org.junit.Test
import kotlin.test.*

class CubeApiTest {
    private val id = UUID.randomUUID().toString()
    private val token = "A".repeat(43)
    private val card = CardDefinition("Card", listOf(DefinitionFace(UUID.randomUUID().toString(), "Card")))
    private val submitted = SubmittedDefinition(DeckJson.digest(card), card)
    private val batch = ReadCubeDefinitions(listOf(submitted.digest))

    @Test fun batchUsesAuthenticatedPostAndValidatesTheResponse() = runBlocking {
        val response = SubmittedDefinitions(listOf(submitted))
        DeckApi("https://cubes.example", HttpClient(MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/v1/cubes/$id/definitions", request.url.encodedPath)
            assertEquals("Bearer $token", request.headers["Authorization"])
            val body = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
            assertEquals(batch, DeckJson.format.decodeFromString<ReadCubeDefinitions>(body))
            respond(DeckJson.format.encodeToString(response), headers = headersOf("Content-Type", "application/json"))
        })).use { api ->
            assertEquals(response, api.cubeDefinitions(token, id, batch))
        }
    }

    @Test fun missingExtraDuplicateOrMismatchedDefinitionsAreRejected() = runBlocking {
        val foreignCard = card.copy(name = "Other")
        val foreign = SubmittedDefinition(DeckJson.digest(foreignCard), foreignCard)
        for (definitions in listOf(emptyList(), listOf(submitted, foreign), listOf(submitted, submitted),
            listOf(foreign), listOf(submitted.copy(definition = foreignCard)))) {
            DeckApi("https://cubes.example", HttpClient(MockEngine {
                respond(DeckJson.format.encodeToString(SubmittedDefinitions(definitions)))
            })).use { api ->
                assertEquals("invalid_response", assertFailsWith<DeckFailure> { api.cubeDefinitions(token, id, batch) }.code)
            }
        }
    }
}
