package org.asyncttwiz.android

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.asyncttwiz.protocol.*

@Composable
fun PodStatsView(
    build: PodDeckBuild,
    copies: Map<String, DraftCopy>,
    definitions: Map<String, CardDefinition>,
    modifier: Modifier = Modifier,
) {
    val stats = remember(build, copies, definitions) { podStats(build, copies, definitions) }
    LazyColumn(modifier.fillMaxSize().testTag("pod-stats"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text("Main including basics", style = MaterialTheme.typography.titleLarge)
            if (stats.missingDefinitions > 0) Text("Card details unavailable: ${stats.missingDefinitions}", color = MaterialTheme.colorScheme.error)
        }
        item {
            Text("Mana curve · nonland cards", style = MaterialTheme.typography.titleMedium)
            val curve = ((0..7).map { it.toDouble() } + stats.curve.keys).distinct().sorted().associate { mv ->
                podStatsNumber(mv) to (stats.curve[mv] ?: 0).toDouble()
            }
            PodStatsBars(curve, MaterialTheme.colorScheme.primary)
        }
        item {
            Text("Card types", style = MaterialTheme.typography.titleMedium)
            PodStatsBars(linkedMapOf("Creature" to stats.creatures.toDouble(), "Noncreature" to stats.noncreatures.toDouble(),
                "Land" to stats.lands.toDouble()), MaterialTheme.colorScheme.tertiary)
            Text("Noncreature excludes lands.", style = MaterialTheme.typography.bodySmall)
        }
        item {
            Text("Colored mana pips", style = MaterialTheme.typography.titleMedium)
            val total = stats.pips.values.sum()
            Canvas(Modifier.fillMaxWidth().height(180.dp).semantics {
                contentDescription = "Colored mana pip pie chart: " + stats.pips.entries.joinToString { "${it.key.name}: ${podStatsNumber(it.value)}" }
            }) {
                val diameter = size.minDimension
                val origin = Offset((size.width - diameter) / 2, 0f)
                if (total == 0.0) drawCircle(Color.Gray, diameter / 2, Offset(size.width / 2, diameter / 2))
                var angle = -90f
                for ((color, count) in stats.pips) {
                    if (count <= 0) continue
                    val sweep = (count / total * 360).toFloat()
                    drawArc(podStatsColor(color), angle, sweep, true, origin, Size(diameter, diameter))
                    angle += sweep
                }
            }
            PodStatsLegend(stats.pips.entries.associate { it.key.name to it.value })
            if (total == 0.0) Text("No colored pips", style = MaterialTheme.typography.bodySmall)
        }
        item {
            Text("Mana sources · approximate", style = MaterialTheme.typography.titleMedium)
            PodStatsBars(stats.sources.entries.associate { it.key.name to it.value.toDouble() }, MaterialTheme.colorScheme.secondary)
            Text("Lands and basics only. Each land counts once per produced color, using basic land types and Add text; not rules analysis.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PodStatsBars(values: Map<String, Double>, color: Color) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val textSize = with(LocalDensity.current) { 11.sp.toPx() }
    val paint = remember(textColor, textSize) { Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = textColor.toArgb(); this.textSize = textSize; textAlign = Paint.Align.CENTER
    } }
    Canvas(Modifier.fillMaxWidth().height(180.dp).semantics {
        contentDescription = values.entries.joinToString { "${it.key}: ${podStatsNumber(it.value)}" }
    }) {
        val maximum = (values.values.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
        val slot = size.width / values.size.coerceAtLeast(1)
        val baseline = size.height - textSize * 1.8f
        val chartHeight = baseline - textSize * 2
        values.entries.forEachIndexed { index, (label, value) ->
            val height = (value / maximum * chartHeight).toFloat()
            drawRect(color, Offset(slot * index + slot * 0.15f, baseline - height), Size(slot * 0.7f, height))
            drawContext.canvas.nativeCanvas.drawText(podStatsNumber(value), slot * (index + 0.5f), baseline - height - textSize * 0.3f, paint)
            drawContext.canvas.nativeCanvas.drawText(label, slot * (index + 0.5f), size.height - textSize * 0.25f, paint)
        }
    }
    // A wrapping numeric legend stays legible even with many distinct mana values.
    PodStatsLegend(values)
}

@Composable
private fun PodStatsLegend(values: Map<String, Double>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        values.forEach { (label, value) -> Text("$label: ${podStatsNumber(value)}", style = MaterialTheme.typography.bodyMedium) }
    }
}

private fun podStatsColor(color: DraftColor): Color = when (color) {
    DraftColor.W -> Color(0xFFE6D99A)
    DraftColor.U -> Color(0xFF3988CC)
    DraftColor.B -> Color(0xFF65556F)
    DraftColor.R -> Color(0xFFD6533C)
    DraftColor.G -> Color(0xFF388A52)
}
