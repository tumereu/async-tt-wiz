package org.asyncttwiz.android

import kotlinx.serialization.json.*
import org.asyncttwiz.protocol.*

internal data class PodStats(
    val curve: Map<Double, Int>,
    val creatures: Int,
    val noncreatures: Int,
    val lands: Long,
    val pips: Map<DraftColor, Double>,
    val sources: Map<DraftColor, Long>,
    val missingDefinitions: Int,
)

private val podStatsSymbols = Regex("\\{([^{}]+)\\}")
private val podStatsLand = Regex("\\bLand\\b", RegexOption.IGNORE_CASE)
private val podStatsCreature = Regex("\\bCreature\\b", RegexOption.IGNORE_CASE)
private val podStatsBasicTypes = listOf("Plains", "Island", "Swamp", "Mountain", "Forest")

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

internal fun podStats(build: PodDeckBuild, copies: Map<String, DraftCopy>, definitions: Map<String, CardDefinition>): PodStats {
    val curve = sortedMapOf<Double, Int>()
    val pips = DraftColor.entries.associateWith { 0.0 }.toMutableMap()
    val sources = DraftColor.entries.mapIndexed { index, color -> color to build.basics.podBuildValues()[index].toLong() }.toMap().toMutableMap()
    var creatures = 0
    var noncreatures = 0
    var lands = build.basics.total
    var missing = 0
    for (entry in build.copies.filter { it.category == PodDeckCategory.MAIN }) {
        val card = copies[entry.copyId]?.definition?.let(definitions::get)
        val face = card?.faces?.firstOrNull()
        if (card == null || face == null) { missing++; continue }
        if (podStatsLand.containsMatchIn(face.typeLine)) {
            lands++
            for (color in podStatsLandColors(card)) sources[color] = sources.getValue(color) + 1
        } else {
            if (podStatsCreature.containsMatchIn(face.typeLine)) creatures++ else noncreatures++
            val mv = PodDeckManaCost.front(card).value
            curve[mv] = (curve[mv] ?: 0) + 1
        }
        for ((color, count) in podStatsPips(podStatsFrontCost(card))) pips[color] = pips.getValue(color) + count
    }
    return PodStats(curve, creatures, noncreatures, lands, pips, sources, missing)
}

internal fun podStatsNumber(value: Double): String = if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

internal fun PodStats.curveBuckets(): Map<String, Double> = (0..7).associate { bucket ->
    (if (bucket == 7) "7+" else "$bucket") to curve.entries.filter { it.key.toInt().coerceIn(0, 7) == bucket }.sumOf { it.value }.toDouble()
}

internal fun PodStats.colorPercentages(color: DraftColor): Pair<Double, Double> {
    val demand = pips.values.sum()
    val supply = sources.values.sum()
    return (if (demand == 0.0) 0.0 else pips.getValue(color) / demand * 100) to
        (if (supply == 0L) 0.0 else sources.getValue(color).toDouble() / supply * 100)
}
