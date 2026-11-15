package org.asyncttwiz.android

import org.asyncttwiz.automatum.TokenArt
import org.asyncttwiz.automatum.TokenArtQuery
import org.asyncttwiz.storage.CardCatalog
import kotlin.concurrent.Volatile
import org.asyncttwiz.appui.Synchronized
import org.asyncttwiz.appui.concurrentMapOf
import org.asyncttwiz.appui.getOrPutConcurrently

/**
 * The pictures of tokens Automatum makes (integration spec §4.6): the installed catalog's best matching token printing
 * ([CatalogRepository.tokenArt]). Answers are kept per catalog generation, so planning looks each kind up once.
 * Without card records, or when reading them fails, a token keeps the plain frame. Each kind's answer is logged once
 * (problem report VSVLSK), and the pictures found are named in [TokenPictures] for the log of their loading.
 */
class CatalogTokenArt(private val repository: () -> CardCatalog) : TokenArt {
    private val found = concurrentMapOf<Pair<String, TokenArtQuery>, String>()
    @Volatile private var withoutRecords = false
    override fun find(token: TokenArtQuery): String? = try {
        val catalog = repository()
        val generation = catalog.tokenGeneration()
        if (generation == null) {
            if (!withoutRecords) AppLog.info("automatum", "Token picture of ${describe(token)}: no card records yet, the plain frame")
            withoutRecords = true
            null
        } else {
            withoutRecords = false
            found.getOrPutConcurrently(generation to token) {
                val printing = catalog.tokenArt(token.face, token.sourceName, token.sourcePrintingId)
                val id = printing?.scryfallId.orEmpty()
                AppLog.info("automatum", "Token picture of ${describe(token)}: " + when {
                    printing == null -> "no matching token in the card records, the plain frame"
                    id.isEmpty() -> "${printing.name} of ${printing.set} ${printing.number} has no picture, the plain frame"
                    else -> "$id (${printing.name} of ${printing.set} ${printing.number})"
                })
                if (id.isNotEmpty()) TokenPictures.found(id, token.face.name)
                id
            }.ifEmpty { null }
        }
    } catch (problem: Exception) {
        AppLog.warn("automatum", "Token picture lookup of ${describe(token)} failed: ${problem.message?.take(200)}")
        null
    }

    private fun describe(token: TokenArtQuery) = token.face.name +
        (token.sourceName?.let { " made by $it" } ?: "") + (token.sourcePrintingId?.let { " ($it)" } ?: "")
}

/** The token pictures found in this run ([CatalogTokenArt]), by picture id, with their tokens' names: their loading is
 * logged by the browser's picture service (problem report VSVLSK), a few hundred at most. */
object TokenPictures {
    private val names = concurrentMapOf<String, String>()
    internal fun found(id: String, name: String) { if (names.size < 512) names.getOrPutConcurrently(id) { name } }
    /** The name of the token drawn with picture [id], null when [id] isn't a token picture found here. */
    fun name(id: String?): String? = id?.let { names[it] }
}

/** One [CatalogTokenArt] per process, on the app's catalog. */
object AndroidTokenArt {
    @Volatile private var instance: Pair<AppServices, CatalogTokenArt>? = null
    @Synchronized fun of(services: AppServices): TokenArt {
        instance?.takeIf { it.first === services }?.let { return it.second }
        return CatalogTokenArt { services.catalogRepository() }.also { instance = services to it }
    }
}
