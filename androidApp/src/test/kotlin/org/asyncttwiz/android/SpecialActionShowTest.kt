package org.asyncttwiz.android

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import org.asyncttwiz.android.EngineGameFixture.Choices
import org.asyncttwiz.protocol.PlayZone
import org.asyncttwiz.protocol.game.GameDevice
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * HIS-112 (owner, 2026-11-20: a Room unlocked in prod showed nothing, as it had no "when you unlock" trigger): the other player's
 * special action, which doesn't use the Stack, shows large in the middle of Alice's table as an ability does, then flies into its
 * own card instead of to the Stack, and the card flashes once it lands. Played through the simulated server; time on the test clock.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class SpecialActionShowTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @After fun forget() {
        AutomatumBoards.reset()
        PlayShowProbe.flight = null
        context.getSharedPreferences("history-playback", Context.MODE_PRIVATE).edit().clear().commit()
    }

    // HIS-112, HIS-237, HIS-031, TBL-232 (owner, 2026-11-20): Bob plots an Ember Plotter: Alice's table shows his plot large in the middle, its card's
    // ability token named "Special action" with "Bob plotted Ember Plotter" under it, for the "Show plays and combat" time; then it flies
    // into the plotted card's place (never the Stack), and once it lands the card flashes
    @Test fun theOtherPlayersPlotShowsThenFliesIntoItsCard() {
        val kit = EngineTableKit(EngineCombatScenario(EngineGameFixture(bobCards = listOf("Mountain", Choices.PLOTTER))))
        kit.opening()
        kit.alice.showPlays(true)
        kit.scenario.confirmStops[kit.f.a] = false
        kit.bobPlots(Choices.PLOTTER)
        assertTrue(kit.toolkit.automatumStep(TableActionIntent.End(kit.scope)), "End turn")
        kit.playOn("Bob's plot shown on Alice's table", rounds = 40) { kit.alice.shown?.show != null }
        val show = assertNotNull(kit.alice.shown?.show)
        assertEquals(GameDevice.PlayKind.Special, show.kind)
        assertTrue(kit.state().stack.isEmpty(), "nothing on the Stack: a special action doesn't use it")
        val flights = mutableListOf<Triple<GameDevice.PlayShow, TableBounds?, TableScene>>()
        PlayShowProbe.flight = { me, s, target, scene -> if (me == kit.f.a) flights += Triple(s, target, scene) }
        val landed = mutableListOf<GameDevice.PlayShow>()
        kit.routes.stackAnimation.specialLanded = { landed += it }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme {
                NoRipples {
                    CompositionLocalProvider(LocalEngineRoutes provides kit.routes) {
                        Box(Modifier.fillMaxSize()) { StackAnimatedTable(kit, Modifier.fillMaxSize()) }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(PLAY_SHOW_FLIGHT_MILLIS + 100L)
        compose.waitForIdle()
        compose.onNodeWithTag(PLAY_SHOW_TAG).assertIsDisplayed().assert(hasContentDescription("Bob plotted Ember Plotter"))
        captureShot("$SCREENSHOTS/engine/play-show-special-action.png",
            "Bob plotted his Ember Plotter: Alice's table shows it large in the middle as an ability, its card's token with \"Bob plotted Ember Plotter\" under it, before it flies into the plotted card instead of to the Stack.")
        compose.mainClock.advanceTimeBy(1_200 + PLAY_SHOW_FLIGHT_MILLIS + 200L)
        compose.waitForIdle()
        kit.settle()
        compose.runOnIdle { assertNull(kit.alice.shown?.show, "done after its time and its flight") }
        val (flown, target, scene) = flights.single()
        assertEquals(show, flown)
        // Into its own card: where the plotted card is on her table (Bob's exile), not the Stack
        val exile = scene.items.first { it.kind == TableItemKind.PILE && it.zone == PlayZone.EXILE && it.owner == kit.f.b && flown.card?.value in it.cards }
        // (the pile lies above the battlefields: the flight ends at their edge in its direction, as a fly-in starts there, HIS-133)
        assertEquals(playShowStart(assertNotNull(scene.boardArea), exile.bounds), assertNotNull(target, "it flies somewhere"))
        assertTrue(target != playShowTarget(scene), "not the Stack")
        assertEquals(listOf(show), landed, "it landed in its card: the card flashes (the table's hook)")
    }
}
