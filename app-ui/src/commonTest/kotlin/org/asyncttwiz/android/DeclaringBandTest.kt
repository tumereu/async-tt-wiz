package org.asyncttwiz.android

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** CMB-097 (owner 2026-11-21, E13): the waves of light crossing the orange combat band while the viewer declares. */
class DeclaringBandTest {
    private fun at(phase: Float) = DeclaringLook.waveX(phase, width = 400f, wave = 100f)

    // CMB-097: each wave enters beyond the band's left edge, moves only rightwards and leaves beyond its right edge
    @Test fun aWaveCrossesTheBandFromLeftToRight() {
        val start = 1f - DeclaringLook.WAVE_START + .0001f
        assertEquals(-50f, assertNotNull(at(start)), .1f, "entering from beyond the left edge")
        val end = (start + DeclaringLook.WAVE_CROSSING) % 1f
        assertEquals(450f, assertNotNull(at(end - .001f)), 1f, "leaving beyond the right edge")
        var last = Float.NEGATIVE_INFINITY
        for (step in 0..100) {
            val x = assertNotNull(at((start + (DeclaringLook.WAVE_CROSSING - .0002f) * step / 100f) % 1f))
            assertTrue(x >= last, "moving right only: $last → $x")
            last = x
        }
    }

    // CMB-097: the waves are periodic: between two the band rests without one, and a still frame (phase 0) shows one on the band
    @Test fun theBandRestsBetweenWavesAndAStillFrameShowsOne() {
        val start = 1f - DeclaringLook.WAVE_START + .0001f
        assertNull(at((start + DeclaringLook.WAVE_CROSSING + .05f) % 1f), "resting after the wave")
        val still = assertNotNull(at(0f))
        assertTrue(still in 0f..400f, "the still frame's wave on the band: $still")
    }
}
