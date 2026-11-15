package org.asyncttwiz.android

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import kotlin.math.roundToInt
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.asyncttwiz.protocol.*

/**
 * WEB-015 (docs/specs/web-client.md §7.2): the desktop table's frame, which [EngineTableScreen] provides in the desktop layout
 * ([AppLayout.Desktop]): whether its right dock (the Stack list and the chat, WEB-017) is open. The table under it is the same
 * table as the phone's landscape one ([TableSurface] with landscape true), arranged by [TablePresentation.scene] for it.
 */
data class DesktopFrame(val dockOpen: Boolean = true) {
    /** How much of the window's width the dock takes from the battlefields. */
    val dockWidth: Float get() = if (dockOpen) DesktopTable.DOCK else 0f
}

/** The desktop table's frame while one is drawn; null in the phone layout. */
val LocalDesktopFrame = compositionLocalOf<DesktopFrame?> { null }

/** WEB-015: the desktop table's fixed parts' sizes, in dp (CSS px). */
object DesktopTable {
    /** The global top row (WEB-020) at 1×: menu, turn line and move pill, the phase track, the dock and Table actions
     * buttons. It is drawn [topRowScale] larger. */
    const val TOP_ROW = 56f
    /** WEB-020 (owner, desktop playtest 2026-11-14: its texts too small): how much larger than at 1× the top row is drawn, its
     * texts, icons and squares alike, in a window [width] wide: 1.3× where it has room (from about 1300 wide), less down to 1×
     * in the narrowest desktop window, so its phase track always fits. */
    fun topRowScale(width: Float): Float = (width / 1000f).coerceIn(1f, UI_SCALE)
    /** The top row's height in a window [width] wide. */
    fun topRow(width: Float): Float = TOP_ROW * topRowScale(width)
    /** WEB-018, WEB-020 (owner, desktop playtest 2026-11-14: "Others up to agent, perhaps 30%"): how much larger than the
     * phone's the desktop table draws its controls: the action area's quick actions, Undo and main button, the panels
     * (WEB-019) and the top row. */
    const val UI_SCALE = 1.3f
    /** The right dock's width (WEB-017). */
    const val DOCK = 320f
    /** How far inside the open dock's left edge the table's own Stack lies, hidden under it (WEB-017): its count bubble
     * (which overhangs its corner) and its frame stay wholly under the dock. */
    const val HIDDEN_STACK_INSET = 24f
    /** The most of the dock's height the Stack's entries take (WEB-017); they scroll beyond it, and the chat has the rest. */
    const val DOCK_STACK_SHARE = .45f
    /** The action area's width at the bottom right (WEB-018), under the dock, whether the dock is open or not. */
    const val ACTIONS = 320f
    /** A seat header's height (WEB-016): its piles ([PILE_HEIGHT]) with their count bubbles above them inside it. */
    const val SEAT = 84f
    /** A seat header's life square's side (the landscape bar row's 48 dp before 2026-11-14), its number drawn as much larger. */
    const val LIFE = 64f
    /** The piles' height in a seat header (WEB-022: twice the 32 dp a landscape bar row fits). */
    const val PILE_HEIGHT = 64f
    /** Each pile's own place, side by side, the first [PILE_GAP] after the life square. */
    const val PILE_PITCH = 64f
    const val PILE_GAP = 12f
    /** How much larger than the phone's a pile's count bubble is in a seat header, with its larger piles. */
    const val PILE_BADGE = 1.25f
    /** The room before a seat header's life square: the player's avatar and name. */
    const val NAME = 172f
    /** The seat header's avatar's side and the name's size (sp). */
    const val AVATAR = 36f
    const val NAME_TEXT = 17f
    /** The bottom band's least height: the action area's quick actions over Undo and the main button, drawn [UI_SCALE]
     * larger, with its padding. */
    val MIN_BAND = (DESKTOP_ACTIONS_HEIGHT + 2 * ACTIONS_PADDING) * UI_SCALE
    /** The action area's padding around its rows (at 1×). */
    const val ACTIONS_PADDING = 8f
    /** WEB-018: the gap between the action area's buttons, as between the quick actions (at 1×). */
    const val ACTION_GAP = 2f
    /** WEB-018 (owner, desktop playtest 2026-11-14: "Undo button size 1/3, resolve button size 2/3?"): Undo's width in the
     * action area's bottom row [row] wide: a third of it, as wide as the first of three quick actions above it. */
    fun undoWidth(row: Float) = (row - 2 * ACTION_GAP) / 3
    /** WEB-018: the main button's width in the bottom row [row] wide: the rest after Undo and the gap, two thirds. */
    fun mainWidth(row: Float) = row - undoWidth(row) - ACTION_GAP

    /** The right end of a seat header's life square and [places] piles from its left end [x]. */
    fun pilesRight(x: Float, places: Int) = x + LIFE + PILE_GAP + places * PILE_PITCH
    /** A prompt strip's most width (WEB-019): bottom-centre, above your seat header. */
    const val PROMPT_WIDTH = 640f
    /** A centred overlay's width (WEB-019). */
    const val OVERLAY_WIDTH = 560f

    /** The room above your hand's cards in the bottom band for a raised card (ZON-079). */
    fun handRoom(handHeight: Float) = handHeight * TablePresentation.RAISE + 6f

    /** The bottom band's height: your hand (its cards [handHeight] tall, a raised one whole) or the action area, whichever is
     * taller; in timeline mode the timeline strip under the hand (WEB-021). */
    fun band(handHeight: Float, timeline: Boolean): Float =
        maxOf(MIN_BAND, handRoom(handHeight) + handHeight + 8f + 6f) + if (timeline) TableTimelineHeight else 0f

    /** [band] for [appearance]'s hand cards. */
    fun band(appearance: CardAppearance, timeline: Boolean): Float = band(40f * appearance.size(CardArea.HAND), timeline)

    /**
     * The right end of a seat header's life square, [places] piles, the other player's hand's fan ([fan] wide, ZON-001; 0 for
     * yours) and round chips ([emblems]: the player has chips; [wanted]: the room their chips would like) on a table
     * [tableWidth] wide. The other player's always keeps the chips' room; yours has it only with chips (your hand is in the
     * bottom band).
     */
    fun rowRight(tableWidth: Float, own: Boolean, places: Int, emblems: Boolean, wanted: Float, fan: Float = 0f): Float {
        val piles = pilesRight(NAME, places) + if (fan > 0f) TablePresentation.FAN_GAP + fan else 0f
        if (own && !emblems) return piles
        val fit = tableWidth - 10f - piles - 4f
        return piles + 4f + maxOf(TablePresentation.Landscape.EMBLEMS, wanted).coerceAtMost(fit).coerceAtLeast(TablePresentation.Landscape.MIN_EMBLEMS)
    }
}

/** WEB-015: where the desktop table's parts lie on the table surface (dp), worked out with its scene by [TableSurface]. */
data class DesktopGeometry(val width: Float, val height: Float, val tableRight: Float, val band: Float) {
    /** Your seat header's top: a prompt strip's bottom (WEB-019). */
    val seatTop: Float get() = height - band - DesktopTable.SEAT
    /** The action area (WEB-018). */
    val actions: TableBounds get() = TableBounds(width - DesktopTable.ACTIONS, height - band, DesktopTable.ACTIONS, band)
}

/** The desktop table's geometry inside [TableSurface]; null in the phone layout. */
val LocalDesktopGeometry = compositionLocalOf<DesktopGeometry?> { null }

/** WEB-019: where a panel goes on the desktop table: [Board] for a decision that needs the board (targets, paying with
 * permanents, picking on the battlefield, a waiting line), [Choice] for one that doesn't (modes, X, ways, a list, a reveal).
 * The phone puts both above the hand (STK-044). */
sealed interface PromptPlace {
    data object Board : PromptPlace
    data object Choice : PromptPlace
}

/** WEB-019: how visible the table's dimming under a centred overlay is (0 to 1): a panel hidden with its eye (STK-044) takes
 * it away, so the board shows. */
class PromptDim { var shown by mutableFloatStateOf(1f) }

/** The dimming of the centred overlay a panel is drawn in; null elsewhere. */
val LocalPromptDim = compositionLocalOf<PromptDim?> { null }

/** How dark the table is under a centred overlay (WEB-019). */
private const val OVERLAY_DIM = .45f

/**
 * WEB-019 (owner, decision 28: as in MTG Arena): a panel on the desktop table. [PromptPlace.Board]: a strip bottom-centre of
 * the battlefields, at most [DesktopTable.PROMPT_WIDTH] wide, its bottom on your seat header, so the board stays in view.
 * [PromptPlace.Choice]: a centred overlay [DesktopTable.OVERLAY_WIDTH] wide over the battlefields, the table and the dock
 * dimmed under it and taking no input while it shows (the eye hides it, and the dimming with it); the top row and the action
 * area stay as they are, since the go-on is the main button. Reports its height as a docked panel (D2) for the strip. Either
 * draws the panel [DesktopTable.UI_SCALE] larger than the phone's (owner, desktop playtest 2026-11-14), in its same width.
 */
@Composable
fun DesktopPrompt(geometry: DesktopGeometry, place: PromptPlace, content: @Composable () -> Unit) {
    val docked = dockedPanelReporter()
    if (place == PromptPlace.Board) {
        Layout(content = { DesktopFittedScale(DesktopTable.UI_SCALE, content) }, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
            val bottom = geometry.seatTop.dp.roundToPx().coerceIn(0, constraints.maxHeight)
            val tableRight = geometry.tableRight.dp.roundToPx().coerceAtMost(constraints.maxWidth)
            val width = minOf(DesktopTable.PROMPT_WIDTH.dp.roundToPx(), tableRight - 16.dp.roundToPx()).coerceAtLeast(0)
            val placeable = measurables.firstOrNull()?.measure(Constraints(minWidth = width, maxWidth = width, maxHeight = bottom))
            docked((placeable?.height ?: 0).toDp().value)
            layout(constraints.maxWidth, constraints.maxHeight) { placeable?.place((tableRight - width) / 2, bottom - placeable.height) }
        }
        return
    }
    val dim = remember { PromptDim() }
    SideEffect { docked(0f) }
    val shade = Color.Black.copy(alpha = OVERLAY_DIM * dim.shown)
    val blocking = dim.shown > .5f
    // The battlefields and the dock, not the action area (its main button is the go-on) nor the top row (outside the surface)
    val parts = listOf(TableBounds(0f, 0f, geometry.tableRight, geometry.height),
        TableBounds(geometry.tableRight, 0f, geometry.width - geometry.tableRight, geometry.height - geometry.band))
    for (part in parts) Box(Modifier.offset(part.x.dp, part.y.dp).size(part.width.dp, part.height.dp).background(shade)
        .testTag("prompt-dim").then(if (blocking) Modifier.pointerInput(Unit) {
            awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
        } else Modifier))
    CompositionLocalProvider(LocalPromptDim provides dim) {
        Layout(content = { DesktopFittedScale(DesktopTable.UI_SCALE, content) }, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
            val tableRight = geometry.tableRight.dp.roundToPx().coerceAtMost(constraints.maxWidth)
            val room = geometry.seatTop.dp.roundToPx().coerceIn(0, constraints.maxHeight)
            val width = minOf(DesktopTable.OVERLAY_WIDTH.dp.roundToPx(), tableRight - 32.dp.roundToPx()).coerceAtLeast(0)
            val placeable = measurables.firstOrNull()?.measure(Constraints(minWidth = width, maxWidth = width,
                maxHeight = (room - 32.dp.roundToPx()).coerceAtLeast(0)))
            layout(constraints.maxWidth, constraints.maxHeight) {
                placeable?.place((tableRight - width) / 2, ((room - placeable.height) / 2).coerceAtLeast(0))
            }
        }
    }
}

/**
 * WEB-020 (owner, decision 15 and 27): the desktop table's global top row: the side menu button, the turn line and move pill
 * (TRN-005, TRN-006), the phase track (WEB-020: the phase dialog's step squares, current outlined, so the step button and
 * dialog aren't needed), Return to current in timeline mode (HIS-018), the chat button while the dock is folded away
 * (LOB-066), the dock's button and Table actions. Only global things: each player's own things are on their seat header.
 */
@Composable
fun DesktopTopRow(game: GameSummary, play: TableBoard, viewer: String, toolkit: TableToolkitController?, reviewing: Boolean,
    navigation: (() -> Unit)?, returnCurrent: (() -> Unit)?, changes: TableChanges?, dockOpen: Boolean, toggleDock: () -> Unit,
    chat: (() -> Unit)?, chatUnread: Int, modifier: Modifier = Modifier) {
    val turnColor = if (play.board.turn.accountId == viewer) OwnTableColor else OpponentTableColor
    val attention = attentionPresentation(game, play, viewer, reviewing, toolkit?.enabled() == true)
    // WEB-020 (owner, desktop playtest 2026-11-14): drawn larger where the window has room
    BoxWithConstraints(modifier.fillMaxWidth()) { DesktopScaled(DesktopTable.topRowScale(maxWidth.value)) {
    Row(Modifier.fillMaxWidth().height(DesktopTable.TOP_ROW.dp).background(tableBarColor(play.board, viewer, reviewing))
        .padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ActionSquare("Open navigation", "", navigation != null, Modifier.width(48.dp), icon = { TableChromeIcon("menu") },
            onClick = { navigation?.invoke() })
        Column(Modifier.width(168.dp)) {
            val header = turnHeader(game, play.board.turn, viewer)
            Text(header, Modifier.semantics { contentDescription = game.gameName }, color = turnColor, fontSize = 13.sp,
                lineHeight = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            ActionSquare(attention.label, "", attention.enabled,
                Modifier.width(IntrinsicSize.Max).height(26.dp).clip(RoundedCornerShape(13.dp))
                    .then(if (attention.own) Modifier else Modifier.border(1.dp, Color(0xffa8afaf), RoundedCornerShape(13.dp))),
                fill = if (attention.own) OwnTableColor else Color.White, ink = if (attention.own) Color.White else OpponentTableColor,
                compactContent = true,
                icon = {
                    Text(attention.label, Modifier.padding(horizontal = 8.dp), fontSize = 12.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, fontWeight = if (attention.own) FontWeight.Bold else FontWeight.Normal)
                }, onClick = { if (!reviewing) toolkit?.submit(PlayAction.PassAttention) })
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) { DesktopPhaseTrack(play, viewer, changes) }
        if (reviewing) ActionSquare("Return to current", "", returnCurrent != null, Modifier.width(48.dp),
            icon = { TableChromeIcon("current") }, onClick = { returnCurrent?.invoke() })
        if (!dockOpen) Box {
            ActionSquare("Chat, $chatUnread unread messages", "", chat != null, Modifier.width(48.dp), icon = { TableChromeIcon("chat") },
                onClick = { chat?.invoke() })
            if (chatUnread > 0) Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(6.dp)
                .background(OpponentTableColor, androidx.compose.foundation.shape.CircleShape))
        }
        ActionSquare(if (dockOpen) "Fold the dock away" else "Show the dock", "", true, Modifier.width(48.dp).testTag("dock-button"),
            icon = { DockIcon() }, onClick = toggleDock)
        // HIS-020, TRN-049: open while a suggestion blocks game actions, for its Timeline (its other entries greyed)
        if (!reviewing) ActionSquare("Table actions", "", toolkit != null, Modifier.width(48.dp), icon = { TableChromeIcon("grid") },
            onClick = { toolkit?.tableMenu() })
    }
    } }
}

/**
 * WEB-018, WEB-020 (owner, desktop playtest 2026-11-14: the top bar's and the quick action buttons' texts too small): [content]
 * drawn [scale] times larger, its sizes and texts alike, so the desktop table's controls grow without the phone's changing.
 * It is laid out in the room given divided by [scale] and then magnified by a layer, taking the room it fills: its layout
 * nodes keep the window's density, so the page's semantics mirror (which places each node by its own density) and the hover
 * pointer find its controls where they are drawn (WEB-058; a density override for the subtree put them where they would be
 * at 1×).
 */
@Composable
fun DesktopScaled(scale: Float, content: @Composable () -> Unit) {
    Layout(content = {
        Box(Modifier.graphicsLayer { scaleX = scale; scaleY = scale; transformOrigin = TransformOrigin(0f, 0f) }) { content() }
    }) { measurables, constraints ->
        val placeable = measurables.firstOrNull()?.measure(constraints.divided(scale))
        layout(placeable.scaledWidth(scale, constraints), placeable.scaledHeight(scale, constraints)) { placeable?.place(0, 0) }
    }
}

/**
 * WEB-019 (coordinator, 2026-11-14: a desktop panel always fits the room it gets): [content] (a panel, strip or overlay)
 * drawn as [DesktopScaled] does at up to [scale], but only as large as fits the height it is given: when it fills that height
 * at the size it is drawn (its content would scroll), it is drawn a step smaller on the next frame, down to 1×, where its
 * content scrolls as the phone's does. It grows back only when the room it is given changes, so it never swings between two
 * sizes.
 */
@Composable
fun DesktopFittedScale(scale: Float, content: @Composable () -> Unit) {
    // The size it is drawn at, and the room it was found for
    val shown = remember { mutableFloatStateOf(scale) }
    val room = remember { mutableStateOf(Constraints()) }
    Layout(content = {
        // Centred in its room: a panel narrower than the room it gets once drawn smaller stays in the middle of it
        Box(Modifier.graphicsLayer { val k = shown.floatValue; scaleX = k; scaleY = k; transformOrigin = TransformOrigin(0f, 0f) },
            contentAlignment = Alignment.TopCenter) {
            content()
        }
    }) { measurables, constraints ->
        if (room.value != constraints) {
            if (room.value != Constraints()) {
                AppLog.info("panel", "Desktop panel: room ${constraints.maxWidth}×${constraints.maxHeight} px, trying ${scale}× again")
                shown.floatValue = scale
            }
            room.value = constraints
        }
        val k = shown.floatValue.coerceIn(1f, scale)
        val inner = constraints.divided(k)
        val placeable = measurables.firstOrNull()?.measure(inner)
        // Filling the height it was given: its content would scroll there; a step smaller next frame, down to 1×
        if (placeable != null && inner.hasBoundedHeight && placeable.height >= inner.maxHeight && k > 1f) {
            val next = maxOf(1f, k - FIT_STEP)
            AppLog.info("panel", "Desktop panel: ${placeable.height} px fills its ${inner.maxHeight} at ${k}×, drawn at ${next}×")
            shown.floatValue = next
        }
        layout(placeable.scaledWidth(k, constraints), placeable.scaledHeight(k, constraints)) { placeable?.place(0, 0) }
    }
}

/** How much smaller a desktop panel is drawn each frame until it fits ([DesktopFittedScale]). */
private const val FIT_STEP = .05f

/** These constraints divided by [scale]: the room of content [scale] times magnified. */
private fun Constraints.divided(scale: Float): Constraints {
    fun shrink(value: Int) = if (value == Constraints.Infinity) value else (value / scale).toInt()
    return Constraints(shrink(minWidth), shrink(maxWidth), shrink(minHeight), shrink(maxHeight))
}
private fun androidx.compose.ui.layout.Placeable?.scaledWidth(scale: Float, constraints: Constraints) =
    ((this?.width ?: 0) * scale).roundToInt().coerceIn(constraints.minWidth, constraints.maxWidth)
private fun androidx.compose.ui.layout.Placeable?.scaledHeight(scale: Float, constraints: Constraints) =
    ((this?.height ?: 0) * scale).roundToInt().coerceIn(constraints.minHeight, constraints.maxHeight)

/** The dock button's icon: a window with its right panel. */
@Composable
private fun DockIcon() {
    val color = LocalContentColor.current
    androidx.compose.foundation.Canvas(Modifier.size(24.dp)) {
        val u = size.width / 24f
        drawRoundRect(color, androidx.compose.ui.geometry.Offset(3f * u, 4f * u), androidx.compose.ui.geometry.Size(18f * u, 16f * u),
            androidx.compose.ui.geometry.CornerRadius(2f * u), style = androidx.compose.ui.graphics.drawscope.Stroke(1.6f * u))
        drawLine(color, androidx.compose.ui.geometry.Offset(15f * u, 4f * u), androidx.compose.ui.geometry.Offset(15f * u, 20f * u), 1.6f * u)
    }
}

/**
 * WEB-020: the phase track: the phase dialog's (TRN-008) phase labels over their step squares in the phases' colours, the
 * current step outlined in the turn player's colour, flashing on a step change (TRN-007). View-only, as the dialog is
 * (TRN-009): the game moves on only with the main button.
 */
@Composable
fun DesktopPhaseTrack(play: TableBoard, viewer: String, changes: TableChanges?) {
    val turn = play.board.turn
    val turnColor = if (turn.accountId == viewer) OwnTableColor else OpponentTableColor
    val current = TurnPosition.of(turn)
    Row(Modifier.testTag("phase-track").semantics(mergeDescendants = true) {
        contentDescription = "Turn ${turn.number}, ${turnStepName(turn)}"
    }, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        for ((phase, steps) in pickerSteps(play.board).groupBy { it.phase }) {
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(desktopPhaseLabel(phase), fontSize = 8.sp, lineHeight = 9.sp, color = MenuInk.copy(alpha = .7f), maxLines = 1,
                    softWrap = false)
                Row(Modifier.background(phaseColor(phase), RoundedCornerShape(3.dp)).padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (step in steps) {
                        val now = step == current
                        Box(Modifier.size(width = 40.dp, height = 36.dp)
                            .then(if (now) Modifier.tableChangeFlash(changes, TableChangeKey("turn", TableChangeKind.STEP))
                                .background(Color.White.copy(alpha = .7f)).border(2.dp, turnColor) else Modifier.border(1.dp, MenuBorder))
                            .semantics { selected = now }, contentAlignment = Alignment.Center) {
                            CompositionLocalProvider(LocalContentColor provides if (now) turnColor else MenuInk) { StepIcon(step) }
                        }
                    }
                }
            }
        }
    }
}

/** WEB-020 (wireframe D1): a phase's label over its step squares: the main phases are "MAIN" (their one square says which). */
fun desktopPhaseLabel(phase: String): String = when (phase) {
    "PRECOMBAT_MAIN", "POSTCOMBAT_MAIN" -> "MAIN"
    else -> phaseName(phase).uppercase()
}

/**
 * WEB-017 (owner, decision 15): the desktop table's right dock: the Stack as its full list, always open, top first as the
 * Stack list shows it (STK-018; "Empty" with nothing on it), and the game chat under it with its input always visible
 * ([chat]: LOB-066's panel docked, Game / Pod tabs in a pod game). A centred overlay's dimming (WEB-019) covers it too.
 */
@Composable
fun DesktopDock(play: TableBoard, viewer: String, names: Map<String, String>, modifier: Modifier = Modifier,
    /** HIS-112 (Stack animation, owner 2026-11-14): told where a new top entry goes in the list (root dp, a card's size; null
     * once the dock is gone), for a play shown to fly into it. */
    stackPlace: (TableBounds?) -> Unit = {},
    chat: @Composable (Modifier) -> Unit) {
    val order = CombatStackRules.stack(play.board).cards
    val entrySize = rememberCardAppearance().size(CardArea.STACK)
    DisposableEffect(Unit) { onDispose { stackPlace(null) } }
    // WEB-017 (owner, desktop playtest 2026-11-14): how the dock shares its height between the Stack and the chat, logged as
    // it changes, so a report of a squeezed Stack or chat says what was measured
    var stackShown by remember { mutableIntStateOf(0) }
    var chatShown by remember { mutableIntStateOf(0) }
    Column(modifier.fillMaxSize().background(Color(0xfffafbfb)).border(1.dp, Color(0xffd5d8dc))
        .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }.testTag("desktop-dock")) {
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val stackMax = maxHeight * DesktopTable.DOCK_STACK_SHARE
            val dockHeight = maxHeight.value.toInt()
            val density = androidx.compose.ui.platform.LocalDensity.current.density
            LaunchedEffect(order.size, stackShown, chatShown, dockHeight) {
                if (stackShown > 0 || chatShown > 0) AppLog.info("dock", "Desktop dock $dockHeight dp: Stack ${order.size} entries in " +
                    "$stackShown dp (at most ${stackMax.value.toInt()}), chat $chatShown dp")
            }
            Column(Modifier.fillMaxSize()) {
                Text("STACK · ${order.size}", Modifier.padding(start = 16.dp, top = 10.dp, bottom = 6.dp).semantics { heading() },
                    fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = .5.sp, color = MenuInk)
                // The list's top (its first entry's card, or the "Empty" box's middle): where a new top entry shows, as it is drawn
                // on the screen (its corners mapped to the root, so a scaled dock, DesktopScaled, gives its drawn place and size)
                val measured = Modifier.onSizeChanged { stackShown = (it.height / density).toInt() }.onGloballyPositioned { c ->
                    val w = 32f * entrySize * density
                    val h = 44f * entrySize * density
                    val x = if (order.isEmpty()) (c.size.width - w) / 2 else 0f
                    val y = if (order.isEmpty()) (c.size.height - h) / 2 else 0f
                    val from = c.localToRoot(androidx.compose.ui.geometry.Offset(x, y))
                    val to = c.localToRoot(androidx.compose.ui.geometry.Offset(x + w, y + h))
                    stackPlace(TableBounds(from.x / density, from.y / density, (to.x - from.x) / density, (to.y - from.y) / density))
                }
                if (order.isEmpty()) Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(48.dp).then(measured)
                    .border(1.dp, MenuBorder, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                    Text("Empty", fontSize = 12.sp, color = MenuInk.copy(alpha = .6f))
                } else StackEntries(play, viewer, Modifier.padding(horizontal = 12.dp).fillMaxWidth().heightIn(max = stackMax).then(measured)
                    .testTag(DOCK_STACK_TAG), names = names, nameInCard = true)
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = Color(0xffd5d8dc))
                chat(Modifier.fillMaxWidth().weight(1f).onSizeChanged { chatShown = (it.height / density).toInt() })
            }
        }
    }
}

/** WEB-017: the dock's list of Stack entries (not shown while the Stack is empty). */
const val DOCK_STACK_TAG = "dock-stack"

/**
 * WEB-016 (owner, decision 27): each seat header's start: the player's avatar and name, before their life square, piles and
 * round chips (which the table scene lays out as the landscape bar row's). Drawn only: no input of its own.
 */
@Composable
fun SeatHeaderNames(game: GameSummary, scene: TableScene, viewer: String) {
    for (life in scene.items.filter { it.kind == TableItemKind.LIFE }) {
        val player = listOf(game.inviter, game.recipient).firstOrNull { it.accountId == life.owner } ?: continue
        val own = player.accountId == viewer
        Row(Modifier.offset((life.bounds.x - DesktopTable.NAME).dp, life.bounds.y.dp)
            .size(DesktopTable.NAME.dp, life.bounds.height.dp).padding(start = 10.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Avatar(player, size = DesktopTable.AVATAR.dp)
            Text(if (own) "You" else player.username, color = if (own) OwnTableColor else OpponentTableColor, fontSize = DesktopTable.NAME_TEXT.sp,
                fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** WEB-016: a seat header's background: the player's colour, grey in timeline mode (HIS-008). */
fun seatHeaderColor(owner: String, viewer: String, reviewing: Boolean): Color =
    if (reviewing) TimelineBarColor else battlefieldColor(owner, viewer, owner, lands = true)
