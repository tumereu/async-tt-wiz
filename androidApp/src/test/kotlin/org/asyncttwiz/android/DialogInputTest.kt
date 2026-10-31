package org.asyncttwiz.android

import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextRange
import org.asyncttwiz.protocol.DraftSettings
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class DialogInputTest {
    @get:Rule val compose = createComposeRule()

    private fun selection(start: Int, end: Int) = SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange(start, end))

    // SYS-047
    @Test fun dialogFieldOpensFocusedWithAllTextSelectedAndShowsTheKeyboard() {
        var shown = 0
        val keyboard = object : SoftwareKeyboardController {
            override fun show() { shown++ }
            override fun hide() {}
        }
        var typed = ""
        compose.setContent { ScreenTheme {
            ScreenDialog({}) {
                CompositionLocalProvider(LocalSoftwareKeyboardController provides keyboard) {
                    val name = rememberDialogField("Old name")
                    OutlinedTextField(name.value, { name.value = it; typed = it.text }, Modifier.dialogField(name), label = { Text("Name") })
                }
            }
        } }
        compose.onNode(hasSetTextAction()).assertIsFocused().assert(selection(0, 8))
        compose.runOnIdle { assertTrue(shown >= 1) }
        // Typing replaces the selected text.
        compose.onNode(hasSetTextAction()).performTextInputSelection(TextRange(0, 8))
        compose.onNode(hasSetTextAction()).performTextInput("New")
        compose.runOnIdle { assertEquals("New", typed) }
    }

    // SYS-047
    @Test fun podSettingPopupOpensWithItsValueSelected() {
        compose.setContent { ScreenTheme { PodSettingPopup(PodSetting.PACKS, DraftSettings(packsPerPlayer = 12), {}) {} } }
        compose.onNode(hasSetTextAction()).assertIsFocused().assert(selection(0, 2))
    }

    // SYS-047
    @Test fun scaleInputOpensWithItsValueSelected() {
        compose.setContent { ScreenTheme { CardScaleInput("Hand cards", 1.25f, {}) {} } }
        compose.onNodeWithTag("card-scale-input").assertIsFocused().assert(selection(0, 4))
    }
}
