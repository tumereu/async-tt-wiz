package org.asyncttwiz.android

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.asyncttwiz.protocol.*

/** Header button that opens the preset list. */
@Composable
internal fun PodPresetsButton(controller: PodPresetController, enabled: Boolean = true) {
    ScreenOutlinedButton(controller::open, Modifier.testTag("pod-presets"), enabled) {
        EntryIcon(null, "presets"); Spacer(Modifier.width(6.dp)); Text("Presets", maxLines = 1)
    }
}

/** The account's presets: search, tap to load, rename/delete per row, and save the current form as a preset. */
@Composable
internal fun PodPresetsDialog(controller: PodPresetController) {
    val state = controller.state
    val prompt = state.prompt
    ScreenDialog(controller::dismiss, title = "Presets", close = "Close presets",
        busyMessage = state.busy.takeIf { prompt == null }, error = state.error.takeIf { prompt == null },
        dismissError = controller::dismissError, actions = {
            DialogMainButton("Save current settings as preset…", controller::startSave,
                Modifier.testTag("pod-preset-save"), enabled = state.busy == null && controller.canSave)
        }) {
        if (state.searchable) OutlinedTextField(state.query, controller::query, Modifier.fillMaxWidth().testTag("pod-preset-search"),
            label = { Text("Search presets") }, singleLine = true,
            keyboardOptions = imeOptions(ImeAction.Search), keyboardActions = hideKeyboardActions())
        val rows = state.filtered
        // A long list keeps its height while searching, so the dialog does not jump as rows filter out.
        Box(Modifier.fillMaxWidth().then(if (state.searchable) Modifier.height(400.dp) else Modifier.heightIn(min = 72.dp, max = 400.dp)),
            contentAlignment = Alignment.TopCenter) {
            when {
                !state.loaded && state.busy != null -> CircularProgressIndicator()
                !state.loaded -> ScreenOutlinedButton(controller::reload) { Text("Retry") }
                state.presets.isEmpty() -> Text("No presets yet. Save the current settings to make one.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                rows.isEmpty() -> Text("No presets match", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> LazyColumn(Modifier.fillMaxWidth().testTag("pod-preset-list")) {
                    items(rows, key = { it.id }) { preset ->
                        PodPresetRow(preset, state.busy == null, { controller.choose(preset) },
                            { controller.startRename(preset) }, { controller.startDelete(preset) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
    when (prompt) {
        is PodPresetPrompt.Name -> PodPresetNameDialog(controller, prompt)
        is PodPresetPrompt.Overwrite -> ScreenDialog(controller::cancelPrompt, title = "Replace preset",
            busyMessage = state.busy, error = state.error, dismissError = controller::dismissError, actions = {
                DialogCancelButton("Cancel", controller::cancelPrompt, enabled = state.busy == null)
                DialogMainButton("Replace", controller::confirmOverwrite, enabled = state.busy == null, danger = true)
            }) {
            Text("A preset called “${prompt.existing}” already exists. Replace it with the current settings?")
        }
        is PodPresetPrompt.Delete -> ScreenDialog(controller::cancelPrompt, title = "Delete preset",
            busyMessage = state.busy, error = state.error, dismissError = controller::dismissError, actions = {
                DialogCancelButton("Cancel", controller::cancelPrompt, enabled = state.busy == null)
                DialogMainButton("Delete", controller::confirmDelete, enabled = state.busy == null, danger = true)
            }) {
            Text("Delete “${prompt.preset.name}”?")
        }
        null -> Unit
    }
}

@Composable
private fun PodPresetRow(preset: PodPreset, enabled: Boolean, load: () -> Unit, rename: () -> Unit, delete: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(enabled = enabled, onClick = load).testTag("pod-preset-row"),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(preset.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(podPresetSummary(preset), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                color = if (preset.cubeMissing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(rename, enabled = enabled) { EntryIcon("Rename ${preset.name}", "rename") }
        IconButton(delete, enabled = enabled) { EntryIcon("Delete ${preset.name}", "delete") }
    }
}

@Composable
private fun PodPresetNameDialog(controller: PodPresetController, prompt: PodPresetPrompt.Name) {
    val state = controller.state
    val input = rememberDialogField(prompt.renaming?.name.orEmpty(), prompt)
    val valid = PodPresetValidation.name(input.text.trim())
    val submit = { controller.submitName(input.text) }
    ScreenDialog(controller::cancelPrompt, title = if (prompt.renaming == null) "Save preset" else "Rename preset",
        busyMessage = state.busy, actions = {
            DialogCancelButton("Cancel", controller::cancelPrompt, enabled = state.busy == null)
            DialogMainButton(if (prompt.renaming == null) "Save" else "Rename", submit, enabled = state.busy == null && valid)
        }) {
        OutlinedTextField(input.value, { if (it.text.length <= PodPresetValidation.MAX_NAME) input.value = it },
            Modifier.fillMaxWidth().dialogField(input).testTag("pod-preset-name"), label = { Text("Preset name") }, singleLine = true,
            keyboardOptions = imeOptions(ImeAction.Done), keyboardActions = submitKeyboardActions(state.busy == null && valid, submit = submit))
        if (prompt.renaming == null) controller.formSummary?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
