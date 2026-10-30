package org.asyncttwiz.android

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.asyncttwiz.protocol.*
import kotlin.math.roundToInt
import org.asyncttwiz.protocol.PodGridView as GridViewData

/** Selection is personal UI state; neither the board nor the pool predicts a pick. */
internal class PodGridRules(val board: GridPublicBoard, mine: GridPrivateView) {
    val myTurn = board.currentPlayer == mine.player && board.id == mine.headGridId

    fun positions(line: GridLine): List<Int> = line.positions()

    fun canSelect(line: GridLine) = myTurn && positions(line).any { board.positions.getOrNull(it) != null }
    fun highlighted(position: Int, line: GridLine?) = line != null && canSelect(line) && position in positions(line)
}

/** Picker colours: yours as at the table, then one per other member in seating order. */
internal fun podGridColors(seating: List<String>, me: String): Map<String, Color> {
    val others = listOf(OpponentTableColor, Color(0xff3457b3), Color(0xffb7791f))
    return (listOf(me to OwnTableColor) + seating.filter { it != me }.mapIndexed { index, player -> player to others[index % others.size] })
        .toMap()
}

/** One grid card: shown in place as dealt, its picker, whether it was discarded, whether it is a refill card. */
internal data class PodGridCell(val copy: DraftCopy?, val pickedBy: String?, val discarded: Boolean, val refill: Boolean)

/**
 * Slots from current servers; an older server sends only the live board. [latest] is the grid's newest board (see
 * [gridBoardStages]): only there are a finished grid's unpicked cards discarded; a board before a refill had none.
 */
internal fun podGridCells(board: GridPublicBoard, slots: List<GridPublicSlot> = board.slots, latest: Boolean = true): List<PodGridCell> =
    if (slots.size == 9) slots.map {
        PodGridCell(it.copy, it.pickedBy, latest && board.finished && it.pickedBy == null && it.copy != null, it.refill)
    }
    else List(9) { PodGridCell(board.positions.getOrNull(it), null, false, false) }

/**
 * The picks whose line band passes under [position], in drawing order: the latest pick first, so each earlier pick's
 * band lies on top of later ones (where lines cross, the card went to the earlier pick, whose band frames it).
 */
internal fun podGridBands(picks: List<GridPublicPickLog>, position: Int): List<GridPublicPickLog> =
    picks.filter { pick -> pick.line?.positions()?.contains(position) == true }.asReversed()

/** Sub-label of one of a refilled grid's boards; null for a grid with one board. */
internal fun podGridStageLabel(index: Int, count: Int): String? = when {
    count < 2 -> null
    index == 0 -> "Before refill"
    count == 2 -> "After refill"
    else -> "After refill $index"
}

/** Your current pick first, then the other open grids in number order, then finished grids (as the server orders them). */
internal fun podGridOrder(view: GridViewData): List<GridPublicBoard> {
    val head = view.publicView.grids.firstOrNull { it.id == view.mine.headGridId && it.currentPlayer == view.mine.player }
    return listOfNotNull(head) + view.publicView.grids.filter { it != head }.sortedBy { it.number } + view.publicView.finishedGrids
}

internal fun podGridHeader(board: GridPublicBoard, view: GridViewData, totalGrids: Int, members: List<AccountIdentity>): String {
    val me = view.mine.player
    val status = when {
        board.finished -> "Finished"
        board.currentPlayer == me && board.id == view.mine.headGridId -> "Your pick"
        board.currentPlayer == me -> "Your pick next"
        else -> "Waiting for " + (members.find { it.accountId == board.currentPlayer }?.username ?: "other players")
    }
    return "$status · Grid ${board.number}/$totalGrids"
}

@Composable
fun PodGridView(
    view: GridViewData?,
    totalGrids: Int,
    definitions: Map<String, CardDefinition>,
    members: List<AccountIdentity>,
    busy: Boolean,
    onConfirm: (gridId: String, line: GridLineType, index: Int) -> Unit,
    modifier: Modifier = Modifier,
    waitingText: String = "Waiting for other players",
    onMainMenu: (() -> Unit)? = null,
    inspection: CardInspectionController = remember { CardInspectionController() },
) {
    val head = view?.publicView?.grids?.firstOrNull { it.id == view.mine.headGridId && it.currentPlayer == view.mine.player }
    val log = view?.publicView?.log.orEmpty()
    // A refill can preserve both the grid id and player: reset on the authoritative board/log too.
    var selected by remember(head, log.count { it.gridId == head?.id }) { mutableStateOf<GridLine?>(null) }
    val rules = if (head != null) PodGridRules(head, view.mine) else null
    var inspected by remember { mutableStateOf<DraftCopy?>(null) }
    val previewScroll = rememberScrollState()
    val colors = view?.let { podGridColors(it.publicView.seating, it.mine.player) }.orEmpty()
    Column(modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        val boards = view?.let(::podGridOrder).orEmpty()
        if (head == null && boards.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            PodPickWaiting(waitingText, onMainMenu)
        } else Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (head == null) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                PodPickWaiting(waitingText, onMainMenu)
            }
            boards.forEach { board -> key(board.id) {
                Text(podGridHeader(board, view!!, totalGrids, members), Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodySmall)
                val stages = remember(board, log) { gridBoardStages(board, log) }
                val boardRules = rules.takeIf { board.id == head?.id }
                // A refilled grid shows each of its boards, oldest first, framed together as one grid.
                val group = if (stages.size > 1) Modifier.fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small).padding(6.dp)
                    else Modifier.fillMaxWidth()
                Column(group.testTag("pod-grid-${board.id}-group"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    stages.forEachIndexed { index, stage ->
                        val latest = index == stages.lastIndex
                        podGridStageLabel(index, stages.size)?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        PodGridBoard(board, stage, latest, if (latest) "pod-grid-${board.id}" else "pod-grid-${board.id}-stage$index",
                            boardRules.takeIf { latest }, boardRules != null, selected.takeIf { head?.id == board.id && latest }, !busy,
                            onSelect = { selected = it }, definitions = definitions, members = members, colors = colors,
                            inspect = { copy, open ->
                                // Several fingers may hold cards: the latest hold shows, and only its release closes.
                                if (open) { previewScroll.dispatchRawDelta(-previewScroll.value.toFloat()); inspected = copy }
                                else if (inspected == copy) inspected = null
                            },
                            inspectScroll = { previewScroll.dispatchRawDelta(it) },
                            // Rows and columns are chosen with the arrows, so a tap on a card is free to inspect it.
                            open = { copy -> inspected = null; inspection.openPodCard(copy.definition, definitions[copy.definition]) })
                    }
                }
            } }
        }
        if (head != null) Button(onClick = {
            selected?.takeIf { !busy && rules?.canSelect(it) == true }?.let { onConfirm(head.id, it.type, it.index) }
        }, enabled = !busy && selected?.let { rules?.canSelect(it) } == true,
            modifier = Modifier.fillMaxWidth()) {
            Text("Confirm")
        }
    }
    inspected?.let { PodPickArtPreview(it, definitions[it.definition], previewScroll) }
    DeckCardInspection(inspection)
}

/** Opacity of the primary tint over the selected row's or column's cards. */
internal const val PodGridHighlightTint = .28f

/** Space around each card where the line bands of picks show: the visible band is this minus [PodGridBandGap]. */
internal val PodGridBandWidth = 5.dp

/** Bands stop this short of a cell's sides across their line, so two adjacent parallel bands stay apart. */
internal val PodGridBandGap = 1.dp

/** The picker badge sits this far down the card: just below a printed card's name bar, at the top-left of its art. */
internal const val PodGridPickerBadgeTop = .115f

/** Discarded cards fade most; while a line is selected the other live cards dim slightly so the selection stands out. */
internal fun podGridArtAlpha(cell: PodGridCell, live: Boolean, highlighted: Boolean, selecting: Boolean) = when {
    cell.discarded -> .35f
    selecting && live && !highlighted -> .7f
    else -> 1f
}

/**
 * One board of a grid ([stage], see [gridBoardStages]) with a band in its picker's colour under each pick's line.
 * Arrows only when [rules] is given (the grid you are picking from now); [arrowSpace] keeps an earlier board of that
 * grid aligned with the arrowed one. [tag] prefixes the test tags.
 */
@Composable
private fun PodGridBoard(board: GridPublicBoard, stage: GridBoardStage, latest: Boolean, tag: String, rules: PodGridRules?,
    arrowSpace: Boolean, selected: GridLine?, enabled: Boolean,
    onSelect: (GridLine) -> Unit, definitions: Map<String, CardDefinition>, members: List<AccountIdentity>,
    colors: Map<String, Color>, inspect: (DraftCopy, Boolean) -> Unit, inspectScroll: (Float) -> Unit = {},
    open: (DraftCopy) -> Unit = {}) {
    val cells = podGridCells(board, stage.slots, latest)
    // Cells touch so bands run unbroken along their line; the cards keep apart by the band space around each.
    Column(Modifier.fillMaxWidth()) {
        if (rules != null) Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(44.dp))
            repeat(3) { column ->
                PodGridArrow(GridLine(GridLineType.COLUMN, column), rules, selected, enabled, Modifier.weight(1f), onSelect)
            }
        }
        repeat(3) { row ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (rules != null) PodGridArrow(GridLine(GridLineType.ROW, row), rules, selected, enabled,
                    Modifier.width(40.dp), onSelect)
                if (rules != null || arrowSpace) Spacer(Modifier.width(if (rules != null) 4.dp else 44.dp))
                repeat(3) { column ->
                    val position = row * 3 + column
                    val bands = podGridBands(stage.picks, position).map { it.line!! to (colors[it.player] ?: OpponentTableColor) }
                    Box(Modifier.weight(1f).drawBehind { drawPodGridBands(bands, row, column) }.padding(PodGridBandWidth)) {
                        PodGridSlot(board, cells[position], position, tag, latest, rules, selected, definitions, members,
                            colors, inspect, inspectScroll, open)
                    }
                }
            }
        }
    }
}

/** Bands passing this cell, bottom first; a band's ends round off at the outer cells of its line. */
private fun DrawScope.drawPodGridBands(bands: List<Pair<GridLine, Color>>, row: Int, column: Int) {
    if (bands.isEmpty()) return
    val gap = PodGridBandGap.toPx()
    val radius = CornerRadius(PodGridBandWidth.toPx() * 1.6f)
    bands.forEach { (line, color) ->
        val horizontal = line.type == GridLineType.ROW
        val along = if (horizontal) column else row
        val start = if (along == 0) radius else CornerRadius.Zero
        val end = if (along == 2) radius else CornerRadius.Zero
        val shape = if (horizontal) RoundRect(0f, gap, size.width, size.height - gap, start, end, end, start)
            else RoundRect(gap, 0f, size.width - gap, size.height, start, start, end, end)
        drawPath(Path().apply { addRoundRect(shape) }, color)
    }
}

@Composable
private fun PodGridSlot(board: GridPublicBoard, cell: PodGridCell, position: Int, tag: String, latest: Boolean,
    rules: PodGridRules?, selected: GridLine?, definitions: Map<String, CardDefinition>, members: List<AccountIdentity>,
    colors: Map<String, Color>, inspect: (DraftCopy, Boolean) -> Unit, inspectScroll: (Float) -> Unit,
    open: (DraftCopy) -> Unit) {
    val copy = cell.copy
    val live = latest && board.positions.getOrNull(position) != null
    val highlighted = rules?.highlighted(position, selected) == true
    val selecting = selected != null && rules?.canSelect(selected) == true
    val picker = cell.pickedBy?.let { colors[it] ?: OpponentTableColor }
    val name = copy?.let { definitions[it.definition]?.name ?: "Card data unavailable" }
    val pickerName = cell.pickedBy?.let { id -> members.find { it.accountId == id }?.username ?: "another player" }
    // Like the pick screen's card face: the art is cropped to fill the clipped slot and the
    // border is drawn over its edge, so the frame hugs the card on all four sides.
    val shape = MaterialTheme.shapes.extraSmall
    // An empty slot is see-through, so a band still shows passing under it.
    Box(Modifier.fillMaxWidth().aspectRatio(1f / 1.4f)
        .testTag("$tag-slot-$position").semantics {
            contentDescription = name?.let { listOfNotNull(it, pickerName?.let { "picked by $it" },
                "discarded".takeIf { cell.discarded }, "new".takeIf { cell.refill }).joinToString(", ") } ?: "Empty slot"
            stateDescription = if (highlighted) "Highlighted" else "Unselected"
        }
        .clip(shape).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .6f))) {
        if (copy != null) {
            val currentInspect by rememberUpdatedState(inspect)
            val currentInspectScroll by rememberUpdatedState(inspectScroll)
            val currentOpen by rememberUpdatedState(open)
            PodGridCard(copy, definitions[copy.definition], Modifier.fillMaxSize()
                .alpha(podGridArtAlpha(cell, live, highlighted, selecting))
                .semantics { onClick("Inspect") { currentOpen(copy); true } }
                .pointerInput(copy) {
                    podPickGestures(onTap = { currentOpen(copy) }, onInspect = { currentInspect(copy, it) },
                        onInspectScroll = { currentInspectScroll(it) })
                })
        }
        if (highlighted && live) Box(Modifier.matchParentSize()
            .background(MaterialTheme.colorScheme.primary.copy(alpha = PodGridHighlightTint)))
        Box(Modifier.matchParentSize().border(if (highlighted && live) 3.dp else 1.dp,
            if (highlighted && live) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape))
        if (copy != null) {
            if (cell.refill) Text("New", Modifier.align(Alignment.BottomStart).padding(3.dp)
                .background(MaterialTheme.colorScheme.tertiary, MaterialTheme.shapes.extraSmall)
                .padding(horizontal = 3.dp), color = MaterialTheme.colorScheme.onTertiary, fontSize = 9.sp,
                lineHeight = 10.sp)
            if (cell.pickedBy != null) {
                val account = members.find { it.accountId == cell.pickedBy }
                // Top-left of the art: clear of the mana cost and the name bar above it, and of New below.
                Box(Modifier.layout { measurable, constraints ->
                    val badge = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                    layout(badge.width, badge.height) {
                        badge.place(3.dp.roundToPx(), (constraints.maxHeight * PodGridPickerBadgeTop).roundToInt())
                    }
                }.testTag("$tag-picker-$position").clearAndSetSemantics {}
                    .size(18.dp).background(picker ?: OpponentTableColor, CircleShape).padding(2.dp)) {
                    if (account != null) Avatar(account, size = 14.dp)
                }
            }
        }
    }
}

@Composable
private fun PodGridArrow(line: GridLine, rules: PodGridRules?, selected: GridLine?, enabled: Boolean,
    modifier: Modifier, onSelect: (GridLine) -> Unit) {
    TextButton({ onSelect(line) }, modifier.height(40.dp).semantics {
        contentDescription = "Select ${if (line.type == GridLineType.ROW) "row" else "column"} ${line.index + 1}"
        this.selected = selected == line
    }, enabled = enabled && rules?.canSelect(line) == true, contentPadding = PaddingValues(0.dp)) {
        Text(if (line.type == GridLineType.ROW) "→" else "↓")
    }
}

@Composable
private fun PodGridCard(copy: DraftCopy, definition: CardDefinition?, modifier: Modifier) {
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
