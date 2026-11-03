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

/** The screen-fixed controls: quick actions and Undo, the live timeline or review panel and transient errors. Inline,
 * so it stays part of TableSurface's own recomposition scope. */
@Composable
internal inline fun BoxWithConstraintsScope.TableControlsLayer(play: PlayState, accountId: String, landscape: Boolean,
    connected: Boolean, runtime: TableRuntimeHooks, toolkit: TableToolkitController?, scene: TableScene,
    crossinline gestureError: () -> String?, crossinline clearGestureError: () -> Unit) {
    val playControls: @Composable () -> Unit = {
        Box(Modifier.fillMaxWidth().height(TableControlsHeight.dp)) {
            if (toolkit != null) {
                val endingColor = if (play.board.turn.accountId == accountId) OwnTableColor else OpponentTableColor
                // Owner playtest 2026-11-03 #1, #6: what a tap of the step arrow does, worked out in the background as each
                // board appears, so its words and its tap are the same plan and the tap needs no work
                LaunchedEffect(toolkit, IdentityKey(play.board), toolkit.flows.busy, toolkit.cast.open) {
                    if (stepArrowShown(play, accountId)) toolkit.planStep()
                }
                QuickActionStrip(play, toolkitQuickContext(play, accountId)
                    .copy(automatumCombat = toolkit.combat.ready(play), castOpen = toolkit.cast.open,
                        plannedStep = toolkit.plannedStep(play))
                    .withCombatTarget(toolkit.combat, play, scene.combatLabels.firstOrNull { it.cardId == null }?.label?.removePrefix("→ ")),
                    toolkit.table, endingColor,
                    Modifier.fillMaxSize().padding(start = 60.dp, end = 8.dp), perform = toolkit::perform, submit = { toolkit.submit(it) })
                // HIS-083 (I-D56 §10): holding Undo opens the rewind dialog to pick an earlier point; there is no Redo
                runtime.undo?.let { undo -> ActionSquare("Undo", "↶", runtime.controlsConnected ?: connected,
                    Modifier.align(Alignment.CenterStart).padding(start = 10.dp).shaken(runtime.undoShakes),
                    holdName = if (runtime.undoHold != null) "Undo to an earlier point" else null,
                    onHold = runtime.undoHold, icon = { TableChromeIcon("undo") }, onClick = { undo() }) }
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
        // Above the own hand row, clear of the own life square, hand and quick actions
        Modifier.align(Alignment.BottomCenter).padding(bottom = scene.items.firstOrNull { it.kind == TableItemKind.HAND && it.owner == accountId }
            ?.let { (maxHeight.value - it.bounds.y).coerceAtLeast(64f) }?.dp ?: 64.dp))
}
