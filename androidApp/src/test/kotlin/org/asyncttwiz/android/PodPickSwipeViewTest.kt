package org.asyncttwiz.android

import android.content.Context
import androidx.compose.material3.Surface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.asyncttwiz.protocol.DraftCopy
import org.asyncttwiz.protocol.DraftPrivatePack
import org.asyncttwiz.protocol.DraftSettings
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class PodPickSwipeViewTest {
    @get:Rule val compose = createComposeRule()
    private val card get() = compose.onNodeWithTag("pod-pick-card-1")
    private val lifted get() = compose.onNodeWithTag("pod-pick-swipe-copy-1")

    @Before fun show() {
        ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("pod-pick", Context.MODE_PRIVATE)
            .edit().clear().commit()
        val pack = DraftPrivatePack("pack", 1, (1..15).map { DraftCopy("$it", "definition") })
        compose.setContent {
            ScreenTheme { Surface { PodPickView(pack, DraftSettings.glimpse(), emptyMap(), false, { _, _ -> }) } }
        }
        compose.mainClock.autoAdvance = false
    }

    private fun assertMark(mark: String) = card.assert(
        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, mark))

    private fun startDrag(fraction: Float) {
        card.performTouchInput { down(center); moveBy(Offset(width * fraction, 0f), delayMillis = 16) }
        compose.mainClock.advanceTimeByFrame()
    }

    @Test fun originalStaysFixedWhileCopyLiftsAndFadesWithoutTravellingBack() {
        val originalBounds = card.fetchSemanticsNode().boundsInRoot
        startDrag(.4f)
        lifted.assertExists()
        assertEquals(originalBounds, card.fetchSemanticsNode().boundsInRoot)
        assertMark("Unmarked")
        val liftedBounds = lifted.fetchSemanticsNode().boundsInRoot
        card.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(80)
        assertMark("Keep")
        assertEquals(originalBounds, card.fetchSemanticsNode().boundsInRoot)
        assertEquals(liftedBounds, lifted.fetchSemanticsNode().boundsInRoot)
        compose.mainClock.advanceTimeBy(160)
        lifted.assertDoesNotExist()
        assertEquals(originalBounds, card.fetchSemanticsNode().boundsInRoot)

        startDrag(.4f)
        card.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(240)
        assertMark("Unmarked")
        lifted.assertDoesNotExist()
        assertEquals(originalBounds, card.fetchSemanticsNode().boundsInRoot)
    }

    @Test fun shortFastReleaseAndCancelledLongSwipeNeverMark() {
        startDrag(.2f)
        lifted.assertExists()
        card.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(240)
        assertMark("Unmarked")
        lifted.assertDoesNotExist()
        compose.onNodeWithText("Keep").assertDoesNotExist()

        startDrag(-.4f)
        lifted.assertExists()
        card.performTouchInput { cancel() }
        compose.mainClock.advanceTimeBy(240)
        assertMark("Unmarked")
        lifted.assertDoesNotExist()
    }

    @Test fun anotherSwipeCanUnmarkWhileTheFirstCopyIsStillFading() {
        startDrag(.4f)
        card.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(64)
        lifted.assertExists()
        assertMark("Keep")
        startDrag(.4f)
        card.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(64)
        lifted.assertExists()
        assertMark("Unmarked")
        compose.mainClock.advanceTimeBy(176)
        lifted.assertDoesNotExist()
    }

    @Test fun verticalDragNeverLiftsOrMarksTheCard() {
        card.performTouchInput { down(center); moveBy(Offset(0f, -height * .4f), delayMillis = 16) }
        compose.mainClock.advanceTimeByFrame()
        lifted.assertDoesNotExist()
        assertMark("Unmarked")
        card.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(240)
        lifted.assertDoesNotExist()
        compose.onNodeWithTag("pod-pick-art-preview").assertDoesNotExist()
    }
}
