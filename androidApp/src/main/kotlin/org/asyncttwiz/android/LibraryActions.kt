package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.semantics.*
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
    var text by remember { mutableStateOf(selectedLibraryQuantity("")) }
    val enabled = controller.available()
    val mayOpen = enabled && board.contentsDisplays.isEmpty()
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
    if (editing == null) Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (many in listOf(false, true)) Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for (action in listOf("Draw", "Reveal", "Look", "Mill", "Exile")) {
                InlinePopupCell(MenuControl("$action ${if (many) "N…" else "1"}", if (action in listOf("Look", "Reveal")) mayOpen else enabled) {
                    if (many) { text = selectedLibraryQuantity(quantities.read(action).toString()); editing = action }
                    else perform(action, 1)
                })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            InlinePopupCell(MenuControl("Search", mayOpen) { show(ContentsSelection.ALL, 1) })
            InlinePopupCell(MenuControl("Shuffle", enabled) { finish(controller.shuffle()) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            InlinePopupCell(MenuControl("Mulligan 7", enabled) { finish(controller.mulligan(displayedHand, 7)) })
            InlinePopupCell(MenuControl("Mulligan −1", enabled) { finish(controller.mulligan(displayedHand, minusOne = true)) })
        }
    }
    if (editing != null) {
        BackHandler { editing = null }
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        for ((label, delta) in listOf("−" to -1, "+" to 1)) InlinePopupCell(MenuControl(label, enabled) {
            text = selectedLibraryQuantity(stepLibraryQuantity(text.text, delta).toString())
        }, label = "")
        val color = LocalInlinePopupColor.current
        Surface(Modifier.size(96.dp, 48.dp).ringInputTarget(draggable = true), shape = RoundedCornerShape(3.dp),
            color = androidx.compose.ui.graphics.Color.White, border = BorderStroke(1.dp, color.copy(alpha = .5f)), shadowElevation = 3.dp) {
            Box(contentAlignment = Alignment.Center) {
                BasicTextField(text, { text = it }, Modifier.fillMaxWidth().padding(8.dp)
                    .semantics { contentDescription = "$editing quantity" }
                    .onFocusChanged { if (it.isFocused) text = selectedLibraryQuantity(text.text) }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do { val event = awaitPointerEvent(PointerEventPass.Final) } while (event.changes.any { it.pressed })
                            text = selectedLibraryQuantity(text.text)
                        }
                    }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MenuInk, textAlign = androidx.compose.ui.text.style.TextAlign.Center))
            }
        }
        // The floating row has no title, so the confirm cell names the action it performs.
        InlinePopupCell(MenuControl(editing ?: "OK", enabled = (if (editing in listOf("Look", "Reveal")) mayOpen else enabled) &&
            text.text.toIntOrNull()?.let { it >= 0 } == true) {
            text.text.toIntOrNull()?.takeIf { it >= 0 }?.let { count ->
                val action = editing ?: return@MenuControl
                quantities.write(action, count); editing = null; perform(action, count)
            }
        })
        }
    }
}

internal fun selectedLibraryQuantity(text: String) = TextFieldValue(text, TextRange(0, text.length))
internal fun stepLibraryQuantity(text: String, delta: Int): Int =
    ((text.toIntOrNull() ?: 0).toLong() + delta).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()

internal class LibraryQuantityStore(context: Context, account: String) {
    private val preferences = context.applicationContext.getSharedPreferences("library-quantities", Context.MODE_PRIVATE)
    private val prefix = "${account.length}:$account:"
    fun read(action: String) = preferences.getInt(prefix + action, 1).coerceAtLeast(0)
    fun write(action: String, value: Int) { preferences.edit().putInt(prefix + action, value.coerceAtLeast(0)).apply() }
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
