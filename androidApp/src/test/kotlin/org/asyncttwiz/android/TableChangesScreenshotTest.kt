package org.asyncttwiz.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

    @Test fun vanishedCounterKeepsMinusOneTagOnHudForItsFadeThenExpires() {
        val initial = LiveTableFixture.menuPlay()
        val play = initial.copy(board = initial.board.copy(players = initial.board.players.map { it.copy(counters = emptyMap()) }))
        val scene = ChangeTargetFixture.scene(play)
        val hud = scene.items.single { it.id == "life/a" }
        assertTrue(scene.items.none { it.kind == TableItemKind.COUNTER })
        val changes = TableChanges { 0L }
        overlay(scene, changes)
        val before = capture("zero-counter-changefx-before")
        animate(changes, GameplayTransition(fields = listOf(GameplayFieldChange("a", GameplayField.PLAYER_COUNTER, "1", "0", CounterColor.BLUE))))
        compose.runOnIdle { assertEquals("-1", changes.active.values.single().tag) }
        assertTrue(changedPixels(before, capture("zero-counter-changefx-flash"), hud.bounds) > 20)
        compose.mainClock.advanceTimeBy(650); compose.waitForIdle()
        val tagArea = TableBounds(hud.bounds.x, hud.bounds.y - 20, hud.bounds.width + 24, 40f)
        assertTrue(changedPixels(before, capture("zero-counter-changefx-tag"), tagArea) > 20,
            "The tag must outlive the flash even though its counter row was removed")
        compose.mainClock.advanceTimeBy(900); compose.waitForIdle()
        compose.runOnIdle { assertTrue(changes.active.isEmpty()) }
        assertEquals(0, changedPixels(before, capture("zero-counter-changefx-expired"), TableBounds(0f, 0f, 412f, 892f)))
    }

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
