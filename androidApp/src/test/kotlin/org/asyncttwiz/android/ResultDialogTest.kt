package org.asyncttwiz.android

import androidx.activity.ComponentDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.Download
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class ResultDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test fun outcomeDrawUsesHandshakeWithoutChangingDrawCardIcon() {
        assertEquals(Icons.Outlined.Handshake, menuIcon("Draw"))
        assertEquals(Icons.Outlined.Handshake, menuIcon("Suggest draw"))
        assertEquals(Icons.Outlined.Download, menuIcon("Draw N"))
    }

    @Test fun bothPlayersMustResolveTheSuggestionButCanOpenChatAndHistory() {
        val target = ResultAcceptanceTarget(ResultTarget("game", "play"), "suggestion")
        var opponent by mutableStateOf(false)
        var chat = 0
        var history = 0
        val rejected = mutableListOf<ResultAcceptanceTarget>()
        val accepted = mutableListOf<ResultAcceptanceTarget>()
        compose.setContent { MaterialTheme {
            ResultAcceptanceDialog(ResultPresentation(target, "Alice proposed a draw.", opponent), true,
                { chat++ }, { history++ }, rejected::add, accepted::add)
        } }
        compose.onNodeWithText("Withdraw").assertIsDisplayed()
        compose.onNodeWithContentDescription("Accept result").assertDoesNotExist()
        compose.runOnIdle { (ShadowDialog.getLatestDialog() as ComponentDialog).onBackPressedDispatcher.onBackPressed() }
        compose.onAllNodes(isDialog()).assertCountEquals(1)
        compose.onNodeWithContentDescription("Chat").performClick()
        compose.onNodeWithContentDescription("History").performClick()
        assertEquals(1, chat)
        assertEquals(1, history)
        compose.onNodeWithText("Withdraw").performClick()
        assertEquals(listOf(target), rejected)
        compose.runOnIdle { opponent = true }
        compose.onNodeWithText("Withdraw").assertDoesNotExist()
        compose.onNodeWithText("Decline").performClick()
        compose.onNodeWithContentDescription("Accept result").performClick()
        assertEquals(listOf(target, target), rejected)
        assertEquals(listOf(target), accepted)
    }
}
