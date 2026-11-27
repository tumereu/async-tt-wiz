package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasContentDescription
import arcanum.api.TurnStep
import org.junit.Test
import kotlin.test.assertTrue

/**
 * Owner, 2026-11-26: while you declare attackers, every creature of the other player shows the violet combat keyword badges the
 * attackers' copies show, to gauge the blockers. Bob plays a Cinder Lark (flying) in his turn; in hers Alice plays one too and
 * goes to combat: Bob's Lark on his battlefield shows "flying" before she stages anything, and holding the badge inspects his Lark.
 */
class OpponentCombatBadgesTest : E2e() {
    // CMB-064, TBL-240, TBL-104: Bob's Lark shows its flying badge while Alice declares attackers; holding it inspects the Lark
    @Test fun theOtherPlayersCreaturesShowTheirCombatKeywordsWhileYouDeclareAttackers() {
        startGame(TestDeck.larks(), TestDeck.larks(), bobStarts = true,
            deal = { a, b -> "Mountain" in hand(a) && "Cinder Lark" in hand(a) && "Mountain" in hand(b) && "Cinder Lark" in hand(b) })
        bothOpen()
        // Both keep and Bob, who starts, goes on to his first Main 1 by himself: bothKeep's wait for Alice's Main 1 would play his
        // first turn along (a Mountain, no Lark) and Alice's then use her Mountain, which she may hold only one of (0.3.5 full run)
        alice.tap("Keep")
        bob.tap("Keep")
        waitUntil("Bob's first Main 1", along = listOf(alice)) { bob.inOwnMain() && bob.state.turn.number == 1 }
        bob.playFromHand("Mountain", this)
        waitUntil("Bob's Mountain on the battlefield", along = listOf(alice)) { bob.field("Mountain").isNotEmpty() && bob.inOwnMain() }
        bob.playFromHand("Cinder Lark", this)
        waitUntil("Bob's Lark on the battlefield", along = listOf(alice)) { bob.field("Cinder Lark").isNotEmpty() && bob.inOwnMain() }
        bob.hold("To combat")
        waitUntil("Alice's Main 1", along = listOf(bob)) { alice.inOwnMain() && alice.field("Cinder Lark", bob.me).size == 1 }
        assertTrue(!alice.shows(hasContentDescription("flying")), "no badge before the declaration: ${alice.screen()}")
        alice.playFromHand("Mountain", this)
        waitUntil("Alice's Mountain on the battlefield", along = listOf(bob)) { alice.field("Mountain").isNotEmpty() && alice.inOwnMain() }
        alice.playFromHand("Cinder Lark", this)
        waitUntil("Alice's Lark on the battlefield", along = listOf(bob)) { alice.field("Cinder Lark").isNotEmpty() && alice.inOwnMain() }
        assertTrue(!alice.shows(hasContentDescription("flying")), "no badge in her main phase: ${alice.screen()}")
        alice.tap("To combat")
        waitUntil("Alice declaring attackers", along = listOf(bob)) {
            alice.state.turn.step == TurnStep.DeclareAttackers && alice.stepArrow() == "No attack"
        }
        alice.combatWorkedOut(this)
        val field = alice.battlefieldBounds(bob)
        waitUntil("Bob's Lark showing its flying badge on his battlefield: ${alice.screen()}") {
            alice.nodes(hasContentDescription("flying")).fetchSemanticsNodes().singleOrNull()?.let { field.contains(it.boundsInRoot.center) } == true
        }
        holding(alice.node(hasContentDescription("flying"))) {
            assertTrue(alice.shows(hasContentDescription("Card inspection content")) &&
                alice.shows(hasContentDescription("Card image: Cinder Lark")), "Bob's Lark inspected: ${alice.screen()}")
        }
        assertTrue(!alice.shows(hasContentDescription("Card inspection content")), "closed on release")
        assertTrue(alice.stepArrow() == "No attack", "the hold staged nothing")
        assertTrue(!alice.refusalSeen, "no refusal")
    }
}
