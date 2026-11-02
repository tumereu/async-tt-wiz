package org.asyncttwiz.android

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.asyncttwiz.automatum.ComputedBoard
import org.asyncttwiz.protocol.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The Automatum violet (emblem chips use it too) and the cyan its sigil fades into (integration spec §5). */
internal val AutomatumViolet = EmblemChipColor
internal val AutomatumCyan = Color(0xff1fb8cf)

/**
 * The Automatum switch in the top bar (integration spec §5, I-D4): a round sigil. Off: a dim outlined rune at about
 * 40% opacity. On: the rune on a violet-to-cyan disc with a soft glow breathing over about 3 s and one small spark
 * orbiting it. Tap switches Automatum for the whole game; hold opens its panel.
 */
@Composable
internal fun AutomatumSwitch(on: Boolean, enabled: Boolean, onToggle: () -> Unit, onHold: () -> Unit) {
    ActionSquare(if (on) "Automatum on. Turn Automatum off" else "Automatum off. Turn Automatum on", "", enabled,
        Modifier.width(48.dp), holdName = "Automatum panel", onHold = onHold,
        icon = { AutomatumSigil(on, Modifier.size(34.dp)) }, onClick = onToggle)
}

@Composable
internal fun AutomatumSigil(on: Boolean, modifier: Modifier = Modifier) {
    // Infinite transitions don't run under the Compose test clock: screenshots show the resting phase.
    val transition = rememberInfiniteTransition(label = "Automatum")
    val breath by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Reverse),
        label = "Automatum glow")
    val orbit by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(4000, easing = LinearEasing)), label = "Automatum spark")
    Canvas(modifier.clearAndSetSemantics {}) {
        val r = size.minDimension * .34f
        if (on) {
            // Soft outer glow, breathing
            val glow = r * (1.32f + .1f * breath)
            drawCircle(Brush.radialGradient(listOf(AutomatumViolet.copy(alpha = .30f + .25f * breath), Color.Transparent),
                center, glow), glow)
            drawCircle(Brush.linearGradient(listOf(AutomatumViolet, AutomatumCyan), Offset(center.x - r, center.y - r),
                Offset(center.x + r, center.y + r)), r)
            rune(Color.White, r)
            val angle = (orbit * 2 * PI - PI / 2).toFloat()
            val spark = Offset(center.x + cos(angle) * r * 1.22f, center.y + sin(angle) * r * 1.22f)
            drawCircle(AutomatumCyan.copy(alpha = .45f), r * .16f, spark)
            drawCircle(Color.White, r * .08f, spark)
        } else {
            val dim = Color(0xff4b5a5f).copy(alpha = .4f)
            drawCircle(dim, r, style = Stroke(r * .1f))
            rune(dim, r)
        }
    }
}

/** A forked rune: a stave with two branches up and two roots down. */
private fun DrawScope.rune(color: Color, r: Float) {
    val w = r * .13f
    fun line(x0: Float, y0: Float, x1: Float, y1: Float) = drawLine(color, Offset(center.x + x0 * r, center.y + y0 * r),
        Offset(center.x + x1 * r, center.y + y1 * r), w, StrokeCap.Round)
    line(0f, -.62f, 0f, .62f)
    line(0f, -.05f, -.42f, -.48f); line(0f, -.05f, .42f, -.48f)
    line(0f, .3f, -.3f, .62f); line(0f, .3f, .3f, .62f)
}

/**
 * The switch's hold panel (integration spec §5): the status ("Automatum on · 2 abilities are manual", and why the
 * board isn't computed when it can't be taken over), the viewer's own "Skip when I have no choice" (I-D18), "Clear
 * Automatum data" (I-D24, confirmed) and a read-only view of the engine's records (P2).
 */
@Composable
internal fun AutomatumPanel(play: PlayState, viewer: String, computed: ComputedBoard, enabled: Boolean,
    dismiss: () -> Unit, submit: (PlayAction) -> Boolean) {
    val board = play.board
    var confirmClear by remember { mutableStateOf(false) }
    // A sheet like Manage Stack: text and controls in one panel; the whole panel takes its own touches.
    InlinePopupPanel(dismiss, "Automatum", width = 320.dp, height = 420.dp, sheet = true) {
        Column(Modifier.fillMaxWidth().ringInputTarget(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(automatumStatus(board.automatum, computed), Modifier.semantics { contentDescription = "Automatum status" },
            fontWeight = FontWeight.Bold, color = AutomatumViolet)
        if (board.automatum && computed.unsupported.isNotEmpty()) {
            Text("Shown by hand while the board holds:", fontSize = 12.sp)
            computed.unsupported.forEach { Text("• $it", fontSize = 12.sp) }
        }
        if (computed.manualCards.isNotEmpty()) Text("Manual: " + computed.manualCards.joinToString(", ") { (name, count) ->
            if (count == 1) name else "$name ($count)" }, fontSize = 12.sp)
        val own = board.players.singleOrNull { it.accountId == viewer }
        if (own != null) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Skip when I have no choice", Modifier.weight(1f))
            Switch(own.skipWhenNoChoice, { submit(PlayAction.Mtg(MtgAction.SetSkipWhenNoChoice(it))) }, enabled = enabled,
                modifier = Modifier.semantics { contentDescription = "Skip when I have no choice" })
        }
        OutlinedButton({ confirmClear = true }, enabled = enabled && automatumDataPresent(board)) { Text("Clear Automatum data") }
        HorizontalDivider()
        Text("Engine data", fontWeight = FontWeight.Bold, fontSize = 12.sp)
        val lines = automatumDebugLines(board)
        if (lines.isEmpty()) Text("Nothing recorded", fontSize = 11.sp)
        Box(Modifier.horizontalScroll(rememberScrollState())) {
            Column { lines.forEach { Text(it, fontSize = 10.sp, fontFamily = FontFamily.Monospace, lineHeight = 12.sp) } }
        }
        }
    }
    if (confirmClear) AlertDialog({ confirmClear = false }, title = { Text("Clear Automatum data?") },
        text = { Text("Removes every effect, ledger and record Automatum keeps in this game. Cards stay where they are.") },
        confirmButton = { TextButton({ confirmClear = false; submit(PlayAction.Mtg(MtgAction.ClearRecords)) }) { Text("Clear") } },
        dismissButton = { TextButton({ confirmClear = false }) { Text("Cancel") } })
}

/** "Automatum on · 2 abilities are manual", "Automatum off". */
internal fun automatumStatus(on: Boolean, computed: ComputedBoard): String {
    if (!on) return "Automatum off"
    val manual = computed.manualAbilities
    return "Automatum on · " + when (manual) {
        0 -> "no abilities are manual"
        1 -> "1 ability is manual"
        else -> "$manual abilities are manual"
    }
}

private fun automatumDataPresent(board: BoardState) = board.engineRecords.isNotEmpty() ||
    board.cards.any { it.ledger.isNotEmpty() || it.typedCounters.isNotEmpty() } ||
    board.players.any { it.ledger.isNotEmpty() || it.typedCounters.isNotEmpty() || it.manaPool.isNotEmpty() } || board.priority != null

/** The engine's records and ledgers as stored (P2): one line each, data cut at 160 characters. */
internal fun automatumDebugLines(board: BoardState): List<String> {
    fun cut(data: String) = if (data.length > 160) data.take(160) + "…" else data
    return buildList {
        board.priority?.let { add("priority ${it.holder ?: "—"} passes ${it.passes}") }
        board.engineRecords.forEach { add("${it.id} [${it.kind::class.simpleName}]${it.timestamp?.let { t -> " @$t" }.orEmpty()} ${cut(it.data)}") }
        board.players.forEach { p ->
            if (p.manaPool.isNotEmpty()) add("pool ${p.accountId.take(8)} ${p.manaPool.joinToString("") { it.color }}")
            if (p.typedCounters.isNotEmpty()) add("counters ${p.accountId.take(8)} ${p.typedCounters.joinToString { "${it.kind}×${it.count}" }}")
            if (p.ledger.isNotEmpty()) add("ledger ${p.accountId.take(8)} ${cut(p.ledger)}")
        }
        board.cards.forEach { c ->
            if (c.typedCounters.isNotEmpty()) add("counters ${c.id.take(8)} ${c.typedCounters.joinToString { "${it.kind}×${it.count}" }}")
            if (c.ledger.isNotEmpty()) add("ledger ${c.id.take(8)} ${cut(c.ledger)}")
        }
    }
}
