package org.asyncttwiz.android

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.asyncttwiz.protocol.PlayZone

/**
 * TBL-124 (async play §29.5; owner, 2026-11-09): the chips of a battlefield card with two or more abilities, one of them
 * usable now, in a row just above the card (below it when there is no room above), as a hand card's cost chips (ZON-080):
 * one per ability, its effect and its cost drawn as card text; one not usable now greyed, saying why. A tap activates that
 * ability (a greyed one shakes); holding a chip shows the ability's whole text in a card-like popup. Personal UI
 * ([TableToolkitController.abilityCard]); closed by a tap anywhere else.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PermanentAbilityChips(toolkit: TableToolkitController, play: TableBoard, scene: TableScene?, names: Map<String, String> = emptyMap()) {
    val cardId = toolkit.abilityCard ?: return
    val offered = toolkit.abilities(play, cardId)
    // The board changed under the chips: nothing left to activate, or a single ability, closes them
    if (offered != null && (offered.size < 2 || offered.none { it.legal })) { LaunchedEffect(cardId, offered) { toolkit.closeAbilities() }; return }
    val item = scene?.items?.firstOrNull { it.cardId == cardId && it.zone == PlayZone.BATTLEFIELD && it.viewport != "combat" }
    if (offered == null || item == null || toolkit.cast.open) return
    val held = toolkit.heldAbility
    val name = CastController.cardName(play, cardId)
    Layout(content = {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            held?.let { offered.getOrNull(it) }?.let { AbilityDetails(name, it.text) }
            // TBL-124 (owner gallery review 2026-11-06): the fewest rows, in a stable order close to the card's
            ChipRowsLayout(4.dp) {
                offered.forEachIndexed { index, ability ->
                    AbilityChip(ability.text, { toolkit.activateChip(index) },
                        { hold -> toolkit.heldAbility = if (hold) index else null },
                        refusal = ability.refusal?.takeIf { !ability.legal }?.let { chipRefusal(it, ability.kind, name, names) })
                }
            }
        }
    }, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
        val margin = 8.dp.roundToPx()
        val gap = 6.dp.roundToPx()
        val placeable = measurables.first().measure(Constraints(maxWidth = (constraints.maxWidth - 2 * margin).coerceAtLeast(0),
            maxHeight = constraints.maxHeight))
        layout(constraints.maxWidth, constraints.maxHeight) {
            val x = (item.bounds.center.x.dp.roundToPx() - placeable.width / 2)
                .coerceIn(margin, (constraints.maxWidth - placeable.width - margin).coerceAtLeast(margin))
            val above = item.bounds.y.dp.roundToPx() - gap - placeable.height
            val y = if (above >= margin) above else item.bounds.bottom.dp.roundToPx() + gap
            placeable.place(x, y.coerceIn(0, (constraints.maxHeight - placeable.height).coerceAtLeast(0)))
        }
    }
}

/** An ability's printed text split into what it does and its cost ("{2}{G}, {T}: Draw a card." → "Draw a card", "{2}{G},
 * {T}"); text without a cost (a special action's) is all effect. A chip shows the cost first ([AbilityChipText]). */
internal fun abilityParts(text: String): Pair<String, String?> {
    val colon = text.indexOf(": ")
    val effect = (if (colon > 0) text.substring(colon + 2) else text).trim().removeSuffix(".")
    return effect to (if (colon > 0) text.substring(0, colon).trim() else null)
}

/** TBL-124: why an ability's chip is greyed: the menu's words (TBL-123), its catch-all shorter on a chip. */
internal fun chipRefusal(refusal: org.asyncttwiz.automatum.CardPlay.Refusal, kind: org.asyncttwiz.automatum.CardPlay.Kind, name: String,
    names: Map<String, String>): String =
    if (refusal == org.asyncttwiz.automatum.CardPlay.Refusal.Other) "Not now: a cost it can't pay, or nothing to target."
    else engineRefusal(refusal, kind, name, names)

/** An ability's chip; with [refusal] greyed, saying why under its words: a tap only shakes it. */
@Composable
internal fun AbilityChip(text: String, tap: () -> Unit, hold: (Boolean) -> Unit, refusal: String? = null) {
    val (effect, cost) = abilityParts(text)
    var shakes by remember { mutableIntStateOf(0) }
    val press: () -> Unit = { if (refusal != null) shakes++ else tap() }
    val ink = if (refusal != null) MenuInk.copy(alpha = .45f) else AutomatumViolet
    Surface(shape = RoundedCornerShape(14.dp), color = Color.White, border = BorderStroke(1.5.dp, if (refusal != null) MenuBorder else AutomatumViolet),
        shadowElevation = 2.dp, modifier = Modifier.shaken(shakes).semantics {
            contentDescription = if (refusal != null) "$text ($refusal)" else text
            role = Role.Button
            if (refusal != null) disabled()
            onClick { press(); true }
            onLongClick { hold(true); true }
        }.chipGestures(press, hold)) {
        // Owner playtest 2026-11-05 evening: a long effect wraps onto more rows instead of being cut
        if (refusal == null) Row(Modifier.heightIn(min = 32.dp).padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            AbilityChipText(effect, cost, ink)
        } else Column(Modifier.heightIn(min = 32.dp).padding(horizontal = 10.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { AbilityChipText(effect, cost, ink) }
            Text(refusal, Modifier.widthIn(max = CHIP_TEXT_WIDTH + CHIP_COST_WIDTH), fontSize = 10.sp, lineHeight = 12.sp, color = MenuInk.copy(alpha = .6f))
        }
    }
}

/** An ability chip's words as on a card, cost first, then the effect (owner playtest 2026-11-06): "{2}{G}, {T}:" in
 * symbols and "Draw a card" in [ink] after it, each wrapping onto more rows rather than being cut. */
@Composable
internal fun RowScope.AbilityChipText(effect: String, cost: String?, ink: Color) {
    cost?.let { CardText("$it:", Modifier.widthIn(max = CHIP_COST_WIDTH), style = TextStyle(fontSize = 13.sp, lineHeight = 16.sp)); Spacer(Modifier.width(4.dp)) }
    CardText(effect, Modifier.weight(1f, fill = false).widthIn(max = CHIP_TEXT_WIDTH),
        style = TextStyle(fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, color = ink))
}

/** The most a chip's words take before they wrap (TBL-124, ZON-080), and its cost's. */
internal val CHIP_TEXT_WIDTH = 170.dp
internal val CHIP_COST_WIDTH = 120.dp

/** The held ability chip's details, card-like: the card's name and the ability's whole printed text. */
@Composable
private fun AbilityDetails(name: String, text: String) {
    Surface(shape = RoundedCornerShape(6.dp), color = Color.White, shadowElevation = 6.dp,
        border = BorderStroke(1.dp, AutomatumViolet),
        modifier = Modifier.widthIn(max = 300.dp).semantics { contentDescription = "Details $text" }) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CardText(name, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold))
            CardText(text, style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp))
        }
    }
}
