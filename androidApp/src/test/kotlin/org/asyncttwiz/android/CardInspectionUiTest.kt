package org.asyncttwiz.android

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.asyncttwiz.protocol.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class CardInspectionUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun tableHoldScrollsToBottomAndBackWithoutRelease() = tableHold(false)

    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi")
    fun landscapeTableHoldScrollsInfoWithoutRelease() = tableHold(true)

    private fun tableHold(landscape: Boolean) {
        val f = TableStatesFixture
        val play = f.combat().let { p -> p.copy(definitions = p.definitions.map { definition ->
            definition.copy(definition = definition.definition.copy(faces = definition.definition.faces.map { face ->
                face.copy(manaCost = "{2}{W/U}{W/U/P}", rulesText = (1..30).joinToString("\n") { "Ability $it: {T}: Add {W}." })
            }))
        }) }
        val sent = mutableListOf<PlayAction>()
        val toolkit = TableToolkitController(f.a, { play }, { true }, { action, _ -> sent += action; true })
        compose.setContent { MaterialTheme {
            TableSurface(f.game, play, f.a, landscape, Modifier.fillMaxSize(), connected = true,
                toolkit = toolkit, onIntent = toolkit::handle)
        } }
        val scene = TablePresentation.scene(f.game, play, f.a, if (landscape) 892f else 412f, if (landscape) 412f else 892f, landscape)
        val card = scene.items.first { it.owner == f.a && it.kind == TableItemKind.CARD && it.zone == PlayZone.BATTLEFIELD && it.visible.isNotEmpty() }
        val point = card.visible.first().center
        val table = compose.onNodeWithContentDescription("Shared table")
        compose.mainClock.autoAdvance = false
        table.performTouchInput { down(Offset(point.x, point.y)) }
        compose.waitForIdle(); compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("Enlarge artwork").assertDoesNotExist()
        compose.onNodeWithContentDescription("Close card inspection").assertDoesNotExist()
        compose.onNodeWithText("{2}{W/U}{W/U/P}").assertExists() // Original tokens remain accessible.
        val content = compose.onNodeWithContentDescription("Card inspection content")
        fun scroll() = content.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertEquals(0f, scroll().value())
        table.performTouchInput { moveBy(Offset(0f, -10_000f)) }
        compose.waitForIdle()
        assertTrue(scroll().value() > 0f)
        assertEquals(scroll().maxValue(), scroll().value())
        compose.onNodeWithText("Ability 30: {T}: Add {W}.").assertIsDisplayed()
        table.performTouchInput { moveBy(Offset(0f, 40f)) }
        compose.waitForIdle()
        assertTrue(scroll().value() < scroll().maxValue(), "Reverse immediately even after overscrolling")
        table.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        content.assertDoesNotExist()
        compose.runOnIdle { assertNull(toolkit.peek); assertNull(toolkit.inspection) }
        assertTrue(sent.isEmpty())
    }

    @Test fun explicitInspectionRetainsTitleAndArtworkControls() {
        val controller = CardInspectionController().apply {
            open(InspectedCard(CardDefinition("Inspector header", listOf(DefinitionFace("face", "Face name",
                manaCost = "{0}{G}", rulesText = "{Q}: Add {S}{E}. Unknown {FOO}."))), listOf(null)))
        }
        compose.setContent { MaterialTheme { CardInspection(controller) } }
        compose.onNodeWithText("Inspector header").assertExists()
        compose.onNodeWithText("Enlarge artwork").assertExists()
        compose.onNodeWithText("{Q}: Add {S}{E}. Unknown {FOO}.").assertExists()
        compose.onNodeWithContentDescription("Close card inspection").performClick()
        compose.runOnIdle { assertNull(controller.state) }
    }
}
