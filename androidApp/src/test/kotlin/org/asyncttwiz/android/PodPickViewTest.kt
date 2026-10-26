package org.asyncttwiz.android

import android.content.Context
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import org.asyncttwiz.protocol.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class PodPickViewTest {
    @get:Rule val compose = createComposeRule()
    private val pack = DraftPrivatePack("first", 2, (1..15).map { DraftCopy("$it", "definition-$it") })
    private val definitions = pack.cards.associate { it.definition to CardDefinition("Card ${it.id}", listOf(DefinitionFace("face", "Card ${it.id}"))) }
    private val preferences get() = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("pod-pick", Context.MODE_PRIVATE)

    @Before fun resetPreferences() { preferences.edit().clear().commit() }

    private fun show(
        currentPack: () -> DraftPrivatePack? = { pack },
        settings: DraftSettings = DraftSettings.glimpse(),
        busy: () -> Boolean = { false },
        waitingOn: List<AccountIdentity> = emptyList(),
        cardDefinitions: Map<String, CardDefinition> = definitions,
        onBuild: () -> Unit = {},
        onConfirm: (List<String>, List<String>) -> Unit = { _, _ -> },
    ) {
        compose.setContent {
            ScreenTheme { Surface {
                PodPickView(currentPack(), 2, settings, cardDefinitions, waitingOn, busy(), onConfirm, onBuild)
            } }
        }
    }

    private fun card(id: String) = compose.onNodeWithTag("pod-pick-card-$id")
    private fun assertMark(id: String, mark: String) = card(id).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, mark))
    private fun SemanticsNodeInteraction.assertColumns(current: Int, maximum: Int) = assert(SemanticsMatcher("$current columns, range 1..$maximum") {
        val range = it.config[SemanticsProperties.ProgressBarRangeInfo]
        abs(range.current - current) < .001f && range.range == 1f..maximum.toFloat() && range.steps == (maximum - 2).coerceAtLeast(0)
    })
    private fun swipe(id: String, right: Boolean) {
        card(id).performTouchInput {
            val start = Offset(if (right) width * .15f else width * .85f, height / 2f)
            val end = Offset(if (right) width * .85f else width * .15f, height / 2f)
            swipe(start, end, 400)
        }
    }

    @Test fun swipeTogglesSwitchesAndDropsEarliestWhileMenuConfirmsCopyIds() {
        var confirmed: Pair<List<String>, List<String>>? = null
        show(settings = DraftSettings(keep = 1, burn = 1), onConfirm = { keep, burn -> confirmed = keep to burn })
        compose.onNodeWithText("Confirm").assertIsNotEnabled()
        swipe("1", true)
        assertMark("1", "Keep")
        swipe("1", true)
        assertMark("1", "Unmarked")
        swipe("1", false)
        assertMark("1", "Burn")
        swipe("1", true)
        assertMark("1", "Keep")
        swipe("2", true)
        assertMark("1", "Unmarked")
        assertMark("2", "Keep")
        card("3").performClick()
        compose.onNodeWithText("Burn").performClick()
        assertMark("3", "Burn")
        compose.onNodeWithText("Confirm").assertIsEnabled().performClick()
        assertEquals(listOf("2") to listOf("3"), confirmed)
    }

    @Test fun menuUnmarkTrayTapAndHoldInspectionWork() {
        show()
        card("1").performClick()
        compose.onNodeWithText("Keep").performClick()
        card("1").performClick()
        compose.onNodeWithText("Unmark").performClick()
        assertMark("1", "Unmarked")
        swipe("1", true)
        compose.onNodeWithTag("pod-pick-tray-1").performTouchInput { longClick() }
        compose.onNodeWithContentDescription("Close card inspection").assertIsDisplayed().performClick()
        compose.onNodeWithTag("pod-pick-tray-1").performClick()
        assertMark("1", "Unmarked")
        card("2").performTouchInput { longClick() }
        compose.onNodeWithContentDescription("Close card inspection").assertIsDisplayed().performClick()
    }

    @Test fun automaticBurnTrayIsPrefilledAndNotSelectableAndSendsRest() {
        var result: Pair<List<String>, List<String>>? = null
        show(currentPack = { pack.copy(cards = pack.cards.take(3)) }, onConfirm = { keep, burn -> result = keep to burn })
        compose.onNodeWithTag("pod-pick-tray-2").performClick()
        assertMark("2", "Automatic burn")
        card("1").performClick()
        compose.onNodeWithText("Burn").assertDoesNotExist()
        compose.onNodeWithText("Keep").performClick()
        compose.onNodeWithText("Confirm").performClick()
        assertEquals(listOf("1") to listOf("2", "3"), result)
    }

    @Test fun boosterLeftSwipeDoesNothingAndHasNoBurnMenuOrTray() {
        show(settings = DraftSettings())
        swipe("1", true)
        swipe("1", false)
        assertMark("1", "Keep")
        compose.onNodeWithTag("pod-pick-burn-tray").assertDoesNotExist()
        card("2").performClick()
        compose.onNodeWithText("Burn").assertDoesNotExist()
        compose.onNodeWithText("Keep").performClick()
        assertMark("2", "Keep")
    }

    @Test fun busyDisablesConfirmationAndNewPackResetsMarks() {
        var current by mutableStateOf(pack)
        var busy by mutableStateOf(false)
        show(currentPack = { current }, settings = DraftSettings(), busy = { busy })
        swipe("1", true)
        compose.onNodeWithText("Confirm").assertIsEnabled()
        compose.runOnIdle { busy = true }
        compose.onNodeWithText("Confirming…").assertIsNotEnabled()
        swipe("2", true)
        assertMark("2", "Unmarked")
        compose.runOnIdle { busy = false; current = pack.copy(id = "second") }
        assertMark("1", "Unmarked")
        compose.onNodeWithText("Confirm").assertIsNotEnabled()
    }

    @Test fun verticalScrollingKeepsHeaderTraysAndConfirmFixed() {
        show()
        val header = compose.onNodeWithText("Pack 2/9 · 15 cards · +2 packs waiting").fetchSemanticsNode().boundsInRoot
        val tray = compose.onNodeWithTag("pod-pick-keep-tray").fetchSemanticsNode().boundsInRoot
        val confirm = compose.onNodeWithText("Confirm").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("pod-pick-list").performTouchInput { swipeUp() }
        card("15").assertIsDisplayed()
        assertEquals(header, compose.onNodeWithText("Pack 2/9 · 15 cards · +2 packs waiting").fetchSemanticsNode().boundsInRoot)
        assertEquals(tray, compose.onNodeWithTag("pod-pick-keep-tray").fetchSemanticsNode().boundsInRoot)
        assertEquals(confirm, compose.onNodeWithText("Confirm").fetchSemanticsNode().boundsInRoot)
    }

    @Test fun missingArtworkKeepsCardShapeAndCompactTraysScrollWithoutGrowing() {
        val longName = "A very long card name that must never expand a card tile or tray even when no artwork has loaded".repeat(2)
        show(settings = DraftSettings(keep = 1, burn = 14), cardDefinitions = definitions.mapValues { (_, definition) -> definition.copy(name = longName) })
        assertTrue(with(compose.onNodeWithContentDescription("Cards per row").getUnclippedBoundsInRoot()) { bottom - top } <= 40.dp)
        val cardBounds = card("1").fetchSemanticsNode().boundsInRoot
        assertTrue(abs(cardBounds.height / cardBounds.width - 1.4f) < .01f)
        val results = mutableListOf<TextLayoutResult>()
        compose.onAllNodesWithText(longName, useUnmergedTree = true).onFirst()
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        assertTrue(results.single().lineCount <= 3)
        swipe("1", true)
        assertTrue(with(compose.onNodeWithTag("pod-pick-keep-tray").getUnclippedBoundsInRoot()) { bottom - top } <= 64.dp)
        assertTrue(with(compose.onNodeWithTag("pod-pick-burn-tray").getUnclippedBoundsInRoot()) { bottom - top } <= 64.dp)
        compose.onNodeWithTag("pod-pick-tray-1").assertHeightIsEqualTo(48.dp)
        compose.onNodeWithTag("pod-pick-tray-15").performScrollTo().assertIsDisplayed()
        assertTrue(with(compose.onNodeWithTag("pod-pick-burn-tray").getUnclippedBoundsInRoot()) { bottom - top } <= 64.dp)
    }

    @Test fun portraitColumnChoiceIsSavedAndClampedToCurrentPackSize() {
        var current by mutableStateOf(pack)
        show(currentPack = { current })
        val slider = compose.onNodeWithContentDescription("Cards per row")
        slider.assertColumns(3, 15)
        slider.performSemanticsAction(SemanticsActions.SetProgress) { it(7f) }
        compose.runOnIdle { assertEquals(7, preferences.getInt("columns-portrait", 0)); current = pack.copy(id = "short", cards = pack.cards.take(2)) }
        slider.assertColumns(2, 2)
        compose.runOnIdle { current = pack.copy(id = "next") }
        slider.assertColumns(7, 15)
    }

    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi")
    fun landscapeUsesFiveColumnsAndKeepsControlsVisible() {
        preferences.edit().putInt("columns-portrait", 7).commit()
        show()
        compose.onNodeWithContentDescription("Cards per row")
            .assertColumns(5, 15)
        card("1").assertIsDisplayed()
        compose.onNodeWithTag("pod-pick-keep-tray").assertIsDisplayed()
        compose.onNodeWithTag("pod-pick-burn-tray").assertIsDisplayed()
        compose.onNodeWithText("Confirm").assertIsDisplayed()
    }

    @Test fun waitingStateShowsNamesAvatarsAndBuildOrNextRound() {
        var builds = 0
        show(currentPack = { null }, waitingOn = listOf(AccountIdentity("b", "Bea"), AccountIdentity("c", "Cal")), onBuild = { builds++ })
        compose.onNodeWithText("Waiting for Bea, Cal").assertIsDisplayed()
        compose.onNodeWithContentDescription("Bea's avatar").assertIsDisplayed()
        compose.onNodeWithContentDescription("Cal's avatar").assertIsDisplayed()
        compose.onNodeWithText("Build").performClick()
        assertEquals(1, builds)
    }

    @Test fun noPackAndNobodyHoldingPacksWaitsForNextRound() {
        show(currentPack = { null })
        compose.onNodeWithText("Waiting for the next round").assertIsDisplayed()
        compose.onNodeWithText("Build").assertIsDisplayed()
    }
}
