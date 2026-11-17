package org.asyncttwiz.android

import org.asyncttwiz.protocol.game.GameDevice
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Stack animation (owner spec 2026-11-14; HIS-112, HIS-133, HIS-136, STK-058, STK-127, HIS-230, TBL-125, TBL-150, LOB-082, TBL-038): the steps a spell or ability plays on
 * its way to and through the Stack, in order, with their times: the fly-in, the middle hold and the flight to the Stack for
 * a play shown, the target arrows for an entry with targets, then its resolution (its effect's own animations).
 */
class StackScheduleTest {
    private fun steps(schedule: List<StackStepTime>) = schedule.map { it.step }

    // HIS-112, HIS-133, HIS-136, STK-058, STK-127, HIS-230, TBL-125, TBL-150: the other player's Shock at your creature: 0.25 s in, the 1.2 s hold, 0.25 s to the Stack,
    // its arrow (0.25 s growing, then 0.4 s drawn), then the damage; its total before the damage about 2.35 s
    @Test fun theOtherPlayersPlayWithATargetHasAllFiveStepsInOrder() {
        val schedule = StackSchedule.of(shown = true, targets = 1, setting = 1_200, multiplier = 1f)
        assertEquals(listOf(StackStep.FlyIn, StackStep.Hold, StackStep.ToStack, StackStep.Targeting, StackStep.Resolution), steps(schedule))
        assertEquals(listOf(250L, 1_200L, 250L, 650L, null), schedule.map { it.millis })
        assertEquals(2_350L, schedule.sumOf { it.millis ?: 0L })
    }

    // HIS-112, HIS-143: your own casts and activations skip the middle: one move to the Stack (the table's), then their arrows
    @Test fun ownCastsSkipTheMiddleButKeepTheirArrows() {
        val schedule = StackSchedule.of(shown = false, targets = 1, setting = 1_200, multiplier = 1f)
        assertEquals(listOf(StackStep.Targeting, StackStep.Resolution), steps(schedule))
        assertEquals(650L, schedule.first().millis)
    }

    // STK-058: an entry without targets skips the targeting step; LOB-082: at Off (0) no play shows at all
    @Test fun noTargetsSkipsTheArrowsAndOffSkipsTheShow() {
        assertEquals(listOf(StackStep.FlyIn, StackStep.Hold, StackStep.ToStack, StackStep.Resolution),
            steps(StackSchedule.of(shown = true, targets = 0, setting = 1_200, multiplier = 1f)))
        assertEquals(listOf(StackStep.Targeting, StackStep.Resolution), steps(StackSchedule.of(shown = true, targets = 2, setting = 0, multiplier = 1f)))
        assertEquals(listOf(StackStep.Resolution), steps(StackSchedule.of(shown = false, targets = 0, setting = 1_200, multiplier = 1f)))
        assertEquals(0L, StackSchedule.targetingMillis(0, 1f))
    }

    // TBL-038, LOB-082: every time scales with Card move speed but the middle hold, the "Show plays and combat" time
    @Test fun cardMoveSpeedScalesEveryStepButTheHold() {
        assertEquals(listOf(125L, 1_200L, 125L, 325L, null), StackSchedule.of(true, 1, 1_200, 2f).map { it.millis })
        assertEquals(listOf(1_000L, 3_000L, 1_000L, 2_600L, null), StackSchedule.of(true, 1, 3_000, .25f).map { it.millis })
    }

    // STK-058, STK-127, HIS-230: several targets' arrows start 80 ms apart, each growing in 0.25 s; then 0.4 s with all of them drawn
    @Test fun severalArrowsGrowEightyMillisecondsApart() {
        assertEquals(250L + 2 * 80L + 400L, StackSchedule.targetingMillis(3, 1f))
        assertEquals(0f, StackSchedule.arrowProgress(0, 0, 1f))
        assertEquals(.5f, StackSchedule.arrowProgress(125, 0, 1f), .001f)
        assertEquals(0f, StackSchedule.arrowProgress(80, 1, 1f), "the second starts 80 ms after the first")
        assertEquals(.5f, StackSchedule.arrowProgress(205, 1, 1f), .001f)
        assertEquals(1f, StackSchedule.arrowProgress(330, 1, 1f))
        assertEquals(1f, StackSchedule.arrowProgress(1_000, 2, 1f))
        assertEquals(.5f, StackSchedule.arrowProgress(63, 0, 2f), .02f, "at 2× it grows in 125 ms")
    }

    // HIS-112, HIS-138, STK-058, STK-128, TBL-125, TBL-150 (spec: chains): a repeat of the same ability in one chain holds, targets and resolves 35%
    // shorter each time, down to 0.15 s for the hold, 0.1 s for an arrow's growth and 0.15 s for the read
    @Test fun repeatsShortenTheHoldTheArrowsAndTheResolutionDownToTheirFloors() {
        assertEquals(listOf(1_200L, 780L, 507L), (0..2).map { StackSchedule.holdMillis(1_200, it) })
        assertEquals(listOf(250L, 163L, 106L, 100L, 100L), (0..4).map { StackSchedule.arrowGrowMillis(1f, it) })
        assertEquals(listOf(400L, 260L, 169L, 150L, 150L), (0..4).map { StackSchedule.arrowReadMillis(1f, it) })
        assertEquals(listOf(650L, 423L, 275L, 250L), (0..3).map { StackSchedule.targetingMillis(1, 1f, it) })
        assertEquals(1.0, StackSchedule.resolutionShare(0))
        assertEquals(.65, StackSchedule.resolutionShare(1), .0001)
        assertEquals(150.0 / 350.0, StackSchedule.resolutionShare(10), .0001, "a repeat's effect plays in no less than 0.15 s of 0.35 s")
        // At 2× the floors halve too
        assertEquals(listOf(125L, 81L, 53L, 50L), (0..3).map { StackSchedule.arrowGrowMillis(2f, it) })
        // A board wipe's 30 triggers with a target each: well under 15 s for their arrows in all
        assertTrue((0 until 30).sumOf { StackSchedule.targetingMillis(1, 1f, it) } < 8_500)
    }

    // HIS-112, HIS-132, HIS-142: the plays not landed yet are taken off the board; the ones this table ended show (Stack animation step 1)
    @Test fun thePlaysNotLandedAreHidden() {
        val bob = arcanum.engine.state.PlayerId("bob")
        fun play(event: String, n: Int) = GameDevice.PlayShow(event, bob, arcanum.engine.state.ObjectId.StackItem("s$n"),
            GameDevice.PlayKind.Triggered, null)
        val a1 = play("e1", 1); val a2 = play("e1", 2)
        val hold = GameDevice.CombatHold("e0", GameDevice.CombatMoment.Attackers)
        val animation = StackAnimation("alice")
        assertEquals(setOf("entry:${a1.entry}", "entry:${a2.entry}"), animation.hidden(listOf(hold, a1, a2)))
        animation.land(a2)
        assertEquals(setOf("entry:${a1.entry}"), animation.hidden(listOf(hold, a1, a2)), "the top one landed: it shows")
        assertEquals(listOf("entry:${a2.entry}"), animation.landed.toList())
        animation.prune(listOf(a1))
        assertEquals(listOf(a1), animation.hidden(listOf(a1)).map { e -> listOf(a1, a2).first { "entry:${it.entry}" == e } })
        assertTrue(animation.ended.isEmpty(), "the ended play the device let go is forgotten")
    }
}
