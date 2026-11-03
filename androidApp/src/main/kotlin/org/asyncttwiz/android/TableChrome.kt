package org.asyncttwiz.android

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.asyncttwiz.protocol.*

/** Compact game chrome shares the surface, so landscape hands occupy the same top/bottom rails. */
@Composable
internal fun BoxWithConstraintsScope.TableChromeHeader(game: GameSummary, play: PlayState, viewer: String, landscape: Boolean,
    runtime: TableRuntimeHooks, toolkit: TableToolkitController?, compact: Boolean, appearance: CardAppearance) {
    val turnColor = if (play.board.turn.accountId == viewer) OwnTableColor else OpponentTableColor
    val attention = attentionPresentation(game, play, viewer, runtime.reviewing, toolkit?.enabled() == true)
    val spacing = if (compact) 0.dp else 4.dp
    // Landscape shares this rail with the fixed opponent Hand (sidebar + 10dp).
    val titleWidth = if (landscape)
        (TablePresentation.sidebarWidth(maxWidth.value, appearance) + 10f - 48f - spacing.value).coerceAtLeast(0f).dp
        else Dp.Infinity
    Row(Modifier.align(Alignment.TopStart).height(48.dp).fillMaxWidth()
        .padding(end = if (compact) 0.dp else 8.dp),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically) {
        ActionSquare("Open navigation", "", runtime.navigation != null, Modifier.width(48.dp), icon = { TableChromeIcon("menu") }, onClick = { runtime.navigation?.invoke() })
        Box(Modifier.weight(1f)) {
            // Keep the weighted space for right-aligned controls, but not for title/chip hit targets.
            Column(Modifier.widthIn(max = titleWidth).fillMaxWidth().padding(horizontal = 4.dp)) {
                // Only the name ellipsizes, so the turn number and "Waiting" survive narrow headers.
                val header = turnHeader(game, play.board.turn, viewer)
                val suffix = header.substring(header.indexOf("'s turn").takeIf { it >= 0 } ?: header.indexOf(" · "))
                Row(Modifier.semantics(mergeDescendants = true) { contentDescription = game.gameName }) {
                    Text(header.removeSuffix(suffix), Modifier.weight(1f, fill = false), color = turnColor, fontSize = 11.sp,
                        lineHeight = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(suffix, color = turnColor, fontSize = 11.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
                Spacer(Modifier.height(2.dp))
                ActionSquare(attention.label, "", attention.enabled,
                    Modifier.width(IntrinsicSize.Max).height(26.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(13.dp))
                        .then(if (attention.own) Modifier else Modifier.border(1.dp, Color(0xffa8afaf), androidx.compose.foundation.shape.RoundedCornerShape(13.dp))),
                    fill = if (attention.own) OwnTableColor else Color.White, ink = if (attention.own) Color.White else OpponentTableColor,
                    compactContent = true,
                    icon = {
                        Text(attention.label, Modifier.padding(horizontal = 6.dp), fontSize = 11.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, fontWeight = if (attention.own) FontWeight.Bold else FontWeight.Normal)
                    }, onClick = { if (!runtime.reviewing) toolkit?.submit(PlayAction.PassAttention) })
            }
        }
        val buttonWidth = 48f
        val turnPlayer = if (play.board.turn.accountId == game.inviter.accountId) game.inviter else game.recipient
        ActionSquare("Turn ${play.board.turn.number}, ${turnPlayer.username}'s turn, ${turnStepName(play.board.turn)}. Choose phase/step",
            "", toolkit != null && !runtime.reviewing && !runtime.gameActionsBlocked,
             Modifier.width(buttonWidth.dp).tableChangeFlash(runtime.changes, TableChangeKey("turn", TableChangeKind.STEP)),
             fill = phaseColor(play.board.turn.phase), ink = Color(0xff25333b),
            icon = { TurnStepIcon(play.board.turn) },
            onClick = { toolkit?.phasePicker() })
        if (runtime.continuationArmed) {
            ActionSquare("Cancel continuation", "", runtime.cancelContinuation != null,
                Modifier.size(48.dp).border(2.dp, OwnTableColor), fill = Color(0xffedf3ef), ink = OwnTableColor,
                icon = { TableChromeIcon("fork") }, onClick = { runtime.cancelContinuation?.invoke() })
        } else if (runtime.reviewing) {
            ActionSquare("Return to current", "", runtime.returnCurrent != null, Modifier.width(buttonWidth.dp), icon = { TableChromeIcon("current") }, onClick = { runtime.returnCurrent?.invoke() })
        }
        Box {
            ActionSquare("Chat, ${runtime.chatUnread} unread messages", "", runtime.chat != null, Modifier.width(buttonWidth.dp), icon = { TableChromeIcon("chat") }, onClick = { runtime.chat?.invoke() })
            if (runtime.chatUnread > 0) Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(6.dp).background(OpponentTableColor, androidx.compose.foundation.shape.CircleShape))
        }
        if (!runtime.reviewing) ActionSquare("Table actions", "", toolkit != null && !runtime.gameActionsBlocked,
            Modifier.width(buttonWidth.dp), icon = { TableChromeIcon("grid") },
            onClick = { toolkit?.tableMenu() })
    }
}

/** Small native vectors, not font-dependent ligatures or emoji. */
@Composable
internal fun TableChromeIcon(kind: String) {
    val color = LocalContentColor.current
    if (kind == "select") {
        Icon(MultipleSelectionIcon, null, Modifier.size(24.dp).clearAndSetSemantics {}, tint = color)
        return
    }
    Canvas(Modifier.size(24.dp).clearAndSetSemantics {}) {
        val u = size.width / 24f
        fun line(x: Float, y: Float, a: Float, b: Float) = drawLine(color, Offset(x * u, y * u), Offset(a * u, b * u), 1.7f * u)
        fun box(x: Float, y: Float, w: Float, h: Float) = drawRect(color, Offset(x * u, y * u), Size(w * u, h * u), style = Stroke(1.6f * u))
        when (kind) {
            "menu" -> for (y in listOf(5f, 12f, 19f)) line(4f, y, 20f, y)
            "chat" -> { box(3f, 3f, 17f, 14f); line(3f, 17f, 3f, 22f); line(3f, 22f, 8f, 17f) }
            "tree" -> { box(2f, 2f, 5f, 5f); box(17f, 3f, 5f, 5f); box(17f, 17f, 5f, 5f); line(7f, 5f, 12f, 5f); line(12f, 5f, 12f, 20f); line(12f, 20f, 17f, 20f); line(12f, 6f, 17f, 6f) }
            "fork" -> { line(6f, 21f, 6f, 4f); line(6f, 11f, 18f, 4f); line(12f, 4f, 18f, 4f); line(18f, 4f, 18f, 10f) }
            "history" -> { drawArc(color, -150f, 305f, false, Offset(4f * u, 4f * u), Size(16f * u, 16f * u), style = Stroke(1.7f * u)); line(3f, 3f, 3f, 9f); line(3f, 9f, 9f, 9f); line(12f, 7f, 12f, 12f); line(12f, 12f, 16f, 15f) }
            "overflow" -> for (y in listOf(5f, 12f, 19f)) drawCircle(color, 1.6f * u, Offset(12f * u, y * u))
            "undo" -> { line(10f, 4f, 4f, 9f); line(4f, 9f, 10f, 13f); line(4f, 9f, 15f, 9f); drawArc(color, -90f, 240f, false, Offset(10f * u, 9f * u), Size(10f * u, 10f * u), style = Stroke(1.7f * u)) }
            "redo" -> { line(14f, 4f, 20f, 9f); line(20f, 9f, 14f, 13f); line(20f, 9f, 9f, 9f); drawArc(color, -90f, -240f, false, Offset(4f * u, 9f * u), Size(10f * u, 10f * u), style = Stroke(1.7f * u)) }
            "close" -> { line(5f, 5f, 19f, 19f); line(5f, 19f, 19f, 5f) }
            "grid" -> for (x in listOf(5f, 12f, 19f)) for (y in listOf(5f, 12f, 19f)) drawCircle(color, 1.6f * u, Offset(x * u, y * u))
            "card" -> box(5f, 2f, 14f, 20f)
            "stack" -> { box(3f, 2f, 13f, 17f); line(7f, 21f, 20f, 21f); line(20f, 21f, 20f, 6f) }
            "previous" -> { line(18f, 5f, 18f, 19f); line(18f, 5f, 7f, 12f); line(18f, 19f, 7f, 12f); line(5f, 5f, 5f, 19f) }
            "next" -> { line(6f, 5f, 6f, 19f); line(6f, 5f, 17f, 12f); line(6f, 19f, 17f, 12f); line(19f, 5f, 19f, 19f) }
            "current" -> { box(2f, 3f, 20f, 15f); line(8f, 21f, 16f, 21f); line(10f, 7f, 16f, 11f); line(16f, 11f, 10f, 15f); line(10f, 15f, 10f, 7f) }
            "ping" -> { drawCircle(color, 7f * u, center, style = Stroke(1.7f * u)); drawCircle(color, 2f * u, center) }
            else -> { drawArc(color, 180f, 180f, false, Offset(4f * u, 2f * u), Size(9f * u, 10f * u), style = Stroke(1.7f * u)); line(8f, 7f, 8f, 17f); line(8f, 17f, 14f, 22f); line(14f, 22f, 19f, 18f); line(19f, 18f, 19f, 13f); line(19f, 13f, 12f, 12f); line(12f, 12f, 12f, 6f); line(20f, 22f, 22f, 24f); line(22f, 24f, 24f, 22f) }
        }
    }
}
