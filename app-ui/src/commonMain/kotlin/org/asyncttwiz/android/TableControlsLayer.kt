package org.asyncttwiz.android

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.asyncttwiz.protocol.*

/** The screen-fixed controls: quick actions and Undo, or the timeline panel, and transient errors. Inline,
 * so it stays part of TableSurface's own recomposition scope. */
@Composable
inline fun BoxWithConstraintsScope.TableControlsLayer(game: GameSummary, play: TableBoard, accountId: String, landscape: Boolean,
    connected: Boolean, runtime: TableRuntimeHooks, toolkit: TableToolkitController?, scene: TableScene) {
    val desktop = LocalDesktopGeometry.current
    // WEB-018: the desktop table's action area at the bottom right: the quick actions in a row, Undo and the main button under
    // them
    if (desktop != null) {
        if (runtime.bottomPanel != null) runtime.bottomPanel.invoke {}
        else if (!runtime.reviewing) Box(Modifier.align(Alignment.BottomEnd).width(DesktopTable.ACTIONS.dp).height(desktop.band.dp)
            .padding(8.dp).testTag("action-area")) {
            if (toolkit != null) {
                val endingColor = if (play.board.turn.accountId == accountId) OwnTableColor else OpponentTableColor
                QuickActionStrip(play, toolkitQuickContext(play, accountId)
                    .copy(automatumCombat = toolkit.combat.ready(play), castOpen = toolkit.cast.open,
                        revealOpen = !runtime.reviewing && LocalEngineRoutes.current?.revealing() == true,
                        assumedBlockers = LocalEngineRoutes.current?.assuming()?.blocks?.size,
                        mainAction = toolkit.mainSlot.action ?: toolkit.flows.divisionAction())
                    .withCombatTarget(toolkit.combat, play, defendingPlayerName(game, play)),
                    toolkit.table, endingColor,
                    Modifier.fillMaxWidth().height(DESKTOP_ACTIONS_HEIGHT.dp), perform = toolkit::perform, submit = { toolkit.submit(it) },
                    mainShakes = toolkit.mainShakes, stacked = true)
                // HIS-083 (I-D56 §10): holding Undo opens the rewind dialog to pick an earlier point; there is no Redo
                runtime.undo?.let { undo -> Box(Modifier.padding(top = 56.dp, start = 2.dp).height(48.dp), contentAlignment = Alignment.CenterStart) {
                    ActionSquare("Undo", "↶", runtime.controlsConnected ?: connected, Modifier.shaken(runtime.undoShakes),
                        holdName = if (runtime.undoHold != null) "Undo to an earlier point" else null,
                        onHold = runtime.undoHold, icon = { TableChromeIcon("undo") }, onClick = { undo() })
                } }
            }
        }
        FloatingTransientError(runtime.errorMessage ?: toolkit?.error,
            { if (runtime.errorMessage != null) runtime.dismissError?.invoke() else toolkit?.dismissError() },
            Modifier.align(Alignment.BottomStart).width(desktop.tableRight.dp).padding(bottom = (desktop.band + DesktopTable.SEAT + 8f).dp))
        return
    }
    val playControls: @Composable () -> Unit = {
        Box(Modifier.fillMaxWidth().height(TableControlsHeight.dp)) {
            if (toolkit != null) {
                val endingColor = if (play.board.turn.accountId == accountId) OwnTableColor else OpponentTableColor
                QuickActionStrip(play, toolkitQuickContext(play, accountId)
                    .copy(automatumCombat = toolkit.combat.ready(play), castOpen = toolkit.cast.open,
                        revealOpen = !runtime.reviewing && LocalEngineRoutes.current?.revealing() == true,
                        assumedBlockers = LocalEngineRoutes.current?.assuming()?.blocks?.size,
                        mainAction = toolkit.mainSlot.action ?: toolkit.flows.divisionAction())
                    .withCombatTarget(toolkit.combat, play, defendingPlayerName(game, play)),
                    toolkit.table, endingColor,
                    Modifier.fillMaxSize().padding(start = 60.dp, end = 8.dp), perform = toolkit::perform, submit = { toolkit.submit(it) },
                    mainShakes = toolkit.mainShakes)
                // HIS-083 (I-D56 §10): holding Undo opens the rewind dialog to pick an earlier point; there is no Redo
                runtime.undo?.let { undo -> ActionSquare("Undo", "↶", runtime.controlsConnected ?: connected,
                    Modifier.align(Alignment.CenterStart).padding(start = 10.dp).shaken(runtime.undoShakes),
                    holdName = if (runtime.undoHold != null) "Undo to an earlier point" else null,
                    onHold = runtime.undoHold, icon = { TableChromeIcon("undo") }, onClick = { undo() }) }
            }
        }
    }
    if (runtime.bottomPanel != null) runtime.bottomPanel.invoke(playControls)
    // HIS-001: no event strip in play mode
    // TBL-009 (owner, 2026-11-08): in landscape the bar's fixed-width right end, your hand left of it
    else if (!runtime.reviewing) Box(Modifier.align(if (landscape) Alignment.BottomEnd else Alignment.BottomCenter)
        .then(if (landscape) Modifier.width(TablePresentation.Landscape.controls(maxWidth.value).dp) else Modifier.fillMaxWidth())) { playControls() }
    FloatingTransientError(runtime.errorMessage ?: toolkit?.error,
        { if (runtime.errorMessage != null) runtime.dismissError?.invoke() else toolkit?.dismissError() },
        // Above the own hand row, clear of the own life square, hand and quick actions
        Modifier.align(Alignment.BottomCenter).padding(bottom = scene.items.firstOrNull { it.kind == TableItemKind.HAND && it.owner == accountId }
            ?.let { (maxHeight.value - it.bounds.y).coerceAtLeast(64f) }?.dp ?: 64.dp))
}

/** WEB-018: the quick actions' row over the main button's in the desktop action area. */
const val DESKTOP_ACTIONS_HEIGHT = 104f
