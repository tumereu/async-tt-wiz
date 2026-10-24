package org.asyncttwiz.android

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.asyncttwiz.protocol.*

/** Pure input intent. The menu captures targets, never the owning group; server re-derives that context. */
data class EventPingIntent(val reference: EventReference, val targets: List<PingTarget>, val add: Boolean) {
    fun actions(): List<PlayAction> = targets.distinct().map {
        if (add) PlayAction.Ping(reference, it) else PlayAction.Unping(reference, it)
    }
}
enum class EventInputMode { NONE, PING, SELECTION }

/** Display projection only. Never rewrite the recorded from/to tuples used by replay and Undo. */
internal fun numberedActivePings(annotations: EventAnnotationState, groupId: String): List<RecordedPingChange> =
    EventAnnotations.activePings(annotations, groupId).mapIndexed { index, record ->
        record.copy(to = record.to!!.copy(ordinal = (index + 1).toString()))
    }

@Composable
internal fun pingPulseProgress(): State<Float> = rememberInfiniteTransition(label = "Active ping").animateFloat(
    initialValue = 0f, targetValue = 1f,
    animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "Expanding ping ring")
data class EventPingMode(val mode: EventInputMode = EventInputMode.NONE, val contextId: String? = null) {
    fun visit(groupId: String?) = if (contextId == groupId) this else EventPingMode(contextId = groupId)
    fun enablePing() = copy(mode = EventInputMode.PING)
    fun enableSelection() = copy(mode = EventInputMode.SELECTION)
}

@Composable
fun EventPingAction(intent: EventPingIntent, connected: Boolean, onSubmit: (List<PlayAction>) -> Unit) {
    TextButton({ onSubmit(intent.actions()) }, enabled = connected && intent.targets.isNotEmpty()) {
        Text(if (intent.add) "Ping" else "Unping")
    }
}

/** The host supplies the current active-ping projection, never a frozen historical ordinal. */
@Composable
fun EventPingMarker(record: RecordedPingChange, targetDescription: String, targetPresent: Boolean,
    onReference: (EventReference) -> Unit, modifier: Modifier = Modifier, usernames: Map<String, String> = emptyMap(),
    color: Color = OwnTableColor) {
    val presence = record.to ?: return
    val pulse = pingPulseProgress()
    Row(modifier.semantics { contentDescription = "Ping ${presence.ordinal}, $targetDescription, by ${historyPlayerName(record.recording.author, usernames)}" },
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(color.copy(alpha = 1f - pulse.value), (10 + 13 * pulse.value).dp.toPx(), style = Stroke(2.dp.toPx()))
            }
            Text(presence.ordinal, Modifier.background(color, CircleShape).padding(5.dp), color = Color.White)
        }
        if (!targetPresent) TextButton({ onReference(record.reference.reference) }) { Text("Original context") }
    }
}
