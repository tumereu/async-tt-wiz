package org.asyncttwiz.android

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import org.asyncttwiz.protocol.*

/** Pure input intent. The menu captures targets, never the owning group; server re-derives that context. */
data class EventPingIntent(val reference: EventReference, val targets: List<PingTarget>, val add: Boolean) {
    fun actions(): List<PlayAction> = targets.distinct().map {
        if (add) PlayAction.Ping(reference, it) else PlayAction.Unping(reference, it)
    }
}
enum class EventInputMode { NONE, PING, SELECTION }
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

/** Uses only recorded identity/ordinal and a host-provided non-disclosing description, never card metadata. */
@Composable
fun EventPingMarker(record: RecordedPingChange, targetDescription: String, targetPresent: Boolean,
    onReference: (EventReference) -> Unit, modifier: Modifier = Modifier) {
    val presence = record.to ?: return
    Row(modifier.semantics { contentDescription = "Ping ${presence.ordinal}, $targetDescription, author ${record.recording.author}" }) {
        Text("◎${presence.ordinal}")
        if (!targetPresent) TextButton({ onReference(record.reference.reference) }) { Text("Original context") }
    }
}
