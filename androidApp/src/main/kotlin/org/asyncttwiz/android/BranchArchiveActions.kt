package org.asyncttwiz.android

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.asyncttwiz.protocol.*

/** Advanced branch-detail context only. Not a replacement H07 screen or a normal switching toolbar.
 * The owner captures the branch/scope before opening; incoming graph growth cannot retarget actions.
 */
@Composable fun BranchArchiveActionsMenu(expanded: Boolean, onDismiss: () -> Unit, branchLabel: String,
    archived: Boolean, active: Boolean, connected: Boolean, onArchive: () -> Unit, onRestore: () -> Unit,
    onPrepareReturn: () -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (archived) {
            DropdownMenuItem(text = { Text("Restore $branchLabel") }, enabled = connected,
                onClick = { onDismiss(); onRestore() })
        } else {
            DropdownMenuItem(text = { Text("Archive $branchLabel") }, enabled = connected && !active,
                onClick = { onDismiss(); onArchive() })
            DropdownMenuItem(text = { Text("Delete this branch and return…") }, enabled = connected,
                onClick = { onDismiss(); onPrepareReturn() })
        }
    }
}

/** Personal filtering remains usable for downloaded read-only history. No request callback. */
@Composable fun ShowHiddenBranchesOption(showHidden: Boolean, onChange: (Boolean) -> Unit) {
    DropdownMenuItem(text = { Text("Show hidden branches") },
        leadingIcon = { Checkbox(checked = showHidden, onCheckedChange = null) },
        onClick = { onChange(!showHidden) })
}

/** Explicit selection/restore flow for changed, missing or archived defaults. No candidate is selected
 * automatically. Choices are captured retained contexts; the owner prepares a NEW exact preview.
 */
@Composable fun BranchReturnSelectionDialog(choices: List<BranchReturnChoice>, reason: String,
    connected: Boolean, onDismiss: () -> Unit, onChoose: (BranchArchiveTarget, Boolean) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Choose return continuation") }, text = {
        Column(Modifier.fillMaxHeight(0.7f).verticalScroll(rememberScrollState())) {
            Text(when (reason) {
                "restoreReturn" -> "The return branch is archived. Explicitly restore it or choose another."
                else -> "The return continuation is unavailable or changed. Choose a retained continuation."
            })
            if (choices.isEmpty()) Text("No retained return continuation is available.")
            for (choice in choices) {
                TextButton(enabled = connected, onClick = { onChoose(choice.target, choice.archived) }) {
                    Text(if (choice.archived) "Restore and choose ${choice.label}" else choice.label)
                }
            }
        }
    }, confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

/** A compact confirmation over the existing table's read-only board renderer, not a second board.
 * Rendered labels AND board must describe confirmation.action's captured exact branch/node/version.
 * The mandatory renderer cannot silently omit the board or fabricate an archived/deleted replacement.
 */
@Composable fun BranchArchiveReturnDialog(confirmation: BranchArchiveConfirmation, branchLabel: String,
    returnLabel: String, connected: Boolean, onDismiss: () -> Unit,
    onConfirm: (CapturedBranchArchiveAction) -> Unit, board: @Composable (PlayState) -> Unit) {
    val action = confirmation.action
    val state = confirmation.preview.board
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Delete this branch and return") }, text = {
        Column(Modifier.fillMaxHeight(0.7f).verticalScroll(rememberScrollState())) {
            Text("Archive $branchLabel; return to $returnLabel.")
            Text("All actions, shared prefixes and descendant branches are retained. Descendants are not archived.")
            if ((action?.intent as? BranchArchiveIntent.ArchiveAndReturn)?.restoreTarget == true)
                Text("The selected return branch will also be restored.")
            if (state != null) board(state)
        }
    }, confirmButton = {
        TextButton(enabled = connected && action != null && state != null && !confirmation.preview.needsSelection,
            onClick = { if (action != null) onConfirm(action) }) { Text("Archive and return") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

/** Separate contextual recovery route; never relabel this as gameplay Undo. Both actions capture the
 * original control ID. The shared processor checks archive/selection applicability at execution.
 */
@Composable fun BranchArchiveRecoveryMenu(expanded: Boolean, onDismiss: () -> Unit, connected: Boolean,
    onRestoreMetadata: () -> Unit, onRestoreMetadataAndSelection: () -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text("Recover branch archive state") }, enabled = connected,
            onClick = { onDismiss(); onRestoreMetadata() })
        DropdownMenuItem(text = { Text("Recover archive state and selection") }, enabled = connected,
            onClick = { onDismiss(); onRestoreMetadataAndSelection() })
    }
}
