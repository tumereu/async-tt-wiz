package org.asyncttwiz.android

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.asyncttwiz.protocol.*

/** The normal table route. No local initialization, entropy or placeholder future action handlers. */
@Composable
internal fun TableScreen(controller: LobbyController, accountId: String, hamburger: (() -> Unit)?, landscape: Boolean) {
    val state = controller.state
    val game = state.detail ?: return
    val play = state.play
    Column(Modifier.fillMaxSize().background(Color(0xfff3f5f5))) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            if (hamburger != null) IconButton(onClick = hamburger) { EntryIcon("Open navigation", "menu") }
            Column(Modifier.weight(1f)) {
                Text(game.gameName, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(gameDate(game), style = MaterialTheme.typography.labelSmall)
            }
            if (play != null) {
                val turn = play.board.turn
                val player = if (turn.accountId == game.inviter.accountId) game.inviter else game.recipient
                Text("↻", Modifier.padding(12.dp).semantics {
                    contentDescription = "Turn ${turn.number}, ${player.username}, ${turn.phase.lowercase()} / ${turn.step.lowercase()}"
                }, color = if (turn.accountId == accountId) OwnTableColor else OpponentTableColor, style = MaterialTheme.typography.titleLarge)
                Text(turn.number, Modifier.padding(end = 12.dp))
            }
        }
        if (play == null) {
            Column(Modifier.weight(1f).padding(16.dp)) {
                Text("Loading accepted table…")
                state.connectionMessage?.let { Text(it) }
                TextButton(onClick = controller::reconnect) { Text("Reconnect") }
            }
        } else {
            TableSurface(game, play, accountId, landscape, Modifier.weight(1f))
            if (game.id !in state.connectedGames) Text("Disconnected · read-only", Modifier.padding(horizontal = 12.dp),
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

private val OwnTableColor = Color(0xff287f85)
private val OpponentTableColor = Color(0xffa84f58)

@Composable
internal fun TableSurface(game: GameSummary, play: PlayState, accountId: String, landscape: Boolean, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth().semantics { contentDescription = "Shared table" }) {
        val scene = remember(game, play, accountId, maxWidth, maxHeight, landscape) {
            TablePresentation.scene(game, play, accountId, maxWidth.value, maxHeight.value, landscape)
        }
        Canvas(Modifier.fillMaxSize()) {
            for (field in scene.fields) {
                val b = field.bounds
                val top = Offset(b.x.dp.toPx(), b.y.dp.toPx())
                val extent = Size(b.width.dp.toPx(), b.height.dp.toPx())
                drawRect(if (field.owner == accountId) Color(0xffe5eeee) else Color(0xfff0e4e6), top, extent)
                // Equal land/spell footprint bands; no placement rules or drawn card slots.
                drawRect(Color.Black.copy(alpha = .035f), top + Offset(0f, if (field.owner == accountId) extent.height * .67f else 0f),
                    Size(extent.width, extent.height * .33f))
            }
        }
        for (field in scene.fields) Box(Modifier.offset(field.bounds.x.dp, field.bounds.y.dp)
            .size(field.bounds.width.dp, field.bounds.height.dp).semantics {
                val name = if (field.owner == game.inviter.accountId) game.inviter.username else game.recipient.username
                contentDescription = "$name Battlefield, ${play.board.containers.single { it.accountId == field.owner && it.zone == PlayZone.BATTLEFIELD }.cards.size} cards"
            })
        for (item in scene.items) {
            val b = item.bounds
            val color = if (item.owner == null) Color(0xff59656a) else if (item.owner == accountId) OwnTableColor else OpponentTableColor
            Box(Modifier.offset(b.x.dp, b.y.dp).size(b.width.dp, b.height.dp).semantics { contentDescription = item.label },
                contentAlignment = Alignment.Center) {
                if (item.zone != PlayZone.HAND) Canvas(Modifier.fillMaxSize()) {
                    val filled = item.cards.isNotEmpty() || item.zone == null
                    drawRoundRect(if (filled) color.copy(alpha = .14f) else Color.White.copy(alpha = .5f), cornerRadius = CornerRadius(4.dp.toPx()))
                    drawRoundRect(color, cornerRadius = CornerRadius(4.dp.toPx()), style = Stroke(2.dp.toPx()))
                    if (item.zone == PlayZone.LIBRARY && filled) {
                        drawLine(color.copy(alpha = .35f), Offset(8.dp.toPx(), 12.dp.toPx()), Offset(size.width - 8.dp.toPx(), size.height - 18.dp.toPx()), 2.dp.toPx())
                    }
                }
                if (item.zone == PlayZone.HAND || item.zone == null) Text(item.value, color = color,
                    style = if (item.zone == null) MaterialTheme.typography.titleLarge else MaterialTheme.typography.labelSmall)
                else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(item.value, fontWeight = FontWeight.Bold, color = color)
                    Text(item.zone.name.lowercase().replaceFirstChar(Char::uppercase), style = MaterialTheme.typography.labelSmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
