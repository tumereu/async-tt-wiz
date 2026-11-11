package org.asyncttwiz.e2e

import org.asyncttwiz.android.AppLog
import org.asyncttwiz.android.YOUR_MOVE_CUE
import org.asyncttwiz.protocol.MoveSound
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Owner, 2026-11-11 (TRN-075, LOB-146): the move passing to you plays your "Your move" sound while the game's table is open,
 * the one your account keeps (Settings → Your move sound); at Off none.
 */
class MoveSoundTest : E2e() {
    private fun soundsFor(player: E2ePlayer, sound: MoveSound? = null) = AppLog.text().lines().count {
        "Your move sound: ${sound?.id ?: ""}" in it && "the move passed to ${player.id.take(8)}" in it
    }

    // LOB-146, TRN-075: Alice's account keeps Drop, Bob's Off; the move passing to Alice (Bob's End turn) plays Drop on her
    // phone with its cue; Bob's phone plays nothing, whatever cues it gives
    @Test fun theMovePassingToYouPlaysYourAccountsSound() {
        val (a, b) = ServerSetup.accounts()
        ServerSetup.moveSound(a, MoveSound.Drop)
        ServerSetup.moveSound(b, MoveSound.Off)
        startGame(bobStarts = true, deal = { x, y -> "Mountain" in hand(x) && "Mountain" in hand(y) })
        // The app log is the process's: what earlier tests' phones of these accounts played is left out
        val bobBefore = soundsFor(bob)
        val aliceBefore = soundsFor(alice)
        val aliceDropsBefore = soundsFor(alice, MoveSound.Drop)
        bothOpen()
        alice.tap("Keep")
        bob.tap("Keep")
        waitUntil("Bob's Main 1") { bob.inOwnMain() }
        waitUntil("both settled") { alice.settled(this) && bob.settled(this) }
        // Her open table has read her account's sound from the server
        waitUntil("Alice's sound read") { "Your move sound of ${alice.id.take(8)} from the server: drop" in AppLog.text() }
        val cues = alice.shownTexts.count { it == YOUR_MOVE_CUE }
        val drops = soundsFor(alice, MoveSound.Drop)
        bob.hold("To combat")
        waitUntil("the move passed to Alice, her cue given") {
            if (alice.status is org.asyncttwiz.protocol.game.GameDevice.Status.Stopped && alice.shownTexts.count { it == YOUR_MOVE_CUE } > cues)
                alice.once { alice.stepArrow()?.let(alice::tap) }
            alice.inOwnMain() && alice.shownTexts.count { it == YOUR_MOVE_CUE } > cues
        }
        assertTrue(soundsFor(alice, MoveSound.Drop) > drops, "her account's Drop played with the cue: ${AppLog.text().lines().filter { "Your move sound" in it }}")
        assertTrue(soundsFor(alice) - aliceBefore == soundsFor(alice, MoveSound.Drop) - aliceDropsBefore, "never another sound for her")
        assertFalse(soundsFor(bob) > bobBefore, "Bob's Off plays nothing")
    }
}
