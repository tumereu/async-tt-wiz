package org.asyncttwiz.android

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.PowerSettingsNew
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
 * The switch's hold panel (integration spec §5), in plain words for players: whether Automatum is on and what it does,
 * what is left to the players (cards with abilities it can't read, and why it can't take over the board when it
 * can't), the viewer's own "Skip when I have no choice" (I-D18) and "Clear Automatum data" (I-D24, confirmed). The
 * engine's records (P2) stay behind a closed "Engine details" toggle at the bottom.
 */
@Composable
internal fun AutomatumPanel(play: PlayState, viewer: String, computed: ComputedBoard, enabled: Boolean,
    dismiss: () -> Unit, submit: (PlayAction) -> Boolean) {
    val board = play.board
    var confirmClear by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf(false) }
    val text = automatumPanelText(board.automatum, computed)
    // A sheet like Manage Stack: text and controls in one panel; the whole panel takes its own touches.
    InlinePopupPanel(dismiss, "Automatum", width = 320.dp, height = 360.dp, sheet = true) {
        Column(Modifier.fillMaxWidth().ringInputTarget(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text.status, Modifier.semantics { contentDescription = "Automatum status" },
            fontWeight = FontWeight.Bold, color = AutomatumViolet)
        Text(text.automated, fontSize = 13.sp, lineHeight = 17.sp)
        text.byHand?.let { Text(it, fontSize = 13.sp, lineHeight = 17.sp) }
        if (text.notTakenOver.isNotEmpty()) Column {
            Text("Automatum can't take over this board yet:", fontSize = 13.sp, lineHeight = 17.sp)
            text.notTakenOver.forEach { Text("• $it", fontSize = 13.sp, lineHeight = 17.sp) }
        }
        val own = board.players.singleOrNull { it.accountId == viewer }
        if (own != null) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Skip when I have no choice", Modifier.weight(1f))
            Switch(own.skipWhenNoChoice, { submit(PlayAction.Mtg(MtgAction.SetSkipWhenNoChoice(it))) }, enabled = enabled,
                modifier = Modifier.semantics { contentDescription = "Skip when I have no choice" })
        }
        OutlinedButton({ confirmClear = true }, enabled = enabled && Automatum.recorded(board)) {
            Icon(Icons.Outlined.DeleteSweep, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Clear Automatum data")
        }
        HorizontalDivider()
        // Developer detail, secondary: small, grey and closed until asked for
        Row(Modifier.fillMaxWidth().clickable { details = !details }.padding(vertical = 4.dp)
            .semantics(mergeDescendants = true) { contentDescription = if (details) "Hide engine details" else "Show engine details" },
            verticalAlignment = Alignment.CenterVertically) {
            Icon(if (details) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, Modifier.size(16.dp), tint = DetailGrey)
            Spacer(Modifier.width(4.dp))
            Text("Engine details", fontSize = 11.sp, color = DetailGrey)
        }
        if (details) {
            val lines = automatumDebugLines(board)
            if (lines.isEmpty()) Text("Nothing recorded", fontSize = 11.sp, color = DetailGrey)
            Box(Modifier.horizontalScroll(rememberScrollState())) {
                Column { lines.forEach { Text(it, fontSize = 10.sp, fontFamily = FontFamily.Monospace, lineHeight = 12.sp, color = DetailGrey) } }
            }
        }
        }
    }
    if (confirmClear) AlertDialog({ confirmClear = false }, title = { Text("Clear Automatum data?") },
        text = { Text("Removes every effect, ledger and record Automatum keeps in this game. Cards, counters and mana stay as they are.") },
        confirmButton = { TextButton({ confirmClear = false; submit(PlayAction.Mtg(MtgAction.ClearRecords)) }) {
            Icon(Icons.Outlined.DeleteSweep, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Clear")
        } },
        dismissButton = { TextButton({ confirmClear = false }) {
            Icon(Icons.Outlined.Close, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Cancel")
        } })
}

private val DetailGrey = Color(0xff6b7b80)

/** What the Automatum panel says (TRN-055): [status] "Automatum on" / "Automatum off", [automated] what that means,
 * [byHand] the cards whose abilities players carry out themselves (null: none), [notTakenOver] why Automatum can't take
 * over the board (empty: it can). */
internal data class AutomatumPanelText(val status: String, val automated: String, val byHand: String?,
    val notTakenOver: List<String>)

internal fun automatumPanelText(on: Boolean, computed: ComputedBoard): AutomatumPanelText {
    if (!on) return AutomatumPanelText("Automatum off", "Both players play by hand; nothing is checked or done automatically.",
        null, emptyList())
    val cards = computed.manualCards.joinToString(", ") { (name, count) -> if (count == 1) name else "$name ($count)" }
    val byHand = if (computed.manualCards.isEmpty()) null
        else "By hand: $cards. Automatum can't read ${if (computed.manualAbilities == 1) "this ability" else "these abilities"} " +
            "yet; you carry ${if (computed.manualAbilities == 1) "it" else "them"} out yourself."
    return AutomatumPanelText("Automatum on",
        "It plays by the rules for both players: casting, abilities, combat and the turn's steps.",
        byHand, computed.unsupported)
}

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

/** What the Automatum switch's confirmation says (I-D45): title, text and confirm button. */
internal data class AutomatumAskText(val title: String, val text: String, val button: String)

/** [on] is what the switch would turn Automatum to; [flowOpen] an unfinished Automatum step and [castOpen] the viewer's
 * own unsent cast, both of which switching off takes back (SYS-060, STK-025). */
internal fun automatumAskText(on: Boolean, flowOpen: Boolean, castOpen: Boolean): AutomatumAskText =
    if (on) AutomatumAskText("Turn Automatum on?",
        "Automatum then plays the game by the rules for both players: it casts, resolves and moves things for you.", "Turn on")
    else AutomatumAskText("Turn Automatum off?", "Both players then play by hand; nothing is checked or done automatically." +
        (if (flowOpen) " Automatum's unfinished step is taken back first." else "") +
        (if (castOpen) " Your unfinished cast is dropped." else ""), "Turn off")

/** I-D45 (TRN-054): tapping the switch asks first; Cancel, Back or a tap outside does nothing. */
@Composable
internal fun AutomatumSwitchDialog(toolkit: TableToolkitController, play: PlayState) {
    val on = toolkit.automatumAsk ?: return
    val text = automatumAskText(on, Automatum.flowOpen(play.board), toolkit.cast.open)
    AlertDialog(onDismissRequest = toolkit::cancelAutomatum,
        title = { Text(text.title) },
        text = { Text(text.text) },
        confirmButton = { TextButton({ toolkit.confirmAutomatum() }) {
            Icon(Icons.Outlined.PowerSettingsNew, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(text.button)
        } },
        dismissButton = { TextButton(toolkit::cancelAutomatum) {
            Icon(Icons.Outlined.Close, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Cancel")
        } })
}
