package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isNotEnabled
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import arcanum.engine.decision.Decision
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Owner gallery review, 2026-11-06: naming a card is a search field autocompleting card names, never free text; the names
 * of your own library come first (a card-stack icon), then those of cards seen this game (an eye), then every card; the
 * effect's restriction filters them. Alice casts an Ember Muzzle ("As this creature enters, choose a nonland card name."):
 * her library's Cinder Hounds are offered, her Mountains (lands) are not, nothing of Bob's hidden hand shows; a written
 * name that isn't offered isn't taken; she taps Cinder Hound and OK names it.
 */
class NameACardTest : E2e() {
    private fun ok() = hasClickAction() and hasAnyDescendant(hasText("OK"))

    // STK-041, STK-030, CR 201.4, I-D27: the name search, from the own library first, restricted to nonland cards
    @Test fun aCardIsNamedFromTheOfferedNames() {
        startGame(TestDeck("Muzzles", listOf(TestCards.mountain to 14, TestCards.muzzle to 13, TestCards.hound to 13)),
            TestDeck.thirds(TestCards.cub), deal = { a, b -> "Ember Muzzle" in hand(a) && "Ember Cub" in hand(b) })
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Ember Muzzle", this)
        waitUntil("Alice naming a card", along = listOf(bob)) {
            alice.state.pending?.decision is Decision.ChooseCardName && alice.shows(hasText("Name a card") and isHeading())
        }
        waitUntil("her library's names offered") { alice.shows(hasContentDescription("Cinder Hound, in your library")) }
        for (where in listOf("in your library", "seen this game", "a card")) {
            assertFalse(alice.shows(hasContentDescription("Mountain, $where", substring = true)), "no land: ${alice.screen()}")
            assertFalse(alice.shows(hasContentDescription("Ember Cub, $where", substring = true)), "nothing of Bob's hidden hand: ${alice.screen()}")
        }
        // A name only written isn't taken
        alice.described("Card name").performTextInput("Cinder Houn")
        waitUntil("the search narrowed") { !alice.shows(hasContentDescription("Ember Muzzle, in your library")) }
        assertTrue(alice.shows(ok() and isNotEnabled()), "OK waits for a name: ${alice.screen()}")
        alice.described("Cinder Hound, in your library").performClick()
        waitUntil("Cinder Hound chosen") { alice.shows(hasContentDescription("Cinder Hound, in your library, chosen")) }
        alice.node(ok() and isEnabled()).performClick()
        waitUntil("the Muzzle on the battlefield, Cinder Hound named", along = listOf(bob)) {
            alice.state.pending == null && alice.field("Ember Muzzle").isNotEmpty() &&
                alice.state.obj(alice.field("Ember Muzzle").first())!!.memory.values.values.any { "Cinder Hound" in it.toString() }
        }
        assertFalse(alice.refusalSeen, "no refusal: ${alice.shownTexts}")
    }

    // STK-041, CR 709.4a, 201.4b, 201.4d: each name of a card is offered by itself, never both together, the restriction judged by
    // that part alone: a split card's second half can be named, and of an instant with a land for a back only the instant's name is nonland
    @Test fun eachNameOfACardIsOfferedApart() {
        startGame(TestDeck("Muzzles", listOf(TestCards.mountain to 13, TestCards.muzzle to 13, TestCards.trailBlaze to 7, TestCards.pathCrag to 7)),
            TestDeck.thirds(TestCards.cub), deal = { a, b -> "Ember Muzzle" in hand(a) && "Ember Cub" in hand(b) })
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Ember Muzzle", this)
        waitUntil("Alice naming a card", along = listOf(bob)) {
            alice.state.pending?.decision is Decision.ChooseCardName && alice.shows(hasText("Name a card") and isHeading())
        }
        waitUntil("both halves offered") { alice.shows(hasContentDescription("Trail, in your library")) && alice.shows(hasContentDescription("Blaze, in your library")) }
        assertTrue(alice.shows(hasContentDescription("Ember Path, in your library")), "the instant side: ${alice.screen()}")
        for (where in listOf("in your library", "seen this game", "a card")) {
            assertFalse(alice.shows(hasContentDescription("Ember Crag, $where")), "the land side isn't a nonland name: ${alice.screen()}")
            assertFalse(alice.shows(hasContentDescription("Trail // Blaze, $where")), "not both: ${alice.screen()}")
        }
        alice.described("Blaze, in your library").performClick()
        waitUntil("Blaze chosen") { alice.shows(hasContentDescription("Blaze, in your library, chosen")) }
        alice.node(ok() and isEnabled()).performClick()
        waitUntil("the Muzzle on the battlefield, Blaze named", along = listOf(bob)) {
            alice.state.pending == null && alice.field("Ember Muzzle").isNotEmpty() &&
                alice.state.obj(alice.field("Ember Muzzle").first())!!.memory.values.values.any { "Blaze" in it.toString() }
        }
        assertFalse(alice.refusalSeen, "no refusal: ${alice.shownTexts}")
    }
}
