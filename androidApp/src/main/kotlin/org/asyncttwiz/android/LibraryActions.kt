package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import org.asyncttwiz.protocol.*

/** Reusable production controls. I16 embeds these at the bound Library, not in Table actions. */
@Composable
fun LibraryActions(controller: LibraryController, board: BoardState, displayedHand: List<String>, modifier: Modifier = Modifier,
    onComplete: () -> Unit = {},
    onShow: ((ContentsSelection, Int, Boolean) -> Unit)? = null) {
    val owner = board.containers.single { it.id == controller.libraryId }.accountId.orEmpty()
    val context = LocalContext.current
    val quantities = remember(owner) { LibraryQuantityStore(context, owner) }
    var editing by remember(controller) { mutableStateOf<String?>(null) }
    var text by remember { mutableStateOf("") }
    val enabled = controller.available()
    fun finish(result: LibrarySubmission) { if (result.refused == 0 && result.submitted > 0) onComplete() }
    fun show(slice: ContentsSelection, count: Int, reveal: Boolean = false) {
        if (onShow != null) onShow(slice, count, reveal) else finish(controller.show(slice, count, reveal))
    }
    val destinations = libraryDestinations(board, sourceContainerId = controller.libraryId).map { it.second }
    fun perform(action: String, count: Int) {
        when (action) {
            "Draw" -> finish(controller.draw(count))
            "Look" -> show(ContentsSelection.TOP, count)
            "Reveal" -> show(ContentsSelection.TOP, count, true)
            else -> {
                val zone = if (action == "Mill") PlayZone.GRAVEYARD else PlayZone.EXILE
                destinations.firstOrNull { d -> board.containers.any { it.id == d.containerId && it.zone == zone } }
                    ?.let { finish(controller.move(ContentsSelection.TOP, count, it)) }
            }
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (many in listOf(false, true)) Row {
            for (action in listOf("Draw", "Reveal", "Look", "Mill", "Exile")) {
                InlinePopupCell(MenuControl("$action ${if (many) "N…" else "1"}", enabled) {
                    if (many) { text = quantities.read(action).toString(); editing = action }
                    else perform(action, 1)
                })
            }
        }
        Row {
            InlinePopupCell(MenuControl("Search", enabled) { show(ContentsSelection.ALL, 1) })
            InlinePopupCell(MenuControl("Shuffle", enabled) { finish(controller.shuffle()) })
        }
        Text("Mulligan (hand → shuffle → draw)", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            InlinePopupCell(MenuControl("Draw 7", enabled) { finish(controller.mulligan(displayedHand, 7)) })
            InlinePopupCell(MenuControl("Draw one fewer", enabled) { finish(controller.mulligan(displayedHand, minusOne = true)) })
        }
    }
    if (editing != null) TableMenuPanel({ editing = null }, title = "${editing} N") {
        OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
            isError = text.toIntOrNull()?.let { it >= 0 } != true)
        MenuButton("OK", enabled = enabled && text.toIntOrNull()?.let { it >= 0 } == true) {
            text.toIntOrNull()?.takeIf { it >= 0 }?.let { count ->
                val action = editing ?: return@MenuButton
                quantities.write(action, count); editing = null; perform(action, count)
            }
        }
    }
}

internal class LibraryQuantityStore(context: Context, account: String) {
    private val preferences = context.applicationContext.getSharedPreferences("library-quantities", Context.MODE_PRIVATE)
    private val prefix = "${account.length}:$account:"
    fun read(action: String) = preferences.getInt(prefix + action, 1).coerceAtLeast(0)
    fun write(action: String, value: Int) { preferences.edit().putInt(prefix + action, value.coerceAtLeast(0)).apply() }
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
