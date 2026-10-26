package org.asyncttwiz.android

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.asyncttwiz.protocol.GameSummary

@Composable
internal fun GameRows(games: List<GameSummary>, controller: LobbyController, accountId: String,
    modifier: Modifier = Modifier) {
    if (games.isEmpty()) return
    Surface(modifier, color = Color.White, shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, ScreenBorder)) {
        Column {
            games.forEachIndexed { index, game ->
                if (index > 0) HorizontalDivider(thickness = 1.dp, color = ScreenBorder)
                GameRow(game, accountId, Modifier.fillMaxWidth().testTag("quick-continue-${game.id}")
                    .gameRowActions(controller, game) { controller.open(game.id) })
            }
        }
    }
}

@Composable
private fun GameRow(game: GameSummary, accountId: String, modifier: Modifier) {
    val move = gameRowMove(game, accountId)
    Column(modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Avatar(game.opponent(accountId))
            Text(game.gameName, Modifier.weight(1f), fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Surface(color = if (move.highlighted) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                contentColor = if (move.highlighted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                shape = RoundedCornerShape(8.dp)) {
                Box(Modifier.width(100.dp).height(28.dp), contentAlignment = Alignment.Center) {
                    Text(move.label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            EntryIcon(null, "chevron")
        }
        Text("vs ${game.opponent(accountId).username} · ${gameStatus(game)} · ${gameLastActivity(game)}",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
