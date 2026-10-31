package org.asyncttwiz.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
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
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class SidewaysInspectionUiTest {
    @get:Rule val compose = createComposeRule()

    private fun card(layout: String, vararg types: String) = InspectedCard(CardDefinition("Sideways test", types.mapIndexed { index, type ->
        DefinitionFace("f$index", "Face $index", typeLine = type) }, layout = layout), types.map { null })

    @Test fun quarterTurnLaysOutPortraitContentAcrossALandscapeBox() {
        var measured = IntSize.Zero
        compose.setContent { Box(Modifier.size(300.dp, 200.dp)) {
            Box(Modifier.fillMaxSize().turned(90).onSizeChanged { measured = it }.testTag("turned"))
        } }
        compose.runOnIdle { assertEquals(IntSize(200, 300), measured) }
        val bounds = compose.onNodeWithTag("turned").getBoundsInRoot()
        assertEquals(0f, bounds.left.value, .5f); assertEquals(0f, bounds.top.value, .5f)
        assertEquals(300f, bounds.width.value, .5f); assertEquals(200f, bounds.height.value, .5f)
    }

    // TBL-108
    @Test fun splitCardInspectionIsLandscapeWithoutRotateButton() {
        val controller = CardInspectionController().apply { open(card("split", "Instant")) }
        compose.setContent { MaterialTheme { CardInspection(controller) } }
        val viewport = compose.onNodeWithContentDescription("Card artwork viewport").getBoundsInRoot()
        assertTrue(viewport.width > viewport.height, "$viewport")
        compose.onNodeWithContentDescription("Rotate card").assertDoesNotExist()
    }

    // TBL-108
    @Test fun aftermathInspectionTurnsOnlyOnRequest() {
        val controller = CardInspectionController().apply { open(card("aftermath", "Instant")) }
        compose.setContent { MaterialTheme { CardInspection(controller) } }
        fun viewport() = compose.onNodeWithContentDescription("Card artwork viewport").getBoundsInRoot()
        assertTrue(viewport().height > viewport().width)
        compose.onNodeWithContentDescription("Rotate card").performClick()
        compose.onNodeWithContentDescription("Rotate back").assertExists()
        compose.runOnIdle { assertEquals(270, controller.state!!.degrees) }
        assertTrue(viewport().width > viewport().height)
        compose.onNodeWithContentDescription("Rotate back").performClick()
        compose.runOnIdle { assertEquals(0, controller.state!!.degrees) }
    }

    @Test fun deckInspectionOffersTheTurnForFlipCards() {
        val controller = CardInspectionController().apply { open(card("flip", "Creature — Human Soldier", "Legendary Creature — Spirit")) }
        compose.setContent { MaterialTheme { DeckCardInspection(controller) } }
        // Without artwork the deck inspector shows text only, so no artwork controls appear.
        compose.onNodeWithContentDescription("Rotate card").assertDoesNotExist()
        compose.onNodeWithText("Face 0").assertExists()
    }
}
