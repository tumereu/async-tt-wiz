package org.asyncttwiz.android

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import android.graphics.Paint
import javax.imageio.ImageIO
import org.asyncttwiz.protocol.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.test.*
import org.asyncttwiz.appui.AppPlatform

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CardSettingsUiTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    @Before fun clear() { context.getSharedPreferences("card-appearance", Context.MODE_PRIVATE).edit().clear().commit() }

    // LOB-081, TBL-034, TBL-036
    @Test fun typedValuesValidateLimitsAndLiveSampleTracksSliderAndCustomization() {
        val preferences = CardAppearancePreferences(AppPlatform.current)
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) { CardSettings(preferences) } } }
        compose.onNodeWithTag("card-size-value").performClick()
        compose.onNodeWithTag("card-scale-input").performTextReplacement("9")
        compose.onNodeWithTag("card-scale-save").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(3f, preferences.read().cardSize) }
        compose.onNodeWithTag("card-size-value").performClick()
        compose.onNodeWithTag("card-scale-input").performTextReplacement("NaN")
        compose.onNodeWithTag("card-scale-save").assertIsNotEnabled()
        compose.onNodeWithTag("card-scale-input").performTextReplacement("0.1")
        compose.onNodeWithTag("card-scale-save").assertIsEnabled().performClick()
        compose.onNodeWithTag("card-size-sample").assertWidthIsEqualTo(14.dp).assertHeightIsEqualTo(20.dp)
        compose.onNodeWithTag("card-size").performTouchInput { swipeRight() }
        compose.runOnIdle { assertTrue(preferences.read().cardSize > .5f) }
        compose.onNodeWithText("Customize per area").performClick()
        compose.onNodeWithTag("card-size-HAND").assertExists()
        compose.onNodeWithTag("card-size-HAND-value").performScrollTo().performClick()
        compose.onNodeWithTag("card-scale-input").performTextReplacement("3")
        compose.onNodeWithTag("card-scale-save").performClick()
        compose.onNodeWithTag("card-size-HAND-sample").assertWidthIsEqualTo(84.dp).assertHeightIsEqualTo(120.dp)
        compose.runOnIdle { assertEquals(3f, preferences.read().size(CardArea.HAND)) }
    }

    // TBL-139, WEB-022 (owner 2026-11-16): the two full picture switches, kept for each layout: off in the phone layout, the
    // hand's on and the battlefield's off in the desktop layout; turning one changes only that layout's
    @Test fun fullPictureSwitchesAreKeptForEachLayout() {
        context.getSharedPreferences("card-sizes-desktop", Context.MODE_PRIVATE).edit().clear().commit()
        val phone = CardAppearancePreferences(AppPlatform.current)
        val desktop = CardAppearancePreferences(AppPlatform.current, AppLayout.Desktop)
        assertEquals(false to false, phone.read().let { it.handFullPicture to it.battlefieldFullPicture })
        assertEquals(true to false, desktop.read().let { it.handFullPicture to it.battlefieldFullPicture })
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) { CardSettings(phone) } } }
        compose.onNodeWithText("Hand cards: full picture").assertExists()
        compose.onNodeWithTag(FULL_PICTURE_HAND_TAG).performScrollTo().assertIsOff().performClick()
        compose.onNodeWithTag(FULL_PICTURE_BATTLEFIELD_TAG).performScrollTo().assertIsOff().performClick()
        compose.onNodeWithTag(FULL_PICTURE_HAND_TAG).assertIsOn()
        compose.onNodeWithTag(FULL_PICTURE_BATTLEFIELD_TAG).assertIsOn()
        compose.runOnIdle {
            assertEquals(true to true, phone.read().let { it.handFullPicture to it.battlefieldFullPicture })
            assertEquals(true to false, desktop.read().let { it.handFullPicture to it.battlefieldFullPicture }, "the desktop layout's own")
        }
        compose.onNodeWithTag(FULL_PICTURE_HAND_TAG).performClick()
        compose.runOnIdle { assertFalse(phone.read().handFullPicture) }
        context.getSharedPreferences("card-sizes-desktop", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun screenshots(landscape: Boolean) {
        val f = TableStatesFixture
        val play = f.combat()
        val preferences = CardAppearancePreferences(AppPlatform.current)
        val toolkit = TableToolkitController(f.a, { engineTableBoard(play, f.a) }, { true }, { _, _ -> true })
        compose.setContent { MaterialTheme { EngineFixtureTable(play, f.a) { TableSurface(f.game, engineTableBoard(play, f.a), f.a, landscape, Modifier.fillMaxSize(), toolkit = toolkit) } } }
        for (scale in listOf(.5f, 3f)) for (zoom in listOf(.65f, 1f, 2f)) {
            compose.runOnIdle {
                preferences.setSize(scale); preferences.setText(scale)
                toolkit.layoutState.value = TableLayoutState(camera = TableCamera(zoom = zoom))
            }
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(1000)
            compose.waitForIdle()
            val name = "card-sizes"
            val file = File("$SCREENSHOTS/$name-${if (landscape) "landscape" else "portrait"}-$scale-$zoom.png")
            file.parentFile!!.mkdirs()
            val situation = "${if (landscape) "Landscape" else "Portrait"} table in combat; card size ${scale}x, text ${scale}x, zoom ${zoom}x"
            captureShot(file.path, "$situation.")
            if (scale == 3f && zoom == 1f) {
                val home = TablePresentation.scene(f.game, engineTableBoard(play, f.a), f.a, if (landscape) 892f else 412f,
                    if (landscape) 412f else 892f, landscape, appearance = preferences.read())
                val world = home.world!!
                for (field in home.fields) {
                    val card = home.items.first { it.viewport == field.containerId }
                    val camera = world.clamp(TableCamera(
                        x = card.bounds.center.x - world.viewport.center.x,
                        y = card.bounds.center.y - world.viewport.center.y))
                    compose.runOnIdle { toolkit.layoutState.value = TableLayoutState(camera = camera) }
                    compose.waitForIdle()
                    captureShot("${file.path.removeSuffix(".png")}-${if (field.owner == f.a) "own" else "opponent"}-panned.png",
                        "$situation, panned to ${if (field.owner == f.a) "your" else "the opponent's"} battlefield.")
                }
            }
        }
    }

    @Test fun portraitSizeExtremes() { screenshots(false) }
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi")
    fun landscapeSizeExtremes() { screenshots(true) }

    // TBL-238, ZON-138 (owner 2026-11-24, 2026-11-25): in landscape with hand cards taller than the bar the battlefield is seen under
    // them, your hand's fan drawn over it without a hand row behind it; the rows may be panned there
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi")
    fun landscapeBattlefieldUnderLargeHandCards() {
        // Alice's side with six Forests (two of them tapped) besides her creatures, so her land row reaches under her hand
        val initial = LiveTableFixture.play()
        val forests = initial.board.cards.filter { it.containerId == "a/LIBRARY" && it.faceId == "land" }.take(4).map { it.id }
        val play = initial.copy(board = initial.board.copy(
            cards = initial.board.cards.map { c -> if (c.id !in forests) c else c.copy(containerId = "a/BATTLEFIELD", covered = false,
                tapped = c.id == forests.first(), placement = PlayPlacement(BoardPosition(0, 0), c.id)) },
            containers = initial.board.containers.map { when (it.id) {
                "a/LIBRARY" -> it.copy(cards = it.cards - forests.toSet())
                "a/BATTLEFIELD" -> it.copy(cards = it.cards + forests)
                else -> it
            } }))
        val preferences = CardAppearancePreferences(AppPlatform.current)
        preferences.customizeAreas(true)
        preferences.setSize(2.5f, CardArea.HAND)
        try {
            val board = engineTableBoard(play, "a")
            val toolkit = TableToolkitController("a", { board }, { true }, { _, _ -> true })
            // At 2x, panned right and up until her Forests lie in her hand's cards' height, their bottoms just above the bar
            val bar = 412f - TableControlsHeight
            val zoom = 2f
            val home = TablePresentation.scene(LiveTableFixture.game, board, "a", 892f, 412f, true, TableLayoutState(TableCamera(zoom = zoom)),
                appearance = preferences.read())
            val landIds = play.board.cards.filter { it.containerId == "a/BATTLEFIELD" && it.faceId == "land" }.map { it.id }.toSet()
            val lands = home.items.filter { it.battlefieldCard && it.cardId in landIds }
            assertTrue(lands.size >= 2)
            val dx = 140f - lands.minOf { it.bounds.x }
            val dy = (bar - 6f) - lands.maxOf { it.bounds.bottom }
            val camera = TableCamera(-dx / zoom, -dy / zoom, zoom)
            assertEquals(camera, home.world!!.clamp(camera), "within the pan limits")
            toolkit.layoutState.value = TableLayoutState(camera = camera)
            compose.setContent { MaterialTheme { EngineFixtureTable(play, "a") {
                TableSurface(LiveTableFixture.game, board, "a", true, Modifier.fillMaxSize(), connected = true,
                    onIntent = toolkit::handle, toolkit = toolkit, runtime = TableRuntimeHooks(navigation = {}, chat = {}, history = {}))
            } } }
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(1000)
            compose.waitForIdle()
            val scene = TablePresentation.scene(LiveTableFixture.game, board, "a", 892f, 412f, true, TableLayoutState(camera),
                appearance = preferences.read())
            val hand = scene.items.single { it.kind == TableItemKind.HAND && it.owner == "a" }
            val handCards = scene.items.filter { it.kind == TableItemKind.CARD && it.viewport == hand.viewport }
            assertTrue(handCards.all { it.bounds.y < bar - 20f }, "the 2.5x hand cards stand above the bar")
            val under = scene.items.filter { it.battlefieldCard && it.bounds.intersection(hand.bounds) != null }
            assertTrue(under.isNotEmpty(), "battlefield cards lie under the hand")
            assertTrue(under.all { card -> card.visible.none { part -> handCards.any { part.intersection(it.bounds)?.let { i -> i.width > .01f && i.height > .01f } == true } } })
            captureShot("$SCREENSHOTS/battlefield/under-large-hand-landscape.png",
                "Landscape table with hand cards at 2.5x, zoomed to 2x and panned until Alice's Forests lie at her hand cards' height: " +
                    "the battlefield shows beside and under her hand's fan, whose cards stand over it, turned a little and partly below the " +
                    "screen, with no white hand row behind them.")
        } finally { preferences.setSize(1f, CardArea.HAND); preferences.customizeAreas(false) }
    }


}
