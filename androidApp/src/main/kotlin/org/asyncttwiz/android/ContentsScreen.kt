package org.asyncttwiz.android

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import org.asyncttwiz.protocol.*

data class ContentsEntry(val cardId: String, val name: String, val category: String, val location: String,
    val bottommost: Boolean = false, val faceVisible: Boolean = true)

/** Live personal browsing; unlike ContentsDisplay this is not a shared disclosure or fixed tray. */
data class PileContents(val containerId: String, val title: String, val cardIds: List<String>, val ordered: Boolean = false)

private fun contentsCategory(type: String) = listOf("Creature", "Land", "Artifact", "Enchantment", "Planeswalker", "Instant", "Sorcery", "Battle")
    .firstOrNull { type.contains(it, ignoreCase = true) } ?: "Other"

private val contentsOrder = compareBy<ContentsEntry>({ it.category }, { it.name.lowercase() }, { it.cardId })

fun contentsEntries(state: PlayState, pile: PileContents, viewer: String, search: String = ""): List<ContentsEntry> {
    val entries = pile.cardIds.mapIndexed { index, id ->
        val card = CombatStackRules.inspection(state.board, id)
        val visible = card != null && CardVisibility.isVisibleFaceUp(state.board, card, viewer)
        val face = if (visible) state.definitions.singleOrNull { it.digest == card.definition }?.definition?.faces
            ?.singleOrNull { it.faceId == card.faceId } else null
        val effect = state.board.stackEffects.singleOrNull { it.id == id }
        val location = card?.let { c -> state.board.containers.singleOrNull { it.id == c.containerId }?.zone?.name?.lowercase() } ?: "Unavailable"
        ContentsEntry(id, if (visible) face?.name ?: "Unavailable card" else "Card back",
            if (visible) contentsCategory(face?.typeLine.orEmpty()) else "Cards",
            (if (pile.ordered) "${index + 1} · " else "") + location +
                when { effect?.frozen != null -> " · Retained effect"; effect != null -> " · Effect"; else -> "" },
            faceVisible = visible)
    }.filter { !it.faceVisible || it.name.contains(search, ignoreCase = true) }
    return if (pile.ordered) entries else entries.sortedWith(contentsOrder)
}

/** Permission-aware presentation: backs contain no definition-derived names, types or search matches. */
fun contentsEntries(state: PlayState, display: ContentsDisplay, viewer: String, search: String = ""): List<ContentsEntry> {
    val visible = display.facesVisibleTo(viewer)
    val entries = display.cardIds.mapIndexed { index, id ->
        val card = state.board.cards.singleOrNull { it.id == id }
        val face = if (visible) state.definitions.singleOrNull { it.digest == card?.definition }?.definition?.faces
            ?.singleOrNull { it.faceId == card?.faceId } else null
        val type = face?.typeLine.orEmpty()
        val category = if (!visible) "Cards" else contentsCategory(type)
        ContentsEntry(id, if (visible) face?.name ?: "Unavailable card" else "Card back", category,
            card?.let { c -> state.board.containers.singleOrNull { it.id == c.containerId }?.zone?.name?.lowercase() } ?: "Unavailable",
            display.selection == ContentsSelection.BOTTOM && index == display.cardIds.lastIndex, faceVisible = visible)
    }
    // Covered rows must not be removed/reordered by a hidden name query.
    val filtered = if (visible) entries.filter { it.name.contains(search, ignoreCase = true) } else entries
    return if (display.selection == ContentsSelection.ALL) filtered.sortedWith(contentsOrder) else filtered
}

@Composable
fun ContentsScreen(state: PlayState, pile: PileContents, viewer: String, onActions: (String) -> Unit,
    onInspect: (String) -> Unit, modifier: Modifier = Modifier, onRotate: ((String) -> Unit)? = null,
    onStackIntent: ((TableIntent) -> Boolean)? = null, stackWritable: Boolean = false) {
    var search by remember(pile.containerId) { mutableStateOf("") }
    var bubbleId by remember(pile.containerId) { mutableStateOf<String?>(null) }
    var pendingAdvance by remember(pile.containerId) { mutableStateOf<Pair<String, String>?>(null) }
    var root by remember { mutableStateOf(Rect.Zero) }
    val rows = remember(pile.containerId) { mutableStateMapOf<String, Rect>() }
    val density = LocalDensity.current.density
    val stack = pile.ordered && onStackIntent != null
    val entries = contentsEntries(state, pile, viewer, search)
    val callback by rememberUpdatedState(onStackIntent)
    val dispatch by rememberUpdatedState<(TableIntent) -> Unit>({ intent ->
        if (intent is TableIntent.StackBubbles) { pendingAdvance = null; bubbleId = intent.entryId }
        else {
            val advance = stackBubbleAdvanceEntry(intent, state.board)
            if (intent is TableIntent.StackDestination || intent is TableIntent.StackBubbleRemove) bubbleId = null
            if (callback?.invoke(intent) == true && advance != null) pendingAdvance = advance to state.currentNodeId
        }
    })
    LaunchedEffect(state, pendingAdvance) {
        pendingAdvance?.let { (entry, node) ->
            val order = CombatStackRules.stack(state.board).cards
            if (entry !in order) bubbleId = order.firstOrNull()
            if (entry !in order || node != state.currentNodeId) pendingAdvance = null
        }
    }
    val coordinator = remember(pile.containerId, density) { TableGestureCoordinator({ intent ->
        dispatch(intent)
    }, density = density) }
    var preview by remember(coordinator) { mutableStateOf(TableGesturePreview()) }
    DisposableEffect(coordinator) { onDispose { coordinator.cancel() } }
    BoxWithConstraints(modifier.onGloballyPositioned { root = it.boundsInRoot() }) {
        val anchor = rows[bubbleId]?.takeIf { bubbleId in entries.map { it.cardId } && it.width > 0 && it.height > 0 }
            ?.let { TableBounds((it.left - root.left) / density, (it.top - root.top) / density, it.width / density, it.height / density) }
        val bubble = if (stack && anchor != null) StackBubbleGeometry.create(requireNotNull(bubbleId), anchor,
            maxWidth.value, maxHeight.value, state.board.stackTextMarks[bubbleId]?.minOrNull(), stackTextOptions(state, requireNotNull(bubbleId), viewer).size)
            .copy(multipleMarks = state.board.stackTextMarks[bubbleId].orEmpty().size > 1) else null
        val currentBubble by rememberUpdatedState(bubble)
        SideEffect {
            // The same production coordinator owns the bubble taps and mark-text drag as on the table.
            val item = bubble?.let { TableItem("contents/${it.entryId}", it.anchor, "Stack entry", "",
                zone = PlayZone.STACK, kind = TableItemKind.CARD, cardId = it.entryId) }
            coordinator.update(TableScene(listOfNotNull(item), emptyList(), stackBubbles = bubble), TableSelection(), stackWritable)
        }
        Box(Modifier.fillMaxSize().pointerInput(coordinator) {
            try {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val point = coordinator.pointFromPixels(down.position.x, down.position.y)
                    if (currentBubble?.controls?.any { it.bounds.contains(point) } != true) {
                        pendingAdvance = null
                        bubbleId = null // No scrim: outside touches continue to rows, search and scrolling.
                    } else {
                        coordinator.down(point, down.uptimeMillis); down.consume()
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null || event.changes.count { it.pressed } > 1) coordinator.cancel()
                            else {
                                val p = coordinator.pointFromPixels(change.position.x, change.position.y)
                                if (change.pressed) coordinator.move(p, change.uptimeMillis) else coordinator.up(p, change.uptimeMillis)
                            }
                            preview = coordinator.preview
                            event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
                    }
                }
            } finally { coordinator.cancel() }
        }) {
            Column(Modifier.fillMaxSize()) {
                OutlinedTextField(search, { search = it; bubbleId = null; pendingAdvance = null }, Modifier.fillMaxWidth(), label = { Text("Search by name") }, singleLine = true)
                ContentsRows(state, viewer, entries, !pile.ordered,
                    onActions = { if (stack) { if (it == pile.cardIds.firstOrNull()) bubbleId = it } else onActions(it) }, onInspect = onInspect,
                    modifier = Modifier.weight(1f), onRotate = if (stack) { id -> bubbleId = null; onActions(id) } else onRotate,
                    onRowBounds = { id, bounds -> rows[id] = bounds })
            }
            if (bubble != null) StackBubbleOverlay(bubble, stackTextOptions(state, bubble.entryId, viewer),
                if (CombatStackRules.inspection(state.board, bubble.entryId)?.owner == viewer) OwnTableColor else OpponentTableColor,
                preview, stackWritable) { intent ->
                dispatch(intent)
            }
        }
    }
}

/** Rendering never issues View/inspection. The adapter receives exact bound contexts only after gestures. */
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
        ContentsRows(state, viewer, entries, display.selection == ContentsSelection.ALL,
            onActions = { selected = it; onActions?.invoke(it) },
            onInspect = { controller.inspection(state, it, viewer, hold = true,
                displayFaceVisible = display.facesVisibleTo(viewer))?.let(onInspect) }, modifier = Modifier.weight(1f))
        selected?.let { id ->
            LibraryDestinationPicker(state.board, destination, { destination = it }, names, sourceIds = listOf(id))
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

/** Both public piles and logged Library displays share search results, columns and hold targets. */
@Composable
private fun ContentsRows(state: PlayState, viewer: String, entries: List<ContentsEntry>, grouped: Boolean, onActions: (String) -> Unit,
    onInspect: (String) -> Unit, modifier: Modifier = Modifier, onRotate: ((String) -> Unit)? = null,
    onRowBounds: ((String, Rect) -> Unit)? = null) {
    if (entries.isEmpty()) { Box(modifier.padding(8.dp)) { Text("No cards") }; return }
    var peek by remember { mutableStateOf<ManualInspection?>(null) }
    val peekScroll = rememberScrollState()
    val groups = if (grouped) entries.groupBy { it.category } else mapOf("" to entries)
    Row(modifier.horizontalScroll(rememberScrollState())) {
        groups.forEach { (category, cards) ->
            Column(Modifier.width(220.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(8.dp)) {
                if (category.isNotEmpty()) Text(category, style = MaterialTheme.typography.labelMedium)
                cards.forEach { entry ->
                    Row(Modifier.fillMaxWidth().onGloballyPositioned { onRowBounds?.invoke(entry.cardId, it.boundsInRoot()) }
                        .contentsCardGestures(entry.cardId, state.board.containers.firstOrNull {
                            entry.cardId in it.cards }?.zone ?: PlayZone.STACK, actions = { onActions(entry.cardId) },
                            double = onRotate?.let { rotate -> { rotate(entry.cardId) } }, inspect = { onInspect(entry.cardId) }, peek = { held ->
                                if (held) peekScroll.dispatchRawDelta(-peekScroll.value.toFloat())
                                peek = if (!held) null else CombatStackRules.inspection(state.board, entry.cardId)?.let { card ->
                                    val faces = if (entry.faceVisible) listOf(card.faceId) else
                                        ManualDisclosure.permittedFaces(state, card, viewer, inspecting = true).map { it.faceId }
                                    ManualInspection(state.gameId, state.id, entry.cardId, faces, explicit = entry.faceVisible,
                                        stackEntryId = entry.cardId.takeIf { it in CombatStackRules.stack(state.board).cards })
                                }
                            }, scrollPeek = { peekScroll.dispatchRawDelta(it) }).padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val card = CombatStackRules.inspection(state.board, entry.cardId)
                        val definition = state.definitions.singleOrNull { it.digest == card?.definition }?.definition
                        val face = definition?.faces?.indexOfFirst { it.faceId == card?.faceId } ?: -1
                        val art = if (entry.faceVisible && definition != null && face >= 0)
                            InspectedCard.retained(requireNotNull(card).definition, definition).artwork[face] else null
                        val owner = if (card?.owner == viewer) OwnTableColor else OpponentTableColor
                        Box(Modifier.size(40.dp, 56.dp).cardFrame(card?.token == true, owner,
                            entry.cardId in CombatStackRules.projectionIds(state.board)), contentAlignment = Alignment.Center) {
                            if (art != null) CardArtwork(art, entry.name, 80, 112, Modifier.fillMaxSize())
                            else MenuIcon(if (entry.faceVisible) "Inspect" else "Turn face down")
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
    peek?.let { GameplayCardInspection(state, viewer, it, { peek = null }, peek = true, peekScroll = peekScroll) }
}
