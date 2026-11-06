package org.asyncttwiz.android

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch
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

/** STK-044 (owner playtest 2026-11-06): the width and height of a choice's card: the 28 × 40 dp base the hand, piles and
 * Stack use, scaled by the Panels card size. */
@Composable
internal fun choiceCardSize(): Pair<androidx.compose.ui.unit.Dp, androidx.compose.ui.unit.Dp> {
    val scale = rememberCardAppearance().size(CardArea.PANELS)
    return 28.dp * scale to 40.dp * scale
}

/** A choice's card face, as the battlefield draws it: the name on top, the art below, the owner's outline; covered
 * when hidden. [badge]: a small number in its corner (its place in an order); [selected]: a violet ring. [small] (a
 * panel's source, the cards a choice is about) draws its name smaller; every panel card has the same size. */
@Composable
internal fun ChoiceCardFace(card: ChoiceCardData, modifier: Modifier = Modifier, selected: Boolean = false, badge: String? = null,
    small: Boolean = false) {
    val (width, height) = choiceCardSize()
    val owner = if (card.own) OwnTableColor else OpponentTableColor
    // A tapped card lies sideways, taking its sideways room
    Box(modifier.size(if (card.tapped) height else width, if (card.tapped) width else height)
        .semantics { contentDescription = card.name + if (selected) ", chosen" else "" }, contentAlignment = Alignment.Center) {
        val ability = card.ability
        if (ability != null && card.shown) AbilityTokenFace(ability, card.art, card.own, Modifier.requiredSize(width, height),
            card.typeIcon)
        else Box(Modifier.requiredSize(width, height).then(if (card.tapped) Modifier.rotate(90f) else Modifier)
            .cardFrame(card.token, owner).then(if (card.shown) Modifier else Modifier.background(Color(0xff506b61)))) {
            // The battlefield card's proportions at any Panels size: the name a quarter of the width high
            val name = (width.value * if (small) .24f else .26f).sp
            if (card.shown) Column(Modifier.fillMaxSize().padding(horizontal = width * .07f, vertical = height * .04f)) {
                Text(card.name, fontSize = name, lineHeight = name * 1.15f, fontWeight = FontWeight.Bold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, color = MenuInk)
                Box(Modifier.fillMaxWidth().weight(1f).padding(vertical = height * .04f)) {
                    // As on the battlefield: the type glyph while there is no art
                    CardArtwork(card.art, "", 192, 140, Modifier.fillMaxSize(), crop = true, placeholder = {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(card.typeIcon, fontSize = (width.value * .45f).sp, color = MenuInk)
                        }
                    })
                }
                Spacer(Modifier.height(height * .12f))
            }
        }
        if (selected) Box(Modifier.fillMaxSize().border(3.dp, AutomatumViolet, RoundedCornerShape(4.dp)))
        if (badge != null) Surface(Modifier.align(Alignment.BottomStart).padding(1.dp), shape = CircleShape,
            color = AutomatumViolet, contentColor = Color.White) {
            Text(badge, Modifier.padding(horizontal = 4.dp), fontSize = 8.sp, lineHeight = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * Holding a choice's card inspects it (I-D48), as on the table: the large card shows while held. Only cards the viewer
 * may see ([ChoiceCardData.shown]) open; a covered one doesn't.
 */
internal class ChoiceInspector {
    var held by mutableStateOf<ChoiceCardData?>(null); private set
    /** TBL-127: the held card's Inspect scrolls with the holding finger. */
    val scroll = ScrollState(0)
    /** TBL-104: a drag to the left pins the held card's Inspect. */
    val swipe = InspectSwipe()
    /** The screen height in px the finger's movement is measured against ([holdPreviewScroll]). */
    var screenHeight = 0f

    init { swipe.onClose = { held = null } }

    fun hold(card: ChoiceCardData, holding: Boolean) {
        if (holding) {
            if (!card.shown || card.cardId == null) return
            held = card
            scroll.dispatchRawDelta(-scroll.value.toFloat())
            swipe.begin()
        } else if (held?.key == card.key && !swipe.released()) held = null
    }

    /** The holding finger is [dx], [dy] dp from where the hold began and moved [step] px up or down just now (positive
     * reveals lower content): the pinned swipe's, or else the scroll's (TBL-127). */
    fun moved(dx: Float, dy: Float, step: Float) {
        if (held == null) return
        if (!swipe.moved(dx, dy)) scroll.holdPreviewScroll(step, screenHeight)
    }

    /** Closes the Inspect, pinned or not. */
    fun close() {
        swipe.begin()
        held = null
    }
}

/**
 * Where an in-game panel's Inspect shows (TBL-104): the table screen's host draws it, outside the panel's own layout, so
 * opening it never moves anything in the panel (owner playtest 2026-11-06: holding a revealed card made the panel jump).
 */
@Stable
internal class InspectHost {
    var shown by mutableStateOf<HostedInspection?>(null); private set
    fun show(inspection: HostedInspection) { shown = inspection }
    fun hide(inspection: HostedInspection) { if (shown == inspection) shown = null }
}

/** One panel's Inspect for [InspectHost]. */
internal data class HostedInspection(val play: TableBoard, val viewer: String, val inspection: ManualInspection,
    val inspector: ChoiceInspector)

/** The table screen's [InspectHost]; null outside it (a panel then draws its Inspect itself). */
internal val LocalInspectHost = staticCompositionLocalOf<InspectHost?> { null }

/** Draws what [host] shows. */
@Composable
internal fun HostedInspectionView(host: InspectHost) {
    val shown = host.shown ?: return
    GameplayCardInspection(shown.play, shown.viewer, shown.inspection, shown.inspector::close,
        scroll = shown.inspector.scroll, swipe = shown.inspector.swipe)
}

@Composable
internal fun ChoiceInspection(inspector: ChoiceInspector, play: TableBoard, viewer: String) {
    val screenHeight = holdPreviewScreenHeight()
    SideEffect { inspector.screenHeight = screenHeight }
    val card = inspector.held
    val played = card?.cardId?.let { id -> play.board.cards.firstOrNull { it.id == id } }
    val inspection = if (card == null || played == null) null else remember(play.playId, played.id, card.key) {
        val faces = play.definitions.firstOrNull { it.digest == played.definition }?.definition?.faces.orEmpty()
        ManualInspection(play.gameId, play.playId, played.id, faces.map { it.faceId }, explicit = true, snapshot = played,
            stackEntryId = card.key.takeIf { card.ability != null })
    }
    val host = LocalInspectHost.current
    if (host != null) {
        // Drawn by the host: nothing of it is laid out here
        val hosted = inspection?.let { HostedInspection(play, viewer, it, inspector) }
        DisposableEffect(host, hosted) {
            if (hosted != null) host.show(hosted)
            onDispose { if (hosted != null) host.hide(hosted) }
        }
        return
    }
    if (inspection != null) GameplayCardInspection(play, viewer, inspection, inspector::close, scroll = inspector.scroll,
        swipe = inspector.swipe)
}

/**
 * A choice's card's gestures (I-D48): tap, hold (Inspect while held), and drag (positions in the root's coordinates).
 * Hold wins after [CardPeekHoldMillis] without moving; moving past the touch slop first drags. While held, the finger's
 * movement goes to [moved] (TBL-127's scroll, the pinned swipe: [ChoiceInspector.moved]).
 */
internal fun Modifier.choiceCardGestures(key: Any, tap: () -> Unit, hold: (Boolean) -> Unit,
    drag: ((DragPhase, Offset) -> Unit)? = null,
    moved: ((dx: Float, dy: Float, step: Float) -> Unit)? = null): Modifier = composed {
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val currentTap by rememberUpdatedState(tap)
    val currentHold by rememberUpdatedState(hold)
    val currentDrag by rememberUpdatedState(drag)
    val currentMoved by rememberUpdatedState(moved)
    val density = LocalDensity.current.density
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
                    var start: Offset? = null
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            event.changes.firstOrNull { it.id == down.id }?.takeIf { it.pressed }?.let { change ->
                                val from = start ?: change.previousPosition.also { start = it }
                                val step = -change.positionChange().y
                                if (change.positionChange() != Offset.Zero) currentMoved?.invoke((change.position.x - from.x) / density,
                                    (change.position.y - from.y) / density, step)
                            }
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

/** TBL-038's quadratic ease-out: a moving card starts at once and slows into place. */
private val EaseOutQuadratic = Easing { 1f - (1f - it) * (1f - it) }

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
    // STK-044 (owner playtest 2026-11-06): a card that moves (a tap, a drop, "All to …") slides from where it was to its
    // new place and the others slide to close the gap, at the card move speed (TBL-038)
    val density = LocalDensity.current.density
    val multiplier by rememberCardMovePreferences(LocalChoiceFrame.current.viewer).multiplierState()
    val scope = rememberCoroutineScope()
    val slides = remember { mutableStateMapOf<String, Animatable<Offset, AnimationVector2D>>() }
    val placed = remember { HashMap<String, Offset>() }
    var box by remember { mutableStateOf<LayoutCoordinates?>(null) }
    fun placedAt(key: String, at: Offset) {
        val before = placed.put(key, at) ?: return
        if (before == at) return
        val slide = slides.getOrPut(key) { Animatable(Offset.Zero, Offset.VectorConverter) }
        val from = before + slide.value - at
        val millis = cardMoveDurationMillis(from.getDistance() / density, DEFAULT_CARD_MOVE_SPEED * multiplier)
        scope.launch { slide.snapTo(from); slide.animateTo(Offset.Zero, tween(millis, easing = EaseOutQuadratic)) }
    }
    fun drop(key: String, at: Offset) {
        // The dropped card slides from under the finger
        placed[key] = at - origin - Offset(width.value * density / 2, height.value * density / 2)
        slides[key]?.let { scope.launch { it.snapTo(Offset.Zero) } }
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
    Box(modifier.onGloballyPositioned { origin = it.boundsInRoot().topLeft; box = it }) {
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
                            key(key) {
                            ChoiceCardFace(card, Modifier
                                .onGloballyPositioned { c ->
                                    bounds[key] = c.boundsInRoot()
                                    box?.takeIf { it.isAttached && c.isAttached }?.let { placedAt(key, it.localPositionOf(c, Offset.Zero)) }
                                }
                                .graphicsLayer { slides[key]?.value?.let { translationX = it.x; translationY = it.y } }
                                .zIndex(if (slides[key]?.isRunning == true) 1f else 0f)
                                .alpha(if (dragged == key) .3f else 1f)
                                .choiceCardGestures(key, tap = { state.tap(key, single = second == null) },
                                    hold = { inspector.hold(card, it) }, moved = inspector::moved, drag = { phase, at ->
                                        when (phase) {
                                            DragPhase.Start -> { dragged = key; pointer = at }
                                            DragPhase.Move -> pointer = at
                                            DragPhase.End -> { drop(key, at); dragged = null }
                                            DragPhase.Cancel -> dragged = null
                                        }
                                    }),
                                badge = if (area.ordered && keys.size > 1) "${index + 1}" else null)
                        } }
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
