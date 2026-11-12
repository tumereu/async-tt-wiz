package org.asyncttwiz.android

import arcanum.api.Ability
import org.asyncttwiz.appui.FormatLocale
import org.asyncttwiz.appui.formatIn
import arcanum.api.Effect
import arcanum.api.Instructions
import arcanum.api.ManaKind
import arcanum.api.ManaSymbol
import arcanum.api.Step
import arcanum.api.isManaAbility
import kotlinx.serialization.json.*
import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.ParsedCard
import kotlin.math.roundToInt
import arcanum.api.CardDefinition as RulesCard
import arcanum.api.Color as RulesColor
import org.asyncttwiz.appui.sortedMutableMapOf

data class PodStats(
    val curve: Map<Double, PodStatsCurveBucket>,
    val creatures: Int,
    val noncreatures: Int,
    val lands: Long,
    val pips: Map<DraftColor, Double>,
    /** Land sources per colour (DRF-114): basics, basic land types and the lands' mana abilities. */
    val sources: Map<DraftColor, Long>,
    val colors: Map<PodStatsColor, Int>,
    val missingDefinitions: Int,
    /** Main lands (not basics) that produce no colored mana by [podStatsLandColors]. */
    val colorlessLands: Int = 0,
    /** Nonland sources per colour (DRF-115): nonland permanents whose mana abilities can add it. */
    val otherSources: Map<DraftColor, Long> = DraftColor.entries.associateWith { 0L },
    /** Lands that make at least one colour, basics included, and nonland permanents that do (the total line). */
    val sourceLands: Long = 0,
    val sourceOthers: Int = 0,
    /** Main nonland copies per colored-symbol pattern of the front cost, most common first. */
    val pipPatterns: List<Pair<String, Int>> = emptyList(),
    val noPips: Int = 0,
    /** Main copies per word of the front type line before the dash, basics included; most common first, zeros absent. */
    val types: List<Pair<String, Long>> = emptyList(),
    /** Main nonland copies per mana value and colour (DRF-111), by the same colour as [colors]; zeros absent. */
    val curveColors: Map<Double, Map<PodStatsColor, Int>> = emptyMap(),
    /** Main copies per subtype of the front type line (after the dash), basics' land types included; most common first,
     * zeros absent (DRF-180). */
    val subtypes: List<Pair<String, Long>> = emptyList(),
)

data class PodStatsCurveBucket(val creatures: Int = 0, val noncreatures: Int = 0) {
    val total: Int get() = creatures + noncreatures
}

enum class PodStatsColor { W, U, B, R, G, MULTICOLOR, COLORLESS }

private val podStatsSymbols = Regex("\\{([^{}]+)\\}")
private val podStatsLand = Regex("\\bLand\\b", RegexOption.IGNORE_CASE)
private val podStatsCreature = Regex("\\bCreature\\b", RegexOption.IGNORE_CASE)
private val podStatsPermanent = Regex("\\b(Artifact|Creature|Enchantment|Planeswalker|Battle)\\b", RegexOption.IGNORE_CASE)
private val podStatsBasicTypes = listOf("Plains", "Island", "Swamp", "Mountain", "Forest")

/** Each word of the front type line before the type/subtype dash: "Legendary Artifact Creature — Golem" gives three. */
fun podStatsTypeWords(typeLine: String): List<String> =
    typeLine.substringBefore(" // ").substringBefore('—').substringBefore(" - ")
        .split(Regex("\\s+")).filter { it.isNotEmpty() }.distinct()

/** Each subtype of the front type line, after the type/subtype dash: "Legendary Artifact Creature — Golem Construct" gives
 * two, "Creature — Giant // Sorcery — Adventure" only Giant (DRF-180). */
fun podStatsSubtypeWords(typeLine: String): List<String> {
    val front = typeLine.substringBefore(" // ")
    val rest = when {
        '—' in front -> front.substringAfter('—')
        " - " in front -> front.substringAfter(" - ")
        else -> return emptyList()
    }
    return rest.split(Regex("\\s+")).filter { it.isNotEmpty() }.distinct()
}

fun podStatsIsLand(card: CardDefinition): Boolean =
    card.faces.firstOrNull()?.let { podStatsLand.containsMatchIn(it.typeLine) } == true

/** The front cost's symbols other than generic, X/Y/Z and snow, in printed order: "{R/W}{R/W}", "{C}", "{2/U}". */
fun podStatsPipPattern(cost: String): String = podStatsSymbols.findAll(cost.substringBefore("//"))
    .map { it.groupValues[1].uppercase() }
    .filterNot { symbol -> symbol.all { it.isDigit() || it == '½' } || symbol in setOf("X", "Y", "Z", "S") }
    .joinToString("") { "{$it}" }

/** Whole pips plainly, halves as "4½"; anything finer to one decimal. */
fun podStatsPipText(value: Double): String {
    val whole = kotlin.math.floor(value + 1e-9).toLong()
    val rest = value - whole
    return when {
        rest < 1e-6 -> "$whole"
        kotlin.math.abs(rest - .5) < 1e-6 -> if (whole == 0L) "½" else "$whole½"
        else -> formatIn(FormatLocale.Root, "%.1f", value)
    }
}

/** Match the front-cost fallback used by PodDeckManaCost.front; never inspect the back face. */
fun podStatsFrontCost(card: CardDefinition): String {
    val face = card.faces.firstOrNull() ?: return ""
    return face.manaCost.ifEmpty {
        face.sourceData?.let { runCatching {
            Json.parseToJsonElement(it).jsonObject["manaCost"]?.jsonPrimitive?.content
        }.getOrNull() }.orEmpty()
    }.substringBefore("//")
}

/** The cost whose coloured symbols Stats counts (DRF-114, DRF-116): a split (fuse included) or aftermath card's two
 * halves together, as its mana cost is outside the Stack (CR 709.4); any other card's front cost as [podStatsFrontCost]
 * reads it (an adventurer or flip card's main part, never a back face). */
fun podStatsPipCost(card: CardDefinition): String =
    PodDeckManaCost.splitHalfCosts(card).takeIf { it.isNotEmpty() }?.joinToString("") ?: podStatsFrontCost(card)

fun podStatsPips(cost: String): Map<DraftColor, Double> {
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

/**
 * One source per produced colour per land, not mana quantity (DRF-115): its front face's basic land types (CR 305.6) and
 * the colours its mana abilities can add ([manaColors], from its Automatum definition; none when that is missing).
 */
fun podStatsLandColors(card: CardDefinition, manaColors: Set<DraftColor> = emptySet()): Set<DraftColor> {
    val face = card.faces.firstOrNull() ?: return emptySet()
    if (!podStatsLand.containsMatchIn(face.typeLine)) return emptySet()
    val result = mutableSetOf<DraftColor>()
    val subtypes = face.typeLine.substringAfter('—', face.typeLine.substringAfter(" - ", ""))
    podStatsBasicTypes.forEachIndexed { index, name ->
        if (Regex("\\b$name\\b", RegexOption.IGNORE_CASE).containsMatchIn(subtypes)) result += DraftColor.entries[index]
    }
    return result + manaColors
}

/**
 * DRF-115: the colours the mana abilities (CR 605) of [card]'s front face can add, one each: a card adding one colour at
 * a time, "any color" or a colour chosen as it enters counts every colour it may make. A spell adding mana (a ritual)
 * has no mana ability, and the tokens a card creates (Treasure) are abilities of their own, so neither counts.
 */
fun podStatsManaColors(card: RulesCard): Set<DraftColor> =
    card.faces.firstOrNull()?.abilities.orEmpty().filter { it.isManaAbility() }.flatMap { ability ->
        val instructions = when (ability) {
            is Ability.Activated -> ability.instructions
            is Ability.Triggered -> ability.instructions
            else -> return@flatMap emptyList()
        }
        podStatsAddedMana(instructions).flatMap(::podStatsKindColors)
    }.toSet()

/** Every mana an ability's resolution can add, in nested steps, modes and its own replacements too. */
private fun podStatsAddedMana(instructions: Instructions): List<ManaKind> {
    val found = mutableListOf<ManaKind>()
    fun visit(steps: List<Step>) {
        for (step in steps) when (step) {
            is Step.Do -> (step.effect as? Effect.AddMana)?.let { found += it.mana.kind }
            is Step.Simultaneously -> visit(step.steps)
            is Step.Pay -> visit(step.steps)
            is Step.May -> visit(step.steps)
            is Step.If -> { visit(step.then); visit(step.otherwise) }
            is Step.ForEach -> visit(step.steps)
            is Step.Repeat -> visit(step.steps)
            is Step.RepeatWhile -> visit(step.steps)
            else -> Unit
        }
    }
    fun visit(instructions: Instructions) {
        visit(instructions.steps)
        instructions.modes?.options.orEmpty().forEach { visit(it.instructions) }
        instructions.replacements.forEach { replacement -> replacement.instead?.let { visit(it) } }
    }
    visit(instructions)
    return found
}

private fun RulesColor.draftColor(): DraftColor = DraftColor.entries.first { it.name == symbol.toString() }

/** The colours one kind of added mana can be: its symbols' colours, or every colour it may be chosen as. Mana "of any
 * type a land could produce" or "of the type produced" adds no colour of its own. */
private fun podStatsKindColors(kind: ManaKind): Set<DraftColor> = when (kind) {
    is ManaKind.Fixed -> kind.mana.symbols.flatMap { symbol ->
        when (symbol) {
            is ManaSymbol.Colored -> listOf(symbol.color)
            is ManaSymbol.Hybrid -> listOf(symbol.first, symbol.second)
            is ManaSymbol.GenericHybrid -> listOf(symbol.color)
            is ManaSymbol.ColorlessHybrid -> listOf(symbol.color)
            is ManaSymbol.Phyrexian -> listOf(symbol.color)
            is ManaSymbol.PhyrexianHybrid -> listOf(symbol.first, symbol.second)
            else -> emptyList()
        }
    }.map { it.draftColor() }.toSet()
    is ManaKind.Choose -> kind.colors?.let { colors ->
        // A chosen, remembered or "among" colour may be any of the five.
        if (colors.all { it is arcanum.api.ColorRef.Literal }) colors.map { (it as arcanum.api.ColorRef.Literal).color.draftColor() }.toSet()
        else DraftColor.entries.toSet()
    } ?: DraftColor.entries.toSet()
    is ManaKind.EachColorAmong -> DraftColor.entries.toSet()
    is ManaKind.AsCouldProduce, is ManaKind.SameAsProduced -> emptySet()
}

/** DRF-115: [podStatsManaColors] of every card the device could read, by definition digest; a card it couldn't read
 * makes no colour. */
fun podStatsManaColors(parsed: Map<String, ParsedCard>): Map<String, Set<DraftColor>> =
    parsed.mapValues { (_, card) -> (card as? ParsedCard.Read)?.let { podStatsManaColors(it.definition) }.orEmpty() }

/** Prefer catalog identity, which includes rules symbols and both faces, not just the cost. */
fun podStatsColorIdentity(card: CardDefinition, copyColors: Set<DraftColor>): PodStatsColor {
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

/** [manaColors]: the colours each card's mana abilities can add, by definition digest ([podStatsManaColors]); a card
 * missing there makes no colour beyond its basic land types. */
fun podStats(build: PodDeckBuild, copies: Map<String, DraftCopy>, definitions: Map<String, CardDefinition>,
    manaColors: Map<String, Set<DraftColor>> = emptyMap()): PodStats {
    val curve = sortedMutableMapOf<Double, PodStatsCurveBucket>()
    val pips = DraftColor.entries.associateWith { 0.0 }.toMutableMap()
    val sources = DraftColor.entries.mapIndexed { index, color -> color to build.basics.podBuildValues()[index].toLong() }.toMap().toMutableMap()
    val others = DraftColor.entries.associateWith { 0L }.toMutableMap()
    var sourceLands = build.basics.total
    var sourceOthers = 0
    val colors = PodStatsColor.entries.associateWith { 0 }.toMutableMap()
    var creatures = 0
    var noncreatures = 0
    var lands = build.basics.total
    var missing = 0
    var colorlessLands = 0
    val patterns = mutableMapOf<String, Int>()
    val types = mutableMapOf<String, Long>()
    val subtypes = mutableMapOf<String, Long>()
    val curveColors = mutableMapOf<Double, MutableMap<PodStatsColor, Int>>()
    // Basics are counts, not drafted copies: "Basic Land — Plains" and so on.
    if (build.basics.total > 0) for (word in podStatsTypeWords("Basic Land")) types[word] = (types[word] ?: 0) + build.basics.total
    build.basics.podBuildValues().forEachIndexed { index, count ->
        if (count > 0) podStatsBasicTypes[index].let { subtypes[it] = (subtypes[it] ?: 0) + count }
    }
    for (entry in build.copies.filter { it.category == PodDeckCategory.MAIN }) {
        val copy = copies[entry.copyId]
        val card = copy?.definition?.let(definitions::get)
        val face = card?.faces?.firstOrNull()
        if (card == null || face == null) { missing++; continue }
        for (word in podStatsTypeWords(face.typeLine)) types[word] = (types[word] ?: 0) + 1
        for (word in podStatsSubtypeWords(face.typeLine)) subtypes[word] = (subtypes[word] ?: 0) + 1
        if (podStatsLand.containsMatchIn(face.typeLine)) {
            lands++
            val produced = podStatsLandColors(card, manaColors[copy.definition].orEmpty())
            if (produced.isEmpty()) colorlessLands++ else sourceLands++
            for (color in produced) sources[color] = sources.getValue(color) + 1
        } else {
            // A nonland permanent with a mana ability: a mana creature or rock (DRF-115).
            val produced = if (podStatsPermanent.containsMatchIn(face.typeLine.substringBefore(" // "))) manaColors[copy.definition].orEmpty() else emptySet()
            if (produced.isNotEmpty()) sourceOthers++
            for (color in produced) others[color] = others.getValue(color) + 1
            val creature = podStatsCreature.containsMatchIn(face.typeLine)
            if (creature) creatures++ else noncreatures++
            val mv = PodDeckManaCost.front(card).value
            val bucket = curve[mv] ?: PodStatsCurveBucket()
            curve[mv] = if (creature) bucket.copy(creatures = bucket.creatures + 1)
                else bucket.copy(noncreatures = bucket.noncreatures + 1)
            val color = podStatsColorIdentity(card, copy?.colors.orEmpty())
            colors[color] = colors.getValue(color) + 1
            val slot = curveColors.getOrPut(mv) { mutableMapOf() }
            slot[color] = (slot[color] ?: 0) + 1
            val pattern = podStatsPipPattern(podStatsPipCost(card))
            patterns[pattern] = (patterns[pattern] ?: 0) + 1
        }
        for ((color, count) in podStatsPips(podStatsPipCost(card))) pips[color] = pips.getValue(color) + count
    }
    val listed = patterns.filterKeys { it.isNotEmpty() }.toList()
        .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
    val mostFirst = compareByDescending<Pair<String, Long>> { it.second }.thenBy { it.first }
    val typeCounts = types.toList().sortedWith(mostFirst)
    return PodStats(curve, creatures, noncreatures, lands, pips, sources, colors, missing, colorlessLands, others, sourceLands,
        sourceOthers, listed, patterns[""] ?: 0, typeCounts, curveColors, subtypes.toList().sortedWith(mostFirst))
}

private fun podStatsCurveLabel(slot: Int) = if (slot == 7) "7+" else "$slot"

fun PodStats.curveBuckets(): Map<String, PodStatsCurveBucket> = (0..7).associate { bucket ->
    val entries = curve.entries.filter { it.key.toInt().coerceIn(0, 7) == bucket }
    podStatsCurveLabel(bucket) to PodStatsCurveBucket(
        entries.sumOf { it.value.creatures }, entries.sumOf { it.value.noncreatures })
}

/** The curve's slots by colour (DRF-111): each slot holds a count for every [PodStatsColor], in its order. */
fun PodStats.curveColorBuckets(): Map<String, Map<PodStatsColor, Int>> = (0..7).associate { bucket ->
    val entries = curveColors.entries.filter { it.key.toInt().coerceIn(0, 7) == bucket }
    podStatsCurveLabel(bucket) to PodStatsColor.entries.associateWith { color -> entries.sumOf { it.value[color] ?: 0 } }
}

/** A colour's name in the curve's Color view and its legend (DRF-111). */
fun PodStatsColor.podStatsCurveName(): String = when (this) {
    PodStatsColor.MULTICOLOR -> "Gold"
    PodStatsColor.COLORLESS -> "Colorless"
    else -> name
}

/** A colour's bars in the mana table (DRF-114): its share of all coloured pips, of all sources, and the lands' part of
 * that sources share. A dual land counts for both its colours, so the source shares add up to 100% as the pip shares do;
 * a bar's width is its share of the full width, nothing scaled, so what is drawn sums to one whole. */
data class PodStatsManaShare(val pips: Double, val sources: Double, val lands: Double)

fun PodStats.manaShares(): Map<DraftColor, PodStatsManaShare> {
    val pipShares = podStatsShares(pips)
    val sourceTotal = DraftColor.entries.sumOf { sources.getValue(it) + otherSources.getValue(it) }
    fun share(count: Long) = if (sourceTotal == 0L) 0.0 else count.toDouble() / sourceTotal
    return DraftColor.entries.associateWith { color ->
        val lands = sources.getValue(color)
        PodStatsManaShare(pipShares.getValue(color), share(lands + otherSources.getValue(color)), share(lands))
    }
}

/** A colour's pip line in the mana table (DRF-114): "9 pips · 34%", "½ pip · 2%", "0 pips · 0%". */
fun podStatsPipLine(pips: Double, share: Double): String =
    "${podStatsPipText(pips)} ${if (pips > 0.0 && pips <= 1.0) "pip" else "pips"} · ${podStatsPercent(share)}"

/** A colour's source line: "8 sources · 30%", "1 source · 4%", lands and other producers added up. */
fun podStatsSourceLine(sources: Long, share: Double): String =
    "$sources ${if (sources == 1L) "source" else "sources"} · ${podStatsPercent(share)}"

private fun podStatsPercent(share: Double) = "${(share * 100).roundToInt()}%"

/** Empty donuts have zero shares, never NaN; each chart normalizes independently. */
fun <T> podStatsShares(values: Map<T, Number>): Map<T, Double> {
    val total = values.values.sumOf { it.toDouble() }
    return values.mapValues { (_, count) -> if (total == 0.0) 0.0 else count.toDouble() / total }
}

/** Drops control characters other than line breaks and keeps the limits, so edits never become unsaveable. */
fun PodDeckNotes.podNotesSanitized(): PodDeckNotes {
    var cleaned = text.replace("\r\n", "\n").filter { !it.isISOControl() || it == '\n' }.take(PodDeckNotes.MAX_TEXT)
    if (cleaned.lastOrNull()?.isHighSurrogate() == true) cleaned = cleaned.dropLast(1)
    val values = (counters + List(PodDeckNotes.COUNTERS) { 0 }).take(PodDeckNotes.COUNTERS)
        .map { it.coerceIn(0, PodDeckNotes.MAX_COUNTER) }
    return PodDeckNotes(cleaned, values)
}

fun PodDeckNotes.podNotesAdjust(index: Int, delta: Int): PodDeckNotes =
    copy(counters = counters.mapIndexed { i, value -> if (i == index) (value + delta).coerceIn(0, PodDeckNotes.MAX_COUNTER) else value })
