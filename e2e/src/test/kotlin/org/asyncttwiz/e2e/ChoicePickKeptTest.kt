package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.performClick
import arcanum.engine.setup.GameResult
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.PlaySettings
import org.asyncttwiz.protocol.game.ResponseMode
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * A worker's run, 2026-11-09 (RewindToOpenChoiceTest): a card picked in a choice's panel vanished when a new board arrived
 * right after the tap. A choice's picks live in its panel, and every new board took the panel off the table until that
 * board's own flow was worked out, also when it asked the very same choice: the picks went with it and the panel came back
 * with nothing picked, without a word. On a board that only changed whose move it is (an undo request), the panel didn't
 * come back at all: such a board keeps its computed values, so its flow stayed the old board's and never matched it.
 *
 * Alice plays a Cinder Trail and activates it ({T}, pay 1 life, sacrifice it: search for a basic land card), as in
 * LibrarySearchTest: a choice of cards in a panel, with a card to pick and OK.
 */
class ChoicePickKeptTest : E2e() {
    private val inPanel = hasAnyAncestor(hasContentDescription("Automatum choice"))
    private val chosen = hasContentDescription("Mountain, chosen") and inPanel

    /** The game with Alice's Cinder Trail on the battlefield, in her Main 1, everything settled. */
    private fun aliceWithHerTrail() {
        startGame(TestDeck.trails(), deal = { a, _ -> "Cinder Trail" in hand(a) })
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Cinder Trail", this)
        waitUntil("the Trail on the battlefield", along = listOf(bob)) { alice.field("Cinder Trail").isNotEmpty() && alice.inOwnMain() }
        waitUntil("both settled") { alice.settled(this) && bob.settled(this) }
    }

    /** Alice activates her Trail and passes it on; [along]: who plays along while she does. */
    private fun aliceActivatesHerTrail(along: List<E2ePlayer> = emptyList()) {
        alice.battlefieldCard("Cinder Trail").performClick()
        waitUntil("Alice's search", along = along) {
            // Alice passes her own ability on (STK-026) as the main button says
            if (alice.stepArrow() == "Resolve" || alice.stepArrow() == "Pass") alice.once { alice.tap(alice.stepArrow()!!) }
            alice.shows(hasText("Search your library for a basic land card") and isHeading())
        }
    }

    private fun alicePicksAMountain() {
        alice.nodes(hasContentDescription("Mountain") and inPanel)[0].performClick()
        waitUntil("the Mountain picked") { alice.shows(chosen) }
    }

    /** Alice's table has taken in what arrived, and the panel of her search is up on it. */
    private fun thePanelUpOnTheNewBoard() = waitUntil("the search on Alice's newest board") {
        alice.current() && alice.shows(hasContentDescription("Mountain") and inPanel)
    }

    /** OK takes the pick she still sees: the Mountain is found. */
    private fun okFindsTheMountain(mountains: Int) {
        alice.text("OK").performClick()
        waitUntil("the Mountain found, on the server", along = listOf(bob)) {
            alice.field("Mountain").size == mountains + 1 && alice.field("Cinder Trail").isEmpty() &&
                alice.engine { device.session.pending.isEmpty() }
        }
        assertFalse(alice.refusalSeen, "no refusal: ${alice.shownTexts}")
    }

    // STK-030, STK-056, STK-044, HIS-087: Bob asks to go back while Alice is choosing, then takes his request back: each is
    // a new board asking the same choice (whose move it is changes): the panel stays and the card stays picked, and OK takes it
    @Test fun aPickStaysWhenTheOtherPlayerAsksToGoBackWhileSheChooses() {
        aliceWithHerTrail()
        val mountains = alice.field("Mountain").size
        val before = alice.engine { device.session.tip }
        aliceActivatesHerTrail(along = listOf(bob))
        waitUntil("both settled, her search open") { alice.settled(this) && bob.settled(this) }
        alicePicksAMountain()
        var board = alice.shown.board.board
        bob.game!!.requestUndo(before, "")
        waitUntil("Bob's request on Alice's phone: a new board") { alice.shown.request != null && alice.shown.board.board !== board }
        thePanelUpOnTheNewBoard()
        assertTrue(alice.shows(chosen), "the Mountain still picked with his request up: ${alice.screen()}")
        board = alice.shown.board.board
        bob.game!!.cancelRequest()
        waitUntil("Bob's request gone from Alice's phone: a new board") { alice.shown.request == null && alice.shown.board.board !== board }
        thePanelUpOnTheNewBoard()
        assertTrue(alice.shows(chosen), "the Mountain still picked: ${alice.screen()}")
        okFindsTheMountain(mountains)
    }

    // STK-030, STK-056, HIS-096, TRN-070: Bob stops at Alice's ability and is away, so she taps Assume and chooses on what she
    // assumed; he comes back and passes while she is choosing: the card she tapped stays picked, and OK takes it
    @Test fun aPickStaysWhenTheOtherPlayersOwnPassComesInWhileSheChoosesOnAnAssumption() {
        startGame(TestDeck.trails(), bobSettings = PlaySettings(mode = ResponseMode.Normal), deal = { a, _ -> "Cinder Trail" in hand(a) })
        bothOpen()
        bothKeep(bobAway = true)
        alice.playFromHand("Cinder Trail", this)
        waitUntil("the Trail on the battlefield") { alice.field("Cinder Trail").isNotEmpty() && alice.inOwnMain() }
        val mountains = alice.field("Mountain").size
        alice.battlefieldCard("Cinder Trail").performClick()
        waitUntil("Alice's search, on what she assumed") {
            if (alice.stepArrow() == "Resolve" || alice.stepArrow() == "Pass") alice.once { alice.tap(alice.stepArrow()!!) }
            if ((alice.status as? GameDevice.Status.Waiting)?.offer == GameDevice.Offer.AssumeResolves) alice.once { alice.tap("Assume resolves") }
            alice.shows(hasText("Search your library for a basic land card") and isHeading())
        }
        waitUntil("her events on the server") { alice.engine { device.session.pending.isEmpty() } && alice.current() }
        alicePicksAMountain()
        bob.back()
        val tip = alice.engine { device.session.tip }
        waitUntil("Bob's pass on Alice's phone", along = listOf(bob)) { alice.engine { device.localHeads[bob.me] == tip } }
        thePanelUpOnTheNewBoard()
        assertTrue(alice.shows(chosen), "the Mountain still picked: ${alice.screen()}")
        okFindsTheMountain(mountains)
    }

    // STK-030, STK-056, TRN-047: the other player's event beside the game (his result suggestion, table-only) while Alice is
    // choosing: the same choice stays open, so the card she tapped stays picked, and OK takes it
    @Test fun aPickStaysWhenTheOtherPlayerSuggestsAResultWhileSheChooses() {
        aliceWithHerTrail()
        val mountains = alice.field("Mountain").size
        aliceActivatesHerTrail(along = listOf(bob))
        waitUntil("both settled, her search open") { alice.settled(this) && bob.settled(this) }
        alicePicksAMountain()
        var board = alice.shown.board.board
        assertIs<GameDevice.Acted.Sent>(bob.game!!.suggest(GameResult.Won(bob.me)))
        waitUntil("Bob's suggestion on Alice's table: a new board") { alice.shown.board.board !== board && alice.state.pending != null }
        thePanelUpOnTheNewBoard()
        assertTrue(alice.shows(chosen), "the Mountain still picked with his suggestion up: ${alice.screen()}")
        // TRN-049: a waiting suggestion locks the game's actions; she declines it, and the choice goes on
        board = alice.shown.board.board
        assertIs<GameDevice.Acted.Sent>(alice.game!!.answerSuggestion(false))
        waitUntil("her answer on her table: a new board") { alice.shown.board.board !== board && alice.shown.locked == false }
        thePanelUpOnTheNewBoard()
        assertTrue(alice.shows(chosen), "the Mountain still picked: ${alice.screen()}")
        okFindsTheMountain(mountains)
    }
}
