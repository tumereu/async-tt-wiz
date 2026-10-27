package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import org.asyncttwiz.protocol.*

/** Reusable table-action content. I20 owns placement in the actual Table action catalog. */
@Composable
internal fun ResultSuggestionControls(target: ResultTarget, enabled: Boolean,
    suggest: (ResultTarget, SuggestedResult) -> Unit) {
    Column {
        Text("Suggest a result")
        for ((outcome, label) in listOf(SuggestedResult.WIN to "My win", SuggestedResult.LOSS to "My loss", SuggestedResult.DRAW to "Draw")) {
            Button(onClick = { suggest(target, outcome) }, enabled = enabled) { Text(label) }
        }
    }
}

@Composable
internal fun ResultAcceptanceDialog(model: ResultPresentation, enabled: Boolean,
    chat: () -> Unit, history: () -> Unit, reject: (ResultAcceptanceTarget) -> Unit,
    accept: (ResultAcceptanceTarget) -> Unit) {
    AlertDialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(model.text) },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalIconButton(onClick = chat, modifier = Modifier.semantics { contentDescription = "Chat" }) { TableChromeIcon("chat") }
                FilledTonalIconButton(onClick = history, modifier = Modifier.semantics { contentDescription = "History" }) { TableChromeIcon("history") }
            }
        },
        confirmButton = {
            if (model.canAccept) FilledIconButton(onClick = { accept(model.target) }, enabled = enabled) {
                Icon(menuIcon("Accept"), "Accept result")
            }
        }, dismissButton = {
            OutlinedButton(onClick = { reject(model.target) }, enabled = enabled) {
                Text(if (model.canAccept) "Decline" else "Withdraw")
            }
        })
}

@Composable
internal fun ResultPendingBanner(model: ResultPresentation, modifier: Modifier = Modifier, returnToTable: () -> Unit) {
    FilledTonalButton(onClick = returnToTable, modifier = modifier) { Text(model.text, maxLines = 1) }
}
