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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Text
import org.asyncttwiz.protocol.*

/** The screen-fixed controls: quick actions and Undo, or the timeline panel, and transient errors. Inline,
 * so it stays part of TableSurface's own recomposition scope. */
@Composable
inline fun BoxWithConstraintsScope.TableControlsLayer(game: GameSummary, play: TableBoard, accountId: String, landscape: Boolean,
    connected: Boolean, runtime: TableRuntimeHooks, toolkit: TableToolkitController?, scene: TableScene) {
    val desktop = LocalDesktopGeometry.current
    // WEB-018: the desktop table's action area at the bottom right: the quick actions in a row, Undo and the main button under
    // them, drawn larger (owner, desktop playtest 2026-11-14: the quick action buttons' texts too small)
    if (desktop != null) {
        if (runtime.bottomPanel != null) runtime.bottomPanel.invoke {}
        else if (!runtime.reviewing) Box(Modifier.align(Alignment.BottomEnd).width(DesktopTable.ACTIONS.dp).height(desktop.band.dp)
            .testTag("action-area")) { DesktopScaled(DesktopTable.UI_SCALE) { BoxWithConstraints(Modifier.fillMaxSize().padding(DesktopTable.ACTIONS_PADDING.dp)) {
            if (toolkit != null) {
                val endingColor = if (play.board.turn.accountId == accountId) OwnTableColor else OpponentTableColor
                QuickActionStrip(play, toolkitQuickContext(play, accountId)
                    .copy(automatumCombat = toolkit.combat.ready(play), castOpen = toolkit.cast.open,
                        revealOpen = !runtime.reviewing && LocalEngineRoutes.current?.revealing() == true,
                        assumedBlockers = LocalEngineRoutes.current?.assuming()?.blocks?.size,
                        mainAction = toolkit.mainSlot.action ?: toolkit.flows.divisionAction(),
                        costsOpen = toolkit.combat.costPanelOpen(play))
                    .withCombatTarget(toolkit.combat, play, defendingPlayerName(game, play)),
                    toolkit.table, endingColor,
                    Modifier.fillMaxWidth().height(DESKTOP_ACTIONS_HEIGHT.dp), perform = toolkit::perform, submit = { toolkit.submit(it) },
                    mainShakes = toolkit.mainShakes, mainShakeReason = toolkit.mainShakeReason, stacked = true)
                // HIS-083 (I-D56 §10): holding Undo opens the rewind dialog to pick an earlier point; there is no Redo. WEB-018
                // (owner, desktop playtest 2026-11-14): a real button in the bottom row's first third, lined up with the main
                // button in the other two
                runtime.undo?.let { undo ->
                    DesktopUndoButton(runtime.controlsConnected ?: connected, runtime.undoHold,
                        Modifier.padding(top = (DESKTOP_ACTIONS_HEIGHT - 48f).dp).width(DesktopTable.undoWidth(maxWidth.value).dp)
                            .shaken(runtime.undoShakes, runtime.undoShakeReason, "undo")) { undo() }
                }
            }
        } } }
        FloatingTransientError(runtime.errorMessage ?: toolkit?.error,
            { if (runtime.errorMessage != null) runtime.dismissError?.invoke() else toolkit?.dismissError() },
            Modifier.align(Alignment.BottomStart).width(desktop.tableRight.dp).padding(bottom = (desktop.band + 8f).dp))
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
                        mainAction = toolkit.mainSlot.action ?: toolkit.flows.divisionAction(),
                        costsOpen = toolkit.combat.costPanelOpen(play))
                    .withCombatTarget(toolkit.combat, play, defendingPlayerName(game, play)),
                    toolkit.table, endingColor,
                    Modifier.fillMaxSize().padding(start = 60.dp, end = 8.dp), perform = toolkit::perform, submit = { toolkit.submit(it) },
                    mainShakes = toolkit.mainShakes, mainShakeReason = toolkit.mainShakeReason)
                // HIS-083 (I-D56 §10): holding Undo opens the rewind dialog to pick an earlier point; there is no Redo
                runtime.undo?.let { undo -> ActionSquare("Undo", "↶", runtime.controlsConnected ?: connected,
                    Modifier.align(Alignment.CenterStart).padding(start = 10.dp).shaken(runtime.undoShakes, runtime.undoShakeReason, "undo"),
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
const val DESKTOP_ACTIONS_HEIGHT = 98f

/**
 * WEB-018 (owner, desktop playtest 2026-11-14: "perhaps undo should be a "real" button here to make the panel line up more
 * nicely"): the desktop action area's Undo, a button as tall as the main button beside it, in its white with its outline and
 * shadow, its icon over the word. Tap = Undo, hold or right-click = [hold] (HIS-083), Ctrl+Z its key (WEB-031).
 */
@Composable
fun DesktopUndoButton(enabled: Boolean, hold: (() -> Unit)?, modifier: Modifier, onClick: () -> Unit) {
    val ink = AutomatumStepInk.copy(alpha = if (enabled) 1f else .38f)
    ActionSquare("Undo", "↶", enabled, modifier.testTag(DESKTOP_UNDO_TAG).shadow(3.dp, RectangleShape).background(Color.White)
        .border(1.dp, AutomatumStepOutline), fill = Color.White, ink = ink, compactContent = true,
        holdName = if (hold != null) "Undo to an earlier point" else null, onHold = hold, keys = LocalTableHotkeys.current?.undo,
        icon = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TableChromeIcon("undo")
                Text("Undo", fontSize = 11.sp, lineHeight = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            }
        }, onClick = onClick)
}

/** The test tag of the desktop action area's Undo button (WEB-018). */
const val DESKTOP_UNDO_TAG = "desktop-undo"
