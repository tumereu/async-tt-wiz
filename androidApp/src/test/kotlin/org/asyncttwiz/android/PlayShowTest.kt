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

/**
 * HIS-112 (owner 2026-11-07, MTG Arena as the reference): on Alice's table Bob's plays show large in the middle of the board
 * for the "Show the other player's plays" time, her view held at their event meanwhile, then they fly to the Stack; a tap
 * ends one at once, and at 0 none shows. Games played through the simulated server; time on the test clock.
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

    // HIS-112: a tap on the shown card ends it at once, with no time passing
    @Test fun aTapEndsTheShowAtOnce() {
        val (kit, _) = bobsSparkShown()
        compose.mainClock.autoAdvance = false
        render(kit)
        compose.onNodeWithTag(PLAY_SHOW_TAG).performClick()
        compose.runOnIdle { assertNull(kit.alice.shown?.show, "ended by the tap") }
        assertEquals(kit.me, kit.state().priority.holder, "she holds priority over the Spark, live")
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
        HistoryPlaybackPreferences(context, kit.f.a).showPlaysMillis = 0
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
}
