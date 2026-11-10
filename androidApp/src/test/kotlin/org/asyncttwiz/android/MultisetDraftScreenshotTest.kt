package org.asyncttwiz.android

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.DraftableSet
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.lang.reflect.Proxy
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

/**
 * Multiset drafting for the owner's gallery: set symbols in the Sets tab (DRF-176) and the pack grid (DRF-177, DRF-178):
 * one set by default with Mix sets, the open grid, a row label, a column label and a single cell chosen, Sealed's one row
 * and a preset saved with a grid. The creator's setup is shown from seeded pod state and driven by taps; nothing is sent
 * (the worker never runs requests). Real set names and symbols, so the symbols show as players see them.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-xhdpi")
class MultisetDraftScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val worker = object : AbstractExecutorService() {
        override fun execute(command: Runnable) = Unit // Leave network work pending.
        override fun shutdown() = Unit
        override fun shutdownNow() = mutableListOf<Runnable>()
        override fun isShutdown() = false
        override fun isTerminated() = false
        override fun awaitTermination(timeout: Long, unit: TimeUnit) = true
    }
    private inline fun <reified T> unused(): T = Proxy.newProxyInstance(T::class.java.classLoader,
        arrayOf(T::class.java)) { _, method, _ -> error("Unexpected network call: ${method.name}") } as T
    private val pods = PodController(unused<Pods>(), unused<Cubes>(), { "token" }, worker, { it() })

    @After fun close() { pods.close() }

    private val alice = AccountIdentity("alice", "Alice")
    private val bob = AccountIdentity("bob", "Bob")

    private val sets = listOf(
        DraftableSet("FDN", "Foundations", "2024-11-15", 291, true, keyrune = "FDN"),
        DraftableSet("DSK", "Duskmourn: House of Horror", "2024-09-27", 286, false, listOf("Unholy Annex"), keyrune = "DSK"),
        DraftableSet("BLB", "Bloomburrow", "2024-08-02", 281, true, keyrune = "BLB"),
        DraftableSet("EMR", "Ember Reaches", "2014-06-01", 250, true),
        DraftableSet("DGM", "Dragon's Maze", "2013-05-03", 156, true, keyrune = "DGM"),
        DraftableSet("GTC", "Gatecrash", "2013-02-01", 249, true, keyrune = "GTC"),
        DraftableSet("RTR", "Return to Ravnica", "2012-10-05", 274, true, keyrune = "RTR"),
    )

    private fun source(vararg packs: String) = PodSource(kind = PodSourceKind.SET, setCode = packs.first(),
        setName = sets.single { it.code == packs.first() }.name, boosterKind = SetBoosterKind.RECIPE_BASED, boosterName = "draft",
        packSets = packs.toList())

    private fun detail(settings: DraftSettings, source: PodSource, name: String = "Ravnica night") = PodDetail("pod", name,
        PodPhase.SETUP, alice, settings, source, DraftSupply(90, Long.MAX_VALUE), listOf(PodMember(alice), PodMember(bob)), emptyList(),
        updatedAt = "2026-11-10T00:00:00Z")

    private fun show(detail: PodDetail, picker: PodSetPickerState = PodSetPickerState(loaded = true, sets = sets)) {
        pods.setPicker.seed(picker)
        pods.seed(PodState(loaded = true, selected = detail.id, detail = detail, tab = PodTab.DRAFT))
        // The server's last answer, which setup edits are shown on top of
        PodController::class.java.getDeclaredField("accepted").apply { isAccessible = true }.set(pods, detail)
        compose.setContent { ScreenTheme { NoRipples { Surface(Modifier.fillMaxSize(), color = ScreenBackground) {
            PodsDestination(pods, alice.accountId, {}, null, {}, mainMenu = {})
        } } } }
        settle()
    }

    private fun settle() {
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
    }

    private fun shot(name: String, caption: String) = captureShot("$SCREENSHOTS/draft/$name.png", caption)

    /** Booster, 6 packs with 2 open at once: three rounds of two packs. */
    private val sixPacks = DraftSettings(packsPerPlayer = 6, packsOpenAtOnce = 2)

    private fun tap(tag: String) { compose.onNodeWithTag(tag).performScrollTo().performClick(); settle() }
    private fun choose(name: String) { compose.onNode(hasClickAction() and hasAnyDescendant(hasText(name)), useUnmergedTree = true).performScrollTo().performClick(); settle() }
    private fun cells() = pods.state.detail!!.source.packSets.let { PodPackGrid.cells(it, pods.state.detail!!.settings) }

    // DRF-088, DRF-176
    @Test fun setPickerSymbols() {
        show(detail(sixPacks, source("RTR")), PodSetPickerState(open = true, loaded = true, sets = sets))
        compose.onNodeWithText("Sets").performClick()
        settle()
        compose.onNodeWithTag("set-symbol-RTR", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("set-code-EMR", useUnmergedTree = true).assertExists()
        shot("source-sets-symbols", "Tapping the source's edit button in pod setup and then the Sets tab lists the draftable sets " +
            "newest first, each with its set symbol before its name; Ember Reaches (invented, no symbol) shows its code instead, and " +
            "Duskmourn, greyed with the warning sign, can't be chosen.")
    }

    // DRF-177: one set for every pack by default, with Mix sets under the source; then the open grid
    @Test fun oneSetThenTheOpenGrid() {
        show(detail(sixPacks, source("RTR")))
        compose.onNodeWithTag("mix-sets").assertIsDisplayed()
        compose.onNodeWithTag("pack-grid").assertDoesNotExist()
        shot("multiset-one-set", "The creator's setup of a Booster pod from Return to Ravnica with 6 packs, 2 open at once: one set " +
            "for every pack, with Mix sets under the source.")
        tap("mix-sets")
        compose.onNodeWithTag("pack-grid").assertIsDisplayed()
        shot("multiset-grid-open", "Tapping Mix sets opens the pack grid: Pack 1 and Pack 2 across, Round 1 to Round 3 down, every cell " +
            "Return to Ravnica's symbol over its code.")
    }

    // DRF-178: a row label, a column label and a single cell
    @Test fun rowColumnAndCell() {
        show(detail(sixPacks, source("RTR")))
        tap("mix-sets")
        tap("pack-row-1")
        compose.onNodeWithTag("pack-set-title").assertTextEquals("Set for Round 2")
        choose("Gatecrash")
        assertEquals(listOf("RTR", "RTR", "GTC", "GTC", "RTR", "RTR"), cells())
        shot("multiset-row", "After Mix sets, tapping the row label Round 2 and choosing Gatecrash in the set list makes both packs " +
            "of round 2 Gatecrash; the source now reads Mixed sets.")
        tap("pack-column-0")
        compose.onNodeWithTag("pack-set-title").assertTextEquals("Set for Pack 1")
        choose("Dragon's Maze")
        assertEquals(listOf("DGM", "RTR", "DGM", "GTC", "DGM", "RTR"), cells())
        shot("multiset-column", "Then tapping the column label Pack 1 and choosing Dragon's Maze makes the first pack of every round " +
            "Dragon's Maze, leaving the second packs as they were.")
        tap("pack-cell-5")
        compose.onNodeWithTag("pack-set-title").assertTextEquals("Set for Round 3 · Pack 2")
        shot("multiset-cell-list", "Then tapping the cell of round 3's second pack opens the set list titled Set for Round 3 · Pack 2, " +
            "each set with its symbol and Duskmourn greyed.")
        choose("Gatecrash")
        assertEquals(listOf("DGM", "RTR", "DGM", "GTC", "DGM", "GTC"), cells())
        shot("multiset-cell", "Choosing Gatecrash there changes only that cell: round 3 is now Dragon's Maze then Gatecrash.")
    }

    // DRF-177: Sealed's grid is one row
    @Test fun sealedOneRow() {
        show(detail(DraftSettings.sealed(), source("RTR", "RTR", "RTR", "GTC"), name = "Sealed league"))
        compose.onNodeWithTag("pack-row-0").assertTextEquals("All packs")
        shot("multiset-sealed", "The creator's setup of a Sealed pod of 6 boosters whose pack grid is one row, All packs: boosters " +
            "1 to 3 from Return to Ravnica and 4 to 6 from Gatecrash; the source reads Mixed sets.")
    }

    // DRF-032, DRF-177: a preset saved with a grid
    @Test fun presetWithAGrid() {
        pods.presets.seed(PodPresetState(open = true, loaded = true, presets = listOf(
            PodPreset("1", "Friday cube", DraftSettings.booster(), cubeId = "runes", cubeName = "Runes", updatedAt = "2026-11-01T00:00:00Z"),
            PodPreset("2", "Original RTR block", DraftSettings.booster(), setCode = "RTR", setName = "Return to Ravnica",
                updatedAt = "2026-11-10T00:00:00Z", packSets = listOf("RTR", "GTC", "DGM")),
            PodPreset("3", "Sealed league", DraftSettings.sealed(), setCode = "FDN", setName = "Foundations",
                updatedAt = "2026-11-01T00:00:00Z"))))
        show(detail(DraftSettings.booster(), source("RTR", "GTC", "DGM")))
        compose.onNodeWithText("Booster · RTR, GTC, DGM · 3 packs").assertIsDisplayed()
        shot("multiset-preset", "In the setup of a Booster pod whose rounds are Return to Ravnica, Gatecrash and Dragon's Maze, tapping " +
            "Presets lists Alice's preset Original RTR block, saved with that pack grid and read as Booster · RTR, GTC, DGM · 3 packs.")
    }
}
