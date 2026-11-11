package org.asyncttwiz.android

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import arcanum.engine.decision.ManaUse
import org.asyncttwiz.automatum.ManaWays

/** The test tag of a paying source's corner chip (STK-024). */
internal const val PAY_CHIP_TAG = "pay-chip"

/** The amber of a payment's outlined sources and their corner chips (STK-024). */
internal val PayAmber = Color(0xffd08a1e)

/**
 * STK-024 (owner playtest 2026-11-09): each source paying the open payment on the table (a cast's, or one made while something
 * resolves, STK-049) carries a small amber chip at its top right corner with the mana it pays ("{U}", "Convoke"); when the source
 * can pay in more than one way (two colours, several mana abilities, a mana ability and convoke) a tap on the chip opens its ways
 * beside it, and a tap on one pays that way from then on. A source with one way has a chip that takes no taps.
 * [bounds]: where each item is drawn now (moving cards included).
 */
@Composable
internal fun PaymentCornerChips(toolkit: TableToolkitController, scene: TableScene, bounds: (TableItem) -> TableBounds) {
    if (!toolkit.paying) return
    var open by remember { mutableStateOf<String?>(null) }
    val paying = toolkit.paySources()
    if (open != null && open !in paying) open = null
    val chips = paying.mapNotNull { card ->
        val item = scene.items.firstOrNull { it.cardId == card && it.battlefieldCard } ?: return@mapNotNull null
        val shown = bounds(item)
        val field = scene.viewports.firstOrNull { it.id == item.viewport }?.bounds
        if (field != null && !field.contains(shown.center)) return@mapNotNull null
        val use = toolkit.paidWith(card) ?: return@mapNotNull null
        PayChip(card, item.label, shown, use, toolkit.waysOf(card))
    }
    for (chip in chips) key(chip.card) {
        val card = chip.card
        val use = chip.use
        val ways = chip.ways
        val label = ManaWays.label(use)
        val several = ways.size > 1
        CornerPlaced(chip.bounds) {
            Surface(shape = RoundedCornerShape(8.dp), color = Color.White, border = BorderStroke(1.5.dp, PayAmber), shadowElevation = 2.dp,
                modifier = Modifier.testTag(PAY_CHIP_TAG).semantics {
                    contentDescription = "Pays $label: ${chip.name}${if (several) ", tap for its ways" else ""}"
                    if (several) role = Role.Button
                }.then(if (several) Modifier.clickable {
                    AppLog.info("cast", "Corner chip of $card tapped: ways ${ways.joinToString { ManaWays.label(it) }}")
                    open = if (open == card) null else card
                } else Modifier)) {
                Row(Modifier.padding(horizontal = 4.dp, vertical = 0.dp), verticalAlignment = Alignment.CenterVertically) {
                    CardText(label, style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium))
                    if (several) Text(" ▾", fontSize = 9.sp, color = PayAmber)
                }
            }
            if (open == card) WaysPopup(ways, use, chip.bounds.height, { open = null }) { way -> open = null; toolkit.chooseWay(card, way) }
        }
    }
}

/** One paying source's corner chip: its table card, name, where it is drawn, the way it pays and all its ways. */
private class PayChip(val card: String, val name: String, val bounds: TableBounds, val use: ManaUse, val ways: List<ManaUse>)

/** [content] centred on [card]'s top right corner. */
@Composable
private fun CornerPlaced(card: TableBounds, content: @Composable () -> Unit) {
    Layout(content = { Box { content() } }, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
        val placeable = measurables.first().measure(Constraints())
        layout(constraints.maxWidth, constraints.maxHeight) {
            // Straddling the corner, so most of the card stays in view
            val x = card.right.dp.roundToPx() - placeable.width / 2
            val y = card.y.dp.roundToPx() - placeable.height / 2
            placeable.place(x.coerceIn(0, (constraints.maxWidth - placeable.width).coerceAtLeast(0)),
                y.coerceIn(0, (constraints.maxHeight - placeable.height).coerceAtLeast(0)))
        }
    }
}

/** A source's ways as chips under its card, the way it pays now marked; a tap on one chooses it, a tap elsewhere closes. */
@Composable
private fun WaysPopup(ways: List<ManaUse>, now: ManaUse, cardHeight: Float, dismiss: () -> Unit, choose: (ManaUse) -> Unit) {
    // Below the card, so the card stays in view
    val below = with(androidx.compose.ui.platform.LocalDensity.current) { (cardHeight + 12).dp.roundToPx() }
    Popup(alignment = Alignment.TopEnd, offset = androidx.compose.ui.unit.IntOffset(0, below), onDismissRequest = dismiss,
        properties = PopupProperties(focusable = true)) {
        Surface(shape = RoundedCornerShape(10.dp), color = Color.White, border = BorderStroke(1.5.dp, PayAmber), shadowElevation = 6.dp,
            modifier = Modifier.semantics { contentDescription = "Ways to pay" }) {
            Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (way in ways) {
                    val label = ManaWays.label(way)
                    val chosen = way == now
                    Surface(shape = RoundedCornerShape(8.dp), color = if (chosen) PayAmber.copy(alpha = .18f) else Color.White,
                        border = BorderStroke(1.dp, if (chosen) PayAmber else Color(0xff9aa7a3)),
                        modifier = Modifier.semantics { contentDescription = "Pay with $label${if (chosen) ", paying" else ""}"; role = Role.Button }
                            .clickable { choose(way) }) {
                        CardText(label, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = TextStyle(fontSize = 13.sp))
                    }
                }
            }
        }
    }
}
