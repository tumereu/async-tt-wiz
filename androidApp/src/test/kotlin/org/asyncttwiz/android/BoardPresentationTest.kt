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
class BoardPresentationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun compactStackAndNoteChipsAtNormalCardSize() = render(1f)
    @Test fun noteChipsScaleWithWorldZoom() = render(2f)

    private fun render(zoom: Float) {
        val initial = LiveTableFixture.play()
        val source = "a/BATTLEFIELD/4"
        val library = initial.board.containers.single { it.id == "a/LIBRARY" }
        val shown = library.cards.first()
        val entries = (1..3).map { "effect/$it" }
        val play = initial.copy(board = initial.board.copy(
            cards = initial.board.cards.map { card -> if (card.id in listOf(source, shown, "a/GRAVEYARD/0"))
                card.copy(note = "Shield until end of turn", counters = mapOf(CounterColor.BLUE to "2")) else card },
            containers = initial.board.containers.map { if (it.zone == PlayZone.STACK) it.copy(cards = entries) else it },
            stackEffects = entries.map { StackEffect(it, source) },
            contentsDisplays = listOf(ContentsDisplay("look", "a", library.id, ContentsSelection.TOP, 1, false,
                listOf(shown), revealedIds = setOf(shown)))))
        val toolkit = TableToolkitController("a", { play }, { true }, { _, _ -> true })
        toolkit.layoutState.value = TableLayoutState(camera = TableCamera(x = if (zoom > 1f) -100f else 0f, zoom = zoom))
        compose.setContent { MaterialTheme {
            TableSurface(LiveTableFixture.game, play, "a", false, Modifier.fillMaxSize(), toolkit = toolkit)
        } }
        compose.waitForIdle()
        val scene = TablePresentation.scene(LiveTableFixture.game, play, "a", 412f, 892f, false, toolkit.layoutState.value)
        val pile = scene.items.single { it.id == "stack" }
        val drawings = scene.items.filter { it.viewport == "stack" && it.kind == TableItemKind.CARD }
        assertEquals(1, drawings.size)
        assertEquals(4f, scene.stackRegion!!.right - drawings.maxOf { it.bounds.right }, .001f)
        assertTrue(drawings.all { it.bounds.intersection(pile.stackBadge) == null }, "Count must not cover names")
        assertEquals(pile, scene.hit(pile.stackBadge.center))
        val panel = scene.items.single { it.cardId == shown }
        assertTrue(panel.panelRevealed)
        assertEquals("Shield until end of turn", panel.note)
        assertFalse(panel.covered)
        val graveyard = scene.items.single { it.id == "a/GRAVEYARD" }
        assertEquals("Shield until end of turn", graveyard.note)
        assertEquals("Forest", graveyard.faceTitle)

        val output = File("build/outputs/roborazzi/board-notes-stack-${zoom.toInt()}x.png")
        output.parentFile.mkdirs()
        compose.onRoot().captureRoboImage(output.path)
        val image = ImageIO.read(output)
        for (item in listOf(panel, scene.items.single { it.cardId == source })) {
            val b = item.bounds
            val z = b.width / 28f
            assertTrue(item.visible.any { it.contains(TablePoint(b.x + 10 * z, b.y + 14 * z)) })
            val pixels = (3..22).flatMap { x -> (11..16).map { y ->
                image.getRGB((b.x + x * z).toInt(), (b.y + y * z).toInt()) and 0xffffff } }
            assertTrue(pixels.count { it == 0xffffff } > 20, "White note chip at $zoom zoom")
            assertTrue(pixels.any { it and 0xff < 160 && (it shr 8 and 0xff) < 160 }, "Dark note text at $zoom zoom")
        }
        val concealed = play.copy(board = play.board.copy(cards = play.board.cards.map {
            if (it.id == source) it.copy(covered = true, explicitlyCovered = true) else it }))
        val hidden = TablePresentation.scene(LiveTableFixture.game, concealed, "a", 412f, 892f, false)
            .items.single { it.cardId == source }
        assertTrue(hidden.covered)
        assertEquals("", hidden.note)
    }
}
