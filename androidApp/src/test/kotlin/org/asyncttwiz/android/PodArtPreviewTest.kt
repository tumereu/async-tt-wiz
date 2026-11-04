package org.asyncttwiz.android

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.asyncttwiz.protocol.CardDefinition
import org.asyncttwiz.protocol.DefinitionFace
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PodArtPreviewTest {
    @get:Rule val compose = createComposeRule()

    private fun show(card: InspectedCard, width: Int, height: Int): ScrollState {
        lateinit var scroll: ScrollState
        compose.setContent { MaterialTheme {
            scroll = rememberScrollState()
            Box(Modifier.size(width.dp, height.dp)) { InspectContent(inspectTiles(card, card.definition.name), scroll) }
        } }
        return scroll
    }

    // DRF-063, TBL-106: a double-faced card held outside a game shows both faces, one under the other, scrolled by the finger
    @Test fun doubleFacedHoldContentShowsEveryFaceStacked() {
        val card = InspectedCard(CardDefinition("Front // Back", listOf(DefinitionFace("a", "Front"),
            DefinitionFace("b", "Back")), layout = "modal_dfc"), listOf(null, null))
        val scroll = show(card, 300, 400)
        val front = compose.onNodeWithContentDescription("Card image: Front").assertIsDisplayed().fetchSemanticsNode()
        val back = compose.onNodeWithContentDescription("Card image: Back").assertExists().fetchSemanticsNode()
        // Unclipped: the back lies below the front, past the visible part until scrolled
        assertTrue(front.positionInRoot.y + front.size.height <= back.positionInRoot.y)
        compose.runOnIdle { assertTrue(scroll.maxValue > 0) }
        compose.onNodeWithContentDescription("Previous face").assertDoesNotExist()
    }

    // TBL-106, TBL-108
    @Test fun splitCardKeepsOneFittedImageAndNoScrolling() {
        val card = InspectedCard(CardDefinition("X // Y", listOf(DefinitionFace("a", "X"),
            DefinitionFace("b", "Y")), layout = "split"), listOf(null, null))
        val scroll = show(card, 320, 300)
        compose.onNodeWithContentDescription("Card image: X // Y").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Card image", substring = true).assertCountEquals(1)
        compose.runOnIdle { assertEquals(0, scroll.maxValue) }
    }
}
