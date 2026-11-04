package org.asyncttwiz.android

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.asyncttwiz.protocol.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class CardInspectionUiTest {
    @get:Rule val compose = createComposeRule()

    // TBL-104
    @Test fun tableHoldScrollsToBottomAndBackWithoutRelease() = tableHold()

    /** A resting finger still reports tiny moves every frame. Each one restarts the hold clock outside
     * composition; the peek must still open once the hold time has passed since the press. */
    @Test fun jitteringHoldStillPeeksAtTheHoldDeadline() {
        val f = TableStatesFixture
        val play = f.combat()
        val toolkit = TableToolkitController(f.a, { tableBoard(play) }, { true }, { _, _ -> true })
        compose.setContent { MaterialTheme {
            TableSurface(f.game, tableBoard(play), f.a, false, Modifier.fillMaxSize(), connected = true,
                toolkit = toolkit, onIntent = toolkit::handle)
        } }
        val scene = TablePresentation.scene(f.game, tableBoard(play), f.a, 412f, 892f, false)
        val card = scene.items.first { it.owner == f.a && it.kind == TableItemKind.CARD && it.zone == PlayZone.BATTLEFIELD && it.visible.isNotEmpty() }
        val point = card.visible.first().center
        val table = compose.onNodeWithContentDescription("Shared table")
        compose.mainClock.autoAdvance = false
        table.performTouchInput { down(Offset(point.x, point.y)) }
        val pressed = compose.mainClock.currentTime
        var sign = 1f
        // Each injected move's delay also advances the paused main clock.
        fun jitter(until: Long) {
            while (compose.mainClock.currentTime - pressed < until) {
                table.performTouchInput { moveBy(Offset(.5f * sign, 0f), delayMillis = 16) }
                sign = -sign
            }
        }
        jitter(CardPeekHoldMillis - 64)
        compose.runOnIdle { assertNull(toolkit.peek) }
        jitter(CardPeekHoldMillis + 160)
        compose.runOnIdle { assertNotNull(toolkit.peek) }
        table.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.runOnIdle { assertNull(toolkit.peek) }
    }

    /** Holding a double-faced card in portrait: its two faces are taller than the screen, and the finger scrolls them. */
    private fun tableHold() {
        val f = TableStatesFixture
        val (play, held) = transformedFixture()
        val sent = mutableListOf<PlayAction>()
        val toolkit = TableToolkitController(f.a, { tableBoard(play) }, { true }, { action, _ -> sent += action; true })
        compose.setContent { MaterialTheme {
            TableSurface(f.game, tableBoard(play), f.a, false, Modifier.fillMaxSize(), connected = true,
                toolkit = toolkit, onIntent = toolkit::handle)
        } }
        val scene = TablePresentation.scene(f.game, tableBoard(play), f.a, 412f, 892f, false)
        val point = scene.items.first { it.cardId == held.id && it.visible.isNotEmpty() }.visible.first().center
        val table = compose.onNodeWithContentDescription("Shared table")
        compose.mainClock.autoAdvance = false
        table.performTouchInput { down(Offset(point.x, point.y)) }
        compose.waitForIdle(); compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithContentDescription("Close card inspection").assertDoesNotExist()
        val content = compose.onNodeWithContentDescription("Card inspection content")
        fun scroll() = content.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertEquals(0f, scroll().value())
        assertTrue(scroll().maxValue() > 0f, "Two card-sized faces are taller than the screen")
        // At least 1:1, sped up so that 90% of the screen height covers the whole preview (mdpi: 1 px = 1 dp).
        val gain = HoldPreviewScroll.gain(scroll().maxValue(), 892f)
        table.performTouchInput { moveBy(Offset(0f, -50f)) }
        compose.waitForIdle()
        assertEquals(minOf(50f * gain, scroll().maxValue()), scroll().value(), 1.5f)
        table.performTouchInput { moveBy(Offset(0f, -10_000f)) }
        compose.waitForIdle()
        assertEquals(scroll().maxValue(), scroll().value())
        compose.onNodeWithContentDescription("Card image: Daylight healer").assertIsDisplayed()
        table.performTouchInput { moveBy(Offset(0f, 40f)) }
        compose.waitForIdle()
        assertTrue(scroll().value() < scroll().maxValue(), "Reverse immediately even after overscrolling")
        table.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        content.assertDoesNotExist()
        compose.runOnIdle { assertNull(toolkit.peek) }
        assertTrue(sent.isEmpty())
    }
}
