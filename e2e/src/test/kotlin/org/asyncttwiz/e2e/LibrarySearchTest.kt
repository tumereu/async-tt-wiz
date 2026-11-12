package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.performClick
import arcanum.engine.decision.Decision
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Owner playtest 2026-11-05 evening (a fetch land): the library search didn't show the effect causing it, said nothing of
 * what was searched, lost its pick when hidden with the eye, and didn't show the library's other cards. Alice plays a
 * Cinder Trail and activates it ({T}, pay 1 life, sacrifice it: search for a basic land card).
 */
class LibrarySearchTest : E2e() {
    private val inPanel = hasAnyAncestor(hasContentDescription("Automatum choice"))

    // STK-056, STK-044: the search panel: the Trail's ability at its right, "Search your library for a basic land card",
    // the Mountains to pick, the other cards under "Other cards"; the pick survives hiding the panel; OK finds the Mountain
    @Test fun aFetchLandsSearchShowsItsSourceAndKeepsItsPick() {
        startGame(TestDeck.trails(), deal = { a, _ -> "Cinder Trail" in hand(a) })
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Cinder Trail", this)
        waitUntil("the Trail on the battlefield", along = listOf(bob)) { alice.field("Cinder Trail").isNotEmpty() && alice.inOwnMain() }
        val mountains = alice.field("Mountain").size
        alice.battlefieldCard("Cinder Trail").performClick()
        waitUntil("Alice's search", along = listOf(bob)) {
            // Alice passes her own ability on (STK-026) as the main button says
            if (alice.stepArrow() == "Resolve" || alice.stepArrow() == "Pass") alice.once { alice.tap(alice.stepArrow()!!) }
            alice.shows(hasText("Search your library for a basic land card") and isHeading())
        }
        assertTrue(alice.shows(hasTestTag("choice-source") and hasContentDescription("Cinder Trail")), "the Trail at the right: ${alice.screen()}")
        assertTrue(alice.shows(hasText("Other cards") and isHeading()), "Other cards: ${alice.screen()}")
        assertTrue(alice.shows(hasContentDescription("Storm Spark") and inPanel), "a Storm Spark among the other cards")
        // A Storm Spark can't be picked; a Mountain can, and stays picked while the panel is hidden and shown again
        alice.nodes(hasContentDescription("Storm Spark") and inPanel)[0].performClick()
        alice.nodes(hasContentDescription("Mountain") and inPanel)[0].performClick()
        waitUntil("the Mountain picked") { alice.shows(hasContentDescription("Mountain, chosen") and inPanel) }
        assertFalse(alice.shows(hasContentDescription("Storm Spark, chosen")), "the Spark isn't picked")
        alice.tap("Hide Automatum panel")
        waitUntil("the panel hidden") { !alice.shows(hasContentDescription("Mountain, chosen") and inPanel) && alice.showsControl("Show Automatum panel") }
        alice.tap("Show Automatum panel")
        waitUntil("the pick still there") { alice.shows(hasContentDescription("Mountain, chosen") and inPanel) }
        alice.text("OK").performClick()
        waitUntil("the Mountain found, on the server", along = listOf(bob)) {
            alice.field("Mountain").size == mountains + 1 && alice.field("Cinder Trail").isEmpty() &&
                alice.engine { device.session.pending.isEmpty() }
        }
        assertFalse(alice.refusalSeen, "no refusal: ${alice.shownTexts}")
    }

    // STK-056 (owner, 2026-11-12: Recruiter of the Guard showed no other cards): a trigger's "you may search your library for
    // …" is a search too: titled by what is searched, the library's other cards under "Other cards", dimmed and unselectable
    @Test fun aSearchInsideATriggersSentenceShowsTheOtherCards() {
        startGame(TestDeck("Recruiters", listOf(TestCards.mountain to 14, TestCards.recruiter to 13, TestCards.spark to 13)),
            deal = { a, _ -> "Ember Recruiter" in hand(a) })
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Ember Recruiter", this)
        waitUntil("the Recruiter's trigger asking", along = listOf(bob)) {
            // Alice passes her own spell and trigger on (STK-026) as the main button says
            if (alice.stepArrow() == "Resolve" || alice.stepArrow() == "Pass") alice.once { alice.tap(alice.stepArrow()!!) }
            alice.state.pending?.decision is Decision.May && alice.showsText("Yes")
        }
        alice.text("Yes").performClick()
        waitUntil("Alice's search: ${alice.screen()}") {
            alice.state.pending?.decision is Decision.ChooseObjects && alice.shows(hasContentDescription("Ember Recruiter") and inPanel)
        }
        assertTrue(alice.shows(hasText("Search your library for a creature card with toughness 2 or less") and isHeading()),
            "titled by what is searched: ${alice.screen()}")
        assertTrue(alice.shows(hasText("Other cards") and isHeading()), "Other cards: ${alice.screen()}")
        assertTrue(alice.shows(hasContentDescription("Storm Spark") and inPanel), "a Storm Spark among the other cards: ${alice.screen()}")
        assertTrue(alice.shows(hasContentDescription("Mountain") and inPanel), "a Mountain among the other cards: ${alice.screen()}")
        alice.nodes(hasContentDescription("Storm Spark") and inPanel)[0].performClick()
        // A Recruiter to find, not the trigger's own source at the panel's right
        alice.nodes(hasContentDescription("Ember Recruiter") and inPanel and !hasTestTag("choice-source"))[0].performClick()
        waitUntil("the Recruiter picked") { alice.shows(hasContentDescription("Ember Recruiter, chosen") and inPanel) }
        assertFalse(alice.shows(hasContentDescription("Storm Spark, chosen")), "the Spark isn't picked")
        val hand = alice.state.player(alice.me).hand.size
        alice.text("OK").performClick()
        waitUntil("the Recruiter found, on the server", along = listOf(bob)) {
            alice.state.player(alice.me).hand.size == hand + 1 && alice.engine { device.session.pending.isEmpty() }
        }
        assertFalse(alice.refusalSeen, "no refusal: ${alice.shownTexts}")
    }
}
