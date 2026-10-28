package org.asyncttwiz.android

import kotlinx.serialization.json.*
import org.asyncttwiz.protocol.*

internal data class PodStats(
    val curve: Map<Double, PodStatsCurveBucket>,
    val creatures: Int,
    val noncreatures: Int,
    val lands: Long,
    val pips: Map<DraftColor, Double>,
    val sources: Map<DraftColor, Long>,
    val colors: Map<PodStatsColor, Int>,
    val missingDefinitions: Int,
    /** Main lands (not basics) that produce no colored mana by [podStatsLandColors]. */
    val colorlessLands: Int = 0,
    /** Main nonland copies per colored-symbol pattern of the front cost, most common first. */
    val pipPatterns: List<Pair<String, Int>> = emptyList(),
    val noPips: Int = 0,
)

internal data class PodStatsCurveBucket(val creatures: Int = 0, val noncreatures: Int = 0) {
    val total: Int get() = creatures + noncreatures
}

internal enum class PodStatsColor { W, U, B, R, G, MULTICOLOR, COLORLESS }

private val podStatsSymbols = Regex("\\{([^{}]+)\\}")
private val podStatsLand = Regex("\\bLand\\b", RegexOption.IGNORE_CASE)
private val podStatsCreature = Regex("\\bCreature\\b", RegexOption.IGNORE_CASE)
private val podStatsBasicTypes = listOf("Plains", "Island", "Swamp", "Mountain", "Forest")

internal fun podStatsIsLand(card: CardDefinition): Boolean =
    card.faces.firstOrNull()?.let { podStatsLand.containsMatchIn(it.typeLine) } == true

/** The front cost's symbols other than generic, X/Y/Z and snow, in printed order: "{R/W}{R/W}", "{C}", "{2/U}". */
internal fun podStatsPipPattern(cost: String): String = podStatsSymbols.findAll(cost.substringBefore("//"))
    .map { it.groupValues[1].uppercase() }
    .filterNot { symbol -> symbol.all { it.isDigit() || it == '½' } || symbol in setOf("X", "Y", "Z", "S") }
    .joinToString("") { "{$it}" }

/** Whole pips plainly, halves as "4½"; anything finer to one decimal. */
internal fun podStatsPipText(value: Double): String {
    val whole = kotlin.math.floor(value + 1e-9).toLong()
    val rest = value - whole
    return when {
        rest < 1e-6 -> "$whole"
        kotlin.math.abs(rest - .5) < 1e-6 -> if (whole == 0L) "½" else "$whole½"
        else -> "%.1f".format(java.util.Locale.ROOT, value)
    }
}

/** Match the front-cost fallback used by PodDeckManaCost.front; never inspect the back face. */
internal fun podStatsFrontCost(card: CardDefinition): String {
    val face = card.faces.firstOrNull() ?: return ""
    return face.manaCost.ifEmpty {
        face.sourceData?.let { runCatching {
            Json.parseToJsonElement(it).jsonObject["manaCost"]?.jsonPrimitive?.content
        }.getOrNull() }.orEmpty()
    }.substringBefore("//")
}

internal fun podStatsPips(cost: String): Map<DraftColor, Double> {
    val result = DraftColor.entries.associateWith { 0.0 }.toMutableMap()
    for (symbol in podStatsSymbols.findAll(cost.substringBefore("//"))) {
        val parts = symbol.groupValues[1].uppercase().split('/')
        val colors = DraftColor.entries.filter { it.name in parts }
        // Two-color (including Phyrexian hybrid) divides the pip between its colors.
        // Monocolor hybrid {2/W} contributes half; {W/P} contributes a full white pip.
        val amount = when {
            colors.size > 1 -> 1.0 / colors.size
            parts.any { it.toIntOrNull() != null } -> 0.5
            else -> 1.0
        }
        for (color in colors) result[color] = result.getValue(color) + amount
        for (color in DraftColor.entries) if ("H${color.name}" in parts)
            result[color] = result.getValue(color) + 0.5
    }
    return result
}

/** Deliberately approximate: one source per produced color per land, not mana quantity. */
internal fun podStatsLandColors(card: CardDefinition): Set<DraftColor> {
    val face = card.faces.firstOrNull() ?: return emptySet()
    if (!podStatsLand.containsMatchIn(face.typeLine)) return emptySet()
    val result = mutableSetOf<DraftColor>()
    val subtypes = face.typeLine.substringAfter('—', face.typeLine.substringAfter(" - ", ""))
    podStatsBasicTypes.forEachIndexed { index, name ->
        if (Regex("\\b$name\\b", RegexOption.IGNORE_CASE).containsMatchIn(subtypes)) result += DraftColor.entries[index]
    }
    // Limit symbols to an Add clause: activation costs and unrelated rules aren't sources.
    Regex("\\bAdd\\b([^.;\\n]*)", RegexOption.IGNORE_CASE).findAll(face.rulesText).forEach { clause ->
        podStatsSymbols.findAll(clause.groupValues[1]).forEach { symbol ->
            symbol.groupValues[1].uppercase().split('/').forEach { part ->
                DraftColor.entries.firstOrNull { it.name == part }?.let(result::add)
            }
        }
        if (Regex("\\bany colou?r\\b", RegexOption.IGNORE_CASE).containsMatchIn(clause.value)) result += DraftColor.entries
    }
    return result
}

/** Prefer catalog identity, which includes rules symbols and both faces, not just the cost. */
internal fun podStatsColorIdentity(card: CardDefinition, copyColors: Set<DraftColor>): PodStatsColor {
    val recorded = card.faces.mapNotNull { face ->
        face.sourceData?.let { data -> runCatching {
            val record = Json.parseToJsonElement(data).jsonObject
            (record["colorIdentity"] ?: record["color_identity"])?.jsonArray?.mapNotNull { color ->
                DraftColor.entries.firstOrNull { it.name == color.jsonPrimitive.content }
            }?.toSet()
        }.getOrNull() }
    }
    val identity = if (recorded.isNotEmpty()) recorded.flatten().toSet() else buildSet {
        addAll(copyColors)
        addAll(PodDeckManaCost.catalogColors(card).orEmpty())
        for (face in card.faces) {
            addAll(PodDeckManaCost.parse(podStatsFrontCost(card.copy(faces = listOf(face)))).colors)
            // Reminder text doesn't contribute to color identity (e.g. extort).
            val rules = face.rulesText.replace(Regex("\\([^()]*\\)"), "")
            addAll(PodDeckManaCost.parse(rules).colors)
        }
    }
    return when (identity.size) {
        0 -> PodStatsColor.COLORLESS
        1 -> PodStatsColor.valueOf(identity.single().name)
        else -> PodStatsColor.MULTICOLOR
    }
}

internal fun podStats(build: PodDeckBuild, copies: Map<String, DraftCopy>, definitions: Map<String, CardDefinition>): PodStats {
    val curve = sortedMapOf<Double, PodStatsCurveBucket>()
    val pips = DraftColor.entries.associateWith { 0.0 }.toMutableMap()
    val sources = DraftColor.entries.mapIndexed { index, color -> color to build.basics.podBuildValues()[index].toLong() }.toMap().toMutableMap()
    val colors = PodStatsColor.entries.associateWith { 0 }.toMutableMap()
    var creatures = 0
    var noncreatures = 0
    var lands = build.basics.total
    var missing = 0
    var colorlessLands = 0
    val patterns = mutableMapOf<String, Int>()
    for (entry in build.copies.filter { it.category == PodDeckCategory.MAIN }) {
        val copy = copies[entry.copyId]
        val card = copy?.definition?.let(definitions::get)
        val face = card?.faces?.firstOrNull()
        if (card == null || face == null) { missing++; continue }
        if (podStatsLand.containsMatchIn(face.typeLine)) {
            lands++
            val produced = podStatsLandColors(card)
            if (produced.isEmpty()) colorlessLands++
            for (color in produced) sources[color] = sources.getValue(color) + 1
        } else {
            val creature = podStatsCreature.containsMatchIn(face.typeLine)
            if (creature) creatures++ else noncreatures++
            val mv = PodDeckManaCost.front(card).value
            val bucket = curve[mv] ?: PodStatsCurveBucket()
            curve[mv] = if (creature) bucket.copy(creatures = bucket.creatures + 1)
                else bucket.copy(noncreatures = bucket.noncreatures + 1)
            val color = podStatsColorIdentity(card, copy?.colors.orEmpty())
            colors[color] = colors.getValue(color) + 1
            val pattern = podStatsPipPattern(podStatsFrontCost(card))
            patterns[pattern] = (patterns[pattern] ?: 0) + 1
        }
        for ((color, count) in podStatsPips(podStatsFrontCost(card))) pips[color] = pips.getValue(color) + count
    }
    val listed = patterns.filterKeys { it.isNotEmpty() }.toList()
        .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
    return PodStats(curve, creatures, noncreatures, lands, pips, sources, colors, missing, colorlessLands, listed, patterns[""] ?: 0)
}

internal fun PodStats.curveBuckets(): Map<String, PodStatsCurveBucket> = (0..7).associate { bucket ->
    val entries = curve.entries.filter { it.key.toInt().coerceIn(0, 7) == bucket }
    (if (bucket == 7) "7+" else "$bucket") to PodStatsCurveBucket(
        entries.sumOf { it.value.creatures }, entries.sumOf { it.value.noncreatures })
}

/** Empty donuts have zero shares, never NaN; each chart normalizes independently. */
internal fun <T> podStatsShares(values: Map<T, Number>): Map<T, Double> {
    val total = values.values.sumOf { it.toDouble() }
    return values.mapValues { (_, count) -> if (total == 0.0) 0.0 else count.toDouble() / total }
}

/** Drops control characters other than line breaks and keeps the limits, so edits never become unsaveable. */
internal fun PodDeckNotes.podNotesSanitized(): PodDeckNotes {
    var cleaned = text.replace("\r\n", "\n").filter { !it.isISOControl() || it == '\n' }.take(PodDeckNotes.MAX_TEXT)
    if (cleaned.lastOrNull()?.isHighSurrogate() == true) cleaned = cleaned.dropLast(1)
    val values = (counters + List(PodDeckNotes.COUNTERS) { 0 }).take(PodDeckNotes.COUNTERS)
        .map { it.coerceIn(0, PodDeckNotes.MAX_COUNTER) }
    return PodDeckNotes(cleaned, values)
}

internal fun PodDeckNotes.podNotesAdjust(index: Int, delta: Int): PodDeckNotes =
    copy(counters = counters.mapIndexed { i, value -> if (i == index) (value + delta).coerceIn(0, PodDeckNotes.MAX_COUNTER) else value })
