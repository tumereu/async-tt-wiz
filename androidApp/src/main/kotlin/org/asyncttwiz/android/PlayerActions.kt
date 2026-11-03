package org.asyncttwiz.android

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

/**
 * The Life panel of [accountId] (TRN-030), opened by tapping their life square: their life, which only the rules
 * change (I-D52), and for the other player their presence line (TRN-043).
 */
@Composable
internal fun PlayerPanel(state: PlayState, accountId: String, viewer: String, close: () -> Unit) {
    val player = state.board.players.singleOrNull { it.accountId == accountId } ?: return
    val own = player.accountId == viewer
    InlinePopupPanel(close, title = "Life") {
        // The life row sits on the panel edge nearest the life square: own panels open above it, the opponent's below.
        if (!own) PlayerLifeRow(player)
        if (!own) LocalOpponentPresence.current?.let { PlayerStatusLabel(rememberPresenceLine(it), dot = rememberPresenceDot(it)?.color) }
        if (own) PlayerLifeRow(player)
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
