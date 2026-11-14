package org.asyncttwiz.e2e

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.performClick
import arcanum.engine.decision.Decision
import arcanum.engine.state.ObjectId
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Problem report JN5UQN (prod 0.2.0-beta16): "when I connived the drawn card was shown upside down". Connive draws and then
 * asks the discard in one engine step, so while the discard is asked the drawn card isn't in the hand of the device's state
 * yet (the step is redone with the answer): the choice named it by an object the table didn't have and drew it as a hidden
 * card. Alice casts an Ember Schemer, which connives as it enters; Bob plays along.
 */
class ConniveShownTest : E2e() {
    private val ok = hasClickAction() and hasAnyDescendant(hasText("OK"))

    // TRN-067, STK-044 (CR 701.50a): Alice's discard choice shows every card face up, the one she drew too; the card she
    // discards is face up in her graveyard on both tables
    @Test fun theDrawnCardIsShownFaceUpInTheDiscardChoice() {
        startGame(TestDeck.schemers(), deal = { a, _ -> "Ember Schemer" in hand(a) })
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Ember Schemer", this)
        waitUntil("Alice's discard choice", along = listOf(bob)) {
            alice.state.pending?.decision is Decision.ChooseObjects && alice.current()
        }
        val choice = assertIs<Decision.ChooseObjects>(alice.state.pending?.decision)
        val state = alice.state
        // The card she drew: the option the hand doesn't hold yet, the same card as an object of the state
        val drawn = choice.options.single { it !in state.player(alice.me).hand } as ObjectId.Card
        val name = assertNotNull(state.objects.values.firstOrNull { it.card == drawn.card }?.definition?.faces?.first()?.name)
        waitUntil("the choice's cards on Alice's screen") { alice.shows(hasContentDescription(name)) || alice.shows(hasContentDescription("Hidden card")) }
        assertFalse(alice.shows(hasContentDescription("Hidden card")), "no card of the choice hidden: ${alice.screen()}")
        assertTrue(alice.shows(hasContentDescription(name)), "the drawn $name shown by its name: ${alice.screen()}")
        // She discards a card of the choice
        val card = alice.nodes(hasContentDescription(name)).fetchSemanticsNodes().last()
        alice.node(SemanticsMatcher("a card of the choice") { it.id == card.id }).performClick()
        run()
        if (alice.state.pending != null) {
            waitUntil("OK enabled") { alice.shows(ok and isEnabled()) }
            alice.text("OK").performClick()
        }
        waitUntil("the discard on both tables", along = listOf(bob)) {
            alice.state.pending == null && alice.state.player(alice.me).graveyard.size == 1 && bob.state.player(alice.me).graveyard.size == 1 &&
                alice.engine { device.session.pending.isEmpty() }
        }
        val gone = bob.state.player(alice.me).graveyard.single() as ObjectId.Card
        val shown = bob.shown.board
        val atBob = shown.board.cards.single { it.id == gone.card.value }
        assertFalse(atBob.covered, "the discarded card face up to Bob")
        assertEquals(name, shown.definitions.first { it.digest == atBob.definition }.definition.faces.first().name)
        assertFalse(alice.refusalSeen || bob.refusalSeen, "no refusal")
    }
}
