package org.asyncttwiz.android

import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import org.asyncttwiz.protocol.*

enum class PodSetting(val label: String) {
    PACKS("Packs"), PACK_SIZE("Pack size"), KEEP("Keep"), BURN("Burn"), DISCARD("Discard"),
    OPEN_PACKS("Open packs"), GRIDS("Grids"), OPEN_GRIDS("Open grids"), SIDEBOARD("Sideboard"), LIBRARIANS("Librarians"),
    /** Rochester: packs open at once across the table; empty = one per player. */
    TABLE_OPEN("Open packs"),
    /** Rotisserie and cube Sealed. */
    CARDS_EACH("Cards each"),
    /** Winston and Winchester. */
    PILES("Piles"),
    /** Sealed from a set. */
    BOOSTERS("Boosters"),
    /** Winston and Winchester: independent tables (DRF-156, DRF-158). */
    TABLES("Tables");

    fun value(s: DraftSettings): Int? = when (this) {
        PACKS -> s.packsPerPlayer; PACK_SIZE -> s.cardsPerPack; KEEP -> s.keep; BURN -> s.burn
        DISCARD -> s.discardRemainingAt; OPEN_PACKS -> s.packsOpenAtOnce; GRIDS -> s.grids
        OPEN_GRIDS -> s.gridsOpenAtOnce; SIDEBOARD -> s.sideboardLimit
        LIBRARIANS -> s.librarians
        TABLE_OPEN -> s.tablePacksOpen; CARDS_EACH -> s.cardsPerPlayer; PILES -> s.piles; BOOSTERS -> s.packsPerPlayer
        TABLES -> s.tables
    }
    /** The empty value's tile and toggle text: Sideboard ∞, Rochester's open packs "1 each" (one per player). */
    val emptyText: String? get() = when (this) { SIDEBOARD -> "∞"; TABLE_OPEN -> "1 each"; else -> null }
    fun range(s: DraftSettings): IntRange = when (this) {
        PACKS -> if (s.mode.limited) 1..15 else s.packsOpenAtOnce..30
        PACK_SIZE -> if (s.mode.limited) 5..30 else maxOf(1, s.keep + s.burn, s.discardRemainingAt)..30
        KEEP -> 1..(s.cardsPerPack - s.burn)
        BURN -> 0..(s.cardsPerPack - s.keep)
        DISCARD -> 0..s.cardsPerPack
        OPEN_PACKS -> 1..s.packsPerPlayer
        GRIDS -> s.gridsOpenAtOnce..60
        OPEN_GRIDS -> 1..s.grids
        SIDEBOARD -> 0..Int.MAX_VALUE
        LIBRARIANS -> 0..10
        TABLE_OPEN -> 1..s.packsPerPlayer * DraftMode.ROCHESTER.players.last
        CARDS_EACH -> 5..225
        PILES -> if (s.mode == DraftMode.WINCHESTER) 2..8 else 2..6
        BOOSTERS -> 1..30
        TABLES -> 1..s.mode.players.last
    }
    fun limit(s: DraftSettings): String = when (this) {
        PACKS -> if (s.mode.limited) "1–15" else "Open packs: ${s.packsOpenAtOnce}; maximum 30"
        PACK_SIZE -> if (s.mode.limited) "5–30" else "Keep + burn: ${s.keep + s.burn}; discard: ${s.discardRemainingAt}; maximum 30"
        KEEP -> "Pack size − burn: ${s.cardsPerPack - s.burn}"
        BURN -> "Pack size − keep: ${s.cardsPerPack - s.keep}"
        DISCARD -> "Pack size: ${s.cardsPerPack}"
        OPEN_PACKS -> "Packs: ${s.packsPerPlayer}"
        GRIDS -> "Open grids: ${s.gridsOpenAtOnce}; maximum 60"
        OPEN_GRIDS -> "Grids: ${s.grids}"
        SIDEBOARD -> "Minimum 0"
        LIBRARIANS -> "0–10"
        TABLE_OPEN -> "1–${range(s).last}; more than all packs opens them all"
        CARDS_EACH -> "5–225"
        PILES -> "${range(s).first}–${range(s).last}"
        BOOSTERS -> "1–30"
        TABLES -> "1–${range(s).last}; more than the players gives one table each"
    }
    /** Plain-words meaning shown in the value dialog. */
    fun explanation(s: DraftSettings): String = when (this) {
        PACKS -> if (s.mode == DraftMode.WINSTON || s.mode == DraftMode.WINCHESTER)
            "How many packs each player brings. All their cards are shuffled into one face-down stack."
            else "How many packs each player opens during the draft."
        PACK_SIZE -> "How many cards each pack starts with."
        KEEP -> "How many cards you take from a pack on each pick."
        BURN -> "How many extra cards you remove from the pack on each pick. Burned cards go to nobody."
        DISCARD -> "When a pack has this many cards or fewer left after a pick, the rest are thrown away instead of being passed on. 0 = never."
        OPEN_PACKS -> "How many packs can be open at the same time, so players don't have to wait for each other."
        GRIDS -> "How many 3×3 grids are drafted in total."
        OPEN_GRIDS -> "How many grids can be open at the same time, so players don't have to wait for each other."
        SIDEBOARD -> "The most cards a deck may have in its sideboard. ∞ = no limit."
        LIBRARIANS -> "Cogwork Librarians each player starts with. Spend one to take an extra card from a pack; the Librarian goes into that pack."
        TABLE_OPEN -> "How many packs lie open at the same time, so players don't have to wait for each other. 1 each = one pack per player."
        CARDS_EACH -> if (s.mode == DraftMode.SEALED) "How many random cube cards each player gets." else "How many cards each player picks from the pool."
        PILES -> if (s.mode == DraftMode.WINCHESTER) "How many face-up piles lie on the table." else "How many piles lie on the table."
        BOOSTERS -> "How many boosters each player opens."
        TABLES -> "How many separate tables the cards are split into, each with its own stack and piles, so players can pick at different tables at the same time."
    }
    fun valid(text: String, s: DraftSettings) = text.toIntOrNull()?.let { it in range(s) } == true
    fun apply(s: DraftSettings, value: Int?): DraftSettings = when (this) {
        PACKS -> s.copy(packsPerPlayer = value!!); PACK_SIZE -> s.copy(cardsPerPack = value!!)
        KEEP -> s.copy(keep = value!!); BURN -> s.copy(burn = value!!)
        DISCARD -> s.copy(discardRemainingAt = value!!); OPEN_PACKS -> s.copy(packsOpenAtOnce = value!!)
        GRIDS -> s.copy(grids = value!!); OPEN_GRIDS -> s.copy(gridsOpenAtOnce = value!!)
        SIDEBOARD -> s.copy(sideboardLimit = value)
        LIBRARIANS -> s.copy(librarians = value!!)
        TABLE_OPEN -> s.copy(tablePacksOpen = value)
        CARDS_EACH -> s.copy(cardsPerPlayer = value!!); PILES -> s.copy(piles = value!!); BOOSTERS -> s.copy(packsPerPlayer = value!!)
        TABLES -> s.copy(tables = value!!)
    }
}

/** The tiles a mode shows (DRF-022), Sideboard last; the pack size only where the source doesn't fix it (DRF-024). */
fun podSettingFields(settings: DraftSettings, source: PodSource): List<PodSetting> {
    val packSize = source.kind == PodSourceKind.CUBE || source.boosterKind == SetBoosterKind.APPROXIMATE
    return when (settings.mode) {
        DraftMode.GRID -> listOf(PodSetting.GRIDS, PodSetting.OPEN_GRIDS)
        DraftMode.ROCHESTER -> listOfNotNull(PodSetting.PACKS, PodSetting.PACK_SIZE.takeIf { packSize }, PodSetting.TABLE_OPEN)
        DraftMode.ROTISSERIE -> listOf(PodSetting.CARDS_EACH)
        DraftMode.WINSTON, DraftMode.WINCHESTER -> listOfNotNull(PodSetting.PACKS, PodSetting.PACK_SIZE.takeIf { packSize }, PodSetting.PILES, PodSetting.TABLES)
        DraftMode.SEALED -> listOf(if (source.kind == PodSourceKind.SET) PodSetting.BOOSTERS else PodSetting.CARDS_EACH)
        DraftMode.BOOSTER, DraftMode.GLIMPSE -> listOfNotNull(PodSetting.PACKS, PodSetting.PACK_SIZE.takeIf { packSize },
            PodSetting.KEEP, PodSetting.BURN, PodSetting.DISCARD, PodSetting.OPEN_PACKS, PodSetting.LIBRARIANS)
    } + PodSetting.SIDEBOARD
}

/** The modes the mode button's popup lists (DRF-020); Glimpse is a built-in preset of Booster, not a mode. */
val PodModes = listOf(DraftMode.BOOSTER, DraftMode.GRID, DraftMode.ROCHESTER, DraftMode.ROTISSERIE, DraftMode.WINSTON, DraftMode.WINCHESTER, DraftMode.SEALED)
/** The name a pod's mode reads as: pods and presets saved as Glimpse read "Booster" (DRF-020). */
fun podModeName(mode: DraftMode): String = when (mode) {
    DraftMode.BOOSTER, DraftMode.GLIMPSE -> "Booster"
    DraftMode.GRID -> "Grid"
    DraftMode.ROCHESTER -> "Rochester"
    DraftMode.ROTISSERIE -> "Rotisserie"
    DraftMode.WINSTON -> "Winston"
    DraftMode.WINCHESTER -> "Winchester"
    DraftMode.SEALED -> "Sealed"
}
/** The listed mode a pod's mode is: a Glimpse pod is a Booster pod. */
fun podModeChoice(mode: DraftMode): DraftMode = if (mode == DraftMode.GLIMPSE) DraftMode.BOOSTER else mode
/** A mode with its engine's player range (DRF-150), e.g. "Winston · 2–4 players". */
fun podModeOption(mode: DraftMode): String = "${podModeName(mode)} · ${mode.players.first}–${mode.players.last} players"

fun podModeSettings(mode: DraftMode, current: DraftSettings) = DraftSettings.defaults(mode)
    .copy(sideboardLimit = current.sideboardLimit, freshBoosters = current.freshBoosters,
    librarians = if (mode.packPassing) current.librarians else 0,
    librarianDefinition = if (mode.packPassing) current.librarianDefinition else null)

/** The same complete form is used before and after creation. */
@Composable
fun PodSettingsForm(name: String, settings: DraftSettings, source: PodSource?, members: Int,
    editable: Boolean, rename: (String) -> Unit, chooseSource: () -> Unit,
    change: ((DraftSettings) -> DraftSettings) -> Unit, reroll: (() -> Unit)? = null, automatum: CardListSupport? = null,
    grid: PodPackGridUi? = null) {
    // The other settings follow as choices, not fields: the action key only closes the keyboard.
    OutlinedTextField(name, rename, Modifier.fillMaxWidth(), label = { Text("Pod name") }, singleLine = true,
        readOnly = !editable, keyboardOptions = imeOptions(ImeAction.Done), keyboardActions = hideKeyboardActions(), trailingIcon = reroll?.let { action -> { ScreenIconButton("Suggest another pod name", "shuffle", action) } })
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // DRF-177: a pack grid of several sets reads "Mixed sets"
        Text(source?.let { if (it.kind == PodSourceKind.SET) (if (grid != null && PodPackGrid.mixed(grid.packs)) "Mixed sets" else null)
            ?: it.setName ?: it.setCode.orEmpty() else it.cubeName }
            ?.takeIf { it.isNotBlank() } ?: "No source", Modifier.weight(1f))
        // DRF-161: the chosen cube's or set's Automatum support
        AutomatumSupportIcon(automatum, Modifier.testTag("automatum-support"))
        if (editable) ScreenIconButton("Change source", "edit", chooseSource)
    }
    if (source?.kind == PodSourceKind.SET && grid != null) PodPackGridSection(settings, grid)
    PodSettingsFields(settings, members, editable, source ?: PodSource(), change)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PodSettingsFields(settings: DraftSettings, members: Int, enabled: Boolean,
    source: PodSource = PodSource(), change: ((DraftSettings) -> DraftSettings) -> Unit) {
    PodModeButton(settings.mode, enabled) { mode -> change { podModeSettings(mode, it) } }
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        podSettingFields(settings, source).forEach { field -> key(field) { PodSettingTile(field, settings, enabled, change) } }
        if (settings.mode.packPassing && source.kind == PodSourceKind.CUBE) key("colorBalance") {
            PodToggleTile("Color balancing", settings.colorBalance, enabled) { value -> change { it.copy(colorBalance = value) } }
        }
        // Always shown, greyed out unless the pod drafts grids from a set.
        key("freshBoosters") {
            val applicable = settings.mode == DraftMode.GRID && source.kind == PodSourceKind.SET
            PodToggleTile("Fresh boosters", settings.freshBoosters, enabled && applicable, applicable) { value ->
                change { it.copy(freshBoosters = value) }
            }
        }
    }
    if (settings.mode == DraftMode.GRID) Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(settings.twoPicks, { value -> change { it.copy(twoPicks = value) } }, enabled = enabled && members <= 2)
        Text("Two picks each")
    }
}

/** DRF-020: one full-width button showing the pod's mode with a switch icon; it opens a popup list of the modes,
 * each with its player range, the current one checked. */
@Composable
fun PodModeButton(mode: DraftMode, enabled: Boolean, choose: (DraftMode) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        ScreenOutlinedButton({ open = true }, Modifier.fillMaxWidth().testTag("pod-mode"), enabled = enabled) {
            Text(podModeName(mode), Modifier.weight(1f))
            EntryIcon("Change mode", "switch")
        }
        DropdownMenu(open, { open = false }, Modifier.testTag("pod-modes")) {
            PodModes.forEach { option ->
                val current = podModeChoice(mode) == option
                DropdownMenuItem(text = { Text(podModeOption(option)) }, onClick = { open = false; choose(option) },
                    modifier = Modifier.semantics { selected = current },
                    trailingIcon = if (current) ({ EntryIcon("Current mode", "ready") }) else null)
            }
        }
    }
}

/** On/off setting in the same square tile shape as the numeric settings; a tap toggles it. Greyed when not [applicable]. */
@Composable
private fun PodToggleTile(label: String, checked: Boolean, enabled: Boolean, applicable: Boolean = true, toggle: (Boolean) -> Unit) {
    Surface(checked = checked, onCheckedChange = toggle, enabled = enabled, modifier = Modifier.size(96.dp),
        shape = MaterialTheme.shapes.small, border = BorderStroke(1.dp, ScreenBorder),
        color = if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) {
        Box(Modifier.fillMaxSize().padding(6.dp).alpha(if (applicable) 1f else .38f)) {
            Text(label, Modifier.align(Alignment.TopCenter), style = MaterialTheme.typography.labelSmall)
            Text(if (checked) "On" else "Off", Modifier.align(Alignment.Center), style = MaterialTheme.typography.headlineSmall)
        }
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
            val value = field.value(settings)
            Text(value?.toString() ?: field.emptyText.orEmpty(), Modifier.align(Alignment.Center),
                style = if (value == null && field == PodSetting.TABLE_OPEN) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineMedium)
        }
    }
    if (open) PodSettingPopup(field, settings, { open = false }) { number ->
        change { current -> if (number == null && field.emptyText != null || number != null && number in field.range(current))
            field.apply(current, number) else current }
        open = false
    }
}

@Composable
fun PodSettingPopup(field: PodSetting, settings: DraftSettings, dismiss: () -> Unit, save: (Int?) -> Unit) {
    val input = rememberDialogField((field.value(settings) ?: if (field == PodSetting.TABLE_OPEN) 1 else 0).toString())
    // Sideboard's ∞ and Rochester's "1 each" are the setting left empty.
    var unlimited by remember { mutableStateOf(field.emptyText != null && field.value(settings) == null) }
    val range = field.range(settings)
    val saveEnabled = unlimited || field.valid(input.text, settings)
    val confirm = { save(if (unlimited) null else input.text.toInt()) }
    ScreenDialog(dismiss, Modifier.widthIn(max = 320.dp), title = field.label, actions = {
        DialogCancelButton("Cancel", dismiss)
        DialogMainButton("Save", confirm, enabled = saveEnabled, icon = "save")
    }) {
        Text(field.explanation(settings), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(input.value, { input.value = it }, Modifier.weight(1f).dialogField(input), enabled = !unlimited, singleLine = true,
                label = { Text("${range.first}–${range.last}") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = submitKeyboardActions(saveEnabled, submit = confirm))
            field.emptyText?.let { empty -> OutlinedButton({ unlimited = !unlimited }, Modifier.size(56.dp)
                .semantics { contentDescription = if (field == PodSetting.SIDEBOARD) "Unlimited sideboard" else "One pack per player"; selected = unlimited },
                contentPadding = PaddingValues(0.dp), shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.outlinedButtonColors(containerColor = if (unlimited) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)) {
                Text(if (unlimited) "$empty ✓" else empty, fontSize = if (field == PodSetting.SIDEBOARD) TextUnit.Unspecified else 11.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            } }
        }
    }
}
