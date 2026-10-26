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

    private fun capture(name: String): java.awt.image.BufferedImage {
        val file = File("build/outputs/roborazzi/$name.png")
        requireNotNull(file.parentFile).mkdirs(); compose.onRoot().captureRoboImage(file.path)
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

    @Test fun smallestCardTagsAreOpaqueUnclippedScreenBoundedAndTouchTransparent() {
        val appearance = CardAppearance(cardSize = MIN_CARD_SCALE, textSize = MIN_CARD_SCALE)
        val play = LiveTableFixture.menuPlay()
        val original = TablePresentation.scene(LiveTableFixture.game, play, "a", 412f, 892f, false, appearance = appearance)
            .items.first { it.kind == TableItemKind.CARD && !it.covered && it.zone == PlayZone.BATTLEFIELD }
        // Same smallest-card face as the table, at a left edge and at the bottom/right screen edge.
        val transform = original.faceTransform()
        val left = original.copy(id = "card/left", cardId = "left", bounds = TableBounds(2f, 250f, transform.width, transform.height),
            cardTransform = transform.copy(rotation = 0f), counters = mapOf(CounterColor.RED to "1"), note = "")
            .let { it.copy(visible = listOf(it.bounds.copy(width = it.bounds.width / 2),
                it.bounds.copy(x = it.bounds.center.x, width = it.bounds.width / 2))) }
        val edge = left.copy(id = "card/edge", cardId = "edge",
            bounds = TableBounds(410f - transform.width, 890f - transform.height, transform.width, transform.height))
            .let { it.copy(visible = listOf(it.bounds)) }
        val items = listOf(left, edge).associateBy { it.id }
        val changes = TableChanges { 0L }
        var taps = 0
        compose.setContent {
            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { taps++ } }) {
                Canvas(Modifier.fillMaxSize()) {
                    for (x in 0 until size.width.toInt() step 4)
                        drawRect(if (x % 8 == 0) Color.White else Color(0xff426f97), Offset(x.toFloat(), 0f), Size(4f, size.height))
                    for (card in items.values) drawRect(Color.White, Offset(card.bounds.x, card.bounds.y), Size(card.bounds.width, card.bounds.height))
                }
                TableChangeOverlay(changes, "a", appearance, items::get)
            }
        }
        compose.waitForIdle(); compose.mainClock.autoAdvance = false
        animate(changes, GameplayTransition(fields = listOf(
            GameplayFieldChange("left", GameplayField.CORNER, "1/1", "3/3"),
            GameplayFieldChange("edge", GameplayField.CORNER, "1/1", "3/3"),
            GameplayFieldChange("left", GameplayField.COUNTER, "0", "1", CounterColor.RED))))
        compose.mainClock.advanceTimeBy(200); compose.waitForIdle()
        val image = capture("changefx-smallest-card-300ms")
        val flashLeft = image.getRGB((left.bounds.x + 3).toInt(), (left.bounds.bottom - 3).toInt())
        val flashRight = image.getRGB((left.bounds.right - 3).toInt(), (left.bounds.bottom - 3).toInt())
        assertNotEquals(Color.White.toArgb(), flashLeft, "Value flash must remain visible at 300 ms")
        assertEquals(flashLeft, flashRight, "Value flash must cover every visible fragment")
        val pt = assertOpaquePill(image, TableBounds(left.bounds.right, left.bounds.bottom - 24, 90f, 40f), TableChangeTagFill, "+2/+2")
        assertTrue(pt.x >= left.bounds.right, "P/T tag belongs beside the bottom-right value, not over the art")
        assertTrue(pt.width > left.bounds.width, "The full tag must extend beyond the smallest card width")
        assertOpaquePill(image, TableBounds(310f, 850f, 102f, 42f), TableChangeTagFill, "+2/+2")
        assertOpaquePill(image, TableBounds(2f, left.bounds.y - 24f, 110f, left.bounds.height + 34), counterColor(CounterColor.RED), "+1")
        compose.onRoot().performTouchInput { click(Offset(pt.center.x, pt.center.y)) }
        compose.runOnIdle { assertEquals(1, taps, "Tags must not intercept touches") }
        compose.mainClock.advanceTimeBy(550); compose.waitForIdle()
        val held = capture("changefx-smallest-card-held")
        assertEquals(0, changedPixels(image, held, pt), "Pill stays fully opaque throughout the hold")
        compose.mainClock.advanceTimeBy(350); compose.waitForIdle()
        val fading = capture("changefx-smallest-card-fading")
        assertTrue(changedPixels(held, fading, pt) > 40, "Pill fades only after the hold")
    }

    @Test fun realStackEntryHighlightsAndLinkGlowDrawInBothPlaybackDirections() = stackFeedback(projection = false)

    @Test fun projectedStackEntryHighlightsAndLinkGlowDrawInBothPlaybackDirections() = stackFeedback(projection = true)

    private fun stackFeedback(projection: Boolean) {
        val (initial, id) = ChangeTargetFixture.stack(projection)
        val play = if (!projection) initial else initial.copy(board = initial.board.copy(combat = CombatArea("combat",
            listOf(CombatPair("pair", attackers = listOf(CombatOccurrence("combat-occurrence", initial.board.stackEffects.single().sourceId)))))))
        val scene = ChangeTargetFixture.scene(play)
        val drawing = scene.items.single { it.cardId == id }
        val changes = TableChanges { 0L }
        overlay(scene, changes)
        val before = capture("stack-changefx-$projection-before")
        val owner = if (!projection) GameplayTransition(fields = listOf(GameplayFieldChange(id, GameplayField.OWNER, "a", "b")))
            else GameplayTransition(combatStack = CombatStackTransition(effects = listOf(StackEffectChange(id,
                play.board.stackEffects.single().copy(controller = "a"), play.board.stackEffects.single()))))
        val note = if (!projection) GameplayTransition(fields = listOf(GameplayFieldChange(id, GameplayField.NOTE, "old", "new")))
            else GameplayTransition(combatStack = CombatStackTransition(effects = listOf(StackEffectChange(id,
                play.board.stackEffects.single().copy(note = "old"), play.board.stackEffects.single()))))
        val text = GameplayTransition(combatStack = CombatStackTransition(textMarks = listOf(StackTextMarkChange(id, emptySet(), setOf(0)))))
        val link = if (!projection) GameplayTransition(links = listOf(LinkChange(null, PlayLink("link",
            LinkEndpoint(LinkEndpointKind.CARD, id), LinkEndpoint(LinkEndpointKind.PLAYER, "a")))))
            else GameplayTransition(combatStack = CombatStackTransition(addedLinks = listOf(ProjectionLink("link", id, "combat-occurrence"))))
        for (reverse in listOf(false, true)) for ((name, transition) in listOf("owner" to owner, "note" to note, "text" to text, "link" to link)) {
            // Reversing a removal adds a link; reversing an addition correctly has no glow.
            val recorded = if (reverse && name == "link") if (!projection)
                link.copy(links = link.links.map { LinkChange(it.to, null) })
                else GameplayTransition(combatStack = CombatStackTransition(removedLinks = link.combatStack!!.addedLinks))
                else transition
            animate(changes, recorded, reverse)
            val during = capture("stack-changefx-$projection-$name-$reverse")
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
        val before = capture("covered-changefx-before")
        animate(changes, GameplayTransition(fields = listOf(
            GameplayFieldChange(id, GameplayField.NOTE, "old", "new"),
            GameplayFieldChange(id, GameplayField.CORNER, "1/1", "2/2"),
            GameplayFieldChange(id, GameplayField.COUNTER, "0", "1", CounterColor.BLUE)),
            combatStack = CombatStackTransition(textMarks = listOf(StackTextMarkChange(id, emptySet(), setOf(0))))))
        assertEquals(0, changedPixels(before, capture("covered-changefx-hidden"), TableBounds(0f, 0f, 412f, 892f)))
        compose.runOnIdle { changes.clear() }
        animate(changes, GameplayTransition(fields = listOf(GameplayFieldChange(id, GameplayField.OWNER, "a", "b"))))
        val during = capture("covered-changefx-owner")
        assertTrue(changedPixels(before, during, drawing.bounds) > 20)
        assertEquals(0, changedPixels(before, during, drawing.bounds.let { TableBounds(it.x + 4, it.y + 4, it.width - 8, it.height - 8) }),
            "Public owner feedback must only pulse the border")
    }

    @Test fun zeroedCounterKeepsMinusOneTagOnItsOutlineSlotThenExpires() {
        val initial = LiveTableFixture.menuPlay()
        val play = initial.copy(board = initial.board.copy(players = initial.board.players.map { it.copy(counters = emptyMap()) }))
        val scene = ChangeTargetFixture.scene(play)
        val hud = scene.items.single { it.id == "counter/a/BLUE" }
        assertEquals("", hud.value)
        val changes = TableChanges { 0L }
        overlay(scene, changes)
        val before = capture("zero-counter-changefx-before")
        animate(changes, GameplayTransition(fields = listOf(GameplayFieldChange("a", GameplayField.PLAYER_COUNTER, "1", "0", CounterColor.BLUE))))
        compose.runOnIdle { assertEquals("-1", changes.active.values.single().tag) }
        assertTrue(changedPixels(before, capture("zero-counter-changefx-flash"), hud.bounds) > 20)
        compose.mainClock.advanceTimeBy(650); compose.waitForIdle()
        val tagArea = TableBounds(hud.bounds.x, hud.bounds.y - 20, hud.bounds.width + 24, 40f)
        assertTrue(changedPixels(before, capture("zero-counter-changefx-tag"), tagArea) > 20,
            "The tag must outlive the flash on the empty counter slot")
        compose.mainClock.advanceTimeBy(900); compose.waitForIdle()
        compose.runOnIdle { assertTrue(changes.active.isEmpty()) }
        assertEquals(0, changedPixels(before, capture("zero-counter-changefx-expired"), TableBounds(0f, 0f, 412f, 892f)))
    }

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
                GameplayFieldChange(card.cardId!!, GameplayField.COUNTER, "0", "1", CounterColor.RED),
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
        for ((owner, text) in listOf("a" to "-3", "b" to "+2")) {
            val b = scene.items.single { it.id == "life/$owner" }.bounds
            assertOpaquePill(during, TableBounds(b.x, b.y - 24, b.width + 30, 48f), TableChangeTagFill, text)
        }
        compose.mainClock.advanceTimeBy(1_600)
        compose.runOnIdle { assertTrue(changes.active.isEmpty()) }
    }
}
