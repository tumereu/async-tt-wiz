package org.asyncttwiz.android

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.asyncttwiz.protocol.PlayState
import org.asyncttwiz.protocol.PlayZone

/**
 * TBL-122 (async play §29.5): the chips of a battlefield card with several abilities the viewer can activate now, in a
 * row just above the card (below it when there is no room above), as a hand card's cost chips (ZON-080): one per
 * ability, its effect and its cost drawn as card text. A tap activates that ability; holding a chip shows the ability's
 * whole text in a card-like popup. Personal UI ([TableToolkitController.abilityCard]); closed by a tap anywhere else.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PermanentAbilityChips(toolkit: TableToolkitController, play: PlayState, scene: TableScene?) {
    val cardId = toolkit.abilityCard ?: return
    val offered = toolkit.activatable(play, cardId)
    // The board changed under the chips: fewer than two abilities left to activate closes them
    if (offered != null && offered.size < 2) { LaunchedEffect(cardId, offered) { toolkit.closeAbilities() }; return }
    val item = scene?.items?.firstOrNull { it.cardId == cardId && it.zone == PlayZone.BATTLEFIELD && it.viewport != "combat" }
    if (offered == null || item == null || toolkit.cast.open) return
    val held = toolkit.heldAbility
    val name = CastController.cardName(play, cardId)
    Layout(content = {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            held?.let { offered.getOrNull(it) }?.let { AbilityDetails(name, it.text) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                offered.forEachIndexed { index, ability ->
                    AbilityChip(ability.text, { toolkit.activateChip(index) },
                        { hold -> toolkit.heldAbility = if (hold) index else null })
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
 * {T}"); text without a cost (a special action's) is all effect. */
internal fun abilityParts(text: String): Pair<String, String?> {
    val colon = text.indexOf(": ")
    val effect = (if (colon > 0) text.substring(colon + 2) else text).trim().removeSuffix(".")
    return effect to (if (colon > 0) text.substring(0, colon).trim() else null)
}

@Composable
private fun AbilityChip(text: String, tap: () -> Unit, hold: (Boolean) -> Unit) {
    val (effect, cost) = abilityParts(text)
    Surface(shape = RoundedCornerShape(14.dp), color = Color.White, border = BorderStroke(1.5.dp, AutomatumViolet),
        shadowElevation = 2.dp, modifier = Modifier.semantics {
            contentDescription = text
            role = Role.Button
            onClick { tap(); true }
            onLongClick { hold(true); true }
        }.chipGestures(tap, hold)) {
        Row(Modifier.heightIn(min = 32.dp).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            CardText(effect, Modifier.widthIn(max = 140.dp),
                style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AutomatumViolet), maxLines = 1)
            cost?.let { Spacer(Modifier.width(4.dp)); CardText(it, style = TextStyle(fontSize = 13.sp), maxLines = 1) }
        }
    }
}

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
