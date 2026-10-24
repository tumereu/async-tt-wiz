package org.asyncttwiz.android

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
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
class TableLayoutScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun capture(landscape: Boolean, populated: Boolean) {
        val initial = if (populated) LiveTableFixture.play() else TableFixture.play(library = 0)
        val stackCard = initial.board.cards.firstOrNull { it.containerId == "a/HAND" }
        val exileCard = initial.board.cards.firstOrNull { it.containerId == "b/LIBRARY" }
        val transfers = if (populated) mapOf(stackCard!!.id to "stack", exileCard!!.id to "b/EXILE") else emptyMap()
        val play = initial.copy(board = initial.board.copy(
            players = initial.board.players.map { player -> player.copy(life = if (player.accountId == "a") "-105" else "120",
                counters = if (populated) CounterColor.entries.associateWith { color -> when (color) {
                    CounterColor.RED -> "-2"; CounterColor.BLUE -> "3"; CounterColor.GREEN -> "";
                    CounterColor.YELLOW -> "12"; CounterColor.PURPLE -> "X"; CounterColor.GRAY -> "0"
                } } else mapOf(CounterColor.RED to "0", CounterColor.BLUE to "")) },
            cards = initial.board.cards.map { card -> transfers[card.id]?.let { card.copy(containerId = it, covered = false) } ?: card },
            containers = initial.board.containers.map { c -> c.copy(cards = c.cards.filterNot { it in transfers } +
                transfers.filterValues { it == c.id }.keys) }))
        val toolkit = TableToolkitController("a", { play }, { true }, { _, _ -> true })
        compose.setContent {
            MaterialTheme {
                TableSurface(LiveTableFixture.game, play, "a", landscape, Modifier.fillMaxSize(),
                    connected = true, onIntent = toolkit::handle, toolkit = toolkit,
                    runtime = TableRuntimeHooks(navigation = {}, chat = {}, history = {}, tree = {}))
            }
        }
        compose.waitForIdle()
        val output = File("build/outputs/roborazzi/layout-${if (populated) "populated" else "empty"}-${if (landscape) "landscape" else "portrait"}.png")
        output.parentFile!!.mkdirs()
        compose.onRoot().captureRoboImage(output.path)
        val image = ImageIO.read(output)
        assertEquals(if (landscape) 892 else 412, image.width)
        assertEquals(if (landscape) 412 else 892, image.height)
        val scene = TablePresentation.scene(LiveTableFixture.game, play, "a", image.width.toFloat(), image.height.toFloat(), landscape)
        assertRenderedLandsBands(image, scene, "a")
        val region = scene.stackRegion!!
        assertEquals(0xffeee3f5.toInt(), image.getRGB((region.x + 2).toInt(), (region.y + 2).toInt()))
        assertEquals(if (landscape || populated) 12 else 0, scene.items.count { it.kind == TableItemKind.COUNTER })
        // White life glyphs must be centered, not anchored to the right edge (including minus signs).
        for (life in scene.items.filter { it.kind == TableItemKind.LIFE }) {
            val b = life.bounds
            val pixels = (b.x.toInt() until b.right.toInt()).flatMap { x ->
                (b.y.toInt() until b.bottom.toInt()).filter { y -> image.getRGB(x, y) == 0xffffffff.toInt() }.map { y -> x to y }
            }
            assertTrue(pixels.isNotEmpty())
            assertEquals(b.center.x, (pixels.minOf { it.first } + pixels.maxOf { it.first }) / 2f, 2f)
            assertEquals(b.center.y, (pixels.minOf { it.second } + pixels.maxOf { it.second }) / 2f, 3f)
        }
    }

    @Test fun emptyPortrait() = capture(false, false)
    @Test fun populatedPortrait() = capture(false, true)
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun emptyLandscape() = capture(true, false)
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun populatedLandscape() = capture(true, true)
}
