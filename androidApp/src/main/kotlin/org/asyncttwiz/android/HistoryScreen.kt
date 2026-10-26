package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Shown only when toggling history off away from the shared head. Dismiss keeps review open. */
@Composable internal fun HistoryContinueConfirmation(enabled: Boolean, dismiss: () -> Unit,
    confirm: () -> Unit, backToLatest: () -> Unit) {
    TableMenuPanel(dismiss, title = "Leave history?") {
        Text("Your next successful action will start a new path. The existing future will be kept.", Modifier.padding(vertical = 8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(backToLatest, Modifier.weight(1f).heightIn(min = 48.dp), shape = MaterialTheme.shapes.extraSmall,
                border = BorderStroke(1.dp, MenuBorder), colors = ButtonDefaults.outlinedButtonColors(contentColor = MenuInk)) {
                Text("Back to latest")
            }
            Button(confirm, Modifier.weight(1f).heightIn(min = 48.dp), enabled = enabled, shape = MaterialTheme.shapes.extraSmall,
                colors = ButtonDefaults.buttonColors(containerColor = OwnTableColor)) {
                Text("Continue from here")
            }
        }
    }
}
