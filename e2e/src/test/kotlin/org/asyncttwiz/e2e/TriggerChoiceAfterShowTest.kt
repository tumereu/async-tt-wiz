package org.asyncttwiz.e2e

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import arcanum.engine.decision.Decision
import org.asyncttwiz.android.CardMovePreferences
import org.asyncttwiz.android.HistoryPlaybackPreferences
import org.asyncttwiz.android.PLAY_SHOW_TAG
import org.asyncttwiz.android.StackAnimationProbe
import org.asyncttwiz.appui.AppPlatform
import org.asyncttwiz.protocol.game.AssumeResolves
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.PlaySettings
import org.junit.After
import org.junit.Test
import java.util.Collections
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Owner playtest 2026-11-15: "'You may pay' for an effect was asked before the trigger had animated to its place on the
 * Stack." A trigger that asks its controller to pay as it resolves, put on the Stack by their own action or the other
 * player's: on their table its play shown (HIS-112) lands on the Stack first, and only then does the pay bar open (TBL-125):
 * never while it still flies or shows in the middle.
 */
class TriggerChoiceAfterShowTest : E2e() {
    @After fun forgetProbes() {
        StackAnimationProbe.landed = null
        StackAnimationProbe.resolving = null
    }

    private fun payBar() = alice.shows(hasText("Pay {1}") and isHeading()) && alice.shows(hasContentDescription("Pay on the table"))

    /** What Alice's table's Stack animation does, from now on: the plays landing and the entries leaving her Stack. */
    private fun aliceSteps(): MutableList<String> {
        val steps = Collections.synchronizedList(ArrayList<String>())
        StackAnimationProbe.landed = { me, entry -> if (me == alice.id) steps += "lands $entry" }
        StackAnimationProbe.resolving = { me, entries -> if (me == alice.id) for (e in entries) steps += "resolves $e" }
        return steps
    }

    /** Waits for the pay bar on Alice's table ([bobPlays]: Bob's phone plays along meanwhile), noting each time it shows
     * over a play still shown or before [landings] plays landed. */
    private fun payBarOnceLanded(steps: List<String>, landings: Int, bobPlays: Boolean) {
        val wrong = ArrayList<String>()
        var republished = false
        waitUntil("Alice asked to pay {1}", along = if (bobPlays) listOf(bob) else emptyList()) {
            // While her table plays the board's steps toward the play shown, the same board is published again (on a phone:
            // a status or the other player's presence changing), as in the owner's game
            if (!republished && alice.shown.moments.any { it is GameDevice.PlayShow } && !alice.shows(hasTestTag(PLAY_SHOW_TAG))) {
                republished = true
                requireNotNull(alice.game).settings(alice.shown.settings)
            }
            val asked = payBar()
            if (asked && alice.shows(hasTestTag(PLAY_SHOW_TAG))) wrong += "the pay bar over a play still shown: ${steps.toList()} ${alice.screen()}"
            if (asked && steps.count { it.startsWith("lands ") } < landings) wrong += "the pay bar before the plays landed: ${steps.toList()} ${alice.screen()}"
            asked && alice.state.pending?.decision is Decision.May
        }
        assertTrue(wrong.isEmpty(), wrong.distinct().joinToString("\n"))
        assertEquals(landings, steps.count { it.startsWith("lands ") }, "the plays shown landed on Alice's Stack: $steps")
        for (entry in steps.filter { it.startsWith("lands ") }.map { it.removePrefix("lands ") })
            assertTrue(steps.indexOf("resolves $entry").let { it < 0 || it > steps.indexOf("lands $entry") },
                "nothing leaves Alice's Stack before it landed there: $steps")
    }

    // HIS-112, TBL-125, STK-030 (owner playtest 2026-11-15): Alice casts an Ember Tollkeeper ("When this creature enters, you
    // may pay {1}. If you do, draw a card."), her phone assuming Bob's passes: it resolves and its trigger shows on her table
    // (her own trigger) and resolves at once, asking her to pay once it has landed
    @Test fun aChoiceOfYourOwnTriggerWaitsForItsPlayShownToLand() {
        startGame(TestDeck("Tolls", listOf(TestCards.mountain to 20, TestCards.tollkeeper to 20)),
            aliceSettings = PlaySettings(assume = AssumeResolves.Always),
            deal = { a, _ -> "Mountain" in hand(a) && "Ember Tollkeeper" in hand(a) })
        HistoryPlaybackPreferences(AppPlatform.current, alice.id).showPlaysMillis = 1_500
        // Her cards move at half speed: the board's steps toward the play shown take long enough to be published again meanwhile
        CardMovePreferences(AppPlatform.current, alice.id).multiplier = .5f
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain on the battlefield", along = listOf(bob)) { alice.field("Mountain").isNotEmpty() && alice.inOwnMain() }
        val steps = aliceSteps()
        clockInStep {
            alice.playFromHand("Ember Tollkeeper", this)
            payBarOnceLanded(steps, landings = 1, bobPlays = false)
        }
    }

    // HIS-112, TBL-125, STK-030 (owner playtest 2026-11-15): Alice has an Ember Toll Bell ("At the beginning of your upkeep,
    // you may pay {1}. If you do, draw a card.") and a Mountain; Bob ends his turn, his phone assuming her passes, so his End
    // turn reaches her upkeep, puts her Bell's trigger on the Stack and resolves it up to her choice, all in his one event. On
    // her table the trigger shows and lands first, and only then does the pay bar open
    @Test fun aChoiceOfYourTriggerTheOtherPlayersMoveResolvesWaitsForItsPlayShownToLand() {
        startGame(TestDeck("Bells", listOf(TestCards.mountain to 20, TestCards.bell to 20)),
            TestDeck("Pups", listOf(TestCards.mountain to 20, TestCards.pup to 20)),
            bobSettings = PlaySettings(assume = AssumeResolves.Always),
            deal = { a, b -> "Mountain" in hand(a) && "Ember Toll Bell" in hand(a) && "Ash Pup" in hand(b) })
        HistoryPlaybackPreferences(AppPlatform.current, alice.id).showPlaysMillis = 1_500
        // Her cards move at half speed: the board's steps toward the play shown take long enough to be published again meanwhile
        CardMovePreferences(AppPlatform.current, alice.id).multiplier = .5f
        bothOpen()
        bothKeep(bobAway = false)
        alice.playFromHand("Mountain", this)
        waitUntil("the Mountain on the battlefield", along = listOf(bob)) { alice.field("Mountain").isNotEmpty() && alice.inOwnMain() }
        alice.playFromHand("Ember Toll Bell", this)
        waitUntil("the Bell on Alice's battlefield", along = listOf(bob)) { alice.field("Ember Toll Bell").isNotEmpty() && alice.inOwnMain() }
        alice.hold(requireNotNull(alice.stepArrow()))
        // Bob plays Alice's turn back and answers its stops up to his Main 1
        waitUntil("Bob's Main 1") {
            val inBobsMain = bob.state.turn.active == bob.me && bob.state.turn.step == arcanum.api.TurnStep.PrecombatMain
            when (bob.status) {
                GameDevice.Status.CatchUp -> bob.once { bob.tapToWatch() }
                is GameDevice.Status.Stopped -> if (!inBobsMain) bob.stepArrow()?.takeIf { it != "To combat" }?.let { label -> bob.once { bob.tap(label) } }
                else -> {}
            }
            bob.inOwnMain()
        }
        // Seven cards left in his hand: his cleanup asks nothing of him
        bob.playFromHand("Ash Pup", this)
        waitUntil("Bob's Pup on his battlefield", along = listOf(alice)) { bob.field("Ash Pup").isNotEmpty() && bob.inOwnMain() }
        waitUntil("Alice's table caught up with Bob's Main 1") {
            alice.state.turn.active == bob.me && !alice.shown.behind && alice.shown.show == null && alice.field("Ash Pup", owner = bob.me).isNotEmpty()
        }
        val steps = aliceSteps()
        clockInStep {
            bob.hold(requireNotNull(bob.stepArrow()))
            payBarOnceLanded(steps, landings = 1, bobPlays = false)
        }
    }
}
