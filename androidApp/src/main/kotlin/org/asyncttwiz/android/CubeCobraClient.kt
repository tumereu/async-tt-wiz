package org.asyncttwiz.android

import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.runBlocking
import kotlinx.io.readByteArray
import org.asyncttwiz.storage.CubeCobraImport
import org.asyncttwiz.storage.ParsedDeck

/** Public provider request: no account credentials, fixed HTTPS origin, no redirects or shared cache. */
internal fun fetchCubeCobra(id: String): ParsedDeck {
    require(CubeCobraImport.cubeId(id) == id)
    return runBlocking {
        AccountApi.client().use { client ->
            val response = client.get("https://cubecobra.com/cube/api/cubeJSON/$id") { header("Accept", "application/json") }
            check(response.status.value == 200) { "Cube Cobra could not be loaded." }
            val bytes = response.bodyAsChannel().readRemaining(DeckApi.MAX_BODY.toLong() + 1).readByteArray()
            check(bytes.size <= DeckApi.MAX_BODY) { "Cube Cobra response is too large." }
            CubeCobraImport.parse(bytes.decodeToString(throwOnInvalidSequence = true))
        }
    }
}
