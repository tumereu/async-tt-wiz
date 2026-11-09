package arcanum.tools

import arcanum.parser.OracleParser
import java.io.File
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * The measurements the parser migration is judged by: perturbation by class, the grids, held-out cards read, the regex
 * count and rule usage. `--set CODE` is instead a cold read of one set: its cards and abilities read and the abilities left
 * unread, which measures what a future set costs. Prints to standard output; the long lists go beside the snapshots.
 */
fun main(args: Array<String>) {
    val set = args.indexOf("--set").takeIf { it >= 0 }?.let { args.getOrNull(it + 1) ?: error("--set needs a set code") }
    if (set != null) coldRead(set.uppercase()) else allMetrics()
}

private fun allMetrics() {
    val corpus = readCorpus(ParserToolPaths.mtgjson, ParserToolPaths.cube, supportedSets().keys)
    val outputs = ParserToolPaths.outputs.apply { mkdirs() }
    println("Parser version ${OracleParser.VERSION}, MTGJSON ${corpus.version}")

    // The parse under the rule usage count comes first: a fresh JVM, nothing else compiled yet.
    val read = HashMap<String, Boolean>()
    var compiledByFirstCard = 0L
    corpus.cards.forEachIndexed { index, card ->
        RegexProbe.startCard(index, card.scope == CardScope.SupportedSet)
        read[card.id] = cardModel(card.layout, card.faces).read
        if (index == 0) compiledByFirstCard = RegexProbe.compilations
    }
    RegexProbe.stop()
    val compilations = RegexProbe.compilations

    fun line(scope: CardScope) = corpus.cards.filter { it.scope == scope }.let { cards ->
        "${cards.size} ${scope.code} ${percent(cards.count { read.getValue(it.id) }, cards.size)} read"
    }
    println("Cards: ${corpus.cards.size}: ${line(CardScope.SupportedSet)}, ${line(CardScope.Cube)}, ${line(CardScope.HeldOut)}")
    val heldOut = corpus.cards.filter { it.scope == CardScope.HeldOut }
    println("Held-out cards read: ${rate(heldOut.count { read.getValue(it.id) }, heldOut.size)}")

    val literals = regexLiterals(ParserToolPaths.parserSources)
    println("Regexes: ${literals.total} in the source, ${literals.distinct} distinct")

    val detail = StringBuilder()
    println("\nPerturbation (supported-set cards, one slot swapped, each occurrence separately):")
    val perturbed = perturb(corpus.cards.filter { it.scope == CardScope.SupportedSet }, perturbationRules())
    println("  ${"all".padEnd(10)} ${rate(perturbed.sumOf { it.read }, perturbed.sumOf { it.total })}")
    for ((kind, rules) in perturbed.groupBy { it.rule.kind }.toList().sortedBy { (_, rules) -> rules.sumOf { it.read }.toDouble() / maxOf(1, rules.sumOf { it.total }) }.reversed()) {
        println("  ${kind.padEnd(10)} ${rate(rules.sumOf { it.read }, rules.sumOf { it.total })}")
    }
    println("  reads with the same model as before the swap: ${perturbed.sumOf { it.sameModel.size }} of ${perturbed.sumOf { it.read }}")
    perturbed.forEach { r ->
        detail.append("perturbation\t${r.rule.kind}\t${r.rule.name}\t${r.read}\t${r.total}\n")
        r.sameModel.forEach { detail.append("same model\t${r.rule.name}\t$it\n") }
    }

    println("\nGrids:")
    val cells = parserGrids().map { it to faceReads(it.card) }
    for ((grid, inGrid) in cells.groupBy { it.first.grid }.toList().sortedBy { it.first.drop(1).removeSuffix("b").toInt() * 2 + if (it.first.endsWith("b")) 1 else 0 }) {
        println("  ${grid.padEnd(4)} ${rate(inGrid.count { it.second }, inGrid.size)}")
    }
    cells.forEach { (cell, reads) -> detail.append("grid\t${cell.group}\t${if (reads) "read" else "unread"}\t${cell.text.replace("\n", "\\n")}\n") }

    println("\nRule usage: regexes by how many cards they match (a match is an upper bound on a rule firing):")
    if (RegexProbe.active) {
        RegexProbe.compileAll()
        val usages = RegexProbe.usages()
        val supportedRow = usageTable(usages.map { it.supportedCards })
        val allRow = usageTable(usages.map { it.allCards })
        println("  ${"cards".padEnd(6)} ${"supported-set cards".padEnd(26)} all cards")
        USAGE_BUCKETS.forEachIndexed { i, bucket ->
            println("  ${bucket.label.padEnd(6)} ${"${supportedRow[i]} (${percent(supportedRow[i], usages.size)})".padEnd(26)} ${allRow[i]} (${percent(allRow[i], usages.size)})")
        }
        println("  ${usages.size} patterns; $compilations compilations over the cards, $compiledByFirstCard by the end of the first card")
        File(outputs, "rule-usage.tsv").writeText(usages.joinToString("") { "${it.supportedCards}\t${it.allCards}\t${it.pattern}\n" })
    } else {
        println("  not measured: run through :arcanum-tools:parserMetrics, which uses the shadow parser")
    }
    File(outputs, "parser-metrics-detail.tsv").writeText(detail.toString())
    println("\nPer rule and per cell: ${File(outputs, "parser-metrics-detail.tsv")}; per regex: ${File(outputs, "rule-usage.tsv")}")
}

/** `--set`: a set read by the parser as it is now, whole cards and each ability on its own. */
private fun coldRead(code: String) {
    val cache = SetCache(ParserToolPaths.setCache.apply { mkdirs() })
    val cards = setCards(cache.file(code))
    val read = cards.count { cardModel(it.layout, it.faces.map(::oracleCard)).read }
    val unread = ArrayList<String>()
    var abilities = 0
    var abilitiesRead = 0
    for (card in cards) for (face in card.faces.map(::oracleCard)) for (ability in abilityBlocks(face.text)) {
        abilities++
        if (faceReads(face.copy(text = ability))) abilitiesRead++ else unread += "${face.name}: ${ability.replace("\n", " / ")}"
    }
    if (code in supportedSets()) println("$code is a supported set: this isn't a cold read")
    println("$code, parser version ${OracleParser.VERSION}: cards read ${rate(read, cards.size)}; abilities read ${rate(abilitiesRead, abilities)}")
    println("Unread abilities (${unread.size}):")
    unread.sorted().forEach { println("  $it") }
}

/** One substitution rule: a wording swapped for another of its [kind]; lines matching [skip] are left alone. */
internal class PerturbationRule(val kind: String, val name: String, val regex: Regex, val replacement: String, val skip: Regex?) {
    /** [text] with one occurrence swapped, for each occurrence in turn. Reminder text is gone first: no parser reads a swap in it. */
    fun substitutions(text: String): List<String> {
        val lines = text.split('\n').map { REMINDER.replace(it, "") }
        return lines.flatMapIndexed { i, line ->
            if (skip?.containsMatchIn(line) == true) return@flatMapIndexed emptyList()
            regex.findAll(line).map { m -> line.replaceRange(m.range, GROUP.replace(replacement) { g -> m.groupValues[g.groupValues[1].toInt()] }) }
                .filter { it != line } // the wording was already the one swapped to
                .map { swapped -> (lines.take(i) + swapped + lines.drop(i + 1)).joinToString("\n") }.toList()
        }
    }

    private companion object {
        val GROUP = Regex("""\$(\d)""")
    }
}

/** The committed table of substitutions: class, name, regex, replacement, regex of lines to skip. It holds no card text. */
internal fun perturbationRules(): List<PerturbationRule> {
    val text = checkNotNull(object {}.javaClass.getResource("/arcanum/tools/perturbation-rules.tsv")) { "perturbation-rules.tsv missing" }.readText()
    return text.lines().filter { it.isNotBlank() && !it.startsWith("#") }.map { line ->
        val c = line.split('\t')
        PerturbationRule(c[0], c[1], Regex(c[2]), c[3], c.getOrNull(4)?.takeIf { it.isNotEmpty() }?.let(::Regex))
    }
}

/** A rule's swaps: how many, how many still read, and the cards where the model didn't change at all, a word dropped unread. */
internal class RuleResult(val rule: PerturbationRule, val total: Int, val read: Int, val sameModel: List<String>)

/** A model without what only repeats the text (each ability's printed lines, where its targets sit), so a dropped word shows. */
internal fun meaning(modelJson: String): JsonElement {
    fun walk(e: JsonElement): JsonElement = when (e) {
        is JsonObject -> JsonObject(e.filterKeys { it != "text" && it != "span" }.mapValues { walk(it.value) })
        is JsonArray -> JsonArray(e.map(::walk))
        else -> e
    }
    return walk(Json.parseToJsonElement(modelJson))
}

/** Each rule over [cards]: every occurrence swapped alone and the whole card parsed again. */
internal fun perturb(cards: List<CorpusCard>, rules: List<PerturbationRule>): List<RuleResult> {
    val base = cards.associateWith { meaning(cardModel(it.layout, it.faces).json) }
    return rules.map { rule ->
        var total = 0
        var read = 0
        val same = ArrayList<String>()
        for (card in cards) card.faces.forEachIndexed { index, face ->
            for (text in rule.substitutions(face.text)) {
                val model = cardModel(card.layout, card.faces.mapIndexed { i, f -> if (i == index) f.copy(text = text) else f })
                total++
                if (model.read) {
                    read++
                    if (meaning(model.json) == base.getValue(card)) same += card.name
                }
            }
        }
        RuleResult(rule, total, read, same)
    }
}

/** A face's text as abilities to read alone: a line each, reminder text gone, a mode's "• " line kept with the line it follows. */
internal fun abilityBlocks(text: String): List<String> {
    val out = ArrayList<String>()
    for (raw in text.split('\n')) {
        val line = REMINDER.replace(raw, "").trim()
        if (line.isEmpty()) continue
        if (line.startsWith("• ") && out.isNotEmpty()) out[out.size - 1] += "\n" + line else out += line
    }
    return out
}

private val REMINDER = Regex("""\s*\([^)]*\)""")

internal class RegexLiterals(val total: Int, val distinct: Int)

/** Counts the `Regex(` constructor calls in Kotlin source files under [dir], and how many distinct first arguments they have. */
internal fun regexLiterals(dir: File): RegexLiterals {
    var total = 0
    val distinct = HashSet<String>()
    dir.walkTopDown().filter { it.extension == "kt" }.sortedBy { it.path }.forEach { file ->
        for (m in REGEX_CALL.findAll(file.readText())) {
            total++
            distinct += m.groupValues[1]
        }
    }
    return RegexLiterals(total, distinct.size)
}

private val REGEX_CALL = Regex("\\bRegex\\(\\s*(\"\"\"(?s:.*?)\"\"\"(?!\")|\"(?:\\\\.|[^\"\\\\])*\"|[^\\n]*)")

private fun percent(part: Int, whole: Int) = String.format(Locale.ROOT, "%.1f%%", 100.0 * part / maxOf(1, whole))
private fun rate(part: Int, whole: Int) = "${percent(part, whole)} ($part of $whole)"
