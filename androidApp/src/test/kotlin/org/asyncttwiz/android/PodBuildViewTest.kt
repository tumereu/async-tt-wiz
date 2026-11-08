package org.asyncttwiz.android

import android.content.Context
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import org.asyncttwiz.protocol.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class PodBuildViewTest {
    @get:Rule val compose = createComposeRule()
    private val card = CardDefinition("Test card", listOf(DefinitionFace("front", "Test card", typeLine = "Creature", manaCost = "{1}")))
    private val tile = PodBuildTile("digest", listOf("first", "second"), card, emptySet())
    private val moves = mutableListOf<Pair<String, PodDeckCategory>>()

    @Before fun clearPreferences() {
        ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("pod-build", 0).edit().clear().commit()
    }

    // DRF-098, DRF-099
    @Test fun swipesFollowTheTableAndMoveOnlyOneCopy() {
        var category by mutableStateOf(PodDeckCategory.MAIN)
        compose.setContent { MaterialTheme {
            Box(Modifier.width(160.dp)) { PodBuildCard(tile, category, true, { id, destination -> moves += id to destination }, {}) }
        } }
        for (source in PodDeckCategory.entries) {
            compose.runOnIdle { category = source }
            val node = compose.onNodeWithTag("pod-build-card-first")
            node.performTouchInput { swipeRight() }
            compose.runOnIdle { assertEquals("first" to PodDeckRules.swipe(source, PodDeckSwipe.RIGHT), moves.last()) }
            node.performTouchInput { swipeLeft() }
            compose.runOnIdle { assertEquals("first" to PodDeckRules.swipe(source, PodDeckSwipe.LEFT), moves.last()) }
        }
        assertEquals(6, moves.size)
    }

    // DRF-099, DRF-092 (SYS-054: Maybe in the card list sections' amber, its swipe tint their brighter yellow)
    @Test fun swipeProjectionTargetAndColourFollowTheSwipeTable() {
        assertEquals(PodPickGreen, PodDeckCategory.MAIN.podBuildColor())
        assertEquals(PodBuildBlue, PodDeckCategory.SIDE.podBuildColor())
        assertEquals(SectionMaybeAmber, PodDeckCategory.MAYBE.podBuildColor())
        assertEquals(SectionMaybeYellow, PodDeckCategory.MAYBE.podBuildFill())
        assertEquals(PodBuildBlue, PodDeckCategory.SIDE.podBuildFill())
        for (category in PodDeckCategory.entries) {
            assertNull(podBuildSwipeTarget(category, 0f))
            assertEquals(PodDeckRules.swipe(category, PodDeckSwipe.RIGHT), podBuildSwipeTarget(category, 1f))
            assertEquals(PodDeckRules.swipe(category, PodDeckSwipe.LEFT), podBuildSwipeTarget(category, -1f))
        }
        assertEquals(PodDeckCategory.SIDE, podBuildSwipeTarget(PodDeckCategory.MAIN, 5f))
        assertEquals(PodDeckCategory.MAYBE, podBuildSwipeTarget(PodDeckCategory.MAIN, -5f))
        assertEquals(PodDeckCategory.MAIN, podBuildSwipeTarget(PodDeckCategory.MAYBE, 5f))
        assertEquals(PodDeckCategory.SIDE, podBuildSwipeTarget(PodDeckCategory.MAYBE, -5f))
    }

    // DRF-100
    @Test fun cardStaysWhileItsProjectionFollowsTheFingerAndSnapsBackBelowTheThreshold() {
        compose.setContent { MaterialTheme {
            Box(Modifier.width(160.dp)) { PodBuildCard(tile, PodDeckCategory.MAIN, true, { id, destination -> moves += id to destination }, {}) }
        } }
        compose.mainClock.autoAdvance = false
        val card = compose.onNodeWithTag("pod-build-card-first")
        val projection = compose.onNodeWithTag("pod-build-swipe-copy-first")
        val original = card.fetchSemanticsNode().boundsInRoot
        card.performTouchInput { down(center); moveBy(androidx.compose.ui.geometry.Offset(18f, 0f), delayMillis = 16) }
        compose.mainClock.advanceTimeByFrame()
        projection.assertExists().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Side"))
        assertEquals(original, card.fetchSemanticsNode().boundsInRoot)
        card.performTouchInput { moveBy(androidx.compose.ui.geometry.Offset(-36f, 0f), delayMillis = 16) }
        compose.mainClock.advanceTimeByFrame()
        projection.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Maybe"))
        card.performTouchInput { moveBy(androidx.compose.ui.geometry.Offset(8f, 0f), delayMillis = 16); up() }
        compose.mainClock.advanceTimeByFrame()
        projection.assertExists()
        compose.mainClock.advanceTimeBy(250)
        projection.assertDoesNotExist()
        assertEquals(original, card.fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { assertTrue(moves.isEmpty()) }

        card.performTouchInput { down(center); moveBy(androidx.compose.ui.geometry.Offset(60f, 0f), delayMillis = 16); up() }
        compose.mainClock.advanceTimeBy(250)
        projection.assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf("first" to PodDeckCategory.SIDE), moves) }
    }

    // DRF-097, DRF-101
    @Test fun cardsInOneColumnSitOneDpApartAndAMovedCardFadesOutWhereItWas() {
        var build by mutableStateOf(PodDeckBuild(listOf(PodDeckCopy("first"), PodDeckCopy("second"))))
        compose.setContent { MaterialTheme {
            PodBuildView(build, mapOf("first" to DraftCopy("first", "a"), "second" to DraftCopy("second", "b")),
                mapOf("a" to card, "b" to card.copy(name = "Zed card")), null, true, false,
                { id, destination -> build = build.copy(copies = build.copies.map { if (it.copyId == id) it.copy(category = destination) else it }) },
                { _, _ -> }, {})
        } }
        val first = compose.onNodeWithTag("pod-build-card-first").getUnclippedBoundsInRoot()
        val second = compose.onNodeWithTag("pod-build-card-second").getUnclippedBoundsInRoot()
        assertEquals(first.left, second.left)
        assertEquals(PodBuildCardGap, second.top - first.bottom)
        assertEquals(1.dp, PodBuildCardGap)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("pod-build-card-first").performTouchInput { swipeRight() }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { assertEquals(PodDeckCategory.SIDE, build.copies.first().category) }
        compose.onNodeWithTag("pod-build-leaving-first").assertExists()
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithTag("pod-build-leaving-first").assertDoesNotExist()
        compose.mainClock.autoAdvance = true
        // The remaining card slid up into the freed place.
        assertEquals(first.top, compose.onNodeWithTag("pod-build-card-second").getUnclippedBoundsInRoot().top)
        compose.onNodeWithText("Side 1").assertExists()
    }

    // DRF-102
    @Test fun tapOffersTwoDestinationsAndHoldInspects() {
        var inspections = 0
        compose.setContent { MaterialTheme {
            Box(Modifier.width(160.dp)) { PodBuildCard(tile, PodDeckCategory.MAIN, true,
                { id, destination -> moves += id to destination }, { if (it) inspections++ }) }
        } }
        compose.onNodeWithText("×2").assertExists()
        compose.onNodeWithTag("pod-build-card-first").performClick()
        compose.onNodeWithText("Inspect").assertDoesNotExist()
        compose.onNodeWithText("→ Side").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(listOf("first" to PodDeckCategory.SIDE), moves) }
        compose.onNodeWithTag("pod-build-card-first").performTouchInput { longClick() }
        compose.runOnIdle { assertEquals(1, inspections) }
        compose.onNodeWithText("→ Maybe").assertDoesNotExist()
    }

    // DRF-102
    @Test fun submittedCardsHaveNoMoveGesturesAndHoldingStillInspects() {
        var inspections = 0
        compose.setContent { MaterialTheme {
            Box(Modifier.width(160.dp)) { PodBuildCard(tile, PodDeckCategory.MAIN, false,
                { id, destination -> moves += id to destination }, { if (it) inspections++ }) }
        } }
        val node = compose.onNodeWithTag("pod-build-card-first")
        node.performTouchInput { swipeRight() }
        node.performTouchInput { swipeLeft() }
        node.performTouchInput { longClick() }
        compose.runOnIdle { assertTrue(moves.isEmpty()); assertEquals(1, inspections) }
        node.performClick()
        compose.onNodeWithText("→ Side").assertIsNotEnabled()
        compose.onNodeWithText("→ Maybe").assertIsNotEnabled()
        compose.onNodeWithText("Inspect").assertDoesNotExist()
    }

    // DRF-102, TBL-104, TBL-106: holding a double-faced card in the deck builder shows both faces, only as pictures
    @Test fun holdingADoubleFacedCardShowsEveryFaceUntilRelease() {
        val double = CardDefinition("Day // Night", listOf(DefinitionFace("day", "Day Side", rulesText = "Day rules."),
            DefinitionFace("night", "Night Side", rulesText = "Night rules.")), layout = "transform")
        compose.setContent { MaterialTheme {
            PodBuildView(PodDeckBuild(listOf(PodDeckCopy("first"))), mapOf("first" to DraftCopy("first", "a")),
                mapOf("a" to double), null, true, false, { _, _ -> }, { _, _ -> }, {})
        } }
        val card = compose.onNodeWithTag("pod-build-card-first")
        card.performTouchInput { down(center); advanceEventTime(200) }
        compose.mainClock.advanceTimeBy(200)
        val day = compose.onNodeWithContentDescription("Card image: Day Side").assertExists().getBoundsInRoot()
        val night = compose.onNodeWithContentDescription("Card image: Night Side").assertExists().getBoundsInRoot()
        assertTrue(day.bottom <= night.top, "Portrait stacks the faces, front first")
        compose.onNodeWithText("Day rules.").assertDoesNotExist()
        compose.onNodeWithText("Night rules.").assertDoesNotExist()
        card.performTouchInput { up() }
        compose.onNodeWithTag("card-inspection").assertDoesNotExist()
    }

    @Test fun verticalDragOnACardScrollsInsteadOfMovingIt() {
        lateinit var scroll: ScrollState
        compose.setContent { MaterialTheme {
            scroll = rememberScrollState()
            Column(Modifier.width(160.dp).height(300.dp).verticalScroll(scroll)) {
                PodBuildCard(tile, PodDeckCategory.MAIN, true, { id, destination -> moves += id to destination }, {})
                Spacer(Modifier.height(1000.dp))
            }
        } }
        compose.onNodeWithTag("pod-build-card-first").performTouchInput { swipeUp() }
        compose.runOnIdle { assertTrue(scroll.value > 0); assertTrue(moves.isEmpty()) }
    }

    // DRF-064
    @Test fun doubleFacedPreviewClosesOnReleaseAndSimultaneousTapsOpenOneMenu() {
        val dfc = CardDefinition("Front // Back", listOf(DefinitionFace("a", "Front"), DefinitionFace("b", "Back")),
            layout = "modal_dfc")
        compose.setContent { MaterialTheme {
            PodBuildView(PodDeckBuild(listOf(PodDeckCopy("first"), PodDeckCopy("second"))),
                mapOf("first" to DraftCopy("first", "dfc"), "second" to DraftCopy("second", "digest")),
                mapOf("dfc" to dfc, "digest" to card), null, true, false, { _, _ -> }, { _, _ -> }, {})
        } }
        val first = compose.onNodeWithTag("pod-build-card-first")
        first.performTouchInput { down(center); advanceEventTime(160) }
        compose.mainClock.advanceTimeBy(160)
        compose.onNodeWithContentDescription("Card image: Front").assertExists()
        compose.onNodeWithContentDescription("Card image: Back").assertExists()
        first.performTouchInput { advanceEventTime(PodHoldSettleMillis + 20) }
        compose.mainClock.advanceTimeBy(PodHoldSettleMillis + 20)
        first.performTouchInput { up() }
        compose.onNodeWithTag("card-inspection").assertDoesNotExist()
        val a = first.fetchSemanticsNode().boundsInRoot.center
        val b = compose.onNodeWithTag("pod-build-card-second").fetchSemanticsNode().boundsInRoot.center
        compose.onRoot().performTouchInput { down(0, a); down(1, b); up(0); up(1) }
        compose.onAllNodesWithText("→ Side").assertCountEquals(1)
    }

    // DRF-094, DRF-103, DRF-119
    @Test fun buildShowsCountsValidatesSubmitAndReportsBasicsWithoutMutatingServerData() {
        var build by mutableStateOf(PodDeckBuild(listOf(PodDeckCopy("first")), PodDeckBasics(plains = 38)))
        var requestedBasics: PodDeckBasics? = null
        var submits = 0
        compose.setContent { MaterialTheme {
            PodBuildView(build, mapOf("first" to DraftCopy("first", "digest")), mapOf("digest" to card), 0,
                true, true, { _, _ -> }, { index, delta -> requestedBasics = build.basics.podBuildAdjust(index, delta) }, { submits++ })
        } }
        compose.onNodeWithText("Main 1 + 38 = 39").assertIsDisplayed()
        compose.onNodeWithText("Main needs 40 cards").assertDoesNotExist()
        compose.onNodeWithText("Submit deck (39)").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Edit basic lands").performClick()
        compose.onNodeWithContentDescription("Remove Island").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Add Plains").performClick()
        compose.runOnIdle { assertEquals(PodDeckBasics(plains = 39), requestedBasics); assertEquals(38, build.basics.plains) }
        compose.runOnIdle { build = build.copy(basics = requestedBasics!!) }
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("Main 1 + 39 = 40").assertIsDisplayed()
        compose.onNodeWithText("Submit deck (40)").assertIsEnabled().performClick()
        compose.onNodeWithText("Submit deck?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(0, submits) }
        compose.onNodeWithText("Submit deck (40)").performClick()
        compose.onAllNodesWithText("Submit").onLast().performClick()
        compose.runOnIdle { assertEquals(1, submits) }
    }

    @Test fun columnAndBasicControlsStayOnCompactSingleLinesAndTilesKeepTheirShape() {
        val longName = "An unusually long card name that should be ellipsized inside a card-shaped placeholder".repeat(2)
        compose.setContent { ScreenTheme {
            PodBuildView(PodDeckBuild(listOf(PodDeckCopy("first")), PodDeckBasics(plains = 38)),
                mapOf("first" to DraftCopy("first", "digest")), mapOf("digest" to card.copy(name = longName)), null,
                true, false, { _, _ -> }, { _, _ -> }, {})
        } }
        // Columns live in the header's view settings panel, not in the Main bar.
        compose.onNodeWithContentDescription("Columns").assertDoesNotExist()
        // The single tappable basic-land area retains the standard 48dp touch target.
        assertTrue(with(compose.onNodeWithTag("pod-build-basics").getUnclippedBoundsInRoot()) { bottom - top } <= 48.dp)
        compose.onNodeWithContentDescription("Add Forest").assertDoesNotExist()
        compose.onNodeWithContentDescription("Edit basic lands").performClick()
        compose.onNodeWithContentDescription("Add Forest").assertIsDisplayed()
        compose.onNodeWithText("Done").performClick()
        val bounds = compose.onNodeWithTag("pod-build-card-first").fetchSemanticsNode().boundsInRoot
        assertTrue(kotlin.math.abs(bounds.height / bounds.width - 1.4f) < .03f)
    }

    // DRF-097, DRF-106
    @Test fun columnLabelsCountCopiesAndTheUndoSlotSitsLeftOfSubmit() {
        var canUndo by mutableStateOf(false)
        var undos = 0
        compose.setContent { MaterialTheme {
            PodBuildView(PodDeckBuild(listOf(PodDeckCopy("first"), PodDeckCopy("second"))),
                mapOf("first" to DraftCopy("first", "digest"), "second" to DraftCopy("second", "digest")), mapOf("digest" to card), null,
                true, false, { _, _ -> }, { _, _ -> }, {}, canUndo = canUndo,
                onUndo = { undos++; "first" to PodDeckCategory.SIDE })
        } }
        compose.onNodeWithTag("pod-build-column-count-MAIN-2", useUnmergedTree = true).assertTextEquals("2")
        compose.onNodeWithTag("pod-build-column-count-MAIN-0", useUnmergedTree = true).assertTextEquals("0")
        val undo = compose.onNodeWithTag("pod-build-undo").assertIsNotEnabled()
        val bar = undo.getUnclippedBoundsInRoot()
        val submit = compose.onNodeWithText("Submit deck (2)").getUnclippedBoundsInRoot()
        assertEquals(48.dp, bar.right - bar.left)
        assertTrue(bar.right <= submit.left)
        // The bar spans the full width, not inset by the columns' side margins: Undo at the left edge.
        assertEquals(0.dp, bar.left)
        assertEquals(compose.onRoot().getUnclippedBoundsInRoot().right, submit.right)
        assertTrue(compose.onNodeWithTag("pod-build-basics").getUnclippedBoundsInRoot().left > bar.left)
        compose.runOnIdle { canUndo = true }
        compose.onNodeWithContentDescription("Undo last change").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, undos) }
        compose.onNodeWithText("→ Side").assertIsDisplayed()
    }

    // DRF-120
    @Test fun submittedBuildHidesAllEditingControlsAndMaybeStartsExpanded() {
        compose.setContent { MaterialTheme {
            PodBuildView(PodDeckBuild(listOf(PodDeckCopy("first", PodDeckCategory.MAYBE))),
                mapOf("first" to DraftCopy("first", "digest")), mapOf("digest" to card), null,
                false, false, { _, _ -> fail("read-only") }, { _, _ -> fail("read-only") }, { fail("read-only") })
        } }
        compose.onNodeWithText("✓ Submitted").assertIsDisplayed()
        compose.onNodeWithText("Submit deck (0)").assertDoesNotExist()
        compose.onNodeWithTag("pod-build-undo").assertDoesNotExist()
        compose.onNodeWithContentDescription("Add Plains").assertDoesNotExist()
        compose.onNodeWithTag("pod-build").performScrollToNode(hasTestTag("pod-build-card-first"))
        compose.onNodeWithTag("pod-build-card-first").assertIsDisplayed()
        compose.onNodeWithText("Maybe 1").assertIsDisplayed()
    }

    // DRF-097
    @Test fun buildContentSharesWideSideInsetsAcrossHeadersBasicsAndAllCardSections() {
        val copies = PodDeckCategory.entries.associate { it.name to DraftCopy(it.name, "land") }
        compose.setContent { MaterialTheme {
            PodBuildView(PodDeckBuild(PodDeckCategory.entries.map { PodDeckCopy(it.name, it) }), copies,
                mapOf("land" to card.copy(faces = listOf(card.faces.single().copy(typeLine = "Land")))), null,
                true, false, { _, _ -> }, { _, _ -> }, {})
        } }
        val bounds = compose.onNodeWithTag("pod-build").getUnclippedBoundsInRoot()
        val basics = compose.onNodeWithTag("pod-build-basics").getUnclippedBoundsInRoot()
        assertEquals(bounds.left + 48.dp, basics.left)
        assertEquals(bounds.right - 48.dp, basics.right)
        assertEquals(basics.left, compose.onNodeWithText("Deck").getUnclippedBoundsInRoot().left)
        assertEquals(basics.left, compose.onNodeWithText("Main 0 + 1 = 1").getUnclippedBoundsInRoot().left)
        for (category in PodDeckCategory.entries) {
            compose.onNodeWithTag("pod-build").performScrollToNode(hasTestTag("pod-build-card-${category.name}"))
            assertEquals(basics.left, compose.onNodeWithTag("pod-build-card-${category.name}").getUnclippedBoundsInRoot().left)
        }
    }

    // DRF-094, DRF-103
    @Test fun sideBasicsHaveTheirOwnStepperRowUnderTheSideHeader() {
        var build by mutableStateOf(PodDeckBuild(sideBasics = PodDeckBasics(island = 17)))
        val side = mutableListOf<Pair<Int, Int>>()
        compose.setContent { ScreenTheme {
            PodBuildView(build, emptyMap(), emptyMap(), null, true, false, { _, _ -> }, { _, _ -> fail("Main basics changed") }, {},
                onAdjustSideBasic = { index, delta -> side += index to delta; build = build.copy(sideBasics = build.sideBasics.podBuildAdjust(index, delta)) })
        } }
        compose.onNodeWithTag("pod-build").performScrollToNode(hasTestTag("pod-build-side-basics"))
        compose.onNodeWithText("Side 0 + 17 = 17").assertIsDisplayed()
        compose.onNodeWithContentDescription("Edit Side basic lands").performClick()
        compose.onNodeWithText("Side basic lands").assertIsDisplayed()
        compose.onNodeWithContentDescription("Add Island").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(1 to 1), side); assertEquals(18, build.sideBasics.island) }
    }

    @Test
    @Config(qualifiers = "w800dp-h360dp-land-mdpi")
    fun allBasicLandSteppersAreReachableInShortLandscapeDialog() {
        var build by mutableStateOf(PodDeckBuild())
        val adjustments = mutableListOf<Pair<Int, Int>>()
        compose.setContent { ScreenTheme {
            PodBuildView(build, emptyMap(), emptyMap(), null, true, false, { _, _ -> }, { index, delta ->
                adjustments += index to delta
                build = build.copy(basics = build.basics.podBuildAdjust(index, delta))
            }, {})
        } }
        compose.onNodeWithContentDescription("Edit basic lands").performClick()
        listOf("Plains", "Island", "Swamp", "Mountain", "Forest").forEachIndexed { index, name ->
            val add = compose.onNodeWithContentDescription("Add $name").performScrollTo().assertIsDisplayed()
            val bounds = add.getUnclippedBoundsInRoot()
            val done = compose.onNodeWithText("Done").assertIsDisplayed().getUnclippedBoundsInRoot()
            val dialog = compose.onNode(isDialog()).getUnclippedBoundsInRoot()
            assertTrue(bounds.top >= dialog.top && bounds.bottom <= done.top)
            add.performTouchInput { click() }
            compose.onNodeWithContentDescription("Remove $name").assertIsEnabled().performTouchInput { click() }
            compose.runOnIdle { assertEquals(listOf(index to 1, index to -1), adjustments.takeLast(2)) }
        }
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("Basic lands").assertDoesNotExist()
        compose.runOnIdle { assertEquals(PodDeckBasics(), build.basics) }
    }

    // DRF-095
    @Test
    @Config(qualifiers = "w892dp-h412dp-land-mdpi")
    fun landscapeUsesItsOwnColumnPreferenceAndFitsAllColumns() {
        val preferences = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("pod-build", 0)
        preferences.edit().putInt("columns-1", 3).putInt("columns-2", 10).commit()
        compose.setContent { MaterialTheme { Column {
            val columns = rememberPodBuildColumns()
            PodViewSettingsButton(null, columns)
            PodBuildView(PodDeckBuild(), emptyMap(), emptyMap(), null, true, false, { _, _ -> }, { _, _ -> }, {},
                Modifier.weight(1f), columnPreference = columns)
        } } }
        compose.onAllNodesWithText("8+").onFirst().assertIsDisplayed()
        compose.onNodeWithTag("pod-build-column-MAIN-9").assertExists()
        compose.onNodeWithContentDescription("View settings").performClick()
        compose.onNodeWithText("Draft · cards per row").assertDoesNotExist()
        compose.onNodeWithContentDescription("Columns").assert(hasText("10"))
        compose.onNodeWithContentDescription("Columns").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(4f) }
        compose.onNodeWithContentDescription("Columns").assert(hasText("4"))
        // Applies live behind the open panel.
        compose.onNodeWithTag("pod-build-column-MAIN-3").assertExists()
        compose.onNodeWithTag("pod-build-column-MAIN-4").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(4, preferences.getInt("columns-2", 0))
            assertEquals(3, preferences.getInt("columns-1", 0))
        }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun mainBarHasNoColumnControlAndLongDeckNamesAreNotTruncated() {
        val name = "A wonderfully long deck name that needs more than one line to show in full"
        compose.setContent { ScreenTheme {
            PodBuildView(PodDeckBuild(), emptyMap(), emptyMap(), null, true, false, { _, _ -> }, { _, _ -> }, {}, deckName = name)
        } }
        val nameLayouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithText(name).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(nameLayouts) }
        assertTrue(nameLayouts.single().lineCount > 1)
        assertFalse(nameLayouts.single().hasVisualOverflow)
        val main = compose.onNodeWithText("Main 0 + 0 = 0").getUnclippedBoundsInRoot()
        compose.onNodeWithContentDescription("Columns").assertDoesNotExist()
        val pen = compose.onNodeWithContentDescription("Name deck").getUnclippedBoundsInRoot()
        assertTrue(pen.bottom <= main.top)
    }

    private fun movingBuild(initial: PodDeckBuild): Pair<() -> PodDeckBuild, (String, PodDeckCategory) -> Unit> {
        var build by mutableStateOf(initial)
        return { build } to { id, destination ->
            moves += id to destination
            build = build.copy(copies = build.copies.map { if (it.copyId == id) it.copy(category = destination) else it })
        }
    }

    // DRF-096
    @Test fun cardsSideBySideDefaultToOneAndLayOutRowByRowAtEqualWidths() {
        val preferences = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("pod-build", 0)
        lateinit var sideBySide: PodBuildColumns
        val ids = listOf("a", "b", "c")
        compose.setContent { MaterialTheme { Column {
            sideBySide = rememberPodBuildSideBySide()
            PodViewSettingsButton(null, rememberPodBuildColumns(), sideBySide)
            PodBuildView(PodDeckBuild(ids.map { PodDeckCopy(it) }), ids.associateWith { DraftCopy(it, it) },
                ids.associateWith { card.copy(name = "Card $it") }, null, true, false, { _, _ -> }, { _, _ -> }, {},
                Modifier.weight(1f), sideBySide = sideBySide)
        } } }
        assertEquals(1, sideBySide.count)
        fun bounds(id: String) = compose.onNodeWithTag("pod-build-card-$id").getUnclippedBoundsInRoot()
        assertEquals(bounds("a").left, bounds("b").left)
        compose.onNodeWithContentDescription("View settings").performClick()
        compose.onNodeWithText("Build · cards side by side").assertIsDisplayed()
        compose.onNodeWithContentDescription("Fewer cards side by side").assertIsNotEnabled()
        compose.onNodeWithContentDescription("More cards side by side").performClick()
        compose.waitForIdle()
        assertEquals(2, sideBySide.count)
        assertEquals(2, preferences.getInt("side-by-side-1", 0))
        val (a, b, c) = ids.map(::bounds)
        assertEquals(a.top, b.top)
        assertEquals(a.right - a.left, b.right - b.left)
        assertEquals(a.right - a.left, c.right - c.left)
        assertEquals(PodBuildCardGap, b.left - a.right)
        assertEquals(a.left, c.left)
        assertEquals(PodBuildCardGap, c.top - a.bottom)
        assertTrue(kotlin.math.abs((a.bottom - a.top) / (a.right - a.left) - 1.4f) < .05f)
        compose.onNodeWithContentDescription("More cards side by side").performClick()
        compose.onNodeWithContentDescription("More cards side by side").assertIsNotEnabled()
        compose.runOnIdle { sideBySide.choose(9); assertEquals(3, sideBySide.count); sideBySide.choose(0); assertEquals(1, sideBySide.count) }
    }

    // DRF-105
    @Test fun deckNameKeyboardDoneSavesTheName() {
        var renamed: String? = null
        compose.setContent { MaterialTheme {
            PodBuildView(PodDeckBuild(listOf(PodDeckCopy("first"))), mapOf("first" to DraftCopy("first", "digest")),
                mapOf("digest" to card), null, true, false, { _, _ -> }, { _, _ -> }, {}, onRename = { renamed = it })
        } }
        compose.onNodeWithContentDescription("Name deck").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("  ")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.runOnIdle { assertNull(renamed) }
        compose.onNode(hasSetTextAction()).performTextReplacement(" Fliers ")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.runOnIdle { assertEquals("Fliers", renamed) }
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
    }

    // DRF-107, DRF-108, DRF-109, DRF-110
    @Test fun columnFeedReviewsEachCopyAndUndoMovesItBack() {
        val (build, move) = movingBuild(PodDeckBuild(listOf(PodDeckCopy("first"), PodDeckCopy("second"), PodDeckCopy("land"))))
        val land = card.copy(name = "Land card", faces = listOf(card.faces.single().copy(typeLine = "Land", manaCost = "")))
        compose.setContent { MaterialTheme {
            PodBuildView(build(), mapOf("first" to DraftCopy("first", "digest"), "second" to DraftCopy("second", "digest"),
                "land" to DraftCopy("land", "land")), mapOf("digest" to card, "land" to land), null, true, false, move, { _, _ -> }, {})
        } }
        compose.onNodeWithTag("pod-build-feed-label-MAIN-0").performClick()
        compose.onNodeWithText("Main · Lands").assertIsDisplayed()
        compose.onNodeWithContentDescription("Undo").assertIsNotEnabled()
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithTag("pod-build-feed").assertDoesNotExist()
        compose.onNodeWithTag("pod-build-feed-label-MAIN-0").performClick()
        compose.onNodeWithContentDescription("Close review").performClick()
        compose.onNodeWithTag("pod-build-feed").assertDoesNotExist()

        compose.onNodeWithTag("pod-build-feed-label-MAIN-2").performClick()
        compose.onNodeWithText("Main · MV 1").assertIsDisplayed()
        compose.onNodeWithText("1 / 2").assertIsDisplayed()
        compose.onNodeWithText("Main 3").assertIsDisplayed()
        // The buttons under the card do what the gestures do: Maybe (left), Keep (a tap), Side (right)
        compose.onNodeWithContentDescription("Move to Maybe").assertIsDisplayed()
        compose.onNodeWithContentDescription("Keep").assertIsDisplayed()
        compose.onNodeWithContentDescription("Move to Side").assertIsDisplayed()
        // A swipe left moves it as in the columns (DRF-099): Main → Maybe
        compose.onNodeWithTag("pod-build-feed-card-first").performTouchInput { swipeLeft() }
        compose.runOnIdle { assertEquals(listOf("first" to PodDeckCategory.MAYBE), moves) }
        compose.onNodeWithText("2 / 2").assertIsDisplayed()
        compose.onNodeWithText("Main 2").assertIsDisplayed()
        compose.onNodeWithContentDescription("Undo").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals("first" to PodDeckCategory.MAIN, moves.last()) }
        compose.onNodeWithTag("pod-build-feed-card-first").assertIsDisplayed()
        compose.onNodeWithText("1 / 2").assertIsDisplayed()
        // A tap keeps it where it is, as a swipe right did before
        compose.onNodeWithTag("pod-build-feed-card-first").performTouchInput { click() }
        compose.runOnIdle { assertEquals(2, moves.size) }
        compose.onNodeWithText("2 / 2").assertIsDisplayed()
        compose.onNodeWithContentDescription("Undo").performClick()
        compose.runOnIdle { assertEquals(2, moves.size) }
        // A swipe right: Main → Side
        compose.onNodeWithTag("pod-build-feed-card-first").performTouchInput { swipeRight() }
        compose.runOnIdle { assertEquals("first" to PodDeckCategory.SIDE, moves.last()) }
        compose.onNodeWithContentDescription("Keep").performClick()
        // The last decision closes the review; its summary shows briefly on the Build screen.
        compose.onNodeWithTag("pod-build-feed").assertDoesNotExist()
        compose.runOnIdle { assertEquals(3, moves.size) }
        compose.onNodeWithText("Kept 1 · 1 to Side").assertIsDisplayed()
        compose.onNodeWithText("Next", substring = true).assertDoesNotExist()
    }

    // DRF-107, DRF-108, DRF-110
    @Test fun columnFeedClosesAtItsEndAndSectionFeedsCoverEveryColumn() {
        val (build, move) = movingBuild(PodDeckBuild(listOf(PodDeckCopy("land"), PodDeckCopy("first"),
            PodDeckCopy("side", PodDeckCategory.SIDE), PodDeckCopy("maybe", PodDeckCategory.MAYBE))))
        val land = card.copy(name = "Land card", faces = listOf(card.faces.single().copy(typeLine = "Land", manaCost = "")))
        val copies = listOf("land", "first", "side", "maybe").associateWith { DraftCopy(it, if (it == "land") "land" else "digest") }
        compose.setContent { MaterialTheme {
            PodBuildView(build(), copies, mapOf("digest" to card, "land" to land), null, true, false, move, { _, _ -> }, {})
        } }
        compose.onNodeWithTag("pod-build-feed-label-MAIN-0").performClick()
        val actions = compose.onNodeWithTag("pod-build-feed-card-land").fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsActions.CustomActions]
        assertEquals(listOf("Keep", "Move to Side", "Move to Maybe"), actions.map { it.label })
        compose.runOnIdle { actions.first().action() }
        compose.onNodeWithTag("pod-build-feed").assertDoesNotExist()
        compose.onNodeWithText("Kept 1").assertIsDisplayed()
        compose.onNodeWithTag("pod-build-feed-label-MAIN-2").performClick()
        compose.onNodeWithText("Main · MV 1").assertIsDisplayed()
        compose.onNodeWithContentDescription("Undo").assertIsNotEnabled()
        compose.onNodeWithTag("pod-build-feed-card-first").performTouchInput { swipeRight() }
        compose.onNodeWithTag("pod-build-feed").assertDoesNotExist()
        compose.onNodeWithText("1 to Side").assertIsDisplayed()
        compose.runOnIdle { assertEquals(listOf("first" to PodDeckCategory.SIDE), moves) }

        compose.onNodeWithTag("pod-build-feed-header-SIDE").performClick()
        compose.onNodeWithText("Side · MV 1").assertIsDisplayed()
        compose.onNodeWithText("1 / 2").assertIsDisplayed()
        compose.onNodeWithContentDescription("Move to Main").assertIsDisplayed()
        compose.onNodeWithContentDescription("Move to Maybe").performClick()
        compose.runOnIdle { assertEquals("first" to PodDeckCategory.MAYBE, moves.last()) }
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithText("Maybe 2").assertIsDisplayed()
        // Maybe has a review too: right → Main, left → Side
        compose.onNodeWithTag("pod-build-feed-label-MAYBE-2").assertIsDisplayed()
        compose.onNodeWithTag("pod-build-feed-header-MAYBE").performClick()
        compose.onNodeWithText("Maybe · MV 1").assertIsDisplayed()
        compose.onNodeWithText("1 / 2").assertIsDisplayed()
        compose.onNodeWithContentDescription("Move to Side").assertIsDisplayed()
        compose.onNodeWithContentDescription("Move to Main").performClick()
        compose.onNodeWithContentDescription("Keep").performClick()
        compose.onNodeWithTag("pod-build-feed").assertDoesNotExist()
        compose.runOnIdle { assertEquals(PodDeckCategory.MAIN, moves.last().second) }
        compose.onNodeWithText("Kept 1 · 1 to Main").assertIsDisplayed()
    }

    // DRF-107, DRF-108: the Maybe review: a tap keeps, swipes and the buttons under the card move it (right → Main,
    // left → Side)
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun maybeReviewScreenshot() {
        val (build, move) = movingBuild(PodDeckBuild(listOf(PodDeckCopy("first"), PodDeckCopy("maybe", PodDeckCategory.MAYBE),
            PodDeckCopy("burnt", PodDeckCategory.MAYBE))))
        compose.setContent { NoRipples { MaterialTheme {
            PodBuildView(build(), listOf("first", "maybe", "burnt").associateWith { DraftCopy(it, "digest") },
                mapOf("digest" to card), null, true, false, move, { _, _ -> }, {})
        } } }
        compose.onNodeWithTag("pod-build-feed-header-MAYBE").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Maybe · MV 1").assertIsDisplayed()
        captureShot("$SCREENSHOTS/pod-build-review-maybe.png",
            "Tapping the Maybe header's Review opens the one-by-one review of the two cards in Maybe: \"Maybe · MV 1\", 1 / 2 and the Main count at the top, the card in the middle, and under it ◀ Side, ✓ Keep and Main ▶ in their colours (a tap keeps, swipes move it as in the columns).")
    }

    // DRF-092
    @Test
    @Config(qualifiers = "w412dp-h400dp-port-mdpi")
    fun submittedBuildOffersNoFeedAndTheDeckNameScrollsAway() {
        compose.setContent { MaterialTheme {
            PodBuildView(PodDeckBuild(listOf(PodDeckCopy("first"))), mapOf("first" to DraftCopy("first", "digest")),
                mapOf("digest" to card), null, false, false, { _, _ -> fail("read-only") }, { _, _ -> }, {})
        } }
        compose.onNodeWithTag("pod-build-feed-header-MAIN").assertDoesNotExist()
        compose.onNodeWithTag("pod-build-feed-label-MAIN-2").assertDoesNotExist()
        compose.onNodeWithText("Deck").assertIsDisplayed()
        compose.onNodeWithTag("pod-build").performTouchInput { swipeUp() }
        compose.onNodeWithText("Deck").assertIsNotDisplayed()
    }
}
