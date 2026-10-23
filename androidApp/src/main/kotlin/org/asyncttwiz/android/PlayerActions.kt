package org.asyncttwiz.android

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.asyncttwiz.protocol.*

@Composable
internal fun PlayerActions(state: PlayState, scope: ManualScope, actions: ManualActions, enabled: Boolean,
    names: Map<String, String>, submit: (PlayAction) -> Boolean) {
    val player = state.board.players.single { it.accountId == scope.contextId }
    AlertDialog(onDismissRequest = actions::close, title = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(names[player.accountId] ?: player.accountId, Modifier.weight(1f))
            IconButton(onClick = actions::close) { EntryIcon("Close player actions", "close") }
        }
    }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                ManualActionSquare("Set life", player.life, enabled,
                    onClick = { actions.edit(ManualValue(ManualValueKind.LIFE), state, player.accountId) })
                for (delta in listOf(1, -1)) ManualActionSquare("${if (delta > 0) "Increase" else "Decrease"} life",
                    if (delta > 0) "+" else "−", enabled && ManualText.add(player.life, delta.toLong()) != null,
                    onClick = { submit(PlayAction.AdjustLife(player.accountId, delta.toString())) })
            }
            CounterControls(player.counters, player = true, enabled = enabled,
                set = { actions.edit(ManualValue(ManualValueKind.COUNTER, it), state, player.accountId) },
                applicable = { color, delta -> ManualText.add(player.counters[color] ?: "0", delta.toLong()) != null },
                adjust = { color, delta -> submit(PlayAction.AdjustCounter(CounterTarget.PLAYER, player.accountId, color, delta.toString())) })
        }
    }, confirmButton = {})
}
