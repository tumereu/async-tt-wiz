package org.asyncttwiz.android

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/** Personal per-game/per-player input, shared by the quick action and Library menu. */
internal class MulliganQuantityStore(context: Context, account: String, game: String) {
    private val preferences = context.applicationContext.getSharedPreferences("mulligan-quantities", Context.MODE_PRIVATE)
    private val key = "${account.length}:$account:$game"
    fun read() = preferences.getInt(key, 7).coerceAtLeast(0)
    fun write(value: Int) { preferences.edit().putInt(key, value.coerceAtLeast(0)).apply() }
}

@Composable
internal fun MulliganDialog(controller: LibraryController, handIds: List<String>, owner: String,
    dismiss: () -> Unit, complete: () -> Unit) {
    val context = LocalContext.current
    val game = controller.gameId ?: return
    val quantity = remember(owner, game) { MulliganQuantityStore(context, owner, game) }
    var input by remember(controller) { mutableStateOf(selectedLibraryQuantity(quantity.read().toString())) }
    val count = input.text.toIntOrNull()?.takeIf { it >= 0 }
    Dialog(onDismissRequest = dismiss) {
        TrackGameDialogInput()
        Surface(shape = MaterialTheme.shapes.medium) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val mulligan = {
                    if (count != null) {
                        val result = controller.mulligan(handIds, count)
                        if (result.refused == 0 && result.submitted > 0) { quantity.write(count); complete() }
                    }
                }
                val mayMulligan = count != null && controller.available()
                ValueInputControl("Mulligan quantity", input, { input = it }, controller.available(),
                    submitEnabled = mayMulligan, submit = mulligan)
                FilledIconButton(onClick = mulligan, enabled = mayMulligan) { Icon(menuIcon("Mulligan"), "Mulligan") }
            }
        }
    }
}
