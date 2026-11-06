package org.asyncttwiz.android

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.asyncttwiz.protocol.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class CardRingInteractionTest {
    @get:Rule val compose = createComposeRule()
    private val sent = mutableListOf<PlayAction>()
    /** The menu every test here opens: your Forest's, its mana ability its one row (TBL-115; a combat copy opens no menu
     * any more, CMB-003). */
    private val play = LiveTableFixture.menuPlay()
    private val forest = play.board.cards.first { card -> card.containerId == "a/BATTLEFIELD" &&
        play.definitions.single { it.digest == card.definition }.definition.name == "Forest" }
    private fun render(): TableToolkitController {
        val board = engineTableBoard(play, "a")
        val toolkit = TableToolkitController("a", { board }, { true }, { action, _ -> sent += action; true })
        toolkit.openCard(TableScope(forest.id, listOf(forest.id)))
        compose.setContent {
            MaterialTheme {
                EngineFixtureTable(play, "a") {
                    TableSurface(LiveTableFixture.game, board, "a", false, Modifier.fillMaxSize(),
                        connected = true, toolkit = toolkit, onIntent = toolkit::handle)
                }
            }
        }
        compose.waitForIdle()
        return toolkit
    }
    private fun menuRow() = compose.onAllNodes(hasClickAction() and hasAnyAncestor(hasContentDescription("Card action ring"))).onFirst()

    // TBL-069
    @Test fun bubblePressDuringGuardCannotFireEvenWhenReleasedAfterTheWindow() {
        compose.mainClock.autoAdvance = false
        val toolkit = render()
        compose.mainClock.advanceTimeByFrame()
        menuRow().performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(350)
        menuRow().performTouchInput { advanceEventTime(350); up() }
        compose.runOnIdle { assertNotNull(toolkit.capture); assertNull(toolkit.error); assertTrue(sent.isEmpty()) }
        compose.mainClock.autoAdvance = true
        menuRow().performClick()
        // A press after the guard goes through: the mana ability is activated (the menu closes) or refused with a reason
        compose.waitUntil(5000) { toolkit.capture == null || toolkit.error != null }
    }

    // TBL-068
    @Test fun outsideTapDismissesWithoutActivatingTableChrome() {
        val toolkit = render()
        compose.mainClock.advanceTimeBy(350)
        compose.onNodeWithContentDescription("Card action ring").performTouchInput { click(Offset(width - 30f, height - 25f)) }
        compose.onNodeWithContentDescription("Card action ring").assertDoesNotExist()
        compose.runOnIdle { assertTrue(sent.isEmpty()); assertNull(toolkit.capture) }
    }

    /** Your Meadow Sentinel on the battlefield, which offers nothing here. */
    private fun sourcePoint(): Offset {
        val sentinel = play.board.cards.first { it.containerId == "a/BATTLEFIELD" && it.corner == "1/3" }
        val scene = TablePresentation.scene(LiveTableFixture.game, engineTableBoard(play, "a"), "a", 412f, 892f, false)
        val point = scene.items.single { it.cardId == sentinel.id }.visible.first().center
        return Offset(point.x, point.y)
    }


    // TBL-116: a card whose menu would hold nothing (no rows, no Inspect or Other side any more) opens no menu
    @Test fun aTapOnACardWithNothingToOfferOpensNoMenu() {
        val toolkit = render()
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithContentDescription("Shared table").performTouchInput { click(sourcePoint()) }
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithContentDescription("Card action ring").assertDoesNotExist()
        compose.runOnIdle { assertNull(toolkit.capture); assertNull(toolkit.peek); assertTrue(sent.isEmpty()) }
        compose.onNodeWithContentDescription("Inspect").assertDoesNotExist()
        compose.onNodeWithContentDescription("Inspect other side").assertDoesNotExist()
    }

    // SYS-042, TBL-068: a drag outside the menu closes it and still goes through to the table (here the card goes back
    // where the layout puts it: nothing is placed by hand on the battlefield, ZON-077)
    @Test fun outsideDragSurvivesRingDismissalAndReachesTheTable() {
        val toolkit = render()
        compose.mainClock.advanceTimeBy(600)
        val point = sourcePoint()
        val field = TablePresentation.scene(LiveTableFixture.game, engineTableBoard(play, "a"), "a", 412f, 892f, false).fields.last().bounds
        compose.onNodeWithContentDescription("Shared table").performTouchInput {
            swipe(point, Offset(field.right - 24f, field.center.y), 160)
        }
        compose.onNodeWithContentDescription("Card action ring").assertDoesNotExist()
        compose.runOnIdle { assertNull(toolkit.capture); assertTrue(sent.isEmpty()) }
    }

}
