package org.asyncttwiz.android

import org.asyncttwiz.protocol.TableActions

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** All table numeric editors share a square value cell between minus and plus. */
@Composable
internal fun ValueControl(value: String, set: MenuControl, minus: MenuControl, plus: MenuControl,
    color: Color = LocalInlinePopupColor.current, fill: Color = Color.White,
    dragValue: String = value, corner: Boolean = false, drag: ((Long) -> Unit)? = null,
    valueContent: (@Composable () -> Unit)? = null) {
    var delta by remember(set.name) { mutableStateOf<Long?>(null) }
    val shape = RoundedCornerShape(3.dp)
    Row(Modifier.size((96 + InlinePopupStyle.cellGap).dp, 48.dp).shadow(3.dp, shape).clip(shape)
        .border(1.dp, color.copy(alpha = .5f), shape)) {
        RingPress(minus, Modifier.weight(1f).fillMaxHeight(), color, fill, bordered = false) {
            Text("−", color = MenuInk.copy(alpha = if (minus.enabled) 1f else .35f), fontWeight = FontWeight.Bold)
        }
        val numeric = if (corner) cornerDragValue(dragValue, 0) != null else TableActions.numeric(dragValue)
        val gestures = if (drag != null) Modifier.ringInputTarget(draggable = true)
            .valueDragGestures(set.enabled && numeric, preview = { delta = it },
                side = if (corner) ValueDragSide.TOP else LocalValueDragSide.current, commit = drag) else Modifier
        RingPress(set, Modifier.size(48.dp).border(1.dp, color.copy(alpha = .35f)).then(gestures), color, fill, bordered = false) {
            if (valueContent != null) valueContent() else Text(value, Modifier.padding(horizontal = 2.dp).clearAndSetSemantics {},
                fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MenuInk.copy(alpha = if (set.enabled) 1f else .35f), fontWeight = FontWeight.Bold)
            delta?.let { HoldTooltip(if (corner) cornerDragLabel(dragValue, it) else valueDragLabel(dragValue, it)) }
        }
        RingPress(plus, Modifier.weight(1f).fillMaxHeight(), color, fill, bordered = false) {
            Text("+", color = MenuInk.copy(alpha = if (plus.enabled) 1f else .35f), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
internal fun ValueInputControl(name: String, input: TextFieldValue, change: (TextFieldValue) -> Unit,
    enabled: Boolean, minimum: Int = 0) {
    val latestInput by rememberUpdatedState(input)
    val latestChange by rememberUpdatedState(change)
    fun selectAll() = latestChange(latestInput.copy(selection = TextRange(0, latestInput.text.length)))
    fun step(delta: Int) = change(selectedLibraryQuantity(stepValueInput(input.text, delta, minimum).toString()))
    ValueControl(input.text, MenuControl(name, enabled, click = ::selectAll),
        MenuControl("Decrease $name", enabled && (input.text.toIntOrNull() ?: minimum) > minimum) { step(-1) },
        MenuControl("Increase $name", enabled && input.text.toIntOrNull() != Int.MAX_VALUE) { step(1) },
        valueContent = {
            BasicTextField(input, change, Modifier.fillMaxSize().padding(vertical = 14.dp, horizontal = 2.dp)
                .ringInputTarget(draggable = true).semantics { contentDescription = name }
                .onFocusChanged { if (it.isFocused) selectAll() }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do { val event = awaitPointerEvent(PointerEventPass.Final) } while (event.changes.any { it.pressed })
                        selectAll()
                    }
                },
                enabled = enabled, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = androidx.compose.ui.text.TextStyle(color = MenuInk, textAlign = TextAlign.Center, fontSize = 12.sp))
        })
}

internal fun stepValueInput(text: String, delta: Int, minimum: Int = 0): Int =
    ((text.toIntOrNull() ?: minimum).toLong() + delta).coerceIn(minimum.toLong(), Int.MAX_VALUE.toLong()).toInt()
