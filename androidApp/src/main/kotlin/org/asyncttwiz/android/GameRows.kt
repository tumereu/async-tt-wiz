package org.asyncttwiz.android

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
internal fun GameRow(game: GameSummary, accountId: String, modifier: Modifier) {
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
        game.podName?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis) }
        Text("vs ${game.opponent(accountId).username} · ${gameStatus(game)} · ${gameLastActivity(game)}",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Bare content for the shared menu panel; pod-screen cards keep their own presentation. */
@Composable
internal fun MenuPodRow(entry: QuickContinuePod, open: () -> Unit) {
    val pod = entry.pod
    val invite = entry.invitation
    Row(Modifier.fillMaxWidth().testTag("quick-pod-${entry.id}").clickable(onClick = open).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (pod?.members ?: listOfNotNull(invite?.creator)).take(3).forEach { Avatar(it) }
        }
        Column(Modifier.weight(1f)) {
            Text(pod?.name ?: requireNotNull(invite).name, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(pod?.let(::podStatus) ?: "Invitation", style = MaterialTheme.typography.bodySmall)
        }
        EntryIcon(null, "chevron")
    }
}

@Composable
internal fun MenuActivityPanel(content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.widthIn(max = 540.dp).fillMaxWidth().padding(horizontal = 12.dp),
        color = Color.White, shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp),
        border = BorderStroke(1.dp, ScreenBorder)) {
        Column(content = content)
    }
}
