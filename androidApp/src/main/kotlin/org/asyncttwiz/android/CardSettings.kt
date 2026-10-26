package org.asyncttwiz.android

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
    Column(Modifier.fillMaxWidth().testTag("cards-settings"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Cards", style = MaterialTheme.typography.titleMedium)
        Text("Saved on this device. Card sizes do not change battlefield zoom. Text sizes apply to all table cards.",
            style = MaterialTheme.typography.bodySmall)
        CardScaleSlider("Card size", "card-size", appearance.cardSize, { preferences.setSize(it) }, appearance,
            appearance.cardSize)
        CardCustomizeToggle("Customize per area", appearance.customizeAreas, preferences::customizeAreas)
        if (appearance.customizeAreas) for (area in CardArea.entries) {
            CardScaleSlider(area.label, "card-size-${area.name}", appearance.size(area), { preferences.setSize(it, area) },
                appearance, appearance.size(area))
        }
        CardScaleSlider("Card text size", "card-text-size", appearance.textSize, { preferences.setText(it) },
            appearance.copy(customizeText = false), appearance.cardSize)
        CardCustomizeToggle("Customize per text", appearance.customizeText, preferences::customizeText)
        if (appearance.customizeText) for (part in CardTextScalePart.entries) {
            CardScaleSlider(part.label, "card-text-${part.name}", appearance.text(part), { preferences.setText(it, part) },
                appearance, appearance.cardSize)
        }
    }
}

@Composable
private fun CardCustomizeToggle(label: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(checked, role = Role.Checkbox, onValueChange = change),
        verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, null)
        Text(label)
    }
}

@Composable
private fun CardScaleSlider(label: String, tag: String, value: Float, change: (Float) -> Unit,
    appearance: CardAppearance, sampleScale: Float) {
    var exact by remember { mutableStateOf(false) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            TextButton({ exact = true }, Modifier.testTag("$tag-value")) {
                Text("${value}×")
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Slider(value, { change((it * 100).roundToInt() / 100f) }, valueRange = MIN_CARD_SCALE..MAX_CARD_SCALE,
                modifier = Modifier.weight(1f).testTag(tag))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Sample card", style = MaterialTheme.typography.labelSmall)
                SampleTableCard(appearance, sampleScale, Modifier.testTag("$tag-sample"))
            }
        }
    }
    if (exact) CardScaleInput(label, value, { exact = false }) { change(it); exact = false }
}

@Composable
internal fun CardScaleInput(label: String, value: Float, dismiss: () -> Unit, save: (Float) -> Unit) {
    var input by remember { mutableStateOf(value.toString()) }
    val parsed = parseCardScale(input)
    ScreenDialog(dismiss) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Text("Scale (0.75–2.00×)")
        ScreenTextField(input, { input = it }, modifier = Modifier.fillMaxWidth().testTag("card-scale-input"),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        if (parsed == null) Text("Enter a number from 0.75 to 2.00", color = MaterialTheme.colorScheme.error)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(dismiss) { Text("Cancel") }
            TextButton({ parsed?.let(save) }, enabled = parsed != null, modifier = Modifier.testTag("card-scale-save")) { Text("Save") }
        }
    }
}
