package org.asyncttwiz.android

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.asyncttwiz.protocol.*

/**
 * Prepare cube (DRF-141): the deck preparation screen (LOB-047–LOB-051) for a pod's cube. Main and Maybe as pictures by
 * type; swipe moves one copy to the other section, tap offers "Move one to…", "Move all N to…" and "Inspect", hold
 * previews; the floating Undo takes back moves one at a time. Only this pod's cube changes.
 */
@Composable
internal fun PodCubePreparationScreen(preparation: PodCubePreparation) {
    val state = preparation.state
    BackHandler { if (preparation.inspection.state != null) preparation.inspection.close() else preparation.close() }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(state.feedback) {
        if (state.feedback != null) { alpha.snapTo(1f); alpha.animateTo(0f, tween(1500)) } else alpha.snapTo(0f)
    }
    val menus = remember { PodCardMenuOwner() }
    var held by remember { mutableStateOf<Pair<String, PreparationTile>?>(null) }
    val previewScroll = rememberScrollState()
    val columns = rememberDeckViewColumns(DeckViewScreen.CUBE_PREPARATION)
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(state.cube?.cubeName?.let { "Prepare cube · $it" } ?: "Prepare cube") {
                ScreenOutlinedButton(preparation::close, Modifier.testTag("prepare-cube-done")) {
                    EntryIcon(null, "ready"); Spacer(Modifier.width(6.dp)); Text("Done")
                }
            }
            Row(Modifier.fillMaxWidth().height(40.dp).padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(state.feedback?.text.orEmpty(), Modifier.weight(1f).alpha(alpha.value).semantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = state.feedback?.text?.let { "Latest move: $it" } ?: "Latest move"
                }, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                DeckViewColumnsControl(columns)
            }
            InitialLoadArea(state.cube != null, Modifier.weight(1f).fillMaxWidth(), state.error != null && state.cube == null,
                { state.pod?.let(preparation::open) }, loading = state.loading) {
                val cube = state.cube ?: return@InitialLoadArea
                LazyColumn(Modifier.fillMaxSize().testTag("prepare-cube"), contentPadding = PaddingValues(bottom = 80.dp)) {
                    for (section in SavedDeckSection.CUBE) {
                        val opposite = if (section == SavedDeckSection.MAINBOARD) SavedDeckSection.MAYBEBOARD else SavedDeckSection.MAINBOARD
                        val title = section.sectionLabel()
                        val target = opposite.sectionLabel()
                        val cards = cube.entries.filter { it.section == section }
                        visualDeckSection(section, cards.sumOf { it.quantity.toLong() },
                            preparationGroups(cards.map { it.definition to it.quantity.toLong() }, state.definitions), columns.count) { tile ->
                            val key = "$section/${tile.digest}"
                            val name = tile.name
                            fun move(all: Boolean) = preparation.move(tile.digest, section, all)
                            SwipeDeckCard(tile, key, "prepare-cube-card-$key", title, opposite, true, menus,
                                capture = { { move(false) } }, inspect = { preparation.inspect(tile.digest) },
                                onHold = { open ->
                                    if (open) {
                                        menus.open = null
                                        previewScroll.dispatchRawDelta(-previewScroll.value.toFloat())
                                        held = key to tile
                                    } else if (held?.first == key) held = null
                                }, onHoldScroll = { previewScroll.dispatchRawDelta(it) },
                                actions = listOf(CustomAccessibilityAction("Move one $name to $target") { move(false) },
                                    CustomAccessibilityAction("Move all $name to $target") { move(true) })) { close ->
                                if (tile.quantity > 1) DropdownMenuItem(text = { Text("Move one to $target") }, onClick = { close(); move(false) })
                                DropdownMenuItem(text = { Text(if (tile.quantity > 1) "Move all ${tile.quantity} to $target" else "Move to $target") },
                                    onClick = { close(); move(true) })
                                DropdownMenuItem(text = { Text("Inspect") }, onClick = { close(); preparation.inspect(tile.digest) })
                            }
                        }
                    }
                }
            }
        }
        if (state.undo.isNotEmpty()) Surface(Modifier.align(Alignment.BottomStart).padding(16.dp).size(48.dp).testTag("prepare-cube-undo"),
            shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 4.dp) {
            IconButton(preparation::undo, modifier = Modifier.semantics { contentDescription = "Undo" }) { TableChromeIcon("undo") }
        }
        if (state.cube != null) FloatingTransientError(state.error, preparation::dismissError, Modifier.align(Alignment.BottomCenter))
    }
    held?.second?.let { tile ->
        val card = remember(tile.digest, tile.definition) {
            tile.definition?.takeIf { it.faces.isNotEmpty() }?.let { InspectedCard.retained(tile.digest, it) }
        }
        PodCardArtPreview(card, tile.name, previewScroll)
    }
    DeckCardInspection(preparation.inspection)
}
