package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Bounded non-table inspector. The shared table inspector and disclosure policy stay untouched. */
@Composable
internal fun DeckCardInspection(controller: CardInspectionController) {
    val state = controller.state ?: return
    val card = state.card
    val face = card.definition.faces[state.face]
    ScreenDialog(controller::close, Modifier.widthIn(max = 340.dp).semantics { paneTitle = "Card inspection" }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            ScreenIconButton("Close card inspection", "close", controller::close)
        }
        if (card.definition.faces.size > 1) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ScreenOutlinedButton(onClick = { controller.face(state.face - 1) }, Modifier.weight(1f), enabled = state.face > 0) { Text("Previous face") }
            ScreenOutlinedButton(onClick = { controller.face(state.face + 1) }, Modifier.weight(1f), enabled = state.face < card.definition.faces.lastIndex) { Text("Next face") }
        }
        key(card, state.face) {
            val density = LocalDensity.current
            val scale = if (state.enlarged) 2 else 1
            val reference = card.artwork[state.face]
            if (reference?.printing != null || reference?.customUrl != null) {
                Box(Modifier.fillMaxWidth().height(336.dp).horizontalScroll(rememberScrollState()).verticalScroll(rememberScrollState())
                    .semantics { contentDescription = "Card artwork viewport" }, contentAlignment = Alignment.TopCenter) {
                    CardArtwork(reference, face.name, with(density) { (240.dp * scale).roundToPx() },
                        with(density) { (336.dp * scale).roundToPx() }, Modifier.requiredSize(240.dp * scale, 336.dp * scale))
                }
                ScreenOutlinedButton(onClick = controller::zoom) { EntryIcon(null, "search"); Spacer(Modifier.width(8.dp)); Text(if (state.enlarged) "Fit artwork" else "Enlarge artwork") }
            }
            Column(Modifier.fillMaxWidth().semantics { contentDescription = "Card inspection content" }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(face.name, style = MaterialTheme.typography.titleLarge)
                if (face.manaCost.isNotBlank()) CardText(face.manaCost)
                if (face.typeLine.isNotBlank()) Text(face.typeLine)
                if (face.rulesText.isNotBlank()) CardText(face.rulesText)
                if (face.corner.isNotBlank()) Text(face.corner, Modifier.align(Alignment.End))
                else if (face.power.isNotBlank() || face.toughness.isNotBlank()) Text("${face.power} / ${face.toughness}")
                if (face.loyalty.isNotBlank()) Text("Loyalty: ${face.loyalty}")
                if (face.defense.isNotBlank()) Text("Defense: ${face.defense}")
            }
        }
    }
}
