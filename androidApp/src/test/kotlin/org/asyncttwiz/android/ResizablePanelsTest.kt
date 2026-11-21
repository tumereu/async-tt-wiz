package org.asyncttwiz.android

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import org.asyncttwiz.appui.AppPlatform
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * STK-222, STK-223, WEB-141 (owner playtest after 0.3.0: "all panels are that size if they are draggable"): the large panels
 * above the hand have a drag handle at their top edge while some of their content is cut off (owner 2026-11-21, E14), and one size
 * shared by all of them, kept on the device.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class ResizablePanelsTest {
    @get:Rule val compose = createComposeRule()

    @After fun forget() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        for (file in listOf("panel-size", "panel-size-desktop")) context.getSharedPreferences(file, Context.MODE_PRIVATE).edit().clear().commit()
    }

    /** A panel above the hand named [name] with [rows] rows of 40 dp. */
    @Composable
    private fun TallPanel(name: String, rows: Int, resizable: Boolean = true) {
        AutomatumDockPanel(name, false, {}, name, resizable = resizable) {
            repeat(rows) { Text("$name row $it", Modifier.height(40.dp)) }
        }
    }

    /** The panel shown, [panel] (its name, rows and whether it is one of the large panels), above a hand row at 800 dp, on the
     * phone or the desktop table. */
    private fun render(panel: () -> Triple<String, Int, Boolean>, desktop: Boolean = false) {
        compose.setContent {
            MaterialTheme {
                val layout = if (desktop) AppLayout.Desktop else AppLayout.Phone
                val geometry = if (desktop) DesktopGeometry(1440f, 900f, 1120f, 200f) else null
                CompositionLocalProvider(LocalAppLayout provides layout, LocalDesktopGeometry provides geometry) {
                    Box(Modifier.fillMaxSize()) {
                        val (name, rows, resizable) = panel()
                        AboveHand(TableBounds(0f, 800f, 412f, 92f), PromptPlace.Choice) { key(name) { TallPanel(name, rows, resizable) } }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun panel(name: String) = compose.onNode(hasContentDescription(name))
    private fun height(name: String) = panel(name).fetchSemanticsNode().boundsInRoot.height
    private fun top(name: String) = panel(name).fetchSemanticsNode().boundsInRoot.top
    private fun handle(): SemanticsNodeInteraction = compose.onNode(hasTestTag(PANEL_HANDLE_TAG))
    private fun handles() = compose.onAllNodesWithTag(PANEL_HANDLE_TAG).fetchSemanticsNodes().size
    private fun large(name: String, rows: Int) = Triple(name, rows, true)

    /** A finger drags the open panel's handle by [by] px (up negative) and lifts. */
    private fun drag(by: Float) {
        handle().performTouchInput {
            down(center)
            repeat(10) { moveBy(Offset(0f, by / 10f)) }
            up()
        }
        compose.waitForIdle()
    }

    // STK-222, STK-223: dragging the choice panel's handle up makes it taller; the reveal panel opening next has the same size,
    // and the size is kept on the device
    @Test fun draggingOnePanelsHandleResizesEveryLargePanel() {
        var shown by mutableStateOf(large("Choice", 20))
        render({ shown })
        val before = height("Choice")
        drag(-100f)
        val after = height("Choice")
        // The touch slop isn't counted
        assertTrue(after in before + 70f..before + 100.5f, "taller by the drag: $before → $after")
        compose.runOnIdle { shown = large("Reveal", 30) }
        compose.waitForIdle()
        assertEquals(after, height("Reveal"), "the next panel has the same size")
        val kept = PanelSizePreferences(AppPlatform.current).read()
        assertEquals(after - 52f - 8f, kept, "the content's size kept on the device")
        assertNull(PanelSizePreferences(AppPlatform.current, AppLayout.Desktop).read(), "the desktop layout's size untouched")
    }

    // STK-223: dragged down, the size the panels share is smaller, and a panel opened later (as after a restart) opens at it
    @Test fun aSmallerSizeIsKeptForPanelsOpenedLater() {
        var shown by mutableStateOf<Triple<String, Int, Boolean>?>(large("Choice", 20))
        render({ shown ?: large("none", 0) })
        drag(150f)
        val kept = PanelSizePreferences(AppPlatform.current).read()
        assertTrue(kept != null && kept in 440f - 150f..440f - 120f, "smaller by the drag: $kept")
        compose.runOnIdle { shown = large("Keep or bottom", 25) }
        compose.waitForIdle()
        assertEquals(kept + 52f + 8f, height("Keep or bottom"))
    }

    // STK-222 (owner 2026-11-21, E14): a panel whose content fits, short or taller than the smallest size, opens at the size its
    // content needs and has no handle; one whose content is cut off has it; the damage division never
    @Test fun onlyAPanelWithContentCutOffHasAHandle() {
        var shown by mutableStateOf(large("Choice", 2))
        render({ shown })
        assertEquals(0, handles(), "short: no handle")
        compose.runOnIdle { shown = large("Choice", 6) }
        compose.waitForIdle()
        assertEquals(6 * 40f + 5 * 6f + 8f + 52f, height("Choice"), "fits: as tall as its content and the button row")
        assertEquals(0, handles(), "taller than the smallest size but all shown: no handle")
        compose.runOnIdle { shown = large("Choice", 20) }
        compose.waitForIdle()
        assertEquals(1, handles(), "cut off: the handle")
        compose.runOnIdle { shown = Triple("Division", 20, false) }
        compose.waitForIdle()
        assertEquals(0, handles(), "division: no handle")
    }

    // WEB-141: on the desktop table the centred overlay's top edge follows the pointer (it grows both ways), and the size is
    // kept for the desktop layout only
    @Config(qualifiers = "w1440dp-h900dp-land-mdpi")
    @Test fun theDesktopOverlaysEdgeFollowsThePointer() {
        render({ large("Choice", 20) }, desktop = true)
        val before = top("Choice")
        val tall = height("Choice")
        drag(80f)
        val moved = top("Choice") - before
        assertTrue(moved in 55f..81f, "the top edge followed the pointer down: $moved px")
        assertTrue(height("Choice") in tall - 2 * moved - 4f..tall - 2 * moved + 4f, "shorter at both edges: $tall → ${height("Choice")}")
        assertTrue(PanelSizePreferences(AppPlatform.current, AppLayout.Desktop).read() != null, "kept for the desktop layout")
        assertNull(PanelSizePreferences(AppPlatform.current).read(), "the phone layout's size untouched")
    }
}
