package org.asyncttwiz.e2e

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.performClick
import org.asyncttwiz.android.EngineGame
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.PlaySettings
import org.asyncttwiz.protocol.game.ResponseMode
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Owner, 2026-11-05: countering a spell lower on the Stack. Alice (Full control) casts Idle Glimmer (A), keeps priority
 * and casts Ember Glint (B); Bob, at his pause with both on the Stack, casts Quench Spark (a single-target counterspell)
 * at A: tapping the glowing Stack opens it in place as its list, both spells glowing as legal targets, and he picks A
 * there. B resolves, A is countered. In the variant Bob is away: Alice's device assumes his passes and A and B resolve;
 * Bob comes back and counters A at his playback's stop, a branch that takes Alice's table back.
 */
class CounterLowerSpellTest : E2e() {
    private val aliceDeck = TestDeck("Glimmers and Glints", listOf(TestCards.mountain to 20, TestCards.glimmer to 10, TestCards.glint to 10))
    private val bobDeck = TestDeck("Quenches", listOf(TestCards.mountain to 20, TestCards.quench to 20))

    /** Alice's engine work, passed through; [HeldWorker.observe] sees every board her table is given. */
    private val aliceEngine = HeldWorker()

    private fun start(bobAway: Boolean) {
        startGame(aliceDeck, bobDeck, aliceSettings = PlaySettings(alwaysAssumeResolves = bobAway), aliceWorker = aliceEngine,
            deal = { a, b -> "Idle Glimmer" in hand(a) && "Ember Glint" in hand(a) && "Quench Spark" in hand(b) })
        bothOpen()
        bothKeep(bobAway)
    }

    /** The Stack's spells by name on [player]'s table, bottom first. */
    private fun stack(player: E2ePlayer): List<String?> = player.state.let { s -> s.stack.map { cardName(s, it) } }

    private fun graveyard(player: E2ePlayer, owner: E2ePlayer): List<String?> =
        player.state.let { s -> s.player(owner.me).graveyard.map { cardName(s, it) } }

    private fun aliceHolds(names: List<String>) = alice.status == GameDevice.Status.YourMove && stack(alice) == names &&
        alice.state.priority.holder == alice.me

    /** In her Main 1, Alice turns Full control on, casts A, keeps priority, casts B and passes. Her hand's size before. */
    private fun aliceCastsAThenB(assume: Boolean): Int {
        alice.game!!.settings(PlaySettings(mode = ResponseMode.FullControl, alwaysAssumeResolves = assume))
        waitUntil("Alice's Full control") { alice.shown.settings.mode == ResponseMode.FullControl }
        val hand = alice.state.player(alice.me).hand.size
        alice.playFromHand("Idle Glimmer", this)
        waitUntil("A on the Stack, Alice keeping priority") { aliceHolds(listOf("Idle Glimmer")) }
        alice.playFromHand("Ember Glint", this)
        waitUntil("B on top of A, Alice keeping priority") { aliceHolds(listOf("Idle Glimmer", "Ember Glint")) }
        alice.tap("Resolve")
        return hand
    }

    /**
     * STK-047, STK-048, STK-018: Bob casts Quench Spark; its target bar asks for a spell; the Stack glows (A, below the
     * top, is a legal target) and a tap opens it in place as its list, A and B glowing as legal targets; he taps A's row.
     */
    private fun bobCountersA() {
        bob.playFromHand("Quench Spark", this)
        waitUntil("Bob's target bar") { bob.shows(hasText("Choose target spell") and isHeading()) }
        bob.node(hasContentDescription("Stack, 2 entries")).performClick()
        waitUntil("the Stack open as its list on Bob's table") { bob.showsText("2 · Idle Glimmer") }
        val legal = bob.nodes(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Legal target")).fetchSemanticsNodes()
        assertEquals(2, legal.size, "A and B glow as legal targets: ${bob.screen()}")
        assertTrue(bob.shows(hasText("Choose target spell") and isHeading()), "the target bar stays above the hand")
        // A moment later, as a player taps (the list takes no touch for its first moments)
        run(skip = java.time.Duration.ofMillis(600))
        inputClockCaughtUp()
        bob.text("2 · Idle Glimmer").performClick()
        waitUntil("Bob's Quench on top, the list closed") {
            stack(bob) == listOf("Idle Glimmer", "Ember Glint", "Quench Spark") && !bob.showsText("2 · Idle Glimmer")
        }
    }

    /** Each player passes in turn until the Stack is empty on Alice's table (Alice by her main button, Bob playing along). */
    private fun bothPassUntilTheStackIsEmpty(done: () -> Boolean) {
        waitUntil("the Stack resolved", along = listOf(bob)) {
            if (alice.status == GameDevice.Status.YourMove && alice.state.stack.isNotEmpty()) alice.once { alice.tap("Resolve") }
            done()
        }
    }

    /** Quench countered A and B resolved: on both tables, and all of it on the server. */
    private fun aCounteredAndBResolved(handBefore: Int) {
        bothPassUntilTheStackIsEmpty {
            alice.state.stack.isEmpty() && alice.state.player(alice.me).life == 22 && "Idle Glimmer" in graveyard(alice, alice)
        }
        waitUntil("both tables and the server agreeing", along = listOf(bob)) {
            alice.engine { device.session.pending.isEmpty() } && bob.engine { device.session.pending.isEmpty() } &&
                bob.state.stack.isEmpty() && bob.state.player(alice.me).life == 22
        }
        for (player in listOf(alice, bob)) {
            assertEquals(listOf("Ember Glint", "Idle Glimmer"), graveyard(player, alice).filterNotNull().sorted(),
                "${player.name}: A countered and B resolved into Alice's graveyard")
            assertEquals(listOf("Quench Spark"), graveyard(player, bob), "${player.name}: the Quench resolved")
        }
        assertEquals(handBefore - 2, alice.state.player(alice.me).hand.size, "A was countered: Alice drew nothing")
        assertTrue(alice.leaks.leaks.isEmpty() && bob.leaks.leaks.isEmpty())
    }

    // STK-048, STK-018, STK-047, STK-036: live, both online
    @Test fun bobCountersTheSpellBelowTheTop() {
        start(bobAway = false)
        val hand = aliceCastsAThenB(assume = false)
        waitUntil("Bob's priority with A and B on the Stack") {
            bob.status == GameDevice.Status.YourMove && stack(bob) == listOf("Idle Glimmer", "Ember Glint") &&
                bob.state.priority.holder == bob.me
        }
        bobCountersA()
        aCounteredAndBResolved(hand)
        // The Quench's resolution reads as the Quench resolving, not as the spell it countered
        assertTrue(alice.told("Quench Spark resolved"), "${alice.shownTexts}")
        assertTrue(!alice.told("Idle Glimmer resolved"), "${alice.shownTexts}")
    }

    // STK-048, HIS-096, HIS-095, HIS-097, HIS-106: Bob away; Alice's device assumes his passes, so B and then A resolve;
    // Bob comes back, his playback stops before the assumed pass with both on the Stack, and his Quench at A there is a
    // branch: Alice's table goes back to it and plays it back; then B resolves and A is countered
    @Test fun bobCountersLaterWhileAwayBranchesAndTakesBack() {
        start(bobAway = true)
        val hand = aliceCastsAThenB(assume = true)
        waitUntil("A and B resolved on assumed passes") {
            if (aliceHolds(listOf("Idle Glimmer"))) alice.once { alice.tap("Resolve") }
            alice.state.stack.isEmpty() && alice.state.player(alice.me).life == 22 && "Idle Glimmer" in graveyard(alice, alice) &&
                alice.state.player(alice.me).hand.size == hand - 1 && alice.engine { device.session.pending.isEmpty() }
        }

        // Every board Alice's table is given from here on
        val boards = mutableListOf<EngineGame.Shown>()
        aliceEngine.observe = { alice.game?.shown?.let { s -> if (boards.lastOrNull()?.version != s.version) boards += s } }
        bob.back()
        waitUntil("Bob's playback stopping with A and B on the Stack") {
            if (bob.status == GameDevice.Status.CatchUp) bob.once { bob.tapToWatch() }
            bob.status is GameDevice.Status.Stopped && stack(bob) == listOf("Idle Glimmer", "Ember Glint")
        }
        bobCountersA()
        waitUntil("Alice's table going back to the branch point") {
            boards.any { it.transition?.undone?.branch == true && it.transition!!.backward.isNotEmpty() }
        }
        assertTrue(alice.told("responded — your later actions were taken back"), "HIS-097's note")
        waitUntil("Bob's Quench played back on Alice's table") { stack(alice) == listOf("Idle Glimmer", "Ember Glint", "Quench Spark") }
        aCounteredAndBResolved(hand)
    }
}
