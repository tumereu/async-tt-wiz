package org.asyncttwiz.e2e

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performClick
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Owner playtest 2026-11-05 evening (fix): every way to cast a hand card shows as a chip above it, greyed when it can't be
 * paid or done now. A bestow creature whose bestow needed collect evidence showed no Bestow chip at all while the
 * evidence couldn't be collected. Alice holds a Cinder Lark ({R}; bestow {R} and collect evidence 6) with an empty
 * graveyard: raised, it shows Cast and a greyed Bestow; the greyed chip does nothing and the Lark is cast the regular way.
 */
class WayChipsTest : E2e() {
    private val greyed = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "can't be paid now")
    private val chosen = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "chosen")

    // ZON-080: the Bestow chip shows, greyed, while its collect evidence can't be paid
    @Test fun aBestowNeedingEvidenceShowsItsChipGreyed() {
        startGame(TestDeck.larks(), deal = { a, _ -> "Mountain" in hand(a) && "Cinder Lark" in hand(a) })
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain on the battlefield", along = listOf(bob)) { alice.field("Mountain").isNotEmpty() && alice.inOwnMain() }
        alice.handCard("Cinder Lark").performClick()
        waitUntil("the Lark's chips") { alice.shows(hasContentDescription("Way Cast {R}")) }
        assertTrue(alice.shows(hasContentDescription("Way Cast {R}") and chosen), "Cast first and chosen: ${alice.screen()}")
        assertTrue(alice.shows(hasContentDescription("Way Bestow {R}") and greyed), "Bestow shown, greyed: ${alice.screen()}")
        // The greyed chip does nothing: Cast stays chosen
        alice.node(hasContentDescription("Way Bestow {R}")).performClick()
        run()
        assertTrue(alice.shows(hasContentDescription("Way Cast {R}") and chosen), "Cast still chosen")
        assertFalse(alice.refusalSeen)
        alice.handCard("Cinder Lark").performClick()
        waitUntil("the Lark on the battlefield", along = listOf(bob)) { alice.field("Cinder Lark").isNotEmpty() && alice.inOwnMain() }
    }
}
