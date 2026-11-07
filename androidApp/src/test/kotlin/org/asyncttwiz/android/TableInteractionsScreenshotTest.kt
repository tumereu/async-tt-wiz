package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import org.asyncttwiz.protocol.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class TableInteractionsScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val f = TableStatesFixture
    private val sent = mutableListOf<PlayAction>()
    private fun render(width: Float = 412f, height: Float = 892f, font: Float = 1f): Pair<TableToolkitController, TableScene> {
        val play = f.combat()
        val toolkit = TableToolkitController(f.a, { engineTableBoard(play, f.a) }, { true }, { action, _ -> sent += action; true })
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, font)) {
                MaterialTheme { EngineFixtureTable(play, f.a) { TableSurface(f.game, engineTableBoard(play, f.a), f.a, width > height, Modifier.fillMaxSize(),
                    connected = true, toolkit = toolkit, onIntent = toolkit::handle) } }
            }
        }
        compose.waitForIdle()
        return toolkit to TablePresentation.scene(f.game, engineTableBoard(play, f.a), f.a, width, height, width > height)
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(name: String, caption: String) {
        val file = File("$SCREENSHOTS/interactions-$name.png")
        file.parentFile!!.mkdirs()
        captureShot(file.path, caption)
    }

    /** Your Forest's menu (TBL-115: its mana ability its one row; a combat copy opens no menu any more, CMB-003) at
     * [font] text size. */
    private fun menu(width: Float, height: Float, font: Float) {
        val play = LiveTableFixture.menuPlay()
        val forest = play.board.cards.first { card -> card.containerId == "a/BATTLEFIELD" &&
            play.definitions.single { it.digest == card.definition }.definition.name == "Forest" }
        val board = engineTableBoard(play, "a")
        val toolkit = TableToolkitController("a", { board }, { true }, { action, _ -> sent += action; true })
        toolkit.openCard(TableScope(forest.id, listOf(forest.id)))
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, font)) {
                MaterialTheme { EngineFixtureTable(play, "a") { TableSurface(LiveTableFixture.game, board, "a", width > height,
                    Modifier.fillMaxSize(), connected = true, toolkit = toolkit, onIntent = toolkit::handle) } }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Card action ring").assertIsDisplayed()
        val screen = "${width.toInt()}x${height.toInt()} ${if (width > height) "landscape" else "portrait"} table, " +
            "text size ${font}x: the card menu of your Forest, its mana ability its one row"
        capture("menu-${width.toInt()}x${height.toInt()}-font$font", "$screen.")
        compose.onNodeWithContentDescription("Inspect").assertDoesNotExist()
        val row = compose.onAllNodes(hasClickAction() and hasAnyAncestor(hasContentDescription("Card action ring"))).onFirst()
            .fetchSemanticsNode().boundsInRoot
        assertTrue(row.height >= 40f && row.width >= 48f, "the row keeps its height at any text size: $row")
        assertTrue(sent.isEmpty())
    }

    // CMB-003, TBL-116 (owner 2026-11-06): a combat copy opens no menu, not even when asked directly
    @Test fun combatCopyOpensNoMenu() {
        val (toolkit, _) = render()
        compose.runOnIdle {
            val source = f.source(f.a, "1/3").id
            val id = CombatStackRules.occurrences(f.combat().board.combat).first { it.sourceId == source }.id
            toolkit.openCard(TableScope(id, listOf(id)))
        }
        compose.onNodeWithContentDescription("Card action ring").assertDoesNotExist()
        compose.onNodeWithContentDescription("Remove from combat").assertDoesNotExist()
    }

    // SYS-042
    @Test fun contextMenuNormal() { menu(412f, 892f, 1f) }
    @Test @Config(qualifiers = "w360dp-h640dp-port-mdpi") fun contextMenuSmallLargeFont() { menu(360f, 640f, 1.3f) }
    @Test @Config(qualifiers = "w640dp-h360dp-land-mdpi") fun contextMenuLandscapeLargeFont() { menu(640f, 360f, 1.3f) }


    private fun drag() {
        val (_, scene) = render()
        val source = scene.items.first { it.zone == PlayZone.HAND && it.kind == TableItemKind.CARD && it.owner == f.a && it.visible.isNotEmpty() }
        val start = source.visible.first().center
        val end = scene.fields.last().let { TablePoint(it.bounds.right - 70, it.landsBounds(true, scene.camera).center.y) }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Shared table").performTouchInput {
            // ZON-081: a hand card is dragged by first moving up (sideways pans the hand)
            down(Offset(start.x, start.y)); moveTo(Offset(start.x, start.y - 30f), 40); moveTo(Offset(end.x, end.y), 80)
        }
        compose.waitForIdle(); compose.mainClock.advanceTimeByFrame(); compose.mainClock.advanceTimeByFrame()
        try {
            capture("landing-lands", "Portrait table in combat, dragging a hand card onto your battlefield's land row: your " +
                "whole battlefield lights up with a dashed outline (it lays itself out, so no single slot is shown).")
            assertTrue(sent.isEmpty(), "A preview never submits a move")
        } finally {
            compose.onNodeWithContentDescription("Shared table").performTouchInput { cancel() }
            compose.mainClock.autoAdvance = true
        }
    }

    // TBL-052
    @Test fun dragShowsLandingPlaceholders() { drag() }
}
