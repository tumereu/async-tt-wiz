package org.asyncttwiz.android

import io.ktor.client.plugins.timeout
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readRemaining
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.io.readByteArray
import org.asyncttwiz.storage.CubeCobraImport
import org.asyncttwiz.storage.ParsedDeck

/** A Cube Cobra import failure whose message is shown as is. */
internal class CubeCobraFailure(message: String) : Exception(message)

/** Public provider request: no account credentials, fixed HTTPS origin, no redirects or shared cache. */
internal fun fetchCubeCobra(id: String): ParsedDeck {
    require(CubeCobraImport.cubeId(id) == id)
    val body = try {
        runBlocking {
            AccountApi.client().use { client ->
                // Large cubes are megabytes of JSON; allow slow mobile links more than the account API default.
                val response = client.get("https://cubecobra.com/cube/api/cubeJSON/$id") {
                    header("Accept", "application/json")
                    timeout { requestTimeoutMillis = 90_000; socketTimeoutMillis = 30_000 }
                }
                when (response.status.value) {
                    200 -> {}
                    404 -> throw CubeCobraFailure("Cube Cobra cube $id was not found.")
                    else -> throw CubeCobraFailure("Cube Cobra could not be loaded (HTTP ${response.status.value}).")
                }
                val bytes = response.bodyAsChannel().readRemaining(DeckApi.MAX_BODY.toLong() + 1).readByteArray()
                if (bytes.size > DeckApi.MAX_BODY) throw CubeCobraFailure("Cube Cobra response is too large.")
                bytes.decodeToString()
            }
        }
    } catch (failure: CubeCobraFailure) { throw failure
    } catch (failure: CancellationException) { throw failure
    } catch (_: Exception) { throw CubeCobraFailure("Cube Cobra could not be reached. Try again.") }
    return try { CubeCobraImport.parse(body) } catch (_: Exception) {
        throw CubeCobraFailure("Cube Cobra response could not be read.")
    }
}

/** Executed on the import worker on the phone, without account credentials. */
internal fun fetchDeckUrl(location: DeckImportLocation): ImportedDeck = runBlocking {
    AccountApi.client().use { client ->
        suspend fun read(url: String, accept: String): String {
            val response = client.get(url) { header("Accept", accept) }
            check(response.status.value == 200) { "${location.site.label} could not be loaded. Paste the list instead." }
            val bytes = response.bodyAsChannel().readRemaining(DeckApi.MAX_BODY.toLong() + 1).readByteArray()
            check(bytes.size <= DeckApi.MAX_BODY) { "Deck response is too large. Paste the list instead." }
            return bytes.decodeToString(throwOnInvalidSequence = true)
        }
        val body = read(location.endpoint, "application/json")
        DeckUrlImport.parse(location.site, body)
    }
}
