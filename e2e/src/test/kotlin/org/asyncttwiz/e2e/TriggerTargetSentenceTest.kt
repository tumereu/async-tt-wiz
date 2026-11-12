package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performClick
import arcanum.engine.decision.Decision
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Owner playtest, 2026-11-11: equipping Demonmail Hauberk ("Equip—Sacrifice a creature.") he chose the sacrifice and the equip
 * target the wrong way round; and Blood Artist, sacrificed to it, asked "Choose target player" for its death trigger with no
 * sentence saying what for, though every question shows the sentence it asks about (STK-030). The Artist had left the
 * battlefield, so its trigger's source was taken for hidden (fixed with 3UE2NW's last known information, CR 608.2h).
 */
class TriggerTargetSentenceTest : E2e() {
    private val vigilText = "Whenever this creature or another creature dies, target player loses 1 life and you gain 1 life."

    /** Alice's Ember Vigil, Ash Pup and Ember Hauberk on her battlefield, in her Main 1. */
    private fun aliceHasHerHauberk() {
        startGame(TestDeck("Vigils", listOf(TestCards.mountain to 10, TestCards.vigil to 10, TestCards.pup to 10, TestCards.hauberk to 10)),
            TestDeck.glimmers(),
            deal = { a, _ -> hand(a).let { h -> listOf("Ember Vigil", "Ash Pup", "Ember Hauberk").all { it in h } } })
        bothOpen()
        bothKeep(bobAway = false)
        for (name in listOf("Ember Vigil", "Ash Pup", "Ember Hauberk")) {
            alice.playFromHand(name, this)
            waitUntil("$name on Alice's battlefield", along = listOf(bob)) { alice.field(name).isNotEmpty() && alice.inOwnMain() }
        }
    }

    // STK-047 (owner 2026-11-11, which pick is which), CR 702.6a, 601.2c, 601.2h: the bar shows the equip's whole line with the
    // phrase picked now shining: the keyword's name for its target (it has no phrase of its own), then "a creature" for the
    // sacrifice with the target named after "Equip" in brackets; the picked Pup carries an "Equip" chip on the table
    @Test fun theEquipsTargetAndSacrificeShowWhichPickIsWhich() {
        aliceHasHerHauberk()
        alice.battlefieldCard("Ember Hauberk").performClick()
        waitUntil("the equip's target asked") { alice.shows(hasContentDescription("Choose targets")) }
        assertTrue(alice.shows(hasContentDescription("Question: Equip—Sacrifice a creature.")), "the equip's line: ${alice.screen()}")
        assertTrue(alice.shows(hasContentDescription("Choosing now: Equip")), "the target's phrase: ${alice.screen()}")
        alice.battlefieldCard("Ash Pup").performClick()
        waitUntil("the equip's sacrifice asked on the table: ${alice.screen()}") { alice.shows(hasContentDescription("Choose on the table")) }
        assertTrue(alice.shows(hasContentDescription("Question: Equip (Ash Pup)—Sacrifice a creature.")), "the target named: ${alice.screen()}")
        assertTrue(alice.shows(hasContentDescription("Choosing now: a creature")), "the sacrifice's phrase: ${alice.screen()}")
        assertTrue(alice.shows(hasContentDescription("Equip: Ash Pup", substring = true)), "the Pup's role chip: ${alice.screen()}")
        alice.battlefieldCard("Ember Vigil").performClick()
        waitUntil("the Vigil's trigger asking its target", along = listOf(bob)) { alice.state.pending?.decision is Decision.Targets }
        assertFalse(alice.refusalSeen, "no refusal: ${alice.shownTexts}")
    }

    // STK-030, STK-047, CR 603.3d, CR 608.2h: the trigger of a creature sacrificed as a cost asks its target with the trigger's
    // whole text under the title, the phrase being chosen highlighted
    @Test fun aSacrificedCreaturesTriggerAsksItsTargetWithItsText() {
        aliceHasHerHauberk()
        alice.battlefieldCard("Ember Hauberk").performClick()
        waitUntil("the equip's target asked") { alice.shows(hasContentDescription("Choose targets")) }
        alice.battlefieldCard("Ash Pup").performClick()
        waitUntil("the equip's sacrifice asked on the table: ${alice.screen()}") { alice.shows(hasContentDescription("Choose on the table")) }
        alice.battlefieldCard("Ember Vigil").performClick()
        waitUntil("the Vigil's trigger asking its target", along = listOf(bob)) {
            alice.state.pending?.decision is Decision.Targets && alice.shows(hasContentDescription("Choose targets"))
        }
        assertTrue(alice.field("Ember Vigil").isEmpty(), "the Vigil has left the battlefield")
        assertTrue(alice.shows(hasContentDescription("Question: $vigilText")), "the trigger's text under the title: ${alice.screen()}")
        assertTrue(alice.shows(hasContentDescription("Choosing now: target player")), "the phrase being chosen: ${alice.screen()}")
        alice.node(hasContentDescription("${bob.name}, life 20", substring = true)).performClick()
        waitUntil("the trigger and the equip resolved", along = listOf(bob)) {
            alice.state.stack.isEmpty() && alice.state.pending == null && alice.inOwnMain()
        }
        assertEquals(19, alice.state.player(bob.me).life)
        assertEquals(21, alice.state.player(alice.me).life)
        assertFalse(alice.refusalSeen, "no refusal: ${alice.shownTexts}")
    }
}
