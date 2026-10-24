package org.asyncttwiz.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.asyncttwiz.protocol.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Basic production inspector rendering, including a real phone density rather than only mdpi. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 35], qualifiers = "w412dp-h892dp-port-xxhdpi", fontScale = 1.3f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InspectionRenderingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun holdPeekRendersTypicalCards() = render(true)
    @Test fun inspectActionRendersTypicalCards() = render(false)

    @Test @Config(qualifiers = "w892dp-h412dp-land-xxhdpi")
    fun landscapeHoldPeekRendersTypicalCards() = render(true)

    @Test @Config(qualifiers = "w892dp-h412dp-land-xxhdpi")
    fun landscapeInspectActionRendersTypicalCards() = render(false)

    @OptIn(ExperimentalRoborazziApi::class)
    private fun render(peek: Boolean) {
        val f = TableStatesFixture
        val original = f.source(f.a, "1/3")
        val front = DefinitionFace("front", "Mana wizard", imageUri = "https://cards.scryfall.io/normal/front/a/b/test.jpg", manaCost = "{2}{W/U}",
            rulesText = "{T}: Add {G}.\n{1}{U}, {Q}: Draw a card.")
        val cases = listOf(
            CardDefinition("Mana wizard", listOf(front)),
            CardDefinition("Two faces", listOf(front, front.copy(faceId = "back", name = "Back face"))),
            CardDefinition("Token", listOf(front.copy(name = "Soldier token"))),
            CardDefinition("Covered", listOf(front)),
            CardDefinition("No art", listOf(front.copy(imageUri = null)))
        )
        val selected = mutableStateOf(0)
        compose.setContent {
            val index = selected.value
            val definition = cases[index]
            val card = original.copy(token = index == 2, covered = index == 3)
            val play = f.initial.copy(definitions = f.initial.definitions.map {
                if (it.digest == card.definition) it.copy(definition = definition) else it
            }, board = f.initial.board.copy(cards = f.initial.board.cards.map { if (it.id == card.id) card else it }))
            MaterialTheme {
                GameplayCardInspection(play, f.a, ManualInspection(play.gameId, play.id, card.id,
                    if (index == 3) emptyList() else definition.faces.map { it.faceId }), {}, peek = peek)
            }
        }
        cases.indices.forEach { index ->
            compose.runOnIdle { selected.value = index }
            compose.waitForIdle()
            val image = java.io.File.createTempFile("inspection-", ".png")
            try { captureScreenRoboImage(image.absolutePath) } finally { image.delete() }
            if (index == 3) compose.onNodeWithText("Covered card").assertIsDisplayed()
            else {
                compose.onNodeWithContentDescription("Card artwork viewport").assertExists()
                compose.onNodeWithText("{T}: Add {G}.").assertExists()
                if (!peek && index == 1) {
                    compose.onNodeWithText("Next face").performClick()
                    compose.onNodeWithText("Back face").assertExists()
                }
            }
        }
    }
}
