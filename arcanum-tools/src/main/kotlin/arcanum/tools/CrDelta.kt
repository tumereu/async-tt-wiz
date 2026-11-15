package arcanum.tools

import arcanum.api.ComprehensiveRules
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.system.exitProcess

/**
 * What moving the Comprehensive Rules pin means for the repository's rule citations. Reads two local rules texts (never
 * committed) and, for every rule cited as `CR <number>` in the repository's tracked files (the feature catalog and the
 * specs left out), says whether it is unchanged, renumbered, changed in text or gone; lists the rules new or reworded in the
 * newer text. Citations are read in the numbering of the pinned version (`ComprehensiveRules.EFFECTIVE_DATE`), which must be
 * one of the two: the older before the move, the newer once the pin and the citations have moved.
 * `--apply` (older version pinned) rewrites each citation of a rule renumbered to one place to its new number, and of each
 * rule in `--map=old=new,...` (a reader's choice for a rule changed, split or renumbered to several places) to the given one.
 * A changed rule a reader checked against the newer text is listed in `cr-delta-checked.tsv` beside this class (rule
 * in the pinned numbering, the newer text's effective date, why the citing code still holds) and reported as checked.
 * Args: older rules text, newer rules text, optionally the report file, optionally `--apply` and `--map=...`. Exits 1
 * while any cited rule needs a look.
 */
fun main(args: Array<String>) {
    val apply = "--apply" in args
    val map = args.filter { it.startsWith("--map=") }.flatMap { it.removePrefix("--map=").split(',') }.filter { it.isNotBlank() }
        .associate { pair -> pair.split('=').let { require(it.size == 2) { "--map takes old=new pairs: $pair" }; it[0].trim() to it[1].trim() } }
    val files = args.filter { it != "--apply" && !it.startsWith("--map=") }
    if (files.size !in 2..3 || (map.isNotEmpty() && !apply)) {
        System.err.println("Usage: CrDelta <older rules text> <newer rules text> [<report.md>] [--apply [--map=old=new,...]]")
        exitProcess(2)
    }
    val (older, newer) = files.take(2).map(::File)
    val report = File(files.getOrNull(2) ?: "build/arcanum/cr-delta.md")
    for (file in listOf(older, newer)) if (!file.isFile) {
        System.err.println("No rules text at $file: download it from magic.wizards.com/en/rules (see arcanum-tools/set-work/bin/setup.sh)")
        exitProcess(2)
    }
    val root = File(System.getProperty("arcanum.repoRoot") ?: "..")
    val old = RulesText.parse(older.readText())
    val new = RulesText.parse(newer.readText())
    val citedIn = when (ComprehensiveRules.EFFECTIVE_DATE) {
        old.effectiveDate -> Numbering.Older
        new.effectiveDate -> Numbering.Newer
        else -> {
            System.err.println("The pinned version ${ComprehensiveRules.EFFECTIVE_DATE} is neither ${old.effectiveDate} nor ${new.effectiveDate}")
            exitProcess(2)
        }
    }
    if (apply && citedIn != Numbering.Older) {
        System.err.println("--apply rewrites citations in the older numbering: pin ${old.effectiveDate} while applying, then move the pin")
        exitProcess(2)
    }
    val delta = crDelta(old, new, citedIn, citations(root), checkedRules())
    if (apply) {
        // The report stays the one before the rewrite: the citations are in neither numbering alone until the pin moves too.
        val changed = applyRenumbering(root, delta, map)
        println("Rewrote renumbered citations in $changed files; move the pin to ${new.effectiveDate} and run again")
    }
    report.parentFile?.mkdirs()
    report.writeText(delta.render())
    println(delta.summary())
    println("Report: ${report.absolutePath}")
    exitProcess(if (delta.needsLook) 1 else 0)
}

/** One version of the rules: its effective date (yyyy-mm-dd) and every numbered rule's text (examples left out). */
internal class RulesText(val effectiveDate: String, val rules: Map<String, String>) {
    companion object {
        private val RULE = Regex("""^(\d{3}(?:\.\d+[a-z]?)?)\.?\s+(\S.*)$""", RegexOption.DOT_MATCHES_ALL)
        private val EFFECTIVE = Regex("""These rules are effective as of (\w+ \d{1,2}, \d{4})\.""")
        private val PRINTED_DATE = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH)

        /** Reads a rules text as Wizards publishes it (any line endings, with or without a byte order mark). */
        fun parse(text: String): RulesText {
            val rules = LinkedHashMap<String, String>()
            // A line separator inside a line (U+2028) starts an unnumbered paragraph of the rule above, as a line break does.
            for (raw in text.removePrefix("\uFEFF").replace('\u2028', '\n').lineSequence()) {
                val m = RULE.matchEntire(raw.trim()) ?: continue
                // The contents list names each section once more ahead of the rules, with the same text.
                rules[m.groupValues[1]] = m.groupValues[2].replace(Regex("""\s+"""), " ").trim()
            }
            val date = EFFECTIVE.find(text)?.groupValues?.get(1)?.let { LocalDate.parse(it, PRINTED_DATE).toString() } ?: "unknown"
            return RulesText(date, rules)
        }
    }
}

/** Which version's numbers the repository's citations use. */
internal sealed interface Numbering {
    data object Older : Numbering
    data object Newer : Numbering
}

/** Where one rule number is cited: the file relative to the repository root and the line. */
internal data class Citation(val rule: String, val file: String, val line: Int)

private val CITATION = Regex("""\bCR\s+(\d{3}(?:\.\d+[a-z]?)?(?:(?:\s*[,;/]\s*|\s+(?:and|or)\s+)\d{3}(?:\.\d+[a-z]?)?)*)""")
private val RULE_NUMBER = Regex("""\d{3}(?:\.\d+[a-z]?)?""")
private val DOTTED_RULE = Regex("""(?<![\d.\w])\d{3}\.\d+[a-z]?(?![\d\w])""")
private val CITING_FILES = setOf("kt", "kts", "md", "java", "py", "sh", "txt", "tsv", "js", "ts", "html", "sql")

/** Where [line] cites rules: each number of a `CR <number>` list ("CR 613.4c, 613.5"), and each further numbered rule after a
 * `CR` on the line ("CR 601.2b (an optional cost), 723.1"). */
private fun citedRanges(line: String): List<IntRange> {
    val lists = CITATION.findAll(line).flatMap { m -> RULE_NUMBER.findAll(m.groupValues[1]).map { it.range.shift(m.groups[1]!!.range.first) } }.toList()
    val start = CITATION.find(line)?.range?.first ?: return lists
    val later = DOTTED_RULE.findAll(line, start).map { it.range }.filter { r -> lists.none { it.first == r.first } }
    return (lists + later).sortedBy { it.first }
}

private fun IntRange.shift(by: Int) = (first + by)..(last + by)

/** Every rule cited in [text] (see [citedRanges]), with its line number. */
internal fun citationsIn(text: String, file: String): List<Citation> = text.lineSequence().withIndex().flatMap { (index, line) ->
    citedRanges(line).map { Citation(line.substring(it), file, index + 1) }
}.toList()

/** [text] with each cited rule number replaced by its entry in [renumbering]. */
internal fun renumbered(text: String, renumbering: Map<String, String>): String = text.lines().joinToString("\n") { line ->
    citedRanges(line).asReversed().fold(line) { acc, r -> renumbering[line.substring(r)]?.let { acc.replaceRange(r, it) } ?: acc }
}

private fun citingFiles(root: File): List<String> {
    val process = ProcessBuilder("git", "ls-files").directory(root).redirectErrorStream(true).start()
    val files = process.inputStream.bufferedReader().readLines()
    check(process.waitFor() == 0) { "git ls-files failed in $root: ${files.take(3)}" }
    return files.filter { it.substringAfterLast('.') in CITING_FILES && !it.startsWith("docs/features/") && !it.startsWith("docs/specs/") }
        .filter { File(root, it).isFile }
}

/** The citations in the repository's tracked files, the feature catalog and the specs left out. */
internal fun citations(root: File): List<Citation> = citingFiles(root).flatMap { citationsIn(File(root, it).readText(), it) }

/** Rewrites each citation of a rule renumbered to one place, and of each rule in [map]; returns how many files changed. */
private fun applyRenumbering(root: File, delta: CrDeltaResult, map: Map<String, String>): Int {
    val renumbering = delta.cited.mapNotNull { (rule, value) -> (value.first as? RuleChange.Renumbered)?.to?.singleOrNull()?.let { rule to it } }.toMap() + map
    return citingFiles(root).count { path ->
        val file = File(root, path)
        val text = file.readText()
        val rewritten = renumbered(text, renumbering)
        (rewritten != text).also { if (it) file.writeText(rewritten) }
    }
}

/** A changed rule checked against a version's text: the code citing it still encodes the new text. */
internal data class CheckedRule(val rule: String, val effectiveDate: String, val why: String)

/** `cr-delta-checked.tsv`: rule, effective date (yyyy-mm-dd) of the text it was checked against, why the citing code still holds. */
internal fun checkedRules(): List<CheckedRule> {
    val text = object {}.javaClass.getResource("/arcanum/tools/cr-delta-checked.tsv")?.readText() ?: return emptyList()
    return text.lines().filter { it.isNotBlank() && !it.startsWith("#") }.map { line ->
        val parts = line.split('\t')
        require(parts.size == 3) { "cr-delta-checked.tsv: expected rule, effective date and why, tab-separated: $line" }
        CheckedRule(parts[0], parts[1], parts[2])
    }
}

/** What happened to one cited rule between the two versions. */
internal sealed interface RuleChange {
    val title: String
    /** Nothing to do. */
    val settled: Boolean get() = false

    data object Unchanged : RuleChange { override val title = "unchanged"; override val settled = true }
    /** Only its references to other rules' numbers changed: what it says is the same. */
    data class ReferencesOnly(val old: String, val new: String) : RuleChange {
        override val title = "unchanged but for rule references"
        override val settled = true
    }
    /** The same text (perhaps but for rule references) under another number: the citation is to be updated. */
    data class Renumbered(val to: List<String>, val text: String) : RuleChange { override val title = "renumbered" }
    /** The number names different text now: read it and check the citing code still encodes it. */
    data class TextChanged(val old: String, val new: String) : RuleChange { override val title = "text changed" }
    /** Gone from the version cited: find where the rule went (the nearest texts by shared words are suggested). */
    data class Removed(val old: String, val nearest: List<Pair<String, String>>) : RuleChange { override val title = "removed or split" }
    /** In the newer version only: a rule new in it (or, cited in the older numbering, a wrong citation). */
    data class OnlyInNew(val new: String) : RuleChange { override val title = "not in the older version" }
    /** In neither version: the citation is wrong. */
    data object Unknown : RuleChange { override val title = "in neither version" }
    /** A change listed in cr-delta-checked.tsv for the newer version. */
    data class Checked(val change: RuleChange, val why: String) : RuleChange { override val title = "checked"; override val settled = true }
}

internal class CrDeltaResult(
    val oldDate: String,
    val newDate: String,
    val citedIn: Numbering,
    val cited: Map<String, Pair<RuleChange, List<Citation>>>,
    val newRules: List<Pair<String, String>>,
) {
    val needsLook get() = cited.values.any { !it.first.settled }

    fun summary(): String {
        val counts = cited.values.groupingBy { it.first.title }.eachCount().entries.sortedBy { it.key }.joinToString { "${it.value} ${it.key}" }
        val numbering = if (citedIn == Numbering.Older) oldDate else newDate
        return "Comprehensive Rules $oldDate -> $newDate, citations in the $numbering numbering: ${cited.size} cited rules ($counts), " +
            "${newRules.size} new or reworded rules; " + if (needsLook) "cited rules need a look" else "every cited rule unchanged or checked"
    }

    fun render(): String = buildString {
        appendLine("# Comprehensive Rules delta: $oldDate -> $newDate")
        appendLine()
        appendLine(summary())
        val order = listOf("renumbered", "text changed", "removed or split", "not in the older version", "in neither version", "checked",
            "unchanged but for rule references", "unchanged")
        for (title in order) {
            val entries = cited.entries.filter { it.value.first.title == title }.sortedWith(compareBy(RULE_ORDER) { it.key })
            if (entries.isEmpty()) continue
            appendLine()
            appendLine("## ${title.replaceFirstChar { it.uppercase() }} (${entries.size})")
            appendLine()
            for ((rule, value) in entries) {
                val (change, where) = value
                if (change == RuleChange.Unchanged) { appendLine("- $rule (${where.size})"); continue }
                val at = where.take(3).joinToString { "${it.file}:${it.line}" } + if (where.size > 3) " and ${where.size - 3} more" else ""
                appendLine("- **$rule** (${where.size} citations: $at)")
                describe(change)
            }
        }
        appendLine()
        appendLine("## New or reworded rules in $newDate (${newRules.size})")
        appendLine()
        for ((rule, text) in newRules) appendLine("- **$rule** $text")
    }

    private fun StringBuilder.describe(change: RuleChange) {
        when (change) {
            RuleChange.Unchanged, RuleChange.Unknown -> {}
            is RuleChange.Renumbered -> appendLine("  - now ${change.to.joinToString(" or ")}: ${change.text}")
            is RuleChange.TextChanged -> { appendLine("  - was: ${change.old}"); appendLine("  - now: ${change.new}") }
            is RuleChange.ReferencesOnly -> { appendLine("  - was: ${change.old}"); appendLine("  - now: ${change.new}") }
            is RuleChange.Removed -> {
                appendLine("  - was: ${change.old}")
                for ((rule, text) in change.nearest) appendLine("  - nearest now: $rule $text")
            }
            is RuleChange.OnlyInNew -> appendLine("  - now: ${change.new}")
            is RuleChange.Checked -> { appendLine("  - ${change.change.title}; checked: ${change.why}"); describe(change.change) }
        }
    }
}

/** Rule numbers in rules order: section, then subrule number, then letter. */
internal val RULE_ORDER: Comparator<String> = compareBy<String>({ it.substringBefore('.').toInt() },
    { it.substringAfter('.', "").takeWhile(Char::isDigit).toIntOrNull() ?: -1 }, { it.substringAfter('.', "").dropWhile(Char::isDigit) })

/** Rule numbers inside a rule's text ("see rule 603.7d–f"), masked to compare what the rule says. */
private val REFERENCE = Regex("""\d{3}(?:\.\d+[a-z]?)?(?:[–-][0-9a-z.]+)?""")
private fun masked(text: String) = text.replace(REFERENCE, "#")

internal fun crDelta(old: RulesText, new: RulesText, citedIn: Numbering, citations: List<Citation>, checked: List<CheckedRule>): CrDeltaResult {
    val compare = Comparison(old, new)
    val acknowledged = checked.filter { it.effectiveDate == new.effectiveDate }.associateBy { it.rule }
    val cited = citations.groupBy { it.rule }.mapValues { (rule, where) ->
        val change = if (citedIn == Numbering.Older) compare.fromOlder(rule) else compare.fromNewer(rule)
        val ack = acknowledged[rule]
        (if (ack != null && !change.settled) RuleChange.Checked(change, ack.why) else change) to where
    }
    // New or reworded: said nowhere in the older version, under any number (a rule only renumbered, or changed only in its references, isn't)
    val oldMasked = old.rules.values.mapTo(HashSet(), ::masked)
    val newRules = new.rules.filterValues { masked(it) !in oldMasked }.toList()
    return CrDeltaResult(old.effectiveDate, new.effectiveDate, citedIn, cited, newRules)
}

private class Comparison(val old: RulesText, val new: RulesText) {
    private val newByText = new.rules.entries.groupBy({ it.value }, { it.key })
    private val newByMasked = new.rules.entries.groupBy({ masked(it.value) }, { it.key })
    private val oldTexts = old.rules.values.toSet()
    private val oldMasked = old.rules.values.mapTo(HashSet(), ::masked)

    /** Where each renumbered section went ("714" -> "715"), to choose among equal texts in several places. */
    private val sections = old.rules.filterKeys { '.' !in it }.mapNotNull { (n, text) -> newByText[text]?.singleOrNull()?.let { n to it } }.toMap()

    /** A rule cited by its older number. */
    fun fromOlder(rule: String): RuleChange {
        val was = old.rules[rule]
        val now = new.rules[rule]
        return when {
            was == null && now == null -> RuleChange.Unknown
            was == null -> RuleChange.OnlyInNew(now!!)
            was == now -> RuleChange.Unchanged
            newByText[was] != null -> RuleChange.Renumbered(choose(rule, newByText.getValue(was)), was)
            now != null && masked(was) == masked(now) -> RuleChange.ReferencesOnly(was, now)
            newByMasked[masked(was)] != null -> RuleChange.Renumbered(choose(rule, newByMasked.getValue(masked(was))), was)
            now != null -> RuleChange.TextChanged(was, now)
            else -> RuleChange.Removed(was, nearest(was, new))
        }
    }

    /** A rule cited by its newer number: it is unchanged if the older version said the same, under any number. */
    fun fromNewer(rule: String): RuleChange {
        val was = old.rules[rule]
        val now = new.rules[rule]
        return when {
            now == null && was == null -> RuleChange.Unknown
            now == null -> RuleChange.Removed(was!!, nearest(was, new))
            now in oldTexts -> RuleChange.Unchanged
            was != null && masked(was) == masked(now) -> RuleChange.ReferencesOnly(was, now)
            masked(now) in oldMasked -> RuleChange.ReferencesOnly(old.rules.values.first { masked(it) == masked(now) }, now)
            // the older rule of this number still is in the newer version elsewhere: this one is new
            was != null && was !in newByText -> RuleChange.TextChanged(was, now)
            else -> RuleChange.OnlyInNew(now)
        }
    }

    /** Of several places with the same text, the one in the section the rule's section went to, if that settles it. */
    private fun choose(rule: String, places: List<String>): List<String> {
        if (places.size == 1) return places
        val section = rule.substringBefore('.').let { sections[it] ?: it }
        return places.filter { it.substringBefore('.') == section }.takeIf { it.size == 1 } ?: places
    }
}

/** The three rules of [new] sharing the most words with [text]. */
private fun nearest(text: String, new: RulesText): List<Pair<String, String>> {
    fun words(s: String) = s.lowercase().split(Regex("""\W+""")).filter { it.length > 2 }.toSet()
    val mine = words(text)
    return new.rules.entries.map { it to (words(it.value) intersect mine).size.toDouble() / (words(it.value) union mine).size.coerceAtLeast(1) }
        .sortedByDescending { it.second }.take(3).map { it.first.key to it.first.value }
}
