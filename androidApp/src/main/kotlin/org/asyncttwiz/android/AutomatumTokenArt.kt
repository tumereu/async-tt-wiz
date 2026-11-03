package org.asyncttwiz.android

import org.asyncttwiz.automatum.TokenArt
import org.asyncttwiz.automatum.TokenArtQuery
import org.asyncttwiz.storage.AndroidCatalogStorage
import org.asyncttwiz.storage.CatalogRepository
import java.util.concurrent.ConcurrentHashMap

/**
 * The pictures of tokens Automatum makes (integration spec §4.6): the installed catalog's best matching token printing
 * ([CatalogRepository.tokenArt]). Answers are kept per catalog generation, so planning looks each kind up once.
 * Without card records, or when reading them fails, a token keeps the plain frame.
 */
internal class CatalogTokenArt(private val repository: () -> CatalogRepository) : TokenArt {
    private val found = ConcurrentHashMap<Pair<String, TokenArtQuery>, String>()
    override fun find(token: TokenArtQuery): String? = try {
        val catalog = repository()
        val generation = catalog.tokenGeneration() ?: return null
        found.getOrPut(generation to token) {
            catalog.tokenArt(token.face, token.sourceName, token.sourcePrintingId)?.scryfallId.orEmpty()
        }.ifEmpty { null }
    } catch (problem: Exception) {
        AppLog.warn("automatum", "Token picture lookup failed: ${problem.message?.take(200)}")
        null
    }
}

/** One [CatalogTokenArt] per process, on the app's catalog. */
internal object AndroidTokenArt {
    @Volatile private var instance: Pair<android.content.Context, CatalogTokenArt>? = null
    @Synchronized fun of(context: android.content.Context): TokenArt {
        val app = context.applicationContext
        instance?.takeIf { it.first === app }?.let { return it.second }
        return CatalogTokenArt { AndroidCatalogStorage.repository(app) }.also { instance = app to it }
    }
}
