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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
    submitAll: ((List<PlayAction>) -> Unit)? = null, submit: (PlayAction) -> Boolean) {
    val card = state.board.cards.single { it.id == scope.contextId }
    fun emit(actions: List<PlayAction>) { if (submitAll != null) submitAll(actions) else actions.forEach { submit(it) } }
    fun send(factory: (PlayCard) -> PlayAction?) { emit(scope.cardActions(state, factory)) }
    val general: @Composable () -> Unit = {
        Column {
            Row {
                ManualCardPreview(state, viewer, card.id, scope.targetIds.size)
                Column {
                    Row {
                        ManualActionSquare("Inspect", "⌕", onClick = { actions.inspect(state, viewer) })
                        val faces = state.definitions.singleOrNull { it.digest == card.definition }?.definition?.faces.orEmpty()
                        val other = if (card.covered) faces.singleOrNull { it.faceId == card.faceId }
                            else faces.firstOrNull { it.faceId != card.faceId }
                        ManualActionSquare("Inspect other side", "◉", enabled && other != null, onClick = {
                            other?.let { submit(PlayAction.InspectFace(card.id, it.faceId, card.containerId)) }
                        })
                    }
                    Row {
                        ManualActionSquare(if (card.covered) "Turn face up" else "Turn face down", "▧", enabled,
                            onClick = { send { PlayAction.SetCovered(it.id, !card.covered) } })
                        ManualActionSquare("Reset corner", "↺", enabled, onClick = { send { PlayAction.ResetCorner(it.id) } })
                    }
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                ManualActionSquare("Edit card note", "▤", enabled, onClick = { actions.edit(ManualValue(ManualValueKind.NOTE), state, viewer) })
                for (player in state.board.players) ManualActionSquare("Owner ${names[player.accountId] ?: player.accountId}", "●", enabled,
                    fill = if (player.accountId == viewer) Color(0xffb8dedb) else Color(0xffe5b7bc),
                    selected = card.owner == player.accountId, onClick = { send { PlayAction.SetOwner(it.id, player.accountId) } })
            }
            val faces = state.definitions.singleOrNull { it.digest == card.definition }?.definition?.faces.orEmpty()
            if (faces.size > 1) Row(Modifier.horizontalScroll(rememberScrollState())) {
                // Numbers, not names/art of hidden sides. Explicit chosen face; never auto-uncover.
                for ((index, face) in faces.withIndex()) ManualActionSquare("Switch printed face ${index + 1}", "▣${index + 1}", enabled,
                    selected = card.faceId == face.faceId, onClick = {
                        send { target ->
                            val targetFaces = state.definitions.singleOrNull { it.digest == target.definition }?.definition?.faces.orEmpty()
                            targetFaces.getOrNull(index)?.takeIf { targetFaces.size > 1 }?.let { PlayAction.SetFace(target.id, it.faceId) }
                        }
                    })
            }
            extras()
        }
    }
    val values: @Composable () -> Unit = {
        Column {
            Row(Modifier.horizontalScroll(rememberScrollState())) {
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
    Dialog(onDismissRequest = actions::close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.widthIn(max = if (landscape) 900.dp else 420.dp).fillMaxWidth(.96f).fillMaxHeight(.92f).heightIn(max = 560.dp),
            shape = MaterialTheme.shapes.large) {
            if (landscape) Row(Modifier.padding(8.dp)) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { general() }
                Column(Modifier.weight(1.3f).verticalScroll(rememberScrollState())) { values() }
            } else Column(Modifier.padding(8.dp)) {
                // Preview stays visible while direct field controls scroll.
                Column(Modifier.verticalScroll(rememberScrollState())) { general(); values() }
            }
        }
    }
}

@Composable
internal fun ManualCardPreview(state: PlayState, viewer: String, cardId: String, count: Int) {
    val card = state.board.cards.singleOrNull { it.id == cardId } ?: return
    val faces = ManualDisclosure.permittedFaces(state, card, viewer, inspecting = false)
    val face = faces.singleOrNull()
    val owner = if (card.owner == viewer) Color(0xff287f85) else Color(0xffa84f58)
    Column(Modifier.width(104.dp).heightIn(min = 104.dp).border(2.dp, owner).padding(4.dp)
        .semantics { contentDescription = "${face?.name ?: "Covered card"}, owner ${card.owner}, ${if (card.tapped) "tapped" else "untapped"}, $count captured targets" }) {
        Box(Modifier.height(44.dp).fillMaxWidth().graphicsLayer { rotationZ = if (card.tapped) 90f else 0f }) {
            if (face != null) {
                val definition = state.definitions.single { it.digest == card.definition }.definition
                val inspected = InspectedCard.retained(card.definition, definition)
                CardArtwork(inspected.artwork[definition.faces.indexOf(face)], face.name, 180, 240, Modifier.fillMaxSize())
            } else Text("▧", Modifier.align(Alignment.Center))
        }
        Text(face?.name ?: if (card.covered) "Covered" else "Content unavailable", maxLines = 1,
            overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
        Text(card.corner, Modifier.align(Alignment.End), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (card.note.isNotEmpty()) Text(card.note, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
        Row { for ((color, value) in card.counters) Text(value, Modifier.background(counterColor(color)),
            maxLines = 1, style = MaterialTheme.typography.labelSmall) }
        Text("×$count", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
internal fun CounterControls(values: Map<CounterColor, String>, player: Boolean, enabled: Boolean,
    set: (CounterColor) -> Unit, applicable: (CounterColor, Int) -> Boolean, adjust: (CounterColor, Int) -> Unit) {
    for (pair in CounterColor.entries.chunked(2)) Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
        for ((index, color) in pair.withIndex()) {
            if (index > 0) VerticalDivider(Modifier.height(36.dp).padding(horizontal = 2.dp))
            val name = color.name.lowercase()
            val value = values[color]
            ManualActionSquare("Set $name counter, ${value?.takeIf { it.isNotEmpty() } ?: "no value"}",
                if (player) ManualText.playerCounterText(value) else ManualText.cardCounterText(value), enabled,
                fill = counterColor(color), circle = true, tooltipName = "Set $name counter", onClick = { set(color) })
            for (delta in listOf(1, -1)) ManualActionSquare("${if (delta > 0) "Increase" else "Decrease"} $name counter",
                if (delta > 0) "+" else "−", enabled && applicable(color, delta), onClick = { adjust(color, delta) })
        }
    }
}

/** One pointer recognizer owns click/assigned hold/name tooltip. Sync updates callbacks, not the gesture. */
@Composable
internal fun ManualActionSquare(name: String, symbol: String, enabled: Boolean = true, fill: Color? = null,
    selected: Boolean = false, circle: Boolean = false, tooltipName: String = name,
    assignedHold: (() -> Unit)? = null, holdName: String? = null, onClick: () -> Unit) {
    val currentClick by rememberUpdatedState(onClick)
    val currentHold by rememberUpdatedState(assignedHold)
    var tooltip by remember { mutableStateOf(false) }
    val background = fill ?: MaterialTheme.colorScheme.surfaceContainerHigh
    Box(Modifier.size(48.dp).semantics {
        contentDescription = name
        role = Role.Button
        if (!enabled) disabled()
        onClick { if (enabled) currentClick(); enabled }
        if (assignedHold != null) onLongClick(holdName) { if (enabled) currentHold?.invoke(); enabled }
    }.onKeyEvent {
        if (enabled && (it.key == Key.Enter || it.key == Key.Spacebar)) {
            if (it.type == KeyEventType.KeyUp) currentClick()
            true
        } else false
    }.focusable(enabled).pointerInput(enabled) {
        if (!enabled) return@pointerInput
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
                if (released) { if (press.release()) capturedClick() }
                else if (!press.consumed) {
                    press.hold(capturedHold)
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
        Box(Modifier.size(if (circle) 28.dp else 44.dp)
            .background(background.copy(alpha = if (enabled) 1f else .4f), if (circle) CircleShape else MaterialTheme.shapes.small)
            .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small) else Modifier),
            contentAlignment = Alignment.Center) {
            Text(symbol, Modifier.padding(2.dp), color = if (enabled) Color(0xff19282c) else Color(0xff657074),
                maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
        }
        if (tooltip) Popup(alignment = Alignment.TopCenter) {
            Surface(shadowElevation = 4.dp, shape = MaterialTheme.shapes.small) {
                Text(tooltipName, Modifier.widthIn(max = 240.dp).padding(8.dp), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
