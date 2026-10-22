package org.asyncttwiz.android

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Text remains available regardless of artwork/network state. No gameplay permission policy here. */
@Composable
internal fun CardInspection(controller: CardInspectionController) {
    val state = controller.state ?: return
    val card = state.card
    val face = card.definition.faces[state.face]
    Dialog(onDismissRequest = controller::close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.widthIn(max = 720.dp).fillMaxWidth(.94f).fillMaxHeight(.94f)
            .semantics { paneTitle = "Card inspection" }, shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(card.definition.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = controller::close) { EntryIcon("Close card inspection", "close") }
                }
                if (card.definition.faces.size > 1) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { controller.face(state.face - 1) }, Modifier.weight(1f), enabled = state.face > 0) { Text("Previous face") }
                    Text("${state.face + 1} / ${card.definition.faces.size}", Modifier.padding(horizontal = 8.dp))
                    TextButton(onClick = { controller.face(state.face + 1) }, Modifier.weight(1f), enabled = state.face < card.definition.faces.lastIndex) { Text("Next face") }
                }
                key(card, state.face) {
                    val artwork: @Composable () -> Unit = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            BoxWithConstraints(Modifier.fillMaxWidth()) {
                                val width = minOf(maxWidth, 320.dp)
                                val height = width * 1.4f
                                val density = LocalDensity.current
                                val scale = if (state.enlarged) 2 else 1
                                Box(Modifier.fillMaxWidth().height(height)
                                    .horizontalScroll(rememberScrollState()).verticalScroll(rememberScrollState())
                                    .semantics { contentDescription = "Card artwork viewport" },
                                    contentAlignment = Alignment.TopCenter) {
                                    CardArtwork(card.artwork[state.face], "Artwork for ${face.name}",
                                        with(density) { (width * scale).roundToPx() }.coerceIn(1, 4096),
                                        with(density) { (height * scale).roundToPx() }.coerceIn(1, 4096),
                                        Modifier.requiredSize(width * scale, height * scale))
                                }
                            }
                            TextButton(onClick = controller::zoom) { Text(if (state.enlarged) "Fit artwork" else "Enlarge artwork") }
                        }
                    }
                    val text: @Composable () -> Unit = {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(face.name, style = MaterialTheme.typography.titleLarge)
                            if (face.manaCost.isNotEmpty()) Text(face.manaCost)
                            if (face.typeLine.isNotEmpty()) Text(face.typeLine)
                            if (face.rulesText.isNotEmpty()) Text(face.rulesText)
                            if (face.power.isNotEmpty() || face.toughness.isNotEmpty()) Text("Power / Toughness: ${face.power} / ${face.toughness}")
                            if (face.loyalty.isNotEmpty()) Text("Loyalty: ${face.loyalty}")
                            if (face.defense.isNotEmpty()) Text("Defense: ${face.defense}")
                            if (face.corner.isNotEmpty() && face.power.isEmpty() && face.toughness.isEmpty() &&
                                face.loyalty.isEmpty() && face.defense.isEmpty()) Text(face.corner)
                        }
                    }
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                        if (maxWidth > maxHeight && maxWidth >= 480.dp) {
                            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) { artwork() }
                                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())
                                    .semantics { contentDescription = "Card inspection content" }) { text() }
                            }
                        } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                            .semantics { contentDescription = "Card inspection content" }) {
                            artwork(); Spacer(Modifier.height(12.dp)); text(); Spacer(Modifier.height(12.dp))
                        }
                    }
                }
            }
        }
    }
}
