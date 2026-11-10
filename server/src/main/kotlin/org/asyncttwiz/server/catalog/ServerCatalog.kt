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
import org.asyncttwiz.catalog.CatalogCards
import org.asyncttwiz.catalog.CatalogDungeons
import org.asyncttwiz.catalog.CatalogPrintingRecord
import org.asyncttwiz.protocol.BasicLand
import org.asyncttwiz.protocol.CardDefinition
import org.asyncttwiz.protocol.CardListSupport
import org.asyncttwiz.protocol.DeckJson
import org.asyncttwiz.protocol.PhysicalCardFaces
import org.asyncttwiz.protocol.SubmittedDefinition

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

    /** LOB-124 (I-D51): whether Automatum plays the card of printing [printingId] fully, by the catalog's mark of its card
     * (SYS-082), the mark the app reads: a token, a printing the catalog doesn't have, or none (null) isn't. */
    fun supported(printingId: String?): Boolean =
        printingId?.let { catalog.printing[it] }?.let { !it.token && catalog.faces[it.face].automatum } == true

    /** LOB-124: the support of a deck's or cube's [cards] (each its printing id, or null for a card without one, and its
     * name): the names of those not [supported], each once, sorted. */
    fun support(cards: Collection<Pair<String?, String>>): CardListSupport = CardListSupport(catalog.header.parserVersion,
        cards.filterNot { supported(it.first) }.map { it.second }.distinct().sorted(), catalog.header.engineVersion)

    /** Owner 2026-11-10 (card data from the server's catalog, step 4): the printing a saved card named [name] (case
     * ignored) is found again by when its own printing is missing: the front face of a card of that name, not a token,
     * preferring a paper, not promo printing, then the newest set, the lowest collector number, the id; null when the
     * catalog has no card of that name. */
    fun printingNamed(name: String): String? = byName[name.lowercase()]

    private val byName: Map<String, String> by lazy {
        val order = compareBy<CatalogPrintingRecord>({ it.promo }, { it.onlineOnly || "paper" !in it.availability })
            .thenByDescending { catalog.set[it.set]?.released.orEmpty() }
            .thenBy { it.number.toIntOrNull() ?: Int.MAX_VALUE }.thenBy { it.number }.thenBy { it.id }
        catalog.printings.filter { !it.token && catalog.faces[it.face].side.let { side -> side == "" || side == "a" } }
            .groupBy { CatalogCards.cardName(catalog.faces[it.face]).lowercase() }
            .mapValues { (_, printings) -> printings.minWith(order).id }
    }

    /** The gzip file of catalog [checksum] in this directory (the current one or the one kept before it), or null. */
    fun file(checksum: String): File? = ServerCatalogs.file(directory, checksum)

    /** The card data of the physical card [printingId] is a face of, as a deck or cube holds it (owner 2026-11-10: card
     * data from the server's catalog): the card a device made of it before ([PhysicalCardFaces], so its digest is the
     * one it always had); null for a printing the catalog doesn't have. */
    fun card(printingId: String): CardDefinition? = catalog.printing[printingId]?.let { printing ->
        PhysicalCardFaces.normalize(CatalogCards.definition(catalog, printing))
    }

    /** LOB-052: the five basic lands the server supplies with pod decks, the printings the phones used to: English M21
     * paper, not promo or online-only, of one face, the lowest collector number (then id); without the catalog's build
     * (sourceVersion), so a new catalog alone doesn't change them. Null when the catalog lacks one. */
    val basics: Map<BasicLand, SubmittedDefinition>? by lazy {
        val m21 = catalog.bySet[BASICS_SET].orEmpty()
        BasicLand.entries.associateWith { land ->
            val printing = m21.filter { catalog.faces[it.face].name == land.cardName && !it.token && it.others.isEmpty() &&
                !it.promo && !it.onlineOnly && "paper" in it.availability }
                .minWithOrNull(compareBy<CatalogPrintingRecord> { it.number.toIntOrNull() ?: Int.MAX_VALUE }.thenBy { it.number }.thenBy { it.id })
                ?: return@lazy null.also { ServerCatalogs.log("no M21 ${land.cardName} for pod decks in catalog ${version.checksum}") }
            supplied(printing)
        }
    }

    /** DRF-051: the Cogwork Librarian a pod with Librarians starts its players with, the printing the phones used to (CNS
     * first, then by id), without the catalog's build; null when the catalog has none. */
    val librarian: SubmittedDefinition? by lazy {
        catalog.printings.filter { !it.token && catalog.faces[it.face].name == LIBRARIAN }
            .minWithOrNull(compareBy<CatalogPrintingRecord> { if (it.set.equals("CNS", true)) 0 else 1 }.thenBy { it.id })
            ?.let(::supplied)
    }

    /** LOB-117: the dungeons Automatum plays fully, brought outside the game with the Ready of a deck that ventures. */
    val dungeons: List<SubmittedDefinition> by lazy { CatalogDungeons.definitions(catalog) }

    private fun supplied(printing: CatalogPrintingRecord): SubmittedDefinition =
        CatalogCards.definition(catalog, listOf(printing)).copy(sourceVersion = null).let { SubmittedDefinition(DeckJson.digest(it), it) }

    private companion object {
        const val BASICS_SET = "M21"
        const val LIBRARIAN = "Cogwork Librarian"
    }
}

object ServerCatalogs {
    @Volatile private var loaded: ServerCatalog? = null
    @Volatile private var attempted = false

    internal fun log(message: String) = println("${Instant.now()} Card catalog: $message")

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

    /** Reads the catalog in the background at start, so the first request doesn't wait for it; then finds the saved
     * cards whose printing it lacks again by name ([org.asyncttwiz.server.decks.SavedPrintings]). */
    @JvmStatic fun preload(runtime: org.asyncttwiz.server.ServiceRuntime) {
        Thread({ current()?.let { org.asyncttwiz.server.decks.SavedPrintings.afterLoad(runtime, it) } }, "card-catalog-load")
            .apply { isDaemon = true }.start()
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
