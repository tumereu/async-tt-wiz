package org.asyncttwiz.android

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import org.asyncttwiz.protocol.*

/** Reusable table-action content. I20 owns placement in the actual Table action catalog. */
@Composable
internal fun ResultSuggestionControls(target: ResultTarget, enabled: Boolean,
    suggest: (ResultTarget, SuggestedResult) -> Unit) {
    Column {
        Text("Suggest a result")
        for ((outcome, label) in listOf(SuggestedResult.WIN to "My win", SuggestedResult.LOSS to "My loss", SuggestedResult.DRAW to "Draw")) {
            TextButton(onClick = { suggest(target, outcome) }, enabled = enabled) { Text(label) }
        }
        Text("The other player must accept before you both return to the lobby.")
    }
}

/** No receipt-driven auto-dialog. The host must provide I22's current playback gate. */
@Composable
internal fun ResultAcceptanceDialog(game: GameSummary, play: PlayState?, viewer: String,
    gate: ResultPlaybackGate?, enabled: Boolean, dismiss: () -> Unit,
    accept: (ResultAcceptanceTarget) -> Unit) {
    val model = ResultActions.presentation(game, play, viewer, gate) ?: return
    AlertDialog(onDismissRequest = dismiss, title = { Text("Proposed result") },
        text = { Text(model.text + if (model.canAccept) " Accept to return both players to the lobby." else " Waiting for the other player to accept.") },
        confirmButton = {
            if (model.canAccept) TextButton(onClick = { accept(model.target) }, enabled = enabled) { Text("Accept and return to lobby") }
        }, dismissButton = { TextButton(onClick = dismiss) { Text("Keep viewing board") } })
}
