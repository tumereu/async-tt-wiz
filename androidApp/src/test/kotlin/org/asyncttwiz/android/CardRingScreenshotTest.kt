package org.asyncttwiz.android

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.asyncttwiz.protocol.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class CardRingScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val f = TableStatesFixture

    @OptIn(ExperimentalRoborazziApi::class)
    private fun matrix(name: String, landscape: Boolean, font: Float) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("card-ring-pages", Context.MODE_PRIVATE).edit().clear().commit()
        val play = f.combat()
        val sent = mutableListOf<PlayAction>()
        val toolkit = TableToolkitController(f.a, { play }, { true }, { action, _ -> sent += action; true })
        var position by mutableIntStateOf(0)
        var actualAnchor: TableBounds? = null
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, font)) {
                MaterialTheme { NoRipples {
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        TableSurface(f.game, play, f.a, landscape, Modifier.fillMaxSize())
                        val card = if (position == 2) f.source(f.b, "2/3") else f.source(f.a, "1/3")
                        val anchor = when (position) {
                            0 -> TableBounds(maxWidth.value / 2 - 24, maxHeight.value / 2 - 50, 48f, 100f)
                            1 -> TableBounds(maxWidth.value - 52, maxHeight.value - 104, 48f, 100f)
                            else -> TableBounds(4f, 4f, 48f, 100f)
                        }
                        SideEffect { actualAnchor = anchor }
                        Box(Modifier.offset(anchor.x.dp, anchor.y.dp)) { ManualCardPreview(play, f.a, card.id, 1) }
                        CompositionLocalProvider(LocalCardRingAnchors provides mapOf(card.id to anchor)) {
                            TableToolkitScreen(toolkit, play, f.game, landscape, TableSelection())
                        }
                    }
                } }
            }
        }
        for (at in 0..2) {
            compose.runOnIdle {
                position = at
                CardRingPageStore(context, f.a, play.gameId).write(0)
                val card = if (at == 2) f.source(f.b, "2/3") else f.source(f.a, "1/3")
                toolkit.openCard(TableScope(card.id, listOf(card.id)))
            }
            val pageBounds = mutableListOf<androidx.compose.ui.geometry.Rect>()
            val headerBounds = mutableListOf<androidx.compose.ui.geometry.Rect>()
            for (page in 0..2) {
                compose.mainClock.advanceTimeBy(350)
                compose.onNodeWithContentDescription("Card action ring").assertIsDisplayed()
                compose.onAllNodes(isDialog()).assertCountEquals(0)
                val indicator = compose.onNode(hasContentDescription("Action page ${page + 1} of ", substring = true))
                indicator.assertIsDisplayed()
                val square = compose.onNodeWithContentDescription("Card action grid").fetchSemanticsNode().boundsInRoot
                pageBounds += square
                headerBounds += indicator.fetchSemanticsNode().boundsInRoot
                assertEquals(pageBounds.first(), square, "Popup bounds must stay fixed while paging")
                assertEquals(headerBounds.first(), headerBounds.last(), "Header must stay fixed while paging")
                assertEquals(248f, square.width)
                assertTrue(square.height <= 300f)
                if (page == 0) for (name in listOf("Inspect", "Set corner", "Clone", "Ping / Unping"))
                    compose.onNodeWithContentDescription(name).assertIsDisplayed()
                if (page == 1) for (color in CounterColor.entries) for (prefix in listOf("Set", "Decrease", "Increase"))
                    compose.onNodeWithContentDescription("$prefix ${color.name.lowercase()} counter").assertIsDisplayed()
                if (page == 1) compose.onNodeWithContentDescription("Set corner").assertDoesNotExist()
                val card = actualAnchor!!
                val buttons = compose.onAllNodes(hasClickAction() and hasAnyAncestor(hasContentDescription("Card action ring")) and
                    SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.Role, androidx.compose.ui.semantics.Role.Button)).fetchSemanticsNodes()
                for (button in buttons) {
                    val name = button.config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription].first()
                    val node = compose.onNodeWithContentDescription(name)
                    if (name !in listOf("Previous page", "Choose action page", "Next page")) node.performScrollTo()
                    val b = node.fetchSemanticsNode().boundsInRoot
                    val edge = name.startsWith("Increase ") || name.startsWith("Decrease ")
                    assertTrue(b.width >= (if (edge) 23.9f else 47.9f) && b.height >= 47.9f, "Widget target: ${button.config} $b")
                    assertNull(TableBounds(b.left, b.top, b.width, b.height).intersection(card), "Cell covers card: $b $card")
                }
                val file = File("$SCREENSHOTS/ring-$name-${listOf("middle", "hand", "opponent")[at]}-page${page + 1}.png")
                file.parentFile!!.mkdirs()
                captureScreenRoboImage(file.path)
                if (page < 2) compose.ringPage(page + 2)
            }
            compose.runOnIdle { toolkit.dismiss() }
            compose.waitForIdle()
        }
        assertTrue(sent.isEmpty())
    }

    // TBL-063
    @Test fun portraitEveryPageMiddleHandAndOpponent() { matrix("412x892", false, 1f) }
    @Test @Config(qualifiers = "w360dp-h640dp-port-mdpi")
    fun smallLargeFontEveryPageMiddleHandAndOpponent() { matrix("360x640-font1.3", false, 1.3f) }
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi")
    fun landscapeEveryPageMiddleHandAndOpponent() { matrix("892x412-font1.3", true, 1.3f) }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun handPage(landscape: Boolean) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        CardRingPageStore(context, f.a, f.game.id).write(1, CardMenuLayout.HAND)
        val play = f.initial
        val hand = play.board.containers.single { it.accountId == f.a && it.zone == PlayZone.HAND }
        val cardId = hand.cards.first()
        val toolkit = TableToolkitController(f.a, { play }, { true }, { _, _ -> error("Opening must remain local") })
        toolkit.openCard(TableScope(cardId, listOf(cardId)))
        compose.setContent { MaterialTheme {
            TableSurface(f.game, play, f.a, landscape, Modifier.fillMaxSize(), toolkit = toolkit)
        } }
        compose.mainClock.advanceTimeBy(350)
        // One page: no header, and a stale stored page index clamps to it.
        compose.onNodeWithContentDescription("Card action grid").assertIsDisplayed()
        compose.onNodeWithContentDescription("Choose action page").assertDoesNotExist()
        compose.onNodeWithContentDescription("Next page").assertDoesNotExist()
        val inspect = compose.onNodeWithContentDescription("Inspect").fetchSemanticsNode().boundsInRoot
        for ((index, name) in CardRingPages.hand.single().actions.withIndex()) {
            val label = if (name == "inspect") "Inspect" else name
            val bounds = compose.onNodeWithContentDescription(label).performScrollTo().assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertEquals(inspect.left + index % 5 * (inspect.width + 2f), bounds.left, .1f)
        }
        val file = File("$SCREENSHOTS/ring-hand-${if (landscape) "landscape" else "portrait"}.png")
        file.parentFile!!.mkdirs()
        captureScreenRoboImage(file.path)
    }

    @Test fun handPageHasNoHeaderAndFixedCellsPortrait() { handPage(false) }
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi")
    fun handPageHasNoHeaderAndFixedCellsLandscape() { handPage(true) }
}

/** First press on a single-page ring (no page header): past the uptime guard like [ringPage]. */
internal fun ComposeContentTestRule.ringClick(description: String) {
    mainClock.advanceTimeBy(350)
    onNodeWithContentDescription(description).performTouchInput { advanceEventTime(350); click() }
}

internal fun ComposeContentTestRule.ringPage(page: Int) {
    mainClock.advanceTimeBy(350)
    // Explicit event time keeps physical presses past the uptime guard without an entrance animation.
    onNodeWithContentDescription("Choose action page").performTouchInput { advanceEventTime(350); click() }
    val name = if (page <= 3) listOf("Everyday", "Counters", "More")[page - 1] else "More ${page - 2}"
    onNodeWithContentDescription("$name page").performScrollTo().performClick()
    onNode(hasContentDescription("Action page $page of ", substring = true)).assertIsDisplayed()
}
