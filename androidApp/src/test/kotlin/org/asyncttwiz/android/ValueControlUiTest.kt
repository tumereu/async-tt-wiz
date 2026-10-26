package org.asyncttwiz.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ValueControlUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun minusSquareValuePlusShareOrderingAndDisabledAndSetBehaviour() {
        var value by mutableIntStateOf(100)
        var opened = 0
        compose.setContent { MaterialTheme {
            ValueControl(value.toString(), MenuControl("Set test") { opened++ },
                MenuControl("Decrease test", value > 99) { value-- }, MenuControl("Increase test") { value++ })
        } }
        val minus = compose.onNodeWithContentDescription("Decrease test")
        val field = compose.onNodeWithContentDescription("Set test")
        val plus = compose.onNodeWithContentDescription("Increase test")
        field.assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp)
        assertTrue(minus.fetchSemanticsNode().boundsInRoot.right <= field.fetchSemanticsNode().boundsInRoot.left)
        assertTrue(field.fetchSemanticsNode().boundsInRoot.right <= plus.fetchSemanticsNode().boundsInRoot.left)
        minus.performClick(); minus.assertIsNotEnabled(); minus.performClick()
        assertEquals(99, value)
        plus.performClick(); assertEquals(100, value)
        field.performClick(); assertEquals(1, opened)
    }

    @Test fun quantityCanBeSteppedAndTypedWithoutCommittingAGameAction() {
        var input by mutableStateOf(selectedLibraryQuantity("999"))
        compose.setContent { MaterialTheme { ValueInputControl("Test quantity", input, { input = it }, true) } }
        compose.onNodeWithContentDescription("Increase Test quantity").performClick()
        assertEquals("1000", input.text)
        compose.onNodeWithContentDescription("Decrease Test quantity").performClick()
        assertEquals("999", input.text)
        compose.onNode(hasSetTextAction()).performTextReplacement("12")
        assertEquals("12", input.text)
        assertEquals(0, stepValueInput("0", -1))
        assertEquals(1, stepValueInput("1", -1, 1))
        assertEquals(Int.MAX_VALUE, stepValueInput(Int.MAX_VALUE.toString(), 1))
    }
}
