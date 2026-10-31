package org.asyncttwiz.android

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import org.asyncttwiz.protocol.*
import kotlin.math.roundToInt

/** A limited engine's move, sent by [PodController.turn]. */
internal typealias PodTurnSender = (action: LimitedAction, turn: Int, card: String?, packId: String?, pile: Int?) -> Unit

/** Who the pod waits for (DRF-071): "Waiting for <names>", Rochester with its round. */
internal fun podLimitedWaitingText(detail: PodDetail, view: LimitedView, accountId: String): String {
    val busy = detail.members.filter { it.packsWaiting > 0 }.map { it.account.accountId }.toSet()
    val names = detail.members.map { it.account }.filter { it.accountId != accountId && it.accountId in busy }.map { it.username }
    val who = if (names.isEmpty()) "Waiting for other players" else "Waiting for ${names.joinToString(", ")}"
    return if (view.mode == DraftMode.ROCHESTER && view.rounds > 0) "$who · Round ${view.round} of ${view.rounds}" else who
}

/** "Your pick" or "Waiting for <name>" for the member whose turn it is. */
internal fun podTurnText(current: String?, me: String, members: List<AccountIdentity>): String = when (current) {
    null -> "Finished"
    me -> "Your pick"
    else -> "Waiting for " + (members.firstOrNull { it.accountId == current }?.username ?: "another player")
}

/** Card search data from a card's definition: no printings, so set and rarity terms match nothing. */
internal fun podSearchCard(definition: CardDefinition): CardSearchCard {
    val faces = definition.faces.map { face ->
        val colors = CardColors.mask(PodDeckManaCost.parse(face.manaCost).colors.map { it.name })
        CardSearchFace(face.name, face.typeLine, face.rulesText, face.manaCost, colors,
            face.power.ifEmpty { null }, face.toughness.ifEmpty { null }, face.loyalty.ifEmpty { null })
    }
    return CardSearchCard(definition.name, definition.layout, faces, faces.fold(0) { mask, face -> mask or face.colors })
}

/** The Draft tab of Rochester, Rotisserie, Winston and Winchester pods. */
@Composable
internal fun PodLimitedDraftView(detail: PodDetail, accountId: String, definitions: Map<String, CardDefinition>,
    busy: Boolean, send: PodTurnSender, modifier: Modifier, columns: PodPickColumns, waitingText: String,
    onMainMenu: (() -> Unit)?, inspection: CardInspectionController) {
    val view = detail.limited
    val members = detail.members.map { it.account }
    when (view?.mode) {
        DraftMode.ROTISSERIE -> PodRotisserieView(view, members, definitions, busy, send, modifier, columns, inspection)
        else -> Box(modifier, contentAlignment = Alignment.Center) { PodPickWaiting(waitingText, onMainMenu) }
    }
}

/** The held card's big picture and the full inspector, shared by the limited pick screens. */
@Stable
internal class PodLimitedPreview {
    var held by mutableStateOf<DraftCopy?>(null)
    val scroll = androidx.compose.foundation.ScrollState(0)
    fun hold(copy: DraftCopy, open: Boolean) {
        // Several fingers may hold cards: the latest hold shows, and only its release closes.
        if (open) { scroll.dispatchRawDelta(-scroll.value.toFloat()); held = copy } else if (held == copy) held = null
    }
}

@Composable
internal fun PodLimitedPreviewHost(preview: PodLimitedPreview, definitions: Map<String, CardDefinition>,
    inspection: CardInspectionController) {
    preview.held?.let { PodPickArtPreview(it, definitions[it.definition], preview.scroll) }
    DeckCardInspection(inspection)
}

/**
 * One face-up card of a limited board: a taken card stays in place, greyed, with its picker's small avatar (as in Grid,
 * DRF-085); [selected] frames it. Tap [onTap]; hold previews the front.
 */
@Composable
internal fun PodLimitedCard(copy: DraftCopy, definition: CardDefinition?, pickedBy: String?, members: List<AccountIdentity>,
    colors: Map<String, Color>, selected: Boolean, tag: String, preview: PodLimitedPreview, onTap: () -> Unit) {
    val shape = MaterialTheme.shapes.extraSmall
    val name = definition?.name ?: "Card data unavailable"
    val picker = pickedBy?.let { id -> members.firstOrNull { it.accountId == id } }
    val currentTap by rememberUpdatedState(onTap)
    Box(Modifier.fillMaxWidth().aspectRatio(1f / 1.4f).testTag(tag).semantics {
        contentDescription = listOfNotNull(name, pickedBy?.let { "picked by ${picker?.username ?: "another player"}" }).joinToString(", ")
        this.selected = selected
        onClick("Select") { currentTap(); true }
    }.clip(shape).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .6f))) {
        PodLimitedArt(copy, definition, Modifier.fillMaxSize().alpha(if (pickedBy != null) .4f else 1f).pointerInput(copy) {
            podPickGestures(onTap = { currentTap() }, onInspect = { preview.hold(copy, it) },
                onInspectScroll = { preview.scroll.dispatchRawDelta(it) })
        })
        if (selected) Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.primary.copy(alpha = PodGridHighlightTint)))
        Box(Modifier.matchParentSize().border(if (selected) 3.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape))
        if (pickedBy != null) Box(Modifier.layout { measurable, constraints ->
            val badge = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
            layout(badge.width, badge.height) {
                badge.place(3.dp.roundToPx(), (constraints.maxHeight * PodGridPickerBadgeTop).roundToInt())
            }
        }.testTag("$tag-picker").clearAndSetSemantics {}.size(18.dp)
            .background(colors[pickedBy] ?: OpponentTableColor, CircleShape).padding(2.dp)) {
            picker?.let { Avatar(it, size = 14.dp) }
        }
    }
}

/** A card the viewer has not seen: its back. */
@Composable
internal fun PodCardBack(tag: String) {
    Box(Modifier.fillMaxWidth().aspectRatio(1f / 1.4f).testTag(tag).semantics { contentDescription = "Face-down card" }
        .clip(MaterialTheme.shapes.extraSmall).background(Color(0xff3b2a1e))
        .border(1.dp, Color(0xff1d140e), MaterialTheme.shapes.extraSmall).padding(5.dp)) {
        Box(Modifier.fillMaxSize().clip(MaterialTheme.shapes.extraSmall).background(Color(0xff6b4a2f)).padding(4.dp)) {
            Box(Modifier.fillMaxSize().clip(CircleShape).background(Color(0xff8a6a3f)))
        }
    }
}

@Composable
internal fun PodLimitedArt(copy: DraftCopy, definition: CardDefinition?, modifier: Modifier) {
    val card = remember(copy.definition, definition) {
        definition?.takeIf { it.faces.isNotEmpty() }?.let { InspectedCard.retained(copy.definition, it) }
    }
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        CardArtwork(card?.artwork?.firstOrNull()?.takeIf { it.printing != null || it.customUrl != null }?.copy(size = "normal"),
            definition?.name ?: "Card data unavailable",
            with(density) { maxWidth.roundToPx() }.coerceIn(1, 4096),
            with(density) { maxHeight.roundToPx() }.coerceIn(1, 4096), Modifier.fillMaxSize(),
            crop = true, placeholder = { PodCardPlaceholder(definition?.name ?: "Card data unavailable") })
    }
}

/** Full-width Confirm under a pick screen, enabled with a choice and while nothing is being sent. */
@Composable
internal fun PodLimitedConfirm(text: String, enabled: Boolean, confirm: () -> Unit) {
    Button(confirm, Modifier.fillMaxWidth().testTag("pod-limited-confirm"), enabled = enabled) { Text(text) }
}

/** Rotisserie cards sort like Build columns: W U B R G, multicolour, colourless, then name. */
internal fun podRotisserieOrder(board: List<LimitedSlot>, definitions: Map<String, CardDefinition>): List<LimitedSlot> {
    val order = PodDeckColumns.comparator { null }
    return board.sortedWith { a, b ->
        val first = definitions[a.copy.definition]
        val second = definitions[b.copy.definition]
        when {
            first == null && second == null -> a.copy.id.compareTo(b.copy.id)
            first == null -> 1
            second == null -> -1
            else -> order.compare(first, second).takeIf { it != 0 } ?: a.copy.id.compareTo(b.copy.id)
        }
    }
}

/** Cards of [board] matching the catalog search [query]; all of them for a blank or unreadable query. */
internal fun podRotisserieFilter(board: List<LimitedSlot>, definitions: Map<String, CardDefinition>, query: String): List<LimitedSlot> {
    if (query.isBlank()) return board
    val parsed = CardSearchQuery.parse(query) as? CardSearchParse.Valid ?: return board
    return board.filter { slot -> definitions[slot.copy.definition]?.let { parsed.query.matches(podSearchCard(it)) } == true }
}

/**
 * Rotisserie (DRF-143): the whole pool in one scrolling panel with a catalog search field; taken cards are greyed with
 * the picker's avatar. On your turn tap a card, then Confirm.
 */
@Composable
internal fun PodRotisserieView(view: LimitedView, members: List<AccountIdentity>, definitions: Map<String, CardDefinition>,
    busy: Boolean, send: PodTurnSender, modifier: Modifier, columns: PodPickColumns, inspection: CardInspectionController) {
    val mine = view.currentPlayer == view.player
    var selected by remember(view.turn) { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val preview = remember { PodLimitedPreview() }
    val colors = remember(view.seating, view.player) { podGridColors(view.seating, view.player) }
    val ordered = remember(view.board, definitions) { podRotisserieOrder(view.board, definitions) }
    val shown = remember(ordered, query, definitions) { podRotisserieFilter(ordered, definitions, query) }
    val error = remember(query) { (CardSearchQuery.parse(query) as? CardSearchParse.Invalid)?.error?.message?.takeIf { query.isNotBlank() } }
    Column(modifier.padding(horizontal = 8.dp)) {
        Text("${podTurnText(view.currentPlayer, view.player, members)} · Pick ${minOf(view.turn + 1, view.turns)} of ${view.turns}",
            Modifier.padding(vertical = 6.dp).testTag("pod-limited-header"), style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().testTag("pod-rotisserie-search"),
            label = { Text("Search cards") }, singleLine = true, isError = error != null,
            supportingText = error?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = hideKeyboardActions())
        LazyVerticalGrid(GridCells.Fixed(columns.columns(Int.MAX_VALUE)), Modifier.weight(1f).fillMaxWidth().testTag("pod-rotisserie-pool"),
            contentPadding = PaddingValues(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (shown.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                Text("No cards match", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(shown, key = { it.copy.id }) { slot ->
                val copy = slot.copy
                PodLimitedCard(copy, definitions[copy.definition], slot.pickedBy, members, colors, selected == copy.id,
                    "pod-rotisserie-${copy.id}", preview) {
                    if (mine && slot.pickedBy == null && selected != copy.id) selected = copy.id
                    else inspection.openPodCard(copy.definition, definitions[copy.definition])
                }
            }
        }
        if (mine) PodLimitedConfirm(selected?.let { id -> "Pick " + (view.board.firstOrNull { it.copy.id == id }
            ?.let { definitions[it.copy.definition]?.name } ?: "card") } ?: "Confirm", !busy && selected != null) {
            selected?.let { send(LimitedAction.TAKE_CARD, view.turn, it, null, null) }
        }
    }
    PodLimitedPreviewHost(preview, definitions, inspection)
}
