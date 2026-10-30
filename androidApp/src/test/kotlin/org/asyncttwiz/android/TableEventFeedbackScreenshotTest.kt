package org.asyncttwiz.android

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import org.asyncttwiz.protocol.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class TableEventFeedbackScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test fun chipsAreOpaqueOnBothRollersHalvesStackedTouchTransparentAndExpire() {
        val play = LiveTableFixture.menuPlay()
        val scene = TablePresentation.scene(LiveTableFixture.game, play, "a", 412f, 892f, false)
        val feedback = TableEventFeedback { 0L }
        var taps = 0
        compose.setContent { MaterialTheme {
            Box(Modifier.fillMaxSize().background(Color.White).pointerInput(Unit) { detectTapGestures { taps++ } }) {
                TableEventFeedbackOverlay(feedback, "a", "a", scene, { scene }, mapOf("a" to "A", "b" to "B"))
            }
        } }
        compose.waitForIdle(); compose.mainClock.autoAdvance = false
        compose.runOnIdle {
            fun event(id: String, author: String, action: PlayAction) = TableAppliedEvent(GameplayMutation(
                id, "root", "branch", "request", author, null, action, GameplayTransition()))
            feedback.apply(TableChangeBatch(listOf(event("one", "a", PlayAction.RollDie("20", 14)),
                event("two", "a", PlayAction.RollDie("6", 2)), event("three", "b", PlayAction.FlipCoin(CoinFace.HEADS)))))
        }
        repeat(3) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
        compose.onNodeWithText("d20 → 14").assertIsDisplayed()
        compose.onNodeWithText("d6 → 2").assertIsDisplayed()
        compose.onNodeWithText("Heads / win").assertIsDisplayed()
        val file = File("build/outputs/roborazzi/table-rolls-both-players.png")
        requireNotNull(file.parentFile).mkdirs()
        compose.onRoot().captureRoboImage(file.path)
        val image = ImageIO.read(file)
        for (field in scene.fields) {
            val fill = (if (field.owner == "a") OwnTableColor else OpponentTableColor).toArgb()
            val pixels = (field.bounds.x.toInt() until field.bounds.right.toInt()).sumOf { x ->
                (field.bounds.y.toInt() until field.bounds.bottom.toInt()).count { y -> image.getRGB(x, y) == fill }
            }
            assertTrue(pixels > 1_000, "Opaque result chip must be on ${field.owner}'s battlefield half")
        }
        val own = scene.fields.single { it.owner == "a" }.bounds.center
        compose.onRoot().performTouchInput { click(Offset(own.x, own.y)) }
        compose.runOnIdle { assertEquals(1, taps) }
        compose.mainClock.advanceTimeBy(3_100); compose.waitForIdle()
        compose.runOnIdle { assertTrue(feedback.rolls.isEmpty()) }
        compose.onNodeWithText("d20 → 14").assertDoesNotExist()
        val stopped = play.copy(currentNodeId = "roll", currentGroup = GameplayGroup("group", "a", "roll", "roll", "random", null, "1"))
        val roll = GameplayMutation("roll", "root", "branch", "request", "a", null, PlayAction.RollDie("20", 14), GameplayTransition())
        compose.runOnIdle {
            feedback.apply(TableChangeBatch(jump = true))
            feedback.present(stopped, true, mapOf(roll.id to roll))
            assertEquals(1, feedback.stoppedRolls.size)
        }
        repeat(3) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
        compose.mainClock.advanceTimeBy(10_000); compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/table-roll-stopped.png")
        compose.onNodeWithText("d20 → 14").assertIsDisplayed()
        compose.runOnIdle { feedback.present(play, true, emptyMap()) }
        repeat(3) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
        compose.onNodeWithText("d20 → 14").assertDoesNotExist()
    }

    @Test fun lingeringPingDrawsOnlyUnnumberedRingsAndFadesWithoutAnInputNode() {
        val play = LiveTableFixture.menuPlay()
        val scene = TablePresentation.scene(LiveTableFixture.game, play, "a", 412f, 892f, false)
        val target = PingTarget(PingTargetKind.PLAYER, "a")
        val point = assertNotNull(TableAnnotationGeometry(scene, "a").endpoint(target))
        val reference = ReferencedEventContext(EventReference(play.id, "roll"), "v")
        val record = RecordedPingChange(AnnotationRecording(play.id, "branch", "roll", "ping", "group", "request", "a"),
            reference, target, null, PingPresence(target, "7", "ping"))
        val feedback = TableEventFeedback { 0L }
        feedback.present(play.copy(currentGroup = GameplayGroup("group", "a", "roll", "ping", "random", null, "2"),
            annotations = EventAnnotationState(pings = listOf(record))), false, emptyMap())
        feedback.present(play, false, emptyMap())
        compose.mainClock.autoAdvance = false
        compose.setContent { Box(Modifier.fillMaxSize().background(Color.White)) {
            TableEventFeedbackOverlay(feedback, "a", "a", scene, { scene })
        } }
        repeat(3) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
        fun capture(name: String): java.awt.image.BufferedImage {
            val file = File("build/outputs/roborazzi/$name.png")
            requireNotNull(file.parentFile).mkdirs(); compose.onRoot().captureRoboImage(file.path)
            return ImageIO.read(file)
        }
        val held = capture("table-ping-linger-held")
        assertEquals(Color.White.toArgb(), held.getRGB(point.x.toInt(), point.y.toInt()), "No numbered badge at the ghost centre")
        fun ringPixels(image: java.awt.image.BufferedImage) = ((point.x - 12).toInt()..(point.x + 12).toInt()).sumOf { x ->
            ((point.y - 12).toInt()..(point.y + 12).toInt()).count { y -> image.getRGB(x, y) == OwnTableColor.toArgb() }
        }
        assertTrue(ringPixels(held) > 20)
        compose.onAllNodes(hasClickAction()).assertCountEquals(0)
        compose.onAllNodes(hasContentDescription("Ping", substring = true)).assertCountEquals(0)
        compose.mainClock.advanceTimeBy(2_750); compose.waitForIdle()
        assertEquals(0, ringPixels(capture("table-ping-linger-fading")), "Ghost ring should no longer be opaque during fade")
        compose.mainClock.advanceTimeBy(400); compose.waitForIdle()
        compose.runOnIdle { assertTrue(feedback.pings.isEmpty()) }
    }
}
