package org.asyncttwiz.android

import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.asyncttwiz.protocol.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class PodPickChromeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun avatarBadgesNeverChangeTheSlimRowOrAvatarPositions() {
        val alice = AccountIdentity("alice", "Alice")
        val bob = AccountIdentity("bob", "Bob")
        var waiting by mutableIntStateOf(0)
        val detail = PodDetail("pod", "Pod", PodPhase.DRAFTING, alice, DraftSettings(),
            PodSource("Cube", 540), DraftSupply(90, 540),
            listOf(PodMember(alice), PodMember(bob, packsWaiting = 2)), emptyList(), updatedAt = "now")
        compose.setContent { ScreenTheme {
            PodDraftMemberRow(detail.copy(members = detail.members.map {
                if (it.account.accountId == alice.accountId) it.copy(packsWaiting = waiting) else it
            })) { OutlinedIconButton({}) { Text("⋮") } }
        } }
        val aliceAvatar = compose.onNodeWithContentDescription("Alice's avatar")
        val bobAvatar = compose.onNodeWithContentDescription("Bob's avatar")
        aliceAvatar.assertWidthIsEqualTo(28.dp).assertHeightIsEqualTo(28.dp)
        compose.onNodeWithTag("pod-draft-members").assertHeightIsEqualTo(40.dp)
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Ready")).assertExists()
        compose.onNodeWithText("Alice").assertDoesNotExist()
        compose.onNodeWithText("Bob").assertDoesNotExist()
        val aliceBounds = aliceAvatar.fetchSemanticsNode().boundsInRoot
        val bobBounds = bobAvatar.fetchSemanticsNode().boundsInRoot
        for (count in listOf(1, 9, 10, 100)) {
            compose.runOnIdle { waiting = count }
            assertEquals(aliceBounds, aliceAvatar.fetchSemanticsNode().boundsInRoot)
            assertEquals(bobBounds, bobAvatar.fetchSemanticsNode().boundsInRoot)
            compose.onNodeWithTag("pod-draft-members").assertHeightIsEqualTo(40.dp)
            compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "$count packs waiting")).assertExists()
        }
    }
}
