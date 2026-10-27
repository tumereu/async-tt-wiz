package org.asyncttwiz.android

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.asyncttwiz.protocol.*

internal enum class PodSetting(val label: String) {
    PACKS("Packs"), PACK_SIZE("Pack size"), KEEP("Keep"), BURN("Burn"), DISCARD("Discard"),
    OPEN_PACKS("Open packs"), GRIDS("Grids"), OPEN_GRIDS("Open grids"), SIDEBOARD("Sideboard");

    fun value(s: DraftSettings): Int? = when (this) {
        PACKS -> s.packsPerPlayer; PACK_SIZE -> s.cardsPerPack; KEEP -> s.keep; BURN -> s.burn
        DISCARD -> s.discardRemainingAt; OPEN_PACKS -> s.packsOpenAtOnce; GRIDS -> s.grids
        OPEN_GRIDS -> s.gridsOpenAtOnce; SIDEBOARD -> s.sideboardLimit
    }
    fun range(s: DraftSettings): IntRange = when (this) {
        PACKS -> s.packsOpenAtOnce..30
        PACK_SIZE -> maxOf(1, s.keep + s.burn, s.discardRemainingAt)..30
        KEEP -> 1..(s.cardsPerPack - s.burn)
        BURN -> 0..(s.cardsPerPack - s.keep)
        DISCARD -> 0..s.cardsPerPack
        OPEN_PACKS -> 1..s.packsPerPlayer
        GRIDS -> s.gridsOpenAtOnce..60
        OPEN_GRIDS -> 1..s.grids
        SIDEBOARD -> 0..Int.MAX_VALUE
    }
    fun limit(s: DraftSettings): String = when (this) {
        PACKS -> "Open packs: ${s.packsOpenAtOnce}; maximum 30"
        PACK_SIZE -> "Keep + burn: ${s.keep + s.burn}; discard: ${s.discardRemainingAt}; maximum 30"
        KEEP -> "Pack size − burn: ${s.cardsPerPack - s.burn}"
        BURN -> "Pack size − keep: ${s.cardsPerPack - s.keep}"
        DISCARD -> "Pack size: ${s.cardsPerPack}"
        OPEN_PACKS -> "Packs: ${s.packsPerPlayer}"
        GRIDS -> "Open grids: ${s.gridsOpenAtOnce}; maximum 60"
        OPEN_GRIDS -> "Grids: ${s.grids}"
        SIDEBOARD -> "Minimum 0"
    }
    fun valid(text: String, s: DraftSettings) = text.toIntOrNull()?.let { it in range(s) } == true
    fun apply(s: DraftSettings, value: Int?): DraftSettings = when (this) {
        PACKS -> s.copy(packsPerPlayer = value!!); PACK_SIZE -> s.copy(cardsPerPack = value!!)
        KEEP -> s.copy(keep = value!!); BURN -> s.copy(burn = value!!)
        DISCARD -> s.copy(discardRemainingAt = value!!); OPEN_PACKS -> s.copy(packsOpenAtOnce = value!!)
        GRIDS -> s.copy(grids = value!!); OPEN_GRIDS -> s.copy(gridsOpenAtOnce = value!!)
        SIDEBOARD -> s.copy(sideboardLimit = value)
    }
}

internal fun podModeSettings(mode: DraftMode, current: DraftSettings) = when (mode) {
    DraftMode.BOOSTER -> DraftSettings.booster()
    DraftMode.GLIMPSE -> DraftSettings.glimpse()
    DraftMode.GRID -> DraftSettings.grid()
}.copy(sideboardLimit = current.sideboardLimit)

/** The same complete form is used before and after creation. */
@Composable
internal fun PodSettingsForm(name: String, settings: DraftSettings, source: PodSource?, members: Int,
    editable: Boolean, rename: (String) -> Unit, chooseSource: () -> Unit,
    change: ((DraftSettings) -> DraftSettings) -> Unit, reroll: (() -> Unit)? = null) {
    OutlinedTextField(name, rename, Modifier.fillMaxWidth(), label = { Text("Pod name") }, singleLine = true,
        readOnly = !editable, trailingIcon = reroll?.let { action -> { ScreenIconButton("Suggest another pod name", "shuffle", action) } })
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(source?.let { if (it.kind == PodSourceKind.SET) it.setName ?: it.setCode.orEmpty() else it.cubeName }
            ?.takeIf { it.isNotBlank() } ?: "No source", Modifier.weight(1f))
        if (editable) ScreenIconButton("Change source", "edit", chooseSource)
    }
    PodSettingsFields(settings, members, editable, source ?: PodSource(), change)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PodSettingsFields(settings: DraftSettings, members: Int, enabled: Boolean,
    source: PodSource = PodSource(), change: ((DraftSettings) -> DraftSettings) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DraftMode.entries.forEach { mode -> FilterChip(settings.mode == mode,
            { change { podModeSettings(mode, it) } }, enabled = enabled,
            label = { Text(mode.name.lowercase().replaceFirstChar(Char::uppercase)) }) }
    }
    val fields = if (settings.mode == DraftMode.GRID) listOf(PodSetting.GRIDS, PodSetting.OPEN_GRIDS)
        else buildList {
            add(PodSetting.PACKS)
            if (source.kind == PodSourceKind.CUBE || source.boosterKind == SetBoosterKind.APPROXIMATE) add(PodSetting.PACK_SIZE)
            addAll(listOf(PodSetting.KEEP, PodSetting.BURN, PodSetting.DISCARD, PodSetting.OPEN_PACKS))
        }
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        (fields + PodSetting.SIDEBOARD).forEach { field -> key(field) { PodSettingTile(field, settings, enabled, change) } }
    }
    if (settings.mode == DraftMode.GRID) Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(settings.twoPicks, { value -> change { it.copy(twoPicks = value) } }, enabled = enabled && members <= 2)
        Text("Two picks each")
    } else if (source.kind == PodSourceKind.CUBE) Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(settings.colorBalance, { value -> change { it.copy(colorBalance = value) } }, enabled = enabled)
        Text("Color balancing")
    }
}

@Composable
private fun PodSettingTile(field: PodSetting, settings: DraftSettings, enabled: Boolean,
    change: ((DraftSettings) -> DraftSettings) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Surface(onClick = { open = true }, enabled = enabled, modifier = Modifier.size(96.dp),
        shape = MaterialTheme.shapes.small, border = BorderStroke(1.dp, ScreenBorder)) {
        Box(Modifier.fillMaxSize().padding(6.dp)) {
            Text(field.label, Modifier.align(Alignment.TopCenter), style = MaterialTheme.typography.labelSmall)
            Text(field.value(settings)?.toString() ?: "∞", Modifier.align(Alignment.Center), style = MaterialTheme.typography.headlineMedium)
        }
    }
    if (open) PodSettingPopup(field, settings, { open = false }) { number ->
        change { current -> if (number == null && field == PodSetting.SIDEBOARD || number != null && number in field.range(current))
            field.apply(current, number) else current }
        open = false
    }
}

@Composable
internal fun PodSettingPopup(field: PodSetting, settings: DraftSettings, dismiss: () -> Unit, save: (Int?) -> Unit) {
    var text by remember { mutableStateOf((field.value(settings) ?: 0).toString()) }
    var unlimited by remember { mutableStateOf(field == PodSetting.SIDEBOARD && settings.sideboardLimit == null) }
    val range = field.range(settings)
    ScreenDialog(dismiss, Modifier.widthIn(max = 320.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(field.label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            ScreenIconButton("Close setting", "close", dismiss)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(text, { text = it }, Modifier.weight(1f), enabled = !unlimited, singleLine = true,
                label = { Text("${range.first}–${range.last}") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            if (field == PodSetting.SIDEBOARD) OutlinedButton({ unlimited = !unlimited }, Modifier.size(56.dp)
                .semantics { contentDescription = "Unlimited sideboard"; selected = unlimited },
                contentPadding = PaddingValues(0.dp), shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.outlinedButtonColors(containerColor = if (unlimited) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)) {
                Text(if (unlimited) "∞ ✓" else "∞")
            }
        }
        Text(field.limit(settings), style = MaterialTheme.typography.bodySmall)
        ScreenButton({ save(if (unlimited) null else text.toInt()) }, enabled = unlimited || field.valid(text, settings)) { Text("Save") }
    }
}
