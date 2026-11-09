package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import arcanum.api.ObjectMark
import org.asyncttwiz.protocol.game.PlaySettings
import org.junit.Test
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Owner, 2026-11-08 (card states batch): Alice casts Ember Beckoning with her Ash Pup on the battlefield, so the Ring tempts her.
 * The Pup shows as her Ring-bearer on its chip and in Inspect, and her "Ring 1/4" chip shows by her life square, on both
 * phones; holding the chip says what it means.
 */
class CardStatesTest : E2e() {
    private val ring = hasTestTag("state-chip") and hasContentDescription("Ring 1/4")
    private val explained = hasContentDescription("Rule explanation")
    private val line = "Ring-bearer: legendary, can't be blocked by creatures with greater power"

    // TRN-072, TRN-053 (owner gallery review 2026-11-09, gallery 157): on a screen too narrow for the old room right of the piles, a
    // player's chip stays in their row beside the life square, on both tables, and never goes over the hand row; a hold still explains it
    @Config(qualifiers = "w280dp-h892dp-port-mdpi")
    @Test fun onANarrowScreenAStateChipStaysInItsPlayersRow() {
        ringTemptsAlice()
        waitUntil("the Ring's chip on Alice's table") { alice.shows(ring) }
        waitUntil("Alice's chip on Bob's table", along = listOf(alice)) { bob.shows(ring) }
        for (player in listOf(alice, bob)) {
            val chip = player.node(ring).fetchSemanticsNode().boundsInRoot
            val square = player.node(hasContentDescription("${alice.name}, life ", substring = true)).fetchSemanticsNode().boundsInRoot
            assertTrue(chip.left >= square.right, "${player.name}: right of the life square: $chip, $square")
            assertTrue(chip.top >= square.top - 8f && chip.bottom <= square.bottom + 8f, "${player.name}: in the row, not over the hand: $chip, $square")
            assertTrue(chip.right <= 280f, "${player.name}: on the screen: $chip")
        }
        holding(alice.node(ring)) {
            assertTrue(alice.shows(explained), alice.screen())
        }
        assertTrue(!alice.refusalSeen && !bob.refusalSeen, "no refusal")
    }

    /** Alice's Ash Pup on the battlefield, then Ember Beckoning has the Ring tempt her. */
    private fun ringTemptsAlice() {
        startGame(TestDeck("Ring", listOf(TestCards.mountain to 14, TestCards.pup to 13, TestCards.beckoning to 13)),
            aliceSettings = PlaySettings(assume = org.asyncttwiz.protocol.game.AssumeResolves.Always),
            deal = { a, _ -> listOf("Ash Pup", "Ember Beckoning").all { it in hand(a) } })
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Ash Pup", this)
        waitUntil("the Pup on the battlefield", along = listOf(bob)) { alice.field("Ash Pup").isNotEmpty() && alice.inOwnMain() }
        val pup = alice.field("Ash Pup").single()
        alice.playFromHand("Ember Beckoning", this)
        waitUntil("the Ring's temptation", along = listOf(bob)) {
            if (alice.stepArrow() == "Resolve") alice.once { alice.tap("Resolve") }
            ObjectMark.RingBearer in alice.state.obj(pup)?.marks.orEmpty() && alice.state.stack.isEmpty() && alice.inOwnMain()
        }
        run(skip = java.time.Duration.ofMillis(500))
    }

    // TBL-133, TRN-072, TBL-132: the Ring's temptation on a card and on a player, the same on both tables
    @Test fun aStateShowsOnTheCardAndOnThePlayerOnBothTables() {
        ringTemptsAlice()
        // The card's chip (its words are in its label too), and its Inspect line
        waitUntil("the Ring-bearer's word on Alice's table") { alice.shows(hasContentDescription(", Ring-bearer", substring = true)) }
        holding(alice.battlefieldCard("Ash Pup")) {
            assertTrue(alice.showsText(line), alice.screen())
        }
        // The player's chip, and what it means while held
        waitUntil("the Ring's chip on Alice's table") { alice.shows(ring) }
        holding(alice.node(ring)) {
            assertTrue(alice.shows(explained), alice.screen())
            assertTrue(alice.showsText("The Ring"), "named by its popup")
        }
        assertTrue(!alice.shows(explained), "closed on release")
        // Bob sees the same of Alice's card and of Alice
        waitUntil("Alice's Ring-bearer and her chip on Bob's table", along = listOf(alice)) {
            bob.shows(hasContentDescription(", Ring-bearer", substring = true)) && bob.shows(ring)
        }
        holding(bob.battlefieldCard("Ash Pup", alice)) {
            assertTrue(bob.showsText(line), bob.screen())
        }
        assertEquals(1, bob.nodes(ring).fetchSemanticsNodes().size, "only Alice's chip")
        assertTrue(!alice.refusalSeen && !bob.refusalSeen, "no refusal")
    }
}
