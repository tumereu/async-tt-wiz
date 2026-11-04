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
    private val f = TableStatesFixture
    private val sent = mutableListOf<PlayAction>()
    private val batches = mutableListOf<List<PlayAction>>()
    /** A combat copy: its menu's one cell is Remove from combat (TBL-116); the other cards here offer nothing. */
    private val copy = CombatStackRules.occurrences(f.combat().board.combat).first().id
    private fun render(multi: Boolean = false, play: PlayState = f.combat(),
        contextId: String = f.source(f.a, "1/3").id): TableToolkitController {
        val toolkit = TableToolkitController(f.a, { tableBoard(play) }, { true }, { action, _ -> sent += action; true },
            playBatch = { actions, _ -> batches += actions.toList(); sent += actions; true })
        val source = f.source(f.a, "1/3").id
        toolkit.openCard(TableScope(contextId, if (multi) listOf(source, f.source(f.a, "1/2").id) else listOf(contextId)))
        compose.setContent {
            MaterialTheme {
                TableSurface(f.game, tableBoard(play), f.a, false, Modifier.fillMaxSize(),
                    connected = true, toolkit = toolkit, onIntent = toolkit::handle)
            }
        }
        compose.waitForIdle()
        return toolkit
    }

    // TBL-069
    @Test fun bubblePressDuringGuardCannotFireEvenWhenReleasedAfterTheWindow() {
        compose.mainClock.autoAdvance = false
        val toolkit = render(contextId = copy)
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithContentDescription("Remove from combat").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(350)
        compose.onNodeWithContentDescription("Remove from combat").performTouchInput { advanceEventTime(350); up() }
        compose.runOnIdle { assertNotNull(toolkit.capture); assertNull(toolkit.error); assertTrue(sent.isEmpty()) }
        compose.mainClock.autoAdvance = true
        compose.onNodeWithContentDescription("Remove from combat").performClick()
        // A press after the guard goes through (here refused: these attackers are already declared)
        compose.runOnIdle { assertTrue(sent.isNotEmpty() || toolkit.error != null) }
    }

    // TBL-068
    @Test fun outsideTapDismissesWithoutActivatingTableChrome() {
        val toolkit = render(contextId = copy)
        compose.mainClock.advanceTimeBy(350)
        compose.onNodeWithContentDescription("Card action ring").performTouchInput { click(Offset(width - 30f, height - 25f)) }
        compose.onNodeWithContentDescription("Card action ring").assertDoesNotExist()
        compose.runOnIdle { assertTrue(sent.isEmpty()); assertNull(toolkit.capture) }
    }

    private fun sourcePoint(corner: String = "1/3"): Offset {
        val scene = TablePresentation.scene(f.game, tableBoard(f.combat()), f.a, 412f, 892f, false)
        val point = scene.items.single { it.cardId == f.source(f.a, corner).id }.visible.first().center
        return Offset(point.x, point.y)
    }


    // TBL-116: a card whose menu would hold nothing (no rows, no Inspect or Other side any more) opens no menu
    @Test fun aTapOnACardWithNothingToOfferOpensNoMenu() {
        val toolkit = render(contextId = copy)
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithContentDescription("Shared table").performTouchInput { click(sourcePoint()) }
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithContentDescription("Card action ring").assertDoesNotExist()
        compose.runOnIdle { assertNull(toolkit.capture); assertNull(toolkit.peek); assertTrue(sent.isEmpty()) }
        compose.onNodeWithContentDescription("Inspect").assertDoesNotExist()
        compose.onNodeWithContentDescription("Inspect other side").assertDoesNotExist()
    }

    // SYS-042, TBL-068
    @Test fun outsideDragSurvivesRingDismissalAndMovesCard() {
        render(contextId = copy)
        compose.mainClock.advanceTimeBy(600)
        val point = sourcePoint()
        val field = TablePresentation.scene(f.game, tableBoard(f.combat()), f.a, 412f, 892f, false).fields.last().bounds
        compose.onNodeWithContentDescription("Shared table").performTouchInput {
            swipe(point, Offset(field.right - 24f, field.center.y), 160)
        }
        compose.onNodeWithContentDescription("Card action ring").assertDoesNotExist()
        compose.runOnIdle { assertIs<PlayAction.MoveCard>(sent.single()) }
    }

}
