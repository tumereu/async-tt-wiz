package org.asyncttwiz.android

import androidx.activity.compose.BackHandler

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import org.asyncttwiz.protocol.*

/** Text remains available regardless of artwork/network state. No gameplay permission policy here. */
@Composable
internal fun CardInspection(controller: CardInspectionController, annotation: String? = null,
    markedOptions: List<Pair<Int, StackTextOption>> = emptyList(),
    token: Boolean = false, owner: Color? = null, projection: Boolean = false, peek: Boolean = false,
    peekScroll: ScrollState? = null) {
    val state = controller.state ?: return
    val card = state.card
    val face = card.definition.faces[state.face]
    val localScroll = key(card, state.face) { rememberScrollState() }
    val contentScroll = if (peek) peekScroll ?: localScroll else localScroll
    val content: @Composable () -> Unit = {
        Surface(Modifier.widthIn(max = 720.dp).fillMaxWidth(.94f).fillMaxHeight(.94f)
            .semantics { paneTitle = "Card inspection" }, shape = MaterialTheme.shapes.large,
            color = Color.White, contentColor = MenuInk) {
            Column(Modifier.padding(12.dp)) {
                if (!peek) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(card.definition.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                         maxLines = 2, overflow = TextOverflow.Clip)
                    ManualActionSquare("Close card inspection", "", bordered = false, onClick = controller::close)
                }
                if (!peek && card.definition.faces.size > 1) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    MenuButton("Previous face", Modifier.weight(1f), enabled = state.face > 0) { controller.face(state.face - 1) }
                    Text("${state.face + 1} / ${card.definition.faces.size}", Modifier.padding(horizontal = 8.dp))
                    MenuButton("Next face", Modifier.weight(1f), enabled = state.face < card.definition.faces.lastIndex) { controller.face(state.face + 1) }
                }
                key(card, state.face) {
                    val artwork: @Composable (Dp) -> Unit = { heightLimit ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            BoxWithConstraints(Modifier.fillMaxWidth()) {
                                val width = minOf(maxWidth, 320.dp, if (peek) heightLimit / 1.4f else 320.dp)
                                val height = width * 1.4f
                                val density = LocalDensity.current
                                val scale = if (!peek && state.enlarged) 2 else 1
                                Box(Modifier.fillMaxWidth().height(height)
                                    .horizontalScroll(rememberScrollState()).verticalScroll(rememberScrollState())
                                    .semantics { contentDescription = "Card artwork viewport" },
                                    contentAlignment = Alignment.TopCenter) {
                                    CardArtwork(card.artwork[state.face], "Artwork for ${face.name}",
                                        with(density) { (width * scale).roundToPx() }.coerceIn(1, 4096),
                                        with(density) { (height * scale).roundToPx() }.coerceIn(1, 4096),
                                        Modifier.requiredSize(width * scale, height * scale)
                                            .then(if (owner != null) Modifier.cardFrame(token, owner, projection) else Modifier))
                                }
                            }
                            if (!peek) MenuButton(if (state.enlarged) "Fit artwork" else "Enlarge artwork", onClick = controller::zoom)
                        }
                    }
                    val text: @Composable () -> Unit = {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(if (peek) 4.dp else 12.dp)) {
                            annotation?.let { Text(it) }
                            // Current-face marks are highlighted inline below; list other faces too.
                            for ((index, option) in markedOptions.filter { it.second.faceId != face.faceId }) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StackMarkChip(index)
                                CardText(option.text)
                            }
                            val marked = markedOptions.filter { it.second.faceId == face.faceId }
                            @Composable fun markedText(value: String, paragraph: Int?, title: Boolean = false) {
                                val selected = marked.firstOrNull { it.second.paragraph == paragraph }
                                Row(if (selected != null) Modifier.fillMaxWidth().background(stackMarkColor(selected.first).copy(alpha = .16f),
                                    RoundedCornerShape(4.dp)).padding(6.dp).semantics { stateDescription = "Stack text mark ${selected.first + 1}" } else Modifier,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (selected != null) StackMarkChip(selected.first)
                                    CardText(value, style = if (title) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge)
                                }
                            }
                            markedText(face.name, null, title = true)
                            if (face.manaCost.isNotEmpty()) CardText(face.manaCost)
                            if (face.typeLine.isNotEmpty()) Text(face.typeLine)
                            face.rulesText.lines().map(String::trim).filter(String::isNotEmpty).forEachIndexed { index, paragraph ->
                                markedText(paragraph, index)
                            }
                             if (face.power.isNotEmpty() || face.toughness.isNotEmpty()) Text("${face.power}/${face.toughness}")
                            if (face.loyalty.isNotEmpty()) Text("Loyalty: ${face.loyalty}")
                            if (face.defense.isNotEmpty()) Text("Defense: ${face.defense}")
                            if (face.corner.isNotEmpty() && face.power.isEmpty() && face.toughness.isEmpty() &&
                                face.loyalty.isEmpty() && face.defense.isEmpty()) Text(face.corner)
                        }
                    }
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                        val availableHeight = maxHeight
                        if (maxWidth > maxHeight && maxWidth >= 480.dp) {
                            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) { artwork(availableHeight) }
                                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(contentScroll)
                                    .semantics { contentDescription = "Card inspection content" }) { text() }
                            }
                        } else {
                            val artworkHeight = if (peek) maxHeight * .45f else maxHeight
                            Column(Modifier.fillMaxSize().verticalScroll(contentScroll)
                                .semantics { contentDescription = "Card inspection content" }) {
                                artwork(artworkHeight); Spacer(Modifier.height(if (peek) 4.dp else 12.dp))
                                text(); Spacer(Modifier.height(if (peek) 4.dp else 12.dp))
                            }
                        }
                    }
                }
            }
        }
    }
    if (peek) Popup(properties = PopupProperties(focusable = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .28f)), contentAlignment = Alignment.Center) { content() }
    }
    else Dialog(onDismissRequest = controller::close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        MenuWindowDimming(); content()
    }
}

/** Game inspection filters playable faces BEFORE entering the general deck/art inspector. */
@Composable
internal fun GameplayCardInspection(state: PlayState, viewer: String, inspection: ManualInspection, close: () -> Unit,
    names: Map<String, String> = emptyMap(), peek: Boolean = false, peekScroll: ScrollState? = null) {
    if (inspection.gameId != state.gameId || inspection.playId != state.id) return
    BackHandler(enabled = peek, onBack = close)
    val card = inspection.snapshot ?: CombatStackRules.inspection(state.board, inspection.cardId) ?: return
    val definition = state.definitions.singleOrNull { it.digest == card.definition }?.definition
    val allowed = if (inspection.explicit) inspection.faceIds else
        ManualDisclosure.permittedFaces(state, card, viewer, inspecting = true).map { it.faceId }.filter { it in inspection.faceIds }
    val faces = definition?.faces.orEmpty().filter { it.faceId in allowed }
    if (faces.isEmpty()) {
        if (peek) Popup(properties = PopupProperties(focusable = false)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Surface { Text(if (definition == null) "Card data unavailable" else "Covered card", Modifier.padding(24.dp)) }
            }
        } else AlertDialog(onDismissRequest = close, title = { Text(if (definition == null) "Card data unavailable" else "Covered card") },
            text = { Text("Owner ${names[card.owner] ?: "Player"}") },
            confirmButton = { TextButton(onClick = close) { Text("Close") } })
    } else {
        val filtered = definition!!.copy(name = faces.first().name, faces = faces)
        val full = remember(card.definition, definition) { InspectedCard.retained(card.definition, definition) }
        val permitted = remember(full, filtered) { InspectedCard(filtered,
            faces.map { face -> full.artwork[definition.faces.indexOf(face)] }) }
        val marks = inspection.stackEntryId?.let { entry -> state.board.stackTextMarks[entry].orEmpty().sorted().mapNotNull { index ->
            StackText.options(state, entry).getOrNull(index)?.takeIf { it.faceId in allowed }?.let { index to it }
        } }.orEmpty()
        val controller = remember(inspection) { CardInspectionController().apply {
            open(permitted)
            marks.firstOrNull()?.second?.faceId?.let { id -> faces.indexOfFirst { it.faceId == id }.takeIf { it >= 0 }?.let(::face) }
        } }
        LaunchedEffect(permitted) { if (controller.state?.card != permitted) controller.open(permitted) }
        if (controller.state != null) CardInspection(controller,
            listOfNotNull(
                "Provisional inspection · awaiting shared result".takeIf { inspection.provisional },
                "Owner ${names[card.owner] ?: "Player"} · ${if (card.tapped) "tapped" else "untapped"}",
                card.corner.takeIf(String::isNotBlank),
                card.counters.entries.joinToString(" · ") { "${it.key.name.lowercase()}: ${it.value}" }.takeIf(String::isNotBlank),
                card.note.takeIf(String::isNotBlank)).joinToString("\n"),
            markedOptions = marks,
            token = card.token, owner = if (card.owner == viewer) OwnTableColor else OpponentTableColor,
            projection = inspection.cardId in CombatStackRules.projectionIds(state.board), peek = peek, peekScroll = peekScroll)
        // Inner dialog's native Back/outside/close must also release the retained outer route.
        LaunchedEffect(controller.state) { if (controller.state == null) close() }
    }
}
