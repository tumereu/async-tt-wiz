package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.asyncttwiz.protocol.*

@Composable
internal fun PlayerActions(state: PlayState, scope: ManualScope, actions: ManualActions, enabled: Boolean,
    names: Map<String, String>, viewer: String = scope.contextId, landscape: Boolean = false, submit: (PlayAction) -> Boolean) {
    val player = state.board.players.single { it.accountId == scope.contextId }
    CompositionLocalProvider(LocalValueDragSide provides if (player.accountId == viewer) ValueDragSide.BOTTOM else ValueDragSide.TOP) {
    InlinePopupPanel(actions::close, title = if (scope.playerCounters) "Counters" else "Life") {
        if (!scope.playerCounters) {
            val color = LocalInlinePopupColor.current
            val shape = RoundedCornerShape(3.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(48.dp), color = androidx.compose.ui.graphics.Color.White,
                    shape = shape, border = BorderStroke(1.dp, color.copy(alpha = .5f)), shadowElevation = 3.dp) {
                    Box(Modifier.clearAndSetSemantics {}, contentAlignment = Alignment.Center) { MenuIcon("Life", tint = color) }
                }
                fun adjust(delta: Int) = MenuControl("${if (delta > 0) "Increase" else "Decrease"} life",
                    enabled && ManualText.add(player.life, delta.toLong()) != null) {
                    submit(PlayAction.AdjustLife(player.accountId, delta.toString()))
                }
                ValueControl(player.life, MenuControl("Set life", enabled) {
                    actions.edit(ManualValue(ManualValueKind.LIFE), state, player.accountId)
                }, adjust(-1), adjust(1), dragValue = player.life,
                    drag = { submit(PlayAction.AdjustLife(player.accountId, it.toString())) })
            }
            // Landscape omits empty/zero counters from the table; retain access for that player.
            if (landscape && player.counters.values.none(TablePresentation::nonzero)) {
                InlinePopupCell(MenuControl("Counters") { actions.player(state, player.accountId, counters = true) })
            }
            PlayerStatusSection(state, player, own = player.accountId == viewer, enabled = enabled, close = actions::close, submit = submit)
        } else {
            for (pair in CounterColor.entries.chunked(2)) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (counter in pair) Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    val name = counter.name.lowercase()
                    val value = player.counters[counter]
                    fun adjust(delta: Int) = MenuControl("${if (delta > 0) "Increase" else "Decrease"} $name counter",
                        enabled && ManualText.add(value ?: "0", delta.toLong()) != null) {
                        submit(PlayAction.AdjustCounter(CounterTarget.PLAYER, player.accountId, counter, delta.toString()))
                    }
                    ValueControl(ManualText.playerCounterText(value), MenuControl("Set $name counter", enabled) {
                        actions.edit(ManualValue(ManualValueKind.COUNTER, counter), state, player.accountId)
                    }, adjust(-1), adjust(1), color = counterOutline(counter), fill = counterColor(counter),
                        dragValue = value ?: "0",
                        drag = { submit(PlayAction.AdjustCounter(CounterTarget.PLAYER, player.accountId, counter, it.toString())) })
                }
            }
        }
    }
    }
}

/** Typing aid for the status field: one line, at most [PlayerStatus.MAX_LENGTH] characters. */
object PlayerStatusInput {
    val presets = listOf("Wait", "Ready", "Thinking…", "Got a response", "No responses", "BRB", "GG")
    fun typed(value: String): String = value.map { if (it.isISOControl()) ' ' else it }.joinToString("")
        .take(PlayerStatus.MAX_LENGTH).let { if (it.lastOrNull()?.isHighSurrogate() == true) it.dropLast(1) else it }
    /** What is sent: trimmed and always valid; blank clears the status. */
    fun clean(value: String): String = typed(value).trim().takeIf(PlayerStatus::valid).orEmpty()
}

/** Own status: presets, pen (free text) and clear. The opponent's status is shown read-only. */
/** Popups float over the table without a sheet, so plain text needs its own backing to stay readable. */
@Composable
private fun PlayerStatusLabel(text: String, maxLines: Int) {
    Surface(shape = RoundedCornerShape(4.dp), color = Color.White, border = BorderStroke(1.dp, LocalInlinePopupColor.current.copy(alpha = .4f))) {
        Text(text, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium,
            color = MenuInk, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
    }
}

/** Personal Hide/Show and Mute/Unmute toggles; each icon and label names what a tap will do. Local to this device. */
@Composable
private fun StatusBubbleToggles(state: PlayState, player: PlayPlayer, close: () -> Unit) {
    val bubbles = LocalStatusBubbles.current ?: return
    val hidden = bubbles.modes(state)[player.accountId] == StatusBubbleMode.NONE
    val muted = bubbles.muted(player.accountId)
    fun toggle(action: String, run: () -> Unit) = MenuControl(action) { run(); close() }
    Row(horizontalArrangement = Arrangement.spacedBy(InlinePopupStyle.cellGap.dp)) {
        if (player.status.isNotEmpty()) {
            if (hidden) InlinePopupCell(toggle("Show status bubble") { bubbles.hide(state, player.accountId, hidden = false) }, "Show")
            else InlinePopupCell(toggle("Hide status bubble") { bubbles.hide(state, player.accountId, hidden = true) }, "Hide")
        }
        if (muted) InlinePopupCell(toggle("Unmute status bubble") { bubbles.mute(player.accountId, false) }, "Unmute")
        else InlinePopupCell(toggle("Mute status bubble") { bubbles.mute(player.accountId, true) }, "Mute")
    }
}

@Composable
private fun PlayerStatusSection(state: PlayState, player: PlayPlayer, own: Boolean, enabled: Boolean, close: () -> Unit,
    submit: (PlayAction) -> Boolean) {
    val color = LocalInlinePopupColor.current
    if (!own) {
        if (player.status.isNotEmpty()) PlayerStatusLabel("Status: “${player.status}”", maxLines = 3)
        StatusBubbleToggles(state, player, close)
        return
    }
    var editing by remember { mutableStateOf<String?>(null) }
    fun set(text: String) {
        if (text != player.status) submit(PlayAction.SetStatus(text))
        editing = null; close()
    }
    PlayerStatusLabel(if (player.status.isEmpty()) "No status" else "Status: “${player.status}”", maxLines = 1)
    val cells: List<Pair<MenuControl, @Composable () -> Unit>> = PlayerStatusInput.presets.map { preset ->
        MenuControl("Set status $preset", enabled, selected = player.status == preset) { set(preset) } to @Composable {
            Text(preset, Modifier.padding(horizontal = 2.dp).clearAndSetSemantics {}, fontSize = 12.sp, lineHeight = 13.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, color = MenuInk.copy(alpha = if (enabled) 1f else .38f))
        }
    } + listOfNotNull(MenuControl("Edit status", enabled) { editing = player.status } to @Composable {
        MenuIcon("Edit status", Modifier.size(20.dp), tint = color.copy(alpha = if (enabled) 1f else .35f))
    }, if (player.status.isEmpty()) null else MenuControl("Clear status", enabled) { set("") } to @Composable {
        MenuIcon("Clear status", Modifier.size(20.dp), tint = color.copy(alpha = if (enabled) 1f else .35f))
    })
    for (row in cells.chunked(3)) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for ((control, content) in row) RingPress(control, Modifier.width(96.dp).height(36.dp), color) { content() }
    }
    StatusBubbleToggles(state, player, close)
    editing?.let { initial ->
        val field = rememberDialogField(initial)
        val save = { set(PlayerStatusInput.clean(field.text)) }
        ScreenDialog({ editing = null }, title = "Status", actions = {
            DialogCancelButton("Cancel", { editing = null })
            DialogMainButton("Save", save, enabled = enabled)
        }) {
            OutlinedTextField(field.value, { field.value = it.copy(text = PlayerStatusInput.typed(it.text)) },
                Modifier.fillMaxWidth().dialogField(field).testTag("statusText"), label = { Text("Status") },
                placeholder = { Text("Wait") }, singleLine = true,
                supportingText = { Text("${field.text.length}/${PlayerStatus.MAX_LENGTH}") },
                keyboardOptions = imeOptions(ImeAction.Done), keyboardActions = submitKeyboardActions(enabled, submit = save))
        }
    }
}
