package org.asyncttwiz.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.asyncttwiz.protocol.AccountSettings
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The desktop layout's keys (docs/specs/web-client.md §6, wireframes D6 and D10) at 1440 × 900: the hotkeys on the real table
 * screen, the "?" sheet over it, and rebinding in Settings.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w1440dp-h900dp-land-mdpi")
class DesktopHotkeysScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @After fun forget() = AutomatumBoards.reset()

    private var time = 1_000L

    /** The page's [name] key pressed and released (with [shift] held for both), as the browser hands them over. */
    private fun press(name: String, shift: Boolean = false) {
        time += 500
        compose.runOnIdle {
            DesktopKeyboard.dispatch(DesktopKey(name, true, time, shift = shift))
            DesktopKeyboard.dispatch(DesktopKey(name, false, time + 40, shift = shift))
        }
        compose.waitForIdle()
    }

    private fun drain(f: EngineLobbyFixture) {
        repeat(4) { compose.runOnIdle { f.worker.drain() }; compose.waitForIdle() }
    }

    private class FakeSettings(var server: AccountSettings = AccountSettings()) : AccountSettingsSource {
        val writes = mutableListOf<AccountSettings>()
        override suspend fun cached() = server
        override suspend fun refresh() = server
        override suspend fun set(settings: AccountSettings) { writes += settings; server = settings }
    }

    // WEB-031, WEB-034 (D6), WEB-024: on the real table screen in the desktop layout T turns timeline mode on and off; "?"
    // (Shift held, which lifts Shift's Inspect: a chord) shows the list of keys over the table, every key acting on nothing
    // meanwhile; "?" again closes it; a key in the chat's text field does nothing
    @Test fun theHotkeysActOnTheTableAndQuestionMarkListsThem() {
        val f = EngineLobbyFixture()
        try {
            f.connect(f.head(otherKept = true))
            f.keep()
            compose.setContent {
                MaterialTheme {
                    CompositionLocalProvider(LocalAppLayout provides AppLayout.Desktop) {
                        Box(Modifier.fillMaxSize()) {
                            TableScreen(f.lobby, f.me, {}, true)
                            HotkeySheetHost(true) {}
                        }
                    }
                }
            }
            drain(f)
            compose.onNodeWithTag("phase-track").assertExists()
            press("t")
            drain(f)
            compose.onNodeWithContentDescription("Return to current").assertExists()
            press("t")
            drain(f)
            compose.onNodeWithContentDescription("Return to current").assertDoesNotExist()
            // In the chat's text field T is typed, not a hotkey
            compose.runOnIdle { DesktopKeyboard.dispatch(DesktopKey("t", true, time + 1, textField = true)) }
            drain(f)
            compose.onNodeWithContentDescription("Return to current").assertDoesNotExist()
            time += 500
            compose.runOnIdle { DesktopKeyboard.dispatch(DesktopKey("Shift", true, time, shift = true)) }
            press("?", shift = true)
            compose.runOnIdle { DesktopKeyboard.dispatch(DesktopKey("Shift", false, time + 100)) }
            compose.waitForIdle()
            compose.onNodeWithTag(HOTKEY_SHEET_TAG).assertExists()
            compose.onNodeWithText("Keyboard shortcuts").assertExists()
            compose.onAllNodesWithText("Space").onFirst().assertExists()
            compose.onNodeWithText("Ctrl+Z").assertExists()
            captureShot("$SCREENSHOTS/desktop/hotkey-sheet.png",
                "Alice pressed ? on her desktop table: the list of keyboard shortcuts shows over the dimmed table, the table's, timeline mode's, the pod screens' and the draft pick's keys with her keys for them, the fixed keys and the mouse, with Change keys in Settings at the top.")
            press("t")
            compose.onNodeWithContentDescription("Return to current").assertDoesNotExist()
            press("?", shift = true)
            compose.onNodeWithTag(HOTKEY_SHEET_TAG).assertDoesNotExist()
            press("t")
            drain(f)
            compose.onNodeWithContentDescription("Return to current").assertExists()
        } finally { f.close() }
    }

    // WEB-035, WEB-033 (D10): Settings → Keyboard → Keyboard shortcuts in the desktop layout: a click on Timeline mode's key
    // waits for a key, C makes it Timeline mode's and leaves Write in chat without one, saying so on its row; Esc cancels a
    // wait, Enter can't be taken, Reset all restores the defaults; each change saved for the account and applied at once
    @Test fun rebindingAKeyInSettings() {
        val source = FakeSettings()
        val applied = KeyBindingsState()
        compose.setContent {
            ScreenTheme {
                CompositionLocalProvider(LocalAppLayout provides AppLayout.Desktop, LocalKeyBindings provides applied) {
                    SettingsContent(null, "keys", {}, null, source)
                }
            }
        }
        compose.onNodeWithTag("keyboard-shortcuts").performScrollTo()
        compose.onNodeWithTag("hotkeys-reset").assertIsNotEnabled()
        compose.onNodeWithTag(hotkeyRowTag(HotkeyAction.Timeline)).performScrollTo().performClick()
        compose.onNodeWithText("Press a key… Esc cancels").assertExists()
        press("c")
        compose.onNodeWithText("No key: Timeline mode on / off took it").assertExists()
        compose.runOnIdle {
            assertEquals(mapOf("timeline" to "C", "chat" to ""), source.writes.last().keyBindings)
            assertEquals(HotkeyAction.Timeline, applied.bindings.actionOf(KeyChord("C")))
        }
        compose.onNodeWithTag(hotkeyRowTag(HotkeyAction.Undo)).performClick()
        press("Enter")
        compose.onNodeWithText("Enter can't be changed.").assertExists()
        compose.onNodeWithTag(hotkeyRowTag(HotkeyAction.Chat)).performScrollTo()
        captureShot("$SCREENSHOTS/desktop/hotkey-settings.png",
            "In Settings on the desktop layout Alice gave Timeline mode the C key (Write in chat now has no key, its row says Timeline mode took it), then clicked Undo's key and pressed Enter: the row waits for another key and says Enter can't be changed.")
        press("Escape")
        compose.onNodeWithText("Press a key… Esc cancels").assertDoesNotExist()
        compose.runOnIdle { assertEquals(KeyChord("Z", ctrl = true), applied.bindings.keyOf(HotkeyAction.Undo)) }
        compose.onNodeWithTag("hotkeys-reset").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(emptyMap(), source.writes.last().keyBindings)
            assertEquals(KeyBindings(), applied.bindings)
            assertTrue(source.writes.size == 2, "two saves: ${source.writes}")
        }
    }
}
