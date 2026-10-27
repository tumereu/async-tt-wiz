package org.asyncttwiz.android

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.asyncttwiz.protocol.*
import kotlin.math.roundToInt

@Composable
fun PodStatsView(
    build: PodDeckBuild,
    copies: Map<String, DraftCopy>,
    definitions: Map<String, CardDefinition>,
    modifier: Modifier = Modifier,
) {
    val stats = remember(build, copies, definitions) { podStats(build, copies, definitions) }
    LazyColumn(modifier.fillMaxSize().testTag("pod-stats"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { PodStatsBars(stats.curveBuckets(), MaterialTheme.colorScheme.primary) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("Creature" to stats.creatures, "Noncreature" to stats.noncreatures, "Land" to stats.lands).forEach { (label, count) ->
                    Text("$label: $count", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DraftColor.entries.forEach { color ->
                    val (pips, sources) = stats.colorPercentages(color)
                    Row(Modifier.fillMaxWidth().semantics {
                        contentDescription = "${color.name}: pip demand ${pips.roundToInt()}%, mana sources ${sources.roundToInt()}%"
                    }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CardText("{${color.name}}", Modifier.width(28.dp), style = MaterialTheme.typography.titleLarge)
                        PodColorBar(pips, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                        PodColorBar(sources, MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
                    }
                }
            }
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
            if (value > 0) drawContext.canvas.nativeCanvas.drawText(podStatsNumber(value), slot * (index + 0.5f), baseline - height - textSize * 0.3f, paint)
            drawContext.canvas.nativeCanvas.drawText(label, slot * (index + 0.5f), size.height - textSize * 0.25f, paint)
        }
    }
}

@Composable
private fun PodColorBar(percent: Double, color: Color, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(Modifier.weight(1f).height(4.dp).testTag("pod-color-bar")) {
            drawRect(color.copy(alpha = .15f))
            drawRect(color, size = Size(size.width * (percent / 100).toFloat(), size.height))
        }
        Text("${percent.roundToInt()}%", Modifier.width(36.dp), textAlign = TextAlign.End, style = MaterialTheme.typography.labelSmall)
    }
}
