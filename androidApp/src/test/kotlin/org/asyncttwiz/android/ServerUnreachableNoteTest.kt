package org.asyncttwiz.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class ServerUnreachableNoteTest {
    @get:Rule val compose = createComposeRule()

    // SYS-020
    @Test fun noteLetsTapsReachTheControlUnderneath() {
        var confirms = 0
        compose.setContent {
            ScreenTheme {
                Box(Modifier.fillMaxSize()) {
                    Button({ confirms++ }, Modifier.align(Alignment.BottomCenter)) { Text("Confirm") }
                    ServerUnreachableNote(true, Modifier.align(Alignment.BottomCenter))
                }
            }
        }
        compose.onNodeWithText("Can't reach the server · retrying").assertExists().performClick()
        assertEquals(1, confirms)
    }
}
