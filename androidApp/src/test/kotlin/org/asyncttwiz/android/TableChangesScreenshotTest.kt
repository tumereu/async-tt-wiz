package org.asyncttwiz.android

import android.content.Context
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.test.core.app.ApplicationProvider
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

    private fun capture(name: String, caption: String): java.awt.image.BufferedImage {
        val file = File("$SCREENSHOTS/$name.png")
        requireNotNull(file.parentFile).mkdirs(); compose.onRoot().captureShot(file.path, caption)
        return ImageIO.read(file)
    }

    private fun changedPixels(before: java.awt.image.BufferedImage, after: java.awt.image.BufferedImage, bounds: TableBounds): Int {
        var count = 0
        for (x in bounds.x.toInt().coerceAtLeast(0) until bounds.right.toInt().coerceAtMost(before.width))
            for (y in bounds.y.toInt().coerceAtLeast(0) until bounds.bottom.toInt().coerceAtMost(before.height))
                if (before.getRGB(x, y) != after.getRGB(x, y)) count++
        return count
    }

    private fun overlay(scene: TableScene, changes: TableChanges) {
        val items = scene.items.associateBy { it.id }
        compose.setContent { Box(Modifier.fillMaxSize().background(Color.White)) {
            TableChangeOverlay(changes, "a", scene.appearance, items::get)
        } }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
    }

    private fun animate(changes: TableChanges, transition: GameplayTransition, reverse: Boolean = false) {
        compose.runOnIdle {
            changes.apply(TableChangeBatch(listOf(TableAppliedEvent(GameplayMutation("event", "root", "branch", "request", "a", null,
                PlayAction.PassAttention, transition), reverse))))
        }
        repeat(3) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
        compose.mainClock.advanceTimeBy(64); compose.waitForIdle()
    }

    /** Find the pill's solid pixels, then check its background and the complete glyph span.
     * Exact RGB (rather than screenshot alpha, which is always 255) detects translucent fills. */
    private fun assertOpaquePill(image: java.awt.image.BufferedImage, area: TableBounds, fill: Color, text: String): TableBounds {
        val pixels = buildList {
            for (x in area.x.toInt().coerceAtLeast(0) until area.right.toInt().coerceAtMost(image.width))
                for (y in area.y.toInt().coerceAtLeast(0) until area.bottom.toInt().coerceAtMost(image.height))
                    if (image.getRGB(x, y) == fill.toArgb()) add(x to y)
        }
        assertTrue(pixels.size > 60, "Opaque $text pill missing")
        val left = pixels.minOf { it.first }; val right = pixels.maxOf { it.first }
        val top = pixels.minOf { it.second }; val bottom = pixels.maxOf { it.second }
        assertTrue(left > 0 && top > 0 && right < image.width - 1 && bottom < image.height - 1, "Pill must fit on screen")
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; isFakeBoldText = true }
        assertTrue(right - left + 1 >= paint.measureText(text) + 8f, "Pill must contain full text plus padding")
        // Both solid rows cross the whole pill, over multiple differently coloured underlay stripes.
        for (x in left + 9..right - 9) {
            assertEquals(fill.toArgb(), image.getRGB(x, top + 2), "Solid top of $text at $x")
            assertEquals(fill.toArgb(), image.getRGB(x, bottom - 2), "Solid bottom of $text at $x")
        }
        val ink = java.awt.Color(tableChangeTagInk(fill).toArgb())
        // Fractional anchors anti-alias even the bold glyph edges; count high-contrast coverage,
        // not only perfectly white/black pixels (which can omit the entire final glyph column).
        val columns = (left + 4..right - 4).filter { x -> (top + 3..bottom - 3).any { y ->
            val pixel = java.awt.Color(image.getRGB(x, y))
            val distance = listOf(pixel.red - ink.red, pixel.green - ink.green, pixel.blue - ink.blue).sumOf { it * it }
            distance < 3 * 100 * 100
        } }
        assertTrue(columns.isNotEmpty(), "Bold contrasting text missing")
        val glyphBounds = android.graphics.Rect().also { paint.getTextBounds(text, 0, text.length, it) }
        assertTrue(columns.last() - columns.first() + 1 >= glyphBounds.width() - 2,
            "Full $text glyph width must be drawn, not clipped: ${columns.last() - columns.first() + 1} vs ${glyphBounds.width()}")
        return TableBounds(left.toFloat(), top.toFloat(), (right - left + 1).toFloat(), (bottom - top + 1).toFloat())
    }


    // HIS-031
    @Test fun realStackEntryHighlightsDrawInBothPlaybackDirections() = stackFeedback(projection = false)

    @Test fun projectedStackEntryHighlightsDrawInBothPlaybackDirections() = stackFeedback(projection = true)

    private fun stackFeedback(projection: Boolean) {
        val (initial, id) = ChangeTargetFixture.stack(projection)
        val play = if (!projection) initial else initial.copy(board = initial.board.copy(combat = CombatArea("combat",
            listOf(CombatPair("pair", attackers = listOf(CombatOccurrence("combat-occurrence", initial.board.stackEffects.single().sourceId)))))))
        val scene = ChangeTargetFixture.scene(play)
        val drawing = scene.items.single { it.cardId == id }
        val changes = TableChanges { 0L }
        overlay(scene, changes)
        val entry = if (projection) "an attacking card's ability effect on the Stack" else "a card cast onto the Stack"
        val before = capture("stack-changefx-$projection-before",
            "Change highlight layer alone on white for $entry, before any change plays.")
        val owner = if (!projection) GameplayTransition(fields = listOf(GameplayFieldChange(id, GameplayField.OWNER, "a", "b")))
            else GameplayTransition(combatStack = CombatStackTransition(effects = listOf(StackEffectChange(id,
                play.board.stackEffects.single().copy(controller = "a"), play.board.stackEffects.single()))))
        for (reverse in listOf(false, true)) for ((name, transition) in listOf("owner" to owner)) {
            animate(changes, transition, reverse)
            val change = "controller change"
            val played = "played ${if (reverse) "backwards" else "forward"}"
            val during = capture("stack-changefx-$projection-$name-$reverse", "Change highlight layer alone on white for $entry: " +
                "$change $played, mid-animation.")
            assertTrue(changedPixels(before, during, drawing.bounds) > 10, "$name must draw on the exact Stack entry (reverse=$reverse)")
            if (name == "link") {
                val (a, b) = assertNotNull(tableChangeLinkAnchors(changes.active.values.single().change, scene.items.associateBy { it.id }::get))
                val midpoint = TableBounds((a.x + b.x) / 2 - 4, (a.y + b.y) / 2 - 4, 8f, 8f)
                assertTrue(changedPixels(before, during, midpoint) > 10, "Link glow must reach both rendered endpoints")
            }
            compose.runOnIdle { changes.clear() }
        }
    }

    @Test fun faceDownOwnerBorderPulsesWhileHiddenAnnotationsStayInvisible() {
        val (play, id) = ChangeTargetFixture.stack(projection = false, covered = true)
        val scene = ChangeTargetFixture.scene(play)
        val drawing = scene.items.single { it.cardId == id }
        val changes = TableChanges { 0L }
        overlay(scene, changes)
        val before = capture("covered-changefx-before",
            "Change highlight layer alone on white for a face-down card on the Stack, before any change plays.")
        animate(changes, GameplayTransition(fields = listOf(
            GameplayFieldChange(id, GameplayField.NOTE, "old", "new"),
            GameplayFieldChange(id, GameplayField.CORNER, "1/1", "2/2"),
            GameplayFieldChange(id, GameplayField.COUNTER, "0", "1", CounterColor.BLUE)),
            combatStack = CombatStackTransition(textMarks = listOf(StackTextMarkChange(id, emptySet(), setOf(0))))))
        assertEquals(0, changedPixels(before, capture("covered-changefx-hidden",
            "Face-down Stack card after hidden note, P/T, counter and text mark changes: nothing is highlighted."), TableBounds(0f, 0f, 412f, 892f)))
        compose.runOnIdle { changes.clear() }
        animate(changes, GameplayTransition(fields = listOf(GameplayFieldChange(id, GameplayField.OWNER, "a", "b"))))
        val during = capture("covered-changefx-owner",
            "Face-down Stack card after its controller changes: only the card's border pulses.")
        assertTrue(changedPixels(before, during, drawing.bounds) > 20)
        assertEquals(0, changedPixels(before, during, drawing.bounds.let { TableBounds(it.x + 4, it.y + 4, it.width - 8, it.height - 8) }),
            "Public owner feedback must only pulse the border")
    }


    // HIS-031, TBL-033
    @Test fun accumulatedTagsAndHighlightsAtAMidAnimationFrame() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        CardAppearancePreferences(context).setSize(MIN_CARD_SCALE)
        val play = LiveTableFixture.menuPlay()
        val scene = TablePresentation.scene(LiveTableFixture.game, play, "a", 412f, 892f, false,
            appearance = CardAppearance(cardSize = MIN_CARD_SCALE))
        val card = scene.items.first { it.kind == TableItemKind.CARD && it.zone == PlayZone.BATTLEFIELD && it.owner == "a" && !it.covered }
        val changes = TableChanges { 0L }
        compose.setContent { MaterialTheme {
            TableSurface(LiveTableFixture.game, play, "a", false, runtime = TableRuntimeHooks(changes = changes))
        } }
        compose.waitForIdle()
        val before = capture("changefx-before", "Portrait table at the smallest card size, before any change plays.")
        compose.mainClock.autoAdvance = false
        compose.runOnIdle {
            fun apply(vararg fields: GameplayFieldChange) = changes.apply(TableChangeBatch(listOf(TableAppliedEvent(
                GameplayMutation("event", "root", "branch", "request", "a", null, PlayAction.PassAttention,
                    GameplayTransition(fields = fields.toList()))))))
            repeat(3) { apply(GameplayFieldChange("a", GameplayField.LIFE, "20", "19")) }
            apply(GameplayFieldChange("b", GameplayField.LIFE, "20", "22"),
                GameplayFieldChange(card.cardId!!, GameplayField.CORNER, "2/2", "3/3"),
                GameplayFieldChange(card.cardId!!, GameplayField.COUNTER, "0", "1", CounterColor.RED),
                GameplayFieldChange(card.cardId!!, GameplayField.NOTE, "old", "new"),
                GameplayFieldChange("turn", GameplayField.STEP, "UPKEEP", "DRAW"))
            assertEquals("-3", changes.active.getValue(TableChangeKey("a", TableChangeKind.LIFE)).tag)
        }
        repeat(3) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle() }
        compose.mainClock.advanceTimeBy(300); compose.waitForIdle()
        val during = capture("changefx-mid-animation", "Portrait table at the smallest card size, mid-animation: player A -3 life, player B +2, " +
            "a card's P/T, red counter and note change, step to Draw.")
        fun changedPixels(bounds: TableBounds): Int {
            var count = 0
            for (x in bounds.x.toInt().coerceAtLeast(0) until bounds.right.toInt().coerceAtMost(before.width))
                for (y in bounds.y.toInt().coerceAtLeast(0) until bounds.bottom.toInt().coerceAtMost(before.height))
                    if (before.getRGB(x, y) != during.getRGB(x, y)) count++
            return count
        }
        for (owner in listOf("a", "b")) assertTrue(changedPixels(scene.items.single { it.id == "life/$owner" }.bounds) > 20)
        assertTrue(changedPixels(card.bounds) > 20)
        for ((owner, text) in listOf("a" to "-3", "b" to "+2")) {
            val b = scene.items.single { it.id == "life/$owner" }.bounds
            assertOpaquePill(during, TableBounds(b.x, b.y - 24, b.width + 30, 48f), TableChangeTagFill, text)
        }
        compose.mainClock.advanceTimeBy(1_600)
        compose.runOnIdle { assertTrue(changes.active.isEmpty()) }
    }
}
