package org.asyncttwiz.android

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import org.asyncttwiz.storage.ArtworkReference
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A card a choice shows (I-D48): drawn like a card on the battlefield (its name, its art, the owner's outline), or as
 * a covered card when the viewer may not see it (I-D27). [key] names it in the choice; [cardId] is the ATW card, for
 * Inspect.
 */
internal data class ChoiceCardData(val key: String, val cardId: String?, val name: String, val art: ArtworkReference?,
    val shown: Boolean, val token: Boolean = false, val own: Boolean = true, val tapped: Boolean = false,
    /** The battlefield's type glyph, shown while the art is missing. */
    val typeIcon: String = "",
    /** STK-046: a Stack entry that is an ability ([key] is the entry): drawn as its ability token, inspected with its text. */
    val ability: StackAbilityShown? = null)

/** The [ChoiceCardData] of ATW card [cardId] on [play]'s board as [viewer] may see it ([shown]); [fallback] names an
 * object that is no card. */
internal fun choiceCard(play: TableBoard, viewer: String, key: String, cardId: String?, shown: Boolean, fallback: String,
    tapped: Boolean = false): ChoiceCardData {
    val card = cardId?.let { id -> play.board.cards.firstOrNull { it.id == id } }
        ?: return ChoiceCardData(key, null, if (shown) fallback else "Hidden card", null, shown, tapped = tapped)
    val definition = play.definitions.firstOrNull { it.digest == card.definition }?.definition
    val face = definition?.faces?.firstOrNull { it.faceId == card.faceId } ?: definition?.faces?.firstOrNull()
    val art = if (shown && definition != null && face != null) face.tableArtwork(definition.faces.indexOf(face), card.definition)?.forTable() else null
    return ChoiceCardData(key, card.id, if (shown) face?.name ?: fallback else "Hidden card", art, shown, card.token,
        card.owner == viewer, tapped, if (shown) TablePresentation.cardTypeIcon(face?.typeLine.orEmpty()) else "")
}

/** The width and height of a choice's card (the battlefield's 28 × 40 shape), scaled by the Panels card size. */
@Composable
internal fun choiceCardSize(): Pair<androidx.compose.ui.unit.Dp, androidx.compose.ui.unit.Dp> {
    val scale = rememberCardAppearance().size(CardArea.PANELS)
    return 56.dp * scale to 80.dp * scale
}

/** A choice's card face, as the battlefield draws it: the name on top, the art below, the owner's outline; covered
 * when hidden. [badge]: a small number in its corner (its place in an order); [selected]: a violet ring. */
@Composable
internal fun ChoiceCardFace(card: ChoiceCardData, modifier: Modifier = Modifier, selected: Boolean = false, badge: String? = null,
    small: Boolean = false) {
    val (fullWidth, fullHeight) = choiceCardSize()
    val factor = if (small) .7f else 1f
    val owner = if (card.own) OwnTableColor else OpponentTableColor
    val width = fullWidth * factor
    val height = fullHeight * factor
    // A tapped card lies sideways, taking its sideways room
    Box(modifier.size(if (card.tapped) height else width, if (card.tapped) width else height)
        .semantics { contentDescription = card.name + if (selected) ", chosen" else "" }, contentAlignment = Alignment.Center) {
        val ability = card.ability
        if (ability != null && card.shown) AbilityTokenFace(ability, card.art, card.own, Modifier.requiredSize(width, height),
            card.typeIcon, text = !small)
        else Box(Modifier.requiredSize(width, height).then(if (card.tapped) Modifier.rotate(90f) else Modifier)
            .cardFrame(card.token, owner).then(if (card.shown) Modifier else Modifier.background(Color(0xff506b61)))) {
            if (card.shown) Column(Modifier.fillMaxSize().padding(horizontal = 3.dp, vertical = 2.dp)) {
                Text(card.name, fontSize = if (small) 7.sp else 8.sp, lineHeight = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, color = MenuInk)
                Box(Modifier.fillMaxWidth().weight(1f).padding(vertical = 2.dp)) {
                    // As on the battlefield: the type glyph while there is no art
                    CardArtwork(card.art, "", 192, 140, Modifier.fillMaxSize(), crop = true, placeholder = {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(card.typeIcon, fontSize = if (small) 14.sp else 20.sp, color = MenuInk)
                        }
                    })
                }
                Spacer(Modifier.height(6.dp))
            }
        }
        if (selected) Box(Modifier.fillMaxSize().border(3.dp, AutomatumViolet, RoundedCornerShape(4.dp)))
        if (badge != null) Surface(Modifier.align(Alignment.BottomStart).padding(2.dp), shape = CircleShape,
            color = AutomatumViolet, contentColor = Color.White) {
            Text(badge, Modifier.padding(horizontal = 5.dp), fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * Holding a choice's card inspects it (I-D48), as on the table: the large card shows while held. Only cards the viewer
 * may see ([ChoiceCardData.shown]) open; a covered one doesn't.
 */
internal class ChoiceInspector {
    var held by mutableStateOf<ChoiceCardData?>(null); private set
    fun hold(card: ChoiceCardData, holding: Boolean) {
        if (holding) { if (card.shown && card.cardId != null) held = card } else if (held?.key == card.key) held = null
    }
}

@Composable
internal fun ChoiceInspection(inspector: ChoiceInspector, play: TableBoard, viewer: String) {
    val card = inspector.held ?: return
    val id = card.cardId ?: return
    val played = play.board.cards.firstOrNull { it.id == id } ?: return
    val faces = play.definitions.firstOrNull { it.digest == played.definition }?.definition?.faces.orEmpty()
    GameplayCardInspection(play, viewer, remember(play.playId, id) {
        ManualInspection(play.gameId, play.playId, id, faces.map { it.faceId }, explicit = true, snapshot = played,
            stackEntryId = card.key.takeIf { card.ability != null })
    }, { inspector.hold(card, false) })
}

/**
 * A choice's card's gestures (I-D48): tap, hold (Inspect while held), and drag (positions in the root's coordinates).
 * Hold wins after [CardPeekHoldMillis] without moving; moving past the touch slop first drags.
 */
internal fun Modifier.choiceCardGestures(key: Any, tap: () -> Unit, hold: (Boolean) -> Unit,
    drag: ((DragPhase, Offset) -> Unit)? = null): Modifier = composed {
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val currentTap by rememberUpdatedState(tap)
    val currentHold by rememberUpdatedState(hold)
    val currentDrag by rememberUpdatedState(drag)
    onGloballyPositioned { coordinates = it }.pointerInput(key) {
        awaitEachGesture {
            val down = awaitFirstDown()
            fun root(p: Offset) = coordinates?.takeIf { it.isAttached }?.localToRoot(p) ?: p
            // Hold unless released (a tap), dragged past the slop or cancelled before the hold time
            var outcome = Gesture.Hold
            withTimeoutOrNull(CardPeekHoldMillis) {
                while (outcome == Gesture.Hold) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id }
                    when {
                        change == null || change.isConsumed -> outcome = Gesture.Cancel
                        !change.pressed -> { change.consume(); outcome = Gesture.Tap }
                        currentDrag != null && (change.position - down.position).getDistance() > viewConfiguration.touchSlop -> {
                            change.consume(); outcome = Gesture.Drag
                        }
                    }
                }
            }
            when (outcome) {
                Gesture.Cancel -> Unit
                Gesture.Tap -> currentTap()
                Gesture.Drag -> {
                    val dragging = currentDrag ?: return@awaitEachGesture
                    dragging(DragPhase.Start, root(down.position))
                    var last = down.position
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            if (change.positionChange() != Offset.Zero) { last = change.position; dragging(DragPhase.Move, root(last)) }
                            change.consume()
                        }
                        dragging(DragPhase.End, root(last))
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        dragging(DragPhase.Cancel, root(last)); throw cancelled
                    }
                }
                Gesture.Hold -> {
                    currentHold(true)
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            event.changes.forEach { it.consume() }
                            if (event.changes.none { it.pressed }) break
                        }
                    } finally { currentHold(false) }
                }
            }
        }
    }.semantics { onClick { currentTap(); true } }
}

internal enum class DragPhase { Start, Move, End, Cancel }

private enum class Gesture { Hold, Tap, Drag, Cancel }

/** One area of a choice between two places (I-D48): its [label], its [icon] (on "All to …"), and whether the order
 * of its cards matters ([ordered]: a number on each card, first = top). */
internal data class ChoiceArea(val label: String, val icon: ImageVector, val ordered: Boolean = false)

/**
 * The cards of a choice between two places (I-D48), by key: they start in the first area; [tap] moves a card at once to
 * the end of the other area (and back), [place] puts it at a position; with one area only, a tap moves it to the end.
 */
@Stable
internal class TwoAreas(first: List<String>, second: List<String> = emptyList()) {
    var first by mutableStateOf(first); private set
    var second by mutableStateOf(second); private set

    fun inSecond(key: String) = key in second

    fun tap(key: String, single: Boolean = false) {
        when {
            single -> if (key in first) first = first - key + key
            key in first -> { first = first - key; second = second + key }
            key in second -> { second = second - key; first = first + key }
        }
    }

    /** Puts [key] into the second area ([toSecond]) or the first at [index] (among the others there). */
    fun place(key: String, toSecond: Boolean, index: Int) {
        val a = first - key
        val b = second - key
        if (toSecond) { first = a; second = b.toMutableList().apply { add(index.coerceIn(0, size), key) } }
        else { second = b; first = a.toMutableList().apply { add(index.coerceIn(0, size), key) } }
    }

    fun allTo(toSecond: Boolean) {
        if (toSecond) { second = second + first; first = emptyList() } else { first = first + second; second = emptyList() }
    }
}

/**
 * A choice between two places (I-D48; [second] null: one place, putting cards in order): two labelled areas of cards
 * drawn like battlefield cards. A tap moves a card to the end of the other area, a drag places it where it is dropped
 * (in either area), holding inspects it; "All to …" (icon, tooltip on hold) moves every card. Reused for every choice of
 * this shape: scry, surveil, piles, discarding, a mulligan's bottom.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TwoAreaChoice(state: TwoAreas, cards: Map<String, ChoiceCardData>, first: ChoiceArea, second: ChoiceArea?,
    inspector: ChoiceInspector, modifier: Modifier = Modifier) {
    val bounds = remember { mutableStateMapOf<String, Rect>() }
    val areas = remember { mutableStateMapOf<Boolean, Rect>() }
    var dragged by remember { mutableStateOf<String?>(null) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val (width, height) = choiceCardSize()
    val half = with(LocalDensity.current) { height.toPx() / 2 }
    fun drop(key: String, at: Offset) {
        val toSecond = second != null && (areas[true]?.contains(at) == true ||
            areas[false]?.contains(at) != true && areas[true]?.let { abs(it.center.y - at.y) } ?: Float.MAX_VALUE <
                (areas[false]?.let { abs(it.center.y - at.y) } ?: Float.MAX_VALUE))
        val others = (if (toSecond) state.second else state.first) - key
        val index = others.count { other ->
            val b = bounds[other] ?: return@count false
            b.center.y < at.y - half || abs(b.center.y - at.y) <= half && b.center.x < at.x
        }
        state.place(key, toSecond, index)
    }
    Box(modifier.onGloballyPositioned { origin = it.boundsInRoot().topLeft }) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (isSecond in listOfNotNull(false, second?.let { true })) {
                val area = if (isSecond) second!! else first
                val keys = if (isSecond) state.second else state.first
                Column(Modifier.fillMaxWidth().border(1.dp, AutomatumViolet.copy(alpha = .35f), RoundedCornerShape(6.dp))
                    .background(AutomatumViolet.copy(alpha = .05f), RoundedCornerShape(6.dp))
                    .onGloballyPositioned { areas[isSecond] = it.boundsInRoot() }
                    .padding(6.dp).semantics { contentDescription = "${area.label} area" }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(area.icon, null, Modifier.size(16.dp), tint = AutomatumViolet)
                        Text("${area.label} · ${keys.size}", Modifier.weight(1f).padding(start = 4.dp), fontSize = 12.sp,
                            fontWeight = FontWeight.Bold, color = AutomatumViolet)
                        if (second != null) TipIconButton(area.icon, "All to ${area.label}", enabled = (if (isSecond) state.first else state.second).isNotEmpty(),
                            modifier = Modifier.size(32.dp)) { state.allTo(isSecond) }
                    }
                    FlowRow(Modifier.fillMaxWidth().heightIn(min = height + 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        keys.forEachIndexed { index, key ->
                            val card = cards[key] ?: return@forEachIndexed
                            ChoiceCardFace(card, Modifier.alpha(if (dragged == key) .3f else 1f)
                                .onGloballyPositioned { bounds[key] = it.boundsInRoot() }
                                .choiceCardGestures(key, tap = { state.tap(key, single = second == null) },
                                    hold = { inspector.hold(card, it) }, drag = { phase, at ->
                                        when (phase) {
                                            DragPhase.Start -> { dragged = key; pointer = at }
                                            DragPhase.Move -> pointer = at
                                            DragPhase.End -> { drop(key, at); dragged = null }
                                            DragPhase.Cancel -> dragged = null
                                        }
                                    }),
                                badge = if (area.ordered && keys.size > 1) "${index + 1}" else null)
                        }
                        if (keys.isEmpty()) Text(if (second == null) "" else "Tap a card to move it here", Modifier.align(Alignment.CenterVertically),
                            fontSize = 11.sp, color = Color(0xff4b5a5f))
                    }
                }
            }
        }
        // The dragged card follows the finger above both areas
        dragged?.let { cards[it] }?.let { card ->
            val density = LocalDensity.current
            val offset = pointer - origin
            ChoiceCardFace(card, Modifier.zIndex(2f).offset {
                with(density) { IntOffset((offset.x - width.toPx() / 2).roundToInt(), (offset.y - height.toPx() / 2).roundToInt()) }
            }.alpha(.9f))
        }
    }
}

/** An icon-only button (I-D48): holding it shows what it does in a tooltip. */
@Composable
internal fun TipIconButton(icon: ImageVector, tip: String, modifier: Modifier = Modifier, enabled: Boolean = true,
    tint: Color = AutomatumViolet, onClick: () -> Unit) {
    var tooltip by remember { mutableStateOf(false) }
    val click by rememberUpdatedState(onClick)
    Box(modifier.sizeIn(minWidth = 36.dp, minHeight = 36.dp).semantics {
        contentDescription = tip; role = Role.Button
        if (!enabled) disabled()
        onClick(tip) { if (enabled) { click(); true } else false }
    }.pointerInput(enabled) {
        detectTapGestures(onPress = { tryAwaitRelease(); tooltip = false }, onTap = { if (enabled) click() },
            onLongPress = { tooltip = true })
    }, contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(20.dp), tint = tint.copy(alpha = if (enabled) 1f else .38f))
        if (tooltip) HoldTooltip(tip)
    }
}

/** A light button with an icon and its text (I-D48), for slim bars and secondary actions. */
@Composable
internal fun BarButton(text: String, icon: ImageVector, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(onClick, modifier, enabled = enabled, contentPadding = PaddingValues(horizontal = 8.dp)) {
        Icon(icon, null, Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, maxLines = 1)
    }
}

/** A choice's button with an icon and its text (I-D48); [primary]: filled violet. */
@Composable
internal fun ChoiceButton(text: String, icon: ImageVector, modifier: Modifier = Modifier, enabled: Boolean = true,
    primary: Boolean = false, onClick: () -> Unit) {
    val content: @Composable RowScope.() -> Unit = {
        Icon(icon, null, Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    val padding = PaddingValues(start = 12.dp, end = 16.dp)
    if (primary) Button(onClick, modifier, enabled = enabled, contentPadding = padding,
        colors = ButtonDefaults.buttonColors(containerColor = AutomatumViolet), content = content)
    else OutlinedButton(onClick, modifier, enabled = enabled, contentPadding = padding,
        border = BorderStroke(1.dp, if (enabled) AutomatumViolet.copy(alpha = .6f) else Color(0x1f000000)), content = content)
}
