package org.asyncttwiz.android

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.asyncttwiz.protocol.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.lang.reflect.Proxy
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class PodPresetsDialogTest {
    @get:Rule val compose = createComposeRule()

    private object Inline : AbstractExecutorService() {
        override fun execute(command: Runnable) = command.run()
        override fun shutdown() = Unit
        override fun shutdownNow() = mutableListOf<Runnable>()
        override fun isShutdown() = false
        override fun isTerminated() = false
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = false
    }
    private val modes = listOf(DraftSettings.booster(), DraftSettings.glimpse(), DraftSettings.grid().copy(grids = 15))
    private val presets = List(30) { index ->
        val settings = modes[index % 3].copy(packsPerPlayer = 3 + index % 2)
        PodPreset("id-$index", listOf("Friday cube", "Glimpse league", "Grid with Matti", "Vintage night", "Dominaria rerun")[index % 5] +
            " ${index + 1}", settings, cubeId = "cube".takeIf { index % 3 != 1 }, cubeName = "Vintage cube".takeIf { index % 3 == 0 },
            cubeMissing = index % 3 == 2, setCode = "DMU".takeIf { index % 3 == 1 }, setName = "Dominaria United".takeIf { index % 3 == 1 },
            updatedAt = "2026-10-30T00:00:00Z")
    }

    private fun controller(applied: MutableList<PodPreset> = mutableListOf()): PodPresetController {
        val api = Proxy.newProxyInstance(Pods::class.java.classLoader, arrayOf(Pods::class.java)) { _, method, _ ->
            if (method.name == "presets") PodPresetList(presets) else error("Unexpected ${method.name}")
        } as Pods
        return PodPresetController(api, { "token" }, Inline, { it() }, { PodPresetForm(DraftSettings()) }, { applied += it })
            .also { it.open() }
    }

    /** Set -Dpod.presets.shots=<dir> (through the test JVM) to also write the rendered dialogs there. */
    private fun shot(name: String) {
        val dir = System.getProperty("pod.presets.shots") ?: return
        compose.waitForIdle()
        captureScreenRoboImage(File(dir, "$name.png").path)
    }

    @Test fun manyPresetsScrollFilterAndLoad() {
        val applied = mutableListOf<PodPreset>()
        val controller = controller(applied)
        compose.setContent { ScreenTheme { PodPresetsDialog(controller) } }
        compose.onNodeWithTag("pod-preset-search").assertIsDisplayed()
        compose.onNodeWithText("Friday cube 1").assertIsDisplayed()
        compose.onAllNodesWithText("Booster · Vintage cube · 3×15").onFirst().assertIsDisplayed()
        compose.onAllNodesWithText("Glimpse · Dominaria United · 4 packs").onFirst().assertIsDisplayed()
        compose.onAllNodesWithText("Grid · Cube missing · 15 grids").onFirst().assertIsDisplayed()
        compose.onNodeWithText("Save current settings as preset…").assertIsDisplayed().assertIsEnabled()
        shot("presets-30")
        compose.onNodeWithTag("pod-preset-list").performScrollToNode(hasText("Dominaria rerun 30"))
        compose.onNodeWithText("Dominaria rerun 30").assertIsDisplayed()
        shot("presets-30-scrolled")
        compose.onNodeWithTag("pod-preset-search").performTextInput("night")
        compose.waitForIdle()
        assertEquals(6, compose.onAllNodesWithTag("pod-preset-row").fetchSemanticsNodes().size)
        shot("presets-filtered")
        compose.onNodeWithText("Vintage night 4").performClick()
        assertEquals(listOf("id-3"), applied.map { it.id })
        assertFalse(controller.state.open)
    }

    @Test fun rowActionsOpenRenameAndDeletePrompts() {
        val controller = controller()
        compose.setContent { ScreenTheme { if (controller.state.open) PodPresetsDialog(controller) } }
        compose.onNodeWithContentDescription("Delete Friday cube 1").performClick()
        compose.onNodeWithText("Delete “Friday cube 1”?").assertIsDisplayed()
        shot("presets-delete")
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithContentDescription("Rename Friday cube 1").performClick()
        compose.onNodeWithText("Rename preset").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Save current settings as preset…").performClick()
        compose.onNodeWithText("Save preset").assertIsDisplayed()
        compose.onNodeWithText("Booster · No source · 3×15").assertIsDisplayed()
        compose.onNodeWithTag("pod-preset-name").performTextInput("Friday cube 1")
        compose.onNodeWithText("Save").performClick()
        compose.onNodeWithText("A preset called “Friday cube 1” already exists. Replace it with the current settings?").assertIsDisplayed()
        shot("presets-overwrite")
    }
}
