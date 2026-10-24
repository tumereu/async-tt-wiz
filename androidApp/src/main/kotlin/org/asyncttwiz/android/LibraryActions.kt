package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import org.asyncttwiz.protocol.*

/** Reusable production controls. I16 embeds these at the bound Library, not in Table actions. */
@Composable
fun LibraryActions(controller: LibraryController, board: BoardState, displayedHand: List<String>, modifier: Modifier = Modifier,
    onComplete: () -> Unit = {},
    onShow: ((ContentsSelection, Int, Boolean) -> Unit)? = null) {
    var quantity by remember(controller) { mutableIntStateOf(1) }
    var bottom by remember(controller) { mutableStateOf(false) }
    val selection = if (bottom) ContentsSelection.BOTTOM else ContentsSelection.TOP
    val enabled = controller.available()
    fun finish(result: LibrarySubmission) { if (result.refused == 0 && result.submitted > 0) onComplete() }
    fun show(slice: ContentsSelection, count: Int, reveal: Boolean = false) {
        if (onShow != null) onShow(slice, count, reveal) else finish(controller.show(slice, count, reveal))
    }
    val destinations = libraryDestinations(board, sourceContainerId = controller.libraryId).map { it.second }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            LibraryButton("Draw 1", Modifier.weight(1f), enabled) { finish(controller.draw()) }
            LibraryButton("Search", Modifier.weight(1f), enabled) { show(ContentsSelection.ALL, 1) }
            LibraryButton("Reveal all", Modifier.weight(1f), enabled) { show(ContentsSelection.ALL, 1, true) }
            LibraryButton("Shuffle", Modifier.weight(1f), enabled) { finish(controller.shuffle()) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            LibraryQuantity(quantity, { quantity = it })
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                LibraryButton("Top", Modifier.weight(1f), selected = !bottom) { bottom = false }
                LibraryButton("Bottom", Modifier.weight(1f), selected = bottom) { bottom = true }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            LibraryButton("Draw N", Modifier.weight(1f), enabled) { finish(controller.draw(quantity, selection)) }
            LibraryButton("Look at N", Modifier.weight(1f), enabled) { show(selection, quantity) }
            LibraryButton("Reveal N", Modifier.weight(1f), enabled) { show(selection, quantity, true) }
        }
        Text("Move N to:", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (zone in listOf(PlayZone.GRAVEYARD, PlayZone.EXILE, PlayZone.HAND, PlayZone.BATTLEFIELD)) {
                val destination = destinations.firstOrNull { d -> board.containers.any { it.id == d.containerId && it.zone == zone } }
                LibraryButton(moveZoneLabel(zone), Modifier.weight(1f), enabled && destination != null) {
                    destination?.let { finish(controller.move(selection, quantity, it)) }
                }
            }
        }
        Text("Mulligan (hand → shuffle → draw)", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            LibraryButton("Draw 7", Modifier.weight(1f), enabled) { finish(controller.mulligan(displayedHand, 7)) }
            LibraryButton("Draw one fewer", Modifier.weight(1f), enabled) { finish(controller.mulligan(displayedHand, minusOne = true)) }
        }
    }
}

@Composable
private fun LibraryButton(label: String, modifier: Modifier = Modifier, enabled: Boolean = true,
    selected: Boolean = false, click: () -> Unit) {
    Surface(onClick = click, enabled = enabled,
        modifier = modifier.height(48.dp).semantics { contentDescription = label; this.selected = selected },
        shape = RoundedCornerShape(3.dp), color = if (selected) OwnTableColor.copy(alpha = .12f) else Color.White,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) OwnTableColor else MenuBorder)) {
        Column(Modifier.fillMaxSize().padding(2.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            val ink = MenuInk.copy(alpha = if (enabled) 1f else .38f)
            MenuIcon(label, tint = ink)
            Text(label, color = ink, fontSize = 10.sp, lineHeight = 12.sp, maxLines = 1, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun LibraryQuantity(value: Int, onValue: (Int) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    Row(Modifier.border(1.dp, MenuBorder, RoundedCornerShape(4.dp)), verticalAlignment = Alignment.CenterVertically) {
        ManualActionSquare("Decrease N", "−", value > 0, bordered = false, onClick = { onValue(value - 1) })
        ManualActionSquare("Set N", value.toString(), bordered = false, onClick = { text = value.toString(); editing = true })
        ManualActionSquare("Increase N", "+", value < Int.MAX_VALUE, bordered = false, onClick = { onValue(value + 1) })
    }
    if (editing) TableMenuPanel({ editing = false }, title = "Number of cards") {
        OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
            isError = text.toIntOrNull()?.let { it >= 0 } != true)
        MenuButton("Set", enabled = text.toIntOrNull()?.let { it >= 0 } == true) {
            text.toIntOrNull()?.takeIf { it >= 0 }?.let { onValue(it); editing = false }
        }
    }
}

@Composable
internal fun LibraryDestinationPicker(board: BoardState, selected: MoveDestination?, onSelect: (MoveDestination) -> Unit,
    names: Map<String, String> = emptyMap(), sourceIds: List<String> = emptyList(), sourceContainerId: String? = null) {
    var open by remember { mutableStateOf(false) }
    val choices = libraryDestinations(board, names, sourceIds, sourceContainerId)
    Box {
        MenuButton(choices.firstOrNull { it.second == selected }?.first ?: "Destination", Modifier.fillMaxWidth()) { open = true }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            choices.forEach { (label, destination) -> DropdownMenuItem(text = { Text(label) },
                onClick = { onSelect(destination); open = false }) }
        }
    }
}

internal fun libraryDestinations(board: BoardState, names: Map<String, String> = emptyMap(),
    sourceIds: List<String> = emptyList(), sourceContainerId: String? = null): List<Pair<String, MoveDestination>> {
    val owner = sourceIds.firstOrNull()?.let { CombatStackRules.liveSource(board, it)?.owner }
        ?: board.containers.singleOrNull { it.id == sourceContainerId }?.accountId ?: return emptyList()
    return board.containers.filter { it.accountId == owner || it.zone == PlayZone.STACK }.flatMap { container ->
        val label = moveZoneLabel(container.zone)
        when (container.zone) {
            PlayZone.LIBRARY -> listOf("Library top" to MoveDestination(container.id, MoveInsertion.TOP),
                "Library bottom" to MoveDestination(container.id, MoveInsertion.BOTTOM))
            PlayZone.HAND, PlayZone.GRAVEYARD, PlayZone.EXILE -> listOf(label to MoveDestination(container.id, MoveInsertion.UNORDERED))
            PlayZone.STACK -> if (sourceIds.isNotEmpty() && sourceIds.all { id -> board.cards.singleOrNull { it.id == id }?.let {
                    CombatStackRules.canProject(board, it) } == true }) listOf(label to MoveDestination(container.id, MoveInsertion.TOP)) else emptyList()
            PlayZone.BATTLEFIELD -> {
                val column = board.cards.filter { it.containerId == container.id }.mapNotNull { it.placement?.position?.x }.maxOrNull()?.plus(1) ?: 0
                listOf(label to MoveDestination(container.id, MoveInsertion.EMPTY, BoardPosition(column, 0)))
            }
        }
    }
}
