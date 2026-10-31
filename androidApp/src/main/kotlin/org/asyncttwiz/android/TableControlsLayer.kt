package org.asyncttwiz.android

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import org.asyncttwiz.protocol.*

/** The screen-fixed controls: quick actions and Undo, the live timeline or review panel, transient errors and
 * the interaction mode button. Inline, so it stays part of TableSurface's own recomposition scope. */
@Composable
internal inline fun BoxWithConstraintsScope.TableControlsLayer(play: PlayState, accountId: String, landscape: Boolean,
    connected: Boolean, runtime: TableRuntimeHooks, toolkit: TableToolkitController?, scene: TableScene,
    selectionState: MutableState<TableSelection>, coordinator: TableGestureCoordinator,
    crossinline gestureError: () -> String?, crossinline clearGestureError: () -> Unit) {
    var selection by selectionState
    val playControls: @Composable () -> Unit = {
        Box(Modifier.fillMaxWidth().height(TableControlsHeight.dp)) {
            if (toolkit != null) {
                val endingColor = if (play.board.turn.accountId == accountId) OwnTableColor else OpponentTableColor
                QuickActionStrip(play, toolkitQuickContext(play, selection, toolkit.explicitStack, accountId)
                    .copy(combatDamageEdits = toolkit.combatDamageEdits(play)), toolkit.table, endingColor,
                    Modifier.fillMaxSize().padding(start = 60.dp, end = 8.dp), perform = toolkit::perform)
                runtime.undo?.let { undo -> ActionSquare("Undo", "↶", runtime.controlsConnected ?: connected,
                    Modifier.align(Alignment.CenterStart).padding(start = 10.dp), holdName = if (runtime.redo != null) "Redo" else null,
                    onHold = runtime.redo?.let { redo -> { redo() } }, icon = { TableChromeIcon("undo") }, onClick = { undo() }) }
            }
        }
    }
    if (runtime.bottomPanel != null) runtime.bottomPanel.invoke(playControls)
    else {
        if (runtime.reviewing) runtime.reviewPanel?.invoke()
        if (!runtime.reviewing) Box(Modifier.align(if (landscape) Alignment.BottomStart else Alignment.BottomCenter)
            .padding(bottom = if (landscape) 4.dp else TableControlsHeight.dp)
            .fillMaxWidth(if (landscape) .5f else 1f).height(TableTimelineHeight.dp)) { runtime.liveTimeline?.invoke() }
        if (!runtime.reviewing) Box(Modifier.align(if (landscape) Alignment.BottomEnd else Alignment.BottomCenter)
            .fillMaxWidth(if (landscape) .5f else 1f)) { playControls() }
    }
    FloatingTransientError(runtime.errorMessage ?: toolkit?.error ?: gestureError(),
        { if (runtime.errorMessage != null) runtime.dismissError?.invoke() else if (toolkit?.error != null) toolkit.dismissError() else clearGestureError() },
        Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp))
    // Screen-fixed personal control remains reachable; it does not compete over card hit regions.
    // Beside the own hand row's outer end; the hand row is shortened by its width.
    val modeButton = scene.modeButton
    if (!runtime.reviewing) Box(Modifier.offset(
        (modeButton?.x ?: (maxWidth.value - 56f)).dp, (modeButton?.y ?: (maxHeight.value - 112f)).dp)) {
        ActionSquare("Interaction mode", "", !runtime.gameActionsBlocked,
            modifier = Modifier.size((modeButton?.width ?: 48f).dp).background(Color.White).border(1.dp, OwnTableColor).semantics {
                contentDescription = "Interaction mode"; stateDescription = "${selection.mode.name.lowercase()}, ${selection.ids.size} selected"
            }, ink = OwnTableColor, icon = { TableChromeIcon(when (selection.mode) { TableMode.NORMAL -> "mode"; TableMode.SELECT -> "select"; TableMode.PING -> "ping" }) },
            onClick = { coordinator.cancel(); selection = selection.cycleMode(runtime.ping != null && runtime.controlsConnected == true) })
    }
}
