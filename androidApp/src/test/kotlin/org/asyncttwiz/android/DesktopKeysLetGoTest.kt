package org.asyncttwiz.android

import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * WEB-025, WEB-087 (prod report 96PB2X, 2026-11-16: Alt's key-up never reached the app and the desktop table stayed paused): a key the
 * page said went down is let go when its key-up can't come any more, every listener hearing why.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DesktopKeysLetGoTest {
    @get:Rule val compose = createComposeRule()

    private val heard = mutableListOf<String>()
    private val focusLost = mutableListOf<String>()
    private var newestTakesAll = false
    private var time = 1_000L

    @Before fun listen() {
        compose.setContent {
            DesktopKeyboard.Listen(true) { key ->
                heard += "${key.key} ${if (key.down) "down" else "up"}${key.lost?.let { " ($it)" }.orEmpty()}"
                key.key == "Alt"
            }
            DesktopKeyboard.Listen(true) { newestTakesAll }
            DesktopKeyboard.OnFocusLost(true) { focusLost += it }
        }
        compose.waitForIdle()
    }

    @After fun forget() = compose.runOnIdle { DesktopKeyboard.focusLost("the test ended") }

    private fun key(name: String, down: Boolean, alt: Boolean = false, shift: Boolean = false, textField: Boolean = false) =
        compose.runOnIdle { DesktopKeyboard.dispatch(DesktopKey(name, down, ++time, alt = alt, shift = shift, textField = textField)) }

    // WEB-025, WEB-087: the window losing the focus (Alt+Tab) lets every key down go, and tells what else is held only while the
    // page has the input (the left button held on the table)
    @Test fun theWindowLosingTheFocusLetsEveryKeyGo() {
        key("Alt", true, alt = true)
        compose.runOnIdle { DesktopKeyboard.focusLost("the window lost the focus") }
        assertEquals(listOf("Alt down", "Alt up (the window lost the focus)"), heard)
        assertEquals(listOf("the window lost the focus"), focusLost)
        assertTrue(DesktopKeyboard.keysDown.isEmpty())
        key("Alt", false)
        assertEquals("Alt up", heard.last(), "the late key-up still reaches the listeners")
    }

    // WEB-025, WEB-087: a later key whose modifiers no longer hold Alt says its key-up was lost; one that still holds it doesn't
    @Test fun aKeyWithoutTheModifierLetsItGo() {
        key("Alt", true, alt = true)
        key("Shift", true, alt = true, shift = true)
        key("Shift", false, alt = true)
        assertEquals(listOf("Alt down", "Shift down", "Shift up"), heard, "Alt still held")
        key("Shift", true, shift = true)
        assertEquals("Alt up (Shift pressed without it: its key-up was lost)", heard[3])
        assertEquals("Shift down", heard[4])
    }

    // WEB-025, WEB-087: a pointer event without Alt held lets it go (a click ends a pause Alt left on)
    @Test fun aPointerEventWithoutTheModifierLetsItGo() {
        key("Alt", true, alt = true)
        compose.runOnIdle { DesktopKeyboard.modifiers(alt = true, shift = false, ctrl = false, meta = false, what = "a pointer move") }
        assertEquals(listOf("Alt down"), heard)
        compose.runOnIdle { DesktopKeyboard.modifiers(alt = false, shift = false, ctrl = false, meta = false, what = "a pointer press") }
        assertEquals(listOf("Alt down", "Alt up (a pointer press without it: its key-up was lost)"), heard)
    }

    // WEB-025, WEB-087, WEB-031, WEB-090: a text field taking the keyboard lets the finger keys held from before go; Shift pressed while typing
    // (a capital) was never the finger and stays
    @Test fun aTextFieldTakingTheKeyboardLetsTheFingerKeysGo() {
        key("Alt", true, alt = true)
        key("h", true, alt = true, textField = true)
        assertEquals(listOf("Alt down", "Alt up (a text field has the keyboard)"), heard)
        key("h", false, textField = true)
        heard.clear()
        key("Shift", true, shift = true, textField = true)
        key("A", true, shift = true, textField = true)
        key("A", false, shift = true, textField = true)
        key("Shift", false, textField = true)
        assertEquals(listOf("A up", "Shift up"), heard, "only the releases, no Shift let go while typing")
    }

    // WEB-031, WEB-025: a release reaches every listener, also when a newer one takes it
    @Test fun aReleaseReachesEveryListener() {
        newestTakesAll = true
        key("Alt", true, alt = true)
        key("Alt", false)
        assertEquals(listOf("Alt up"), heard, "the newest took the press; the release reached the older one too")
    }
}
