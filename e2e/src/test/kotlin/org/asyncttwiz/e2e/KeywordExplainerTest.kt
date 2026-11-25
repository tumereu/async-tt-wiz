package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasContentDescription
import arcanum.api.RulesGlossary
import arcanum.api.TurnStep
import org.junit.Test
import kotlin.test.assertTrue

/**
 * Owner decision 2026-11-25 (was: owner gallery review 2026-11-06, rule explainers on the badges): Alice casts a Cinder Lark
 * (flying, haste) and stages it as an attacker; its copy in the combat band shows the "flying" badge, and holding the badge
 * inspects the Lark, as holding anywhere on the card does, with no rule explanation popping up.
 */
class KeywordExplainerTest : E2e() {
    // TBL-240, TBL-104, CMB-035, CMB-064: holding the flying badge of a staged attacker's copy inspects the card
    @Test fun holdingAKeywordBadgeInspectsTheCard() {
        startGame(TestDeck.larks(), deal = { a, _ -> "Mountain" in hand(a) && "Cinder Lark" in hand(a) })
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain on the battlefield", along = listOf(bob)) { alice.field("Mountain").isNotEmpty() && alice.inOwnMain() }
        alice.playFromHand("Cinder Lark", this)
        waitUntil("the Lark on the battlefield", along = listOf(bob)) { alice.field("Cinder Lark").isNotEmpty() && alice.inOwnMain() }
        alice.tap("To combat")
        waitUntil("Alice declaring attackers", along = listOf(bob)) {
            alice.state.turn.step == TurnStep.DeclareAttackers && alice.stepArrow() == "No attack"
        }
        alice.stageAttacker("Cinder Lark", this)
        waitUntil("the Lark staged with its flying badge") { alice.shows(hasContentDescription("flying")) }
        val flying = RulesGlossary.keywords.getValue("flying")
        holding(alice.node(hasContentDescription("flying"))) {
            assertTrue(alice.shows(hasContentDescription("Card inspection content")) &&
                alice.shows(hasContentDescription("Card image: Cinder Lark")), "the Lark inspected: ${alice.screen()}")
            assertTrue(!alice.shows(hasContentDescription("Rule explanation")) && !alice.showsText(flying.text), "no explanation popup")
        }
        assertTrue(!alice.shows(hasContentDescription("Card inspection content")), "closed on release")
        assertTrue(alice.stepArrow() == "Attack (1)", "the hold changed nothing of the staging")
        assertTrue(!alice.refusalSeen, "no refusal")
    }
}
