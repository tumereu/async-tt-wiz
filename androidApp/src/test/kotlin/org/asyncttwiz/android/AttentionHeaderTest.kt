package org.asyncttwiz.android

import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import javax.imageio.ImageIO
import org.asyncttwiz.protocol.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w892dp-h892dp-mdpi")
class AttentionHeaderTest {
    @get:Rule val compose = createComposeRule()

    // TRN-005, TRN-006
    @Test fun pillFitsHeaderInBothOrientationsAndHoldAndReviewNeverPass() {
        val initial = TableFixture.play()
        val play = mutableStateOf(initial)
        val landscape = mutableStateOf(false)
        val reviewing = mutableStateOf(false)
        val sent = mutableListOf<PlayAction>()
        val toolkit = TableToolkitController("a", { play.value }, { true }, { action, _ -> sent += action; true })
        compose.setContent {
            MaterialTheme {
                TableSurface(TableFixture.game, play.value, "a", landscape.value,
                    Modifier.requiredSize(if (landscape.value) 892.dp else 412.dp, if (landscape.value) 412.dp else 892.dp),
                    connected = true, onIntent = toolkit::handle, toolkit = toolkit,
                    runtime = TableRuntimeHooks(navigation = {}, chat = {}, history = {},
                        reviewing = reviewing.value, returnCurrent = {}, continueHere = {}))
            }
        }
        for (wide in listOf(false, true)) {
            compose.runOnIdle { landscape.value = wide; reviewing.value = false }
            // Name and suffix are separate texts so only the name ellipsizes.
            compose.onNodeWithContentDescription(TableFixture.game.gameName).assertIsDisplayed()
                .assert(hasText("Your turn")).assert(hasText(" · 1"))
            val pill = compose.onNodeWithContentDescription("Your move")
            pill.assertIsDisplayed().assertIsEnabled()
            val liveImage = File("$SCREENSHOTS/move-frame-live-$wide.png").also { it.parentFile!!.mkdirs() }
            compose.onRoot().captureRoboImage(liveImage.path)
            val livePixels = ImageIO.read(liveImage)
            assertNotEquals(OwnTableColor.toArgb(), livePixels.getRGB(3, livePixels.height / 2), "No move frame in play")
            val bounds = pill.fetchSemanticsNode().boundsInRoot
            val phase = compose.onNodeWithContentDescription("Turn 1, Alice's turn, Beginning · Untap. Choose phase/step").fetchSemanticsNode().boundsInRoot
            val menu = compose.onNodeWithContentDescription("Open navigation").fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.left >= menu.right)
            assertTrue(bounds.right <= phase.left)
            compose.onNodeWithContentDescription("Table actions").assertIsDisplayed()
            compose.onNodeWithContentDescription("Chat, 0 unread messages").assertIsDisplayed()
            pill.performTouchInput { longClick() }
            compose.runOnIdle { assertTrue(sent.isEmpty()) }
            pill.performTouchInput { click() }
            compose.runOnIdle { assertEquals(listOf<PlayAction>(PlayAction.PassAttention), sent); sent.clear() }
            compose.runOnIdle { reviewing.value = true }
            val reviewImage = File("$SCREENSHOTS/move-frame-review-$wide.png")
            compose.onRoot().captureRoboImage(reviewImage.path)
            val reviewPixels = ImageIO.read(reviewImage)
            assertNotEquals(OwnTableColor.toArgb(), reviewPixels.getRGB(3, reviewPixels.height / 2), "No move frame in review")
            pill.assertIsNotEnabled().performTouchInput { click() }
            compose.runOnIdle { assertTrue(sent.isEmpty()) }
        }
        compose.runOnIdle { play.value = initial.copy(board = initial.board.copy(attentionHolder = "b")); reviewing.value = false }
        compose.onNodeWithContentDescription("Waiting · Bob").assertIsDisplayed().performTouchInput { click() }
        compose.runOnIdle { assertEquals(listOf<PlayAction>(PlayAction.PassAttention), sent) }
    }
}
