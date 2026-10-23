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
import androidx.compose.ui.window.Popup
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
    enabled: Boolean, landscape: Boolean, names: Map<String, String>, extras: @Composable () -> Unit = {},
    submitAll: ((List<PlayAction>) -> Unit)? = null, tools: List<MenuControl> = emptyList(), submit: (PlayAction) -> Boolean) {
    val card = state.board.cards.single { it.id == scope.contextId }
    fun emit(actions: List<PlayAction>) { if (submitAll != null) submitAll(actions) else actions.forEach { submit(it) } }
    fun send(factory: (PlayCard) -> PlayAction?) { emit(scope.cardActions(state, factory)) }
    val faces = state.definitions.singleOrNull { it.digest == card.definition }?.definition?.faces.orEmpty()
    var more by remember(scope) { mutableStateOf(false) }
    val other = if (card.covered) faces.singleOrNull { it.faceId == card.faceId } else faces.firstOrNull { it.faceId != card.faceId }
    val controls = listOf(
        MenuControl("Inspect") { actions.inspect(state, viewer) },
        MenuControl("Inspect other side", enabled && other != null) {
            other?.let { submit(PlayAction.InspectFace(card.id, it.faceId, card.containerId)) }
        },
        MenuControl(if (card.covered) "Turn face up" else "Turn face down", enabled) { send { PlayAction.SetCovered(it.id, !card.covered) } }
    ) + tools.filterNot { it.name in listOf("Declare + tap", "Unlink") } + listOf(
        MenuControl("Reset corner", enabled) { send { PlayAction.ResetCorner(it.id) } }
    ) + tools.filter { it.name == "Declare + tap" } + listOf(
        MenuControl("Edit card note", enabled) { actions.edit(ManualValue(ManualValueKind.NOTE), state, viewer) }
    ) + tools.filter { it.name == "Unlink" } + state.board.players.map { player ->
        MenuControl("Owner ${names[player.accountId] ?: "Player"}", enabled, card.owner == player.accountId,
            if (player.accountId == viewer) OwnTableColor else OpponentTableColor) { send { PlayAction.SetOwner(it.id, player.accountId) } }
    } + MenuControl("More actions", selected = more) { more = !more }
    val general: @Composable () -> Unit = {
        Column(Modifier.horizontalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ManualCardPreview(state, viewer, card.id, scope.targetIds.size, names)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (row in controls.take(12).chunked(6)) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (control in row) control.Square()
                    }
                }
            }
            for (row in controls.drop(12).chunked(7)) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (control in row) control.Square()
            }
            if (faces.size > 1) Row(Modifier.horizontalScroll(rememberScrollState())) {
                for ((index, face) in faces.withIndex()) ManualActionSquare("Switch printed face ${index + 1}", "", enabled,
                    selected = card.faceId == face.faceId, onClick = {
                        send { target ->
                            val targetFaces = state.definitions.singleOrNull { it.digest == target.definition }?.definition?.faces.orEmpty()
                            targetFaces.getOrNull(index)?.takeIf { targetFaces.size > 1 }?.let { PlayAction.SetFace(target.id, it.faceId) }
                        }
                    })
            }
        }
    }
    val values: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ManualActionSquare("Set corner", card.corner, enabled, onClick = { actions.edit(ManualValue(ManualValueKind.CORNER), state, viewer) })
                val targets = scope.cardActions(state) { PlayAction.SetCorner(it.id, it.corner) }
                val hasPair = targets.any { target -> state.board.cards.single { it.id == (target as PlayAction.SetCorner).cardId }.corner.contains('/') }
                val controls = if (hasPair) listOf(Triple(CornerPart.BOTH, 1, "+/+"), Triple(CornerPart.BOTH, -1, "−/−"),
                    Triple(CornerPart.RIGHT, 1, "•/+"), Triple(CornerPart.RIGHT, -1, "•/−"),
                    Triple(CornerPart.LEFT, 1, "+/•"), Triple(CornerPart.LEFT, -1, "−/•"))
                    else listOf(Triple(CornerPart.WHOLE, 1, "+"), Triple(CornerPart.WHOLE, -1, "−"))
                for ((part, delta, symbol) in controls) {
                    val applicable = scope.cardActions(state) {
                        if (ManualText.corner(it.corner, part, delta) != null) PlayAction.AdjustCorner(it.id, part, delta) else null
                    }
                    ManualActionSquare("Adjust corner ${part.name.lowercase()} ${if (delta > 0) "+1" else "−1"}", symbol,
                        enabled && applicable.isNotEmpty(), onClick = { emit(applicable) })
                }
                // Mixed integer/pair selections keep each individually usable full path reachable.
                if (hasPair) for (delta in listOf(1, -1)) {
                    val applicable = scope.cardActions(state) {
                        if (ManualText.corner(it.corner, CornerPart.WHOLE, delta) != null)
                            PlayAction.AdjustCorner(it.id, CornerPart.WHOLE, delta) else null
                    }
                    if (applicable.isNotEmpty()) ManualActionSquare("Adjust integer corner $delta", if (delta > 0) "+" else "−",
                        enabled, onClick = { emit(applicable) })
                }
            }
            CounterControls(card.counters, player = false, enabled = enabled,
                set = { actions.edit(ManualValue(ManualValueKind.COUNTER, it), state, viewer) },
                applicable = { color, delta -> scope.cardActions(state) {
                    if (ManualText.add(it.counters[color] ?: "0", delta.toLong()) != null)
                        PlayAction.AdjustCounter(CounterTarget.CARD, it.id, color, delta.toString()) else null
                }.isNotEmpty() },
                adjust = { color, delta -> send {
                    if (ManualText.add(it.counters[color] ?: "0", delta.toLong()) != null)
                        PlayAction.AdjustCounter(CounterTarget.CARD, it.id, color, delta.toString()) else null
                } })
        }
    }
    TableMenuPanel(actions::close, width = if (landscape) 816.dp else 400.dp) {
        if (landscape) Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f)) { general() }
            Column(Modifier.weight(1f)) { values() }
        } else { general(); values() }
        if (more) extras()
    }
}

@Composable
internal fun ManualCardPreview(state: PlayState, viewer: String, cardId: String, count: Int, names: Map<String, String> = emptyMap()) {
    val card = state.board.cards.singleOrNull { it.id == cardId } ?: return
    val faces = ManualDisclosure.permittedFaces(state, card, viewer, inspecting = false)
    val face = faces.singleOrNull()
    val owner = if (card.owner == viewer) Color(0xff287f85) else Color(0xffa84f58)
    Column(Modifier.width(48.dp).height(100.dp).border(2.dp, owner, MaterialTheme.shapes.extraSmall).padding(3.dp)
        .semantics { contentDescription = "${face?.name ?: "Covered card"}, owner ${names[card.owner] ?: "Player"}, ${if (card.tapped) "tapped" else "untapped"}, $count captured targets" }) {
        Text(face?.name ?: if (card.covered) "Covered" else "Unavailable", maxLines = 1,
            overflow = TextOverflow.Ellipsis, fontSize = 8.sp, lineHeight = 9.sp, fontWeight = FontWeight.Bold)
        Box(Modifier.weight(1f).fillMaxWidth().graphicsLayer { rotationZ = if (card.tapped) 90f else 0f }) {
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
    set: (CounterColor) -> Unit, applicable: (CounterColor, Int) -> Boolean, adjust: (CounterColor, Int) -> Unit) {
    for (pair in CounterColor.entries.chunked(2)) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        for ((index, color) in pair.withIndex()) {
            if (index > 0) VerticalDivider(Modifier.height(36.dp))
            val name = color.name.lowercase()
            val value = values[color]
            ManualActionSquare("Set $name counter, ${value?.takeIf { it.isNotEmpty() } ?: "no value"}",
                if (player) ManualText.playerCounterText(value) else ManualText.cardCounterText(value), enabled,
                fill = counterColor(color), circle = true, tooltipName = "Set $name counter", iconTint = counterOutline(color), onClick = { set(color) })
            for (delta in listOf(1, -1)) ManualActionSquare("${if (delta > 0) "Increase" else "Decrease"} $name counter",
                if (delta > 0) "+" else "−", enabled && applicable(color, delta), onClick = { adjust(color, delta) })
        }
    }
}

/** One pointer recognizer owns click/assigned hold/name tooltip. Sync updates callbacks, not the gesture. */
@Composable
internal fun ManualActionSquare(name: String, symbol: String, enabled: Boolean = true, fill: Color? = null,
    selected: Boolean = false, circle: Boolean = false, tooltipName: String = name,
    assignedHold: (() -> Unit)? = null, holdName: String? = null, iconTint: Color? = null,
    bordered: Boolean = true, modifier: Modifier = Modifier, labelled: Boolean = false, onClick: () -> Unit) {
    val currentClick by rememberUpdatedState(onClick)
    val currentHold by rememberUpdatedState(assignedHold)
    var tooltip by remember { mutableStateOf(false) }
    val background = fill ?: Color.White
    Box(modifier.then(if (labelled) Modifier.fillMaxWidth().heightIn(min = 48.dp) else Modifier.size(48.dp)).semantics(mergeDescendants = true) {
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
        if (tooltip) Popup(alignment = Alignment.TopCenter, offset = androidx.compose.ui.unit.IntOffset(0, -32)) {
            Surface(color = MenuInk, contentColor = Color.White, shadowElevation = 4.dp, shape = MaterialTheme.shapes.extraSmall) {
                Text(tooltipName, Modifier.widthIn(max = 240.dp).padding(8.dp), style = MaterialTheme.typography.labelMedium)
            }
        }
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
