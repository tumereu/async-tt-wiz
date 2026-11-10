package org.asyncttwiz.android

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
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

    // Owner 2026-11-10 (card data from the server's catalog): a cube read names its cards by printing and name, one request
    @Test fun aCubeReadIsOneAuthenticatedGetOfItsPrintings() = runBlocking {
        val cube = SavedCube(CubeSummary(id, "Runes", "1", "2026-11-10T00:00:00Z", "2026-11-10T00:00:00Z", 3),
            listOf(SavedCubeEntry(UUID.randomUUID().toString(), "Card", 2), SavedCubeEntry(null, "Lost card", 1, SavedDeckSection.MAYBEBOARD)))
        DeckApi("https://cubes.example", HttpClient(MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/v1/cubes/$id", request.url.encodedPath)
            assertEquals("Bearer $token", request.headers["Authorization"])
            respond(DeckJson.format.encodeToString(cube), headers = headersOf("Content-Type", "application/json"))
        })).use { api ->
            assertEquals(cube, api.readCube(token, id))
        }
    }
}
