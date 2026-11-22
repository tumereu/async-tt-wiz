package org.asyncttwiz.android

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
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

    /** Bumped when Alice's table should draw her board published since. */
    private var version by mutableIntStateOf(0)

    /** Alice's table of [kit]'s game as [EngineTableScreen] draws it: the board's steps, the special action's card held as it was
     * until its play lands (HIS-238), the flashes on the test clock; the clock stopped. */
    private fun renderWithSteps(kit: EngineTableKit) {
        val changes = TableChanges { compose.mainClock.currentTime }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme {
                NoRipples {
                    CompositionLocalProvider(LocalEngineRoutes provides kit.routes) {
                        Box(Modifier.fillMaxSize()) { StackAnimatedTable(kit, Modifier.fillMaxSize(), version = version, changes = changes) }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }

    /** A special action's clip length: the lead, its flight in, its time in the middle, its flight into its card and the card's flashes. */
    private val specialClipMillis = CLIP_LEAD_MILLIS + PLAY_SHOW_FLIGHT_MILLIS + HistoryPlaybackPreferences.DEFAULT_SHOW_PLAYS_MILLIS +
        PLAY_SHOW_FLIGHT_MILLIS + 1_600L

    // HIS-112, HIS-237, HIS-238, HIS-031 (owner, 2026-11-21: clips of the special actions, which don't use the Stack): the gallery clip of Bob's
    // plot on Alice's table, from before it: it shows large in the middle, flies into the plotted card (to the battlefields' edge
    // toward his exile, out of sight) instead of to the Stack, and only then does the card leave his hand for his exile
    @Test fun clipOfAPlot() {
        val kit = EngineTableKit(EngineCombatScenario(EngineGameFixture(bobCards = listOf("Mountain", Choices.PLOTTER))))
        kit.opening()
        kit.alice.showPlays(true)
        kit.scenario.confirmStops[kit.f.a] = false
        kit.bobPlots(Choices.PLOTTER)
        // Bob waits in his Main 1 with his Mountain out until the clip plots
        val plan = assertNotNull(kit.bobMain)
        var plot = false
        kit.bobMain = { s -> if ((s.turn.landsPlayed[kit.other] ?: 0) == 0 || plot) plan(s) else true }
        assertTrue(kit.toolkit.automatumStep(TableActionIntent.End(kit.scope)), "End turn")
        kit.playOn("Bob's Mountain on Alice's table", rounds = 40) { kit.field("Mountain", kit.other).isNotEmpty() }
        renderWithSteps(kit)
        compose.captureClip("$SCREENSHOTS/clips/special-plot.webp",
            "Bob, his Mountain out, plots his Ember Plotter: Alice's table shows it large in the middle as its card's token with \"Bob plotted Ember Plotter\" under it, then it flies to the battlefields' top edge toward his exile instead of to the Stack, and only then does the card leave his hand.",
            millis = specialClipMillis) { at ->
            if (at == CLIP_LEAD_MILLIS) {
                plot = true
                kit.playOn("Bob's plot shown on Alice's table", rounds = 40) { kit.alice.shown?.show != null }
                assertEquals(GameDevice.PlayKind.Special, kit.alice.shown?.show?.kind)
                version++
            }
        }
        kit.settle()
        compose.runOnIdle { assertNull(kit.alice.shown?.show, "done after its time and its flight") }
    }

    // HIS-112, HIS-237, HIS-238, HIS-031 (owner, 2026-11-21: clips of the special actions, which don't use the Stack): the gallery clip of Bob
    // turning his face-down creature face up (morph, CR 702.37e, 116.2b) on Alice's table, from before it: it shows large in the
    // middle, flies into the face-down card instead of to the Stack, and only then does the card turn face up, flipping as it does
    @Test fun clipOfTurningAFaceDownCreatureFaceUp() {
        val kit = EngineTableKit(EngineCombatScenario(EngineGameFixture(bobCards = listOf("Mountain", Choices.GEODE, Choices.MASQUER))))
        kit.opening()
        kit.scenario.confirmStops[kit.f.b] = false
        var turnUp = false
        val other = kit.other
        fun faceDown(s: arcanum.engine.state.GameState) = s.battlefield.firstOrNull { s.obj(it)?.let { o -> o.controller == other && o.faceDown } == true }
        kit.bobMain = { s ->
            val hand = s.player(other).hand
            val land = kit.scenario.cards(s, other, hand, "Mountain").firstOrNull()
            val geode = kit.scenario.cards(s, other, hand, Choices.GEODE).firstOrNull()
            val masquer = kit.scenario.cards(s, other, hand, Choices.MASQUER).firstOrNull()
            val down = faceDown(s)
            when {
                (s.turn.landsPlayed[other] ?: 0) == 0 && land != null -> { kit.bob.send(listOf(arcanum.engine.input.Input.PlayLand(other, land))); true }
                geode != null && kit.scenario.cards(s, other, s.battlefield, Choices.GEODE).isEmpty() -> {
                    kit.bob.send(kit.scenario.cast(s, other, arcanum.engine.input.Input.Cast(other, geode))); true
                }
                masquer != null && down == null -> {
                    // Cast face down for {3} (CR 702.37b): its morph's alternative cost
                    val morph = arcanum.engine.Engine.view(s)[masquer]!!.characteristics!!.abilities.first { inst ->
                        (inst.ability as? arcanum.api.Ability.Static)?.mechanics?.any { it is arcanum.api.Mechanic.AlternativeCost && it.faceDown } == true
                    }.origin
                    kit.bob.send(kit.scenario.cast(s, other, arcanum.engine.input.Input.Cast(other, masquer,
                        arcanum.engine.input.CastWay(alternative = morph)))); true
                }
                down != null && turnUp -> {
                    // Turning it face up: its morph's special action, as the engine offers it
                    val special = arcanum.engine.Engine.options(s, other, org.asyncttwiz.protocol.game.EnginePreferences.of(s))
                        .filterIsInstance<arcanum.engine.input.Input.Activate>().first { it.source == down }
                    kit.bobMain = null
                    kit.bob.send(kit.scenario.cast(s, other, special)); true
                }
                else -> true
            }
        }
        assertTrue(kit.toolkit.automatumStep(TableActionIntent.End(kit.scope)), "End turn")
        kit.playOn("Bob's face-down creature on Alice's table", rounds = 60) { faceDown(kit.state()) != null && kit.state().stack.isEmpty() }
        kit.alice.showPlays(true)
        kit.scenario.confirmStops[kit.f.a] = false
        renderWithSteps(kit)
        compose.captureClip("$SCREENSHOTS/clips/special-face-up.webp",
            "Bob turns his face-down creature face up: Alice's table shows it large in the middle as a special action with its line, then it flies into the face-down card on his battlefield instead of to the Stack, and only then does the card flip face up as his Ember Masquer.",
            millis = specialClipMillis) { at ->
            if (at == CLIP_LEAD_MILLIS) {
                turnUp = true
                kit.playOn("Bob's turning face up shown on Alice's table", rounds = 40) { kit.alice.shown?.show != null }
                assertEquals(GameDevice.PlayKind.Special, kit.alice.shown?.show?.kind)
                version++
            }
        }
        kit.settle()
        compose.runOnIdle { assertNull(kit.alice.shown?.show, "done after its time and its flight") }
        assertTrue(kit.field(Choices.MASQUER, kit.other).isNotEmpty() && faceDown(kit.state()) == null, "the Masquer face up")
    }
}
