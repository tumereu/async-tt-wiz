package org.asyncttwiz.android

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.asyncttwiz.protocol.*
import kotlin.math.roundToInt
import org.asyncttwiz.appui.NativeTextPaint
import org.asyncttwiz.appui.drawNativeText

// Same W/U/B/R/G, gold and neutral palette as CardText's mana badges.
private val podStatsPalette = listOf(
    Color(0xffffefbd), Color(0xff8fc9ec), Color(0xffb8aec9), Color(0xffefa799),
    Color(0xff9dceaa), Color(0xffffd77b), Color(0xffd6dbdd),
)

@Composable
fun PodStatsView(
    build: PodDeckBuild,
    copies: Map<String, DraftCopy>,
    definitions: Map<String, CardDefinition>,
    modifier: Modifier = Modifier,
    /** The caller's private notes; null hides the notes and counters. */
    notes: PodDeckNotes? = null,
    onNotes: ((PodDeckNotes) -> PodDeckNotes) -> Unit = {},
    curveChoice: PodStatsCurveChoice = rememberPodStatsCurveChoice(),
) {
    // DRF-115: mana sources from the cards' Automatum definitions, worked out off the main thread (none until known).
    val check = LocalCardListSupport.current
    val manaColors by produceState(emptyMap<String, Set<DraftColor>>(), check, definitions) {
        value = check?.let {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                runCatching { podStatsManaColors(it.parsed(definitions.map { (digest, card) -> SubmittedDefinition(digest, card) })) }.getOrNull()
            }
        }.orEmpty()
    }
    val stats = remember(build, copies, definitions, manaColors) { podStats(build, copies, definitions, manaColors) }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        LazyColumn(Modifier.fillMaxSize().testTag("pod-stats"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Column {
                    PodStatsCurveToggle(curveChoice.byColor, curveChoice::choose)
                    PodStatsCurve(stats, curveChoice.byColor)
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (curveChoice.byColor) PodStatsCurveColorLegend()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        listOf("Creature" to stats.creatures, "Noncreature" to stats.noncreatures, "Land" to stats.lands).forEachIndexed { index, (label, count) ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (index < 2 && !curveChoice.byColor) PodStatsSwatch(if (index == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer)
                                Text("$label: $count", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
            if (stats.types.isNotEmpty()) item { PodStatsTypes(stats.types) }
            if (stats.subtypes.isNotEmpty()) item { PodStatsSubtypes(stats.subtypes) }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    PodStatsMana(stats, Modifier.weight(1f))
                    if (wide) PodStatsColors(stats, Modifier.weight(1f))
                }
            }
            if (!wide) item {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    PodStatsColors(stats, Modifier.widthIn(max = 240.dp).fillMaxWidth())
                }
            }
            if (notes != null) item("notes") { PodStatsNotes(notes, onNotes) }
        }
    }
}

/** The Stats chips' look: the type counts and the pip counts (DRF-113, DRF-116). */
@Composable
private fun PodStatsChip(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(modifier, shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Box(Modifier.padding(horizontal = 8.dp, vertical = 3.dp)) { content() }
    }
}

/** Main copies per type-line word, as wrapping "Creature 15" labels, most common first. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PodStatsTypes(types: List<Pair<String, Long>>) {
    FlowRow(Modifier.fillMaxWidth().testTag("pod-stats-types"),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for ((name, count) in types) PodStatsChip(Modifier.testTag("pod-stats-type-$name").semantics(mergeDescendants = true) {}) {
            Text(buildAnnotatedString {
                append("$name ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("$count") }
            }, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

/** DRF-180: how many subtypes show before the rest go behind "+N more" (all of them when only one more would hide). */
const val POD_STATS_SUBTYPES_SHOWN = 6

/**
 * DRF-180 (owner, 2026-11-11): Main copies per subtype as "Elf 4" chips like the type row's, most common first: the first
 * [POD_STATS_SUBTYPES_SHOWN], then a "+N more" chip whose tap shows the rest, and "Fewer" after them to hide them again.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PodStatsSubtypes(subtypes: List<Pair<String, Long>>) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val folds = subtypes.size > POD_STATS_SUBTYPES_SHOWN + 1
    val shown = if (folds && !expanded) subtypes.take(POD_STATS_SUBTYPES_SHOWN) else subtypes
    FlowRow(Modifier.fillMaxWidth().testTag("pod-stats-subtypes"),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for ((name, count) in shown) PodStatsChip(Modifier.testTag("pod-stats-subtype-$name").semantics(mergeDescendants = true) {}) {
            Text(buildAnnotatedString {
                append("$name ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("$count") }
            }, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
        if (folds) PodStatsChip(Modifier.testTag("pod-stats-subtypes-more").clickable { expanded = !expanded }) {
            Text(if (expanded) "Fewer" else "+${subtypes.size - POD_STATS_SUBTYPES_SHOWN} more",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1)
        }
    }
}

/** The sources' total line: "Sources: 17 lands, 3 others" ("1 land", "1 other"). */
fun podStatsSourcesLine(lands: Long, others: Int): String =
    "Sources: $lands ${if (lands == 1L) "land" else "lands"}, $others ${if (others == 1) "other" else "others"}"

private val PodStatsPipBarHeight = 5.dp
private val PodStatsSourceBarHeight = 10.dp

/** A short sample of one bar kind and its name, for the mana table's legend. */
@Composable
private fun PodStatsBarKey(color: Color, height: Dp, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(Modifier.size(16.dp, height)) { drawRoundRect(color, cornerRadius = CornerRadius(size.height / 2)) }
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

/**
 * Per colour its pips and sources with their shares in percent, and two bars where the full width is 100%, pips over
 * sources (lands solid, other producers lighter), then a total line and the cards grouped by their colored cost symbols,
 * as chips (DRF-114, DRF-115, DRF-116).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PodStatsMana(stats: PodStats, modifier: Modifier) {
    val style = MaterialTheme.typography.bodyMedium
    val shares = stats.manaShares()
    val pip = MaterialTheme.colorScheme.onSurface
    val solid = MaterialTheme.colorScheme.primary
    val light = MaterialTheme.colorScheme.primary.copy(alpha = .35f)
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Column(modifier.testTag("pod-stats-mana"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FlowRow(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            Text("Pips and sources", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.testTag("pod-stats-bar-legend"), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PodStatsBarKey(pip, PodStatsPipBarHeight, "Pips")
                PodStatsBarKey(solid, PodStatsSourceBarHeight, "Lands")
                PodStatsBarKey(light, PodStatsSourceBarHeight, "Others")
            }
        }
        for (color in DraftColor.entries) Row(Modifier.fillMaxWidth().testTag("pod-stats-mana-${color.name}")
            .semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
            val share = shares.getValue(color)
            CardText("{${color.name}}", Modifier.width(32.dp), style = MaterialTheme.typography.titleMedium)
            Column(Modifier.widthIn(min = 104.dp)) {
                val lines = MaterialTheme.typography.labelMedium
                Text(podStatsPipLine(stats.pips.getValue(color), share.pips), style = lines, maxLines = 1)
                Text(podStatsSourceLine(stats.sources.getValue(color) + stats.otherSources.getValue(color), share.sources),
                    style = lines, maxLines = 1)
            }
            Spacer(Modifier.width(12.dp))
            Canvas(Modifier.weight(1f).height(PodStatsPipBarHeight + 2.dp + PodStatsSourceBarHeight)
                .testTag("pod-stats-bar-${color.name}").semantics {
                    contentDescription = "${color.name}: ${(share.pips * 100).roundToInt()}% of pips, ${(share.sources * 100).roundToInt()}% of sources"
                }) {
                val pipHeight = PodStatsPipBarHeight.toPx()
                val sourceHeight = PodStatsSourceBarHeight.toPx()
                val sourceTop = size.height - sourceHeight
                fun bar(paint: Color, width: Float, top: Float, height: Float) {
                    if (width > 0f) drawRoundRect(paint, Offset(0f, top), Size(width, height), CornerRadius(height / 2))
                }
                bar(track, size.width, 0f, pipHeight)
                bar(pip, (size.width * share.pips).toFloat(), 0f, pipHeight)
                bar(track, size.width, sourceTop, sourceHeight)
                bar(light, (size.width * share.sources).toFloat(), sourceTop, sourceHeight)
                bar(solid, (size.width * share.lands).toFloat(), sourceTop, sourceHeight)
            }
        }
        Text(podStatsSourcesLine(stats.sourceLands, stats.sourceOthers), Modifier.testTag("pod-stats-sources"),
            style = MaterialTheme.typography.labelMedium)
        if (stats.colorlessLands > 0) Text("Lands without a color: ${stats.colorlessLands}", style = MaterialTheme.typography.labelMedium)
        if (stats.pipPatterns.isNotEmpty() || stats.noPips > 0) {
            Spacer(Modifier.height(8.dp))
            FlowRow(Modifier.fillMaxWidth().testTag("pod-stats-patterns"),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                stats.pipPatterns.forEachIndexed { index, (pattern, count) ->
                    PodStatsChip { CardText("$count× $pattern", Modifier.testTag("pod-stats-pattern-$index"), style = style, maxLines = 1) }
                }
                if (stats.noPips > 0) PodStatsChip {
                    Text("${stats.noPips}× ∅", Modifier.testTag("pod-stats-no-pips")
                        .semantics { contentDescription = "${stats.noPips}× no colored pips" }, style = style, maxLines = 1)
                }
            }
        }
    }
}

private val podStatsCounterNames = listOf("White", "Blue", "Black", "Red", "Green", "Colorless", "Gold", "Neutral")

/** Private free text plus eight tally counters, saved on the server for this draft attempt. */
@Composable
private fun PodStatsNotes(notes: PodDeckNotes, onNotes: ((PodDeckNotes) -> PodDeckNotes) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(notes.text, { text -> onNotes { it.copy(text = text) } },
            Modifier.fillMaxWidth().heightIn(min = 120.dp).testTag("pod-stats-notes"),
            label = { Text("Notes") }, supportingText = { Text("${notes.text.length} / ${PodDeckNotes.MAX_TEXT}") })
        notes.counters.chunked(4).forEachIndexed { row, values ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                values.forEachIndexed { column, value ->
                    val index = row * 4 + column
                    PodStatsCounter(index, value, { delta -> onNotes { it.podNotesAdjust(index, delta) } }, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PodStatsCounter(index: Int, value: Int, onAdjust: (Int) -> Unit, modifier: Modifier) {
    val name = podStatsCounterNames[index]
    Surface(modifier.testTag("pod-stats-counter-$index"), shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(top = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.semantics(mergeDescendants = true) { contentDescription = "$name $value" },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                when (index) {
                    in 0..5 -> CardText("{${"WUBRGC"[index]}}", style = MaterialTheme.typography.titleMedium)
                    else -> Canvas(Modifier.size(18.dp)) {
                        val radius = size.minDimension * .47f
                        drawCircle(if (index == 6) podStatsPalette[5] else podStatsPalette[6], radius)
                        drawCircle(Color(0xff536066), radius, style = Stroke(size.minDimension * .045f))
                    }
                }
                Text("$value", style = MaterialTheme.typography.titleMedium)
            }
            Row(Modifier.fillMaxWidth()) {
                listOf(-1 to "−", 1 to "+").forEach { (delta, glyph) ->
                    val enabled = if (delta < 0) value > 0 else value < PodDeckNotes.MAX_COUNTER
                    Box(Modifier.weight(1f).height(40.dp).clickable(enabled = enabled) { onAdjust(delta) }
                        .semantics { contentDescription = "${if (delta < 0) "Decrease" else "Increase"} $name" },
                        contentAlignment = Alignment.Center) {
                        Text(glyph, style = MaterialTheme.typography.titleMedium,
                            color = LocalContentColor.current.copy(alpha = if (enabled) 1f else .38f))
                    }
                }
            }
        }
    }
}

@Composable
private fun PodStatsColors(stats: PodStats, modifier: Modifier) {
    PodStatsDonut("Colors", podStatsShares(stats.colors).mapKeys { (color, _) -> when (color) {
        PodStatsColor.MULTICOLOR -> "Multicolor"
        PodStatsColor.COLORLESS -> "Colorless"
        else -> color.name
    } }, podStatsPalette, modifier)
}

/** The small Type | Color choice above the mana curve (DRF-111). */
@Composable
private fun PodStatsCurveToggle(byColor: Boolean, choose: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().testTag("pod-stats-curve-toggle"), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End)) {
        listOf(false to "Type", true to "Color").forEach { (value, label) ->
            val chosen = byColor == value
            Surface(selected = chosen, onClick = { choose(value) }, Modifier.testTag("pod-stats-curve-${label.lowercase()}"),
                shape = MaterialTheme.shapes.small,
                color = if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh) {
                Text(label, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }
    }
}

/** The Color view's legend: the curve's stack from the bottom up (DRF-111). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PodStatsCurveColorLegend() {
    FlowRow(Modifier.fillMaxWidth().testTag("pod-stats-curve-legend"), horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        PodStatsColor.entries.forEachIndexed { index, color ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                PodStatsSwatch(podStatsPalette[index])
                Text(color.podStatsCurveName(), style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }
    }
}

/** DRF-111 (owner playtest 2026-11-09): a bar's number: in Type view "creatures · noncreatures" ([counts] creature first),
 * in Color view its total. */
fun podStatsCurveBarLabel(counts: List<Int>, byColor: Boolean): String =
    if (byColor || counts.size != 2) "${counts.sum()}" else "${counts[0]} · ${counts[1]}"

/** The mana curve: the same slots, scale and totals in both views, stacked creature/noncreature or by colour (DRF-111). */
@Composable
private fun PodStatsCurve(stats: PodStats, byColor: Boolean) {
    val creatureColor = MaterialTheme.colorScheme.primary
    val noncreatureColor = MaterialTheme.colorScheme.primaryContainer
    val outline = MaterialTheme.colorScheme.outline
    val textColor = MaterialTheme.colorScheme.onSurface
    val textSize = with(LocalDensity.current) { 11.sp.toPx() }
    val paint = remember(textColor, textSize) { NativeTextPaint().apply {
        color = textColor.toArgb(); this.textSize = textSize; textAlign = NativeTextPaint.TextAlign.Center
    } }
    // Per slot its label and its stack from the bottom up.
    val slots: List<Pair<String, List<Pair<Color, Int>>>> = if (byColor) stats.curveColorBuckets().map { (label, counts) ->
        label to PodStatsColor.entries.mapIndexed { index, color -> podStatsPalette[index] to counts.getValue(color) }
    } else stats.curveBuckets().map { (label, value) ->
        label to listOf(creatureColor to value.creatures, noncreatureColor to value.noncreatures)
    }
    Canvas(Modifier.fillMaxWidth().height(180.dp).testTag("pod-stats-curve").semantics {
        contentDescription = if (byColor) stats.curveColorBuckets().entries.joinToString { (label, counts) ->
            "$label: " + counts.filterValues { it > 0 }.entries.joinToString { (color, count) -> "$count ${color.podStatsCurveName()}" }.ifEmpty { "none" }
        } else stats.curveBuckets().entries.joinToString { (label, value) ->
            "$label: ${value.creatures} creature, ${value.noncreatures} noncreature"
        }
    }) {
        val maximum = (slots.maxOfOrNull { (_, parts) -> parts.sumOf { it.second } } ?: 0).coerceAtLeast(1)
        val slot = size.width / slots.size.coerceAtLeast(1)
        val baseline = size.height - textSize * 1.8f
        val chartHeight = (baseline - textSize * 2).coerceAtLeast(0f)
        slots.forEachIndexed { index, (label, parts) ->
            val left = slot * index + slot * .15f
            var top = baseline
            for ((color, count) in parts) if (count > 0) {
                val height = count.toFloat() / maximum * chartHeight
                top -= height
                drawRect(color, Offset(left, top), Size(slot * .7f, height))
                // A fine line keeps neighboring pale mana colors distinct, as in the donut.
                if (byColor) drawRect(outline, Offset(left, top), Size(slot * .7f, height), style = Stroke(1.dp.toPx()))
            }
            val total = parts.sumOf { it.second }
            if (total > 0) {
                drawRect(outline, Offset(left, top), Size(slot * .7f, baseline - top), style = Stroke(1.dp.toPx()))
                drawNativeText(podStatsCurveBarLabel(parts.map { it.second }, byColor),
                    slot * (index + .5f), top - textSize * .3f, paint)
            }
            drawNativeText(label, slot * (index + .5f), size.height - textSize * .25f, paint)
        }
    }
}

@Composable
private fun PodStatsDonut(title: String, shares: Map<String, Double>, colors: List<Color>, modifier: Modifier) {
    val outline = MaterialTheme.colorScheme.outline
    val track = MaterialTheme.colorScheme.surfaceVariant
    val surface = MaterialTheme.colorScheme.surface
    Column(modifier.testTag("pod-stats-donut-$title").clearAndSetSemantics {
        contentDescription = "$title: " + shares.entries.joinToString { "${it.key} ${(it.value * 100).roundToInt()}%" }
    }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        Canvas(Modifier.sizeIn(maxWidth = 128.dp, maxHeight = 128.dp).fillMaxWidth().aspectRatio(1f)) {
            val stroke = size.minDimension * .23f
            val radius = (size.minDimension - stroke) / 2 - 1.dp.toPx()
            val topLeft = center - Offset(radius, radius)
            val diameter = Size(radius * 2, radius * 2)
            drawCircle(track, radius, style = Stroke(stroke))
            var start = -90f
            shares.values.forEachIndexed { index, share ->
                val sweep = (share * 360).toFloat()
                if (sweep > 0f) {
                    drawArc(colors[index], start, sweep, false, topLeft, diameter, style = Stroke(stroke))
                    // A fine gap keeps neighboring pale mana colors distinct in either theme.
                    if (share < 1.0) drawArc(surface, start, 1.5f, false, topLeft, diameter, style = Stroke(stroke + 1.dp.toPx()))
                    start += sweep
                }
            }
            drawCircle(outline, radius + stroke / 2, style = Stroke(1.dp.toPx()))
            drawCircle(outline, radius - stroke / 2, style = Stroke(1.dp.toPx()))
        }
        Column(Modifier.widthIn(max = 180.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            shares.entries.forEachIndexed { index, (label, share) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PodStatsSwatch(colors[index])
                    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                    Text("${(share * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun PodStatsSwatch(color: Color) {
    val outline = MaterialTheme.colorScheme.outline
    Canvas(Modifier.size(10.dp)) {
        drawCircle(color)
        drawCircle(outline, style = Stroke(1.dp.toPx()))
    }
}
