package org.asyncttwiz.catalog

import java.io.File
import java.io.InputStream
import java.io.PrintStream
import java.util.zip.GZIPInputStream
import kotlin.system.exitProcess

/**
 * The catalog command (docs/specs/web-client.md section 3), run through `./scripts/gradle.sh :catalog:catalogCommand
 * --args="..."` by `scripts/catalog-pin.sh`, the release, and the e2e and dev stacks:
 *
 * - `verify`: the raw MTGJSON file against the pin.
 * - `build --out <dir>`: verifies the raw file, builds the trimmed catalog with its support marks into `<dir>` as
 *   `<checksum>.jsonl.gz` (pointed to by `<dir>/current`, the one before it kept as `previous`), checks that every set
 *   it marks draftable builds its packs, and stops with 1 when its checksum differs from the pin's. `--no-pin` builds
 *   from any file (the test cards' catalogs) without the pin.
 * - `update`: records in the pin the catalog the pinned raw file builds now (after a parser, engine or support list change
 *   that changes it: section 3.3, a change in what we extract is a new version).
 * - `move`: makes the pin name the raw file (and the catalog built from it).
 *
 * Options: `--pin <file>` (default `catalog/pin.properties`), `--file <AllPrintings.json[.gz]>` (default
 * `$ATW_ALLPRINTINGS`, else `~/projects/atw-cache/AllPrintings.json.gz`), `--sets <file>` and `--cards <file>` (the support
 * lists, default the Arcanum tools' `supported-sets.txt` and `supported-cards.txt`), `--report <file>` (every listed card
 * with what Automatum leaves to the players).
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
        return when (command) {
            "verify" -> verify(CatalogPin.read(pinFile), file)?.let { fail(it) } ?: 0.also { out.println("$file is the pinned MTGJSON file.") }
            "build" -> {
                val output = File(options["--out"] ?: return usage())
                val pin = if ("--no-pin" in flags) null else CatalogPin.read(pinFile)
                pin?.let { verify(it, file) }?.let { return fail(it) }
                val built = build(file, sets, cards, options["--report"]?.let(::File))
                val written = publish(built.catalog, output)
                val checksum = written.name.removeSuffix(CardCatalogFiles.SUFFIX)
                if (pin != null && checksum != pin.catalogSha256) return fail("The catalog built from the pinned file has checksum " +
                    "$checksum, the pin says ${pin.catalogSha256}: what the build makes of the cards changed (a parser, engine or support list " +
                    "change). If that is intended, record it with scripts/catalog-pin.sh update, and commit the pin.")
                0
            }
            "update" -> {
                val pin = CatalogPin.read(pinFile)
                verify(pin, file)?.let { return fail(it) }
                val built = build(file, sets, cards, options["--report"]?.let(::File))
                val checksum = CardCatalogFiles.write(built.catalog, java.io.OutputStream.nullOutputStream())
                pin.copy(catalogSha256 = checksum).write(pinFile)
                out.println(if (checksum == pin.catalogSha256) "The pin's catalog is unchanged: $checksum." else
                    "The pin's catalog was ${pin.catalogSha256}, now $checksum.")
                0
            }
            "move" -> {
                if (!file.isFile) return fail("No MTGJSON file at $file to move the pin to.")
                val raw = CardCatalogFiles.sha256(file)
                val built = build(file, sets, cards, options["--report"]?.let(::File))
                val checksum = CardCatalogFiles.write(built.catalog, java.io.OutputStream.nullOutputStream())
                CatalogPin(built.catalog.header.mtgjsonVersion, built.catalog.header.mtgjsonDate, raw, checksum).write(pinFile)
                out.println("The pin names MTGJSON ${built.catalog.header.mtgjsonVersion} (SHA-256 $raw), catalog $checksum.")
                0
            }
            else -> usage()
        }
    }

    /** Why [file] isn't the pinned raw file, or null when it is. */
    private fun verify(pin: CatalogPin, file: File): String? {
        if (!file.isFile) return pin.missing(file)
        val actual = CardCatalogFiles.sha256(file)
        return if (actual == pin.allPrintingsSha256) null else pin.missing(file, actual)
    }

    private fun build(file: File, sets: File, cards: File, report: File?): CatalogBuilder.Built {
        if (!file.isFile) throw IllegalStateException("No MTGJSON file at $file")
        val builder = CatalogBuilder(lines(sets).map { it.substringBefore(' ') }.toSet(), lines(cards).toSet()) { out.println(it) }
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

    private fun usage(): Int = fail("Usage: verify | build --out <dir> [--no-pin] | update | move; options --pin, --file, --sets, --cards, --report")
}
