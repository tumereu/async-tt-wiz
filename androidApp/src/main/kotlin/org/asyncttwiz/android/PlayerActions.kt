package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import org.asyncttwiz.protocol.*

@Composable
internal fun PlayerActions(state: PlayState, scope: ManualScope, actions: ManualActions, enabled: Boolean,
    names: Map<String, String>, viewer: String = scope.contextId, submit: (PlayAction) -> Boolean) {
    val player = state.board.players.single { it.accountId == scope.contextId }
    CompositionLocalProvider(LocalValueDragSide provides if (player.accountId == viewer) ValueDragSide.BOTTOM else ValueDragSide.TOP) {
    InlinePopupPanel(actions::close, title = "Life / counters") {
            val color = LocalInlinePopupColor.current
            val shape = RoundedCornerShape(3.dp)
            val cell = Modifier.shadow(3.dp, shape).border(1.dp, color.copy(alpha = .5f), shape)
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(48.dp), color = androidx.compose.ui.graphics.Color.White,
                    shape = shape, border = BorderStroke(1.dp, color.copy(alpha = .5f)), shadowElevation = 3.dp) {
                    Box(contentAlignment = Alignment.Center) { MenuIcon("Life", tint = color) }
                }
                ManualActionSquare("Set life", player.life, enabled,
                    modifier = cell,
                    dragValue = player.life, onDelta = { submit(PlayAction.AdjustLife(player.accountId, it.toString())) },
                    onClick = { actions.edit(ManualValue(ManualValueKind.LIFE), state, player.accountId) })
                for (delta in listOf(1, -1)) ManualActionSquare("${if (delta > 0) "Increase" else "Decrease"} life",
                    if (delta > 0) "+" else "−", enabled && ManualText.add(player.life, delta.toLong()) != null,
                    modifier = cell,
                    onClick = { submit(PlayAction.AdjustLife(player.accountId, delta.toString())) })
            }
            for (pair in CounterColor.entries.chunked(2)) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (counter in pair) Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    val name = counter.name.lowercase()
                    val value = player.counters[counter]
                    ManualActionSquare("Set $name counter, ${value?.takeIf { it.isNotEmpty() } ?: "no value"}",
                        ManualText.playerCounterText(value), enabled, fill = counterColor(counter), circle = true,
                        tooltipName = "Set $name counter", iconTint = counterOutline(counter), modifier = cell,
                        dragValue = value ?: "0",
                        onDelta = { submit(PlayAction.AdjustCounter(CounterTarget.PLAYER, player.accountId, counter, it.toString())) },
                        onClick = { actions.edit(ManualValue(ManualValueKind.COUNTER, counter), state, player.accountId) })
                    for (delta in listOf(1, -1)) ManualActionSquare("${if (delta > 0) "Increase" else "Decrease"} $name counter",
                        if (delta > 0) "+" else "−", enabled && ManualText.add(value ?: "0", delta.toLong()) != null,
                        modifier = cell,
                        onClick = { submit(PlayAction.AdjustCounter(CounterTarget.PLAYER, player.accountId, counter, delta.toString())) })
                }
            }
    }
    }
}
