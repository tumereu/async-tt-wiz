package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.activity.compose.BackHandler
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
    var mulligan by remember(controller) { mutableStateOf(false) }
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
            InlinePopupCell(MenuControl("Manifest top", enabled &&
                board.containers.singleOrNull { it.id == controller.libraryId }?.cards?.isNotEmpty() == true) {
                finish(controller.manifestTop())
            }, label = "Manifest")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            InlinePopupCell(MenuControl("Mulligan N…", enabled) { mulligan = true })
            InlinePopupCell(MenuControl("Mulligan −1", enabled) { finish(controller.mulligan(displayedHand, minusOne = true)) })
        }
        val revealed = board.containers.single { it.id == controller.libraryId }.keepTopRevealed
        val color = LocalInlinePopupColor.current
        RingPress(MenuControl("Keep top card revealed", controller.maySetKeepTopRevealed(), selected = revealed) {
            finish(controller.setKeepTopRevealed(!revealed))
        }, Modifier.width(248.dp).heightIn(min = 48.dp).semantics {
            stateDescription = if (revealed) "On" else "Off"
        }, color = color, fill = if (revealed) color.copy(alpha = .2f) else androidx.compose.ui.graphics.Color.White) {
            Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (revealed) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff, null, tint = color)
                Text("Keep top card revealed", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    if (editing != null) {
        BackHandler { editing = null }
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        val confirmEnabled = (if (editing in listOf("Look", "Reveal")) mayOpen else enabled) &&
            text.text.toIntOrNull()?.let { it >= 0 } == true
        val confirm = {
            val action = editing
            val count = text.text.toIntOrNull()?.takeIf { it >= 0 }
            if (action != null && count != null) { quantities.write(action, count); editing = null; perform(action, count) }
        }
        ValueInputControl("$editing quantity", text, { text = it }, enabled, submitEnabled = confirmEnabled, submit = confirm)
        // The floating row has no title, so the confirm cell names the action it performs.
        InlinePopupCell(MenuControl(editing ?: "OK", enabled = confirmEnabled, click = confirm))
        }
    }
    if (mulligan) MulliganDialog(controller, displayedHand, owner, { mulligan = false }) {
        mulligan = false; onComplete()
    }
}

/** What a Temp pile menu offers; every action is always shown and disabled when it does not apply. */
internal data class TempPileMenuState(val size: Int, val available: Boolean, val mayLook: Boolean,
    val hasLibrary: Boolean, val mayClose: Boolean) {
    val moves get() = available && size > 0
}

internal fun tempPileMenuState(board: BoardState, ref: TempPiles.Ref, available: Boolean): TempPileMenuState {
    val pile = TempPiles.container(board, ref.accountId, ref.number)
    val size = pile?.cards?.size ?: 0
    return TempPileMenuState(size, available, available && size > 0 && board.contentsDisplays.isEmpty(),
        board.zoneContainer(ref.accountId, PlayZone.LIBRARY) != null,
        // Pile 1 is always there; another pile closes only while empty and nothing is shown from it.
        available && ref.number != 1 && pile != null && pile.cards.isEmpty() && board.contentsDisplays.none { it.containerId == pile.id })
}

/** A player's Temp pile [ref]: hidden like a Library, resolved into another zone or Temp pile in one step.
 * Piles are created by moving cards there; Close turns an empty pile 2..4 off again. */
@Composable
internal fun TempPileActions(controller: LibraryController, board: BoardState, ref: TempPiles.Ref,
    onShow: (ContentsSelection, Int, Boolean) -> Unit, toLibrary: (Boolean) -> Unit, toZone: (PlayZone) -> Unit,
    shuffleIntoLibrary: () -> Unit, toPile: (Int) -> Unit, topToPile: (Int) -> Unit, close: () -> Unit,
    onComplete: () -> Unit) {
    val state = tempPileMenuState(board, ref, controller.available())
    val enabled = state.moves
    val others = TempPiles.NUMBERS.filter { it != ref.number }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            InlinePopupCell(MenuControl("Shuffle", enabled) {
                if (controller.shuffle().let { it.refused == 0 && it.submitted > 0 }) onComplete()
            })
            InlinePopupCell(MenuControl("Look", state.mayLook) { onShow(ContentsSelection.TOP, state.size, false) })
            InlinePopupCell(MenuControl("Shuffle into library", enabled && state.hasLibrary) { shuffleIntoLibrary() })
            InlinePopupCell(MenuControl("Close pile", state.mayClose) { close() }, label = "Close")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            InlinePopupCell(MenuControl("All to library top", enabled && state.hasLibrary) { toLibrary(false) }, label = "All top")
            InlinePopupCell(MenuControl("All to library bottom", enabled && state.hasLibrary) { toLibrary(true) }, label = "All bottom")
            InlinePopupCell(MenuControl("All to graveyard", enabled) { toZone(PlayZone.GRAVEYARD) }, label = "All →\nGraveyard")
            InlinePopupCell(MenuControl("All to exile", enabled) { toZone(PlayZone.EXILE) }, label = "All →\nExile")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for (number in others)
                InlinePopupCell(MenuControl("All to Temp $number", enabled) { toPile(number) }, label = "All → $number")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for (number in others)
                InlinePopupCell(MenuControl("Top to Temp $number", enabled) { topToPile(number) }, label = "Top → $number")
        }
    }
}

internal fun selectedLibraryQuantity(text: String) = TextFieldValue(text, TextRange(0, text.length))
internal fun stepLibraryQuantity(text: String, delta: Int): Int =
    stepValueInput(text, delta)

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
    return board.containers.filter { (it.accountId == owner || it.zone == PlayZone.STACK) && !it.temp }.flatMap { container ->
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
