package org.asyncttwiz.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

// The desktop layout's hotkeys (docs/specs/web-client.md §6; WEB-031 to WEB-036): which key does what, rebindable and kept on
// the account (decision 23), and the hooks the screens act through. The phone layout and Android have none of it.

/**
 * A key with its modifiers, as the hotkeys name it: [key] is the key's name ("Space", a letter "A"–"Z", a digit, "ArrowLeft",
 * "Tab", "F2", or the character a symbol key types, "?" or "/"), [shift] and [ctrl] its modifiers (⌘ on a Mac counts as
 * Ctrl). Written as "Shift+Space", "Ctrl+Z", "T" ([text]) on the wire and in the settings.
 */
data class KeyChord(val key: String, val shift: Boolean = false, val ctrl: Boolean = false) {
    /** "Ctrl+Shift+Z" style: the wire form ([parse] reads it back). */
    val text: String get() = buildString {
        if (ctrl) append("Ctrl+")
        if (shift) append("Shift+")
        append(key)
    }

    /** What a person reads: "Shift+Space", "Ctrl+Z", "←". */
    val label: String get() = buildString {
        if (ctrl) append("Ctrl+")
        if (shift) append("Shift+")
        append(KEY_LABELS[key] ?: key)
    }

    companion object {
        private val KEY_LABELS = mapOf("ArrowLeft" to "←", "ArrowRight" to "→", "ArrowUp" to "↑", "ArrowDown" to "↓",
            "Escape" to "Esc", "PageUp" to "Page Up", "PageDown" to "Page Down")

        /** The modifier keys alone: never a hotkey of their own (Alt and Shift are the finger key, §5). */
        val MODIFIERS = setOf("Shift", "Control", "Alt", "Meta", "AltGraph", "CapsLock", "Fn", "OS", "Hyper", "Super")

        /** [text] ("Shift+G") read back; null when it names no key. */
        fun parse(text: String): KeyChord? {
            if (text.isEmpty()) return null
            var rest = text
            var ctrl = false
            var shift = false
            while (true) {
                if (rest.startsWith("Ctrl+") && rest.length > 5) { ctrl = true; rest = rest.removePrefix("Ctrl+") }
                else if (rest.startsWith("Shift+") && rest.length > 6) { shift = true; rest = rest.removePrefix("Shift+") }
                else break
            }
            return KeyChord(rest, shift, ctrl)
        }

        /**
         * The chord a key press is, from the browser's key name and modifiers; null for a modifier alone or a press with Alt
         * (the finger key's). A letter is named in capitals whatever Shift and Caps Lock do; a symbol key is named by the
         * character it types, which already says Shift ("?" is "?", never "Shift+/").
         */
        fun of(key: DesktopKey): KeyChord? {
            if (key.key in MODIFIERS || key.key.isEmpty() || key.alt) return null
            val name = when {
                key.key == " " || key.key == "Spacebar" -> "Space"
                key.key == "Esc" -> "Escape"
                key.key.length == 1 && key.key[0].isLetter() -> key.key.uppercase()
                else -> key.key
            }
            val printed = name.length == 1 && !name[0].isLetter() && name[0] != ' '
            return KeyChord(name, shift = key.shift && !printed, ctrl = key.ctrl || key.meta)
        }
    }
}

/**
 * The places hotkeys act in (§6's "Where"), as the "?" sheet and Settings group them.
 */
sealed class HotkeyPlace(val title: String) {
    data object Table : HotkeyPlace("Table")
    data object Timeline : HotkeyPlace("Timeline mode")
    data object Pod : HotkeyPlace("Pod screens")
    data object Draft : HotkeyPlace("Draft pick, the card under the pointer")
    data object Editor : HotkeyPlace("Deck and cube editor")

    companion object {
        val all: List<HotkeyPlace> get() = listOf(Table, Timeline, Pod, Draft, Editor)
    }
}

/**
 * WEB-031: a rebindable hotkey: its [id] on the wire, what it does ([label]), where ([place]) and its default key ([default]).
 * Esc, Enter, 1–9, "?" and Alt are fixed ([FixedKey]).
 */
sealed class HotkeyAction(val id: String, val label: String, val place: HotkeyPlace, val default: KeyChord) {
    data object Main : HotkeyAction("main", "Main button", HotkeyPlace.Table, KeyChord("Space"))
    data object MainHold : HotkeyAction("main-hold", "Main button's hold (End turn; on Resolve the whole Stack)", HotkeyPlace.Table,
        KeyChord("Space", shift = true))
    data object Undo : HotkeyAction("undo", "Undo", HotkeyPlace.Table, KeyChord("Z", ctrl = true))
    data object Fit : HotkeyAction("fit", "Zoom to fit", HotkeyPlace.Table, KeyChord("F"))
    data object Stack : HotkeyAction("stack", "Stack list", HotkeyPlace.Table, KeyChord("S"))
    data object Graveyard : HotkeyAction("graveyard", "Your graveyard", HotkeyPlace.Table, KeyChord("G"))
    data object Exile : HotkeyAction("exile", "Your exile", HotkeyPlace.Table, KeyChord("X"))
    data object OpponentGraveyard : HotkeyAction("opponent-graveyard", "The other player's graveyard", HotkeyPlace.Table,
        KeyChord("G", shift = true))
    data object OpponentExile : HotkeyAction("opponent-exile", "The other player's exile", HotkeyPlace.Table, KeyChord("X", shift = true))
    data object Mode : HotkeyAction("mode", "Next response mode (Control, Normal, Quick)", HotkeyPlace.Table, KeyChord("M"))
    data object Chat : HotkeyAction("chat", "Write in chat", HotkeyPlace.Table, KeyChord("C"))
    data object Timeline : HotkeyAction("timeline", "Timeline mode on / off", HotkeyPlace.Timeline, KeyChord("T"))
    data object Previous : HotkeyAction("previous", "Previous event", HotkeyPlace.Timeline, KeyChord("ArrowLeft"))
    data object Next : HotkeyAction("next", "Next event", HotkeyPlace.Timeline, KeyChord("ArrowRight"))
    data object Auto : HotkeyAction("auto", "Auto on / off", HotkeyPlace.Timeline, KeyChord("P"))
    data object NextTab : HotkeyAction("next-tab", "Next tab (Draft, Build, Stats, Games)", HotkeyPlace.Pod, KeyChord("Tab"))
    data object PreviousTab : HotkeyAction("previous-tab", "Previous tab", HotkeyPlace.Pod, KeyChord("Tab", shift = true))
    data object Keep : HotkeyAction("keep", "Keep", HotkeyPlace.Draft, KeyChord("K"))
    data object Burn : HotkeyAction("burn", "Burn", HotkeyPlace.Draft, KeyChord("B"))
    data object Unmark : HotkeyAction("unmark", "Unmark", HotkeyPlace.Draft, KeyChord("U"))
    /** WEB-041: saves the deck or cube being edited, as its Save button does. */
    data object Save : HotkeyAction("save", "Save", HotkeyPlace.Editor, KeyChord("S", ctrl = true))

    companion object {
        /** Every rebindable hotkey, in the order the "?" sheet and Settings list them. */
        val all: List<HotkeyAction> get() = listOf(Main, MainHold, Undo, Fit, Stack, Graveyard, Exile, OpponentGraveyard,
            OpponentExile, Mode, Chat, Timeline, Previous, Next, Auto, NextTab, PreviousTab, Keep, Burn, Unmark, Save)
        fun of(id: String): HotkeyAction? = all.firstOrNull { it.id == id }
    }
}

/** §6: the keys that can't be rebound, as the "?" sheet lists them: [keys] as shown, what they do, where. */
data class FixedKey(val keys: String, val label: String, val place: HotkeyPlace) {
    companion object {
        val all: List<FixedKey> = listOf(
            FixedKey("Esc", "Close the top popup, Inspect, panel or dialog; drop an unsent cast", HotkeyPlace.Table),
            FixedKey("Enter", "The open panel's go-on on the main button (Cast, Done, OK, Pay)", HotkeyPlace.Table),
            FixedKey("1–9", "Choose the Nth mode, way or option of the open panel", HotkeyPlace.Table),
            FixedKey("Alt (held)", "Over a card: Inspect while held, a tap pins it; elsewhere pauses (Shift works too)", HotkeyPlace.Table),
            FixedKey("?", "This list of keys", HotkeyPlace.Table))
    }
}

/**
 * WEB-033 (decision 23): the player's hotkeys: each [HotkeyAction]'s key, its default unless [overrides] says otherwise (a
 * chord's [KeyChord.text], or "" for no key). One key per action and one action per key: binding a key another action has
 * leaves that action without one ([bind]). Kept on the account as [overrides] (AccountSettings.keyBindings).
 */
data class KeyBindings(val overrides: Map<String, String> = emptyMap()) {
    /** The key of [action], null when it has none. */
    fun keyOf(action: HotkeyAction): KeyChord? {
        val set = overrides[action.id] ?: return action.default.takeIf { default -> HotkeyAction.all.none { other ->
            other != action && overrides[other.id]?.let(KeyChord::parse) == default } }
        return KeyChord.parse(set)
    }

    /** The action [chord] does, null when none. */
    fun actionOf(chord: KeyChord): HotkeyAction? = HotkeyAction.all.firstOrNull { keyOf(it) == chord }

    /** The result of binding a key: the new bindings, and the action that lost the key to it ([took]), if any. */
    data class Bound(val bindings: KeyBindings, val took: HotkeyAction?)

    /**
     * [action] gets [chord] (null: no key). An action that had [chord] is left without a key; the bindings keep only what
     * differs from the defaults.
     */
    fun bind(action: HotkeyAction, chord: KeyChord?): Bound {
        val took = chord?.let(::actionOf)?.takeIf { it != action }
        val keys = HotkeyAction.all.associateWith { keyOf(it) }.toMutableMap()
        keys[action] = chord
        if (took != null) keys[took] = null
        return Bound(of(keys), took)
    }

    /** "Reset all": every hotkey on its default. */
    fun reset(): KeyBindings = KeyBindings()

    companion object {
        /** A key that can't be bound (§6: Esc, Enter, 1–9, "?" and the finger key are fixed); null when [chord] can. */
        fun refusal(chord: KeyChord): String? = when {
            chord.key == "Escape" || chord.key == "Enter" -> "${chord.label} can't be changed."
            !chord.ctrl && chord.key.length == 1 && chord.key[0] in '1'..'9' -> "1–9 choose options in panels."
            chord.key == "?" -> "? opens the list of keys."
            else -> null
        }

        /** The bindings giving each action its key in [keys], kept as what differs from the defaults. */
        fun of(keys: Map<HotkeyAction, KeyChord?>): KeyBindings = KeyBindings(HotkeyAction.all.mapNotNull { action ->
            val key = keys[action]
            if (key == action.default) null else action.id to (key?.text ?: "")
        }.toMap())
    }
}

/**
 * The account's hotkeys on this device while signed in on a desktop layout (WEB-033): read from the account settings the
 * device holds, then the server's; Settings changes them ([set]). Main thread.
 */
@Stable
class KeyBindingsState {
    var bindings by mutableStateOf(KeyBindings()); private set
    fun set(next: KeyBindings) {
        if (next == bindings) return
        bindings = next
        AppLog.info("keys", "Hotkeys: ${if (next.overrides.isEmpty()) "the defaults" else next.overrides.entries.joinToString { "${it.key}=${it.value.ifEmpty { "none" }}" }}")
    }
}

/** The account's hotkeys; the defaults where nothing provides them (tests, the phone layout). */
val LocalKeyBindings = staticCompositionLocalOf<KeyBindingsState?> { null }

/** The hotkeys in effect here. */
@Composable
fun currentKeyBindings(): KeyBindings = LocalKeyBindings.current?.bindings ?: KeyBindings()

/**
 * WEB-031: what a hotkey presses on a control: its tap and its hold, as a click and a right-click do ([ActionSquare] fills
 * them while the control is composed). The main button and Undo have one each on the desktop table.
 */
@Stable
class ControlKeys {
    private var owner: Any? = null
    var tap: (() -> Unit)? = null; private set
    var hold: (() -> Unit)? = null; private set
    fun set(key: Any, tap: () -> Unit, hold: () -> Unit) { owner = key; this.tap = tap; this.hold = hold }
    fun clear(key: Any) { if (owner === key) { owner = null; tap = null; hold = null } }
}

/** Fills [keys] with a control's [tap] and [hold] while composed (nothing when [keys] is null). */
@Composable
fun ControlKeysTarget(keys: ControlKeys?, tap: () -> Unit, hold: () -> Unit) {
    if (keys == null) return
    val key = remember { Any() }
    SideEffect { keys.set(key, tap, hold) }
    DisposableEffect(keys, key) { onDispose { keys.clear(key) } }
}

/**
 * WEB-032 (§6: 1–9 choose the Nth mode, way or option): the options of the open panel's choices, the newest shown first
 * ([choose]). Each choice section registers its options while composed ([NumberedOptions]).
 */
@Stable
class PanelOptions {
    private val entries = mutableListOf<Pair<Any, List<() -> Unit>>>()
    fun put(key: Any, options: List<() -> Unit>) {
        val at = entries.indexOfFirst { it.first === key }
        if (at >= 0) entries[at] = key to options else entries += key to options
    }
    fun remove(key: Any) { entries.removeAll { it.first === key } }
    /** Chooses the [n]th option (from 1) of the choice shown last; false when there is none. */
    fun choose(n: Int): Boolean {
        val options = entries.lastOrNull { it.second.isNotEmpty() }?.second ?: return false
        val option = options.getOrNull(n - 1) ?: return false
        option()
        return true
    }
}

/** The open panels' options on the desktop table; null elsewhere. */
val LocalPanelOptions = staticCompositionLocalOf<PanelOptions?> { null }

/** Registers [options] (in the order shown) for the 1–9 keys while composed (WEB-032). */
@Composable
fun NumberedOptions(options: List<() -> Unit>) {
    val panel = LocalPanelOptions.current ?: return
    val key = remember { Any() }
    SideEffect { panel.put(key, options) }
    DisposableEffect(panel, key) { onDispose { panel.remove(key) } }
}

/**
 * WEB-024 (§5): a card outside the table's scene the finger key inspects when the pointer is over it (the play shown, the
 * dock's Stack entries): the card id the table's Inspect opens. Set by the mouse entering and leaving it. Main thread.
 */
@Stable
class FingerTargets {
    var hovered: String? = null; private set
    fun enter(id: String) { hovered = id }
    fun leave(id: String) { if (hovered == id) hovered = null }
}

/** The desktop table's [FingerTargets]; null in the phone layout. */
val LocalFingerTargets = staticCompositionLocalOf<FingerTargets?> { null }

/** WEB-024: the finger key over this inspects [id] (the table's Inspect of that card or Stack entry). */
@Composable
fun androidx.compose.ui.Modifier.fingerTarget(id: String): androidx.compose.ui.Modifier {
    val targets = LocalFingerTargets.current ?: return this
    DisposableEffect(targets, id) { onDispose { targets.leave(id) } }
    return mouseHover(id) { over -> if (over) targets.enter(id) else targets.leave(id) }
}

/** WEB-031: the hotkey a page key is, in [bindings]: the chord ([KeyChord.of]) and its action, when the key goes down and no
 * text field has the keyboard (§6: hotkeys never act then). */
fun hotkeyOf(key: DesktopKey, bindings: KeyBindings): HotkeyAction? {
    if (!key.down || key.textField) return null
    return KeyChord.of(key)?.let(bindings::actionOf)
}

/** WEB-031, WEB-032: what the desktop table's hotkeys press: the main button, Undo, and the open panel's numbered options. */
@Stable
class TableHotkeys {
    val main = ControlKeys()
    val undo = ControlKeys()
    val options = PanelOptions()
}

/** The desktop table's [TableHotkeys]; null in the phone layout. */
val LocalTableHotkeys = staticCompositionLocalOf<TableHotkeys?> { null }
