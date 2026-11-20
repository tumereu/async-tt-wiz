package org.asyncttwiz.e2e

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import arcanum.api.RulesGlossary
import org.asyncttwiz.android.RULES_REMINDER_TAG
import org.junit.Test
import java.util.UUID
import kotlin.test.assertTrue

/**
 * Owner decision 2026-11-19 (playtest N8): rules reminders as chips in Inspect, in a game and in a draft, for the keywords a
 * card prints without explaining them. The Cinder Lark prints "Flying, haste" without reminder text (its bestow and collect
 * evidence have theirs).
 */
class RulesRemindersTest : E2e() {
    private val chip = hasTestTag(RULES_REMINDER_TAG)
    private val flying = RulesGlossary.keywords.getValue("flying").text

    // TBL-209, TBL-210: holding Alice's Lark on the battlefield shows its Flying and Haste chips under it, without their
    // explanations; pinned by a drag to the left, a tap on Flying explains it in the dark rule popup
    @Test fun inAGameTheLarksKeywordsAreRemindedUnderIt() {
        startGame(TestDeck.larks(), deal = { a, _ -> "Mountain" in hand(a) && "Cinder Lark" in hand(a) })
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain on the battlefield", along = listOf(bob)) { alice.field("Mountain").isNotEmpty() && alice.inOwnMain() }
        alice.playFromHand("Cinder Lark", this)
        waitUntil("the Lark on the battlefield", along = listOf(bob)) { alice.field("Cinder Lark").isNotEmpty() && alice.inOwnMain() }
        run(skip = java.time.Duration.ofMillis(500))
        val lark = alice.battlefieldCard("Cinder Lark")
        holding(lark) {
            assertTrue(alice.shows(chip and hasText("Flying")) && alice.shows(chip and hasText("Haste")), alice.screen())
            assertTrue(!alice.shows(chip and hasText("Bestow")) && !alice.shows(chip and hasText("Collect evidence")), "explained by its reminder text")
            assertTrue(!alice.showsText(flying), "no explanation while held")
        }
        holding(lark) { moveHeldFinger(lark, Offset(-200f, 0f)) }
        run(skip = java.time.Duration.ofMillis(400))
        assertTrue(alice.showsControl("Close inspection"), "pinned: ${alice.screen()}")
        alice.node(chip and hasText("Flying")).performClick()
        waitUntil("Flying explained") { alice.shows(hasContentDescription("Rule explanation")) && alice.showsText(flying) }
        assertTrue(!alice.refusalSeen, "no refusal")
    }

    // TBL-209, TBL-210, DRF-063: holding a Lark in Alice's pack shows Flying and Haste under it, each explained, since a release
    // closes this Inspect
    @Test fun inADraftThePackCardsKeywordsAreExplained() {
        launchPlayers()
        val name = "E2E reminders ${UUID.randomUUID().toString().take(6)}"
        val pod = ServerSetup.draftingPod(alice.account, bob.account, name, cards = listOf(TestCards.lark))
        waitUntil("Alice's menu") { alice.shows(hasTestTag("menu-PODS")) }
        alice.tagged("menu-PODS").performClick()
        waitUntil("the pod in Alice's list") { alice.showsText(name) }
        alice.text(name).performScrollTo().performClick()
        waitUntil("Alice's pack") { alice.pods.state.detail?.takeIf { it.id == pod }?.draft?.head != null }
        val copy = alice.pods.state.detail!!.draft!!.head!!.cards.first().id
        val card = hasTestTag("pod-pick-card-$copy")
        waitUntil("the Lark on Alice's pick screen") { alice.shows(card) }
        holding(alice.node(card)) {
            assertTrue(alice.shows(chip and hasText("Flying")) && alice.shows(chip and hasText("Haste")), alice.screen())
            assertTrue(alice.showsText(flying) && alice.showsText(RulesGlossary.keywords.getValue("haste").text), "each explained: ${alice.screen()}")
        }
        assertTrue(!alice.shows(hasTestTag("card-inspection")), "closed once the finger lifted")
    }
}
