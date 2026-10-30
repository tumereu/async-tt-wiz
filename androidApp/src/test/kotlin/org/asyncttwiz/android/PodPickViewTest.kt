package org.asyncttwiz.android

import android.content.Context
import androidx.compose.foundation.layout.Column
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
        cardDefinitions: Map<String, CardDefinition> = definitions,
        onConfirm: (List<String>, List<String>) -> Unit = { _, _ -> },
    ) {
        compose.setContent {
            ScreenTheme { Surface {
                PodPickView(currentPack(), settings, cardDefinitions, busy(), onConfirm)
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
        assertHoldPreview(compose.onNodeWithTag("pod-pick-tray-1"))
        compose.onNodeWithTag("pod-pick-tray-1").performClick()
        assertMark("1", "Unmarked")
        assertHoldPreview(card("2"))
    }

    @Test fun tapMenuInspectOpensTheFullInspectorWithTheBackFace() {
        val double = CardDefinition("Day // Night", listOf(DefinitionFace("day", "Day Side"), DefinitionFace("night", "Night Side")),
            layout = "transform")
        show(cardDefinitions = definitions + ("definition-1" to double))
        card("1").performClick()
        compose.onNodeWithText("Inspect").performClick()
        compose.onNodeWithText("Keep").assertDoesNotExist()
        compose.onNodeWithText("Day Side").assertIsDisplayed()
        compose.onNodeWithContentDescription("Next face").performClick()
        compose.onNodeWithText("Night Side").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close card inspection").performClick()
        compose.onNodeWithText("Night Side").assertDoesNotExist()
        assertMark("1", "Unmarked")
        // Screen readers reach it without the menu too.
        val action = card("2").fetchSemanticsNode().config[SemanticsActions.CustomActions].single { it.label == "Inspect" }
        compose.runOnIdle { action.action() }
        compose.onNode(hasText("Card 2") and hasAnyAncestor(hasContentDescription("Card inspection content"))).assertIsDisplayed()
        compose.onNodeWithContentDescription("Next face").assertDoesNotExist()
    }

    private fun assertHoldPreview(target: SemanticsNodeInteraction) {
        target.performTouchInput { down(center); advanceEventTime(80) }
        compose.mainClock.advanceTimeBy(80)
        compose.onNodeWithTag("pod-pick-art-preview").assertDoesNotExist()
        target.performTouchInput { advanceEventTime(80) }
        compose.mainClock.advanceTimeBy(80)
        compose.onNodeWithTag("pod-pick-art-preview").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close card inspection").assertDoesNotExist()
        compose.onNodeWithText("Enlarge artwork").assertDoesNotExist()
        target.performTouchInput { up() }
        compose.onNodeWithTag("pod-pick-art-preview").assertDoesNotExist()
    }

    @Test fun librarianChipHoldSlotsRefundAndConfirmKeepTrayGeometryStable() {
        val librarian = DraftCopy("l1", "librarian", librarian = true)
        var confirmed: List<String>? = null
        compose.setContent { ScreenTheme { Surface {
            PodPickView(pack, DraftSettings(keep = 1, burn = 2), definitions, false, { _, _ -> },
                librarians = listOf(librarian), onConfirmLibrarians = { _, _, spent -> confirmed = spent })
        } } }
        val keep = compose.onNodeWithTag("pod-pick-keep-tray")
        val burn = compose.onNodeWithTag("pod-pick-burn-tray")
        val beforeKeep = keep.fetchSemanticsNode().boundsInRoot
        val beforeBurn = burn.fetchSemanticsNode().boundsInRoot
        val chip = compose.onNodeWithContentDescription("Librarian, 1 left")
        assertHoldPreview(chip)
        chip.performClick()
        compose.onNodeWithText("Confirm · 1 Librarian").assertIsNotEnabled()
        assertEquals(beforeKeep, keep.fetchSemanticsNode().boundsInRoot)
        assertEquals(beforeBurn, burn.fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithTag("pod-librarian-slot-l1").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Librarian, 1 left").assertExists().performScrollTo().performClick()
        for (id in listOf("1", "2")) { card(id).performClick(); compose.onNodeWithText("Keep").performClick() }
        for (id in listOf("3", "4")) { card(id).performClick(); compose.onNodeWithText("Burn").performClick() }
        compose.onNodeWithText("Confirm · 1 Librarian").assertIsEnabled().performClick()
        assertEquals(listOf("l1"), confirmed)
        assertEquals(beforeKeep, keep.fetchSemanticsNode().boundsInRoot)
        assertEquals(beforeBurn, burn.fetchSemanticsNode().boundsInRoot)
    }

    @Test fun aSpentLibrarianShowsInThePackAndCanBeBurnedKeptOrRefunded() {
        val librarian = DraftCopy("l1", "librarian", librarian = true)
        var confirmed: Triple<List<String>, List<String>, List<String>>? = null
        compose.setContent { ScreenTheme { Surface {
            // A small pack keeps every card, the spent Librarian included, composed in the lazy grid.
            PodPickView(pack.copy(cards = pack.cards.take(6)), DraftSettings(keep = 1, burn = 2), definitions, false, { _, _ -> },
                librarians = listOf(librarian), onConfirmLibrarians = { keep, burn, spent -> confirmed = Triple(keep, burn, spent) })
        } } }
        card("l1").assertDoesNotExist()
        compose.onNodeWithContentDescription("Librarian, 1 left").performScrollTo().performClick()
        card("l1").performScrollTo().assertExists()
        // Refund from the card's own menu takes it back out of the pack.
        card("l1").performClick(); compose.onNodeWithText("Refund Librarian").performClick()
        card("l1").assertDoesNotExist()
        compose.onNodeWithContentDescription("Librarian, 1 left").performScrollTo().performClick()
        // Burn it like any card: one of the two burns.
        card("l1").performScrollTo().performClick(); compose.onNodeWithText("Burn").performClick()
        assertMark("l1", "Burn")
        for (id in listOf("1", "2")) { card(id).performScrollTo().performClick(); compose.onNodeWithText("Keep").performClick() }
        card("3").performScrollTo().performClick(); compose.onNodeWithText("Burn").performClick()
        compose.onNodeWithText("Confirm · 1 Librarian").assertIsEnabled()
        // Keep it back instead (the oldest keep makes room), then refund from the dashed slot clears its mark.
        card("l1").performScrollTo().performClick(); compose.onNodeWithText("Keep").performClick()
        assertMark("l1", "Keep")
        card("4").performScrollTo().performClick(); compose.onNodeWithText("Burn").performClick()
        compose.onNodeWithText("Confirm · 1 Librarian").assertIsEnabled().performClick()
        assertEquals(Triple(listOf("2", "l1"), listOf("3", "4"), listOf("l1")), confirmed)
        compose.onNodeWithTag("pod-librarian-slot-l1").performScrollTo().performClick()
        card("l1").assertDoesNotExist()
        compose.onNodeWithText("Confirm").assertIsEnabled()
    }

    private fun hold(target: SemanticsNodeInteraction, settle: Boolean = false) {
        target.performTouchInput { down(center); advanceEventTime(160) }
        compose.mainClock.advanceTimeBy(160)
        compose.onNodeWithTag("pod-pick-art-preview").assertIsDisplayed()
        if (settle) {
            target.performTouchInput { advanceEventTime(PodHoldSettleMillis + 20) }
            compose.mainClock.advanceTimeBy(PodHoldSettleMillis + 20)
        }
    }

    @Test fun horizontalMoveEarlyInAHoldClosesThePreviewAndSwipes() {
        show()
        hold(card("1"))
        card("1").performTouchInput { moveBy(Offset(width * .3f, 0f)); moveBy(Offset(width * .3f, 0f)) }
        compose.onNodeWithTag("pod-pick-art-preview").assertDoesNotExist()
        compose.onNodeWithTag("pod-pick-swipe-copy-1").assertExists()
        card("1").performTouchInput { up() }
        assertMark("1", "Keep")
        compose.onNodeWithText("Keep").assertDoesNotExist()
    }

    @Test fun verticalMoveEarlyInAHoldClosesThePreviewAndScrollsTheList() {
        show()
        val before = card("1").getUnclippedBoundsInRoot().top
        hold(card("4"))
        card("4").performTouchInput { repeat(6) { moveBy(Offset(0f, -height * .2f)) } }
        compose.onNodeWithTag("pod-pick-art-preview").assertDoesNotExist()
        card("4").performTouchInput { up() }
        assertTrue(card("1").getUnclippedBoundsInRoot().top < before)
        assertMark("4", "Unmarked")
        compose.onNodeWithText("Keep").assertDoesNotExist()
    }

    @Test fun settledHoldOwnsVerticalMotionAndHorizontalMoveDismissesWithoutMarking() {
        show()
        val before = card("1").getUnclippedBoundsInRoot().top
        hold(card("4"), settle = true)
        card("4").performTouchInput { repeat(3) { moveBy(Offset(0f, -height * .2f)) }; repeat(3) { moveBy(Offset(0f, height * .2f)) } }
        compose.onNodeWithTag("pod-pick-art-preview").assertIsDisplayed()
        assertEquals(before, card("1").getUnclippedBoundsInRoot().top)
        card("4").performTouchInput { moveBy(Offset(width * .6f, 0f)) }
        compose.onNodeWithTag("pod-pick-art-preview").assertDoesNotExist()
        card("4").performTouchInput { up() }
        assertMark("4", "Unmarked")
        compose.onNodeWithText("Keep").assertDoesNotExist()
    }

    @Test fun simultaneousTapsOpenOnlyTheLatestCardMenu() {
        show()
        val first = card("1").fetchSemanticsNode().boundsInRoot.center
        val second = card("2").fetchSemanticsNode().boundsInRoot.center
        compose.onRoot().performTouchInput { down(0, first); down(1, second); up(0); up(1) }
        compose.onAllNodesWithText("Keep").assertCountEquals(1)
        compose.onNodeWithText("Keep").performClick()
        assertMark("2", "Keep")
        assertMark("1", "Unmarked")
    }

    @Test fun trayCountIsACornerChipOverTheCards() {
        show()
        swipe("1", true)
        val chip = compose.onNodeWithContentDescription("Keep 1/1").fetchSemanticsNode().boundsInRoot
        val tray = compose.onNodeWithTag("pod-pick-keep-tray").fetchSemanticsNode().boundsInRoot
        val trayCard = compose.onNodeWithTag("pod-pick-tray-1").fetchSemanticsNode().boundsInRoot
        assertTrue(tray.contains(chip.topLeft) && tray.contains(chip.bottomRight - Offset(1f, 1f)))
        assertTrue(chip.overlaps(trayCard))
        compose.onNodeWithTag("pod-pick-tray-1").performClick()
        assertMark("1", "Unmarked")
    }

    @Test fun automaticBurnTrayWaitsForKeepAndIsNotSelectableAndSendsRest() {
        var result: Pair<List<String>, List<String>>? = null
        show(currentPack = { pack.copy(cards = pack.cards.take(3)) }, onConfirm = { keep, burn -> result = keep to burn })
        compose.onNodeWithTag("pod-pick-tray-2").assertDoesNotExist()
        assertMark("2", "Unmarked")
        card("1").performClick()
        compose.onNodeWithText("Burn").assertDoesNotExist()
        compose.onNodeWithText("Keep").performClick()
        compose.onNodeWithTag("pod-pick-tray-2").assertExists().assertHasNoClickAction()
        assertMark("2", "Automatic burn")
        assertHoldPreview(compose.onNodeWithTag("pod-pick-tray-2"))
        compose.onNodeWithTag("pod-pick-tray-1").performClick()
        assertMark("2", "Unmarked")
        compose.onNodeWithTag("pod-pick-tray-2").assertDoesNotExist()
        swipe("1", true)
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

    @Test fun busyOnlyDisablesConfirmationAndNewPackResetsMarks() {
        var current by mutableStateOf(pack)
        var busy by mutableStateOf(false)
        show(currentPack = { current }, settings = DraftSettings(), busy = { busy })
        swipe("1", true)
        compose.onNodeWithText("Confirm").assertIsEnabled()
        compose.runOnIdle { busy = true }
        compose.onNodeWithText("Confirm").assertIsNotEnabled()
        swipe("2", true)
        assertMark("2", "Keep")
        compose.runOnIdle { busy = false; current = pack.copy(id = "second") }
        assertMark("1", "Unmarked")
        compose.onNodeWithText("Confirm").assertIsNotEnabled()
    }

    @Test fun queuedPackIsPickableBeforeResponseAndRejectionRestoresOriginalMarks() {
        val next = pack.copy(id = "next", cards = pack.cards.map { it.copy(id = "next-${it.id}") })
        var current by mutableStateOf<DraftPrivatePack?>(pack)
        val picks = mutableListOf<List<String>>()
        show(currentPack = { current }, settings = DraftSettings(), onConfirm = { keep, _ ->
            picks += keep
            current = if (current == pack) next else null
        })
        swipe("1", true)
        compose.onNodeWithText("Confirm").performClick()
        card("1").assertDoesNotExist()
        card("next-1").assertIsDisplayed()
        compose.onNodeWithTag("pod-pick-keep-tray").assertIsDisplayed()
        compose.onNodeWithText("Build").assertDoesNotExist()
        swipe("next-2", true)
        compose.onNodeWithText("Confirm").assertIsEnabled().performClick()
        assertEquals(listOf(listOf("1"), listOf("next-2")), picks)
        compose.onNodeWithText("Waiting for other players").assertIsDisplayed().assertHasNoClickAction()
        // Rejection of the first request restores that pack, not the later selection.
        compose.runOnIdle { current = pack }
        assertMark("1", "Keep")
        assertMark("2", "Unmarked")
        compose.onNodeWithTag("pod-pick-tray-1").assertIsDisplayed()
        compose.onNodeWithText("Confirm").assertIsEnabled()
    }

    @Test fun lastQueuedPackShowsNormalWaitingImmediatelyAndRejectionRestoresMarks() {
        var current by mutableStateOf<DraftPrivatePack?>(pack)
        show(currentPack = { current }, settings = DraftSettings(),
            onConfirm = { _, _ -> current = null })
        swipe("1", true)
        compose.onNodeWithText("Confirm").performClick()
        card("1").assertDoesNotExist()
        compose.onNodeWithTag("pod-pick-keep-tray").assertDoesNotExist()
        compose.onNodeWithText("Waiting for other players").assertIsDisplayed().assertHasNoClickAction()
        compose.onNodeWithText("Build").assertDoesNotExist()
        compose.runOnIdle { current = pack }
        assertMark("1", "Keep")
        compose.onNodeWithText("Confirm").assertIsEnabled()
    }

    @Test fun smallTraysShareOneRowAndKeepTheirSizeWhileMarking() {
        show()
        val keep = compose.onNodeWithTag("pod-pick-keep-tray")
        val burn = compose.onNodeWithTag("pod-pick-burn-tray")
        val initialKeep = keep.fetchSemanticsNode().boundsInRoot
        val initialBurn = burn.fetchSemanticsNode().boundsInRoot
        assertEquals(initialKeep.top, initialBurn.top)
        assertTrue(initialKeep.right < initialBurn.left)
        swipe("1", true)
        swipe("2", false)
        assertEquals(initialKeep, keep.fetchSemanticsNode().boundsInRoot)
        assertEquals(initialBurn, burn.fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithText("Keep").assertDoesNotExist()
        compose.onNodeWithText("Burn").assertDoesNotExist()
    }

    @Test fun verticalScrollingKeepsTraysAndFullWidthConfirmFixed() {
        show()
        val tray = compose.onNodeWithTag("pod-pick-keep-tray").fetchSemanticsNode().boundsInRoot
        val confirm = compose.onNodeWithText("Confirm").fetchSemanticsNode().boundsInRoot
        val confirmButton = compose.onNodeWithText("Confirm").getUnclippedBoundsInRoot()
        val list = compose.onNodeWithTag("pod-pick-list").getUnclippedBoundsInRoot()
        assertEquals(list.right - list.left, confirmButton.right - confirmButton.left)
        compose.onNodeWithTag("pod-pick-list").performTouchInput { swipeUp() }
        card("15").assertIsDisplayed()
        assertEquals(tray, compose.onNodeWithTag("pod-pick-keep-tray").fetchSemanticsNode().boundsInRoot)
        assertEquals(confirm, compose.onNodeWithText("Confirm").fetchSemanticsNode().boundsInRoot)
    }

    @Test fun packCaptionsDoNotTakeSpaceFromCards() {
        show()
        compose.onNodeWithText("Pack 2/9", substring = true).assertDoesNotExist()
        compose.onNodeWithText("waiting", substring = true).assertDoesNotExist()
    }

    @Test fun missingArtworkKeepsCardShapeAndCompactTraysScrollWithoutGrowing() {
        val longName = "A very long card name that must never expand a card tile or tray even when no artwork has loaded".repeat(2)
        show(settings = DraftSettings(keep = 1, burn = 14), cardDefinitions = definitions.mapValues { (_, definition) -> definition.copy(name = longName) })
        compose.onNodeWithContentDescription("Cards per row").assertDoesNotExist()
        val cardBounds = card("1").fetchSemanticsNode().boundsInRoot
        assertTrue(abs(cardBounds.height / cardBounds.width - 1.4f) < .01f)
        val results = mutableListOf<TextLayoutResult>()
        compose.onAllNodesWithText(longName, useUnmergedTree = true).onFirst()
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        assertTrue(results.single().lineCount <= 3)
        swipe("1", true)
        val keepBounds = compose.onNodeWithTag("pod-pick-keep-tray").fetchSemanticsNode().boundsInRoot
        val burnBounds = compose.onNodeWithTag("pod-pick-burn-tray").fetchSemanticsNode().boundsInRoot
        assertTrue(burnBounds.top >= keepBounds.bottom)
        assertTrue(keepBounds.width < burnBounds.width)
        assertTrue(with(compose.onNodeWithTag("pod-pick-keep-tray").getUnclippedBoundsInRoot()) { bottom - top } <= 64.dp)
        assertTrue(with(compose.onNodeWithTag("pod-pick-burn-tray").getUnclippedBoundsInRoot()) { bottom - top } <= 64.dp)
        compose.onNodeWithTag("pod-pick-tray-1").assertHeightIsEqualTo(48.dp)
        compose.onNodeWithTag("pod-pick-burn-cards").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("pod-pick-art-preview").assertDoesNotExist()
        compose.onNodeWithTag("pod-pick-swipe-copy-2").assertDoesNotExist()
        assertMark("1", "Keep")
        compose.onNodeWithTag("pod-pick-tray-15").performScrollTo().assertIsDisplayed()
        assertTrue(with(compose.onNodeWithTag("pod-pick-burn-tray").getUnclippedBoundsInRoot()) { bottom - top } <= 64.dp)
    }

    @Test fun overflowingKeepTrayPansWithoutUnmarkingHoldingOrSwipingGridCards() {
        show(settings = DraftSettings(keep = 14))
        for (id in 1..14) {
            compose.onNodeWithTag("pod-pick-list").performScrollToNode(hasTestTag("pod-pick-card-$id"))
            card("$id").performClick()
            compose.onNodeWithText("Keep").performClick()
        }
        val tray = compose.onNodeWithTag("pod-pick-keep-cards")
        compose.onNodeWithTag("pod-pick-tray-1").assertIsDisplayed()
        val firstPosition = compose.onNodeWithTag("pod-pick-tray-1").getUnclippedBoundsInRoot().left
        tray.performTouchInput { swipeLeft() }
        assertTrue(compose.onNodeWithTag("pod-pick-tray-1").getUnclippedBoundsInRoot().left < firstPosition)
        compose.onNodeWithTag("pod-pick-art-preview").assertDoesNotExist()
        compose.onAllNodes(hasTestTag("pod-pick-swipe-copy-1")).assertCountEquals(0)
        compose.onNodeWithContentDescription("Keep 14/14").assertExists()
        compose.onNodeWithText("Confirm").assertIsEnabled()
        compose.onNodeWithTag("pod-pick-tray-14").performScrollTo().assertIsDisplayed()
    }

    private fun showWithSettings(currentPack: () -> DraftPrivatePack?) {
        compose.setContent { ScreenTheme { Surface { Column {
            val columns = rememberPodPickColumns()
            PodViewSettingsButton(columns, rememberPodBuildColumns())
            PodPickView(currentPack(), DraftSettings.glimpse(), definitions, false, { _, _ -> }, columns = columns)
        } } } }
    }

    /** Cards 1..[count] share the first row and the next card starts a new one. */
    private fun assertRowOf(count: Int) {
        val first = card("1").fetchSemanticsNode().boundsInRoot
        assertEquals(first.top, card("$count").fetchSemanticsNode().boundsInRoot.top, .5f)
        // A card not composed at all lies outside the visible first row as well.
        compose.onAllNodesWithTag("pod-pick-card-${count + 1}").fetchSemanticsNodes().firstOrNull()
            ?.let { assertTrue(it.boundsInRoot.top > first.bottom) }
    }

    @Test fun portraitColumnChoiceIsSavedAndClampedToCurrentPackSize() {
        var current by mutableStateOf(pack)
        showWithSettings { current }
        compose.onNodeWithContentDescription("View settings").performClick()
        val slider = compose.onNodeWithContentDescription("Cards per row")
        slider.assertColumns(3, 15)
        assertRowOf(3)
        slider.performSemanticsAction(SemanticsActions.SetProgress) { it(7f) }
        compose.runOnIdle { assertEquals(7, preferences.getInt("columns-portrait", 0)) }
        assertRowOf(7)
        compose.runOnIdle { current = pack.copy(id = "short", cards = pack.cards.take(2)) }
        // The panel keeps the preference; the view clamps it to the two cards left.
        slider.assertColumns(7, 15)
        val first = card("1").fetchSemanticsNode().boundsInRoot
        val second = card("2").fetchSemanticsNode().boundsInRoot
        assertEquals(first.top, second.top, .5f)
        assertTrue(first.width > compose.onNodeWithTag("pod-pick-list").fetchSemanticsNode().boundsInRoot.width / 3f)
        compose.runOnIdle { current = pack.copy(id = "next") }
        assertRowOf(7)
    }

    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi")
    fun landscapeUsesFiveColumnsAndKeepsControlsVisible() {
        preferences.edit().putInt("columns-portrait", 7).commit()
        showWithSettings { pack }
        assertRowOf(5)
        compose.onNodeWithContentDescription("View settings").performClick()
        compose.onNodeWithContentDescription("Cards per row").assertColumns(5, 15)
        androidx.test.espresso.Espresso.pressBack()
        card("1").assertIsDisplayed()
        compose.onNodeWithTag("pod-pick-keep-tray").assertIsDisplayed()
        compose.onNodeWithTag("pod-pick-burn-tray").assertIsDisplayed()
        compose.onNodeWithText("Confirm").assertIsDisplayed()
    }

    @Test fun emptyQueueShowsCentredWaitingWithoutBuildButtonOrPickControls() {
        show(currentPack = { null })
        val waiting = compose.onNodeWithText("Waiting for other players").assertIsDisplayed().assertHasNoClickAction()
        compose.onNodeWithText("Build").assertDoesNotExist()
        compose.onNodeWithText("Confirm").assertDoesNotExist()
        compose.onNodeWithContentDescription("Cards per row").assertDoesNotExist()
        compose.onNodeWithContentDescription("Bea's avatar").assertDoesNotExist()
        val bounds = waiting.fetchSemanticsNode().boundsInRoot
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertEquals(root.center.x, bounds.center.x, 1f)
        assertEquals(root.height / 2f, bounds.center.y, 1f)
    }

    @Test fun waitingDecisionDependsOnQueueNotPendingConfirmation() {
        var current by mutableStateOf<DraftPrivatePack?>(null)
        var busy by mutableStateOf(true)
        show(currentPack = { current }, busy = { busy })
        compose.onNodeWithText("Waiting for other players").assertIsDisplayed()
        compose.runOnIdle { busy = false }
        compose.onNodeWithText("Waiting for other players").assertIsDisplayed()
        compose.runOnIdle { current = pack; busy = true }
        compose.onNodeWithText("Waiting for other players").assertDoesNotExist()
        card("1").assertIsDisplayed()
    }

    @Test fun waitingNamesWhoWeWaitForWithACentredMainMenuLink() {
        var menus = 0
        compose.setContent { ScreenTheme { Surface {
            PodPickView(null, DraftSettings.glimpse(), definitions, false, { _, _ -> },
                waitingText = "Waiting for Bob", onMainMenu = { menus++ })
        } } }
        val text = compose.onNodeWithText("Waiting for Bob").assertIsDisplayed().assertHasNoClickAction()
            .fetchSemanticsNode().boundsInRoot
        val link = compose.onNodeWithText("Main menu").assertIsDisplayed().assertHasClickAction()
        val linkBounds = link.fetchSemanticsNode().boundsInRoot
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertEquals(root.center.x, text.center.x, 1f)
        assertEquals(root.center.x, linkBounds.center.x, 1f)
        assertTrue(linkBounds.top >= text.bottom)
        assertEquals(root.center.y, (text.top + linkBounds.bottom) / 2f, 1f)
        compose.onNodeWithText("Build").assertDoesNotExist()
        link.performClick()
        compose.runOnIdle { assertEquals(1, menus) }
    }
}
