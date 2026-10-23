package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.asyncttwiz.protocol.*

/** Unfinished text is personal UI only. Preserve message whitespace; this is neither card Note nor I27 Chat. */
class EventCommentDraft(val reference: EventReference, val initialText: String = "") {
    var text by mutableStateOf(initialText)
    fun action(): PlayAction.EventComment? = PlayAction.EventComment(reference, text).takeIf(GameplayRules::valid)
}

@Composable
fun EventCommentComposer(draft: EventCommentDraft, connected: Boolean, onSubmit: (PlayAction.EventComment) -> Unit,
    onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(12.dp)) {
        Text("Event comment", style = MaterialTheme.typography.titleMedium)
        Text("Selected event", style = MaterialTheme.typography.labelSmall)
        OutlinedTextField(draft.text, { draft.text = it }, Modifier.fillMaxWidth(),
            label = { Text("Comment") }, minLines = 3, maxLines = 8)
        Row {
            TextButton(onCancel) { Text("Cancel") }
            Button({ draft.action()?.let(onSubmit) }, enabled = connected && draft.action() != null) { Text("Post comment") }
        }
    }
}

/** The host handles the route personally; reading never activates the referenced line or logs inspection. */
@Composable
fun EventCommentRecord(comment: RecordedEventComment, onReference: (EventReference) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(comment.text)
        TextButton({ onReference(comment.reference.reference) }) { Text("Referenced event") }
    }
}
