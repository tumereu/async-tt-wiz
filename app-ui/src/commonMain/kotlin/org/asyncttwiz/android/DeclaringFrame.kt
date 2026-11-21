package org.asyncttwiz.android

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.asyncttwiz.protocol.BoardState
import org.asyncttwiz.protocol.PlayZone

/** CMB-095 (owner playtest 2026-11-21): what the viewer declares now, while nobody holds priority (CMB-047). */
sealed interface CombatDeclaring {
    /** The chip's words over the framed table. */
    val chip: String
    data object Attackers : CombatDeclaring { override val chip = "Declaring attackers: abilities can't be used now" }
    data object Blockers : CombatDeclaring { override val chip = "Declaring blockers: abilities can't be used now" }
}

/** CMB-095: the declaring chip's test tag. */
const val DECLARING_CHIP_TAG = "declaring-chip"

object DeclaringLook {
    /** CMB-096: how opaque the cards that can't take part in the declaration are drawn (as the dimmed cards elsewhere). */
    const val DIMMED = .4f
    /** CMB-095: the frame's width (dp) and its fade as declaring starts or ends. */
    const val FRAME_WIDTH = 3f
    const val FADE_MILLIS = GLOW_FADE_MILLIS
    /** CMB-095: where the chip's top is (dp) from the frame's top edge: centred on the edge; on the desktop table below the event text's row. */
    const val CHIP_TOP = -12f
    const val DESKTOP_CHIP_TOP = 52f

    /**
     * CMB-096 (owner playtest 2026-11-21): the cards on [viewer]'s battlefield of [board] that can't take part in the declaration:
     * every card not in [takingPart] (the creatures that may attack, or block, staged or placed ones too).
     */
    fun dimmed(board: BoardState, viewer: String, takingPart: Set<String>): Set<String> {
        val fields = board.containers.filter { it.zone == PlayZone.BATTLEFIELD && it.accountId == viewer }.mapTo(HashSet()) { it.id }
        return board.cards.filter { it.containerId in fields && it.id !in takingPart }.mapTo(HashSet()) { it.id }
    }
}

/**
 * CMB-095 (owner playtest 2026-11-21): while the viewer declares attackers or blockers ([declaring]), the table's visible
 * area ([scene]'s world) is framed in the combat's red-orange with a chip at its top saying so, fading in as declaring starts
 * and out as it ends. Drawn only: taps reach the table.
 */
@Composable
fun DeclaringFrame(scene: TableScene, declaring: CombatDeclaring?, desktop: DesktopGeometry? = null) {
    var last by remember { mutableStateOf(declaring) }
    LaunchedEffect(declaring) {
        if (declaring != null || last != null)
            AppLog.info("combat", if (declaring != null) "Declaring frame shown: ${declaring.chip}" else "Declaring frame gone")
        if (declaring != null) last = declaring
    }
    val level by animateFloatAsState(if (declaring != null) 1f else 0f, tween(DeclaringLook.FADE_MILLIS), label = "Declaring frame")
    val shown = declaring ?: last ?: return
    if (level <= 0f) return
    val world = scene.world?.visibleViewport ?: return
    // WEB-015: on the desktop table the battlefields end where the bottom band (your seat, hand and action area) begins
    val area = desktop?.let { g -> world.copy(height = (minOf(world.bottom, g.seatTop) - world.y).coerceAtLeast(0f)) } ?: world
    // On the desktop table the event text sits at the battlefields' top centre (WEB-020): the chip goes under it
    val chipTop = if (desktop != null) DeclaringLook.DESKTOP_CHIP_TOP else DeclaringLook.CHIP_TOP
    Canvas(Modifier.fillMaxSize()) {
        val d = density
        val w = DeclaringLook.FRAME_WIDTH * d
        drawRoundRect(AttackOrange.copy(alpha = level), Offset(area.x * d + w / 2, area.y * d + w / 2),
            Size(area.width * d - w, area.height * d - w), CornerRadius(4 * d), style = Stroke(w))
    }
    Box(Modifier.offset { IntOffset(area.x.dp.roundToPx(), area.y.dp.roundToPx()) }.size(area.width.dp, area.height.dp)
        .alpha(level), contentAlignment = Alignment.TopCenter) {
        Text(shown.chip, Modifier.offset(y = chipTop.dp).padding(start = 8.dp, end = 8.dp).widthIn(max = 420.dp).height(24.dp)
            .background(AttackOrange, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 4.dp)
            .testTag(DECLARING_CHIP_TAG).clearAndSetSemantics { contentDescription = shown.chip },
            color = Color.White, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1,
            overflow = TextOverflow.Ellipsis)
    }
}
