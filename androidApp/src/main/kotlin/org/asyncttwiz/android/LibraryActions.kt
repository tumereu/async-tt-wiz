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
    initialTool: LibraryTool = LibraryTool.CONTENTS) {
    var quantity by remember(controller) { mutableStateOf("1") }
    var mulliganQuantity by remember(controller) { mutableStateOf("7") }
    var bottom by remember(controller) { mutableStateOf(false) }
    var destination by remember(controller) { mutableStateOf<MoveDestination?>(null) }
    val selection = if (bottom) ContentsSelection.BOTTOM else ContentsSelection.TOP
    val count = quantity.toIntOrNull()?.takeIf { it >= 0 }
    val enabled = controller.available()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            ActionSquare("Draw one", "↓", enabled, onClick = { controller.draw() })
            ActionSquare("View contents", "⌕", enabled, onClick = { controller.show() })
            ActionSquare("Reveal contents", "◉", enabled, onClick = { controller.show(reveal = true) })
            ActionSquare("Shuffle all", "⇄", enabled, onClick = { controller.shuffle() })
        }
        LibraryQuantity(quantity, { quantity = it }, "Count")
        Row {
            TextButton(onClick = { bottom = false }) { Text(if (!bottom) "✓ Top" else "Top") }
            TextButton(onClick = { bottom = true }) { Text(if (bottom) "✓ Bottom" else "Bottom") }
            TextButton(onClick = { count?.let { controller.draw(it) } }, enabled = enabled && count != null) { Text("Draw N") }
        }
        Row {
            TextButton(onClick = { count?.let { controller.show(selection, it) } }, enabled = enabled && count != null) { Text("View N") }
            TextButton(onClick = { count?.let { controller.show(selection, it, true) } }, enabled = enabled && count != null) { Text("Reveal N") }
        }
        LibraryDestinationPicker(board, destination, { destination = it })
        TextButton(onClick = { count?.let { n -> destination?.let { controller.move(selection, n, it) } } },
            enabled = enabled && count != null && destination != null) { Text("Move N") }
        LibraryQuantity(mulliganQuantity, { mulliganQuantity = it }, "Shuffle and draw count")
        if (initialTool == LibraryTool.MULLIGAN_N) Text("Return captured Hand · choose draw count")
        TextButton(onClick = { mulliganQuantity.toIntOrNull()?.let { controller.mulligan(displayedHand, it) } },
            enabled = enabled && mulliganQuantity.toIntOrNull()?.let { it >= 0 } == true) { Text("Return Hand → Shuffle → Draw N") }
        TextButton(onClick = { controller.mulligan(displayedHand, minusOne = true) }, enabled = enabled) {
            Text("Return Hand → Shuffle → Draw Hand size −1")
        }
    }
}

@Composable
private fun LibraryQuantity(value: String, onValue: (String) -> Unit, label: String) {
    Row(Modifier.fillMaxWidth()) {
        TextButton(onClick = { onValue(((value.toIntOrNull() ?: 0) - 1).coerceAtLeast(0).toString()) }) { Text("−") }
        OutlinedTextField(value, onValue, Modifier.weight(1f), label = { Text(label) }, singleLine = true,
            isError = value.toIntOrNull()?.let { it >= 0 } != true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
        TextButton(onClick = { onValue(((value.toIntOrNull() ?: 0).coerceAtMost(Int.MAX_VALUE - 1) + 1).toString()) }) { Text("+") }
    }
}

@Composable
internal fun LibraryDestinationPicker(board: BoardState, selected: MoveDestination?, onSelect: (MoveDestination) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val choices = libraryDestinations(board)
    Box {
        TextButton(onClick = { open = true }) { Text(choices.firstOrNull { it.second == selected }?.first ?: "Destination") }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            choices.forEach { (label, destination) -> DropdownMenuItem(text = { Text(label) },
                onClick = { onSelect(destination); open = false }) }
        }
    }
}

internal fun libraryDestinations(board: BoardState): List<Pair<String, MoveDestination>> = board.containers.flatMap { container ->
    val label = "${container.accountId.orEmpty()} ${container.zone.name.lowercase()}"
    when (container.zone) {
        PlayZone.LIBRARY -> listOf("$label top" to MoveDestination(container.id, MoveInsertion.TOP),
            "$label bottom" to MoveDestination(container.id, MoveInsertion.BOTTOM))
        PlayZone.HAND, PlayZone.GRAVEYARD, PlayZone.EXILE -> listOf(label to MoveDestination(container.id, MoveInsertion.UNORDERED))
        PlayZone.STACK -> listOf(label to MoveDestination(container.id, MoveInsertion.TOP))
        PlayZone.BATTLEFIELD -> {
            val column = board.cards.filter { it.containerId == container.id }.mapNotNull { it.placement?.position?.x }.maxOrNull()?.plus(1) ?: 0
            listOf(label to MoveDestination(container.id, MoveInsertion.EMPTY, BoardPosition(column, 0)))
        }
    }
}
