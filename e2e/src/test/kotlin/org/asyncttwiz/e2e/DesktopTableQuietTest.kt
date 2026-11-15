package org.asyncttwiz.e2e

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performMouseInput
import org.asyncttwiz.android.TableProbes
import org.junit.After
import org.junit.Test
import org.robolectric.annotation.Config
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The desktop table moving without recomposing (owner, 2026-11-15: the table in the browser stutters, in Firefox and Chrome
 * alike): Alice, in the desktop layout at 1440 × 900, zooms and pans her table (the table's own zoom and pan steps, which the
 * wheel and the middle button make too); the cards scale and move (their places change) while the table recomposes nothing
 * for it. Before, every zoom or pan step rebuilt the whole table.
 */
@Config(qualifiers = "w1440dp-h900dp-land-mdpi")
class DesktopTableQuietTest : E2e() {
    @After fun noProbe() { TableProbes.composed = null }

    // WEB-029, TBL-010, TBL-001 (owner, 2026-11-15: "Web FPS is still stuttery"): each zoom step scales Alice's Ember Cub and each pan
    // step moves it, and her table's own scope recomposes for fewer than half of the steps
    @Test fun zoomingAndPanningMoveTheCardsWithoutRecomposingTheTable() {
        startGame(aliceDesktop = true, deal = { a, _ -> "Mountain" in hand(a) && "Ember Cub" in hand(a) })
        bothOpen()
        alice.control("Keep").performMouseInput { click() }
        waitUntil("Alice's Main 1", along = listOf(bob)) { alice.inOwnMain() }
        for (name in listOf("Mountain", "Ember Cub")) {
            waitUntil("Alice's game settled") { alice.settled(this) }
            alice.handCard(name).performMouseInput { click() }
            waitUntil("$name raised in Alice's hand") { alice.raisedInHand() == name && alice.settled(this) }
            alice.handCard(name).performMouseInput { click() }
            waitUntil("$name on Alice's battlefield", along = listOf(bob)) {
                alice.field(name).isNotEmpty() && alice.showsOnBattlefield(name) && alice.inOwnMain() && alice.settled(this)
            }
        }
        run()
        var composed = 0
        TableProbes.composed = { me -> if (me == alice.id) composed++ }
        fun cub() = alice.battlefieldCard("Ember Cub").fetchSemanticsNode().boundsInRoot
        /** The table's own step [label] ("Zoom in", "Pan left"), as its accessibility action takes it, then a frame. */
        fun step(label: String) {
            alice.described("Shared table").fetchSemanticsNode().config[SemanticsActions.CustomActions].first { it.label == label }.action()
            run()
        }

        // TBL-010, WEB-029: zoomed out step by step and back in (the Cub stays in view)
        val before = cub()
        step("Zoom out")
        val zoomed = cub()
        assertNotEquals(before.width, zoomed.width, "the zoom scaled the Cub: $before, then $zoomed")
        step("Zoom out"); step("Zoom in"); step("Zoom in")
        assertTrue(composed < ZOOMS / 2.0, "the table recomposed $composed times for $ZOOMS zoom steps")

        // TBL-011, WEB-029: panned step by step, to one side and back
        composed = 0
        val unpanned = cub()
        step("Pan left")
        val panned = cub()
        assertNotEquals(unpanned.left, panned.left, "the pan moved the Cub: $unpanned, then $panned")
        repeat(PANS - 1) { i -> step(if (i % 2 == 0) "Pan right" else "Pan left") }
        assertTrue(composed < PANS / 2.0, "the table recomposed $composed times for $PANS pan steps")
    }

    private companion object {
        const val ZOOMS = 4
        const val PANS = 8
    }
}
