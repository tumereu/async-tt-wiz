@file:OptIn(ExperimentalWasmJsInterop::class)

package org.asyncttwiz.android

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.double
import org.asyncttwiz.appui.playPcm
import org.asyncttwiz.appui.unlockSound
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

/** The "Your move" sound and the haptic ticks in the browser, over stand-ins for Web Audio and the vibration. */
class BrowserSoundAndHapticsTest {
    @AfterTest fun reals() { realAudio(); realVibration() }

    private fun audio(): JsonObject = Json.parseToJsonElement(fakeAudioJson()).jsonObject
    private fun count(name: String) = audio()[name]!!.jsonPrimitive.int
    private fun vibrations(): List<Int> = Json.parseToJsonElement(fakeVibrationsJson()).jsonArray.map { it.jsonPrimitive.int }

    // WEB-008: browsers start a page's sound only after a tap or key on it: the page's audio starts on the first one and keeps
    // running after it (also in a background tab); the sound then plays through it as the 16-bit samples given
    @Test fun theSoundStartsOnTheFirstTapAndPlaysTheSamples() {
        fakeAudio("suspended", available = true)
        unlockSound()
        assertEquals(0, count("made"), "no audio before the page has had a tap")
        pageInput(key = false)
        assertEquals(1, count("made"))
        assertEquals(1, count("resumed"), "started on the tap")
        pageInput(key = false)
        assertEquals(1, count("resumed"), "running: later taps leave it be")
        playPcm(shortArrayOf(0, 16384, -32768), 22050)
        val buffer = audio()["buffers"]!!.jsonArray.single().jsonObject
        assertEquals(1, buffer["channels"]!!.jsonPrimitive.int)
        assertEquals(22050, buffer["rate"]!!.jsonPrimitive.int)
        assertEquals(listOf(0.0, .5, -1.0), buffer["samples"]!!.jsonArray.map { it.jsonPrimitive.double })
        assertEquals(1, count("started"))
        assertEquals(1, count("made"), "one audio context for the page")
    }

    // WEB-008: a key press starts it too
    @Test fun aKeyPressStartsTheSound() {
        fakeAudio("suspended", available = true)
        unlockSound()
        pageInput(key = true)
        assertEquals(1, count("resumed"))
    }

    // WEB-008: a browser with no Web Audio: playing throws (MoveSounds logs it and goes on), a tap fails nothing
    @Test fun noWebAudioThrowsOnlyWhenPlayed() {
        fakeAudio("suspended", available = false)
        unlockSound()
        pageInput(key = false)
        assertFails { playPcm(shortArrayOf(1, 2), 22050) }
    }

    // WEB-012: the haptic ticks vibrate where the browser can, once the page has had a tap (a held quick action longer than the
    // move passing to you); before a tap, or where the browser has no vibration (iOS, desktops), there is none and nothing fails
    @Test fun ticksVibrateOnlyWhereTheBrowserCanAfterATap() {
        fakeVibration(vibrates = true, active = true)
        BrowserHaptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        BrowserHaptics.performHapticFeedback(HapticFeedbackType.LongPress)
        assertEquals(listOf(12, 30), vibrations())
        fakeVibration(vibrates = true, active = false)
        BrowserHaptics.performHapticFeedback(HapticFeedbackType.LongPress)
        assertEquals(emptyList(), vibrations(), "before the page has had a tap")
        fakeVibration(vibrates = false, active = true)
        BrowserHaptics.performHapticFeedback(HapticFeedbackType.LongPress)
        assertEquals(emptyList(), vibrations(), "a browser with no vibration")
    }
}
