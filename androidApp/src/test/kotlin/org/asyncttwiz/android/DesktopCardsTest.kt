package org.asyncttwiz.android

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The desktop layout's mouse and keys on the draft pick, Build, the card lists and dialogs (docs/specs/web-client.md §5, §6,
 * step 6b; WEB-044 to WEB-048).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1440dp-h900dp-land-mdpi")
class DesktopCardsTest {
    @get:Rule val compose = createComposeRule()
    private var keyTime = 0L

    @Before fun clearPreferences() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        for (name in listOf("pod-pick", "pod-build", "deck-view")) context.getSharedPreferences(name, 0).edit().clear().commit()
    }

    @Composable
    private fun Desktop(content: @Composable () -> Unit) = ScreenTheme {
        CompositionLocalProvider(LocalAppLayout provides AppLayout.Desktop) { Surface { content() } }
    }

    /** The page's key [name] pressed (and released): whether the app took the press. */
    private fun press(name: String, textField: Boolean = false): Boolean {
        var taken = false
        compose.runOnIdle {
            taken = DesktopKeyboard.dispatch(DesktopKey(name, true, ++keyTime, textField = textField))
            DesktopKeyboard.dispatch(DesktopKey(name, false, ++keyTime, textField = textField))
        }
        compose.waitForIdle()
        return taken
    }

    private val pack = DraftPrivatePack("first", 2, (1..6).map { DraftCopy("$it", "definition-$it") })
    private val definitions = pack.cards.associate { it.definition to CardDefinition("Card ${it.id}", listOf(DefinitionFace("face", "Card ${it.id}"))) }
    private fun pickCard(id: String) = compose.onNodeWithTag("pod-pick-card-$id")
    private fun assertMark(id: String, mark: String) = pickCard(id).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, mark))

    // WEB-044, WEB-028, WEB-036, DRF-060: a click keeps (again: unmarks), a right-click burns, K on the hovered card keeps it, the
    // hovered card's ⋯ opens its menu, Enter confirms; the trays and Confirm stay at the bottom
    @Test fun theDraftPickByClicksAndKeys() {
        var confirmed: Pair<List<String>, List<String>>? = null
        compose.setContent { Desktop { PodPickView(pack, DraftSettings(keep = 1, burn = 1), definitions, false, { keep, burn -> confirmed = keep to burn }) } }
        assertTrue(!press("Enter") || confirmed == null, "nothing to confirm yet")
        assertEquals(null, confirmed)
        pickCard("1").performMouseInput { click() }
        assertMark("1", "Keep")
        pickCard("1").performMouseInput { click() }
        assertMark("1", "Unmarked")
        pickCard("1").performMouseInput { click() }
        pickCard("2").performMouseInput { moveTo(center); press(androidx.compose.ui.test.MouseButton.Secondary); release(androidx.compose.ui.test.MouseButton.Secondary) }
        assertMark("2", "Burn")
        // A click on the burned card switches it to keep (the earlier keep is dropped)
        pickCard("2").performMouseInput { click() }
        assertMark("2", "Keep")
        assertMark("1", "Unmarked")
        // K / B on the card under the pointer
        pickCard("3").performMouseInput { moveTo(center) }
        assertTrue(press("k"), "K taken over a card")
        assertMark("3", "Keep")
        compose.onNodeWithTag("pod-pick-more-3").assertIsDisplayed()
        pickCard("4").performMouseInput { moveTo(center) }
        compose.onNodeWithTag("pod-pick-more-3").assertDoesNotExist()
        assertTrue(press("b"))
        assertMark("4", "Burn")
        compose.onNodeWithTag("pod-pick-more-4").performMouseInput { click() }
        compose.onNodeWithText("Unmark").assertIsDisplayed()
        compose.onNodeWithText("Unmark").performClick()
        assertMark("4", "Unmarked")
        pickCard("4").performMouseInput { moveTo(center); press(androidx.compose.ui.test.MouseButton.Secondary); release(androidx.compose.ui.test.MouseButton.Secondary) }
        // The trays and Confirm lie under the cards
        val confirm = compose.onNodeWithText("Confirm").fetchSemanticsNode().boundsInRoot
        val tray = compose.onNodeWithTag("pod-pick-keep-tray").fetchSemanticsNode().boundsInRoot
        assertTrue(tray.bottom <= confirm.top + 1f && confirm.bottom > 850f, "trays above Confirm at the bottom: $tray $confirm")
        assertTrue(!press("Enter", textField = true), "not while typing")
        assertEquals(null, confirmed)
        assertTrue(press("Enter"))
        compose.runOnIdle { assertEquals(listOf("3") to listOf("4"), confirmed) }
    }

    // WEB-044: the desktop layout starts at eight cards to a row, kept apart from the phone's orientations
    @Test fun theDraftPickStartsWithEightToARow() {
        compose.setContent { Desktop { PodPickView(pack.copy(cards = (1..12).map { DraftCopy("$it", "definition-1") }), DraftSettings(),
            definitions, false, { _, _ -> }) } }
        val first = pickCard("1").fetchSemanticsNode().boundsInRoot
        assertEquals(first.top, pickCard("8").fetchSemanticsNode().boundsInRoot.top, "eight in the first row")
        assertTrue(pickCard("9").fetchSemanticsNode().boundsInRoot.top > first.top, "the ninth on the next row")
    }

    private val card = CardDefinition("Test card", listOf(DefinitionFace("front", "Test card", typeLine = "Creature", manaCost = "{1}")))
    private val tile = PodBuildTile("digest", listOf("first", "second"), card, emptySet())

    // WEB-045, WEB-046, DRF-099: a Build card's edge zones: hovering its left quarter tints "◂ Side", a click there moves one copy to
    // Side, its right quarter to Maybe, its middle opens the menu; ← / → / ↓ do the same; Alt held shows it large
    @Test fun aBuildCardMovesByItsEdgeZonesAndKeys() {
        val moves = mutableListOf<Pair<String, PodDeckCategory>>()
        val inspected = mutableListOf<Boolean>()
        compose.setContent { Desktop {
            Box(Modifier.width(200.dp)) { PodBuildCard(tile, PodDeckCategory.MAIN, true, { id, to -> moves += id to to }, { inspected += it }) }
        } }
        val node = compose.onNodeWithTag("pod-build-card-first")
        compose.onNodeWithTag("edge-zone-left").assertDoesNotExist()
        node.performMouseInput { moveTo(Offset(width * .1f, height / 2f)) }
        compose.onNodeWithTag("edge-zone-left-active").assertIsDisplayed()
        compose.onNodeWithTag("edge-zone-right").assertIsDisplayed()
        compose.onNodeWithText("◂ Side").assertIsDisplayed()
        compose.onNodeWithText("Maybe ▸").assertIsDisplayed()
        node.performMouseInput { click(Offset(width * .1f, height / 2f)) }
        compose.runOnIdle { assertEquals(listOf("first" to PodDeckCategory.SIDE), moves) }
        node.performMouseInput { click(Offset(width * .9f, height / 2f)) }
        compose.runOnIdle { assertEquals("first" to PodDeckCategory.MAYBE, moves.last()) }
        node.performMouseInput { click(center) }
        compose.onNodeWithText("→ Maybe").assertIsDisplayed()
        compose.onNodeWithText("→ Maybe").performClick()
        moves.clear()
        node.performMouseInput { moveTo(center) }
        assertTrue(press("ArrowLeft"))
        assertTrue(press("ArrowRight"))
        compose.runOnIdle { assertEquals(listOf("first" to PodDeckCategory.SIDE, "first" to PodDeckCategory.MAYBE), moves) }
        assertTrue(!press("ArrowLeft", textField = true), "not while typing")
        compose.runOnIdle { DesktopKeyboard.dispatch(DesktopKey("Alt", true, ++keyTime)) }
        compose.runOnIdle { DesktopKeyboard.dispatch(DesktopKey("Alt", false, ++keyTime)) }
        compose.runOnIdle { assertEquals(listOf(true, false), inspected) }
        assertTrue(press("ArrowDown"))
        compose.onNodeWithText("→ Side").assertIsDisplayed()
    }

    // WEB-045: a submitted deck's card has no edge zones; a click opens its menu
    @Test fun aCardThatCantMoveHasNoEdgeZones() {
        val moves = mutableListOf<Pair<String, PodDeckCategory>>()
        compose.setContent { Desktop {
            Box(Modifier.width(200.dp)) { PodBuildCard(tile, PodDeckCategory.MAIN, false, { id, to -> moves += id to to }, {}) }
        } }
        val node = compose.onNodeWithTag("pod-build-card-first")
        node.performMouseInput { moveTo(Offset(width * .1f, height / 2f)) }
        compose.onNodeWithTag("edge-zone-left").assertDoesNotExist()
        compose.onNodeWithTag("edge-zone-left-active").assertDoesNotExist()
        node.performMouseInput { click(Offset(width * .1f, height / 2f)) }
        compose.onNodeWithText("→ Side").assertIsDisplayed()
        compose.runOnIdle { assertTrue(moves.isEmpty()) }
    }

    // WEB-045, DRF-107: Build has no one-by-one review on the desktop layout
    @Test fun buildHasNoReviewOnTheDesktopLayout() {
        compose.setContent { Desktop {
            PodBuildView(PodDeckBuild(listOf(PodDeckCopy("first"))), mapOf("first" to DraftCopy("first", "a")), mapOf("a" to card),
                null, true, false, { _, _ -> }, { _, _ -> }, {})
        } }
        compose.onNodeWithTag("pod-build-card-first").assertExists()
        compose.onNodeWithText("Review ›").assertDoesNotExist()
        compose.onNodeWithTag("pod-build-feed-header-MAIN").assertDoesNotExist()
        compose.onAllNodes(hasTestTag("pod-build-feed-label-MAIN-0") or hasTestTag("pod-build-feed-label-MAIN-1")).assertCountEquals(0)
    }

    // WEB-045, LOB-048: with two sections (deck preparation, Prepare cube) only the right zone shows
    @Test fun twoSectionsHaveOnlyARightZone() {
        var moved = 0
        compose.setContent { Desktop {
            Box(Modifier.width(200.dp)) {
                SwipeDeckCard(PreparationTile("d", card, 2), "k", "prep-card", "Mainboard", { SavedDeckSection.SIDEBOARD }, true,
                    remember { PodCardMenuOwner() }, capture = { { moved++; true } }, onHold = {}, onHoldScroll = {}) { Text("Move all") }
            }
        } }
        // (The tile clears its children's semantics, so its zones are told by what the clicks do.)
        val node = compose.onNodeWithTag("prep-card")
        node.performMouseInput { click(Offset(width * .9f, height / 2f)) }
        compose.runOnIdle { assertEquals(1, moved) }
        node.performMouseInput { click(Offset(width * .1f, height / 2f)) }
        compose.onNodeWithText("Move all").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, moved) }
    }

    // WEB-048: Enter presses a dialog's main button; not a red one, not while typing
    @Test fun enterPressesTheDialogsMainButton() {
        var saves = 0
        var deletes = 0
        var danger by mutableStateOf(false)
        compose.setContent { Desktop {
            ScreenDialog({}, title = "Name deck", actions = {
                DialogCancelButton("Cancel", {})
                if (danger) DialogMainButton("Delete deck", { deletes++ }, danger = true) else DialogMainButton("Save name", { saves++ })
            })
        } }
        assertTrue(!press("Enter", textField = true))
        compose.runOnIdle { assertEquals(0, saves) }
        assertTrue(press("Enter"))
        compose.runOnIdle { assertEquals(1, saves) }
        compose.runOnIdle { danger = true }
        assertTrue(press("Enter"), "taken, so nothing under the dialog acts")
        compose.runOnIdle { assertEquals(0, deletes) }
    }

    // WEB-046: the edge zones' pure rule: a side's quarter when that side has a destination
    @Test fun edgeZonesAreTheQuarters() {
        assertEquals(EdgeZone.Left, EdgeZone.at(.1f, left = true, right = true))
        assertEquals(EdgeZone.Middle, EdgeZone.at(.3f, left = true, right = true))
        assertEquals(EdgeZone.Right, EdgeZone.at(.8f, left = true, right = true))
        assertEquals(EdgeZone.Middle, EdgeZone.at(.1f, left = false, right = true))
        assertEquals(EdgeZone.Middle, EdgeZone.at(.9f, left = true, right = false))
    }
}
