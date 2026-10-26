package org.asyncttwiz.storage

import org.asyncttwiz.protocol.*
import java.io.File
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.time.Clock
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream

data class DraftableSet(val code: String, val name: String, val releaseDate: String, val cardCount: Long)
enum class MissingSetCardReason { CATALOG, SOURCE_FILE }
data class MissingSetCard(val uuid: String, val setCode: String?, val reason: MissingSetCardReason)
data class SetSourceUpload(val snapshot: SetSourceSnapshot, val definitions: SubmittedDefinitions,
    val missingCards: List<MissingSetCard>, val warnings: List<String>)

/** Blocking, worker-only public data acquisition. Use an app-files directory, not an evictable cache.
 * Files survive account changes. Snapshots/definitions are rebuilt from one installed generation,
 * never persisted with stale catalog digests. Network policy belongs to HttpCatalogSource. */
class SetSourceRepository(
    private val directory: File,
    private val catalog: CatalogRepository,
    private val source: CatalogSource = HttpCatalogSource(),
    private val clock: Clock = Clock.systemUTC(),
    private val compressedLimit: Long = HttpCatalogSource.MAX_SET_BYTES,
    private val decodedLimit: Long = HttpCatalogSource.MAX_DECODED_SET_BYTES,
) {
    init {
        require(compressedLimit in 1..HttpCatalogSource.MAX_SET_BYTES)
        require(decodedLimit in 1..HttpCatalogSource.MAX_DECODED_SET_BYTES)
    }
    private val lock = locks.computeIfAbsent(directory.canonicalPath) { Any() }
    // Legacy catalogs lack names. Reuse the incremental updater's small metadata endpoint,
    // at most once per repository; unavailable metadata must not prevent offline drafting.
    private val setNames by lazy {
        runCatching { source.setList().sets.mapNotNull { set -> set.name?.let { set.code to it } }.toMap() }
            .getOrDefault(emptyMap())
    }

    fun sets(): List<DraftableSet> = catalog.draftableSets().map { set ->
        if (set.name == set.code) set.copy(name = setNames[set.code] ?: set.name) else set
    }
    fun isCached(code: String): Boolean = File(directory, "${catalogSetFilename(code)}.json.gz").isFile
    fun load(code: String): SetSourceUpload = load(code, false)

    /** Also refresh the recipe's dependencies, so manual refresh really replaces its source. */
    fun redownload(code: String): SetSourceUpload = load(code, true)

    private fun load(code: String, force: Boolean): SetSourceUpload = synchronized(lock) {
        catalogSetFilename(code)
        if (catalog.metadata() == null) throw CatalogNotInstalledException()
        val sets = sets().associateBy { it.code }
        require(code in sets) { "Set $code has no installed non-token cards" }
        check(directory.isDirectory || directory.mkdirs())
        val main = acquire(code, sets[code]?.releaseDate, force)
        val referenced = main.recipe?.sheets?.values?.flatMap { it.cards.keys }?.toSet().orEmpty()
        val files = linkedMapOf(code to main)
        val uuids = main.cards.map { it.uuid }.toMutableSet()
        main.recipe?.sourceSetCodes.orEmpty().forEach { dependency ->
            if (dependency !in files) {
                val file = acquire(dependency, sets[dependency]?.releaseDate, force, referenced)
                file.cards.forEach {
                    uuids += it.uuid
                    check(uuids.size <= SetSourceValidation.MAX_CARDS) { "Set source card limit" }
                }
                files[dependency] = file
            }
        }
        buildUpload(main, files.values.toList())
    }

    private fun acquire(code: String, releaseDate: String?, force: Boolean, wanted: Set<String>? = null): SetFile {
        val file = File(directory, "${catalogSetFilename(code)}.json.gz")
        val today = LocalDate.now(clock)
        val recent = releaseDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?.let { !it.isBefore(today.minusDays(60)) } == true
        // MTGJSON publishes daily builds. Reopening a preview today should still be a cache hit.
        val downloaded = file.lastModified().let { java.time.Instant.ofEpochMilli(it).atZone(clock.zone).toLocalDate() }
        val refresh = recent && downloaded != today
        if (file.isFile && !force && !refresh) return parse(file, code, wanted)
        val remote = source.setSource(code)
        val size = remote.size()
        check(size == null || size in 1..compressedLimit) { "Set compressed size limit" }
        val checksum = remote.checksum().lowercase()
        check(Regex("[a-f0-9]{64}").matches(checksum)) { "Invalid set checksum" }
        val temporary = File(directory, "${file.name}.part")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var count = 0L
            FileOutputStream(temporary).use { output ->
                val bounded = object : FilterOutputStream(DigestOutputStream(output, digest)) {
                    override fun write(value: Int) {
                        check(++count <= compressedLimit) { "Set compressed size limit" }
                        out.write(value)
                    }
                    override fun write(bytes: ByteArray, offset: Int, length: Int) {
                        count += length
                        check(count <= compressedLimit) { "Set compressed size limit" }
                        out.write(bytes, offset, length)
                    }
                }
                remote.download(bounded) { check(!Thread.currentThread().isInterrupted) }
                bounded.flush()
                output.fd.sync()
            }
            check(size == null || count == size) { "Incomplete set download" }
            check(digest.digest().joinToString("") { "%02x".format(it) } == checksum) { "Set checksum mismatch" }
            val parsed = parse(temporary, code, wanted)
            check(temporary.setLastModified(clock.millis()))
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            return parsed
        } finally { temporary.delete() }
    }

    private fun parse(file: File, code: String, wanted: Set<String>?): SetFile {
        check(file.length() <= compressedLimit) { "Set compressed size limit" }
        return file.inputStream().use { input ->
            GZIPInputStream(input, 64 * 1024).use { gzip ->
                MtgJsonSetSource().parse(LimitedInput(gzip, decodedLimit), code, wanted)
            }
        }
    }

    private fun buildUpload(main: SetFile, files: List<SetFile>): SetSourceUpload = catalog.snapshot { index ->
        if (index == null) throw CatalogNotInstalledException()
        val metadata = index.metadata()
        val sourceCards = linkedMapOf<String, Pair<String, SetFileCard>>()
        files.forEach { file -> file.cards.forEach { card ->
            check(sourceCards.size < SetSourceValidation.MAX_CARDS || card.uuid in sourceCards) { "Set source card limit" }
            val previous = sourceCards.putIfAbsent(card.uuid, file.code to card)
            check(previous == null || previous.second == card) { "Conflicting set card UUID: ${card.uuid}" }
        } }
        val missing = linkedMapOf<String, MissingSetCard>()
        val definitions = linkedMapOf<String, SubmittedDefinition>()
        val byPrinting = mutableMapOf<String, SubmittedDefinition>()
        val cards = sourceCards.values.mapNotNull { (code, card) ->
            check(!Thread.currentThread().isInterrupted)
            val printing = index.printing(card.uuid)?.takeUnless { it.token }
            if (printing == null) {
                missing[card.uuid] = MissingSetCard(card.uuid, code, MissingSetCardReason.CATALOG)
                return@mapNotNull null
            }
            val submitted = byPrinting[printing.id] ?: run {
                val faces = (printing.otherFaces + printing.id).distinct().map { id ->
                    checkNotNull(index.printing(id)) { "Missing catalog face $id" }
                }.sortedWith(compareBy<CatalogPrinting> { it.side }.thenBy { it.id })
                val definition = ResolvedDeckEntry(DeckSection.MAINBOARD, 1, faces, emptyList()).definition(metadata)
                check(DeckValidation.definition(definition)) { "Invalid catalog definition for ${card.uuid}" }
                SubmittedDefinition(DeckJson.digest(definition), definition).also { entry ->
                    faces.forEach { byPrinting[it.id] = entry }
                }
            }
            definitions[submitted.digest] = submitted
            SetSourceCard(submitted.digest, card.uuid, card.rarity, card.colors)
        }
        // Both MTGJSON face UUIDs can resolve to one physical definition. Give them the same
        // whole-card colors, as required by the protocol, rather than inconsistent face colors.
        val colors = cards.groupBy { it.definition }.mapValues { (_, entries) -> entries.flatMap { it.colors }.toSet() }
        val normalized = cards.map { it.copy(colors = colors.getValue(it.definition)) }
        val available = cards.map { it.uuid }.toSet()
        val warnings = mutableListOf<String>()
        var recipe = main.recipe?.let { recipe ->
            recipe.copy(sheets = recipe.sheets.mapValues { (_, sheet) ->
                val kept = sheet.cards.filterKeys { uuid ->
                    if (uuid !in available && uuid !in missing)
                        missing[uuid] = MissingSetCard(uuid, null, MissingSetCardReason.SOURCE_FILE)
                    uuid in available
                }
                sheet.copy(cards = kept, totalWeight = kept.values.sum())
            })
        }
        if (recipe != null && normalized.isNotEmpty()) {
            val issues = SetSourceSnapshot(main.code, main.name, normalized, recipe).validate()
            if (issues.isNotEmpty() && missing.isNotEmpty() && issues.all {
                    it in setOf(SetSourceIssue.SHEET, SetSourceIssue.WEIGHT, SetSourceIssue.SHEET_CAPACITY)
                }) {
                warnings += "Booster recipe cannot be used after excluding missing cards; packs will be approximate."
                recipe = null
            }
        }
        val snapshot = SetSourceSnapshot(main.code, main.name, normalized, recipe)
        check(normalized.isNotEmpty()) { "No usable cards for ${main.code}. Update installed card records; missing UUIDs: ${missing.keys}" }
        check(snapshot.validate().isEmpty()) { "Invalid set source ${main.code}: ${snapshot.validate()}; missing cards: ${missing.keys}" }
        if (missing.isNotEmpty()) warnings += "${missing.size} cards are unavailable and excluded. Update installed card records or redownload the set."
        SetSourceUpload(snapshot, SubmittedDefinitions(definitions.values.toList()), missing.values.toList(), warnings)
    }

    private companion object {
        val locks = ConcurrentHashMap<String, Any>()
    }
}
