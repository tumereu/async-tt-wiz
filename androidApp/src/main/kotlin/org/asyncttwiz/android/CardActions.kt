package org.asyncttwiz.android

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import org.asyncttwiz.protocol.*

internal fun counterColor(color: CounterColor): Color = when (color) {
    CounterColor.RED -> Color(0xffedafb3)
    CounterColor.BLUE -> Color(0xffb0d6f0)
    CounterColor.GREEN -> Color(0xffb1dab9)
    CounterColor.YELLOW -> Color(0xfff3e49e)
    CounterColor.PURPLE -> Color(0xffd8b8ec)
    CounterColor.GRAY -> Color(0xffcdd1d4)
}

/** Captures no board snapshot: every composition reads the provided current accepted/predicted state. */
@Composable
internal fun CardActions(state: PlayState, viewer: String, scope: ManualScope, actions: ManualActions,
    enabled: Boolean, names: Map<String, String>,
    submitAll: ((List<PlayAction>) -> Unit)? = null, tools: List<MenuControl> = emptyList(), previewViewId: String? = null,
    inspect: (() -> Unit)? = null, submit: (PlayAction) -> Boolean) {
    val card = TablePresentation.cardForActions(state, scope.contextId)
    fun emit(actions: List<PlayAction>) { if (submitAll != null) submitAll(actions) else actions.forEach { submit(it) } }
    fun send(factory: (PlayCard) -> PlayAction?) { emit(scope.cardActions(state, factory)) }
    val faces = state.definitions.singleOrNull { it.digest == card.definition }?.definition?.faces.orEmpty()
    val other = if (card.covered) faces.singleOrNull { it.faceId == card.faceId } else faces.firstOrNull { it.faceId != card.faceId }
    val entries = mutableListOf<RingEntry>()
    fun add(id: String, label: String, control: MenuControl) { entries += RingEntry(id, label, control) }
    add("inspect", "Inspect", MenuControl("Inspect") { if (inspect != null) inspect() else actions.inspect(state, viewer) })
    add("otherSide", "Other side", MenuControl("Inspect other side", enabled && other != null) {
        other?.let { submit(PlayAction.InspectFace(card.id, it.faceId, card.containerId)) }
    })
    add("flip", if (card.covered) "Face up" else "Face down",
        MenuControl(if (card.covered) "Turn face up" else "Turn face down", enabled) { send { PlayAction.SetCovered(it.id, !card.covered) } })
    add("resetCorner", "Reset P/T", MenuControl("Reset corner", enabled) { send { PlayAction.ResetCorner(it.id) } })
    add("note", "Note", MenuControl("Edit card note", enabled) { actions.edit(ManualValue(ManualValueKind.NOTE), state, viewer) })
    val ownerColor = if (card.owner == viewer) OwnTableColor else OpponentTableColor
    if (card.token) add("owner", "Owner", MenuControl("Owner ${names[card.owner] ?: "Player"}; switch to other player", enabled, color = ownerColor) {
        val next = state.board.players.firstOrNull { it.accountId != card.owner }
        if (next != null) send { if (it.token) PlayAction.SetOwner(it.id, next.accountId) else null }
    })
    for ((index, face) in faces.withIndex()) if (faces.size > 1) {
        add("face/$index", "Face ${index + 1}", MenuControl("Switch printed face ${index + 1}", enabled,
            selected = card.faceId == face.faceId) {
            send { target ->
                val targetFaces = state.definitions.singleOrNull { it.digest == target.definition }?.definition?.faces.orEmpty()
                targetFaces.getOrNull(index)?.takeIf { targetFaces.size > 1 }?.let { PlayAction.SetFace(target.id, it.faceId) }
            }
        })
    }
    fun corner(part: CornerPart, delta: Int) = scope.cardActions(state) {
        if (ManualText.corner(it.corner, part, delta) != null) PlayAction.AdjustCorner(it.id, part, delta) else null
    }
    fun whole(delta: Int) = scope.cardActions(state) {
        val part = if ('/' in it.corner) CornerPart.BOTH else CornerPart.WHOLE
        if (ManualText.corner(it.corner, part, delta) != null) PlayAction.AdjustCorner(it.id, part, delta) else null
    }
    fun adjustment(name: String, requests: List<PlayAction>) = MenuControl(name, enabled && requests.isNotEmpty()) { emit(requests) }
    entries += RingEntry("corner", "P/T", MenuControl("Set corner", enabled) { actions.edit(ManualValue(ManualValueKind.CORNER), state, viewer) },
        RingCompound(card.corner.ifEmpty { "—" }, adjustment("Decrease corner", whole(-1)), adjustment("Increase corner", whole(1)), drag = { delta ->
            if (enabled) emit(scope.cardActions(state) { target ->
                cornerDragValue(target.corner, delta)?.let { PlayAction.SetCorner(target.id, it) }
            })
        }, dragValue = card.corner, corner = true))
    entries += entries.last().copy(id = "corner/counters")
    if (scope.cardActions(state) { if ('/' in it.corner) PlayAction.SetCorner(it.id, it.corner) else null }.isNotEmpty()) {
        for ((part, delta, symbol) in listOf(Triple(CornerPart.LEFT, 1, "+/•"), Triple(CornerPart.RIGHT, 1, "•/+"),
            Triple(CornerPart.LEFT, -1, "−/•"), Triple(CornerPart.RIGHT, -1, "•/−"))) {
            add("corner/${part.name}/$delta", symbol,
                adjustment("Adjust corner ${part.name.lowercase()} ${if (delta > 0) "+1" else "−1"}", corner(part, delta)))
        }
    }
    for (color in CounterColor.entries) {
        fun adjust(delta: Int) = adjustment("${if (delta > 0) "Increase" else "Decrease"} ${color.name.lowercase()} counter", scope.cardActions(state) {
            if (ManualText.add(it.counters[color] ?: "0", delta.toLong()) != null)
                PlayAction.AdjustCounter(CounterTarget.CARD, it.id, color, delta.toString()) else null
        })
        entries += RingEntry("counter/${color.name}", color.name.lowercase(), MenuControl("Set ${color.name.lowercase()} counter", enabled,
            color = counterOutline(color)) { actions.edit(ManualValue(ManualValueKind.COUNTER, color), state, viewer) },
            RingCompound(ManualText.cardCounterText(card.counters[color]), adjust(-1), adjust(1), drag = { delta ->
                if (enabled) emit(scope.cardActions(state) {
                    if (ManualText.add(it.counters[color] ?: "0", delta) != null)
                        PlayAction.AdjustCounter(CounterTarget.CARD, it.id, color, delta.toString()) else null
                })
            }, dragValue = card.counters[color]?.takeIf { it.isNotEmpty() } ?: "0"), counterColor(color))
    }
    for (tool in tools.distinctBy { it.name }) add(tool.name, ringShortLabel(tool.name), tool)
    val view = previewViewId ?: card.id
    val anchors = LocalCardRingAnchors.current
    if (view in anchors) CardActionRing(state.gameId, viewer, scope.contextId, view, entries, card.owner == viewer, ownerColor, actions::close)
    else BoxWithConstraints(Modifier.fillMaxSize()) {
        // A browser/off-screen source has no table drawing: retain the old preview, not an invisible anchor.
        val anchor = TableBounds(maxWidth.value / 2 - 24, maxHeight.value / 2 - 50, 48f, 100f)
        Box(Modifier.offset(anchor.x.dp, anchor.y.dp)) { ManualCardPreview(state, viewer, card.id, scope.targetIds.size, names, previewViewId) }
        CompositionLocalProvider(LocalCardRingAnchors provides (anchors + (view to anchor))) {
            CardActionRing(state.gameId, viewer, scope.contextId, view, entries, card.owner == viewer, ownerColor, actions::close)
        }
    }
}

@Composable
internal fun ManualCardPreview(state: PlayState, viewer: String, cardId: String, count: Int, names: Map<String, String> = emptyMap(),
    viewId: String? = null) {
    val card = state.board.cards.singleOrNull { it.id == cardId } ?: return
    val faces = ManualDisclosure.permittedFaces(state, card, viewer, inspecting = false)
    val face = faces.singleOrNull()
    val owner = if (card.owner == viewer) Color(0xff287f85) else Color(0xffa84f58)
    val tapped = card.tapped && viewId !in CombatStackRules.stack(state.board).cards &&
        state.board.containers.none { it.id == card.containerId && it.zone == PlayZone.STACK }
    Column(Modifier.width(48.dp).height(100.dp).cardFrame(card.token, owner, viewId in CombatStackRules.projectionIds(state.board))
        .padding(horizontal = if (card.token) 8.dp else 3.dp, vertical = 4.dp)
        .semantics { contentDescription = "${face?.name ?: "Covered card"}, owner ${names[card.owner] ?: "Player"}, ${if (tapped) "tapped" else "untapped"}, $count captured targets" }) {
        Text(face?.name ?: if (card.covered) "Covered" else "Unavailable", maxLines = 1,
            overflow = TextOverflow.Ellipsis, fontSize = 8.sp, lineHeight = 9.sp, fontWeight = FontWeight.Bold)
        Box(Modifier.weight(1f).fillMaxWidth().graphicsLayer { rotationZ = if (tapped) 90f else 0f }) {
            if (face != null) {
                val definition = state.definitions.single { it.digest == card.definition }.definition
                val inspected = InspectedCard.retained(card.definition, definition)
                if (!face.scryfallId.isNullOrBlank() || !face.imageUri.isNullOrBlank())
                    CardArtwork(inspected.artwork[definition.faces.indexOf(face)], face.name, 180, 240, Modifier.fillMaxSize())
                else MenuIcon("Declare blockers", Modifier.align(Alignment.Center), tint = owner.copy(alpha = .65f))
            } else MenuIcon("Turn face down", Modifier.align(Alignment.Center))
        }
        Row { for ((color, value) in card.counters) Text(value, Modifier.background(counterColor(color)),
            maxLines = 1, fontSize = 8.sp, lineHeight = 9.sp) }
        Text("${if (count > 1) "×$count " else ""}${card.corner}", Modifier.align(Alignment.End), maxLines = 1,
            fontSize = 10.sp, lineHeight = 11.sp, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun CounterControls(values: Map<CounterColor, String>, player: Boolean, enabled: Boolean,
    set: (CounterColor) -> Unit, applicable: (CounterColor, Int) -> Boolean, adjust: (CounterColor, Long) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= 296.dp) 2 else 1
        val horizontalOverflow = maxWidth < 144.dp
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (pair in CounterColor.entries.chunked(columns)) Row(Modifier.fillMaxWidth()
                .then(if (horizontalOverflow) Modifier.horizontalScroll(rememberScrollState()) else Modifier),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                for ((index, color) in pair.withIndex()) {
                    if (index > 0) VerticalDivider(Modifier.height(36.dp))
                    val name = color.name.lowercase()
                    val value = values[color]
                    ManualActionSquare("Set $name counter, ${value?.takeIf { it.isNotEmpty() } ?: "no value"}",
                        if (player) ManualText.playerCounterText(value) else ManualText.cardCounterText(value), enabled,
                        fill = counterColor(color), circle = true, tooltipName = "Set $name counter", iconTint = counterOutline(color),
                        dragValue = value ?: "0", onDelta = { adjust(color, it) }, onClick = { set(color) })
                    for (delta in listOf(1, -1)) ManualActionSquare("${if (delta > 0) "Increase" else "Decrease"} $name counter",
                        if (delta > 0) "+" else "−", enabled && applicable(color, delta), onClick = { adjust(color, delta.toLong()) })
                }
            }
        }
    }
}

/** One pointer recognizer owns click/assigned hold/name tooltip. Sync updates callbacks, not the gesture. */
@Composable
internal fun ManualActionSquare(name: String, symbol: String, enabled: Boolean = true, fill: Color? = null,
    selected: Boolean = false, circle: Boolean = false, tooltipName: String = name,
    assignedHold: (() -> Unit)? = null, holdName: String? = null, iconTint: Color? = null,
    bordered: Boolean = true, modifier: Modifier = Modifier, labelled: Boolean = false,
    dragValue: String? = null, onDelta: ((Long) -> Unit)? = null, onClick: () -> Unit) {
    val currentClick by rememberUpdatedState(onClick)
    val currentHold by rememberUpdatedState(assignedHold)
    var tooltip by remember { mutableStateOf(false) }
    var delta by remember { mutableStateOf<Long?>(null) }
    val background = fill ?: Color.White
    val dragModifier = if (onDelta != null) Modifier.valueDragGestures(enabled && TableActions.numeric(dragValue ?: "0"),
        preview = { delta = it }, commit = onDelta) else Modifier
    Box(modifier.then(if (labelled) Modifier.fillMaxWidth().heightIn(min = 48.dp) else Modifier.size(48.dp))
        .pressFeedback(enabled).then(dragModifier).semantics(mergeDescendants = true) {
        contentDescription = name
        role = Role.Button
        this.selected = selected
        if (!enabled) disabled()
        onClick { if (enabled) currentClick(); enabled }
        if (assignedHold != null) onLongClick(holdName) { if (enabled) currentHold?.invoke(); enabled }
    }.onKeyEvent {
        if (enabled && it.key in listOf(Key.Enter, Key.Spacebar, Key.DirectionCenter)) {
            if (it.type == KeyEventType.KeyUp) currentClick()
            true
        } else false
    }.focusable(enabled).pointerInput(enabled) {
        val press = ManualActionPress()
        awaitEachGesture {
            val down = awaitFirstDown()
            val capturedClick = currentClick
            val capturedHold = currentHold
            down.consume()
            press.start()
            try {
                var released = false
                withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || change.isConsumed || (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                            press.cancel(); break
                        }
                        if (!change.pressed) { released = true; change.consume(); break }
                    }
                }
                if (released) { if (press.release() && enabled) capturedClick() }
                else if (!press.consumed) {
                    press.hold(if (enabled) capturedHold else null)
                    tooltip = press.tooltip
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || !change.pressed || change.isConsumed ||
                            (change.position - down.position).getDistance() > viewConfiguration.touchSlop) break
                        change.consume()
                    }
                    press.cancel()
                }
            } finally { tooltip = false }
        }
    }, contentAlignment = Alignment.Center) {
        if (labelled) {
            val tint = (if (fill == null) MenuInk else Color.White).copy(alpha = if (enabled) 1f else .38f)
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).background(background, MaterialTheme.shapes.extraSmall)
                .border(1.dp, MenuBorder, MaterialTheme.shapes.extraSmall).padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MenuIcon(name, tint = tint)
                Text(name, Modifier.weight(1f), color = tint, fontSize = 13.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold)
            }
        } else {
        Box(Modifier.fillMaxSize().then(if (bordered) Modifier.border(if (selected) 2.dp else 1.dp,
            if (selected) iconTint ?: OwnTableColor else MenuBorder, MaterialTheme.shapes.extraSmall) else Modifier)
            .background(Color.White, MaterialTheme.shapes.extraSmall), contentAlignment = Alignment.Center) {
        Box(Modifier.size(if (circle) 32.dp else 44.dp)
            .background(background.copy(alpha = if (circle) .25f else if (enabled) 1f else .4f), if (circle) CircleShape else MaterialTheme.shapes.extraSmall)
            .then(if (circle) Modifier.border(1.dp, iconTint ?: MenuBorder, CircleShape) else Modifier),
            contentAlignment = Alignment.Center) {
            val tint = (if (circle) MenuInk else iconTint ?: MenuInk).copy(alpha = if (enabled) 1f else .38f)
            if (name.startsWith("Set ") || name.startsWith("Adjust corner") || name.startsWith("Adjust integer"))
                Text(symbol, Modifier.padding(2.dp), color = tint, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    fontSize = if (symbol.length > 7) 10.sp else if (name.startsWith("Set ") && !circle) 16.sp else 14.sp,
                    fontWeight = FontWeight.Bold)
            else MenuIcon(name, tint = tint)
        }
        }
        }
        if (tooltip) HoldTooltip(tooltipName)
        delta?.let { HoldTooltip(valueDragLabel(dragValue, it)) }
    }
}

internal fun counterOutline(color: CounterColor): Color = when (color) {
    CounterColor.RED -> Color(0xffb64754)
    CounterColor.BLUE -> Color(0xff287fbb)
    CounterColor.GREEN -> Color(0xff268477)
    CounterColor.YELLOW -> Color(0xffaa902f)
    CounterColor.PURPLE -> Color(0xff8952b6)
    CounterColor.GRAY -> Color(0xff71817a)
}
