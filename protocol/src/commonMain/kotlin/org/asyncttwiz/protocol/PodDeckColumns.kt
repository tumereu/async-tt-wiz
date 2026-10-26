package org.asyncttwiz.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Mana cost, not color identity: reminders and a card's rules text never contribute colors. */
data class PodDeckMana(val value: Double, val colors: Set<DraftColor>)

object PodDeckManaCost {
    private val symbols = Regex("\\{([^{}]+)}")

    /** Hybrid takes the largest alternative; Phyrexian is one; X/Y/Z are zero. */
    fun parse(cost: String): PodDeckMana {
        val colors = mutableSetOf<DraftColor>()
        var value = 0.0
        for (match in symbols.findAll(cost.substringBefore("//"))) {
            val alternatives = match.groupValues[1].uppercase().split('/')
            for (part in alternatives) DraftColor.entries.firstOrNull { it.name == part }?.let(colors::add)
            value += alternatives.maxOf { part ->
                when {
                    part in listOf("W", "U", "B", "R", "G", "C", "S", "P") -> 1.0
                    part == "HW" || part == "HU" || part == "HB" || part == "HR" || part == "HG" -> 0.5
                    else -> part.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 } ?: 0.0
                }
            }
            for (part in alternatives) {
                if (part.length == 2 && part[0] == 'H') DraftColor.entries.firstOrNull { it.name == part.drop(1) }?.let(colors::add)
            }
        }
        return PodDeckMana(value, colors)
    }

    /** Catalog normalization blanks the cost of adventure/split/flip cards; their front record still has it.
     * Split and aftermath cards count both halves, whose costs survive only in the combined rules text. */
    fun front(card: CardDefinition): PodDeckMana {
        val face = card.faces.firstOrNull() ?: return PodDeckMana(0.0, emptySet())
        if (face.manaCost.isEmpty() && card.layout in setOf("split", "aftermath") && card.faces.size == 1) {
            val halves = face.rulesText.split("────────").mapNotNull { half ->
                half.lines().map(String::trim).firstOrNull { it.isNotEmpty() && costLine.matches(it) }?.let(::parse)
            }
            if (halves.isNotEmpty()) return PodDeckMana(halves.sumOf { it.value }, halves.flatMap { it.colors }.toSet())
        }
        val cost = face.manaCost.ifEmpty { record(face)?.get("manaCost")?.jsonPrimitive?.content.orEmpty() }
        return parse(cost)
    }

    /** The catalog's own colors (devoid, color indicators) when retained; null when unknown. */
    fun catalogColors(card: CardDefinition): Set<DraftColor>? = card.faces.firstOrNull()?.let(::record)?.get("colors")
        ?.let { runCatching { it.jsonArray.mapNotNull { color -> DraftColor.entries.firstOrNull { c -> c.name == color.jsonPrimitive.content } }.toSet() }.getOrNull() }

    private val costLine = Regex("(\\{[^{}]+})+")
    private fun record(face: DefinitionFace) = face.sourceData?.let { data -> runCatching { Json.parseToJsonElement(data).jsonObject }.getOrNull() }
}

data class PodDeckBucket(val label: String, val minimumManaValue: Int? = null, val andAbove: Boolean = false)

object PodDeckColumns {
    private val land = Regex("\\bLand\\b", RegexOption.IGNORE_CASE)

    fun buckets(columns: Int): List<PodDeckBucket> {
        require(columns in 2..10)
        return listOf(PodDeckBucket("Lands")) + (0..columns - 2).map { value ->
            val last = value == columns - 2
            PodDeckBucket(if (last) "$value+" else "$value", value, last)
        }
    }

    /** Index into buckets(). Only the front face supplies type and mana cost. */
    fun bucket(columns: Int, card: CardDefinition): Int {
        require(columns in 2..10)
        val front = card.faces.firstOrNull()
        if (front != null && land.containsMatchIn(front.typeLine)) return 0
        val value = PodDeckManaCost.front(card).value
        return 1 + value.coerceIn(0.0, (columns - 2).toDouble()).toInt()
    }

    /** Use explicit colors for color indicators/devoid when available; otherwise infer from front-face cost. */
    fun colorRank(card: CardDefinition, colors: Set<DraftColor>? = null): Int {
        val actual = colors ?: PodDeckManaCost.catalogColors(card) ?: PodDeckManaCost.front(card).colors
        return when (actual.size) {
            0 -> 6
            1 -> actual.single().ordinal
            else -> 5
        }
    }

    fun comparator(colors: (CardDefinition) -> Set<DraftColor>? = { null }): Comparator<CardDefinition> =
        compareBy<CardDefinition> { colorRank(it, colors(it)) }.thenBy { it.name.lowercase() }.thenBy { it.name }
}
