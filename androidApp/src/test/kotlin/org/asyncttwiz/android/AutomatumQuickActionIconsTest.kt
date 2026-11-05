package org.asyncttwiz.android

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Gavel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomatumQuickActionIconsTest {
    private val fallback = Icons.AutoMirrored.Outlined.DriveFileMove

    /** Automatum's quick actions (CMB-048, CMB-034) and toggles (TRN-059) each have their own icon, none the generic
     * fallback (UI review 2026-11-03 #8). */
    @Test fun everyAutomatumQuickActionHasItsOwnIcon() {
        val vectors = listOf("Clear", "Full control", "Skip if no choice", "Autotap mana", "Keep", "Mull N")
        vectors.forEach { assertNotEquals(it, fallback, menuIcon(it)) }
        assertEquals(vectors.size, vectors.map(::menuIcon).toSet().size)
        // The attack's own crossed swords, badged differently from Declare
        listOf("Rest → Bob", "Rest → Gate Warden").forEach {
            assertTrue(it, swordsIcon(it))
            assertNotEquals(it, fallback, menuIcon(it))
        }
        assertFalse(swordsIcon("Declare blockers"))
        assertTrue(swordsIcon("Declare + tap"))
    }

    /** TRN-063 (UI review round 2 #10): the step arrow draws the combat quick actions' crossed swords for To combat and
     * Attack (N), never a gavel; Pass and Next have icons of their own. */
    @Test fun theStepArrowsIconsMatchWhatTheyDo() {
        assertTrue(AutomatumStep.ToCombat.swords)
        assertTrue(AutomatumStep.Attack(2).swords)
        assertFalse(AutomatumStep.Attack(0).swords)
        assertNotEquals(AutomatumStep.Pass.icon, AutomatumStep.Next.icon)
        val all = listOf(AutomatumStep.Pass, AutomatumStep.Next, AutomatumStep.ToCombat, AutomatumStep.EndTurn, AutomatumStep.Resolve,
            AutomatumStep.Attack(0), AutomatumStep.Attack(1), AutomatumStep.Block(0), AutomatumStep.Block(1))
        all.forEach { assertNotEquals(it.label, Icons.Outlined.Gavel, it.icon) }
    }
}
