package org.asyncttwiz.android

import org.asyncttwiz.android.DesktopFingerKey.Effect
import org.asyncttwiz.protocol.AccountJson
import org.asyncttwiz.protocol.AccountSettings
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The desktop layout's hotkeys (docs/specs/web-client.md §6): the keys' names, the bindings, conflicts, keeping them. */
class DesktopHotkeysTest {
    private fun key(name: String, shift: Boolean = false, ctrl: Boolean = false, meta: Boolean = false, alt: Boolean = false,
        typing: Boolean = false, down: Boolean = true) = DesktopKey(name, down, 0, shift = shift, ctrl = ctrl, meta = meta, alt = alt,
        textField = typing)

    // WEB-031: a page key's chord: Space by name, letters in capitals whatever Shift does, a symbol key by the character it
    // types (no Shift), ⌘ as Ctrl; a modifier alone or a press with Alt (the finger key) is no hotkey
    @Test fun aPageKeyIsNamedAsTheHotkeysNameIt() {
        assertEquals(KeyChord("Space"), KeyChord.of(key(" ")))
        assertEquals(KeyChord("Space", shift = true), KeyChord.of(key(" ", shift = true)))
        assertEquals(KeyChord("G", shift = true), KeyChord.of(key("G", shift = true)))
        assertEquals(KeyChord("Z", ctrl = true), KeyChord.of(key("z", ctrl = true)))
        assertEquals(KeyChord("Z", ctrl = true), KeyChord.of(key("z", meta = true)), "⌘Z on a Mac")
        assertEquals(KeyChord("?"), KeyChord.of(key("?", shift = true)))
        assertEquals(KeyChord("Tab", shift = true), KeyChord.of(key("Tab", shift = true)))
        assertNull(KeyChord.of(key("Shift", shift = true)))
        assertNull(KeyChord.of(key("Alt", alt = true)))
        assertNull(KeyChord.of(key("t", alt = true)))
        for (chord in listOf(KeyChord("Space", shift = true), KeyChord("Z", ctrl = true), KeyChord("ArrowLeft"), KeyChord("?")))
            assertEquals(chord, KeyChord.parse(chord.text), "the wire form reads back")
        assertEquals("Shift+Space", KeyChord("Space", shift = true).label)
        assertEquals("←", KeyChord("ArrowLeft").label)
    }

    // WEB-031 (§6): the default keys: Space the main button, Shift+Space its hold, Ctrl+Z Undo, T timeline mode, …
    @Test fun theDefaultKeysAreTheSpecs() {
        val keys = KeyBindings()
        assertEquals(HotkeyAction.Main, hotkeyOf(key(" "), keys))
        assertEquals(HotkeyAction.MainHold, hotkeyOf(key(" ", shift = true), keys))
        assertEquals(HotkeyAction.Undo, hotkeyOf(key("z", ctrl = true), keys))
        assertEquals(HotkeyAction.Timeline, hotkeyOf(key("t"), keys))
        assertEquals(HotkeyAction.OpponentGraveyard, hotkeyOf(key("G", shift = true), keys))
        assertEquals(HotkeyAction.PreviousTab, hotkeyOf(key("Tab", shift = true), keys))
        assertEquals(HotkeyAction.Keep, hotkeyOf(key("k"), keys))
        assertNull(hotkeyOf(key("q"), keys))
        val ids = HotkeyAction.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "unique ids")
        val defaults = HotkeyAction.all.map { it.default }
        assertEquals(defaults.size, defaults.toSet().size, "no two hotkeys share a default")
    }

    // WEB-031 (§6): keys never act while a text field has the keyboard, nor on their release
    @Test fun keysNeverActWhileTyping() {
        assertNull(hotkeyOf(key(" ", typing = true), KeyBindings()))
        assertNull(hotkeyOf(key("c", typing = true), KeyBindings()))
        assertNull(hotkeyOf(key(" ", down = false), KeyBindings()))
    }

    // WEB-035: taking a key another hotkey has leaves that one without a key; the bindings keep only what differs from the
    // defaults; "Reset all" restores them
    @Test fun takingAUsedKeyLeavesTheOtherWithout() {
        val bound = KeyBindings().bind(HotkeyAction.Timeline, KeyChord("C"))
        assertEquals(HotkeyAction.Chat, bound.took)
        val keys = bound.bindings
        assertEquals(KeyChord("C"), keys.keyOf(HotkeyAction.Timeline))
        assertNull(keys.keyOf(HotkeyAction.Chat))
        assertEquals(HotkeyAction.Timeline, keys.actionOf(KeyChord("C")))
        assertNull(keys.actionOf(KeyChord("T")), "T is free now")
        assertEquals(mapOf("timeline" to "C", "chat" to ""), keys.overrides)
        // Chat takes T: nothing loses a key (T was free)
        val again = keys.bind(HotkeyAction.Chat, KeyChord("T"))
        assertNull(again.took)
        assertEquals(KeyChord("T"), again.bindings.keyOf(HotkeyAction.Chat))
        // Back on its default: that entry goes
        val back = again.bindings.bind(HotkeyAction.Timeline, KeyChord("T"))
        assertEquals(HotkeyAction.Chat, back.took)
        assertEquals(mapOf("chat" to ""), back.bindings.overrides)
        assertEquals(KeyBindings(), back.bindings.reset())
        // Rebinding a hotkey to its own key changes nothing
        assertEquals(KeyBindings(), KeyBindings().bind(HotkeyAction.Main, KeyChord("Space")).bindings)
    }

    // WEB-035 (§6): Esc, Enter, 1–9 and ? can't be taken; Ctrl with a digit can
    @Test fun theFixedKeysCantBeTaken() {
        for (chord in listOf(KeyChord("Escape"), KeyChord("Enter"), KeyChord("1"), KeyChord("9"), KeyChord("?")))
            assertTrue(KeyBindings.refusal(chord) != null, chord.text)
        assertNull(KeyBindings.refusal(KeyChord("1", ctrl = true)))
        assertNull(KeyBindings.refusal(KeyChord("0")))
        assertNull(KeyBindings.refusal(KeyChord("Q")))
    }

    // WEB-033: the bindings go to the account settings and back unchanged (the wire form), and a device's copy of an account
    // without any reads as the defaults
    @Test fun theBindingsRoundTripThroughTheAccountSettings() {
        val keys = KeyBindings().bind(HotkeyAction.MainHold, KeyChord("Space", shift = true, ctrl = true)).bindings
            .bind(HotkeyAction.Fit, null).bindings
        val settings = AccountSettings(keyBindings = keys.overrides)
        val wire = AccountJson.format.encodeToString(AccountSettings.serializer(), settings)
        val read = AccountJson.format.decodeFromString(AccountSettings.serializer(), wire)
        assertEquals(keys, KeyBindings(read.keyBindings))
        assertEquals(KeyChord("Space", shift = true, ctrl = true), KeyBindings(read.keyBindings).keyOf(HotkeyAction.MainHold))
        assertNull(KeyBindings(read.keyBindings).keyOf(HotkeyAction.Fit))
        assertTrue(read.keyBindingsValid)
        assertFalse("keyBindings" in AccountJson.format.encodeToString(AccountSettings.serializer(), AccountSettings()), "defaults omitted")
        assertEquals(KeyBindings(), KeyBindings(AccountJson.format.decodeFromString(AccountSettings.serializer(), "{}").keyBindings))
    }

    // WEB-032: 1–9 choose from the choice shown last that has options; one gone gives the keys back to the one before
    @Test fun numberedOptionsChooseFromTheNewestChoice() {
        val panel = PanelOptions()
        val chosen = mutableListOf<String>()
        val ways = Any(); val modes = Any()
        panel.put(ways, listOf({ chosen += "way 1" }, { chosen += "way 2" }))
        panel.put(modes, listOf({ chosen += "mode 1" }, { chosen += "mode 2" }, { chosen += "mode 3" }))
        assertTrue(panel.choose(3))
        assertFalse(panel.choose(4), "no fourth mode")
        panel.put(ways, listOf({ chosen += "way 1" }, { chosen += "way 2!" }))
        assertTrue(panel.choose(1), "the modes still answer: updating the ways keeps their place")
        panel.remove(modes)
        assertTrue(panel.choose(2))
        assertEquals(listOf("mode 3", "mode 1", "way 2!"), chosen)
    }

    // WEB-024, WEB-031: Shift held is the finger, but with another key it is a chord (Shift+Space): the Inspect it opened closes,
    // the view goes on, its release does nothing more; Alt with another key stays the finger
    @Test fun shiftWithAnotherKeyIsAChordNotTheFinger() {
        val key = DesktopFingerKey()
        assertEquals(listOf(Effect.Pause(true), Effect.Inspect("c1")), key.press("Shift", 0, "c1", inspectOpen = false))
        assertEquals(listOf(Effect.Close, Effect.Pause(false)), key.chord())
        assertEquals(emptyList(), key.chord(), "once")
        assertEquals(emptyList(), key.release("Shift", 50), "a quick release pins nothing")
        assertFalse(key.pinned)
        key.press("Shift", 1_000, null, inspectOpen = false)
        assertEquals(listOf(Effect.Pause(false)), key.chord(), "over nothing: only the pause ends")
        key.release("Shift", 1_200)
        key.press("Alt", 2_000, "c1", inspectOpen = false)
        assertEquals(emptyList(), key.chord(), "Alt stays the finger")
        assertEquals(listOf(Effect.Pause(false), Effect.Close), key.release("Alt", 2_050), "but its release is no tap")
    }
}
