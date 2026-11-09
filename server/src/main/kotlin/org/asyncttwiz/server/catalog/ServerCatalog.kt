package org.asyncttwiz.server.catalog

import java.io.File
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import org.asyncttwiz.catalog.CardCatalog
import org.asyncttwiz.catalog.CardCatalogFiles
import org.asyncttwiz.catalog.CatalogSetRecord
import org.asyncttwiz.catalog.CatalogSetSource
import org.asyncttwiz.catalog.CatalogSetSources
import org.asyncttwiz.catalog.CatalogVersion
import org.asyncttwiz.protocol.CardListSupport

/**
 * The card catalog the server serves and builds set packs from (docs/specs/web-client.md section 3), built at release
 * and deployed beside the server: a directory (`CATALOG_DIR`, or the `atw.catalogDir` system property in tests) holding
 * `<checksum>.jsonl.gz` files and `current`, the checksum of the one in use; the one before it stays for a rollback.
 */
class ServerCatalog(val directory: File, val catalog: CardCatalog, val version: CatalogVersion) {
    private val sources = ConcurrentHashMap<String, Result<CatalogSetSource?>>()

    /** A set a pod may be made from (section 3.8), or null: one the Sets tab lists (DRF-088) and the catalog has. */
    fun draftSet(code: String): CatalogSetRecord? = catalog.set[code]?.takeIf { it.draftable }

    /** Set [code]'s source from the catalog (section 3.7), worked out once; null when it has no usable cards. */
    fun source(code: String): CatalogSetSource? =
        sources.computeIfAbsent(code) { runCatching { CatalogSetSources.build(catalog, code) } }.getOrThrow()

    /** What a pod from [set] keeps as its source's Automatum support (I-D52), from the catalog's marks. */
    fun support(set: CatalogSetRecord) = CardListSupport(catalog.header.parserVersion, set.unsupported, catalog.header.engineVersion)

    /** The gzip file of catalog [checksum] in this directory (the current one or the one kept before it), or null. */
    fun file(checksum: String): File? = ServerCatalogs.file(directory, checksum)
}

object ServerCatalogs {
    @Volatile private var loaded: ServerCatalog? = null
    @Volatile private var attempted = false

    private fun log(message: String) = println("${Instant.now()} Card catalog: $message")

    private fun directory(): File? = (System.getProperty("atw.catalogDir") ?: System.getenv("CATALOG_DIR"))
        ?.takeIf { it.isNotBlank() }?.let(::File)

    /** The current catalog, read once (null when none is configured or it can't be read, logged). */
    fun current(): ServerCatalog? {
        if (attempted) return loaded
        synchronized(this) {
            if (!attempted) {
                loaded = load()
                attempted = true
            }
        }
        return loaded
    }

    /** Reads the catalog in the background at start, so the first request doesn't wait for it. */
    @JvmStatic fun preload() {
        Thread({ current() }, "card-catalog-load").apply { isDaemon = true }.start()
    }

    private fun load(): ServerCatalog? {
        val directory = directory() ?: return null.also { log("none configured (CATALOG_DIR); set pods and the catalog endpoints are unavailable") }
        return try {
            val checksum = File(directory, "current").readText().trim()
            val file = checkNotNull(file(directory, checksum)) { "no file for the current catalog $checksum" }
            val started = System.nanoTime()
            val catalog = CardCatalogFiles.read(file)
            val header = catalog.header
            val version = CatalogVersion(checksum, file.length(), header.mtgjsonVersion, header.mtgjsonDate, header.sets, header.faces,
                header.printings)
            log("$checksum from MTGJSON ${header.mtgjsonVersion}: ${header.sets} sets " +
                "(${catalog.sets.count { it.draftable && it.automatum }} draftable by Automatum), ${header.faces} faces, " +
                "${header.printings} printings, ${file.length()} bytes, read in ${(System.nanoTime() - started) / 1_000_000} ms")
            ServerCatalog(directory, catalog, version)
        } catch (failure: Exception) {
            log("not read from $directory: ${failure::class.java.simpleName}: ${failure.message}")
            null
        }
    }

    /** The gzip file of catalog [checksum] (the current one or the one kept before it), or null. */
    fun file(checksum: String): File? = directory()?.let { file(it, checksum) }

    internal fun file(directory: File, checksum: String): File? =
        if (!CardCatalogFiles.valid(checksum)) null else File(directory, checksum + CardCatalogFiles.SUFFIX).takeIf { it.isFile }
}
