package org.asyncttwiz.e2e

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import org.junit.Test
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

/**
 * Owner-reported 2026-11-26: "the eye has begun moving again; its position should be fixed". A target bar's eye sat in the middle of
 * the bar's height, which its title, sentence and card made taller than a panel's bottom row, and dropped when the bar was hidden.
 * Alice casts Ember Cascade, whose long text makes its bar tall: the eye of its target bar is at the bar's bottom left, where every panel's eye is (24 dp from the left,
 * 26 dp above the panel's bottom), and stays there while the bar is hidden and shown again, in portrait and in landscape.
 */
class EyeStaysPutTest : E2e() {
    private fun eye(hidden: Boolean): Offset = alice.control(if (hidden) "Show bar" else "Hide bar").fetchSemanticsNode().boundsInRoot.center

    private fun theTargetBarsEyeStaysPut() {
        startGame(TestDeck.thirds(TestCards.cascade), deal = { a, _ -> "Mountain" in hand(a) && "Ember Cascade" in hand(a) })
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain on the battlefield", along = listOf(bob)) { alice.field("Mountain").isNotEmpty() && alice.inOwnMain() }
        alice.playFromHand("Ember Cascade", this)
        waitUntil("Alice's target bar") { alice.shows(hasText("Choose any target") and isHeading()) }
        val bar = alice.described("Choose targets").fetchSemanticsNode().boundsInRoot
        val spot = Offset(bar.left + 24f, bar.bottom - 26f)
        val shown = eye(hidden = false)
        assertTrue((shown - spot).getDistance() < 1f, "the eye at the bar's bottom left ($spot), as on every panel: $shown, bar $bar")
        alice.tap("Hide bar")
        waitUntil("the bar hidden") { alice.showsControl("Show bar") && !alice.shows(hasText("Choose any target") and isHeading()) }
        val hidden = eye(hidden = true)
        assertTrue((hidden - spot).getDistance() < 1f, "hidden, only the eye stays, at the same spot ($spot): $hidden")
        alice.tap("Show bar")
        waitUntil("the bar back") { alice.shows(hasText("Choose any target") and isHeading()) }
        val again = eye(hidden = false)
        assertTrue((again - spot).getDistance() < 1f, "shown again, the eye still there ($spot): $again")
    }

    // STK-176, STK-044, STK-047
    @Test fun aTargetBarsEyeStaysPut() = theTargetBarsEyeStaysPut()

    // STK-176, STK-044: in landscape the bar stands on the bottom bar, its eye at the same spot of it
    @Config(qualifiers = "w892dp-h412dp-land-mdpi")
    @Test fun inLandscapeATargetBarsEyeStaysPut() = theTargetBarsEyeStaysPut()
}
