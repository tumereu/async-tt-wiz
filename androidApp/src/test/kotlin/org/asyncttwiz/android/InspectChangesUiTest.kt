package org.asyncttwiz.android

import androidx.compose.foundation.ScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * TBL-104, TBL-111 (owner 2026-11-06): Inspect lists under each card only what changed on it, with a thin line between
 * cards; pinned mode (a drag to the left) shrinks the cards and slides the changes beside them, with a close button, and a
 * tap on a change there names its source.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class InspectChangesUiTest {
    @get:Rule val compose = createComposeRule()
    private val f = TableStatesFixture

    /** The faces of the fixture's card named [name], as Inspect shows them. */
    private fun faces(name: String): List<InspectTile> {
        val submitted = f.initial.definitions.first { it.definition.name == name }
        return inspectFaces(InspectedCard.retained(submitted.digest, submitted.definition), key = name, owner = OwnTableColor)
    }

    /** Alice's Meadow Sentinel with counters, a pump, a keyword it gained and its enter mark, and the River Adept attached to
     * it, which shows only that; a third card with nothing changed has no lines. */
    private val groups = listOf(
        InspectGroup("sentinel", faces("Meadow Sentinel"), listOf(
            InspectChange("+1/+1 counter ×2"), InspectChange("1 damage"),
            InspectChange("+3/+3 until end of turn", "Wild Surge"), InspectChange("Flying", "Gale Banner"),
            InspectChange("Entered this turn"))),
        InspectGroup("adept", faces("River Adept"), listOf(InspectChange("Attached to Meadow Sentinel"))),
    )

    private fun show(swipe: InspectSwipe? = null, scroll: ScrollState = ScrollState(0)) = compose.setContent { MaterialTheme { NoRipples {
        InspectContent(groups, scroll, swipe)
    } } }

    private fun changes() = compose.onAllNodes(hasContentDescription("Card changes"), useUnmergedTree = true)

    // TBL-104, TBL-111: the changes sit under each card on a dark panel as wide as the card; a line separates the cards
    @Test fun eachCardShowsOnlyItsChangesUnderIt() {
        val scroll = ScrollState(0)
        show(scroll = scroll)
        changes().assertCountEquals(2)
        val card = compose.onNodeWithContentDescription("Card image: Meadow Sentinel").getBoundsInRoot()
        val panel = changes()[0].getBoundsInRoot()
        assertTrue(panel.top >= card.bottom, "under the card: $panel, card $card")
        assertEquals((card.right - card.left).value, (panel.right - panel.left).value, .5f)
        compose.onNodeWithText("+3/+3 until end of turn").assertExists()
        // No source unless pinned and asked for
        compose.onNodeWithText("From Wild Surge").assertDoesNotExist()
        compose.runOnIdle { scroll.dispatchRawDelta(380f) }
        compose.waitForIdle()
        captureShot("$SCREENSHOTS/inspect/changes-portrait.png", "Portrait Inspect of a creature and the card attached to it, " +
            "scrolled down: under the Meadow Sentinel's picture a dark panel lists only what changed on it (two +1/+1 counters, " +
            "1 damage, +3/+3 until end of turn, Flying, Entered this turn), a thin white line, then the River Adept with " +
            "\"Attached to Meadow Sentinel\" under it.")
    }

    // TBL-131 (owner 2026-11-06): half way through the drag to the left the cards are shrinking, the changes folding away
    // under them and sliding in beside them, the close button coming in
    @Test fun midSwipeTheChangesMoveBesideTheCards() {
        val swipe = InspectSwipe()
        show(swipe)
        compose.runOnIdle { swipe.moved(-(InspectSwipe.SLOP + InspectSwipe.DISTANCE / 2), 0f) }
        compose.waitForIdle()
        assertEquals(.5f, swipe.progress, .01f)
        captureShot("$SCREENSHOTS/inspect/changes-mid-swipe.png", "Portrait Inspect half way through a drag to the left: " +
            "the Meadow Sentinel's picture has shrunk part way, its changes fading out under it and sliding in at its right, " +
            "and a round close button growing in at the top right.")
    }

    // TBL-131 (owner 2026-11-06): pinned, the cards take a little over half the width with their changes beside them and a
    // close button at the top right; tapping a change names its source; Close closes it
    @Test fun pinnedTheChangesSitBesideTheCardsAndNameTheirSourceOnATap() {
        var closed = false
        val swipe = InspectSwipe().apply { onClose = { closed = true } }
        show(swipe)
        compose.runOnIdle { swipe.moved(-200f, 0f); assertTrue(swipe.released()) }
        compose.waitForIdle()
        val card = compose.onNodeWithContentDescription("Card image: Meadow Sentinel").getBoundsInRoot()
        val panel = changes()[0].getBoundsInRoot()
        assertTrue(panel.left >= card.right && panel.top < card.bottom, "beside the card: $panel, card $card")
        // TBL-131 (owner playtest 2026-11-06): the card at the left edge; each change its own block
        assertTrue(card.left.value <= 12f, "at the left edge: $card")
        compose.onAllNodes(hasText("+1/+1 counter ×2") or hasText("1 damage"), useUnmergedTree = true).assertCountEquals(2)
        compose.onNodeWithText("+3/+3 until end of turn").performClick()
        compose.onNodeWithText("From Wild Surge").assertExists()
        captureShot("$SCREENSHOTS/inspect/changes-pinned.png", "Portrait Inspect pinned by a drag to the left: the Meadow " +
            "Sentinel's picture takes a little over half the width at the left edge with its changes at its right, each in a " +
            "dark block of its own, the +3/+3 change tapped shows \"From Wild Surge\" under it, and a round close button " +
            "sits at the top right.")
        compose.onNodeWithContentDescription("Close inspection").performClick()
        compose.runOnIdle { assertTrue(closed) }
    }

    // TBL-132 (owner gallery review 2026-11-06): pinned, a tap on a change line naming a keyword ("Flying") shows its source
    // and what the keyword means; a line naming none ("1 damage") explains nothing
    @Test fun pinnedAKeywordChangeExplainsTheKeyword() {
        val swipe = InspectSwipe()
        show(swipe)
        compose.runOnIdle { swipe.moved(-200f, 0f); assertTrue(swipe.released()) }
        compose.waitForIdle()
        val flying = arcanum.api.RulesGlossary.keywords.getValue("flying").text
        compose.onNodeWithText(flying).assertDoesNotExist()
        compose.onNodeWithText("Flying").performClick()
        compose.onNodeWithText("From Gale Banner").assertExists()
        compose.onNodeWithText(flying).assertExists()
        compose.onNodeWithText("1 damage").performClick()
        compose.onAllNodes(hasContentDescription("Rule explanation"), useUnmergedTree = true).assertCountEquals(1)
    }

    // TBL-131 (owner playtest 2026-11-06): pinned by a drag to the right, an unchanged card goes to the left edge with a faded
    // "No changes" in the middle of the room beside it
    @Test fun pinnedAnUnchangedCardSaysNoChanges() {
        val swipe = InspectSwipe()
        compose.setContent { MaterialTheme { NoRipples { InspectContent(listOf(InspectGroup("adept", faces("River Adept"))), ScrollState(0), swipe) } } }
        compose.onNodeWithContentDescription("No changes").assertDoesNotExist()
        compose.runOnIdle { swipe.moved(200f, 0f); assertTrue(swipe.released()) }
        compose.waitForIdle()
        val card = compose.onNodeWithContentDescription("Card image: River Adept").getBoundsInRoot()
        val note = compose.onNodeWithContentDescription("No changes").assertIsDisplayed().getBoundsInRoot()
        assertTrue(card.left.value <= 12f, "at the left edge: $card")
        assertTrue(note.left >= card.right, "beside the card: $note, card $card")
        captureShot("$SCREENSHOTS/inspect/no-changes-pinned.png", "Portrait Inspect of an unchanged River Adept pinned by a " +
            "drag to the right: the picture at the left edge, a faded \"No changes\" centred in the room at its right, and the " +
            "round close button at the top right.")
    }

    // TBL-104: in landscape the cards stand side by side, each card's changes beside it, a line between cards
    @Config(qualifiers = "w892dp-h412dp-land-mdpi")
    @Test fun inLandscapeTheChangesSitBesideEachCard() {
        val swipe = InspectSwipe()
        show(swipe)
        val card = compose.onNodeWithContentDescription("Card image: Meadow Sentinel").getBoundsInRoot()
        val panel = changes()[0].getBoundsInRoot()
        assertTrue(panel.left >= card.right, "beside the card: $panel, card $card")
        captureShot("$SCREENSHOTS/inspect/changes-landscape.png", "Landscape Inspect of a creature and the card attached to " +
            "it: the Meadow Sentinel's picture with its changes in a dark panel beside it, a thin white line, then the River " +
            "Adept with \"Attached to Meadow Sentinel\" beside it.")
        compose.runOnIdle { swipe.moved(-200f, 0f); assertTrue(swipe.released()) }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Close inspection").assertIsDisplayed()
        // TBL-131: pinned, the changes narrow and wrap to stay on the screen
        val screen = compose.onRoot().getBoundsInRoot()
        for (index in 0 until 2) changes()[index].getBoundsInRoot().let { assertTrue(it.right <= screen.right, "on the screen: $it") }
        captureShot("$SCREENSHOTS/inspect/changes-landscape-pinned.png", "Landscape Inspect pinned by a drag to the left: " +
            "the same cards, a little smaller under a round close button at the top right, their changes in narrower panels " +
            "whose lines wrap so that all of them stay on the screen.")
    }
}
