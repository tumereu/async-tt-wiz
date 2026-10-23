package org.asyncttwiz.android

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import org.asyncttwiz.protocol.*

data class ContentsEntry(val cardId: String, val name: String, val category: String, val location: String,
    val bottommost: Boolean = false)

/** Permission-aware presentation: backs contain no definition-derived names, types or search matches. */
fun contentsEntries(state: PlayState, display: ContentsDisplay, viewer: String, search: String = ""): List<ContentsEntry> {
    val visible = display.facesVisibleTo(viewer)
    val entries = display.cardIds.mapIndexed { index, id ->
        val card = state.board.cards.singleOrNull { it.id == id }
        val face = if (visible) state.definitions.singleOrNull { it.digest == card?.definition }?.definition?.faces
            ?.singleOrNull { it.faceId == card?.faceId } else null
        val type = face?.typeLine.orEmpty()
        val category = if (!visible) "Cards" else listOf("Creature", "Land", "Artifact", "Enchantment", "Planeswalker", "Instant", "Sorcery", "Battle")
            .firstOrNull { type.contains(it, ignoreCase = true) } ?: "Other"
        ContentsEntry(id, if (visible) face?.name ?: "Unavailable card" else "Card back", category,
            card?.let { c -> state.board.containers.singleOrNull { it.id == c.containerId }?.zone?.name?.lowercase() } ?: "Unavailable",
            display.selection == ContentsSelection.BOTTOM && index == display.cardIds.lastIndex)
    }
    // Covered rows must not be removed/reordered by a hidden name query.
    val filtered = if (visible) entries.filter { it.name.contains(search, ignoreCase = true) } else entries
    return if (display.selection == ContentsSelection.ALL) filtered.sortedWith(compareBy({ it.category }, { it.name.lowercase() }, { it.cardId })) else filtered
}

/** Rendering never issues View/inspection. The adapter receives exact bound contexts only after gestures. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContentsScreen(state: PlayState, display: ContentsDisplay, viewer: String, controller: LibraryController,
    onInspect: (LibraryInspection) -> Unit, modifier: Modifier = Modifier, onActions: ((String) -> Unit)? = null,
    names: Map<String, String> = emptyMap(), onComplete: () -> Unit = {}) {
    var search by remember(display.id) { mutableStateOf("") }
    var selected by remember(display.id) { mutableStateOf<String?>(null) }
    var destination by remember(display.id) { mutableStateOf<MoveDestination?>(null) }
    val entries = contentsEntries(state, display, viewer, search)
    Column(modifier) {
        Row {
            Text(if (display.reveal) "Reveal" else "View", Modifier.weight(1f))
            MenuButton("Dismiss", Modifier.width(128.dp), enabled = controller.available()) {
                val result = controller.dismiss(display.id)
                if (result.submitted > 0 && result.refused == 0) onComplete()
            }
        }
        if (display.selection == ContentsSelection.ALL && display.facesVisibleTo(viewer))
            OutlinedTextField(search, { search = it }, label = { Text("Search by name") }, singleLine = true)
        val groups = if (display.selection == ContentsSelection.ALL) entries.groupBy { it.category } else mapOf("" to entries)
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
            groups.forEach { (category, cards) ->
                Column(Modifier.width(220.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(8.dp)) {
                    if (category.isNotEmpty()) Text(category, style = MaterialTheme.typography.labelMedium)
                    cards.forEach { entry ->
                        Row(
                            Modifier.fillMaxWidth().combinedClickable(onClick = { selected = entry.cardId; onActions?.invoke(entry.cardId) },
                                onLongClick = { controller.inspection(state, entry.cardId, viewer, hold = true,
                                    displayFaceVisible = display.facesVisibleTo(viewer))?.let(onInspect) }).padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val card = state.board.cards.singleOrNull { it.id == entry.cardId }
                            val definition = state.definitions.singleOrNull { it.digest == card?.definition }?.definition
                            val face = definition?.faces?.indexOfFirst { it.faceId == card?.faceId } ?: -1
                            val art = if (display.facesVisibleTo(viewer) && definition != null && face >= 0)
                                InspectedCard.retained(requireNotNull(card).definition, definition).artwork[face] else null
                            Box(Modifier.size(40.dp, 56.dp), contentAlignment = Alignment.Center) {
                                if (art != null) CardArtwork(art, entry.name, 80, 112, Modifier.fillMaxSize())
                                else MenuIcon(if (display.facesVisibleTo(viewer)) "Inspect" else "Turn face down")
                            }
                            Column(Modifier.weight(1f)) {
                                Text(entry.name, style = MaterialTheme.typography.bodyMedium)
                                Text("${entry.location}${if (entry.bottommost) " · Bottommost" else ""}", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
        selected?.let { id ->
            LibraryDestinationPicker(state.board, destination, { destination = it }, names)
            Row {
                MenuButton("Move", Modifier.weight(1f), enabled = controller.available() && destination != null) {
                    destination?.let {
                        val result = controller.moveCards(listOf(id), it)
                        if (result.submitted > 0 && result.refused == 0) onComplete()
                    }
                }
                MenuButton("Inspect other side", Modifier.weight(1f), enabled = controller.available()) {
                    controller.inspection(state, id, viewer, hold = false)?.let(onInspect)
                }
            }
        }
    }
}
