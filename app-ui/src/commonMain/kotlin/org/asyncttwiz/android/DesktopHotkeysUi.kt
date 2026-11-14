package org.asyncttwiz.android

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.asyncttwiz.appui.BackHandler

/** The test tag of the "?" sheet (WEB-034). */
const val HOTKEY_SHEET_TAG = "hotkey-sheet"

/** The test tag of a hotkey's row in Settings (WEB-035), by its id. */
fun hotkeyRowTag(action: HotkeyAction) = "hotkey-${action.id}"

/** A key as a key cap (D6): "Shift+Space" as one cap. */
@Composable
fun KeyCap(text: String, modifier: Modifier = Modifier, ink: Color = Color(0xff222222), border: Color = Color(0xff9aa0a6)) {
    Box(modifier.background(Color.White, RoundedCornerShape(5.dp)).border(1.dp, border, RoundedCornerShape(5.dp))
        .padding(horizontal = 7.dp, vertical = 2.dp), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium, color = ink, maxLines = 1)
    }
}

/** What the mouse does on the desktop layout, as the "?" sheet lists it (§5). */
private val MOUSE_ROWS = listOf(
    "Click" to "Tap", "Double click" to "Double tap; on a play shown or a combat hold, ends it",
    "Hover" to "Quick look at the picture; a usable card's chips, faded", "Right-click" to "A control's hold action; in a draft pick, burn",
    "Wheel" to "Zoom (Shift: pans sideways)", "Middle drag" to "Pan the table")

/**
 * WEB-034 (wireframe D6): the hotkey list over the screen, opened by "?" on the desktop layout: every hotkey with the player's
 * current key ([bindings]; "No key" where they took it away), the fixed keys and the mouse, by where they act, and "Change
 * keys in Settings" ([settings]). "?", Esc, ✕ or a click outside closes it ([close]).
 */
@Composable
fun HotkeySheet(bindings: KeyBindings, settings: () -> Unit, close: () -> Unit) {
    BackHandler { close() }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .4f)).pointerInput(Unit) { detectTapGestures { close() } },
        contentAlignment = Alignment.Center) {
        Surface(Modifier.padding(24.dp).widthIn(max = 1000.dp).fillMaxWidth().testTag(HOTKEY_SHEET_TAG)
            .pointerInput(Unit) { detectTapGestures { } }.semantics { contentDescription = "Keyboard shortcuts" },
            shape = RoundedCornerShape(14.dp), color = Color.White, shadowElevation = 12.dp) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Keyboard shortcuts", Modifier.weight(1f).semantics { heading() }, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                        color = MenuInk)
                    IconButton(close, Modifier.testTag("hotkey-sheet-close")) { EntryIcon("Close", "close") }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Your keys, kept on your account. They don't act while you type in a text field. ? opens this list, Esc closes it.",
                        Modifier.weight(1f), fontSize = 12.sp, color = MenuInk.copy(alpha = .75f))
                    OutlinedButton(settings, Modifier.testTag("hotkey-sheet-settings")) { Text("Change keys in Settings") }
                }
                val keyed = HotkeyAction.all.groupBy { it.place }
                fun rows(place: HotkeyPlace): List<Pair<String, String>> =
                    keyed[place].orEmpty().map { (bindings.keyOf(it)?.label ?: "No key") to it.label }
                // The fixed keys: the panels' (Esc, Enter, 1–9) apart, the finger key and "?" with the mouse
                val panels = FixedKey.all.filter { it.keys in PANEL_KEYS }.map { it.keys to it.label }
                val others = FixedKey.all.filter { it.keys !in PANEL_KEYS }.map { it.keys to it.label }
                Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    Column(Modifier.weight(1f)) { SheetGroup(HotkeyPlace.Table.title, rows(HotkeyPlace.Table)) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        SheetGroup("Panels and choices", panels)
                        SheetGroup(HotkeyPlace.Timeline.title, rows(HotkeyPlace.Timeline))
                        SheetGroup(HotkeyPlace.Pod.title, rows(HotkeyPlace.Pod))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        SheetGroup(HotkeyPlace.Draft.title, rows(HotkeyPlace.Draft))
                        SheetGroup(HotkeyPlace.Editor.title, rows(HotkeyPlace.Editor))
                        SheetGroup(HotkeyPlace.Cards.title, FixedKey.cards.map { it.keys to it.label })
                        SheetGroup("Mouse", others, extra = MOUSE_ROWS)
                    }
                }
            }
        }
    }
}

/** The fixed keys the "?" sheet lists under "Panels and choices". */
private val PANEL_KEYS = setOf("Esc", "Enter", "1–9")

@Composable
private fun SheetGroup(title: String, rows: List<Pair<String, String>>, caps: Boolean = true,
    /** Rows after [rows] whose first part is plain words (the mouse's), not a key cap. */
    extra: List<Pair<String, String>> = emptyList()) {
    Column {
        Text(title.uppercase(), Modifier.padding(bottom = 6.dp).semantics { heading() }, fontSize = 12.sp, letterSpacing = .7.sp,
            fontWeight = FontWeight.Bold, color = Color(0xff667077))
        for ((index, row) in (rows + extra).withIndex()) {
            val (keys, label) = row
            val cap = caps && index < rows.size
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(132.dp)) {
                    if (cap) KeyCap(keys, ink = if (keys == "No key") Color(0xff8a8f94) else Color(0xff222222))
                    else Text(keys, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MenuInk)
                }
                Text(label, Modifier.weight(1f), fontSize = 13.sp, lineHeight = 16.sp, color = MenuInk)
            }
            HorizontalDivider(color = Color(0xffeceef0))
        }
    }
}

/**
 * WEB-034: "?" opens the hotkey list over any screen of the desktop layout ([enabled]), never while a text field has the
 * keyboard; while it shows it takes every key (but Esc, which Compose's Back gives it), and "?" again closes it.
 */
@Composable
fun HotkeySheetHost(enabled: Boolean, settings: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    if (!enabled && open) open = false
    // Listening from sign-in on, behind the screens' own listeners (which never take "?")
    DesktopKeyboard.Listen(enabled) { key ->
        if (open || !key.down || key.textField || key.repeat || KeyChord.of(key)?.key != "?") return@Listen false
        AppLog.info("keys", "Hotkey ? : the list of keys opened")
        open = true
        true
    }
    if (!open) return
    val close = { AppLog.info("keys", "The list of keys closed"); open = false }
    DesktopKeyboard.Listen(true) { key ->
        if (key.key == "Escape" || key.key == "Esc" || key.key == "Alt" || key.key == "Shift") return@Listen false
        if (key.down && !key.repeat && KeyChord.of(key)?.key == "?") close()
        true
    }
    HotkeySheet(currentKeyBindings(), { close(); settings() }, close)
}

/**
 * WEB-035 (wireframe D10): Settings → "Keyboard shortcuts" on the desktop layout: every hotkey with its key; a click on a key
 * waits for the next key press (Esc cancels) and binds it, a key another hotkey had leaving that one without a key and saying
 * so on its row; Esc, Enter, 1–9 and "?" can't be taken. "Reset all" restores the defaults. Kept on the account ([state]),
 * applied at once ([applied]).
 */
@Composable
fun KeyboardShortcutsSetting(state: AccountSettingsState?, applied: KeyBindingsState?) {
    val bindings = state?.settings?.keyBindings?.let(::KeyBindings) ?: applied?.bindings ?: KeyBindings()
    var waiting by remember { mutableStateOf<HotkeyAction?>(null) }
    // The hotkey that lost its key to another (and which), shown on its row until the next change
    var lost by remember { mutableStateOf<Pair<HotkeyAction, HotkeyAction>?>(null) }
    var refused by remember { mutableStateOf<Pair<HotkeyAction, String>?>(null) }
    fun save(next: KeyBindings) {
        if (state != null) state.setKeyBindings(next, applied) else applied?.set(next)
    }
    DesktopKeyboard.Listen(waiting != null) { key ->
        val action = waiting ?: return@Listen false
        if (!key.down) return@Listen true
        if (key.key == "Escape" || key.key == "Esc") {
            AppLog.info("keys", "Settings: changing the key of ${action.id} cancelled")
            waiting = null
            return@Listen true
        }
        val chord = KeyChord.of(key) ?: return@Listen key.key in KeyChord.MODIFIERS
        KeyBindings.refusal(chord)?.let { why ->
            AppLog.info("keys", "Settings: ${chord.text} refused for ${action.id}: $why")
            refused = action to why
            return@Listen true
        }
        val bound = bindings.bind(action, chord)
        AppLog.info("keys", "Settings: ${action.id} on ${chord.text}" + (bound.took?.let { ", ${it.id} left without a key" } ?: ""))
        lost = bound.took?.let { it to action }
        refused = null
        waiting = null
        save(bound.bindings)
        true
    }
    Column(Modifier.fillMaxWidth().testTag("keyboard-shortcuts")) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            SettingsLabel("Keyboard shortcuts", "Kept on your account: every browser shows the same. Click a key to change it.",
                Modifier.weight(1f))
            OutlinedButton({
                AppLog.info("keys", "Settings: every key reset to its default")
                waiting = null; lost = null; refused = null
                save(bindings.reset())
            }, Modifier.testTag("hotkeys-reset"), enabled = bindings.overrides.isNotEmpty()) { Text("Reset all") }
        }
        for (place in HotkeyPlace.all) {
            Text(place.title, Modifier.padding(top = 10.dp, bottom = 2.dp).semantics { heading() },
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            for (action in HotkeyAction.all.filter { it.place == place }) {
                val key = bindings.keyOf(action)
                val now = waiting == action
                val note = when {
                    now -> refused?.takeIf { it.first == action }?.second
                    lost?.first == action -> "No key: ${lost?.second?.label} took it"
                    else -> null
                }
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).background(if (now) Color(0xfff1ecfb) else Color.Transparent)
                    .testTag(hotkeyRowTag(action)).clickable(role = Role.Button) {
                        AppLog.info("keys", "Settings: waiting for a key for ${action.id}")
                        refused = null
                        waiting = if (now) null else action
                    }.semantics(mergeDescendants = true) {
                        contentDescription = "${action.label}: ${if (now) "press a key" else key?.label ?: "no key"}"
                        stateDescription = note.orEmpty()
                    }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(action.label, style = MaterialTheme.typography.bodyLarge)
                        if (note != null) Text(note, style = MaterialTheme.typography.bodySmall, color = Color(0xffb26a00))
                    }
                    if (now) KeyCap("Press a key… Esc cancels", ink = AutomatumViolet, border = AutomatumViolet)
                    else KeyCap(key?.label ?: "No key", ink = if (key == null) Color(0xff8a8f94) else Color(0xff222222))
                }
            }
        }
    }
}
