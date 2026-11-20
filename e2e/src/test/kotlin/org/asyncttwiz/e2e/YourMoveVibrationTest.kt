package org.asyncttwiz.e2e

import org.asyncttwiz.android.AppLog
import org.asyncttwiz.android.YOUR_MOVE_CUE
import org.asyncttwiz.appui.MoveVibration
import org.asyncttwiz.protocol.MoveSound
import org.junit.Test
import org.robolectric.shadows.ShadowVibrator
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Owner report 2026-11-19 (TRN-075): the Your move vibration was never felt on a real phone; it was a Compose haptic tick,
 * which the phone's touch feedback setting mutes (and which is the lightest tick there is). No emulator or Robolectric phone
 * can feel a vibration, so this asserts what can be seen: the phone's vibrator service got a real one-shot vibration
 * (Robolectric's vibrator shadow, which both phones of the JVM share) and the app log names the phone it was for.
 */
class YourMoveVibrationTest : E2e() {
    private fun vibrationsFor(player: E2ePlayer) = AppLog.text().lines().count {
        "Your move vibration: ${MoveVibration.MILLIS} ms (the move passed to ${player.id.take(8)}" in it
    }

    // TRN-075: the move passing to Alice (Bob's End turn) vibrates her phone once with its cue, through the vibrator, even
    // with her "Your move" sound Off; Bob's own End turn vibrates nothing for him
    @Test fun theMovePassingToYouVibratesThePhone() {
        val (a, _) = ServerSetup.accounts()
        ServerSetup.moveSound(a, MoveSound.Off)
        startGame(bobStarts = true, deal = { x, y -> "Mountain" in hand(x) && "Mountain" in hand(y) })
        bothOpen()
        alice.tap("Keep")
        bob.tap("Keep")
        waitUntil("Bob's Main 1") { bob.inOwnMain() }
        waitUntil("both settled") { alice.settled(this) && bob.settled(this) }
        val cues = alice.shownTexts.count { it == YOUR_MOVE_CUE }
        val hers = vibrationsFor(alice)
        val his = vibrationsFor(bob)
        ShadowVibrator.reset()
        bob.hold("To combat")
        waitUntil("the move passed to Alice, her cue given") {
            if (alice.status is org.asyncttwiz.protocol.game.GameDevice.Status.Stopped && alice.shownTexts.count { it == YOUR_MOVE_CUE } > cues)
                alice.once { alice.stepArrow()?.let(alice::tap) }
            alice.inOwnMain() && alice.shownTexts.count { it == YOUR_MOVE_CUE } > cues
        }
        assertTrue(vibrationsFor(alice) > hers, "her phone asked for the vibration: ${AppLog.text().lines().filter { "Your move vibration" in it }}")
        val vibrator = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
            .getSystemService(android.os.VibratorManager::class.java).defaultVibrator
        assertEquals(MoveVibration.MILLIS, org.robolectric.Shadows.shadowOf(vibrator).milliseconds, "a real one-shot reached the vibrator service")
        assertEquals(his, vibrationsFor(bob), "his own End turn vibrates nothing for him")
    }
}
