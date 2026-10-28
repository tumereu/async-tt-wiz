package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
internal fun ResultTablePanel(model: ResultPresentation, controls: TableResultControls?, modifier: Modifier = Modifier) {
    Surface(modifier.widthIn(max = 320.dp), shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface, contentColor = MenuInk, border = BorderStroke(1.dp, MenuBorder),
        shadowElevation = 2.dp) {
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(model.text, style = MaterialTheme.typography.titleSmall)
            if (model.note.isNotEmpty()) Text("“${model.note}”", style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedIconButton(onClick = { controls?.reject?.invoke(model.target) }, enabled = controls?.enabled == true) {
                    Icon(if (model.canAccept) menuIcon("Cancel") else Icons.AutoMirrored.Outlined.Undo,
                        if (model.canAccept) "Decline result" else "Withdraw result")
                }
                if (model.canAccept) FilledIconButton(onClick = { controls?.accept?.invoke(model.target) }, enabled = controls?.enabled == true) {
                    Icon(menuIcon("Accept"), "Accept result")
                }
            }
        }
    }
}

data class TableResultControls(val enabled: Boolean, val reject: (ResultAcceptanceTarget) -> Unit,
    val accept: (ResultAcceptanceTarget) -> Unit)
