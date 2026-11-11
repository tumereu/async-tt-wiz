package org.asyncttwiz.android

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import arcanum.engine.state.StackItemKind
import arcanum.engine.state.TargetRef
import org.asyncttwiz.android.EngineGameFixture.Choices
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
import org.asyncttwiz.appui.AppPlatform

/**
 * HIS-112 (owner 2026-11-07, MTG Arena as the reference): on Alice's table Bob's plays show large in the middle of the board
 * for the "Show plays and combat" time, her view held at their event meanwhile, then they fly to the Stack; a double tap
 * ends one at once (a single tap does nothing, a held finger pauses it, owner 2026-11-08), and at 0 none shows. Games played through the simulated server; time on the test clock.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class PlayShowTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @After fun forget() {
        AutomatumBoards.reset()
        context.getSharedPreferences("history-playback", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun render(kit: EngineTableKit) {
        compose.setContent {
            MaterialTheme {
                NoRipples {
                    CompositionLocalProvider(LocalEngineRoutes provides kit.routes) {
                        Box(Modifier.fillMaxSize()) {
                            val play = kit.alice.play()
                            TableSurface(kit.f.game, play, kit.f.a, false, Modifier.fillMaxSize(), connected = true,
                                onIntent = kit.toolkit::handle, toolkit = kit.toolkit,
                                runtime = TableRuntimeHooks(navigation = {}, chat = {}, undo = { kit.routes.undo() },
                                    controlsConnected = true, waitingLine = kit.routes.waitingLine(play)))
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }

    /** From Alice's next Main 1 with a ready Cub to Bob's Storm Spark at it shown on her table. */
    private fun bobsSparkShown(): Pair<EngineTableKit, GameDevice.PlayShow> {
        val kit = EngineTableKit()
        val cub = kit.toReadyCub()
        kit.alice.showPlays(true)
        kit.scenario.confirmStops[kit.f.a] = false
        assertTrue(kit.toolkit.automatumStep(TableActionIntent.End(kit.scope)), "End turn")
        kit.bobCastsSpark { s -> TargetRef.Object(s.battlefield.first { (it as arcanum.engine.state.ObjectId.Card).card.value == cub }) }
        kit.playOn("Bob's Spark shown on Alice's table", rounds = 40) { kit.alice.shown?.show != null }
        return kit to assertNotNull(kit.alice.shown?.show)
    }

    // HIS-112, HIS-021: Bob's Storm Spark at Alice's Cub shows as its card in the middle of her board with "Bob cast Storm
    // Spark · deals 3 damage to Ember Cub" under it, her table held at the cast (the Spark on the Stack, the Cub alive)
    // for the 1.2 s default on the test clock; then it moves to the Stack and the table plays on
    @Test fun theOtherPlayersSpellShowsForItsTimeThenTheTablePlaysOn() {
        val (kit, show) = bobsSparkShown()
        assertEquals(GameDevice.PlayKind.Cast, show.kind)
        val held = kit.alice.device.session.localHead
        assertEquals(show.event, held, "her table at the cast")
        assertEquals(StackItemKind.Spell, kit.state().stackItems[kit.state().stack.last()]?.kind, "the Spark on the Stack")
        compose.mainClock.autoAdvance = false
        render(kit)
        val line = "Bob cast Storm Spark · deals 3 damage to Ember Cub"
        compose.onNodeWithTag(PLAY_SHOW_TAG).assertIsDisplayed().assert(hasContentDescription(line))
        compose.onNodeWithTag("stack-line").assertDoesNotExist()
        captureShot("$SCREENSHOTS/engine/play-show-spell.png",
            "Bob cast Storm Spark at Alice's Ember Cub: her table shows the Spark large in the middle of the board, with \"Bob cast Storm Spark · deals 3 damage to Ember Cub\" under it, before it moves to the Stack; her table waits at the cast meanwhile.")
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        kit.settle()
        assertNotNull(kit.alice.shown?.show, "still shown after 1 s")
        assertEquals(held, kit.alice.device.session.localHead, "her table still at the cast")
        assertTrue(kit.field("Ember Cub").isNotEmpty(), "the Cub still alive on her table")
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        compose.runOnIdle { assertNull(kit.alice.shown?.show, "done after its time and its move to the Stack") }
        // She holds priority over it (STK-027) and lets it resolve
        assertEquals(kit.me, kit.state().priority.holder)
        kit.alice.pass()
        kit.playOn("the Spark resolving on Alice's table") { kit.state().stack.isEmpty() }
        assertTrue(kit.field("Ember Cub").isEmpty(), "the Cub is gone")
    }

    // HIS-112 (owner, 2026-11-08): a single tap on the shown card does nothing; a double tap ends it at once
    @Test fun aDoubleTapEndsTheShowAtOnceASingleTapDoesNothing() {
        val (kit, _) = bobsSparkShown()
        compose.mainClock.autoAdvance = false
        render(kit)
        compose.onNodeWithTag(PLAY_SHOW_TAG).performClick()
        compose.mainClock.advanceTimeBy(DOUBLE_TAP_MILLIS + 50)
        kit.settle()
        compose.runOnIdle { assertNotNull(kit.alice.shown?.show, "a single tap does nothing") }
        compose.onNodeWithTag(PLAY_SHOW_TAG).performTouchInput { doubleClick() }
        kit.settle()
        compose.runOnIdle { assertNull(kit.alice.shown?.show, "ended by the double tap") }
        assertEquals(kit.me, kit.state().priority.holder, "she holds priority over the Spark, live")
    }

    // HIS-112, HIS-021, TBL-104 (owner, 2026-11-08): a finger held on the shown card inspects it and pauses its time, the pause
    // icon in the corner; lifted, the show goes on with the time left: 0.6 s and the hold's own time had gone of its 1.2 s
    @Test fun aHeldFingerPausesTheShowWithTheTimeLeftAndInspectsIt() {
        val (kit, _) = bobsSparkShown()
        compose.mainClock.autoAdvance = false
        render(kit)
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithTag(PLAY_SHOW_TAG).performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(5_000)
        compose.waitForIdle()
        kit.settle()
        compose.runOnIdle { assertNotNull(kit.alice.shown?.show, "still shown while held") }
        compose.onNodeWithContentDescription("Card inspection content").assertExists()
        compose.onNodeWithTag(VIEW_PAUSED_TAG).assertExists()
        compose.onNodeWithTag(PLAY_SHOW_TAG).performTouchInput { up() }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Card inspection content").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(100)
        kit.settle()
        compose.runOnIdle { assertNotNull(kit.alice.shown?.show, "the time left isn't over yet") }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        kit.settle()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        kit.settle()
        compose.runOnIdle { assertNull(kit.alice.shown?.show, "over with the time left, not a whole new time") }
    }

    // HIS-021, HIS-112 (owner, 2026-11-08): a finger held on the table beside the shown play pauses it, the icon in the corner
    @Test fun aHeldFingerOnTheTableShowsThePauseIcon() {
        val (kit, _) = bobsSparkShown()
        compose.mainClock.autoAdvance = false
        render(kit)
        val table = compose.onNodeWithContentDescription("Shared table")
        table.performTouchInput { down(androidx.compose.ui.geometry.Offset(width * .08f, height * .35f)) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        compose.onNodeWithTag(VIEW_PAUSED_TAG).assertExists()
        captureShot("$SCREENSHOTS/engine/hold-pause.png",
            "Bob's Storm Spark shows on Alice's table and she holds a finger on the board beside it: its time is paused until she lifts her finger, and the pause icon sits in the board's top-right corner.")
        table.performTouchInput { up() }
        compose.waitForIdle()
    }

    // HIS-112, STK-046, STK-050: Bob cycles Shattered Reason: Alice's table shows the cycling ability, then the cycling
    // trigger, each as its ability token enlarged with its source and kind named under it
    @Test fun anAbilityAndATriggerShowAsTheirTokens() {
        val kit = EngineTableKit(EngineCombatScenario(EngineGameFixture(bobCards = listOf(Choices.REASON, "Mountain"))))
        kit.opening()
        kit.playLand()
        kit.alice.showPlays(true)
        kit.scenario.confirmStops[kit.f.a] = false
        kit.bobCycles(Choices.REASON)
        assertTrue(kit.toolkit.automatumStep(TableActionIntent.End(kit.scope)), "End turn")
        kit.playOn("Bob's cycling shown on Alice's table", rounds = 40) { kit.alice.shown?.show != null }
        val activation = assertNotNull(kit.alice.shown?.show)
        assertEquals(GameDevice.PlayKind.Activated, activation.kind)
        val trigger = kit.alice.device.openShows.getOrNull(1)
        assertEquals(GameDevice.PlayKind.Triggered, trigger?.kind, "the cycling trigger next: ${kit.alice.device.openShows}")
        kit.alice.dismissShow(activation)
        assertEquals(trigger, kit.alice.shown?.show)
        compose.mainClock.autoAdvance = false
        render(kit)
        compose.onNodeWithTag(PLAY_SHOW_TAG).assertIsDisplayed()
            .assert(hasContentDescription("${Choices.REASON}'s cycling trigger · each opponent mills 4 cards"))
        compose.onNode(hasContentDescription("Cycling trigger") and hasAnyAncestor(hasTestTag(PLAY_SHOW_TAG)), useUnmergedTree = true).assertExists()
        captureShot("$SCREENSHOTS/engine/play-show-trigger.png",
            "Bob cycled Shattered Reason: after its cycling ability, Alice's table shows his cycling trigger as its ability token enlarged in the middle of the board (the card's art, the \"T\" chip), with \"Shattered Reason's cycling trigger · each opponent mills 4 cards\" under it.")
    }

    // HIS-112, LOB-082: at 0 s ("Off") nothing shows: Bob's Spark goes to the Stack and resolves on Alice's table as before
    @Test fun offShowsNothing() {
        val kit = EngineTableKit()
        val cub = kit.toReadyCub()
        kit.alice.showPlays(true)
        HistoryPlaybackPreferences(AppPlatform.current, kit.f.a).showPlaysMillis = 0
        render(kit)
        compose.runOnIdle { }
        kit.scenario.confirmStops[kit.f.a] = false
        assertTrue(kit.toolkit.automatumStep(TableActionIntent.End(kit.scope)), "End turn")
        kit.bobCastsSpark { s -> TargetRef.Object(s.battlefield.first { (it as arcanum.engine.state.ObjectId.Card).card.value == cub }) }
        var shown = false
        kit.playOn("Alice holding priority over Bob's Spark", rounds = 40) {
            if (kit.alice.shown?.show != null) shown = true
            kit.state().stack.isNotEmpty() && kit.state().priority.holder == kit.me
        }
        assertTrue(!shown, "nothing shown at 0 s")
        assertNull(kit.alice.shown?.show)
    }

    // HIS-112 (owner playtest 2026-11-07): the flight to the Stack takes about 0.25 s at Card move speed 1×, scaled by it;
    // the card is whole over its first half and fades out over the second
    @Test fun theFlightIsAQuarterSecondScaledByCardMoveSpeedAndFadesInItsSecondHalf() {
        assertEquals(250, playShowFlightMillis(1f))
        assertEquals(125, playShowFlightMillis(2f))
        assertEquals(1000, playShowFlightMillis(.25f))
        assertEquals(1f, playShowFlightAlpha(0f))
        assertEquals(1f, playShowFlightAlpha(.5f))
        assertEquals(.5f, playShowFlightAlpha(.75f), .001f)
        assertEquals(0f, playShowFlightAlpha(1f))
    }

    // HIS-112 (owner playtest 2026-11-07): after its time the line goes as the flight starts, the card flies to the Stack's
    // top card and is gone from the screen as it lands, without waiting for the device (here its dismissal never comes)
    @Test fun theShowLeavesTheScreenWhenItLandsWithoutWaitingForTheDevice() {
        val (kit, show) = bobsSparkShown()
        val play = kit.alice.play()
        val scene = TablePresentation.scene(kit.f.game, play, kit.f.a, 412f, 892f, false)
        val top = assertNotNull(playShowTarget(scene), "the Stack's top card")
        assertTrue(scene.items.any { it.zone == org.asyncttwiz.protocol.PlayZone.STACK && it.kind == TableItemKind.CARD && it.bounds == top },
            "the target is the top card's place: $top")
        var done = 0
        compose.mainClock.autoAdvance = false
        compose.setContent { MaterialTheme { NoRipples { Box(Modifier.fillMaxSize()) {
            PlayShowOverlay(play, scene, kit.f.a, show, emptyMap(), 1_200) { done++ }
        } } } }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        val line = "cast Storm Spark"
        compose.onNodeWithText(line, substring = true, useUnmergedTree = true).assertExists()
        compose.mainClock.advanceTimeBy(1_250)
        compose.waitForIdle()
        compose.onNodeWithTag(PLAY_SHOW_TAG).assertExists()
        compose.onNodeWithText(line, substring = true, useUnmergedTree = true).assertDoesNotExist()
        compose.mainClock.advanceTimeBy(PLAY_SHOW_FLIGHT_MILLIS.toLong() + 100)
        compose.waitForIdle()
        compose.onNodeWithTag(PLAY_SHOW_TAG).assertDoesNotExist()
        assertEquals(1, done)
    }
}
