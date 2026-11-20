package org.asyncttwiz.e2e

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Owner playtest 2026-11-20: "Face-down cards don't show their public modifiers." A face-down permanent hides only its face:
 * what is public about it (its counters, its P/T as changed, the effects on it) shows to both players, on the table and when
 * it is held (TBL-231).
 */
class FaceDownPublicTest : E2e() {
    private val changes = hasAnyAncestor(hasContentDescription("Card changes"))

    /** The change lines [player]'s Inspect shows now, top to bottom. */
    private fun lines(player: E2ePlayer): List<String> = player.nodes(hasText("", substring = true) and changes).fetchSemanticsNodes()
        .sortedBy { it.positionInRoot.y }.flatMap { node ->
            if (SemanticsProperties.Text in node.config) node.config[SemanticsProperties.Text].map { it.text } else emptyList() }

    /** Alice plays on to her Main 1 with an empty Stack, tapping Resolve where her phone stops. */
    private fun aliceResolves(what: String, done: () -> Boolean) = waitUntil(what, along = listOf(bob)) {
        if (alice.stepArrow() == "Resolve") alice.once { alice.tap("Resolve") }
        done() && alice.state.stack.isEmpty() && alice.inOwnMain()
    }

    // TBL-231, TBL-029, TBL-021 (owner playtest 2026-11-20: face-down cards don't show their public modifiers): Alice manifests the top card of
    // her library and puts a +1/+1 counter on it: Bob's table shows her face-down creature as 3/3 with its counter, and
    // holding it Bob reads "+1/+1 counter" under its back, never what the card is
    @Test fun aFaceDownCreaturesCounterShowsToBoth() {
        startGame(TestDeck("Summons", listOf(TestCards.mountain to 20, TestCards.summons to 10, TestCards.boon to 10)),
            deal = { a, _ -> "Ember Summons" in hand(a) && "Ember Boon" in hand(a) })
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Ember Summons", this)
        aliceResolves("a face-down creature on Alice's battlefield") { alice.state.battlefield.any { alice.state.obj(it)?.faceDown == true } }
        val manifested = alice.state.battlefield.first { alice.state.obj(it)?.faceDown == true }
        val name = cardName(alice.state, manifested)!!
        alice.playFromHand("Ember Boon", this)
        waitUntil("Alice's target bar or the Boon cast: ${alice.screen()}", along = listOf(bob)) {
            alice.shows(hasContentDescription("Choose targets")) || alice.state.stack.isNotEmpty()
        }
        if (alice.shows(hasContentDescription("Choose targets"))) alice.battlefieldCard("Covered card").performClick()
        aliceResolves("the +1/+1 counter on her face-down creature") { alice.state.obj(manifested)?.counters.orEmpty().isNotEmpty() }
        waitUntil("Bob's table shows it 3/3 with its counter: ${bob.screen()}", along = listOf(alice)) {
            bob.shows(hasContentDescription("Covered card, owner ${alice.name}, ", substring = true) and
                hasContentDescription("+1/+1 counter", substring = true))
        }
        assertTrue(bob.battlefieldCard("Covered card", owner = alice).fetchSemanticsNode().config
            .getOrNull(SemanticsProperties.ContentDescription).orEmpty().none { name in it }, "the card isn't named to Bob")
        holding(bob.battlefieldCard("Covered card", owner = alice)) {
            val shown = lines(bob)
            assertTrue(shown.any { "+1/+1 counter" in it }, "Bob's Inspect lists its counter: $shown ${bob.screen()}")
            assertTrue(shown.none { name in it }, "nothing names it to Bob: $shown")
        }
        holding(alice.battlefieldCard("Covered card")) {
            assertTrue(lines(alice).any { "+1/+1 counter" in it }, "Alice's Inspect lists it too: ${lines(alice)}")
        }
        assertFalse(alice.refusalSeen, "no refusal: ${alice.shownTexts}")
    }
}
