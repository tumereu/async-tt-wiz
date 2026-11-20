package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performClick
import arcanum.engine.decision.Decision
import org.asyncttwiz.android.AppLog
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Problem report KZBKLR (player, prod 0.2.1, 2026-11-20): "I had a card which milled cards from my deck, revealed them and let
 * me pick one to my hand. I was unable to press ok when picking what card to pick." Her Frontier Seeker looked at the top five
 * cards of her library while her line rested on her assumption of the other player's passes (he was away); OK on the choice was
 * refused, picking a Plains or nothing: the rest go to the bottom of her library in a random order, and the information rule
 * took that order for a random result she would see (TRN-067), though a library's order is hidden, as a shuffle's is.
 *
 * Bob is away; Alice casts a Trailhead Seeker (the same wording), her phone assumes his passes, and she picks a Plains in the
 * choice's panel and presses OK.
 */
class RandomBottomOnAssumptionTest : E2e() {
    private val inPanel = hasAnyAncestor(hasContentDescription("Automatum choice"))

    // TRN-067, TRN-159, STK-030: on her tentative line OK takes her pick: the Plains goes to her hand, nothing refused, Bob still away
    @Test fun okTakesThePickWhileTheRestGoToTheBottomInARandomOrder() {
        startGame(TestDeck("Seekers", listOf(TestCards.plains to 20, TestCards.seeker to 20)), deal = { a, _ -> "Trailhead Seeker" in hand(a) })
        bothOpen()
        bothKeep(bobAway = true)
        waitUntil("Alice's Main 1") { alice.inOwnMain() && alice.settled(this) }
        val plains = alice.hand("Plains").size
        alice.playFromHand("Trailhead Seeker", this)
        waitUntil("the Seeker's choice on what Alice assumed: ${alice.screen()}") {
            if (alice.stepArrow() == "Resolve" || alice.stepArrow() == "Pass") alice.once { alice.tap(alice.stepArrow()!!) }
            (alice.state.pending?.decision as? Decision.ChooseObjects)?.player == alice.me && alice.shows(hasContentDescription("Plains") and inPanel)
        }
        assertTrue(alice.engine { device.tentative() }, "Alice's line rests on her assumption")
        alice.nodes(hasContentDescription("Plains") and inPanel)[0].performClick()
        waitUntil("the Plains picked") { alice.shows(hasContentDescription("Plains, chosen") and inPanel) }
        alice.text("OK").performClick()
        waitUntil("the Plains in Alice's hand, on the server") {
            alice.state.pending == null && alice.hand("Plains").size == plains + 1 && alice.field("Trailhead Seeker").isNotEmpty() &&
                alice.engine { device.session.pending.isEmpty() }
        }
        assertFalse(alice.refusalSeen, "no refusal: ${alice.shownTexts}")
        assertFalse("refused: revealsHidden" in AppLog.text(), "nothing held back")
        assertTrue(alice.engine { device.tentative() }, "still on her assumption: Bob is away")
    }
}
