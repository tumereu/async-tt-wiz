package org.asyncttwiz.e2e

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.performClick
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Owner playtest, 2026-11-12 (Scheming Fence): Inspect missed what the card gained and what was chosen for it. It lists every
 * effect on a card from the engine's view of its effects, and a card an effect applies to carries a violet sparkle. Alice's
 * Ember Cairn ({T}: Add {R}. {5}: You gain 1 life.) is chosen by her Scheming Warden as it enters.
 */
class InspectEffectsTest : E2e() {
    private val changes = hasAnyAncestor(hasContentDescription("Card changes"))
    private val bar = hasContentDescription("Choose on the table")

    /** The change lines Inspect shows now, top to bottom. */
    private fun lines(): List<String> = alice.nodes(hasText("", substring = true) and changes).fetchSemanticsNodes()
        .sortedBy { it.positionInRoot.y }.flatMap { node ->
            if (SemanticsProperties.Text in node.config) node.config[SemanticsProperties.Text].map { it.text } else emptyList() }

    private fun marked(player: E2ePlayer, name: String) = player.shows(hasContentDescription("$name, owner ${alice.name}, ", substring = true) and
        hasContentDescription(", changed by effects", substring = true))

    // TBL-104, TBL-133, TBL-134, TBL-136 (owner 2026-11-12: Scheming Fence): holding the Warden lists what was chosen for it and the
    // abilities it has from the Cairn; holding the Cairn lists that its abilities can't be activated; both carry the effects
    // marker, on Bob's table too
    @Test fun inspectListsGainedAbilitiesAndWhatWasChosen() {
        startGame(TestDeck("Fence", listOf(TestCards.mountain to 14, TestCards.cairn to 13, TestCards.schemer to 13)),
            deal = { a, _ -> "Ember Cairn" in hand(a) && "Scheming Warden" in hand(a) })
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Ember Cairn", this)
        waitUntil("the Cairn on the battlefield", along = listOf(bob)) { alice.field("Ember Cairn").isNotEmpty() && alice.inOwnMain() }
        assertFalse(marked(alice, "Ember Cairn"), "no marker while nothing applies to it")
        alice.playFromHand("Scheming Warden", this)
        waitUntil("the Warden asking for a permanent: ${alice.screen()}", along = listOf(bob)) {
            if (alice.stepArrow() == "Resolve") alice.once { alice.tap("Resolve") }
            alice.shows(bar)
        }
        alice.battlefieldCard("Ember Cairn").performClick()
        waitUntil("the Warden on the battlefield: ${alice.screen()}", along = listOf(bob)) {
            if (alice.shows(bar) && alice.shows(hasTestTag("main-button") and isEnabled())) alice.once { alice.node(hasTestTag("main-button")).performClick() }
            alice.field("Scheming Warden").isNotEmpty() && alice.state.stack.isEmpty() && alice.inOwnMain()
        }
        run(skip = java.time.Duration.ofMillis(500))
        holding(alice.battlefieldCard("Scheming Warden")) {
            assertEquals(listOf("Chosen: Ember Cairn", "Has the activated abilities of Ember Cairn", "Entered this turn"), lines(), alice.screen())
        }
        holding(alice.battlefieldCard("Ember Cairn")) {
            assertTrue("Its abilities can't be activated" in lines(), "${lines()}")
        }
        // The effects marker on both, read aloud as "changed by effects"
        assertTrue(marked(alice, "Scheming Warden") && marked(alice, "Ember Cairn"), alice.screen())
        waitUntil("Bob's table marks them too", along = listOf(alice)) { marked(bob, "Scheming Warden") && marked(bob, "Ember Cairn") }
    }
}
