package org.asyncttwiz.android

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import org.asyncttwiz.automatum.StateChip

/** One state chip where it lies: [chip] by its label, or, for a "+N" chip ([hidden] not empty), the states it stands for. */
data class PlacedStateChip(val label: String, val bounds: TableBounds, val chip: StateChip? = null,
    val hidden: List<StateChip> = emptyList())

/**
 * TRN-072: a player's state chips by the life square, with the rules of the emblems' row (TRN-053): in portrait at the right of
 * the free room right of the piles (when that room is too narrow, by the life square, past the room chip and the emblems), in
 * landscape right after the piles in the player's bar row; above the emblems and the room chip when there are any, those that
 * don't fit going on beside the emblems when no room chip is there. Where they still don't fit, the last chip reads "+N".
 * TRN-073: the game's day or night sits above the Stack.
 */
internal object StateChips {
    const val HEIGHT = 18f
    const val TEXT = 10f
    const val GAP = 3f
    private const val PADDING = 6f
    /** What the row takes off the room of the emblems and the room chip. */
    const val LINE = HEIGHT + 2f
    private val PLUS_WIDTH = width("+9")

    /** A chip's width for [label]: a generous estimate of its text, so the same on every layout. */
    fun width(label: String): Float = CombatStackPresentation.laneTextWidth(label, TEXT) + 2 * PADDING

    /** The width of a row of all [chips]. */
    fun rowWidth(chips: List<StateChip>): Float = chips.sumOf { width(it.label).toDouble() }.toFloat() + (chips.size - 1).coerceAtLeast(0) * GAP

    /** [chips] in the room's line and, those that don't fit, a second line at most [room2] wide from x [start2] (or ending at
     * [end2]) at height [y2] (none without [room2]); [start] or [end] places the first line as in [row]. */
    fun rows(chips: List<StateChip>, room: Float, y: Float, start: Float? = null, end: Float? = null, room2: Float = 0f, y2: Float = 0f,
        start2: Float? = null, end2: Float? = null): List<PlacedStateChip> {
        if (room2 < PLUS_WIDTH || rowWidth(chips) <= room) return row(chips, room, y, start, end)
        var first = 0
        while (first < chips.size && rowWidth(chips.take(first + 1)) <= room) first++
        return row(chips.take(first), room, y, start, end) + row(chips.drop(first), room2, y2, start2, end2)
    }

    /** [chips] in a row at most [room] wide, from x [start] (or ending at [end]) at height [y]: as many as fit, the last
     * one "+N" for the rest, standing for all of them when not even the first fits. */
    fun row(chips: List<StateChip>, room: Float, y: Float, start: Float? = null, end: Float? = null): List<PlacedStateChip> {
        if (chips.isEmpty()) return emptyList()
        var shown = chips.size
        var used = rowWidth(chips)
        if (used > room) {
            shown = chips.size - 1
            while (shown > 0 && rowWidth(chips.take(shown)) + GAP + PLUS_WIDTH > room) shown--
            used = (if (shown > 0) rowWidth(chips.take(shown)) + GAP else 0f) + PLUS_WIDTH
        }
        var x = start ?: ((end ?: 0f) - used)
        val placed = chips.take(shown).map { chip ->
            val w = width(chip.label)
            PlacedStateChip(chip.label, TableBounds(x, y, w, HEIGHT), chip).also { x += w + GAP }
        }
        val rest = chips.drop(shown)
        return if (rest.isEmpty()) placed else placed + PlacedStateChip("+${rest.size}", TableBounds(x, y, PLUS_WIDTH, HEIGHT), hidden = rest)
    }

    /** TRN-073: the game's day or night, right-aligned above the Stack's [stack] region and its count badge. */
    fun aboveStack(chip: StateChip, stack: TableBounds): PlacedStateChip =
        row(listOf(chip), Float.MAX_VALUE, stack.y - 8.5f - HEIGHT - 2f, end = stack.right - 2f).single()
}

/** The explanations a hold on [this] chip shows: what it stands for; the label first where it says more than the name
 * ("Ring 2/4"). */
private fun PlacedStateChip.explained(): Pair<List<arcanum.api.RulesGlossary.Entry>, String?> {
    val chips = if (hidden.isNotEmpty()) hidden else listOfNotNull(chip)
    val entries = chips.mapNotNull { it.explanation }
    val lead = chips.filter { it.explanation?.name != it.label }.map { it.label }.joinToString(", ").ifEmpty { null }
    return entries to lead
}

/**
 * TRN-072, TRN-073: each player's state chips and the game's day or night. A tap does nothing; holding one shows what it means
 * in the dark rule popup (TBL-132), placed as a held button's name is (SYS-045), until the finger lifts.
 */
@Composable
internal fun StateChipsOverlay(scene: TableScene) {
    for (life in scene.items.filter { it.kind == TableItemKind.LIFE && it.stateChips.isNotEmpty() && it.visible.isNotEmpty() }) key(life.id) {
        for (placed in life.stateChips) key(placed.label) { StateChipView(placed) }
    }
    scene.dayNightChip?.let { placed -> key(placed.label) { StateChipView(placed) } }
}

/** The test tag of a state chip (TRN-072, TRN-073). */
internal const val STATE_CHIP_TAG = "state-chip"

@Composable
private fun StateChipView(placed: PlacedStateChip) {
    var holding by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(9.dp)
    val b = placed.bounds
    Box(Modifier.offset { IntOffset(b.x.dp.roundToPx(), b.y.dp.roundToPx()) }.size(b.width.dp, b.height.dp).testTag(STATE_CHIP_TAG)
        .semantics { contentDescription = placed.chip?.label ?: placed.hidden.joinToString(", ") { it.label } }
        .clip(shape).background(StateChipFill).border(1.dp, AutomatumViolet, shape)
        .pointerInput(Unit) { detectTapGestures(onPress = { tryAwaitRelease(); holding = false }, onLongPress = { holding = true }) },
        contentAlignment = Alignment.Center) {
        Text(placed.label, color = StateChipInk, fontSize = StateChips.TEXT.sp, lineHeight = 12.sp, maxLines = 1, softWrap = false)
        if (holding) {
            val gap = with(LocalDensity.current) { 8.dp.roundToPx() }
            val (entries, lead) = placed.explained()
            Popup(popupPositionProvider = remember(gap) { HoldTooltipPosition(gap) }) { RuleExplanation(entries, lead) }
        }
    }
}

/** The room chip's colours (TRN-069). */
private val StateChipFill = Color(0xfff3effb)
private val StateChipInk = Color(0xff301e52)
