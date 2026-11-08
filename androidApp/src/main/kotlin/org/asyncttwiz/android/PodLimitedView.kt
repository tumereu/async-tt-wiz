package org.asyncttwiz.android

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.asyncttwiz.protocol.*

/** A limited engine's move, sent by [PodController.turn]; [table] is Winston's and Winchester's (DRF-156, DRF-158), 0 elsewhere. */
internal typealias PodTurnSender = (action: LimitedAction, turn: Int, card: String?, packId: String?, pile: Int?, table: Int) -> Unit

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
    onMainMenu: (() -> Unit)?) {
    val view = detail.limited
    val members = detail.members.map { it.account }
    when (view?.mode) {
        DraftMode.ROTISSERIE -> PodRotisserieView(view, members, definitions, busy, send, modifier, columns)
        DraftMode.ROCHESTER -> PodRochesterView(view, members, definitions, busy, send, modifier, columns)
        DraftMode.WINSTON -> PodWinstonView(view, members, definitions, busy, send, modifier, columns)
        DraftMode.WINCHESTER -> PodWinchesterView(view, members, definitions, busy, send, modifier, columns)
        else -> Box(modifier, contentAlignment = Alignment.Center) { PodPickWaiting(waitingText, onMainMenu) }
    }
}

/** The held card's Inspect (every face, TBL-104), shared by the limited pick screens. */
@Stable
internal class PodLimitedPreview {
    var held by mutableStateOf<DraftCopy?>(null)
    val scroll = androidx.compose.foundation.ScrollState(0)
    /** One board tile's size: prefetched pictures decode at it (DRF-070). */
    var tileSize by mutableStateOf(IntSize.Zero)
    fun hold(copy: DraftCopy, open: Boolean) {
        // Several fingers may hold cards: the latest hold shows, and only its release closes.
        if (open) { scroll.dispatchRawDelta(-scroll.value.toFloat()); held = copy } else if (held == copy) held = null
    }
}

@Composable
internal fun PodLimitedPreviewHost(preview: PodLimitedPreview, definitions: Map<String, CardDefinition>) {
    preview.held?.let { PodPickArtPreview(it, definitions[it.definition], preview.scroll) }
}

/**
 * One face-up card of a limited board: a taken card stays in place, greyed, with its picker's small avatar (as in Grid,
 * DRF-085); [selected] frames it. Tap [onTap]; holding inspects it.
 */
@Composable
internal fun PodLimitedCard(copy: DraftCopy, definition: CardDefinition?, pickedBy: String?, members: List<AccountIdentity>,
    colors: Map<String, Color>, selected: Boolean, tag: String, preview: PodLimitedPreview, onTap: () -> Unit) {
    val shape = MaterialTheme.shapes.extraSmall
    val name = definition?.name ?: "Card data unavailable"
    val picker = pickedBy?.let { id -> members.firstOrNull { it.accountId == id } }
    val currentTap by rememberUpdatedState(onTap)
    Box(Modifier.fillMaxWidth().aspectRatio(1f / 1.4f).onSizeChanged { preview.tileSize = it }.testTag(tag).semantics {
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
        if (pickedBy != null) PodPickerBadge(picker, colors[pickedBy] ?: OpponentTableColor, "$tag-picker")
    }
}

/** A card the viewer has not seen: its back. It reports its size to [preview], as face-up tiles do. */
@Composable
internal fun PodCardBack(tag: String, preview: PodLimitedPreview? = null) {
    Box(Modifier.fillMaxWidth().aspectRatio(1f / 1.4f).onSizeChanged { preview?.tileSize = it }.testTag(tag).semantics { contentDescription = "Face-down card" }
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

/**
 * DRF-070: every face-up card of a limited board, in the order the screen shows them: the whole Rotisserie pool (search
 * filter or not), every open Rochester pack, and the pile cards of Winston and Winchester. Face-down cards (null) are
 * never included: the app does not know them.
 */
internal fun podLimitedPrefetchCards(view: LimitedView, definitions: Map<String, CardDefinition>): List<DraftCopy> = when (view.mode) {
    DraftMode.ROTISSERIE -> podRotisserieOrder(view.board, definitions).map { it.copy }
    DraftMode.ROCHESTER -> podRochesterOrder(view).flatMap { pack -> pack.slots.map { it.copy } }
    // Every table's piles, in table order (DRF-156, DRF-158).
    DraftMode.WINSTON, DraftMode.WINCHESTER ->
        view.tables.flatMap { it.piles.flatten().filterNotNull() }.ifEmpty { view.piles.flatten().filterNotNull() }
    else -> emptyList()
}

/** Starts the pictures of [view]'s whole board at once, at the board's tile size (DRF-070). */
@Composable
private fun PodLimitedBoardPrefetch(view: LimitedView, definitions: Map<String, CardDefinition>, preview: PodLimitedPreview) {
    val cards = remember(view, definitions) { podLimitedPrefetchCards(view, definitions) }
    PodArtPrefetch(cards, definitions, preview.tileSize)
}

/** Full-width Confirm under a pick screen, enabled with a choice and while nothing is being sent. */
@Composable
internal fun PodLimitedConfirm(text: String, enabled: Boolean, confirm: () -> Unit) {
    Button(confirm, Modifier.fillMaxWidth().testTag("pod-limited-confirm"), enabled = enabled) { ButtonLabel("ready", text) }
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
 * Rotisserie (DRF-152): the whole pool in one scrolling panel with a catalog search field; taken cards are greyed with
 * the picker's avatar. On your turn tap a card, then Confirm.
 */
@Composable
internal fun PodRotisserieView(view: LimitedView, members: List<AccountIdentity>, definitions: Map<String, CardDefinition>,
    busy: Boolean, send: PodTurnSender, modifier: Modifier, columns: PodPickColumns) {
    val mine = view.currentPlayer == view.player
    var selected by remember(view.turn) { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val preview = remember { PodLimitedPreview() }
    PodLimitedBoardPrefetch(view, definitions, preview)
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
                }
            }
        }
        if (mine) PodLimitedConfirm(selected?.let { id -> "Pick " + (view.board.firstOrNull { it.copy.id == id }
            ?.let { definitions[it.copy.definition]?.name } ?: "card") } ?: "Confirm", !busy && selected != null) {
            selected?.let { send(LimitedAction.TAKE_CARD, view.turn, it, null, null, 0) }
        }
    }
    PodLimitedPreviewHost(preview, definitions)
}

/** Rochester packs in view order: those where it is your pick first, then the rest by number. */
internal fun podRochesterOrder(view: LimitedView): List<LimitedPackView> =
    view.packs.sortedWith(compareBy<LimitedPackView> { it.currentPlayer != view.player }.thenBy { it.number })

/** A Rochester pack's header: "Your pick · Pack 4", "Waiting for <name> · Pack 5" or "Finished · Pack 3". */
internal fun podRochesterHeader(pack: LimitedPackView, view: LimitedView, members: List<AccountIdentity>): String =
    "${podTurnText(pack.currentPlayer, view.player, members)} · Pack ${pack.number}"

/**
 * Rochester (DRF-155): every open pack face up in one scrolling view, packs apart under a divider and their header;
 * taken cards stay in place, greyed, with the picker's avatar. Tap a card of a pack where it is your pick, then Confirm.
 */
@Composable
internal fun PodRochesterView(view: LimitedView, members: List<AccountIdentity>, definitions: Map<String, CardDefinition>,
    busy: Boolean, send: PodTurnSender, modifier: Modifier, columns: PodPickColumns) {
    val packs = remember(view) { podRochesterOrder(view) }
    var selected by remember(view.packs.map { it.picks }) { mutableStateOf<Pair<String, String>?>(null) }
    val preview = remember { PodLimitedPreview() }
    PodLimitedBoardPrefetch(view, definitions, preview)
    val colors = remember(view.seating, view.player) { podGridColors(view.seating, view.player) }
    val count = columns.columns(Int.MAX_VALUE)
    Column(modifier.padding(horizontal = 8.dp)) {
        LazyVerticalGrid(GridCells.Fixed(count), Modifier.weight(1f).fillMaxWidth().testTag("pod-rochester-packs"),
            contentPadding = PaddingValues(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "round") {
                Text("Round ${view.round} of ${view.rounds}", Modifier.padding(vertical = 6.dp).testTag("pod-limited-header"),
                    style = MaterialTheme.typography.bodySmall)
            }
            packs.forEachIndexed { index, pack ->
                item(span = { GridItemSpan(maxLineSpan) }, key = "header-${pack.id}") {
                    Column {
                        if (index > 0) HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        Text(podRochesterHeader(pack, view, members), Modifier.testTag("pod-rochester-header-${pack.id}"),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (pack.currentPlayer == view.player) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(pack.slots, key = { "${pack.id}:${it.copy.id}" }) { slot ->
                    val copy = slot.copy
                    val mine = pack.currentPlayer == view.player && slot.pickedBy == null
                    PodLimitedCard(copy, definitions[copy.definition], slot.pickedBy, members, colors,
                        selected == pack.id to copy.id, "pod-rochester-${copy.id}", preview) {
                        if (mine && selected != pack.id to copy.id) selected = pack.id to copy.id
                    }
                }
            }
        }
        if (packs.any { it.currentPlayer == view.player }) {
            val choice = selected
            val name = choice?.let { (_, card) -> view.packs.flatMap { it.slots }.firstOrNull { it.copy.id == card }
                ?.let { definitions[it.copy.definition]?.name } ?: "card" }
            PodLimitedConfirm(name?.let { "Pick $it" } ?: "Confirm", !busy && choice != null) {
                val pack = choice?.let { (id, _) -> view.packs.firstOrNull { it.id == id } } ?: return@PodLimitedConfirm
                send(LimitedAction.TAKE_CARD, pack.picks, choice.second, pack.id, null, 0)
            }
        }
    }
    PodLimitedPreviewHost(preview, definitions)
}

/** Whether the Winston player may pass the pile they look at: the stack still has cards, or a later pile does. */
internal fun podWinstonCanPass(view: LimitedView): Boolean =
    view.stack > 0 || view.piles.drop(view.pile + 1).any { it.isNotEmpty() }

/** "Pile 2 · 3 cards", with who is looking at it during a turn. */
internal fun podPileHeader(index: Int, size: Int, view: LimitedView, members: List<AccountIdentity>): String {
    val base = "Pile ${index + 1} · ${podCardCount(size)}"
    if (view.mode != DraftMode.WINSTON || view.finished || index != view.pile) return base
    val who = if (view.currentPlayer == view.player) "you look at it"
        else (members.firstOrNull { it.accountId == view.currentPlayer }?.username ?: "another player") + " looks at it"
    return "$base · $who"
}
internal fun podCardCount(count: Int) = "$count ${if (count == 1) "card" else "cards"}"

/** Header line of the pile engines: whose turn and what is left in the stack. */
internal fun podPileTurnText(view: LimitedView, members: List<AccountIdentity>): String =
    "${podTurnText(view.currentPlayer, view.player, members)} · Stack: ${podCardCount(view.stack)}"

/** Piles as grid sections: a header line, then the pile's cards (face up or backs), [framed] piles outlined. */
@Composable
private fun PodPiles(view: LimitedView, members: List<AccountIdentity>, definitions: Map<String, CardDefinition>,
    columns: PodPickColumns, preview: PodLimitedPreview, framed: (Int) -> Boolean, tag: String, modifier: Modifier,
    onTap: (pile: Int, copy: DraftCopy) -> Unit, footer: (Int) -> String? = { null }) {
    val colors = remember(view.seating, view.player) { podGridColors(view.seating, view.player) }
    LazyVerticalGrid(GridCells.Fixed(columns.columns(Int.MAX_VALUE)), modifier.testTag("pod-$tag-piles"),
        contentPadding = PaddingValues(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }, key = "turn") {
            Text(podPileTurnText(view, members), Modifier.padding(vertical = 6.dp).testTag("pod-limited-header"),
                style = MaterialTheme.typography.bodySmall)
        }
        view.piles.forEachIndexed { index, pile ->
            item(span = { GridItemSpan(maxLineSpan) }, key = "pile-$index") {
                Column {
                    if (index > 0) HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    Text(podPileHeader(index, pile.size, view, members), Modifier.testTag("pod-$tag-pile-$index"),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (framed(index)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    footer(index)?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            if (pile.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }, key = "empty-$index") {
                Text("Empty", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            itemsIndexed(pile, key = { position, _ -> "pile-$index-$position" }) { position, copy ->
                if (copy == null) PodCardBack("pod-$tag-back-$index-$position", preview)
                else PodLimitedCard(copy, definitions[copy.definition], null, members, colors, framed(index),
                    "pod-$tag-${copy.id}", preview) { onTap(index, copy) }
            }
        }
    }
}

/** The [index]-th Winston or Winchester table as a one-table view (DRF-156, DRF-158): its turn, stack, piles and pile in place of the first's. */
internal fun LimitedView.table(index: Int): LimitedView = tables.getOrNull(index)?.let { table ->
    copy(currentPlayer = table.currentPlayer, turn = table.turn, stack = table.stack, piles = table.piles, pile = table.pile)
} ?: this

/**
 * A table chip: "Table 1 · Your pick", "Table 2 · Bob picks" (Winston: "Bob looks", as its piles say, DRF-157) or
 * "Table 3 · Finished".
 */
internal fun podTableChipText(index: Int, table: LimitedTableView, me: String, members: List<AccountIdentity>,
    mode: DraftMode = DraftMode.WINCHESTER): String =
    "Table ${index + 1} · " + when (table.currentPlayer) {
        null -> "Finished"
        me -> "Your pick"
        else -> (members.firstOrNull { it.accountId == table.currentPlayer }?.username ?: "Another player") +
            if (mode == DraftMode.WINSTON) " looks" else " picks"
    }

/** The first table where it is [me]'s pick, or null. */
internal fun podFirstTableToPick(view: LimitedView, me: String = view.player): Int? =
    view.tables.indexOfFirst { it.currentPlayer == me }.takeIf { it >= 0 }

/**
 * Which table a pile engine's screen shows (DRF-156, DRF-158, DRF-159): it opens on a table where it is your pick and,
 * after your turn at a table ends, moves to one as soon as there is one; a tap on a chip first keeps the table chosen.
 */
@Stable
internal class PodTableChoice(shown: Int) {
    var shown by mutableIntStateOf(shown)
    /** The table and turn of your last move, until the screen has moved on after it. */
    var acted by mutableStateOf<Pair<Int, Int>?>(null)
    fun index(view: LimitedView): Int = shown.coerceIn(0, (view.tables.size - 1).coerceAtLeast(0))
    fun choose(index: Int) { acted = null; shown = index }
}

@Composable
private fun rememberPodTableChoice(view: LimitedView): PodTableChoice {
    val choice = rememberSaveable(saver = androidx.compose.runtime.saveable.Saver<PodTableChoice, Int>({ it.shown }, { PodTableChoice(it) })) {
        PodTableChoice(podFirstTableToPick(view) ?: 0)
    }
    LaunchedEffect(view) {
        val (index, turn) = choice.acted ?: return@LaunchedEffect
        if ((view.tables.getOrNull(index)?.turn ?: return@LaunchedEffect) <= turn) return@LaunchedEffect
        podFirstTableToPick(view)?.let { choice.acted = null; choice.shown = it }
    }
    return choice
}

/** The chip row of several tables (one table shows none), tagged "pod-<tag>-tables" and "pod-<tag>-table-N". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PodTableChips(view: LimitedView, members: List<AccountIdentity>, choice: PodTableChoice, tag: String) {
    if (view.tables.size <= 1) return
    val index = choice.index(view)
    FlowRow(Modifier.fillMaxWidth().testTag("pod-$tag-tables"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        view.tables.forEachIndexed { i, entry ->
            FilterChip(i == index, { choice.choose(i) }, label = { Text(podTableChipText(i, entry, view.player, members, view.mode)) },
                modifier = Modifier.testTag("pod-$tag-table-$i"))
        }
    }
}

/**
 * Winston (DRF-157): the piles, cards you have seen face up and the rest as backs, and the stack's count. On your turn
 * you look at one pile at a time: take it, or pass (it gets one more card) and look at the next; passing the last pile
 * takes the top card of the stack. With several tables (DRF-156) the chip row shows one table at a time, as Winchester's.
 */
@Composable
internal fun PodWinstonView(view: LimitedView, members: List<AccountIdentity>, definitions: Map<String, CardDefinition>,
    busy: Boolean, send: PodTurnSender, modifier: Modifier, columns: PodPickColumns) {
    val choice = rememberPodTableChoice(view)
    val index = choice.index(view)
    val table = view.table(index)
    val mine = table.currentPlayer == view.player && !view.finished
    val preview = remember { PodLimitedPreview() }
    PodLimitedBoardPrefetch(view, definitions, preview)
    Column(modifier.padding(horizontal = 8.dp)) {
        PodTableChips(view, members, choice, "winston")
        PodPiles(table, members, definitions, columns, preview, { mine && it == table.pile }, "winston", Modifier.weight(1f).fillMaxWidth(),
            onTap = { _, _ -> })
        if (mine) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val last = table.piles.drop(table.pile + 1).none { it.isNotEmpty() }
            fun act(action: LimitedAction) { choice.acted = index to table.turn; send(action, table.turn, null, null, table.pile, index) }
            OutlinedButton({ act(LimitedAction.PASS_PILE) }, Modifier.weight(1f).testTag("pod-winston-pass"),
                enabled = !busy && podWinstonCanPass(table)) { ButtonLabel("pass", if (last) "Pass · take top card" else "Pass") }
            Button({ act(LimitedAction.TAKE_PILE) }, Modifier.weight(1f).testTag("pod-winston-take"),
                enabled = !busy && table.piles.getOrNull(table.pile)?.isNotEmpty() == true) { ButtonLabel("take", "Take pile ${table.pile + 1}") }
        }
    }
    PodLimitedPreviewHost(preview, definitions)
}

/**
 * Winchester (DRF-159): every pile face up with the stack's count. On your turn tap a card of a pile to choose that
 * pile, then "Take pile N". With several tables (DRF-158) a chip row on top shows one table at a time, opening on (and
 * moving to, after you take a pile) a table where it is your pick.
 */
@Composable
internal fun PodWinchesterView(view: LimitedView, members: List<AccountIdentity>, definitions: Map<String, CardDefinition>,
    busy: Boolean, send: PodTurnSender, modifier: Modifier, columns: PodPickColumns) {
    val choice = rememberPodTableChoice(view)
    val index = choice.index(view)
    val table = view.table(index)
    val mine = table.currentPlayer == view.player && !view.finished
    var chosen by remember(index, table.turn) { mutableStateOf<Int?>(null) }
    val preview = remember { PodLimitedPreview() }
    PodLimitedBoardPrefetch(view, definitions, preview)
    Column(modifier.padding(horizontal = 8.dp)) {
        PodTableChips(view, members, choice, "winchester")
        PodPiles(table, members, definitions, columns, preview, { it == chosen }, "winchester", Modifier.weight(1f).fillMaxWidth(),
            onTap = { pile, _ -> if (mine && chosen != pile) chosen = pile })
        if (mine) PodLimitedConfirm(chosen?.let { "Take pile ${it + 1} (${podCardCount(table.piles[it].size)})" } ?: "Confirm",
            !busy && chosen != null) { chosen?.let { choice.acted = index to table.turn; send(LimitedAction.TAKE_PILE, table.turn, null, null, it, index) } }
    }
    PodLimitedPreviewHost(preview, definitions)
}
