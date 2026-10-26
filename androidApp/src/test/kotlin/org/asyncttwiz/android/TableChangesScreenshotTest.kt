package org.asyncttwiz.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
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
class TableChangesScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test fun accumulatedTagsAndHighlightsAtAMidAnimationFrame() {
        val play = LiveTableFixture.menuPlay()
        val scene = TablePresentation.scene(LiveTableFixture.game, play, "a", 412f, 892f, false)
        val card = scene.items.first { it.kind == TableItemKind.CARD && it.zone == PlayZone.BATTLEFIELD && it.owner == "a" && !it.covered }
        val changes = TableChanges { 0L }
        compose.setContent { MaterialTheme {
            TableSurface(LiveTableFixture.game, play, "a", false, runtime = TableRuntimeHooks(changes = changes))
        } }
        compose.waitForIdle()
        fun capture(name: String): java.awt.image.BufferedImage {
            val file = File("build/outputs/roborazzi/$name.png")
            requireNotNull(file.parentFile).mkdirs(); compose.onRoot().captureRoboImage(file.path)
            return ImageIO.read(file)
        }
        val before = capture("changefx-before")
        compose.mainClock.autoAdvance = false
        compose.runOnIdle {
            fun apply(vararg fields: GameplayFieldChange) = changes.apply(TableChangeBatch(listOf(TableAppliedEvent(
                GameplayMutation("event", "root", "branch", "request", "a", null, PlayAction.PassAttention,
                    GameplayTransition(fields = fields.toList()))))))
            repeat(3) { apply(GameplayFieldChange("a", GameplayField.LIFE, "20", "19")) }
            apply(GameplayFieldChange("b", GameplayField.LIFE, "20", "22"),
                GameplayFieldChange(card.cardId!!, GameplayField.CORNER, "2/2", "3/3"),
                GameplayFieldChange(card.cardId!!, GameplayField.NOTE, "old", "new"),
                GameplayFieldChange("turn", GameplayField.STEP, "UPKEEP", "DRAW"))
            assertEquals("-3", changes.active.getValue(TableChangeKey("a", TableChangeKind.LIFE)).tag)
        }
        repeat(3) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
        compose.mainClock.advanceTimeBy(300); compose.waitForIdle()
        val during = capture("changefx-mid-animation")
        fun changedPixels(bounds: TableBounds): Int {
            var count = 0
            for (x in bounds.x.toInt().coerceAtLeast(0) until bounds.right.toInt().coerceAtMost(before.width))
                for (y in bounds.y.toInt().coerceAtLeast(0) until bounds.bottom.toInt().coerceAtMost(before.height))
                    if (before.getRGB(x, y) != during.getRGB(x, y)) count++
            return count
        }
        for (owner in listOf("a", "b")) assertTrue(changedPixels(scene.items.single { it.id == "life/$owner" }.bounds) > 20)
        assertTrue(changedPixels(card.bounds) > 20)
        compose.mainClock.advanceTimeBy(1_600)
        compose.runOnIdle { assertTrue(changes.active.isEmpty()) }
    }
}
