package org.asyncttwiz.android

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomatumQuickActionIconsTest {
    private val fallback = Icons.AutoMirrored.Outlined.DriveFileMove

    /** Automatum's quick actions (CMB-028, CMB-029, CMB-034) and toggles (TRN-059) each have their own icon, none the
     * generic fallback (UI review 2026-11-03 #8). */
    @Test fun everyAutomatumQuickActionHasItsOwnIcon() {
        val vectors = listOf("Pass", "No blocks", "Commit blocks", "Clear", "No attack", "Full control",
            "Skip if no choice", "Autotap mana", "Keep", "Mull N")
        vectors.forEach { assertNotEquals(it, fallback, menuIcon(it)) }
        assertEquals(vectors.size, vectors.map(::menuIcon).toSet().size)
        // The attack's own crossed swords, badged differently from Declare
        listOf("Rest → Bob", "Rest → Gate Warden", "Commit attack (2)").forEach {
            assertTrue(it, swordsIcon(it))
            assertNotEquals(it, fallback, menuIcon(it))
        }
        assertFalse(swordsIcon("Declare blockers"))
        assertTrue(swordsIcon("Declare + tap"))
    }
}
