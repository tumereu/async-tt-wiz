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
fun GameRows(games: List<GameSummary>, controller: LobbyController, accountId: String,
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
                    .gameRowActions(controller, game, enabled = ready || incoming, desktop = desktopLayout()) { if (!incoming) controller.open(game.id) },
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
fun GameRow(game: GameSummary, accountId: String, modifier: Modifier,
    trailing: (@Composable () -> Unit)? = null, dot: WaitingDot? = null, dotSlot: Boolean = false) {
    val move = gameRowMove(game, accountId)
    val chip: @Composable () -> Unit = {
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
    val lines: @Composable () -> Unit = {
        game.podName?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis) }
        Text("vs ${game.opponent(accountId).username} · ${gameStatus(game, accountId)} · ${gameLastActivity(game)}",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (game.invitationHeld) 2 else 1, overflow = TextOverflow.Ellipsis)
        // LOB-252: an invitation whose creator lends you decks says so
        lendsDecksLine(game, accountId)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("lends-decks-${game.id}")) }
    }
    // WEB-038 (D7): on the desktop layout one line of columns: avatar, the name over its lines, the move chip, the chevron
    if (desktopLayout()) Row(modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Avatar(game.opponent(accountId))
        // LOB-269: the menu panel's rows have the dot's slot between the avatar and the name
        if (dotSlot) AfterDotSlot(dot, Arrangement.spacedBy(2.dp)) {
            RowName(game.gameName, fontWeight = FontWeight.SemiBold)
            lines()
        } else Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            RowName(game.gameName, fontWeight = FontWeight.SemiBold)
            lines()
        }
        chip()
    } else Column(modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Avatar(game.opponent(accountId))
            if (dotSlot) WaitingDotSlot(dot)
            RowName(game.gameName, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            chip()
        }
        lines()
    }
}

/** A row's name on one line, shortened with … when it doesn't fit. */
@Composable
fun RowName(name: String, modifier: Modifier = Modifier, fontWeight: FontWeight? = null) {
    Text(name, modifier, fontWeight = fontWeight, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/**
 * LOB-269 (owner gallery review 2026-11-25): the waiting dot's slot before a row's name, between its avatar column and its name:
 * [dot] (LOB-267) centred on the name's line, empty on a row without one but always there, so on every row the dot sits at
 * the same x and the names line up.
 */
@Composable
fun WaitingDotSlot(dot: WaitingDot?, modifier: Modifier = Modifier) {
    val line = LocalTextStyle.current.lineHeight
    val height = with(androidx.compose.ui.platform.LocalDensity.current) { if (line.isSp) line.toDp() else 24.dp }
    Box(modifier.testTag(WAITING_DOT_SLOT_TAG).width(WAITING_DOT_SLOT).height(height), contentAlignment = Alignment.Center) {
        dot?.let { WaitingDotMark(it) }
    }
}

/** LOB-269: a row's name and the lines under it ([content]) after its waiting dot's slot ([WaitingDotSlot]), taking the row's
 * free width. */
@Composable
fun RowScope.AfterDotSlot(dot: WaitingDot?, arrangement: Arrangement.Vertical = Arrangement.Top, content: @Composable ColumnScope.() -> Unit) {
    Row(Modifier.weight(1f), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WaitingDotSlot(dot)
        Column(Modifier.weight(1f), verticalArrangement = arrangement, content = content)
    }
}

/** The width of a row's waiting dot slot (LOB-269): the dot's own. */
val WAITING_DOT_SLOT = 6.dp
const val WAITING_DOT_SLOT_TAG = "waiting-dot-slot"

/** Bare content for the shared menu panel; pod-screen cards keep their own presentation. */
@Composable
fun MenuPodRow(entry: QuickContinuePod, accountId: String?, stackWidth: Dp, respond: (Boolean) -> Unit,
    responseEnabled: Boolean, open: () -> Unit) {
    val pod = entry.pod
    val invite = entry.invitation
    Row(Modifier.fillMaxWidth().testTag("quick-pod-${entry.id}").clickable(onClick = open).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // LOB-017: pods show the side menu's compact stack (LOB-018); an invitation keeps its creator's avatar.
        if (pod != null) AvatarStack(podNavigationAvatars(pod.members, accountId), ring = Color.White, width = stackWidth)
        else Box(Modifier.width(stackWidth)) { Avatar(requireNotNull(invite).creator) }
        // LOB-269: a pod's waiting dot, an invitation's blue (owner, 2026-11-25), in its slot before the name
        AfterDotSlot(if (pod != null) podWaitingDot(pod) else podInvitationWaitingDot) {
            RowName(pod?.name ?: requireNotNull(invite).name, fontWeight = FontWeight.SemiBold)
            Text(pod?.let(::podStatus) ?: "Invitation", style = MaterialTheme.typography.bodySmall)
        }
        if (invite != null) MenuInvitationActions(responseEnabled, respond = respond)
        else EntryIcon(null, "chevron")
    }
}

@Composable
fun MenuInvitationActions(enabled: Boolean, acceptEnabled: Boolean = true, respond: (Boolean) -> Unit) {
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
fun MenuActivityPanel(content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.widthIn(max = 540.dp).fillMaxWidth().padding(horizontal = 12.dp),
        color = Color.White, shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp),
        border = BorderStroke(1.dp, ScreenBorder)) {
        Column(content = content)
    }
}
