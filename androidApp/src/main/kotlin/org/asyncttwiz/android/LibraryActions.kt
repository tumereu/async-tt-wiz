package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.asyncttwiz.protocol.*

/** Reusable production controls. I16 embeds these at the bound Library, not in Table actions. */
@Composable
fun LibraryActions(controller: LibraryController, board: BoardState, displayedHand: List<String>, modifier: Modifier = Modifier,
    initialTool: LibraryTool = LibraryTool.CONTENTS, names: Map<String, String> = emptyMap(), onComplete: () -> Unit = {}) {
    var quantity by remember(controller) { mutableStateOf("1") }
    var mulliganQuantity by remember(controller) { mutableStateOf("7") }
    var bottom by remember(controller) { mutableStateOf(false) }
    var destination by remember(controller) { mutableStateOf<MoveDestination?>(null) }
    val selection = if (bottom) ContentsSelection.BOTTOM else ContentsSelection.TOP
    val count = quantity.toIntOrNull()?.takeIf { it >= 0 }
    val enabled = controller.available()
    fun finish(result: LibrarySubmission) { if (result.refused == 0 && result.submitted > 0) onComplete() }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            ManualActionSquare("Draw one", "", enabled, onClick = { finish(controller.draw()) })
            ManualActionSquare("View contents", "", enabled, onClick = { finish(controller.show()) })
            ManualActionSquare("Reveal contents", "", enabled, onClick = { finish(controller.show(reveal = true)) })
            ManualActionSquare("Shuffle all", "", enabled, onClick = { finish(controller.shuffle()) })
        }
        LibraryQuantity(quantity, { quantity = it }, "Count")
        Row {
            ManualActionSquare("Library top", "", selected = !bottom, onClick = { bottom = false })
            ManualActionSquare("Library bottom", "", selected = bottom, onClick = { bottom = true })
            MenuButton("Draw N", Modifier.weight(1f), enabled = enabled && count != null) { count?.let { finish(controller.draw(it)) } }
        }
        Row {
            MenuButton("View N", Modifier.weight(1f), enabled = enabled && count != null) { count?.let { finish(controller.show(selection, it)) } }
            MenuButton("Reveal N", Modifier.weight(1f), enabled = enabled && count != null) { count?.let { finish(controller.show(selection, it, true)) } }
        }
        LibraryDestinationPicker(board, destination, { destination = it }, names, sourceContainerId = controller.libraryId)
        MenuButton("Move N", Modifier.fillMaxWidth(), enabled = enabled && count != null && destination != null) {
            count?.let { n -> destination?.let { finish(controller.move(selection, n, it)) } }
        }
        LibraryQuantity(mulliganQuantity, { mulliganQuantity = it }, "Shuffle and draw count")
        if (initialTool == LibraryTool.MULLIGAN_N) Text("Return captured Hand · choose draw count")
        MenuButton("Return Hand → Shuffle → Draw N", Modifier.fillMaxWidth(),
            enabled = enabled && mulliganQuantity.toIntOrNull()?.let { it >= 0 } == true) {
            mulliganQuantity.toIntOrNull()?.let { finish(controller.mulligan(displayedHand, it)) }
        }
        MenuButton("Return Hand → Shuffle → Draw Hand size −1", Modifier.fillMaxWidth(), enabled = enabled) {
            finish(controller.mulligan(displayedHand, minusOne = true))
        }
    }
}

@Composable
private fun LibraryQuantity(value: String, onValue: (String) -> Unit, label: String) {
    Row(Modifier.fillMaxWidth()) {
        ManualActionSquare("Decrease $label", "", onClick = { onValue(((value.toIntOrNull() ?: 0) - 1).coerceAtLeast(0).toString()) })
        OutlinedTextField(value, onValue, Modifier.weight(1f), label = { Text(label) }, singleLine = true,
            isError = value.toIntOrNull()?.let { it >= 0 } != true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
        ManualActionSquare("Increase $label", "", onClick = { onValue(((value.toIntOrNull() ?: 0).coerceAtMost(Int.MAX_VALUE - 1) + 1).toString()) })
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
