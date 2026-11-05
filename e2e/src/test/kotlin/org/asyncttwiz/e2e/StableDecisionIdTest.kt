package org.asyncttwiz.e2e

import arcanum.engine.Engine
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.state.GameState
import org.asyncttwiz.android.AppLog
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.Placement
import org.asyncttwiz.protocol.game.TableChange
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Stable decision identity (owner, 2026-11-05): an engine decision's id depends only on what is asked, of whom, and how
 * many inputs the game has had before it on its line, so the same question asked again after an undo or a new line's
 * reshuffle is the same decision, on the phone and on the server alike.
 */
class StableDecisionIdTest : E2e() {
    private val held = HeldWorker()
    private val deck = TestDeck("Sparks", listOf(TestCards.mountain to 22, TestCards.spark to 12, TestCards.glimmer to 6))

    /** Spark's cast with Alice's own answers (its first target, the suggested payment), as the cast panel makes them on
     * the board shown. */
    private fun spark(s: GameState): List<Input> {
        val cast = assertNotNull(Engine.options(s, alice.me).filterIsInstance<Input.Cast>().firstOrNull(), "a Storm Spark castable")
        val copy = s.deepCopy()
        val inputs = mutableListOf<Input>(cast)
        var input: Input = cast
        repeat(10) {
            Engine.perform(copy, input).changes.forEach(copy::apply)
            val d = copy.pending?.decision ?: return inputs
            if (d is Decision.Random || Engine.decider(copy, d.player) != alice.me) return inputs
            input = if (d is Decision.ManaPayment) Input.Answer(d.id, Answer.ManaPayment(d.suggestion))
            else Engine.options(copy, alice.me).filterIsInstance<Input.Answer>().filter { it.decision == d.id }.first()
            inputs += input
        }
        return inputs
    }

    private fun aliceCast(): GameDevice.Acted = alice.game!!.send(spark(alice.state))

    // STK-024, HIS-084, problem report GXKJSE (owner, 2026-11-05): Alice casts Storm Spark (its target and payment answered
    // on the board shown, as the cast panel answers them), undoes it and casts it again: the event after the undo starts
    // a new line with the libraries' reshuffle first, and her answers name the same decisions as the first time; her phone
    // and the server name them alike, so the server takes the cast
    @Test fun castUndoAndTheSameCastAgainAnswerTheSameDecisions() {
        startGame(deck, deck, deal = { a, b -> "Mountain" in hand(a) && "Storm Spark" in hand(a) && "Mountain" in hand(b) })
        bothOpen()
        bothKeep(bobAway = true)
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain on the battlefield") { alice.field("Mountain").isNotEmpty() && alice.inOwnMain() }
        val first = spark(alice.state)
        assertTrue(first.any { it is Input.Answer }, "the cast answers decisions: $first")
        assertFalse(alice.game!!.send(first) is GameDevice.Acted.Refused)
        waitUntil("the Spark cast, on the server") { alice.state.stack.isNotEmpty() && alice.engine { device.session.pending.isEmpty() } }
        alice.tap("Undo")
        waitUntil("the Spark back in Alice's hand") { alice.state.stack.isEmpty() && alice.hand("Storm Spark").isNotEmpty() }
        val again = spark(alice.state)
        assertEquals(first, again, "the same cast names the same decisions")
        assertFalse(alice.game!!.send(again) is GameDevice.Acted.Refused)
        waitUntil("the server taking the cast again") {
            val head = alice.engine { device.session.head.takeIf { device.session.pending.isEmpty() } }
            head != null && alice.state.stack.isNotEmpty() && serverGame()?.headId == head
        }
        val recast = alice.engine { device.session.events }.last { e -> e.author == alice.id && e.engine?.steps.orEmpty().any { it.input is Input.Cast } }
        assertNotNull(recast.startsLine, "the cast after the undo starts the new line")
        assertFalse(alice.refusalSeen, "no refusal")
    }

    /** Alice and Bob live, Bob first; both keep, Bob plays a Mountain in his turn and ends it; Alice in her Main 1. */
    private fun bobsMountainThenAlicesMain() {
        startGame(deck, deck, bobStarts = true, aliceWorker = held,
            deal = { a, b -> "Mountain" in hand(a) && "Mountain" in hand(b) && "Storm Spark" in hand(a) })
        bothOpen()
        alice.tap("Keep")
        bob.tap("Keep")
        waitUntil("Alice's Main 1", along = listOf(alice, bob)) { alice.inOwnMain() }
        assertTrue(alice.field("Mountain", owner = bob.me).isNotEmpty(), "Bob's Mountain")
    }

    /** Bob moves his Mountain on his battlefield (a table-only event), on his phone and the server. */
    private fun bobMovesHisMountain(x: Int) {
        // He moves it on the board his table shows: the newest his phone has
        waitUntil("Bob's table showing his phone's newest board") {
            bob.engine { device.session.tip to device.session.behind } == (bob.shown.tip to false)
        }
        val mountain = bob.field("Mountain").first()
        val before = bob.engine { device.model.table.placements[mountain] }
        assertIs<GameDevice.Acted.Sent>(bob.game!!.table(listOf(TableChange.Placed(mountain, before, Placement(x, 0)))))
        waitUntil("Bob's move on the server") { bob.engine { device.session.pending.isEmpty() } }
    }

    // SYS-011, problem report GXKJSE (owner, 2026-11-05: stable decision identity): Alice casts Storm Spark (its target
    // and payment answered on the board her screen shows) while her phone, not her screen yet, has had Bob move his
    // Mountain and take that back (his free undo: a new line is due). Nothing but table-only events changed, so her cast
    // is made on the newer board, where its event starts the new line with the libraries' reshuffle first: her answers
    // still name the decisions asked there, and the server takes it, with no note
    @Test fun aCastMadeOnTheNewerBoardWhereItsEventReshufflesIsTaken() {
        bobsMountainThenAlicesMain()
        alice.playFromHand("Mountain", this)
        waitUntil("Alice's Mountain on the server") {
            alice.field("Mountain").isNotEmpty() && alice.inOwnMain() && alice.engine { device.session.pending.isEmpty() }
        }
        val shown = alice.shown
        val inputs = spark(alice.state)
        held.holding = true
        bobMovesHisMountain(4)
        val lines = bob.engine { device.lines }
        assertIs<GameDevice.Undo.Free>(bob.game!!.undo())
        waitUntil("Bob's undo on Alice's phone") { alice.engine { device.lines > lines && !device.session.behind } }
        assertEquals(shown.version, alice.shown.version, "her screen still shows the board from before")
        assertFalse(alice.game!!.send(inputs) is GameDevice.Acted.Refused, "her cast is made")
        held.release()
        assertTrue(AppLog.text().lines().any { "Own send made on board" in it && "the same decision" in it }, "made on the newer board")
        fun cast() = alice.engine { device.session.events + device.session.pending }
            .lastOrNull { e -> e.author == alice.id && e.engine?.steps.orEmpty().any { it.input is Input.Cast } }
        waitUntil("the server taking her cast") {
            cast() != null && alice.engine { device.session.pending.isEmpty() } && serverGame()?.headId == alice.engine { device.session.head }
        }
        val event = assertNotNull(cast())
        assertNotNull(event.startsLine, "her cast starts the new line")
        assertTrue(event.engine!!.changes.any { it is arcanum.engine.change.Change.Reorder }, "the libraries reshuffled first")
        assertFalse(alice.refusalSeen, "no refusal note")
    }
}
