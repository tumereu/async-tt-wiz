package org.asyncttwiz.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import arcanum.engine.Engine
import arcanum.engine.input.Input
import arcanum.engine.setup.GameResult
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import org.asyncttwiz.protocol.game.GameDevice
import org.asyncttwiz.protocol.game.PlaySettings
import org.asyncttwiz.protocol.game.ResponseMode
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The history of a game played on engine state as the app shows it (R4b's replacements of the old model's history and
 * playback tests): Alice's and Bob's app games on the simulated server ([EngineAppSim]), coming and going.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class EngineHistoryShownTest {
    @get:Rule val compose = createComposeRule()
    private val f = EngineGameFixture()
    private val a = PlayerId(f.a)
    private val b = PlayerId(f.b)
    private val names = mapOf(f.a to "Alice", f.b to "Bob")

    @After fun forget() = AutomatumBoards.reset()

    /** [p]'s device on its own while the other player is away: what the server sent played back, its own steps sent. */
    private fun alone(sim: EngineAppSim, game: EngineGame) {
        sim.deliver(game.me)
        game.playToStop()
        (game.device.status as? GameDevice.Status.Stopped)?.let { game.act(it.assumed) }
        game.tick(sim.now)
        sim.flush(game.me)
        sim.now += 1_000
    }

    /** Both keep (all delivered, Bob sees the opening end); then Bob goes away and Alice plays on to her first main phase. */
    private fun bothKeepThenBobAway(sim: EngineAppSim): Pair<EngineGame, EngineGame> {
        // HIS-096: Alice plays on while Bob is away
        sim.settings[f.a] = PLAYS_ON
        val alice = sim.load(f.a)
        val bob = sim.load(f.b)
        assertIs<GameDevice.Acted.Sent>(bob.keep(emptyList()))
        sim.flush(f.b)
        sim.deliver(f.a)
        alice.playToStop()
        assertIs<GameDevice.Acted.Sent>(alice.keep(emptyList()))
        sim.flush(f.a)
        // Bob sees the opening end, then goes away
        sim.deliver(f.b); bob.playToStop(); bob.tick(sim.now); sim.flush(f.b)
        repeat(5) { alone(sim, alice) }
        assertEquals(GameDevice.Status.YourMove, alice.device.status)
        return alice to bob
    }

    private fun playLand(sim: EngineAppSim, game: EngineGame): ObjectId {
        val land = assertNotNull(Engine.options(game.device.model.engine, PlayerId(game.me)).filterIsInstance<Input.PlayLand>().firstOrNull())
        assertIs<GameDevice.Acted.Sent>(game.send(listOf(land)))
        sim.flush(game.me)
        return land.card
    }

    // HIS-032: a new device opens the game at the player's local head kept on the server
    @Test fun aNewDeviceOpensAtTheLocalHeadKeptOnTheServer() {
        val sim = EngineAppSim(f)
        playLand(sim, bothKeepThenBobAway(sim).first)
        val bobsHead = sim.heads.getValue(f.b)
        assertNotEquals(sim.head, bobsHead, "Alice played on past Bob's local head")
        sim.games.remove(f.b)
        val device = sim.load(f.b)
        assertEquals(bobsHead, device.device.session.localHead, "opened at his local head")
        assertTrue(device.device.session.behind, "the rest waits for him")
    }

    // HIS-006, HIS-035, HIS-094: coming back (the game loaded again) with Alice's events past his local head, Bob's playback
    // starts paused; a return where only the player's own events arrived stays live
    @Test fun comingBackWithTheOtherPlayersEventsStartsPaused() {
        val sim = EngineAppSim(f)
        val (alice, _) = bothKeepThenBobAway(sim)
        playLand(sim, alice)
        val bob = sim.load(f.b)
        assertEquals(GameDevice.Status.CatchUp, bob.device.status, "paused until he taps Play")
        val head = bob.device.session.localHead
        assertNull(bob.step())
        assertEquals(head, bob.device.session.localHead, "nothing rushes through")
        // Alice's own events only: her return stays live
        val again = sim.load(f.a)
        assertEquals(GameDevice.Status.YourMove, again.device.status)
        assertFalse(again.device.session.behind)
    }

    // HIS-006: on a live table the other player's actions show without timeline mode, which starts only when entered;
    // the player's own actions show live at once
    @Test fun onALiveTableTheOtherPlayersActionsKeepItLive() {
        val sim = EngineAppSim(f)
        val alice = sim.load(f.a)
        val bob = sim.load(f.b)
        val routes = EngineTableRoutes(f.b, { bob }, {}, { bob.play() })
        bob.keep(emptyList()); sim.flush(f.b)
        sim.deliver(f.a); alice.playToStop(); alice.keep(emptyList()); sim.flush(f.a)
        repeat(6) { for (g in listOf(alice, bob)) { sim.deliver(g.me); g.playToStop(); g.tick(sim.now); sim.flush(g.me) }; sim.now += 500 }
        assertEquals(GameDevice.Status.YourMove, alice.device.status)
        val land = playLand(sim, alice)
        val card = (land as ObjectId.Card).card
        assertTrue(alice.device.model.engine.battlefield.any { (it as? ObjectId.Card)?.card == card }, "Alice's own land shows at once")
        assertEquals(GameDevice.Status.YourMove, alice.device.status)
        sim.deliver(f.b)
        bob.playToStop()
        assertTrue(bob.device.model.engine.battlefield.any { (it as? ObjectId.Card)?.card == card }, "Bob sees it")
        assertNull(routes.review, "no timeline mode")
        assertFalse(bob.device.session.behind)
    }

    // HIS-093, HIS-095: playback stops at Bob's decision point (in Control here) and waits there with no
    // time limit
    @Test fun playbackStopsAtADecisionPointAndWaits() {
        val sim = EngineAppSim(f)
        val (alice, bob) = bothKeepThenBobAway(sim)
        bob.settings(PlaySettings(mode = org.asyncttwiz.protocol.game.ResponseMode.FullControl)); sim.flush(f.b)
        playLand(sim, alice)
        // Alice casts a Storm Spark (at the first target the engine lists): Bob may respond
        val s = alice.device.model.engine
        val spark = assertNotNull(s.player(a).hand.firstOrNull { s.obj(it)?.definition?.faces?.first()?.name == "Storm Spark" })
        assertIs<GameDevice.Acted.Sent>(alice.send(EngineCombatScenario(f).cast(s, a, Input.Cast(a, spark))))
        sim.flush(f.a)
        repeat(3) { alone(sim, alice) }
        val again = sim.load(f.b)
        assertEquals(GameDevice.Status.CatchUp, again.device.status)
        again.startPlayback()
        var guard = 0
        while (guard++ < 100) {
            while (again.device.status is GameDevice.Status.Playing && guard++ < 100) again.step()
            val at = again.device.status as? GameDevice.Status.Stopped ?: break
            if (again.device.model.engine.stack.isNotEmpty()) break
            again.act(at.assumed)
        }
        val stop = assertIs<GameDevice.Status.Stopped>(again.device.status, "playback stops at the Spark for Bob")
        assertEquals(Input.PassPriority(b), stop.assumed)
        assertTrue(again.device.model.engine.stack.isNotEmpty())
        // No time limit: an hour later it still waits
        again.tick(sim.now + 3_600_000)
        again.playToStop()
        assertIs<GameDevice.Status.Stopped>(again.device.status)
    }

    // HIS-015, HIS-013: in timeline mode, going to the last event by hand stays in timeline mode; Auto reaching the end
    // returns to play mode
    @Test fun onlyAutoReachingTheEndLeavesTimelineMode() {
        val events = LiveTableFixture.timeline()
        val routes = EngineTableRoutes(f.a, { null }, {}, { null }).apply { review = events[events.lastIndex - 1].id }
        compose.mainClock.autoAdvance = false
        compose.setContent { MaterialTheme { EngineReviewPanel(routes, events, f.a, false, 500L) } }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Next").performClick()
        compose.mainClock.advanceTimeBy(2_000)
        compose.runOnIdle { assertEquals(events.last().id, routes.review, "at the last event, still in timeline mode") }
        compose.onNodeWithContentDescription("Next").performClick()
        compose.mainClock.advanceTimeBy(2_000)
        compose.runOnIdle { assertEquals(events.last().id, routes.review, "Next again: still there") }
        compose.onNodeWithContentDescription(EXIT_TIMELINE).assertExists()
        compose.onNodeWithContentDescription("Previous").performClick()
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithContentDescription("Auto advance").performClick()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(100)
        compose.mainClock.advanceTimeBy(600)
        compose.runOnIdle { assertEquals(events.last().id, routes.review) }
        compose.mainClock.advanceTimeBy(600)
        compose.runOnIdle { assertNull(routes.review, "Auto reaching the end returned to play mode") }
    }

    // HIS-062: texts never name what the viewer couldn't see: Alice's cast read before its card is shown is "a card" to
    // Bob, her own text names it; once on the Stack it is named to both
    @Test fun textsNeverNameHiddenCards() {
        val sim = EngineAppSim(f)
        val (alice, _) = bothKeepThenBobAway(sim)
        playLand(sim, alice)
        val s = alice.device.model.engine.deepCopy()
        val spark = assertNotNull(s.player(a).hand.firstOrNull { s.obj(it)?.definition?.faces?.first()?.name == "Storm Spark" })
        assertIs<GameDevice.Acted.Sent>(alice.send(EngineCombatScenario(f).cast(s, a, Input.Cast(a, spark))))
        sim.flush(f.a)
        val cast = sim.line().last { e -> e.engine?.steps?.any { it.input is Input.Cast } == true }
        assertEquals("Alice cast a card", EngineEventText.of(cast, s, f.b, names), "Bob couldn't see it in her hand")
        assertEquals("You cast Storm Spark", EngineEventText.of(cast, s, f.a, names))
        assertEquals("Alice cast Storm Spark", EngineEventText.of(cast, alice.device.model.engine, f.b, names))
    }

    // TRN-051: the suggestion panel's suggestion appears in playback once its event is reached; it can be answered only
    // at the live end
    @Test fun aSuggestionShowsOnceReachedAndIsAnsweredAtTheLiveEnd() {
        val sim = EngineAppSim(f)
        val alice = sim.load(f.a)
        val bob = sim.load(f.b)
        bob.keep(emptyList()); sim.flush(f.b)
        sim.deliver(f.a); alice.playToStop(); alice.keep(emptyList()); sim.flush(f.a)
        repeat(6) { for (g in listOf(alice, bob)) { sim.deliver(g.me); g.playToStop(); g.tick(sim.now); sim.flush(g.me) }; sim.now += 500 }
        // Alice is away; Bob suggests a draw
        sim.deliver(f.b); bob.playToStop()
        assertIs<GameDevice.Acted.Sent>(bob.suggest(GameResult.Draw))
        sim.flush(f.b)
        val again = sim.load(f.a)
        assertEquals(GameDevice.Status.CatchUp, again.device.status)
        assertNull(again.play().board.resultSuggestion, "not shown before its event is reached")
        assertIs<GameDevice.Acted.Refused>(again.answerSuggestion(false))
        again.startPlayback()
        var guard = 0
        while (again.device.status is GameDevice.Status.Playing && guard++ < 100) again.step()
        assertNotNull(again.play().board.resultSuggestion, "shown once reached")
        assertFalse(again.device.session.behind, "the live end")
        assertIs<GameDevice.Acted.Sent>(again.answerSuggestion(false))
        sim.flush(f.a)
        assertNull(sim.state().table.suggestion)
    }
}
