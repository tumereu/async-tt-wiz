package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.performClick
import arcanum.engine.decision.Decision
import arcanum.engine.state.TargetRef
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Owner playtest, 2026-11-07 (D1): picking a creature to sacrifice in a panel was hard: identical cards, with nothing of
 * their auras, counters or tapped state. Every cost or choice whose candidates are all battlefield permanents is now picked
 * on the table as targets are (STK-047): the slim bar with the question, the candidates glowing, Done; a sacrifice cost of
 * the caster's and an edict the other player answers alike.
 */
class TablePickTest : E2e() {
    private val bar = hasContentDescription("Choose on the table")

    /** Alice casts her free [name] (a creature or artifact) and waits until it is on her battlefield. */
    private fun castFree(name: String, count: Int) {
        alice.playFromHand(name, this)
        waitUntil("$name $count on Alice's battlefield", along = listOf(bob)) { alice.field(name).size == count && alice.inOwnMain() }
    }

    // D1, STK-047, STK-023, CR 602.2b: Alice's Ember Altar ("{T}, Sacrifice another creature: …") with two identical Ash Pups
    // on her battlefield, one wearing an Ember Mantle: tapping the Altar asks the sacrifice on the table (no panel), both
    // Pups glowing; a tap on the enchanted one sacrifices it (its Mantle goes too) and the ability resolves
    @Test fun aSacrificeCostIsPickedOnTheTable() {
        startGame(TestDeck("Altars", listOf(TestCards.mountain to 10, TestCards.altar to 10, TestCards.pup to 10, TestCards.mantle to 10)),
            TestDeck.glimmers(),
            deal = { a, _ -> hand(a).let { h -> "Ember Altar" in h && h.count { it == "Ash Pup" } >= 2 && "Ember Mantle" in h } })
        bothOpen()
        bothKeep(bobAway = false)
        castFree("Ember Altar", 1)
        castFree("Ash Pup", 1)
        // The Mantle on the one Pup there is
        alice.playFromHand("Ember Mantle", this)
        waitUntil("the Mantle's target bar or the Mantle on the Pup", along = listOf(bob)) {
            alice.shows(hasContentDescription("Choose targets")) || alice.field("Ember Mantle").isNotEmpty()
        }
        if (alice.shows(hasContentDescription("Choose targets"))) alice.battlefieldCard("Ash Pup").performClick()
        waitUntil("the Mantle on Alice's Pup", along = listOf(bob)) { alice.field("Ember Mantle").size == 1 && alice.inOwnMain() }
        castFree("Ash Pup", 2)
        val enchanted = (alice.state.obj(alice.field("Ember Mantle").single())?.attachedTo as TargetRef.Object).id
        val plain = alice.field("Ash Pup").single { it != enchanted }

        alice.battlefieldCard("Ember Altar").performClick()
        waitUntil("Alice's sacrifice asked on the table") { alice.shows(bar) && alice.shows(hasText("Sacrifice") and isHeading()) }
        assertFalse(alice.shows(hasContentDescription("Cast panel")) || alice.shows(hasContentDescription("Automatum choice")),
            "no panel: ${alice.screen()}")
        // The enchanted Pup is the one by the Mantle; a tap on its group (the Pup with its Mantle) opens it (TBL-130), a
        // tap on the Pup then picks it
        fun tapEnchanted() {
            val mantle = alice.battlefieldCard("Ember Mantle").fetchSemanticsNode().boundsInRoot
            val pups = (0..1).map { alice.battlefieldCard("Ash Pup", index = it) }
            pups.minBy { kotlin.math.abs(it.fetchSemanticsNode().boundsInRoot.center.x - mantle.center.x) }.performClick()
        }
        tapEnchanted()
        run(skip = java.time.Duration.ofMillis(300))
        if (alice.shows(bar)) tapEnchanted()
        waitUntil("the enchanted Pup sacrificed and the Altar's ability resolved", along = listOf(bob)) {
            alice.field("Ash Pup") == listOf(plain) && alice.field("Ember Mantle").isEmpty() && alice.state.stack.isEmpty() &&
                alice.state.player(alice.me).life == 22 && alice.inOwnMain()
        }
        assertFalse(alice.refusalSeen, "no refusal: ${alice.shownTexts}")
    }

    // D1, STK-047, STK-030, TBL-130: Bob has two identical Ash Pups (one stack); Alice's Ember Edict ("Each opponent sacrifices
    // a creature of their choice") asks Bob on his table: the bar, his Pups glowing; a tap opens their stack, a tap on one
    // sacrifices it
    @Test fun anEdictIsAnsweredOnTheTable() {
        startGame(TestDeck("Edicts", listOf(TestCards.mountain to 20, TestCards.edict to 20)),
            TestDeck("Pups", listOf(TestCards.mountain to 10, TestCards.pup to 30)),
            deal = { a, b -> "Ember Edict" in hand(a) && hand(b).count { it == "Ash Pup" } >= 2 })
        bothOpen()
        bothKeep(bobAway = false)
        alice.hold("To combat")
        waitUntil("Bob's Main 1", along = listOf(alice)) { bob.inOwnMain() }
        for (n in 1..2) {
            bob.playFromHand("Ash Pup", this)
            waitUntil("Pup $n on Bob's battlefield", along = listOf(alice)) { bob.field("Ash Pup").size == n && bob.inOwnMain() }
        }
        bob.hold("To combat")
        waitUntil("Alice's second Main 1", along = listOf(bob)) { alice.inOwnMain() && alice.state.turn.number >= 3 }
        alice.playFromHand("Ember Edict", this)
        waitUntil("Bob's sacrifice asked on his table") {
            bob.state.pending?.decision is Decision.ChooseObjects && bob.shows(bar)
        }
        assertFalse(bob.shows(hasContentDescription("Automatum choice")), "no panel: ${bob.screen()}")
        bob.battlefieldCard("Ash Pup").performClick()
        run(skip = java.time.Duration.ofMillis(300))
        // TBL-130: the stack of identical Pups opened on the tap; a tap on a member picks it
        if (bob.shows(bar)) bob.battlefieldCard("Ash Pup", index = 1).performClick()
        waitUntil("one of Bob's Pups sacrificed", along = listOf(alice)) {
            bob.field("Ash Pup").size == 1 && bob.state.pending == null && bob.state.stack.isEmpty()
        }
        assertEquals(1, bob.state.player(bob.me).graveyard.count { cardName(bob.state, it) == "Ash Pup" })
        assertTrue(!bob.refusalSeen, "no refusal: ${bob.shownTexts}")
    }
}
