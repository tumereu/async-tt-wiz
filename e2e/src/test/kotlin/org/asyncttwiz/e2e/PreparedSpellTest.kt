package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performClick
import org.asyncttwiz.android.HandRow
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * SYS-206, SYS-068 (owner playtest 2026-11-19, N24), CR 723.3c on two phones: a prepared creature's spell is cast from the creature
 * as one of its abilities (its copy waits in exile, not in the hand row).
 */
class PreparedSpellTest : E2e() {
    private val chip = hasContentDescription("{0}: Cast Ember Mending")

    /** Alice's Adept on her battlefield as her screen names it (by its face, or by the card's both names). */
    private fun adept() = listOf("Ember Adept", "Ember Adept // Ember Mending").firstNotNullOf { runCatching { alice.battlefieldCard(it) }.getOrNull() }

    // SYS-206, TBL-124: Alice casts her Ember Adept and prepares it; a tap on it opens its chips, among them "{0}: Cast Ember Mending",
    // and that chip casts the spell: she gains 2 life, the Adept is no longer prepared and nothing of it was in her hand row
    @Test fun aPreparedCreaturesSpellIsCastFromTheCreature() {
        startGame(TestDeck("Prepare", listOf(TestCards.mountain to 20, TestCards.adept to 20)), TestDeck.glimmers(), deal = { a, _ ->
            hand(a).any { it.startsWith("Ember Adept") }
        })
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand(listOf("Ember Adept // Ember Mending", "Ember Adept").first { runCatching { alice.handCard(it) }.isSuccess }, this)
        waitUntil("the Adept on the battlefield", along = listOf(bob)) {
            (alice.field("Ember Adept").size + alice.field("Ember Adept // Ember Mending").size) == 1 && alice.state.stack.isEmpty() && alice.inOwnMain() && alice.settled(this)
        }
        // Its one ability now: a tap prepares it
        adept().performClick()
        waitUntil("the Adept prepared, its spell's copy in exile, on the server", along = listOf(bob)) {
            alice.named(alice.state.exile, "Ember Mending").size == 1 && alice.state.stack.isEmpty() && alice.inOwnMain() &&
                alice.engine { device.session.pending.isEmpty() } && alice.settled(this)
        }
        assertTrue(HandRow.cards(alice.shown.board, alice.id).isEmpty(), "the copy isn't in her hand row")
        assertFalse(alice.shows(hasContentDescription("from exile", substring = true)), "nothing from exile in her hand row")
        adept().performClick()
        waitUntil("the Adept's chips with its spell: ${alice.screen()}") { alice.shows(chip) }
        alice.node(chip).performClick()
        waitUntil("the Mending resolved, on the server", along = listOf(bob)) {
            alice.state.player(alice.me).life == 22 && alice.state.stack.isEmpty() && alice.inOwnMain() &&
                alice.engine { device.session.pending.isEmpty() }
        }
        assertEquals(0, alice.named(alice.state.exile, "Ember Mending").size, "the copy is gone")
        assertFalse(alice.refusalSeen, "no refusal or shake: ${alice.shownTexts}")
    }
}
