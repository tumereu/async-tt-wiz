package org.asyncttwiz.android

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.asyncttwiz.protocol.*

/** LOB-139, TRN-030: what your own Life panel's quick messages do ([send], a plain chat message) and its Write button
 * ([write], opening the chat); the chips are greyed unless [enabled] (a message could go now). */
data class TableQuickChat(val enabled: Boolean, val send: (String) -> Unit, val write: () -> Unit)

internal val LocalTableQuickChat = compositionLocalOf<TableQuickChat?> { null }

/**
 * The Life panel of [accountId] (TRN-030), opened by tapping their life square: their life, which only the rules
 * change (I-D52), and for the other player their presence line (TRN-043); your own has the quick messages and Write
 * (LOB-139).
 */
@Composable
internal fun PlayerPanel(state: TableBoard, accountId: String, viewer: String, close: () -> Unit) {
    val player = state.board.players.singleOrNull { it.accountId == accountId } ?: return
    val own = player.accountId == viewer
    val quick = LocalTableQuickChat.current
    InlinePopupPanel(close, title = "Life") {
        // The life row sits on the panel edge nearest the life square: own panels open above it, the opponent's below.
        if (!own) PlayerLifeRow(player)
        if (!own) LocalOpponentPresence.current?.let { PlayerStatusLabel(rememberPresenceLine(it), dot = rememberPresenceDot(it)?.color) }
        if (own && quick != null) QuickMessages(quick, close)
        if (own) PlayerLifeRow(player)
    }
}

/** LOB-139: the six quick messages as chips, each sent with one tap (closing the panel), and the icon-only Write. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickMessages(quick: TableQuickChat, close: () -> Unit) {
    val color = LocalInlinePopupColor.current
    FlowRow(Modifier.widthIn(max = 320.dp).semantics { contentDescription = "Quick messages" },
        horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (text in QuickChatMessages) RingPress(MenuControl(text, quick.enabled) { quick.send(text); close() },
            Modifier.heightIn(min = 40.dp), color) {
            Text(text, Modifier.padding(horizontal = 10.dp), color = MenuInk.copy(alpha = if (quick.enabled) 1f else .38f),
                style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
        // SYS-045: icon only, holding it names it
        RingPress(MenuControl("Write a message") { close(); quick.write() }, Modifier.size(40.dp), color) {
            Icon(Icons.Outlined.Edit, null, tint = color)
        }
    }
}

/** The life value with its heart; read only. */
@Composable
private fun PlayerLifeRow(player: PlayPlayer) {
    val color = LocalInlinePopupColor.current
    val shape = RoundedCornerShape(3.dp)
    Row(Modifier.inlinePopupFocus().semantics(mergeDescendants = true) { contentDescription = "Life ${player.life}" },
        horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(Modifier.size(48.dp), color = Color.White,
            shape = shape, border = BorderStroke(1.dp, color.copy(alpha = .5f)), shadowElevation = 3.dp) {
            Box(Modifier.clearAndSetSemantics {}, contentAlignment = Alignment.Center) { MenuIcon("Life", tint = color) }
        }
        Surface(Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp), color = Color.White,
            shape = shape, border = BorderStroke(1.dp, color.copy(alpha = .5f)), shadowElevation = 3.dp) {
            Box(Modifier.padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                Text(player.life, color = MenuInk, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

/** Popups float over the table without a sheet, so plain text needs its own backing to stay readable. */
@Composable
private fun PlayerStatusLabel(text: String, dot: Color? = null) {
    Surface(shape = RoundedCornerShape(4.dp), color = Color.White, border = BorderStroke(1.dp, LocalInlinePopupColor.current.copy(alpha = .4f))) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            // The same dot as on the life square, so the line explains what its colour means.
            if (dot != null) Box(Modifier.padding(end = 6.dp).size(8.dp).background(dot, CircleShape))
            Text(text, style = MaterialTheme.typography.labelMedium, color = MenuInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
