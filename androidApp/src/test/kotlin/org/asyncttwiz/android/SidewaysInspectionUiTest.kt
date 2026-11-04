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

    private fun show(card: InspectedCard) = compose.setContent { MaterialTheme {
        InspectContent(inspectTiles(card, card.definition.name), androidx.compose.foundation.rememberScrollState())
    } }

    // TBL-108
    @Test fun splitCardInspectionIsLandscapeAndNoTallerThanAnUprightCard() {
        show(card("split", "Instant", "Instant"))
        val image = compose.onNodeWithContentDescription("Card image: Sideways test").getBoundsInRoot()
        assertTrue(image.width > image.height, "$image")
        assertTrue(image.height.value <= 448.5f, "$image")
        compose.onAllNodesWithContentDescription("Card image", substring = true).assertCountEquals(1)
        compose.onNodeWithContentDescription("Rotate card").assertDoesNotExist()
    }

    // TBL-108
    @Test fun aftermathCardsStayUprightWithoutARotateButton() = assertUpright(card("aftermath", "Instant", "Sorcery"))

    // TBL-108
    @Test fun flipCardsStayUprightWithoutARotateButton() =
        assertUpright(card("flip", "Creature — Human Soldier", "Legendary Creature — Spirit"))

    private fun assertUpright(card: InspectedCard) {
        show(card)
        val image = compose.onNodeWithContentDescription("Card image: Sideways test").getBoundsInRoot()
        assertTrue(image.height > image.width, "$image")
        compose.onNodeWithContentDescription("Rotate card").assertDoesNotExist()
    }
}
