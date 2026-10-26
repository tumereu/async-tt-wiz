package org.asyncttwiz.android

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.asyncttwiz.protocol.*
import org.asyncttwiz.protocol.PodGridView as GridViewData

/** Selection is personal UI state; neither the board nor the pool predicts a pick. */
internal class PodGridRules(val board: GridPublicBoard, mine: GridPrivateView) {
    val myTurn = board.currentPlayer == mine.player && board.id == mine.headGridId

    fun positions(line: GridLine): List<Int> = if (line.index !in 0..2) emptyList() else when (line.type) {
        GridLineType.ROW -> List(3) { line.index * 3 + it }
        GridLineType.COLUMN -> List(3) { it * 3 + line.index }
    }

    fun canSelect(line: GridLine) = myTurn && positions(line).any { board.positions.getOrNull(it) != null }
    fun highlighted(position: Int, line: GridLine?) = line != null && canSelect(line) && position in positions(line)
}

@Composable
fun PodGridView(
    view: GridViewData?,
    totalGrids: Int,
    definitions: Map<String, CardDefinition>,
    members: List<AccountIdentity>,
    busy: Boolean,
    onConfirm: (gridId: String, line: GridLineType, index: Int) -> Unit,
    onOpenBuild: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val head = view?.publicView?.grids?.firstOrNull { it.id == view.mine.headGridId && it.currentPlayer == view.mine.player }
    val log = view?.publicView?.log.orEmpty()
    // A refill can preserve both the grid id and player: reset on the authoritative board/log too.
    var selected by remember(head, log.count { it.gridId == head?.id }) { mutableStateOf<GridLine?>(null) }
    val rules = if (head != null) PodGridRules(head, view.mine) else null
    val waiting = view?.publicView?.grids.orEmpty().count { it.currentPlayer == view?.mine?.player } - if (head != null) 1 else 0
    val inspection = remember { CardInspectionController() }
    val inspect: (DraftCopy) -> Unit = { copy ->
        definitions[copy.definition]?.takeIf { it.faces.isNotEmpty() }?.let {
            inspection.open(InspectedCard.retained(copy.definition, it))
        }
    }
    Column(modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        if (head == null) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Waiting for ${view?.publicView?.grids.orEmpty().mapNotNull { it.currentPlayer }.distinct()
                .joinToString { player -> members.find { it.accountId == player }?.username ?: player }
                .ifEmpty { "the next grid" }}", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onOpenBuild) { Text("Build") }
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val boards = if (head != null) listOf(head) else view?.publicView?.grids.orEmpty().sortedBy { it.number }
            boards.forEach { board -> key(board.id) {
                val boardRules = view?.let { PodGridRules(board, it.mine) }
                Text("Grid ${board.number}/$totalGrids · +${waiting.coerceAtLeast(0)} waiting",
                    Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
                if (head == null) board.currentPlayer?.let { player ->
                    Text("Waiting for ${members.find { it.accountId == player }?.username ?: player}",
                        style = MaterialTheme.typography.labelMedium)
                }
                PodGridLog(log.filter { it.gridId == board.id }, definitions, members)
                PodGridBoard(board, boardRules, selected.takeIf { head?.id == board.id }, !busy,
                    onSelect = { selected = it }, definitions = definitions, inspect = inspect)
            } }
        }
        if (head != null) Button(onClick = {
            selected?.takeIf { !busy && rules?.canSelect(it) == true }?.let { onConfirm(head.id, it.type, it.index) }
        }, enabled = !busy && selected?.let { rules?.canSelect(it) } == true,
            modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(if (busy) "Confirming…" else "Confirm")
        }
    }
    CardInspection(inspection)
}

@Composable
private fun PodGridLog(log: List<GridPublicPickLog>, definitions: Map<String, CardDefinition>, members: List<AccountIdentity>) {
    if (log.isEmpty()) return
    var expanded by remember(log.first().gridId) { mutableStateOf(false) }
    fun description(entry: GridPublicPickLog): String {
        val name = members.find { it.accountId == entry.player }?.username ?: entry.player
        val line = entry.line ?: return "$name skipped: no cards remaining"
        return "$name took ${if (line.type == GridLineType.ROW) "row" else "column"} ${line.index + 1}: " +
            entry.kept.joinToString { definitions[it.definition]?.name ?: "Card data unavailable" }
    }
    Text(description(log.last()), Modifier.fillMaxWidth().clickable { expanded = !expanded }.semantics {
        contentDescription = if (expanded) "Collapse grid selections" else "Expand grid selections"
    }, style = MaterialTheme.typography.bodySmall, maxLines = if (expanded) Int.MAX_VALUE else 1,
        overflow = TextOverflow.Ellipsis)
    if (expanded) log.dropLast(1).forEach { Text(description(it), style = MaterialTheme.typography.bodySmall) }
}

@Composable
private fun PodGridBoard(board: GridPublicBoard, rules: PodGridRules?, selected: GridLine?, enabled: Boolean,
    onSelect: (GridLine) -> Unit, definitions: Map<String, CardDefinition>, inspect: (DraftCopy) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Spacer(Modifier.width(40.dp))
            repeat(3) { column ->
                PodGridArrow(GridLine(GridLineType.COLUMN, column), rules, selected, enabled, Modifier.weight(1f), onSelect)
            }
        }
        repeat(3) { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                PodGridArrow(GridLine(GridLineType.ROW, row), rules, selected, enabled, Modifier.width(40.dp), onSelect)
                repeat(3) { column ->
                    val position = row * 3 + column
                    val copy = board.positions.getOrNull(position)
                    val highlighted = rules?.highlighted(position, selected) == true
                    Box(Modifier.weight(1f).aspectRatio(1f / 1.4f)
                        .testTag("pod-grid-${board.id}-slot-$position").semantics {
                            contentDescription = copy?.let { definitions[it.definition]?.name ?: "Card data unavailable" } ?: "Empty slot"
                            stateDescription = if (highlighted) "Highlighted" else "Unselected"
                        }
                        .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.extraSmall)
                        .border(if (highlighted) 3.dp else 1.dp,
                            if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            MaterialTheme.shapes.extraSmall).padding(3.dp)) {
                        if (copy != null) PodGridCard(copy, definitions[copy.definition], Modifier.fillMaxSize()
                            .combinedClickable(onClick = {}, onLongClick = { inspect(copy) }))
                    }
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
            placeholder = { PodCardPlaceholder(definition?.name ?: "Card data unavailable") })
    }
}
