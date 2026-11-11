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
 * Without card records, or when reading them fails, a token keeps the plain frame.
 */
class CatalogTokenArt(private val repository: () -> CardCatalog) : TokenArt {
    private val found = concurrentMapOf<Pair<String, TokenArtQuery>, String>()
    override fun find(token: TokenArtQuery): String? = try {
        val catalog = repository()
        val generation = catalog.tokenGeneration() ?: return null
        found.getOrPutConcurrently(generation to token) {
            catalog.tokenArt(token.face, token.sourceName, token.sourcePrintingId)?.scryfallId.orEmpty()
        }.ifEmpty { null }
    } catch (problem: Exception) {
        AppLog.warn("automatum", "Token picture lookup failed: ${problem.message?.take(200)}")
        null
    }
}

/** One [CatalogTokenArt] per process, on the app's catalog. */
object AndroidTokenArt {
    @Volatile private var instance: Pair<AppServices, CatalogTokenArt>? = null
    @Synchronized fun of(services: AppServices): TokenArt {
        instance?.takeIf { it.first === services }?.let { return it.second }
        return CatalogTokenArt { services.catalogRepository() }.also { instance = services to it }
    }
}
