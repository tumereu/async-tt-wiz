package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.asyncttwiz.protocol.*

@Composable
internal fun PlayerActions(state: PlayState, scope: ManualScope, actions: ManualActions, enabled: Boolean,
    names: Map<String, String>, viewer: String = scope.contextId, submit: (PlayAction) -> Boolean) {
    val player = state.board.players.single { it.accountId == scope.contextId }
    TableMenuPanel(actions::close, width = 400.dp, title = "Life / counters",
        titleDescription = "${names[player.accountId] ?: "Player"}, life / counters",
        titleColor = if (player.accountId == viewer) OwnTableColor else OpponentTableColor) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { MenuIcon("Life") }
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
}
