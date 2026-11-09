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

data class DraftableSet(val code: String, val name: String, val releaseDate: String, val cardCount: Long,
    val type: String? = null) {
    val draftable: Boolean get() = type == null || type in setOf("expansion", "core", "masters", "draft_innovation")
}
enum class MissingSetCardReason { CATALOG, SOURCE_FILE }
data class MissingSetCard(val uuid: String, val setCode: String?, val reason: MissingSetCardReason)
data class SetSourceUpload(val snapshot: SetSourceSnapshot, val definitions: SubmittedDefinitions,
    val missingCards: List<MissingSetCard>, val warnings: List<String>)

/** A set that can't be loaded as a draft source; [playerMessage] says why in words a player reads, [message] is for the log. */
class SetSourceProblem(message: String, val playerMessage: String, cause: Throwable? = null) : IllegalStateException(message, cause)

/** What the player reads when loading set [code] failed with [failure]. */
fun setSourceFailureMessage(code: String, failure: Exception): String = when (failure) {
    is SetSourceProblem -> failure.playerMessage
    is CatalogNotInstalledException -> failure.message.orEmpty()
    is IllegalArgumentException -> "$code isn't in your card records. Check for updates in Settings → Card records."
    else -> "Could not load source."
}

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
    /** The load's steps and refusals, for the app log (what reaches a problem report). */
    private val log: (String) -> Unit = {},
) {
    init {
        require(compressedLimit in 1..HttpCatalogSource.MAX_SET_BYTES)
        require(decodedLimit in 1..HttpCatalogSource.MAX_DECODED_SET_BYTES)
    }
    private val lock = locks.computeIfAbsent(directory.canonicalPath) { Any() }
    // Cache only a successful backfill; lazy retries a failed initializer on the next picker open.
    private val enrichMetadata by lazy {
        catalog.enrichSets(source.setList().sets)
    }

    fun sets(): List<DraftableSet> {
        val installed = catalog.draftableSets()
        if (installed.any { it.type == null || it.name == it.code }) runCatching { enrichMetadata }
        return catalog.draftableSets().filter { it.draftable }
    }
    fun isCached(code: String): Boolean = File(directory, "${catalogSetFilename(code)}.json.gz").isFile
    /** Loads [code] from its kept file, downloading it when needed. DRF-089: when cards turn out missing and the set
     * wasn't downloaded today, it is redownloaded once (with its recipe's dependencies) before they are reported. */
    fun load(code: String): SetSourceUpload = synchronized(lock) {
        val upload = load(code, false)
        if (upload.missingCards.isEmpty() || downloadedToday(code)) upload else {
            log("$code: ${upload.missingCards.size} cards missing and the file wasn't downloaded today: downloading it again")
            load(code, true)
        }
    }

    /** Also refresh the recipe's dependencies, so a redownload really replaces its source. */
    fun redownload(code: String): SetSourceUpload = load(code, true)

    private fun downloadedToday(code: String): Boolean {
        val file = File(directory, "${catalogSetFilename(code)}.json.gz")
        return file.isFile && java.time.Instant.ofEpochMilli(file.lastModified()).atZone(clock.zone).toLocalDate() == LocalDate.now(clock)
    }

    private fun load(code: String, force: Boolean): SetSourceUpload = synchronized(lock) {
        catalogSetFilename(code)
        val installed = catalog.metadata() ?: throw CatalogNotInstalledException()
        log("$code: loading (force=$force, card records ${installed.date}/${installed.version})")
        // Recipe dependencies may be non-draftable supplemental sets; keep their refresh dates.
        val sets = catalog.draftableSets().associateBy { it.code }
        require(code in sets) { "Set $code has no installed non-token cards" }
        check(directory.isDirectory || directory.mkdirs())
        val name = sets[code]?.name ?: code
        val main = acquire(code, name, sets[code]?.releaseDate, force)
        val referenced = main.recipe?.sheets?.values?.flatMap { it.cards.keys }?.toSet().orEmpty()
        val files = linkedMapOf(code to main)
        val uuids = main.cards.map { it.uuid }.toMutableSet()
        log("$code: recipe ${main.recipe?.name ?: "none"}, ${main.recipe?.sheets?.size ?: 0} sheets, ${referenced.size} cards on them, " +
            "from sets ${main.recipe?.sourceSetCodes.orEmpty()}")
        main.recipe?.sourceSetCodes.orEmpty().forEach { dependency ->
            if (dependency !in files) {
                val file = acquire(dependency, sets[dependency]?.name ?: dependency, sets[dependency]?.releaseDate, force, referenced)
                file.cards.forEach {
                    uuids += it.uuid
                    if (uuids.size > SetSourceValidation.MAX_CARDS) throw tooManyCards(code, name)
                }
                log("$code: dependency $dependency gives ${file.cards.size} cards (${if (dependency in sets) "in" else "not in"} the set list)")
                files[dependency] = file
            }
        }
        buildUpload(main, name, files.values.toList())
    }

    private fun tooManyCards(code: String, name: String): SetSourceProblem {
        log("$code: refused, over ${SetSourceValidation.MAX_CARDS} cards")
        return SetSourceProblem("Set source card limit", "$name has too many cards to use as a draft source.")
    }

    /** [block], whose failure tells the player [playerMessage] (unless it already says something better) and goes in the log. */
    private inline fun <T> telling(code: String, playerMessage: String, block: () -> T): T = try {
        block()
    } catch (failure: Exception) {
        if (failure is SetSourceProblem || failure is CatalogNotInstalledException) throw failure
        log("$code: refused: ${failure::class.java.simpleName}: ${failure.message}")
        throw SetSourceProblem(failure.message ?: playerMessage, playerMessage, failure)
    }

    private fun acquire(code: String, name: String, releaseDate: String?, force: Boolean, wanted: Set<String>? = null): SetFile {
        val file = File(directory, "${catalogSetFilename(code)}.json.gz")
        val today = LocalDate.now(clock)
        val recent = releaseDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?.let { !it.isBefore(today.minusDays(60)) } == true
        // MTGJSON publishes daily builds. Reopening a preview today should still be a cache hit.
        val downloaded = file.lastModified().let { java.time.Instant.ofEpochMilli(it).atZone(clock.zone).toLocalDate() }
        val refresh = recent && downloaded != today
        if (file.isFile && !force && !refresh) {
            log("$code: using the kept file, ${file.length()} bytes, downloaded $downloaded")
            return parse(file, code, name, wanted)
        }
        return telling(code, "Could not download the cards of $name. Check your connection and try again.") {
            download(code, name, file, wanted, force, refresh)
        }
    }

    private fun download(code: String, name: String, file: File, wanted: Set<String>?, force: Boolean, refresh: Boolean): SetFile {
        val remote = source.setSource(code)
        val size = remote.size()
        log("$code: downloading (force=$force, refresh=$refresh), $size bytes")
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
            log("$code: downloaded $count bytes")
            val parsed = parse(temporary, code, name, wanted)
            check(temporary.setLastModified(clock.millis()))
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            return parsed
        } finally { temporary.delete() }
    }

    private fun parse(file: File, code: String, name: String, wanted: Set<String>?): SetFile =
        telling(code, "The card file of $name could not be read. Try again later.") {
            check(file.length() <= compressedLimit) { "Set compressed size limit" }
            try {
                file.inputStream().use { input ->
                    GZIPInputStream(input, 64 * 1024).use { gzip ->
                        MtgJsonSetSource().parse(LimitedInput(gzip, decodedLimit), code, wanted)
                    }
                }
            } catch (_: SetCardLimitExceeded) {
                throw tooManyCards(code, name)
            } catch (failure: SetSheetLimitExceeded) {
                log("$code: refused: ${failure.message}")
                throw SetSourceProblem(failure.message.orEmpty(),
                    "$name can't be used as a draft source yet: its boosters are more complicated than the app can handle.", failure)
            }.also { log("$code: file read, ${it.cards.size} cards${wanted?.let { w -> " of ${w.size} wanted" }.orEmpty()}") }
        }

    private fun buildUpload(main: SetFile, name: String, files: List<SetFile>): SetSourceUpload =
        telling(main.code, "$name can't be used as a draft source: its card data could not be read.") {
            prepareUpload(main, name, files)
        }

    private fun prepareUpload(main: SetFile, name: String, files: List<SetFile>): SetSourceUpload = catalog.snapshot { index ->
        if (index == null) throw CatalogNotInstalledException()
        val metadata = index.metadata()
        val sourceCards = linkedMapOf<String, Pair<String, SetFileCard>>()
        files.forEach { file -> file.cards.forEach { card ->
            if (sourceCards.size >= SetSourceValidation.MAX_CARDS && card.uuid !in sourceCards) throw tooManyCards(main.code, name)
            val previous = sourceCards.putIfAbsent(card.uuid, file.code to card)
            check(previous == null || previous.second == card) { "Conflicting set card UUID: ${card.uuid}" }
        } }
        val missing = linkedMapOf<String, MissingSetCard>()
        val definitions = linkedMapOf<String, SubmittedDefinition>()
        val byPrinting = mutableMapOf<String, SubmittedDefinition>()
        var meldResults = 0
        val cards = sourceCards.values.mapNotNull { (code, card) ->
            check(!Thread.currentThread().isInterrupted)
            val printing = index.printing(card.uuid)?.takeUnless { it.token }
            if (printing == null) {
                missing[card.uuid] = MissingSetCard(card.uuid, code, MissingSetCardReason.CATALOG)
                return@mapNotNull null
            }
            val linked = (printing.otherFaces + printing.id).distinct().map { id ->
                checkNotNull(index.printing(id)) { "Missing catalog face $id" }
            }.sortedWith(compareBy<CatalogPrinting> { it.side }.thenBy { it.id })
            // 7ZZC89: a meld result is the back of both its fronts, never a card of its own: no pack holds it
            if (meldResult(printing, linked)) {
                meldResults++
                return@mapNotNull null
            }
            val submitted = byPrinting[printing.id] ?: run {
                val faces = physicalSides(linked)
                val definition = ResolvedDeckEntry(DeckSection.MAINBOARD, 1, faces, emptyList()).definition(metadata)
                if (!DeckValidation.definition(definition)) {
                    log("${main.code}: refused, card data of ${printing.name} ${card.uuid} (${printing.set} ${printing.number}) isn't valid, " +
                        "layout ${definition.layout}, faces ${definition.faces.map { "${it.faceId} ${it.name} other=${it.otherFaceIds}" }}")
                    throw SetSourceProblem("Invalid catalog definition for ${card.uuid}",
                        "$name can't be used as a draft source: the card data of ${printing.name} could not be read.")
                }
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
                log("${main.code}: recipe dropped after excluding ${missing.size} missing cards: $issues")
                recipe = null
            }
        }
        val snapshot = SetSourceSnapshot(main.code, main.name, normalized, recipe)
        if (normalized.isEmpty()) {
            log("${main.code}: refused, no usable cards, ${missing.size} missing")
            throw SetSourceProblem("No usable cards for ${main.code}. Update installed card records; missing UUIDs: ${missing.keys}",
                "None of the cards of $name are in your card records. Check for updates in Settings → Card records.")
        }
        check(snapshot.validate().isEmpty()) { "Invalid set source ${main.code}: ${snapshot.validate()}; missing cards: ${missing.keys}" }
        if (missing.isNotEmpty()) warnings += "${missing.size} cards are unavailable and excluded. Check for updates in Settings → Card records."
        log("${main.code}: ready, ${normalized.size} cards, ${definitions.size} definitions, $meldResults meld results left out, " +
            "recipe ${recipe?.name ?: "none"}, ${missing.size} missing ${missing.values.take(MISSING_LOGGED).map { "${it.uuid} ${it.setCode} ${it.reason}" }}, warnings $warnings")
        SetSourceUpload(snapshot, SubmittedDefinitions(definitions.values.toList()), missing.values.toList(), warnings)
    }

    private companion object {
        /** So a mismatch with the card records can't push everything else out of the log. */
        const val MISSING_LOGGED = 20
        val locks = ConcurrentHashMap<String, Any>()
    }
}
