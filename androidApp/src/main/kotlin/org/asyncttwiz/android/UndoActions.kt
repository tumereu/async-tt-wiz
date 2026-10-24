package org.asyncttwiz.android

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** Tap Undo / hold Redo. combinedClickable consumes a recognized hold, including its release.
 * The explicit menu below is the discoverable/accessibility route, not tooltip-triggered dispatch.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable fun UndoQuickAction(availability: UndoAvailability, callbacks: UndoCallbacks,
    modifier: Modifier = Modifier) {
    val haptic = LocalHapticFeedback.current
    Box(modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).combinedClickable(
        enabled = availability.connected && (availability.canUndo || availability.canRedo), role = Role.Button,
        onClickLabel = "Undo", onLongClickLabel = "Redo", hapticFeedbackEnabled = false,
        onClick = { if (availability.permits(UndoIntent.UNDO)) callbacks.undo() },
        onLongClick = { if (availability.permits(UndoIntent.REDO)) {
            haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
            callbacks.redo()
        } }
    ).padding(12.dp)) { Text("Undo") }
}

/** Mount in the gameplay actions menu. Both explicit actions remain present when unavailable. */
@Composable fun UndoActionsMenu(expanded: Boolean, onDismiss: () -> Unit,
    availability: UndoAvailability, callbacks: UndoCallbacks) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text("Undo") }, enabled = availability.permits(UndoIntent.UNDO),
            onClick = { onDismiss(); callbacks.undo() })
        DropdownMenuItem(text = { Text("Redo") }, enabled = availability.permits(UndoIntent.REDO),
            onClick = { onDismiss(); callbacks.redo() })
    }
}
