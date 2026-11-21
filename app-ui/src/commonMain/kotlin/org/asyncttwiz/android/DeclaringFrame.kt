package org.asyncttwiz.android

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
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
    /** The chip's words at the table's top. */
    val chip: String
    data object Attackers : CombatDeclaring { override val chip = "Declaring attackers: abilities can't be used now" }
    data object Blockers : CombatDeclaring { override val chip = "Declaring blockers: abilities can't be used now" }
}

/** CMB-095: the declaring chip's test tag. */
const val DECLARING_CHIP_TAG = "declaring-chip"

/** CMB-097 (owner 2026-11-21, E13): the test tag of the orange, glowing combat band while the viewer declares ([DeclaringBand]). */
const val DECLARING_BAND_TAG = "declaring-band"

object DeclaringLook {
    /** CMB-096: how opaque the cards that can't take part in the declaration are drawn (as the dimmed cards elsewhere). */
    const val DIMMED = .4f
    /** CMB-095: the band's and the chip's fade as declaring starts or ends. */
    const val FADE_MILLIS = GLOW_FADE_MILLIS
    /** CMB-095: where the chip's top is (dp) from the table's top edge: centred on the edge; on the desktop table below the event text's row. */
    const val CHIP_TOP = -12f
    const val DESKTOP_CHIP_TOP = 52f
    /** CMB-097 (owner 2026-11-21, E13): one wave of light's period: it crosses the band from left to right in the first
     * [WAVE_CROSSING] of it, then the band rests until the next. */
    const val WAVE_MILLIS = 2_400
    const val WAVE_CROSSING = .7f
    /** Where in its period the wave starts (so a still frame, as a screenshot's, shows it on the band). */
    const val WAVE_START = .3f
    /** The wave's soft band of light: its width (dp, at least; else 1.6 times the band's height) and brightness at its middle. */
    const val WAVE_WIDTH = 96f
    const val WAVE_ALPHA = .6f

    /**
     * CMB-097 (E13): where the wave's middle is across a band [width] wide whose light is [wave] wide, at [phase] (0–1) of its
     * period; null while the band rests between waves. It enters fully from beyond the left edge and leaves beyond the right.
     */
    fun waveX(phase: Float, width: Float, wave: Float): Float? {
        val t = ((phase + WAVE_START) % 1f) / WAVE_CROSSING
        if (t > 1f) return null
        return -wave / 2 + (width + wave) * t
    }

    /**
     * CMB-096 (owner playtest 2026-11-21): the cards on [viewer]'s battlefield of [board] that can't take part in the declaration:
     * every card not in [takingPart] (the creatures that may attack, or block, staged or placed ones too).
     */
    fun dimmed(board: BoardState, viewer: String, takingPart: Set<String>): Set<String> {
        val fields = board.containers.filter { it.zone == PlayZone.BATTLEFIELD && it.accountId == viewer }.mapTo(HashSet()) { it.id }
        return board.cards.filter { it.containerId in fields && it.id !in takingPart }.mapTo(HashSet()) { it.id }
    }
}

/** CMB-097 (owner 2026-11-21, E13): the combat band's orange while the viewer declares, warm and clearly not its cream. */
val DeclaringOrange = Color(0xffffa94d)

/**
 * CMB-097 (owner 2026-11-21, E13): while the viewer declares attackers or blockers ([level], 0–1, fading in as declaring starts
 * and out as it ends) the combat band between the battlefields of [scene] turns orange and glows: a soft band of light, as
 * draft Confirm's while Auto is on (DRF-294), crosses it from left to right again and again. Drawn under the table, whose band
 * lets it through while declaring (drawTable's declaring), so the combat copies, lanes and chips lie over it; drawn only.
 */
@Composable
fun DeclaringBand(level: State<Float>, scene: () -> TableScene?) {
    val on by remember(level) { derivedStateOf { level.value > 0f } }
    if (!on) return
    val phase by rememberInfiniteTransition(label = "Declaring waves").animateFloat(0f, 1f,
        infiniteRepeatable(tween(DeclaringLook.WAVE_MILLIS, easing = LinearEasing), RepeatMode.Restart), label = "Declaring wave")
    Canvas(Modifier.fillMaxSize().testTag(DECLARING_BAND_TAG)) {
        val b = scene()?.combatBandBounds() ?: return@Canvas
        val d = density
        val shown = level.value.coerceIn(0f, 1f)
        val left = b.x * d; val top = b.y * d; val width = b.width * d; val height = b.height * d
        if (width <= 0f || height <= 0f) return@Canvas
        clipRect(left, top, left + width, top + height) {
            drawRect(DeclaringOrange.copy(alpha = shown), Offset(left, top), Size(width, height))
            val wave = maxOf(height * 1.6f, DeclaringLook.WAVE_WIDTH * d)
            val x = DeclaringLook.waveX(phase, width, wave) ?: return@clipRect
            drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = DeclaringLook.WAVE_ALPHA * shown), Color.Transparent),
                start = Offset(left + x - wave / 2, top), end = Offset(left + x + wave / 2, top + height)), Offset(left, top), Size(width, height))
        }
    }
}

/**
 * CMB-095 (owner playtest 2026-11-21): while the viewer declares attackers or blockers ([declaring]) a chip at the top of the
 * table's visible area ([scene]'s world) says so, fading in as declaring starts and out as it ends (the band glows meanwhile,
 * [DeclaringBand]). Drawn only: taps reach the table.
 */
@Composable
fun DeclaringChip(scene: TableScene, declaring: CombatDeclaring?, level: State<Float>, desktop: DesktopGeometry? = null) {
    var last by remember { mutableStateOf(declaring) }
    LaunchedEffect(declaring) {
        if (declaring != null || last != null)
            AppLog.info("combat", if (declaring != null) "Declaring shown (chip, orange band): ${declaring.chip}" else "Declaring gone")
        if (declaring != null) last = declaring
    }
    val shown = declaring ?: last ?: return
    val on by remember(level) { derivedStateOf { level.value > 0f } }
    if (!on) return
    val world = scene.world?.visibleViewport ?: return
    // WEB-015: on the desktop table the battlefields end where the bottom band (your seat, hand and action area) begins
    val area = desktop?.let { g -> world.copy(height = (minOf(world.bottom, g.seatTop) - world.y).coerceAtLeast(0f)) } ?: world
    // On the desktop table the event text sits at the battlefields' top centre (WEB-020): the chip goes under it
    val chipTop = if (desktop != null) DeclaringLook.DESKTOP_CHIP_TOP else DeclaringLook.CHIP_TOP
    Box(Modifier.offset { IntOffset(area.x.dp.roundToPx(), area.y.dp.roundToPx()) }.size(area.width.dp, area.height.dp)
        .graphicsLayer { alpha = level.value }, contentAlignment = Alignment.TopCenter) {
        Text(shown.chip, Modifier.offset(y = chipTop.dp).padding(start = 8.dp, end = 8.dp).widthIn(max = 420.dp).height(24.dp)
            .background(AttackOrange, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 4.dp)
            .testTag(DECLARING_CHIP_TAG).clearAndSetSemantics { contentDescription = shown.chip },
            color = Color.White, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1,
            overflow = TextOverflow.Ellipsis)
    }
}
