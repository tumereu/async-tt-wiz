package org.asyncttwiz.catalog

import java.io.File
import java.io.InputStream
import java.io.PrintStream
import java.util.zip.GZIPInputStream
import kotlin.system.exitProcess

/**
 * The catalog command (SYS-081), run through `./scripts/gradle.sh :catalog:catalogCommand
 * --args="..."` by `scripts/catalog-pin.sh`, the release, and the e2e and dev stacks:
 *
 * - `verify`: the raw MTGJSON file against the pin.
 * - `build --out <dir>`: verifies the raw file, builds the trimmed catalog with its support marks into `<dir>` as
 *   `<checksum>.jsonl.gz` (pointed to by `<dir>/current`, the one before it kept as `previous`) and checks that every
 *   set it marks draftable builds its packs. The checksum in `current` is what the release builds into the app
 *   (SYS-081). `--no-pin` builds from any file (the test cards' catalogs) without the pin.
 * - `move`: makes the pin name the raw file.
 * - `ratings-fetch --out <file>`: fetches the draft bots' card ratings of every supported set from 17Lands' public game
 *   data into `<file>` ([CatalogRatings.fetch]); `ratings-move`: makes the pin name the ratings file.
 * - `compare-support --old <file|dir> --new <file|dir> [--allow <count>]`: lists the cards the old catalog marks as
 *   supported and the new one doesn't (a directory means its `current` catalog). Exit code 3 when there are any, unless
 *   `--allow` names exactly their count: the release's guard against dropping support for cards (owner, 2026-11-19).
 *
 * Options: `--pin <file>` (default `catalog/pin.properties`), `--file <AllPrintings.json[.gz]>` (default
 * `$ATW_ALLPRINTINGS`, else `~/projects/atw-cache/AllPrintings.json.gz`), `--sets <file>` and `--cards <file>` (the support
 * lists, default the Arcanum tools' `supported-sets.txt` and `supported-cards.txt`), `--report <file>` (every listed card
 * with what Automatum leaves to the players), `--ratings <file>` (the card ratings, default `$ATW_RATINGS`, else
 * `~/projects/atw-cache/17lands-ratings.tsv.gz`; a build with the pin reads them when the pin names them, `--no-pin` only
 * when given).
 */
fun main(args: Array<String>) {
    exitProcess(CatalogCommand(System.out).run(args.toList()))
}

class CatalogCommand(private val out: PrintStream, private val root: File = File(System.getProperty("atw.catalogRoot") ?: ".")) {
    fun run(args: List<String>): Int {
        val command = args.firstOrNull() ?: return usage()
        val options = HashMap<String, String>()
        val flags = HashSet<String>()
        var index = 1
        while (index < args.size) {
            val key = args[index]
            when {
                key == "--no-pin" -> flags += key
                key.startsWith("--") && index + 1 < args.size -> { options[key] = args[index + 1]; index++ }
                else -> return usage()
            }
            index++
        }
        val pinFile = File(options["--pin"] ?: File(root, "catalog/pin.properties").path)
        val file = File(options["--file"] ?: System.getenv("ATW_ALLPRINTINGS")?.takeIf { it.isNotBlank() }
            ?: (System.getProperty("user.home") + "/projects/atw-cache/AllPrintings.json.gz"))
        val tools = File(root, "arcanum-tools/src/main/resources/arcanum/tools")
        val sets = File(options["--sets"] ?: File(tools, "supported-sets.txt").path)
        val cards = File(options["--cards"] ?: File(tools, "supported-cards.txt").path)
        val ratingsFile = File(options["--ratings"] ?: System.getenv("ATW_RATINGS")?.takeIf { it.isNotBlank() }
            ?: (System.getProperty("user.home") + "/projects/atw-cache/17lands-ratings.tsv.gz"))
        return when (command) {
            "verify" -> {
                val pin = CatalogPin.read(pinFile)
                verify(pin, file)?.let { return fail(it) }
                out.println("$file is the pinned MTGJSON file.")
                if (pin.ratingsSha256 != null) {
                    verifyRatings(pin, ratingsFile)?.let { return fail(it) }
                    out.println("$ratingsFile is the pinned card ratings file.")
                }
                0
            }
            "build" -> {
                val output = File(options["--out"] ?: return usage())
                val pin = if ("--no-pin" in flags) null else CatalogPin.read(pinFile)
                pin?.let { verify(it, file) }?.let { return fail(it) }
                pin?.takeIf { it.ratingsSha256 != null }?.let { verifyRatings(it, ratingsFile) }?.let { return fail(it) }
                val ratings = if (pin?.ratingsSha256 != null || "--ratings" in options) CatalogRatings.read(ratingsFile) else null
                val built = build(file, sets, cards, options["--report"]?.let(::File), ratings)
                publish(built.catalog, output)
                0
            }
            "ratings-fetch" -> {
                val target = File(options["--out"] ?: return usage())
                val ratings = CatalogRatings.fetch(lines(sets).map { it.substringBefore(' ') }) { out.println(it) }
                CatalogRatings.write(ratings, target)
                out.println("Wrote $target: ${ratings.sets.size} sets with ratings" +
                    if (ratings.missing.isEmpty()) "" else "; no data at 17Lands for ${ratings.missing.joinToString(" ")}")
                0
            }
            "ratings-move" -> {
                if (!ratingsFile.isFile) return fail("No card ratings file at $ratingsFile to pin.")
                val pin = CatalogPin.read(pinFile)
                val sha = CardCatalogFiles.sha256(ratingsFile)
                val date = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString()
                pin.copy(ratingsDate = date, ratingsSha256 = sha).write(pinFile)
                out.println("The pin names the card ratings of $date (SHA-256 $sha).")
                0
            }
            "move" -> {
                if (!file.isFile) return fail("No MTGJSON file at $file to move the pin to.")
                val metadata = open(file).use(org.asyncttwiz.storage.MtgJsonCatalog()::peekMetadata)
                val raw = CardCatalogFiles.sha256(file)
                val old = pinFile.takeIf { it.isFile }?.let(CatalogPin::read)
                CatalogPin(metadata.version, metadata.date, raw, old?.ratingsDate, old?.ratingsSha256).write(pinFile)
                out.println("The pin names MTGJSON ${metadata.version} (SHA-256 $raw).")
                0
            }
            "compare-support" -> {
                val old = catalogFile(File(options["--old"] ?: return usage())) ?: return fail("No catalog at ${options["--old"]}")
                val new = catalogFile(File(options["--new"] ?: return usage())) ?: return fail("No catalog at ${options["--new"]}")
                val allow = options["--allow"]?.let { it.toIntOrNull() ?: return usage() } ?: 0
                compareSupport(old, new, allow)
            }
            else -> usage()
        }
    }

    /** [path] itself, or the catalog a directory's `current` names; null when there is none. */
    private fun catalogFile(path: File): File? {
        if (path.isFile) return path
        val current = File(path, "current").takeIf { it.isFile }?.readText()?.trim() ?: return null
        return File(path, current + CardCatalogFiles.SUFFIX).takeIf { CardCatalogFiles.valid(current) && it.isFile }
    }

    /** Lists the cards [old] supports and [new] doesn't; 0 when there are none or [allow] is their count, else 3. */
    private fun compareSupport(old: File, new: File, allow: Int): Int {
        val before = supportedCards(old)
        val after = supportedCards(new)
        val dropped = (before - after).sorted()
        if (dropped.isEmpty()) {
            out.println("No card loses Automatum support: ${before.size} supported before, ${after.size} now.")
            return 0
        }
        out.println("${dropped.size} cards Automatum supports in ${old.name} are no longer supported in ${new.name}:")
        dropped.forEach { out.println("  $it") }
        if (allow == dropped.size) {
            out.println("Allowed: dropping support for these ${dropped.size} cards was confirmed.")
            return 0
        }
        if (allow != 0) out.println("The confirmation names $allow cards, not ${dropped.size}.")
        return 3
    }

    /** The names of the cards [file]'s catalog marks as supported ([CatalogFaceRecord.automatum]), checked against the
     * checksum its name gives. */
    private fun supportedCards(file: File): Set<String> {
        val names = HashSet<String>()
        val checksum = file.inputStream().use { input ->
            CardCatalogFiles.stream(input, recipes = false) { record ->
                if (record is CatalogFaceRecord && record.automatum) names += CatalogCards.cardName(record)
            }
        }
        val named = file.name.removeSuffix(CardCatalogFiles.SUFFIX)
        check(!CardCatalogFiles.valid(named) || named == checksum) { "Catalog ${file.name} has checksum $checksum" }
        return names
    }

    /** Why [file] isn't the pinned raw file, or null when it is. */
    private fun verify(pin: CatalogPin, file: File): String? {
        if (!file.isFile) return pin.missing(file)
        val actual = CardCatalogFiles.sha256(file)
        return if (actual == pin.allPrintingsSha256) null else pin.missing(file, actual)
    }

    /** Why [file] isn't the pinned card ratings file, or null when it is. */
    private fun verifyRatings(pin: CatalogPin, file: File): String? {
        if (!file.isFile) return pin.missingRatings(file)
        val actual = CardCatalogFiles.sha256(file)
        return if (actual == pin.ratingsSha256) null else pin.missingRatings(file, actual)
    }

    private fun build(file: File, sets: File, cards: File, report: File?, ratings: CatalogRatings.Ratings?): CatalogBuilder.Built {
        if (!file.isFile) throw IllegalStateException("No MTGJSON file at $file")
        val builder = CatalogBuilder(lines(sets).map { it.substringBefore(' ') }.toSet(), lines(cards).toSet(),
            ratings?.sets.orEmpty().mapValues { (_, counts) -> CatalogRatings.table(counts) }) { out.println(it) }
        val built = builder.build { open(file) }
        val catalog = built.catalog
        // Every set the marks say can be drafted builds its packs (the server does the same for a pod).
        val draftable = catalog.sets.filter { it.draftable && it.automatum }
        draftable.forEach { set -> checkNotNull(CatalogSetSources.build(catalog, set.code)) { "Set ${set.code} has no cards for packs" } }
        val unsupported = built.listed.filter { it.parts.isNotEmpty() }
        out.println("Catalog: ${catalog.header.sets} sets (${draftable.size} draftable by Automatum: ${draftable.joinToString(" ") { it.code }}), " +
            "${catalog.header.faces} faces, ${catalog.header.printings} printings, ${catalog.header.recipes} recipes")
        out.println("Listed cards: ${built.listed.size}, ${unsupported.size} with parts Automatum leaves to the players")
        if (built.missingSets.isNotEmpty()) out.println("Listed sets the file doesn't have: ${built.missingSets.joinToString(" ")}")
        if (built.unknownNames.isNotEmpty()) out.println("Listed names no card has: ${built.unknownNames.joinToString(" | ")}")
        if (ratings != null) {
            val rated = catalog.sets.filter { it.ratings.isNotEmpty() }
            out.println("Card ratings: ${rated.joinToString(" ") { "${it.code} ${it.ratings.size}" }}" +
                ratings.missing.takeIf { it.isNotEmpty() }?.let { "; no 17Lands data for ${it.joinToString(" ")}" }.orEmpty())
        }
        catalog.sets.filter { it.draftable && !it.automatum && it.code in lines(sets).map { line -> line.substringBefore(' ') } }.forEach {
            out.println("Listed set ${it.code} can't be drafted: ${it.unsupported.size} pack cards unsupported (${it.unsupported.take(12).joinToString(", ")})")
        }
        report?.let { target ->
            target.parentFile?.mkdirs()
            target.writeText(built.listed.joinToString("") { card ->
                "${card.name}\t${card.sets.joinToString(",")}\t${if (card.onList) "list" else ""}\t" +
                    (if (card.parts.isEmpty()) "supported" else card.parts.joinToString(" | ")) + "\n"
            })
            out.println("Report: $target")
        }
        return built
    }

    /** Writes [catalog] into [directory], points `current` to it and keeps the one before as `previous` (others go). */
    private fun publish(catalog: CardCatalog, directory: File): File {
        val written = CardCatalogFiles.write(catalog, directory)
        val checksum = written.name.removeSuffix(CardCatalogFiles.SUFFIX)
        val current = File(directory, "current")
        val before = current.takeIf { it.isFile }?.readText()?.trim()?.takeIf { it != checksum && CardCatalogFiles.valid(it) }
        before?.let { File(directory, "previous").writeText(it + "\n") }
        File(directory, "current.part").apply { writeText(checksum + "\n") }.renameTo(current)
        val keep = setOfNotNull(checksum, before ?: File(directory, "previous").takeIf { it.isFile }?.readText()?.trim())
        directory.listFiles { f -> f.name.endsWith(CardCatalogFiles.SUFFIX) && f.name.removeSuffix(CardCatalogFiles.SUFFIX) !in keep }
            ?.forEach { it.delete() }
        out.println("Wrote $written (${written.length()} bytes); current catalog $checksum")
        return written
    }

    private fun open(file: File): InputStream {
        val input = file.inputStream().buffered(1 shl 16)
        return if (file.name.endsWith(".gz")) GZIPInputStream(input, 1 shl 16) else input
    }

    private fun lines(file: File): List<String> {
        check(file.isFile) { "No support list at $file" }
        return file.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
    }

    private fun fail(message: String): Int { out.println(message); return 1 }

    private fun usage(): Int = fail("Usage: verify | build --out <dir> [--no-pin] | move | ratings-fetch --out <file> | ratings-move | compare-support --old <file|dir> --new <file|dir> [--allow <count>]; options --pin, --file, --sets, --cards, --report, --ratings")
}
