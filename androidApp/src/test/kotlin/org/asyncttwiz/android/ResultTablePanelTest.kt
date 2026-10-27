package org.asyncttwiz.android

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.Download
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class ResultTablePanelTest {
    @get:Rule val compose = createComposeRule()

    @Test fun outcomeDrawUsesHandshakeWithoutChangingDrawCardIcon() {
        assertEquals(Icons.Outlined.Handshake, menuIcon("Draw"))
        assertEquals(Icons.Outlined.Handshake, menuIcon("Suggest draw"))
        assertEquals(Icons.Outlined.Download, menuIcon("Draw N"))
    }

    @Test fun onTablePanelHasRoleSpecificButtonsWithoutModalBackOrOutsideTapHandling() {
        val target = ResultAcceptanceTarget(ResultTarget("game", "play"), "suggestion")
        var opponent by mutableStateOf(false)
        var enabled by mutableStateOf(true)
        var backs = 0
        lateinit var dispatcher: OnBackPressedDispatcher
        val rejected = mutableListOf<ResultAcceptanceTarget>()
        val accepted = mutableListOf<ResultAcceptanceTarget>()
        compose.setContent { MaterialTheme {
            dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            BackHandler { backs++ }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ResultTablePanel(ResultPresentation(target, "Alice proposed a draw.", opponent),
                    TableResultControls(enabled, rejected::add, accepted::add))
            }
        } }
        compose.onNodeWithContentDescription("Withdraw result").assertIsDisplayed()
        compose.onNodeWithContentDescription("Accept result").assertDoesNotExist()
        compose.onAllNodes(isDialog()).assertCountEquals(0)
        compose.onRoot().performTouchInput { click(Offset(2f, 2f)) }
        compose.runOnIdle { dispatcher.onBackPressed() }
        assertEquals(1, backs)
        compose.onNodeWithText("Alice proposed a draw.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Withdraw result").performClick()
        assertEquals(listOf(target), rejected)
        compose.runOnIdle { opponent = true }
        compose.onNodeWithContentDescription("Withdraw result").assertDoesNotExist()
        compose.onNodeWithContentDescription("Decline result").performClick()
        compose.onNodeWithContentDescription("Accept result").performClick()
        assertEquals(listOf(target, target), rejected)
        assertEquals(listOf(target), accepted)
        compose.runOnIdle { enabled = false }
        compose.onNodeWithContentDescription("Decline result").assertIsNotEnabled().performClick()
        compose.onNodeWithContentDescription("Accept result").assertIsNotEnabled().performClick()
        assertEquals(listOf(target, target), rejected)
        assertEquals(listOf(target), accepted)
        compose.runOnIdle { opponent = false }
        compose.onNodeWithContentDescription("Withdraw result").assertIsNotEnabled().performClick()
        assertEquals(listOf(target, target), rejected)
    }
}
