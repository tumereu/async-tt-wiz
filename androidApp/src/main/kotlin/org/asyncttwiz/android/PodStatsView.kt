package org.asyncttwiz.android

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.asyncttwiz.protocol.*
import kotlin.math.roundToInt

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
) {
    val stats = remember(build, copies, definitions) { podStats(build, copies, definitions) }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        LazyColumn(Modifier.fillMaxSize().testTag("pod-stats"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { PodStatsCurve(stats.curveBuckets()) }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf("Creature" to stats.creatures, "Noncreature" to stats.noncreatures, "Land" to stats.lands).forEachIndexed { index, (label, count) ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (index < 2) PodStatsSwatch(if (index == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer)
                            Text("$label: $count", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    PodStatsDonut("Pips", podStatsShares(stats.pips).mapKeys { it.key.name }, podStatsPalette.take(5), Modifier.weight(1f))
                    PodStatsDonut("Sources", podStatsShares(stats.sources).mapKeys { it.key.name }, podStatsPalette.take(5), Modifier.weight(1f))
                    if (wide) PodStatsColors(stats, Modifier.weight(1f))
                }
            }
            if (!wide) item {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    PodStatsColors(stats, Modifier.widthIn(max = 240.dp).fillMaxWidth())
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

@Composable
private fun PodStatsCurve(values: Map<String, PodStatsCurveBucket>) {
    val creatureColor = MaterialTheme.colorScheme.primary
    val noncreatureColor = MaterialTheme.colorScheme.primaryContainer
    val outline = MaterialTheme.colorScheme.outline
    val textColor = MaterialTheme.colorScheme.onSurface
    val textSize = with(LocalDensity.current) { 11.sp.toPx() }
    val paint = remember(textColor, textSize) { Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor.toArgb(); this.textSize = textSize; textAlign = Paint.Align.CENTER
    } }
    Canvas(Modifier.fillMaxWidth().height(180.dp).testTag("pod-stats-curve").semantics {
        contentDescription = values.entries.joinToString { (label, value) ->
            "$label: ${value.creatures} creature, ${value.noncreatures} noncreature"
        }
    }) {
        val maximum = (values.values.maxOfOrNull { it.total } ?: 0).coerceAtLeast(1)
        val slot = size.width / values.size.coerceAtLeast(1)
        val baseline = size.height - textSize * 1.8f
        val chartHeight = (baseline - textSize * 2).coerceAtLeast(0f)
        values.entries.forEachIndexed { index, (label, value) ->
            val creatureHeight = value.creatures.toFloat() / maximum * chartHeight
            val noncreatureHeight = value.noncreatures.toFloat() / maximum * chartHeight
            val height = creatureHeight + noncreatureHeight
            val left = slot * index + slot * .15f
            drawRect(creatureColor, Offset(left, baseline - creatureHeight), Size(slot * .7f, creatureHeight))
            drawRect(noncreatureColor, Offset(left, baseline - height), Size(slot * .7f, noncreatureHeight))
            if (value.total > 0) {
                drawRect(outline, Offset(left, baseline - height), Size(slot * .7f, height), style = Stroke(1.dp.toPx()))
                drawContext.canvas.nativeCanvas.drawText("${value.total}", slot * (index + .5f), baseline - height - textSize * .3f, paint)
            }
            drawContext.canvas.nativeCanvas.drawText(label, slot * (index + .5f), size.height - textSize * .25f, paint)
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
