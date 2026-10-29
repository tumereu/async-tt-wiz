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
    ScreenDialog(controller::close, Modifier.widthIn(max = if (state.degrees % 180 != 0) 380.dp else 340.dp).semantics { paneTitle = "Card inspection" }) {
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
            val orientation = card.orientation(state.face)
            if (reference?.printing != null || reference?.customUrl != null) BoxWithConstraints(Modifier.fillMaxWidth()) {
                // A card printed sideways shows turned, across the full width.
                val sideways = state.degrees % 180 != 0
                val sidewaysSize = inspectedArtworkSize(true, maxWidth)
                val width = if (sideways) sidewaysSize.width else 240.dp
                val height = if (sideways) sidewaysSize.height else 336.dp
                Box(Modifier.fillMaxWidth().height(height).horizontalScroll(rememberScrollState()).verticalScroll(rememberScrollState())
                    .semantics { contentDescription = "Card artwork viewport" }, contentAlignment = Alignment.TopCenter) {
                    CardArtwork(reference, face.name, with(density) { (width * scale).roundToPx() },
                        with(density) { (height * scale).roundToPx() }, Modifier.requiredSize(width * scale, height * scale),
                        placeholder = { InspectorArtworkPlaceholder(reference, face.name) }, degrees = state.degrees)
                }
            }
            if (reference?.printing != null || reference?.customUrl != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScreenOutlinedButton(onClick = controller::zoom, Modifier.weight(1f)) { EntryIcon(null, "search"); Spacer(Modifier.width(8.dp)); Text(if (state.enlarged) "Fit artwork" else "Enlarge artwork") }
                // Aftermath and flip halves: a personal turn to read the other half.
                if (orientation.alternate != null)
                    ScreenIconButton(if (state.turned) "Rotate back" else "Rotate card", "rotate", controller::turn)
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
