package org.asyncttwiz.android

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.asyncttwiz.protocol.RandomTool

@Composable
private fun feedbackClock(feedback: TableEventFeedback, started: Long, expire: () -> Unit): State<Float> {
    val elapsed = remember { Animatable(feedback.elapsed(started).toFloat()) }
    LaunchedEffect(Unit) {
        val age = feedback.elapsed(started).coerceAtMost(TableEventFeedback.WINDOW_MS)
        elapsed.snapTo(age.toFloat())
        elapsed.animateTo(TableEventFeedback.WINDOW_MS.toFloat(),
            tween((TableEventFeedback.WINDOW_MS - age).toInt(), easing = LinearEasing))
        expire()
    }
    return elapsed.asState()
}

/** Screen-space, opaque player-coloured chips. All clocks are read only in draw/layer, not by
 * the board composition. Ghost rings have no semantics, pointer handlers, or ordinal badges. */
@Composable
internal fun TableEventFeedbackOverlay(feedback: TableEventFeedback?, viewer: String, firstPlayer: String,
    scene: TableScene, renderedScene: () -> TableScene, usernames: Map<String, String> = emptyMap()) {
    if (feedback == null) return
    val transient = feedback.rolls.values.sortedBy { it.serial }.map { active ->
        key("roll", active.serial) {
            active.result to feedbackClock(feedback, active.started) { feedback.expireRoll(active.serial) }
        }
    }
    val chips = if (feedback.isStopped) feedback.stoppedRolls.map { it to null } else transient
    for (field in scene.fields) {
        val results = chips.filter { it.first.author == field.owner }
            .takeLast((field.bounds.height / 56f).toInt().coerceAtLeast(1))
        if (results.isEmpty()) continue
        Column(Modifier.offset(field.bounds.x.dp, field.bounds.y.dp).size(field.bounds.width.dp, field.bounds.height.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)) {
            for ((result, elapsed) in results) key(result.nodeId, elapsed) {
                val fill = if (result.author == viewer) OwnTableColor else OpponentTableColor
                val ink = tableChangeTagInk(fill)
                Row(Modifier.graphicsLayer { alpha = elapsed?.let { TableEventFeedback.alpha(it.value) } ?: 1f }
                    .background(fill, RoundedCornerShape(12.dp))
                    .semantics { contentDescription = "${result.label}, by ${historyPlayerName(result.author, usernames)}" }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Canvas(Modifier.size(32.dp)) {
                        val stroke = Stroke(2.dp.toPx())
                        if (result.tool == RandomTool.COIN) {
                            drawCircle(ink, size.minDimension / 2 - stroke.width, style = stroke)
                            drawCircle(ink, size.minDimension / 2 - 6.dp.toPx(), style = stroke)
                        } else {
                            drawRoundRect(ink, Offset(stroke.width, stroke.width),
                                Size(size.width - 2 * stroke.width, size.height - 2 * stroke.width), CornerRadius(4.dp.toPx()), style = stroke)
                            for ((x, y) in listOf(.3f to .3f, .7f to .3f, .5f to .5f, .3f to .7f, .7f to .7f))
                                drawCircle(ink, 2.dp.toPx(), Offset(x * size.width, y * size.height))
                        }
                    }
                    Text(result.label, color = ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    val pulse = if (feedback.pings.isNotEmpty()) pingPulseProgress() else null
    for (active in feedback.pings.values) key("ping", active.serial) {
        val elapsed = feedbackClock(feedback, active.started) { feedback.expirePing(active.serial) }
        Canvas(Modifier.fillMaxSize()) {
            val point = TableAnnotationGeometry(renderedScene(), firstPlayer).endpoint(active.record.target) ?: return@Canvas
            val color = if (active.record.recording.author == viewer) OwnTableColor else OpponentTableColor
            val alpha = TableEventFeedback.alpha(elapsed.value)
            val phase = pulse?.value ?: 0f
            val center = Offset(point.x * density, point.y * density)
            drawCircle(color.copy(alpha = alpha * (1f - phase)), (10 + 18 * phase) * density, center, style = Stroke(2 * density))
            drawCircle(color.copy(alpha = alpha), 10 * density, center, style = Stroke(2 * density))
        }
    }
}
