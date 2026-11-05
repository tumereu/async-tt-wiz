package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.performClick
import org.asyncttwiz.android.CHOICE_SOURCE_TAG
import org.junit.Test
import kotlin.test.assertTrue

/**
 * Owner, 2026-11-05: Automatum's choice panels name their source by the card, not by text. Alice casts Storm Spark: its
 * target bar says only "Choose any target", the Spark sits at the bar's right as a small card, and holding it inspects
 * it; the cast then goes on as usual.
 */
class TargetBarSourceTest : E2e() {
    // STK-044, STK-047, TBL-104: the target bar's title is only the choice, the spell is the card at its right, and
    // holding that card shows Inspect with the Spark's picture until released
    @Test fun aTargetedSpellsBarShowsItsCardAtTheRight() {
        startGame(TestDeck.sparks(), deal = { a, _ -> "Mountain" in hand(a) && "Storm Spark" in hand(a) })
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain on the battlefield", along = listOf(bob)) { alice.field("Mountain").isNotEmpty() && alice.inOwnMain() }
        alice.playFromHand("Storm Spark", this)
        waitUntil("Alice's target bar") { alice.shows(hasText("Choose any target") and isHeading()) }
        val source = alice.node(hasTestTag(CHOICE_SOURCE_TAG) and hasContentDescription("Storm Spark"))
        val bar = alice.described("Choose targets").fetchSemanticsNode().boundsInRoot
        val card = source.fetchSemanticsNode().boundsInRoot
        val title = alice.node(hasText("Choose any target") and isHeading()).fetchSemanticsNode().boundsInRoot
        assertTrue(bar.right - card.right < 20f && card.left >= title.right, "the Spark at the bar's right: $card, bar $bar, title $title")
        holding(source) {
            val shown = alice.screen()
            for (part in listOf("Card inspection content", "Card image: Storm Spark")) assertTrue(part in shown, "$part: $shown")
        }
        assertTrue(!alice.shows(hasContentDescription("Card inspection content")), "closed on release")
        // The cast goes on: Bob's life square is the target
        alice.node(hasContentDescription("${bob.name}, life 20", substring = true)).performClick()
        waitUntil("the Spark resolving at Bob", along = listOf(bob)) { alice.state.player(bob.me).life == 17 }
    }
}
