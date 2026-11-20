package org.asyncttwiz.android

import androidx.compose.foundation.ScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import arcanum.api.RulesGlossary
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertTrue

/**
 * TBL-209, TBL-210 (owner 2026-11-19, playtest N8): a card's rules reminders in Inspect: chips under the card naming the
 * keywords and token kinds its text doesn't explain; in a game they explain themselves on a tap or hold once pinned, outside
 * a game (draft, decks) each shows its explanation under it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class RulesRemindersUiTest {
    @get:Rule val compose = createComposeRule()

    /** The fixture's Meadow Sentinel (its picture) printed with flying, ward and a Treasure it makes, none explained. */
    private val card = TableStatesFixture.initial.definitions.first { it.definition.name == "Meadow Sentinel" }.definition.let { definition ->
        InspectedCard.retained(definition.copy(faces = definition.faces.mapIndexed { index, face ->
            if (index == 0) face.copy(rulesText = "Flying\nWard {2}\nWhen this creature enters, create a Treasure token.") else face
        }))
    }

    private fun chips() = compose.onAllNodesWithTag(RULES_REMINDER_TAG, useUnmergedTree = true)
    private fun explanation() = compose.onAllNodes(hasContentDescription("Rule explanation"), useUnmergedTree = true)
    private val ward = RulesGlossary.lookup("Ward")!!.text

    // TBL-209, TBL-210: in a game the chips sit under the card alone; pinned, a tap on one shows its explanation in the dark
    // rule popup, and a tap again closes it
    @Test fun inAGameAChipExplainsItselfOnceInspectIsPinned() {
        val swipe = InspectSwipe()
        val group = InspectGroup("sentinel", inspectFaces(card, key = "sentinel", owner = OwnTableColor), reminders = cardReminders(card.definition))
        compose.setContent { MaterialTheme { NoRipples { InspectContent(listOf(group), ScrollState(0), swipe) } } }
        chips().assertCountEquals(3)
        listOf("Flying", "Ward", "Treasure").forEach { compose.onNodeWithText(it, useUnmergedTree = true).assertExists() }
        val picture = compose.onNodeWithContentDescription("Card image: Meadow Sentinel").getBoundsInRoot()
        assertTrue(chips()[0].getBoundsInRoot().top >= picture.bottom, "the chips are under the card")
        // Held, nothing can tap them: chips alone, no explanation
        explanation().assertCountEquals(0)
        compose.onNodeWithText(ward, useUnmergedTree = true).assertDoesNotExist()
        compose.runOnIdle { swipe.moved(-200f, 0f); assertTrue(swipe.released()) }
        compose.waitForIdle()
        chips()[1].performClick()
        compose.waitForIdle()
        compose.onNodeWithText(ward, useUnmergedTree = true).assertExists()
        captureShot("$SCREENSHOTS/inspect/reminders-pinned.png", "Portrait game Inspect of a Meadow Sentinel printed with " +
            "flying, ward and a Treasure it makes, pinned by a drag to the left: under the shrunk card the chips Flying, Ward and " +
            "Treasure, and a tap on Ward shows its explanation in the dark rule popup above the chip.")
        chips()[1].performClick()
        compose.waitForIdle()
        compose.onNodeWithText(ward, useUnmergedTree = true).assertDoesNotExist()
    }

    // TBL-209, TBL-210, DRF-063: held outside a game (a draft pick, Build, decks), where a release closes it, each chip shows its
    // explanation under it
    @Test fun outsideAGameEachChipShowsItsExplanation() {
        compose.setContent { MaterialTheme { NoRipples { PodCardArtPreview(card, card.definition.name) } } }
        compose.waitForIdle()
        chips().assertCountEquals(3)
        compose.onNodeWithText(ward, useUnmergedTree = true).assertExists()
        compose.onNodeWithText(RulesGlossary.token("Treasure")!!.text, useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("Card inspection content").performTouchInput { swipeUp() }
        compose.waitForIdle()
        captureShot("$SCREENSHOTS/inspect/reminders-draft.png", "A draft card held to inspect it, scrolled down: under the Meadow " +
            "Sentinel's picture a dark panel with the chips Flying, Ward and Treasure, each with its plain-words explanation " +
            "under it, since a release closes this Inspect and nothing could tap them.")
    }

    // TBL-209: a card whose text explains its words with reminder text shows no chips
    @Test fun aCardExplainingItselfShowsNoChips() {
        val explained = card.copy(definition = card.definition.copy(faces = card.definition.faces.mapIndexed { index, face ->
            if (index == 0) face.copy(rulesText = "Ward {2} (Whenever this creature becomes the target of a spell an opponent " +
                "controls, counter it unless that player pays {2}.)") else face }))
        compose.setContent { MaterialTheme { NoRipples { PodCardArtPreview(explained, explained.definition.name) } } }
        compose.waitForIdle()
        chips().assertCountEquals(0)
        compose.onAllNodes(hasContentDescription("Rules reminders"), useUnmergedTree = true).assertCountEquals(0)
    }
}
