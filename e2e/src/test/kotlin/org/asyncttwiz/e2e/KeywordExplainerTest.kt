package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasContentDescription
import arcanum.api.RulesGlossary
import arcanum.api.TurnStep
import org.junit.Test
import kotlin.test.assertTrue

/**
 * Owner gallery review, 2026-11-06 (rule explainers): Alice casts a Cinder Lark (flying, haste) and stages it as an
 * attacker; its copy in the combat band shows the "flying" badge, and holding the badge explains flying in plain words.
 */
class KeywordExplainerTest : E2e() {
    // TBL-132, CMB-035: holding the flying badge of a staged attacker's copy shows what flying means
    @Test fun holdingAKeywordBadgeExplainsIt() {
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
        assertTrue(!alice.showsText(flying.text), "no explanation before the hold")
        holding(alice.node(hasContentDescription("flying"))) {
            assertTrue(alice.shows(hasContentDescription("Rule explanation")) && alice.showsText(flying.text), alice.screen())
        }
        assertTrue(!alice.refusalSeen, "no refusal")
    }
}
