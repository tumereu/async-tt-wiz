package org.asyncttwiz.e2e

import androidx.compose.ui.test.performClick
import arcanum.api.TurnStep
import arcanum.engine.input.Input
import org.asyncttwiz.android.EngineGame
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.GameEvent
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Owner problem report DY6VCM (0.2.0-beta4): Alice attacked and waited for Bob's blocks, her phone having assumed his
 * pass right after the attack; Bob pumped his creature there instead. Her phone said "Bob responded — your later
 * actions were taken back" though nothing of hers but that assumed pass was taken back. A branch that takes back only
 * the phone's own passes and assumptions says just "Bob responded", with no rewind animation when the board shown
 * doesn't change (HIS-097).
 */
class BranchNoteTest : E2e() {
    // HIS-097, HIS-096, TRN-070: Bob plays a Mountain and an Ash Hound in his first turn; Alice plays a Mountain and an
    // Ember Dasher (haste) and attacks with it, her phone assuming Bob's pass right after the attack (never a wait) so
    // that she waits for his blocks; Bob pumps his Hound at that point instead. Alice's table goes back to her attack
    // with the note "Bob responded" (no take-back clause: only the assumed pass went), without the take-back's rewind
    // look (nothing on the board moved back), then shows Bob's pump; her attack stays
    @Test fun aBranchTakingBackOnlyAnAssumedPassJustSaysTheyResponded() {
        startGame(TestDeck("Dashers", listOf(TestCards.mountain to 20, TestCards.dasher to 20)),
            TestDeck("Hounds", listOf(TestCards.mountain to 20, TestCards.ashHound to 20)), bobStarts = true,
            deal = { a, b -> "Mountain" in hand(a) && "Ember Dasher" in hand(a) && "Mountain" in hand(b) && "Ash Hound" in hand(b) })
        bothOpen()
        alice.tap("Keep")
        bob.tap("Keep")
        waitUntil("Bob's Main 1", along = listOf(alice)) { bob.inOwnMain() }
        bob.playFromHand("Mountain", this)
        waitUntil("Bob's Mountain", along = listOf(alice)) { bob.field("Mountain").isNotEmpty() && bob.inOwnMain() }
        bob.playFromHand("Ash Hound", this)
        waitUntil("Bob's Hound", along = listOf(alice)) { bob.field("Ash Hound").isNotEmpty() && bob.inOwnMain() }
        bob.hold("To combat")
        waitUntil("Alice's Main 1", along = listOf(bob)) { alice.inOwnMain() }
        alice.playFromHand("Mountain", this)
        waitUntil("Alice's Mountain", along = listOf(bob)) { alice.field("Mountain").isNotEmpty() && alice.inOwnMain() }
        alice.playFromHand("Ember Dasher", this)
        waitUntil("Alice's Dasher", along = listOf(bob)) { alice.field("Ember Dasher").isNotEmpty() && alice.inOwnMain() }

        // Every board Alice's table shows from here on, and whether it ever had timeline mode's look
        val boards = mutableListOf<EngineGame.Shown>()
        var timelineLook = false
        everyRun += {
            alice.game?.shown?.let { s -> if (boards.lastOrNull()?.version != s.version) boards += s }
            if (alice.showsTimelineLook()) timelineLook = true
        }
        alice.tap("To combat")
        waitUntil("Alice declaring attackers") { alice.state.turn.step == TurnStep.DeclareAttackers }
        alice.stageAttacker("Ember Dasher", this)
        waitUntil("the Dasher staged") { alice.stepArrow() == "Attack (1)" }
        alice.tap("Attack (1)")
        // Her phone assumed Bob's pass after the attack: she waits for his blocks
        waitUntil("Alice waiting for Bob's blocks") { alice.showsControl("Assume no blockers") }
        fun assumedPass(e: GameEvent) = e.assumed?.assumedBy == alice.me && e.assumed?.input == Input.PassPriority(bob.me)
        val assumed = alice.engine { (device.session.events + device.session.pending).last(::assumedPass) }

        // Bob, at that point (his pause after her attack: he could respond), pumps his Hound instead
        waitUntil("Bob able to act after Alice's attack") {
            bob.settled(this) && bob.state.turn.step == TurnStep.DeclareAttackers && bob.state.priority.holder == bob.me &&
                (bob.status == GameDevice.Status.YourMove || bob.status is GameDevice.Status.Stopped)
        }
        val hound = bob.field("Ash Hound").single()
        bob.battlefieldCard("Ash Hound").performClick()
        fun pump(e: GameEvent) = e.author == bob.id && e.engine?.steps.orEmpty().any { (it.input as? Input.Activate)?.source == hound }
        waitUntil("Bob's pump on the server's main line") {
            bob.engine { device.session.pending.isEmpty() && device.session.events.any(::pump) }
        }

        waitUntil("Alice told Bob responded") { alice.told("${bob.name} responded") }
        // HIS-106: her table stops at the branch point; her tap plays Bob's pump back
        alice.continuesPastBranchStop(this)
        waitUntil("Bob's pump shown on Alice's table") { alice.shown.held.any(::pump) && !alice.shown.behind }
        assertFalse(alice.told("taken back"), "no take-back clause: only her phone's assumed pass was taken back: ${alice.shownTexts}")
        assertTrue(alice.engine { device.session.events }.none { it.id == assumed.id }, "the assumed pass is discarded")
        val back = boards.firstOrNull { it.transition?.undone?.branch == true }
        assertTrue(back == null || back.transition!!.steps.isEmpty(), "no rewind animation: nothing on the board moved back")
        assertFalse(timelineLook, "no rewind look")
        assertTrue(alice.state.combat?.attackers?.isNotEmpty() == true, "her attack stays")
        assertTrue(alice.leaks.leaks.isEmpty() && bob.leaks.leaks.isEmpty())
    }
}
