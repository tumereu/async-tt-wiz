package org.asyncttwiz.android

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Castle
import androidx.compose.material.icons.outlined.Coronavirus
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.MeetingRoom
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Stars
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import org.asyncttwiz.automatum.StateChip
import org.asyncttwiz.automatum.StateGlyph
import org.asyncttwiz.automatum.StateIcon

/** One state chip where it lies: [chip] by its label, or, for a "+N" chip ([hidden] not empty), the states it stands for;
 * ZON-136: the other player's playable chip with its cards ([playable], [OtherPlayableChip]). */
data class PlacedStateChip(val label: String, val bounds: TableBounds, val chip: StateChip? = null,
    val hidden: List<StateChip> = emptyList(), val playable: List<OtherPlayableChip.Shown> = emptyList())

/** Where a player's round chips lie (TRN-072, TRN-053, TRN-069): their states' ([states], the dungeon room first) and their
 * emblems' ([emblems], as [EmblemChips.shown] reads them). */
data class PlayerChipRow(val states: List<PlacedStateChip> = emptyList(), val emblems: List<TableBounds> = emptyList())

/**
 * TRN-072, TRN-053, TRN-069 (owner gallery review 2026-11-10, gallery 157: "display them like emblems"): a player's states, the
 * room of their dungeon and their emblems are round chips of one size in one row by the life square, never overlapping: in
 * portrait at the right of the free room right of the piles, over two lines where that room is tall enough; in landscape right
 * after the piles in the player's bar row. The room first, then the states, then the emblems. Where they don't all fit the chips
 * shrink (to [EmblemChips.MIN_SIZE]) and then the states' last chip reads "+N" for the states left out, as the emblems' does
 * for theirs. TRN-073: the game's day or night, a chip alike, sits above the Stack.
 */
object PlayerChips {
    /** The room a row of [chips] round chips wants (landscape: the bar row widens for them as far as the hand allows). */
    fun wanted(chips: Int): Float = EmblemChips.rowWidth(chips)

    /**
     * [states] and [emblemCount] emblems laid out in [area]: lines of chips filled top to bottom, each line ending at the area's
     * right ([fromRight], portrait) or starting at its left, the lines centred in its height.
     */
    fun layout(states: List<StateChip>, emblemCount: Int, area: TableBounds, fromRight: Boolean): PlayerChipRow {
        val emblemChips = EmblemChips.chipCount(emblemCount)
        val total = states.size + emblemChips
        if (total == 0 || area.width <= 0f) return PlayerChipRow()
        val gap = EmblemChips.ROW_GAP
        fun lines(size: Float) = ((area.height + gap) / (size + gap)).toInt().coerceAtLeast(1)
        fun perLine(size: Float) = ((area.width + gap) / (size + gap)).toInt().coerceAtLeast(1)
        val size = if (total <= lines(EmblemChips.SIZE) * perLine(EmblemChips.SIZE)) EmblemChips.SIZE else EmblemChips.MIN_SIZE
        val per = perLine(size)
        val capacity = lines(size) * per
        var emblemSlots = emblemChips
        var stateSlots = states.size
        if (total > capacity) {
            // The emblems keep at least half the chips when the states would leave them fewer; the states at least one
            emblemSlots = if (states.isEmpty()) minOf(emblemChips, capacity) else minOf(emblemChips, maxOf(capacity - states.size, capacity / 2))
            stateSlots = minOf(states.size, capacity - emblemSlots)
            if (stateSlots == 0 && states.isNotEmpty()) { stateSlots = 1; emblemSlots = capacity - 1 }
        }
        val count = stateSlots + emblemSlots
        val usedLines = (count + per - 1) / per
        val top = area.y + (area.height - (usedLines * size + (usedLines - 1) * gap)) / 2
        val spots = (0 until count).map { n ->
            val line = n / per
            val column = n % per
            val inLine = minOf(per, count - line * per)
            val start = if (fromRight) area.right - inLine * size - (inLine - 1) * gap else area.x
            TableBounds(start + column * (size + gap), top + line * (size + gap), size, size)
        }
        val shownStates = if (stateSlots < states.size) stateSlots - 1 else stateSlots
        val placed = states.take(shownStates).mapIndexed { n, chip -> PlacedStateChip(chip.label, spots[n], chip) } +
            if (shownStates < states.size) listOf(PlacedStateChip("+${states.size - shownStates}", spots[shownStates],
                hidden = states.drop(shownStates))) else emptyList()
        return PlayerChipRow(placed, spots.drop(stateSlots))
    }

    /** TRN-073: the game's day or night, right-aligned above the Stack's [stack] region and its count badge. */
    fun aboveStack(chip: StateChip, stack: TableBounds): PlacedStateChip = EmblemChips.SIZE.let { size ->
        PlacedStateChip(chip.label, TableBounds(stack.right - 2f - size, stack.y - 8.5f - size - 2f, size, size), chip)
    }
}

/** The explanations a hold on [this] chip shows: what it stands for; the label first where it says more than the name
 * ("Ring 2/4", "Room: Hall of Moths"). */
private fun PlacedStateChip.explained(): Pair<List<arcanum.api.RulesGlossary.Entry>, String?> {
    val chips = if (hidden.isNotEmpty()) hidden else listOfNotNull(chip)
    val entries = chips.mapNotNull { it.explanation }
    val lead = chips.filter { it.explanation?.name != it.label }.map { it.label }.joinToString(", ").ifEmpty { null }
    return entries to lead
}

/**
 * TRN-072, TRN-073, TRN-069, TRN-133: each player's state chips (their dungeon's room among them) and the game's day or night. A
 * tap does nothing. Holding a player's chip calls [held] with its owner (true; false on release): the table inspects that
 * player's states and emblems together ([PlayerChipsInspect]) until the finger lifts. Holding the day or night chip shows what
 * it means in the dark rule popup (TBL-132), placed as a held button's name is (SYS-045). ZON-136: the other player's playable
 * chip lists its cards ([play], [viewer] for their Inspect).
 */
@Composable
fun StateChipsOverlay(scene: TableScene, play: TableBoard?, viewer: String, held: (owner: String, down: Boolean) -> Unit) {
    for (life in scene.items.filter { it.kind == TableItemKind.LIFE && it.stateChips.isNotEmpty() && it.visible.isNotEmpty() }) key(life.id) {
        val owner = life.owner
        for (placed in life.stateChips) key(placed.label) {
            if (placed.playable.isNotEmpty()) OtherPlayableChipView(placed, play, viewer)
            else StateChipView(placed, owner?.let { o -> { down: Boolean -> held(o, down) } })
        }
    }
    scene.dayNightChip?.let { placed -> key(placed.label) { StateChipView(placed, null) } }
}

/** TRN-133 (owner 2026-11-21, E7): the Inspect tile of a player's state [chip] ([own]: the viewer's): its glyph, its name (its
 * explanation's, else its label), its label as the words that say more where it differs ("Speed 2", "Ring 2/4") and its
 * explanation. */
fun stateInspectTile(key: String, chip: StateChip, own: Boolean): InspectTile.State {
    val name = chip.explanation?.name ?: chip.label
    return InspectTile.State(key, chip.glyph, name, chip.label.takeIf { it != name }, chip.explanation?.text, own)
}

/**
 * TRN-133, TRN-129 (owner 2026-11-21, E7: "states and emblems together"): Inspect of [holder]'s round chips on [scene]: one dark
 * panel per state (the dungeon's room first, as their chips go; [stateInspectTile]), then one per emblem (TRN-129), over the dimmed
 * screen until the finger lifts.
 */
@Composable
fun PlayerChipsInspect(scene: TableScene, holder: String, viewer: String) {
    val life = scene.items.firstOrNull { it.kind == TableItemKind.LIFE && it.owner == holder } ?: return
    val own = holder == viewer
    val states = life.stateChips.filter { it.playable.isEmpty() }.flatMap { placed -> placed.chip?.let(::listOf) ?: placed.hidden }
    val texts = life.emblemTexts
    val arts = texts.map { rememberEmblemArt(it) }
    val groups = states.mapIndexed { n, chip -> InspectGroup("state/$n", listOf(stateInspectTile("state/$n", chip, own))) } +
        texts.mapIndexed { n, text -> InspectGroup("emblem/$n", listOf(InspectTile.Emblem("emblem/$n", text, arts[n], own))) }
    if (groups.isEmpty()) return
    LaunchedEffect(holder) {
        AppLog.info("table", "Player chips inspected: $holder's ${states.joinToString { it.label }}; ${texts.size} emblems")
    }
    InspectPopup(groups, rememberScrollState())
}

/** The test tag of a state chip (TRN-072, TRN-073). */
const val STATE_CHIP_TAG = "state-chip"

/** One state chip; [held] (a player's chip): its hold and release, for the table's Inspect of that player's chips (TRN-133);
 * without it (the day or night chip) a hold shows the rule popup (TRN-073). */
@Composable
private fun StateChipView(placed: PlacedStateChip, held: ((Boolean) -> Unit)?) {
    var holding by remember { mutableStateOf(false) }
    val currentHeld by rememberUpdatedState(held)
    val b = placed.bounds
    val plus = placed.hidden.isNotEmpty()
    Box(Modifier.offset { IntOffset(b.x.dp.roundToPx(), b.y.dp.roundToPx()) }.size(b.width.dp, b.height.dp).testTag(STATE_CHIP_TAG)
        .semantics { contentDescription = placed.chip?.label ?: placed.hidden.joinToString(", ") { it.label } }
        .clip(CircleShape).background(if (plus) Color.White else StateChipFill).border(1.5.dp, AutomatumViolet, CircleShape)
        .pointerInput(Unit) {
            detectTapGestures(onPress = {
                tryAwaitRelease()
                if (holding) currentHeld?.invoke(false)
                holding = false
            }, onLongPress = {
                holding = true
                AppLog.info("table", "State chip held: ${placed.label}")
                currentHeld?.invoke(true)
            })
        },
        contentAlignment = Alignment.Center) {
        val scale = b.width / EmblemChips.SIZE
        if (plus) Text(placed.label, color = AutomatumViolet, fontSize = (10 * scale).sp, fontWeight = FontWeight.Bold,
            lineHeight = (11 * scale).sp, maxLines = 1, softWrap = false)
        else placed.chip?.glyph?.let { StateGlyphView(it, scale, StateChipInk) }
        if (holding && held == null) {
            val gap = with(LocalDensity.current) { 8.dp.roundToPx() }
            val (entries, lead) = placed.explained()
            Popup(popupPositionProvider = remember(gap) { HoldTooltipPosition(gap) }) { RuleExplanation(entries, lead) }
        }
    }
}

/** A state's [glyph] in [ink], at [scale] times its size on a full-size chip (TRN-072): its letters, or its icon with its level
 * under it where it has one; the state's Inspect panel draws it larger (TRN-133). */
@Composable
internal fun StateGlyphView(glyph: StateGlyph, scale: Float, ink: Color) {
    when (glyph) {
        is StateGlyph.Letters -> Text(glyph.text, color = ink, fontSize = (glyphSize(glyph.text) * scale).sp,
            fontWeight = FontWeight.Bold, lineHeight = (glyphSize(glyph.text) * scale + 1).sp, maxLines = 1, softWrap = false)
        StateGlyph.Day -> Icon(Icons.Outlined.LightMode, null, Modifier.size((15 * scale).dp), tint = ink)
        StateGlyph.Night -> Icon(Icons.Outlined.DarkMode, null, Modifier.size((15 * scale).dp), tint = ink)
        StateGlyph.Room -> Icon(Icons.Outlined.MeetingRoom, null, Modifier.size((15 * scale).dp), tint = ink)
        // TRN-072 (owner notes 2026-11-14): the state's icon, its level under it where it has one
        is StateGlyph.Icon -> if (glyph.level == null) Icon(stateIcon(glyph.icon), null, Modifier.size((15 * scale).dp), tint = ink)
            else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(stateIcon(glyph.icon), null, Modifier.size((11 * scale).dp), tint = ink)
                val level = glyph.level.toString()
                Text(level, color = ink, fontSize = (levelSize(level) * scale).sp, fontWeight = FontWeight.Bold,
                    lineHeight = (levelSize(level) * scale).sp, maxLines = 1, softWrap = false)
            }
    }
}

/** TRN-072 (owner notes 2026-11-14): each state's icon. */
private fun stateIcon(icon: StateIcon): ImageVector = when (icon) {
    StateIcon.Monarch -> CrownIcon
    StateIcon.Initiative -> Icons.Outlined.Explore
    StateIcon.CitysBlessing -> Icons.Outlined.Castle
    StateIcon.EnduringStory -> Icons.Outlined.AutoStories
    StateIcon.Speed -> Icons.Outlined.Speed
    StateIcon.Ring -> Icons.Outlined.RadioButtonUnchecked
    StateIcon.Poison -> Icons.Outlined.WaterDrop
    StateIcon.Energy -> Icons.Outlined.Bolt
    StateIcon.Experience -> Icons.Outlined.Stars
    StateIcon.Rad -> Icons.Outlined.Coronavirus
    StateIcon.Dungeons -> Icons.Outlined.EmojiEvents
    StateIcon.ExtraTurn -> Icons.AutoMirrored.Outlined.Redo
    StateIcon.SkippedTurn -> Icons.Outlined.SkipNext
}

/** TRN-072: the monarch's crown (the icon set has none). */
private val CrownIcon = ImageVector.Builder("Crown", 24.dp, 24.dp, 24f, 24f).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(4f, 17f); lineTo(2.5f, 7f); lineTo(8f, 11f); lineTo(12f, 4f); lineTo(16f, 11f); lineTo(21.5f, 7f); lineTo(20f, 17f); close()
        moveTo(4f, 19f); lineTo(20f, 19f); lineTo(20f, 21f); lineTo(4f, 21f); close()
    }
}.build()

/** A level's text size under its icon on a full-size chip (sp): smaller for more digits. */
private fun levelSize(text: String): Float = when (text.length) {
    1 -> 8.5f
    2 -> 7.5f
    else -> 6.5f
}

/** A glyph's text size on a full-size chip (sp): smaller for longer glyphs, so up to four characters fit the circle. */
private fun glyphSize(text: String): Float = when (text.length) {
    1 -> 12f
    2 -> 10f
    3 -> 8.5f
    else -> 7f
}

/** TRN-069 (TRN-072): the room of a player's dungeon as their first state chip, its name said by a hold. */
fun roomChip(room: String) = StateChip("Room: $room", null, StateGlyph.Room)

/** The state chips' colours (TRN-072): a pale violet fill, dark violet ink. */
private val StateChipFill = Color(0xfff3effb)
private val StateChipInk = Color(0xff301e52)
