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

/** Executed on the import worker on the phone, without account credentials. */
internal fun fetchDeckUrl(location: DeckImportLocation): ImportedDeck = runBlocking {
    AccountApi.client().use { client ->
        suspend fun read(url: String, accept: String): String {
            val response = client.get(url) { header("Accept", accept) }
            check(response.status.value == 200) { "${location.site.label} could not be loaded. Try Paste list." }
            val bytes = response.bodyAsChannel().readRemaining(DeckApi.MAX_BODY.toLong() + 1).readByteArray()
            check(bytes.size <= DeckApi.MAX_BODY) { "Deck response is too large. Try Paste list." }
            return bytes.decodeToString(throwOnInvalidSequence = true)
        }
        val body = read(location.endpoint, if (location.site == DeckImportSite.MTGGOLDFISH) "text/plain" else "application/json")
        val name = if (location.site == DeckImportSite.MTGGOLDFISH) {
            DeckUrlImport.goldfishName(read("https://www.mtggoldfish.com/deck/${location.id}", "text/html"))
        } else null
        DeckUrlImport.parse(location.site, body, name)
    }
}
