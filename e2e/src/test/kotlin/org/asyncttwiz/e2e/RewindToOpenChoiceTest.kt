package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.state.ObjectId
import org.asyncttwiz.android.AppLog
import org.asyncttwiz.protocol.game.GameDevice
import org.junit.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Problem report DH3QX6 (prod, 0.2.0-beta10): "When I went back in the timeline to when I cast a card which manifests
 * dread, I was unable to select a card." Going back to a resolution waiting on a choice among the top cards of a library,
 * the first new event starts a new line (HIS-042), whose reshuffle moved those very cards: the step, redone with the
 * answer, looked at two other cards and refused it ("Not a valid choice"), so OK only shook, however many times.
 */
class RewindToOpenChoiceTest : E2e() {
    private val inPanel = hasAnyAncestor(hasContentDescription("Automatum choice"))

    /** Both keep, live; Alice, in her Main 1, casts an Ember Dread: it resolves and Manifest which card? waits for her. */
    private fun aliceCastsADread(): Decision.ChooseObjects {
        startGame(TestDeck.dreads(), TestDeck.dreads(), deal = { a, _ -> "Mountain" in hand(a) && "Ember Dread" in hand(a) })
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Ember Dread", this)
        return choiceOpen()
    }

    /** Alice's panel, up with the choice on the phone's newest board, everything before it on the server. */
    private fun choiceOpen(): Decision.ChooseObjects {
        waitUntil("Manifest which card? on Alice's phone", along = listOf(bob)) {
            alice.state.pending?.decision is Decision.ChooseObjects && alice.shows(inPanel) &&
                alice.engine { device.session.pending.isEmpty() } && alice.current()
        }
        return assertIs<Decision.ChooseObjects>(alice.state.pending?.decision)
    }

    /** Whether this player's table told a refusal: a shake, a note that it wasn't taken, or the panel's. */
    private fun E2ePlayer.refused() = refusalSeen || told("doesn't allow")

    private fun nameOf(id: ObjectId): String = alice.state.obj(id)!!.definition!!.faces.first().name

    /** Alice taps the card [index] of [choice] and OK: it is face down on her battlefield and the other in her graveyard,
     * on the server. */
    private fun aliceChooses(choice: Decision.ChooseObjects, index: Int) {
        val card = assertNotNull(alice.state.obj(choice.options[index])).card
        val other = assertNotNull(alice.state.obj(choice.options[1 - index])).card
        val name = nameOf(choice.options[index])
        // Two of one name are side by side in the options' order
        val at = choice.options.take(index).count { nameOf(it) == name }
        alice.nodes(hasContentDescription(name) and inPanel)[at].performClick()
        waitUntil("the card picked") { alice.shows(hasContentDescription("$name, chosen") and inPanel) }
        alice.text("OK").performClick()
        waitUntil("the choice made, on the server, or refused", along = listOf(bob)) {
            alice.refused() || (alice.state.pending == null && alice.engine { device.session.pending.isEmpty() })
        }
        assertFalse(alice.refused(), "OK isn't refused: ${alice.shownTexts}")
        val s = alice.state
        assertEquals(card, s.battlefield.mapNotNull(s::obj).single { it.faceDown }.card, "the card she chose is the one manifested")
        assertTrue(s.player(alice.me).graveyard.mapNotNull(s::obj).any { it.card == other }, "the other is in her graveyard")
    }

    /** Alice asks to go back to just after [target] and Bob accepts: the server and her phone are back there. */
    private fun bobAcceptsAliceGoingBackTo(target: String) {
        assertEquals(true, alice.game!!.undoNeedsRequest(target), "looked-at cards can't be undone for free (HIS-087)")
        alice.game!!.requestUndo(target, "")
        waitUntil("Bob asked to accept the undo") { bob.shows(hasTestTag("request-accept")) }
        bob.node(hasTestTag("request-accept")).performClick()
        waitUntil("the server and Alice's phone back at the choice") {
            serverGame()?.headId == target && alice.engine { device.session.tip == target } &&
                alice.state.battlefield.none { alice.state.obj(it)?.faceDown == true }
        }
    }

    // HIS-042, HIS-087, STK-056 (report DH3QX6): Alice manifests dread, goes back to her open choice with Bob's approval,
    // and chooses again: the panel shows the same two cards, and OK manifests the one she taps
    @Test fun aRewindToAnOpenChoiceAmongTheTopCardsLetsThePlayerChooseAgain() {
        val first = aliceCastsADread()
        val choosing = alice.engine { device.session.tip }
        aliceChooses(first, 0)
        bobAcceptsAliceGoingBackTo(choosing)
        val again = choiceOpen()
        assertEquals(first.options.toSet(), again.options.toSet(), "the same two cards to choose from")
        assertEquals(first.id, again.id)
        // An answer that can't be taken (no such card asked): nothing goes out, and the report can tell why
        val notAsked = alice.state.player(alice.me).library.last()
        assertIs<GameDevice.Acted.Refused>(alice.game!!.send(listOf(Input.Answer(again.id, Answer.Objects(listOf(notAsked))))))
        waitUntil("the board after the refusal on Alice's table") { alice.current() }
        aliceChooses(again, 1)
        waitUntil("Alice's phone settled") { alice.engine { device.session.pending.isEmpty() } }
        // What a report carries says what was asked, what was shown, each tap and why nothing went out
        val log = AppLog.text()
        val record = alice.game!!.repro()!!.log
        assertContains(log, "Undo request to $choosing")
        assertContains(log, "asks ${alice.id.take(8)}: ChooseObjects ${first.id.value} \"Manifest which card?\"")
        assertContains(log, "Card tapped in ${first.id.value}")
        assertContains(log, "Answer tapped on ${first.id.value}")
        assertContains(log, "Flow step sent")
        assertContains(log, "Own send refused: Not an object offered")
        assertTrue(record.any { it.startsWith("out EngineRequestRewind $choosing") }, "the request: $record")
        assertTrue(record.any { it.startsWith("rewind to $choosing by ${alice.id}") && "taken back" in it }, "the rewind: $record")
        assertTrue(record.any { it.startsWith("act refused: Not an object offered") }, "the refusal: $record")
        assertTrue(record.any { it.startsWith("line ") && "ChooseObjects ${first.id.value}" in it }, "the new line at the choice: $record")
        waitUntil("the server taking the new line's choice", along = listOf(bob)) {
            alice.engine { device.session.head } == serverGame()?.headId
        }
    }

    // HIS-042, HIS-084 (report DH3QX6, as it happened): after the choice Alice plays on (a Mountain), takes that back with
    // her own free Undo, then goes back further to the open choice with Bob's approval and chooses again
    @Test fun anOwnUndoThenARewindToTheOpenChoiceLetsThePlayerChooseAgain() {
        val first = aliceCastsADread()
        val choosing = alice.engine { device.session.tip }
        aliceChooses(first, 0)
        val chosen = alice.engine { device.session.head }
        waitUntil("Alice's own main phase again", along = listOf(bob)) { alice.inOwnMain() && alice.current() }
        // A Mountain chosen is on the battlefield too, face down
        val mountains = alice.field("Mountain").size
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain on the server", along = listOf(bob)) {
            alice.field("Mountain").size == mountains + 1 && alice.engine { device.session.pending.isEmpty() }
        }
        val landed = alice.engine { device.session.head }
        assertEquals(false, alice.game!!.undoNeedsRequest(chosen), "her land play undoes freely")
        alice.game!!.undo(chosen)
        waitUntil("the Mountain back in her hand, on the server", along = listOf(bob)) {
            alice.field("Mountain").size == mountains && serverGame()?.headId != landed && alice.current()
        }
        bobAcceptsAliceGoingBackTo(choosing)
        val again = choiceOpen()
        assertEquals(first.options.toSet(), again.options.toSet(), "the same two cards to choose from")
        aliceChooses(again, 1)
        waitUntil("the server taking the new line's choice", along = listOf(bob)) {
            alice.engine { device.session.head } == serverGame()?.headId
        }
    }
}
