package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun ReconnectingDialog(backToGames: () -> Unit, dismiss: () -> Unit = backToGames) {
    ScreenDialog(dismiss) {
        Text("Reconnecting…", style = MaterialTheme.typography.titleMedium)
        Text("Game actions unavailable.", style = MaterialTheme.typography.bodySmall)
        ScreenButton(onClick = backToGames, Modifier.fillMaxWidth()) {
            EntryIcon(null, "games"); Spacer(Modifier.width(8.dp)); Text("Back to games list")
        }
    }
}
