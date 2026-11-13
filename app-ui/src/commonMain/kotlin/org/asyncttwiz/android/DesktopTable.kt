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
import androidx.compose.ui.platform.testTag
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
    /** The global top row (WEB-020): menu, turn line and move pill, the phase track, the dock and Table actions buttons. */
    const val TOP_ROW = 56f
    /** The right dock's width (WEB-017). */
    const val DOCK = 320f
    /** The action area's width at the bottom right (WEB-018), under the dock, whether the dock is open or not. */
    const val ACTIONS = 320f
    /** A seat header's height (WEB-016): the life square's. */
    const val SEAT = 48f
    /** The room before a seat header's life square: the player's avatar and name. */
    const val NAME = 132f
    /** The narrowest the other player's hand gets in their seat header before their chips' room gives way. */
    const val MIN_OPPONENT_HAND = 64f
    /** The bottom band's least height: the action area's quick actions over Undo and the main button. */
    const val MIN_BAND = 128f
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
     * The right end of a seat header's life square, [places] piles and round chips ([emblems]: the player has chips; [wanted]:
     * the room their chips would like) on a table [tableWidth] wide. The other player's always keeps the chips' room, so their
     * hand after it doesn't move; yours has it only with chips (your hand is in the bottom band).
     */
    fun rowRight(tableWidth: Float, own: Boolean, places: Int, emblems: Boolean, wanted: Float): Float {
        val piles = TablePresentation.Landscape.pilesRight(NAME, places)
        if (own && !emblems) return piles
        val fit = (tableWidth - 10f - (if (own) 0f else MIN_OPPONENT_HAND + 10f) - piles - 4f)
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
 * area stay as they are, since the go-on is the main button. Reports its height as a docked panel (D2) for the strip.
 */
@Composable
fun DesktopPrompt(geometry: DesktopGeometry, place: PromptPlace, content: @Composable () -> Unit) {
    val docked = dockedPanelReporter()
    if (place == PromptPlace.Board) {
        Layout(content = content, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
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
        Layout(content = content, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
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
    Row(modifier.fillMaxWidth().height(DesktopTable.TOP_ROW.dp).background(tableBarColor(play.board, viewer, reviewing))
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
}

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
                Text(phaseName(phase).uppercase(), fontSize = 8.sp, lineHeight = 9.sp, color = MenuInk.copy(alpha = .7f), maxLines = 1,
                    letterSpacing = .3.sp)
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

/**
 * WEB-017 (owner, decision 15): the desktop table's right dock: the Stack as its full list, always open, top first as the
 * Stack list shows it (STK-018; "Empty" with nothing on it), and the game chat under it with its input always visible
 * ([chat]: LOB-066's panel docked, Game / Pod tabs in a pod game). A centred overlay's dimming (WEB-019) covers it too.
 */
@Composable
fun DesktopDock(play: TableBoard, viewer: String, names: Map<String, String>, modifier: Modifier = Modifier,
    chat: @Composable (Modifier) -> Unit) {
    val order = CombatStackRules.stack(play.board).cards
    Column(modifier.fillMaxSize().background(Color(0xfffafbfb)).border(1.dp, Color(0xffd5d8dc))
        .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }.testTag("desktop-dock")) {
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val stackMax = maxHeight * .45f
            Column(Modifier.fillMaxSize()) {
                Text("STACK · ${order.size}", Modifier.padding(start = 16.dp, top = 10.dp, bottom = 6.dp).semantics { heading() },
                    fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = .5.sp, color = MenuInk)
                if (order.isEmpty()) Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(48.dp)
                    .border(1.dp, MenuBorder, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                    Text("Empty", fontSize = 12.sp, color = MenuInk.copy(alpha = .6f))
                } else StackEntries(play, viewer, Modifier.padding(horizontal = 12.dp).fillMaxWidth().heightIn(max = stackMax), names = names)
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = Color(0xffd5d8dc))
                chat(Modifier.fillMaxWidth().weight(1f))
            }
        }
    }
}

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
            Avatar(player, size = 28.dp)
            Text(if (own) "You" else player.username, color = if (own) OwnTableColor else OpponentTableColor, fontSize = 13.sp,
                fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** WEB-016: a seat header's background: the player's colour, grey in timeline mode (HIS-008). */
fun seatHeaderColor(owner: String, viewer: String, reviewing: Boolean): Color =
    if (reviewing) TimelineBarColor else battlefieldColor(owner, viewer, owner, lands = true)
