package org.asyncttwiz.android

import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
internal fun CardSettings(preferences: CardAppearancePreferences = rememberCardAppearancePreferences()) {
    val appearance by preferences.state()
    Column(Modifier.fillMaxWidth().testTag("cards-settings")) {
        // One sample card shows both card size and text size; per-area sizes each show their own area's card.
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Card sizes are saved on this device. Card size also applies to the battlefield, independently of zoom. " +
                "Text sizes apply to all table cards.", Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            CardSample(appearance, appearance.cardSize, "card-size-sample")
        }
        CardScaleSlider("Card size", "card-size", appearance.cardSize, { preferences.setSize(it) })
        CardCustomizeToggle("Customize per area", appearance.customizeAreas, preferences::customizeAreas)
        if (appearance.customizeAreas) for (area in CardArea.entries) {
            CardScaleSlider(area.label, "card-size-${area.name}", appearance.size(area), { preferences.setSize(it, area) },
                appearance, appearance.size(area))
        }
        CardScaleSlider("Card text size", "card-text-size", appearance.textSize, { preferences.setText(it) })
        CardCustomizeToggle("Customize per text", appearance.customizeText, preferences::customizeText)
        if (appearance.customizeText) for (part in CardTextScalePart.entries) {
            CardScaleSlider(part.label, "card-text-${part.name}", appearance.text(part), { preferences.setText(it, part) })
        }
    }
}

@Composable
private fun CardCustomizeToggle(label: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(checked, role = Role.Checkbox, onValueChange = change),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Checkbox(checked, null)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun CardScaleSlider(label: String, tag: String, value: Float, change: (Float) -> Unit,
    appearance: CardAppearance? = null, sampleScale: Float = 1f) {
    var exact by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f)) {
            Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                SettingsLabel(label, modifier = Modifier.weight(1f))
                TextButton({ exact = true }, Modifier.testTag("$tag-value"), contentPadding = SettingsValuePadding) { Text("${value}×") }
            }
            Slider(value, { change((it * 100).roundToInt() / 100f) }, valueRange = MIN_CARD_SCALE..MAX_CARD_SCALE,
                modifier = Modifier.testTag(tag))
        }
        if (appearance != null) CardSample(appearance, sampleScale, "$tag-sample")
    }
    if (exact) CardScaleInput(label, value, { exact = false }) { change(it); exact = false }
}

@Composable
private fun CardSample(appearance: CardAppearance, scale: Float, tag: String) {
    Column(Modifier.widthIn(min = 64.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Sample", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SampleTableCard(appearance, scale, Modifier.testTag(tag))
    }
}

@Composable
internal fun CardScaleInput(label: String, value: Float, dismiss: () -> Unit, save: (Float) -> Unit) {
    ScaleValueInput(label, value, MIN_CARD_SCALE..MAX_CARD_SCALE, dismiss, save)
}

/** Tap-to-type multiplier editor shared by card appearance and movement speed. */
@Composable
internal fun ScaleValueInput(label: String, value: Float, range: ClosedFloatingPointRange<Float>,
    dismiss: () -> Unit, save: (Float) -> Unit) {
    val input = rememberDialogField(value.toString())
    val parsed = input.text.trim().toFloatOrNull()?.takeIf { it.isFinite() }?.coerceIn(range)
    ScreenDialog(dismiss, title = label, actions = {
        DialogCancelButton("Cancel", dismiss)
        DialogMainButton("Save", { parsed?.let(save) }, Modifier.testTag("card-scale-save"), enabled = parsed != null)
    }) {
        Text("Scale (${range.start}–${range.endInclusive}×)")
        ScreenTextField(input.value, { input.value = it }, modifier = Modifier.fillMaxWidth().testTag("card-scale-input").dialogField(input),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = submitKeyboardActions(parsed != null) { parsed?.let(save) })
        if (parsed == null) Text("Enter a finite number", color = MaterialTheme.colorScheme.error)
    }
}
