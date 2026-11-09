package arcanum.tools

import arcanum.parser.OracleParser
import java.io.File
import kotlin.system.exitProcess

/**
 * The gate of the parser migration: writes a canonical model of every local card to a snapshot, and compares a snapshot
 * with the current parser. A change in a supported set or a cube card, or a card that stops being read, exits with 1;
 * what changes on held-out cards is listed for review. Snapshots and reports stay outside the repository.
 * Args: `snapshot` (take one) or `compare` (the default), then optionally the snapshot file.
 */
fun main(args: Array<String>) {
    val command = args.getOrElse(0) { "compare" }
    val file = args.getOrNull(1)?.let(::File) ?: File(ParserToolPaths.outputs, "parser-models.snapshot")
    when (command) {
        "snapshot" -> {
            val current = currentSnapshot()
            writeSnapshot(current, file)
            println("Wrote ${current.lines.size} card models to $file")
        }
        "compare" -> {
            if (!file.exists()) {
                System.err.println("No snapshot at $file: take one before the change with --args=snapshot")
                exitProcess(2)
            }
            val before = readSnapshot(file)
            val current = currentSnapshot()
            writeSnapshot(current, File(file.parentFile, file.name.removeSuffix(".snapshot") + ".current.snapshot"))
            val code = gateResult(before, current, File(file.parentFile, "parser-diff.txt"))
            exitProcess(code)
        }
        else -> {
            System.err.println("Usage: ParserDiff snapshot|compare [snapshot file]")
            exitProcess(2)
        }
    }
}

/** One card in a snapshot. The scope is what the card counted as when the snapshot was taken. */
internal data class ModelLine(val id: String, val name: String, val scope: CardScope, val read: Boolean, val model: String)

/** Every card's model under one parser and one card data version; [lines] are sorted, so equal parsers give equal files. */
internal class Snapshot(val parser: Int, val mtgjson: String, val lines: List<ModelLine>)

/** The models the current parser gives for the corpus, printing how long the parsing took. */
private fun currentSnapshot(): Snapshot {
    val corpus = readCorpus(ParserToolPaths.mtgjson, ParserToolPaths.cube, supportedSets().keys)
    val start = System.nanoTime()
    var first = 0L
    val lines = corpus.cards.map { card ->
        val model = cardModel(card.layout, card.faces)
        if (first == 0L) first = System.nanoTime() - start
        ModelLine(card.id, card.name, card.scope, model.read, model.json)
    }
    val total = System.nanoTime() - start
    println("Parsed ${lines.size} cards (and wrote their models) in ${total / 1_000_000} ms: first card ${first / 1_000_000} ms, " +
        "then ${(total - first) / 1000 / maxOf(1, lines.size - 1)} µs per card")
    return Snapshot(OracleParser.VERSION, corpus.version, lines)
}

internal fun snapshotOf(parser: Int, mtgjson: String, lines: List<ModelLine>) =
    Snapshot(parser, mtgjson, lines.sortedWith(compareBy({ it.name }, { it.id })))

internal fun writeSnapshot(snapshot: Snapshot, file: File) {
    file.parentFile?.mkdirs()
    file.bufferedWriter(Charsets.UTF_8).use { out ->
        out.write("#parserDiff\tparser=${snapshot.parser}\tmtgjson=${snapshot.mtgjson}\tcards=${snapshot.lines.size}\n")
        for (l in snapshot.lines) {
            out.write(l.id); out.write('\t'.code); out.write(l.name); out.write('\t'.code); out.write(l.scope.code); out.write('\t'.code)
            out.write(if (l.read) "read" else "unread"); out.write('\t'.code); out.write(l.model); out.write('\n'.code)
        }
    }
}

internal fun readSnapshot(file: File): Snapshot = file.bufferedReader(Charsets.UTF_8).useLines { sequence ->
    val all = sequence.iterator()
    val header = all.next().split('\t').drop(1).associate { it.substringBefore('=') to it.substringAfter('=') }
    val lines = ArrayList<ModelLine>()
    while (all.hasNext()) {
        val p = all.next().split('\t', limit = 5)
        lines += ModelLine(p[0], p[1], CardScope.of(p[2]), p[3] == "read", p[4])
    }
    check(lines.size == header.getValue("cards").toInt()) { "$file is cut short: ${lines.size} of ${header["cards"]} cards" }
    Snapshot(header.getValue("parser").toInt(), header.getValue("mtgjson"), lines)
}

/** A card whose model differs: before and after. */
internal class ChangedCard(val before: ModelLine, val after: ModelLine) {
    val name get() = after.name

    /** Where the two models first differ, with a little context around it. */
    fun firstDifference(): String {
        val at = before.model.commonPrefixWith(after.model).length
        fun around(text: String) = text.substring(maxOf(0, at - 60), minOf(text.length, at + 100))
        return "at $at\n    was: ${around(before.model)}\n    now: ${around(after.model)}"
    }
}

/** What a compare of two snapshots found, each group sorted by name. A card's scope is the one it has now. */
internal class ParserDiffResult(
    val cards: Int,
    /** Models that changed in a supported set. */
    val supported: List<ChangedCard>,
    /** Models that changed on a card of the cube outside the supported sets. */
    val cube: List<ChangedCard>,
    /** Cards of any scope that were read and no longer are, or are gone from the cards compared. */
    val stoppedReading: List<ModelLine>,
    val heldOutNewlyRead: List<ChangedCard>,
    val heldOutReadDifferently: List<ChangedCard>,
    /** Held-out cards still not read whose model changed: an ability among several read or lost. */
    val heldOutUnreadChanged: List<ChangedCard>,
    /** Cards in the current parser's corpus that the snapshot doesn't have. */
    val added: List<ModelLine>,
) {
    /** What fails the gate: any change in a supported set or a cube card, and any card that stops being read. */
    val failed: Boolean get() = supported.isNotEmpty() || cube.isNotEmpty() || stoppedReading.isNotEmpty()
    val changes: Int get() = supported.size + cube.size + heldOutChanges + stoppedReading.count { it.scope == CardScope.HeldOut }
    private val heldOutChanges get() = heldOutNewlyRead.size + heldOutReadDifferently.size + heldOutUnreadChanged.size
}

internal fun diffSnapshots(before: Snapshot, current: Snapshot): ParserDiffResult {
    val now = current.lines.associateBy { it.id }
    val supported = ArrayList<ChangedCard>()
    val cube = ArrayList<ChangedCard>()
    val stopped = ArrayList<ModelLine>()
    val newlyRead = ArrayList<ChangedCard>()
    val readDifferently = ArrayList<ChangedCard>()
    val unreadChanged = ArrayList<ChangedCard>()
    for (old in before.lines) {
        val new = now[old.id]
        if (new == null) {
            if (old.read) stopped += old
            continue
        }
        if (old.read && !new.read) stopped += new
        if (old.model == new.model) continue
        val change = ChangedCard(old, new)
        when (new.scope) {
            CardScope.SupportedSet -> supported += change
            CardScope.Cube -> cube += change
            CardScope.HeldOut -> when {
                !old.read && new.read -> newlyRead += change
                old.read && new.read -> readDifferently += change
                !old.read && !new.read -> unreadChanged += change
                else -> Unit // stopped reading: listed above
            }
        }
    }
    val known = before.lines.mapTo(HashSet()) { it.id }
    return ParserDiffResult(current.lines.size, supported, cube, stopped, newlyRead, readDifferently, unreadChanged,
        current.lines.filter { it.id !in known })
}

/**
 * Compares and reports: a summary and the first entries of each group on standard output, everything in [report].
 * Returns the exit code: 0 passed, 1 the gate failed, 2 the snapshot is of other card data, so it can't say anything about
 * the parser.
 */
internal fun gateResult(before: Snapshot, current: Snapshot, report: File): Int {
    if (before.mtgjson != current.mtgjson) {
        System.err.println("The snapshot is of MTGJSON ${before.mtgjson}, the cards are ${current.mtgjson}: take a new snapshot on the old parser first")
        return 2
    }
    val diff = diffSnapshots(before, current)
    val groups = renderDiff(diff)
    report.parentFile?.mkdirs()
    report.writeText(groups.joinToString("\n") { (title, entries) -> "$title: ${entries.size}\n" + entries.joinToString("") { "  $it\n" } })
    println("Compared ${diff.cards} cards with a snapshot of parser ${before.parser} (now ${current.parser}), MTGJSON ${current.mtgjson}: ${diff.changes} changes")
    for ((title, entries) in groups) {
        println("$title: ${entries.size}")
        entries.take(STDOUT_ENTRIES).forEach { println("  $it") }
        if (entries.size > STDOUT_ENTRIES) println("  ... ${entries.size - STDOUT_ENTRIES} more in $report")
    }
    println(if (diff.failed) "FAILED: a supported or cube card changed, or a card stopped being read" else "Passed")
    return if (diff.failed) 1 else 0
}

/** The report's groups in order: title and entries. The groups that fail the gate come first. */
internal fun renderDiff(diff: ParserDiffResult): List<Pair<String, List<String>>> {
    fun changed(list: List<ChangedCard>) = list.sortedBy { it.name }.map { "${it.name} ${it.firstDifference()}" }
    return listOf(
        "Supported sets, changed (must be zero unless the task names them)" to changed(diff.supported),
        "Cube, changed (must be zero unless the task names them)" to changed(diff.cube),
        "Any card, stopped being read (must be zero)" to diff.stoppedReading.sortedBy { it.name }.map { "${it.name} [${it.scope.code}]" },
        "Held out, newly read (for review)" to changed(diff.heldOutNewlyRead),
        "Held out, read differently (for review)" to changed(diff.heldOutReadDifferently),
        "Held out, still unread but changed (for review)" to changed(diff.heldOutUnreadChanged),
        "Not in the snapshot" to diff.added.sortedBy { it.name }.map { "${it.name} [${it.scope.code}]" },
    )
}

private const val STDOUT_ENTRIES = 30
