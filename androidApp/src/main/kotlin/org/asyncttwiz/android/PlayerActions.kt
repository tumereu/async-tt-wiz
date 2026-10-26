package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(48.dp), color = androidx.compose.ui.graphics.Color.White,
                    shape = shape, border = BorderStroke(1.dp, color.copy(alpha = .5f)), shadowElevation = 3.dp) {
                    Box(contentAlignment = Alignment.Center) { MenuIcon("Life", tint = color) }
                }
                fun adjust(delta: Int) = MenuControl("${if (delta > 0) "Increase" else "Decrease"} life",
                    enabled && ManualText.add(player.life, delta.toLong()) != null) {
                    submit(PlayAction.AdjustLife(player.accountId, delta.toString()))
                }
                ValueControl(player.life, MenuControl("Set life", enabled) {
                    actions.edit(ManualValue(ManualValueKind.LIFE), state, player.accountId)
                }, adjust(-1), adjust(1), dragValue = player.life,
                    drag = { submit(PlayAction.AdjustLife(player.accountId, it.toString())) })
            }
            for (pair in CounterColor.entries.chunked(2)) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (counter in pair) Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    val name = counter.name.lowercase()
                    val value = player.counters[counter]
                    fun adjust(delta: Int) = MenuControl("${if (delta > 0) "Increase" else "Decrease"} $name counter",
                        enabled && ManualText.add(value ?: "0", delta.toLong()) != null) {
                        submit(PlayAction.AdjustCounter(CounterTarget.PLAYER, player.accountId, counter, delta.toString()))
                    }
                    ValueControl(ManualText.playerCounterText(value), MenuControl("Set $name counter", enabled) {
                        actions.edit(ManualValue(ManualValueKind.COUNTER, counter), state, player.accountId)
                    }, adjust(-1), adjust(1), color = counterOutline(counter), fill = counterColor(counter),
                        dragValue = value ?: "0",
                        drag = { submit(PlayAction.AdjustCounter(CounterTarget.PLAYER, player.accountId, counter, it.toString())) })
                }
            }
    }
    }
}
