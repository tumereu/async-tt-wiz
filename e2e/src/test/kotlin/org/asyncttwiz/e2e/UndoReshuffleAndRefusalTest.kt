package org.asyncttwiz.e2e

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Owner decisions 2026-11-19 (playtest N4 and the "can't do" explainer): an undo that takes back library cards drawn says
 * the libraries reshuffle, in the request's dialogs on both phones or, after a free undo, in its fading text (owner,
 * 2026-11-22: only on the phone of the player who undid; the other player's table shows no text for it); and a shake says
 * why in a short bubble.
 */
class UndoReshuffleAndRefusalTest : E2e() {
    // HIS-236, HIS-202, HIS-168: Bob ends his first turn and Alice's phone passes on into her turn, drawing her card; Bob's
    // Undo takes his End turn back, free (Alice only passed), with her draw: his fading text says her library reshuffled;
    // her table shows no text for his undo (owner, 2026-11-22)
    @Test fun aFreeUndoOfTheOtherPlayersDrawSaysTheLibraryReshuffled() {
        startGame(bobStarts = true)
        bothOpen()
        alice.tap("Keep")
        bob.tap("Keep")
        waitUntil("Bob's Main 1", along = listOf(alice)) { bob.inOwnMain() }
        bob.hold("To combat")
        waitUntil("Alice's Main 1 after her draw", along = listOf(bob)) { alice.inOwnMain() && alice.state.turn.active == alice.me }
        waitUntil("both settled") { alice.settled(this) && bob.settled(this) }
        bob.tap("Undo")
        waitUntil("Bob back in his turn, Alice's draw taken back on both", along = listOf(alice)) {
            bob.state.turn.active == bob.me && alice.state.turn.active == bob.me
        }
        assertFalse(bob.shows(hasTestTag("rewind-confirm")), "free: no dialog")
        waitUntil("Bob told her library reshuffled") { bob.told("${alice.name}'s library reshuffled: the next draws may change.") }
        assertTrue(bob.told("Undid"), "${bob.shownTexts}")
        repeat(3) { run() }
        assertFalse(alice.toldATakeBack(bob), "nothing told on Alice's table: ${alice.shownTexts}")
    }

    // HIS-235, HIS-209, HIS-100: Alice plays a Mountain and ends her turn; Bob draws and plays his Mountain. Alice's Undo back to
    // before her End turn takes his draw back too: her dialog asking him and his "asks to undo" both say his library reshuffles;
    // once he accepts, neither fading text repeats it (HIS-236)
    @Test fun anUndoRequestThatTakesBackADrawSaysSoOnBothPhones() {
        startGame(deal = { a, b -> "Mountain" in hand(a) && "Mountain" in hand(b) })
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Mountain", this)
        waitUntil("Alice's Mountain", along = listOf(bob)) { alice.field("Mountain").size == 1 && alice.inOwnMain() }
        alice.hold("To combat")
        waitUntil("Bob's Main 1 after his draw", along = listOf(alice)) { bob.inOwnMain() && bob.settled(this) }
        bob.playFromHand("Mountain", this)
        waitUntil("Bob's Mountain on Alice's table, all of it on the server", along = listOf(alice)) {
            alice.field("Mountain", owner = bob.me).size == 1 && bob.engine { device.session.pending.isEmpty() } &&
                alice.engine { device.session.pending.isEmpty() } && alice.game?.shown?.behind == false
        }
        alice.hold("Undo")
        waitUntil("Alice's Undo dialog asking Bob, saying his library reshuffles") {
            alice.showsText("Ask ${bob.name}") && runCatching { alice.node(hasTestTag("rewind-confirm")).assertIsEnabled() }.isSuccess &&
                alice.showsText("Undoing this reshuffles ${bob.name}'s library: the next cards drawn may change.")
        }
        alice.node(hasTestTag("rewind-confirm")).performClick()
        waitUntil("Bob asked to accept the undo, told his library reshuffles", along = listOf(alice)) {
            bob.shows(hasTestTag("request-accept")) && bob.shows(hasText("Undoing this reshuffles your library: the next cards drawn may change."))
        }
        bob.node(hasTestTag("request-accept")).performClick()
        waitUntil("both back in Alice's turn", along = listOf(alice)) {
            alice.state.turn.active == alice.me && bob.state.turn.active == alice.me && bob.field("Mountain").isEmpty()
        }
        repeat(3) { run() }
        assertFalse(alice.told("library reshuffled") || bob.told("library reshuffled"), "${alice.shownTexts} / ${bob.shownTexts}")
    }

    // TBL-118, TBL-212, ZON-079: Alice in her Main 1 with no land out taps Storm Spark in her hand: it shakes and a bubble says why
    @Test fun aShakeSaysWhyInABubble() {
        startGame(deal = { a, _ -> "Storm Spark" in hand(a) })
        bothOpen()
        bothKeep(bobAway = false)
        waitUntil("Alice's Main 1", along = listOf(bob)) { alice.inOwnMain() && alice.settled(this) }
        alice.handCard("Storm Spark").performClick()
        // (the card menu's words when its plays are worked out already, else the raised card's chips found nothing to pay)
        waitUntil("the bubble says why") {
            (alice.told("Storm Spark can't be cast now.") || alice.told(org.asyncttwiz.android.CANT_PAY)) &&
                alice.shows(hasTestTag("refusal-bubble"))
        }
        assertTrue(alice.raisedInHand() == null, "not raised")
    }
}
