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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PodArtPreviewTest {
    @get:Rule val compose = createComposeRule()

    @Test fun doubleFacedHoldContentShowsOnlyTheFittedFrontLikeTheGameTable() {
        lateinit var scroll: ScrollState
        val card = InspectedCard(CardDefinition("Front // Back", listOf(DefinitionFace("a", "Front"),
            DefinitionFace("b", "Back")), layout = "modal_dfc"), listOf(null, null))
        compose.setContent { MaterialTheme {
            scroll = rememberScrollState()
            Box(Modifier.size(320.dp, 300.dp)) { PodArtPreviewContent(card, card.definition.name, scroll) }
        } }
        compose.onNodeWithTag("pod-preview-face-0").assertIsDisplayed()
        compose.onNodeWithTag("pod-preview-face-1").assertDoesNotExist()
        compose.onNodeWithText("Back").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, scroll.maxValue) }
        compose.onNodeWithText("Previous face").assertDoesNotExist()
    }

    @Test fun splitCardKeepsOneFittedImageAndNoScrolling() {
        lateinit var scroll: ScrollState
        val card = InspectedCard(CardDefinition("X // Y", listOf(DefinitionFace("a", "X"),
            DefinitionFace("b", "Y")), layout = "split"), listOf(null, null))
        compose.setContent { MaterialTheme {
            scroll = rememberScrollState()
            Box(Modifier.size(320.dp, 300.dp)) { PodArtPreviewContent(card, card.definition.name, scroll) }
        } }
        compose.onNodeWithTag("pod-preview-face-0").assertIsDisplayed()
        compose.onNodeWithTag("pod-preview-face-1").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, scroll.maxValue) }
    }
}
