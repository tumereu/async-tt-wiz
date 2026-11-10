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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.asyncttwiz.protocol.GameSummary

/** Games screen list, already in the shared order; an invitation to answer carries Accept/Decline like the Menu. */
@Composable
internal fun GameRows(games: List<GameSummary>, controller: LobbyController, accountId: String,
    modifier: Modifier = Modifier) {
    if (games.isEmpty()) return
    Surface(modifier, color = Color.White, shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, ScreenBorder)) {
        Column {
            games.forEachIndexed { index, game ->
                if (index > 0) HorizontalDivider(thickness = 1.dp, color = ScreenBorder)
                val incoming = incomingInvitation(game, accountId)
                val ready = cardRecordsReady()
                GameRow(game, accountId, Modifier.fillMaxWidth().testTag("quick-continue-${game.id}")
                    .gameRowActions(controller, game, enabled = ready || incoming) { if (!incoming) controller.open(game.id) },
                    trailing = if (incoming) ({
                        val state = controller.state
                        MenuInvitationActions(game.id in state.connectedGames && game.id !in state.pendingGames,
                            acceptEnabled = !game.invitationHeld && ready) { controller.respond(game, it) }
                    }) else null)
            }
        }
    }
}

@Composable
internal fun GameRow(game: GameSummary, accountId: String, modifier: Modifier,
    trailing: (@Composable () -> Unit)? = null) {
    val move = gameRowMove(game, accountId)
    Column(modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Avatar(game.opponent(accountId))
            Text(game.gameName, Modifier.weight(1f), fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (trailing != null) trailing() else {
                Surface(color = if (move.highlighted) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    contentColor = if (move.highlighted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    shape = RoundedCornerShape(8.dp)) {
                    Box(Modifier.width(100.dp).height(28.dp), contentAlignment = Alignment.Center) {
                        Text(move.label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                EntryIcon(null, "chevron")
            }
        }
        game.podName?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis) }
        Text("vs ${game.opponent(accountId).username} · ${gameStatus(game, accountId)} · ${gameLastActivity(game)}",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (game.invitationHeld) 2 else 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Bare content for the shared menu panel; pod-screen cards keep their own presentation. */
@Composable
internal fun MenuPodRow(entry: QuickContinuePod, accountId: String?, stackWidth: Dp, respond: (Boolean) -> Unit,
    responseEnabled: Boolean, open: () -> Unit) {
    val pod = entry.pod
    val invite = entry.invitation
    Row(Modifier.fillMaxWidth().testTag("quick-pod-${entry.id}").clickable(onClick = open).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // LOB-017: pods show the side menu's compact stack (LOB-018); an invitation keeps its creator's avatar.
        if (pod != null) AvatarStack(podNavigationAvatars(pod.members, accountId), ring = Color.White, width = stackWidth)
        else Box(Modifier.width(stackWidth)) { Avatar(requireNotNull(invite).creator) }
        Column(Modifier.weight(1f)) {
            Text(pod?.name ?: requireNotNull(invite).name, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(pod?.let(::podStatus) ?: "Invitation", style = MaterialTheme.typography.bodySmall)
        }
        if (invite != null) MenuInvitationActions(responseEnabled, respond = respond)
        else EntryIcon(null, "chevron")
    }
}

@Composable
internal fun MenuInvitationActions(enabled: Boolean, acceptEnabled: Boolean = true, respond: (Boolean) -> Unit) {
    Row {
        HoldTip("Accept invitation") {
            FilledTonalIconButton(onClick = { respond(true) }, enabled = enabled && acceptEnabled) { EntryIcon("Accept invitation", "ready") }
        }
        HoldTip("Decline invitation") {
            OutlinedIconButton(onClick = { respond(false) }, enabled = enabled,
                colors = IconButtonDefaults.outlinedIconButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                EntryIcon("Decline invitation", "close")
            }
        }
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
