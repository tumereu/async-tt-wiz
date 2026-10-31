package org.asyncttwiz.android

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.input.ImeAction
import org.asyncttwiz.protocol.DraftSettings
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The keyboard's action key does what the screen's matching button does. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class KeyboardActionKeyTest {
    @get:Rule val compose = createComposeRule()

    private fun ime(action: ImeAction) = SemanticsMatcher.expectValue(SemanticsProperties.ImeAction, action)

    // LOB-102, SYS-047
    @Test fun signInEmailMovesToPasswordAndGoSignsIn() {
        val fixtures = ScreenFixtures()
        fixtures.auth.seed(AuthState(restoring = false, email = "a@example.test", password = "secret"))
        compose.setContent { AccountEntry(fixtures.auth, fixtures.reset) }
        compose.onNodeWithTag("email").assert(ime(ImeAction.Next)).performImeAction()
        compose.onNodeWithTag("password").assertIsFocused().assert(ime(ImeAction.Go))
        compose.runOnIdle { assertFalse(fixtures.auth.state.busy) }
        compose.onNodeWithTag("password").performImeAction()
        compose.runOnIdle { assertTrue(fixtures.auth.state.busy) }
    }

    // LOB-102, SYS-047
    @Test fun scaleInputDoneSavesOnlyAValidValue() {
        var saved: Float? = null
        compose.setContent { ScreenTheme { CardScaleInput("Hand cards", 1.25f, {}) { saved = it } } }
        val field = compose.onNodeWithTag("card-scale-input").assert(ime(ImeAction.Done))
        field.performTextReplacement("big")
        field.performImeAction()
        compose.runOnIdle { assertNull(saved) }
        field.performTextReplacement("2")
        field.performImeAction()
        compose.runOnIdle { assertEquals(2f, saved) }
    }

    // LOB-102, SYS-047
    @Test fun podSettingDoneSavesLikeTheSaveButton() {
        var saved: Int? = null
        compose.setContent { ScreenTheme { PodSettingPopup(PodSetting.PACKS, DraftSettings(packsPerPlayer = 3), {}) { saved = it } } }
        compose.onNode(hasSetTextAction()).performTextReplacement("4")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.runOnIdle { assertEquals(4, saved) }
    }

    @Test fun liveSearchActionKeyOnlyClosesTheKeyboard() {
        var hidden = 0
        val keyboard = object : SoftwareKeyboardController {
            override fun show() {}
            override fun hide() { hidden++ }
        }
        compose.setContent { ScreenTheme {
            CompositionLocalProvider(LocalSoftwareKeyboardController provides keyboard) {
                androidx.compose.foundation.layout.Column { PodSetPickerContent(PodSetPickerState(), {}, {}, {}, {}) }
            }
        } }
        compose.onNode(hasSetTextAction()).assert(ime(ImeAction.Search)).performImeAction()
        compose.runOnIdle { assertEquals(1, hidden) }
    }
}
