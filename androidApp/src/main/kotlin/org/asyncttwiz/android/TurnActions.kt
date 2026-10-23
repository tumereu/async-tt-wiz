package org.asyncttwiz.android

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.asyncttwiz.protocol.*
import kotlin.math.roundToLong

internal fun phaseColor(phase: String) = when (phase) {
    "BEGINNING" -> Color(0xffdcebf8)
    "PRECOMBAT_MAIN" -> Color(0xffe9dff6)
    "COMBAT" -> Color(0xffffe5ce)
    "POSTCOMBAT_MAIN" -> Color(0xffdcefe5)
    else -> Color(0xffe5e7e9)
}

/** Icon + phase background + turn-player border. Number is a separate badge, never priority. */
@Composable
fun TurnIndicator(turn: TurnState, turnPlayerName: String, turnPlayerColor: Color,
    onOpenPicker: () -> Unit, modifier: Modifier = Modifier) {
    val position = TurnPosition.of(turn) ?: return
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        ActionSquare("$turnPlayerName, ${position.label}. Choose phase/step", position.glyph, true,
            Modifier.border(2.dp, turnPlayerColor), fill = phaseColor(turn.phase), ink = Color(0xff25333b), onClick = onOpenPicker)
        Text(turn.number, Modifier.padding(4.dp).semantics { contentDescription = "Turn ${turn.number}" },
            style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun PhaseStepPicker(play: PlayState, viewerId: String, endingPlayerColor: Color,
    controller: TableActionController, onDismiss: () -> Unit) {
    val scope = TableActionScope(play.gameId, play.id)
    Dialog(onDismissRequest = onDismiss) {
        Surface(Modifier.border(2.dp, endingPlayerColor)) {
            Column(Modifier.padding(8.dp).verticalScroll(rememberScrollState())) {
                for ((phase, steps) in TurnPosition.entries.groupBy { it.phase }) {
                    Row(Modifier.fillMaxWidth().background(phaseColor(phase)).horizontalScroll(rememberScrollState())) {
                        for (step in steps) ActionSquare(step.label, step.glyph, controller.enabled(scope),
                            modifier = if (TurnPosition.of(play.board.turn) == step) Modifier.border(2.dp, endingPlayerColor) else Modifier,
                            selected = TurnPosition.of(play.board.turn) == step,
                            onClick = { controller.perform(TableActionIntent.Choose(scope, step)) })
                    }
                }
                TextButton(enabled = controller.enabled(scope), onClick = { controller.perform(TableActionIntent.Next(scope)) }) {
                    Text("Next step")
                }
                Button(enabled = controller.enabled(scope), colors = ButtonDefaults.buttonColors(containerColor = endingPlayerColor),
                    onClick = { controller.perform(TableActionIntent.End(scope)) }) {
                    Text(if (play.board.turn.accountId == viewerId) "End turn" else "End opponent's turn")
                }
            }
        }
    }
}

data class PlayerDeltaRelease(val target: PlayerValueTarget, val delta: Long)

/** Local preview only. One immutable target and at most one nonzero release; movement sends nothing. */
class PlayerDeltaDrag(val target: PlayerValueTarget, private val pixelsPerStep: Float) {
    init { require(pixelsPerStep.isFinite() && pixelsPerStep > 0) }
    private var pixels = 0f
    private var finished = false
    val delta: Long get() = (pixels / pixelsPerStep).roundToLong()
    fun move(upPixels: Float) { if (!finished && upPixels.isFinite()) pixels += upPixels }
    fun cancel() { finished = true }
    fun release(): PlayerDeltaRelease? {
        if (finished) return null
        finished = true
        return delta.takeIf { it != 0L }?.let { PlayerDeltaRelease(target, it) }
    }
}

/** Table life/counter affordance. Menu/Set and +/- are explicit native alternatives, not timed holds.
 * The target key excludes the current text so sync/reflow cannot redirect or cancel a valid capture. */
@Composable
fun PlayerDeltaControl(target: PlayerValueTarget, value: String?, color: Color, enabled: Boolean,
    controller: TableActionController, modifier: Modifier = Modifier) {
    val label = if (target.color == null) "Life" else "${target.color.name.lowercase()} counter"
    val currentText by rememberUpdatedState(value)
    val currentEnabled by rememberUpdatedState(enabled)
    var preview by remember(target) { mutableStateOf(0L) }
    var tooltip by remember(target) { mutableStateOf(false) }
    var press by remember(target) { mutableStateOf(ActionPress(false)) }
    val open = {
        if (currentEnabled) controller.perform(TableActionIntent.Player(target.scope, target.accountId))
        Unit
    }
    Box(modifier.size(48.dp).background(color.copy(alpha = .15f)).onKeyEvent { event ->
        if (enabled && event.key in listOf(Key.Enter, Key.Spacebar, Key.DirectionCenter, Key.DirectionUp, Key.DirectionDown)) {
            if (event.type == KeyEventType.KeyUp) when (event.key) {
                Key.DirectionUp -> controller.adjust(target, 1)
                Key.DirectionDown -> controller.adjust(target, -1)
                else -> open()
            }
            true
        } else false
    }.focusable(enabled).semantics(mergeDescendants = true) {
        contentDescription = "$label, ${value ?: "empty"}"
        role = Role.Button
        if (!enabled) disabled()
        onClick("Edit $label") { if (enabled) { open(); true } else false }
        customActions = listOf(
            CustomAccessibilityAction("Increase $label") { enabled && controller.adjust(target, 1) },
            CustomAccessibilityAction("Decrease $label") { enabled && controller.adjust(target, -1) },
            CustomAccessibilityAction("Set $label") { if (enabled) { open(); true } else false })
    }.pointerInput(target) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.changes.any { it.position != it.previousPosition || !it.pressed }) tooltip = false
            }
        }
    }.pointerInput(target) {
        var drag: PlayerDeltaDrag? = null
        detectDragGestures(onDragStart = {
            val numeric = TableActions.numeric(currentText ?: if (target.color != null) "0" else "")
            drag = if (currentEnabled && numeric && !press.consumed) PlayerDeltaDrag(target, 12.dp.toPx()) else null
            press.cancel()
            tooltip = false
        }, onDrag = { change, amount ->
            change.consume() // A recognized drag never also opens the menu, including a zero delta.
            drag?.move(-amount.y)
            preview = drag?.delta ?: 0
        }, onDragCancel = { drag?.cancel(); drag = null; preview = 0 }, onDragEnd = {
            val released = drag?.release()
            drag = null; preview = 0
            if (currentEnabled && released != null) controller.adjust(released.target, released.delta)
        })
    }.pointerInput(target) {
        detectTapGestures(onPress = {
            press = ActionPress(false)
            if (!tryAwaitRelease()) press.cancel()
            tooltip = false
        }, onTap = { if (currentEnabled && press.release() == PressAction.TAP) open() },
            onLongPress = { if (currentEnabled && press.hold() == PressAction.TOOLTIP) tooltip = true })
    }, contentAlignment = Alignment.Center) {
        Text(value.orEmpty(), color = color, modifier = Modifier.clearAndSetSemantics {})
        if (preview != 0L) androidx.compose.ui.window.Popup(alignment = Alignment.TopCenter) {
            Surface { Text(if (preview > 0) "+$preview" else "−${preview.toString().removePrefix("-")}", Modifier.padding(4.dp)) }
        }
        if (tooltip) androidx.compose.ui.window.Popup(alignment = Alignment.TopCenter) {
            Surface { Text(label, Modifier.padding(6.dp)) }
        }
    }
}
