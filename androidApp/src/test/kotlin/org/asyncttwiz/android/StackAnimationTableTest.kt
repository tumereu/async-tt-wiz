package org.asyncttwiz.android

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.test.core.app.ApplicationProvider
import arcanum.engine.state.TargetRef
import org.asyncttwiz.protocol.CombatStackRules
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
 * Stack animation (owner spec 2026-11-14; HIS-112, HIS-133, HIS-136, HIS-137, STK-058, STK-127, HIS-230, TBL-125): Bob casts Storm Spark at Alice's Ember Cub. On her
 * table it flies in from his hand, holds in the middle and flies to the Stack, and only as it lands does its Stack entry
 * show, in place (owner playtest 2026-11-14: a play flew to the middle and at the same time straight to the Stack); then,
 * the top entry with a target, its arrow grows to the Cub and stays while the game waits. Time on the test clock.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class StackAnimationTableTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @After fun forget() {
        AutomatumBoards.reset()
        StackAnimationProbe.targeting = null
        StackAnimationProbe.resolving = null
        context.getSharedPreferences("history-playback", Context.MODE_PRIVATE).edit().clear().commit()
    }

    /** From Alice's next Main 1 with a ready Cub to Bob's Storm Spark at it held on her table, its play open. */
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

    /** Bumped when Alice's table should draw her board published since. */
    private var version by androidx.compose.runtime.mutableIntStateOf(0)

    private fun render(kit: EngineTableKit) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme { NoRipples {
                CompositionLocalProvider(LocalEngineRoutes provides kit.routes) {
                    Box(Modifier.fillMaxSize()) { StackAnimatedTable(kit, Modifier.fillMaxSize(), version = version) }
                }
            } }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }

    private fun advance(millis: Long) { compose.mainClock.advanceTimeBy(millis); compose.waitForIdle() }

    // HIS-112, HIS-132, HIS-137, STK-058, STK-127, HIS-230 (spec steps 1–4): until the Spark lands its entry isn't on Alice's Stack (the device has it there), and
    // nothing of it moves to the Stack but the play shown; it appears in the flying card's place as it lands, and its arrow
    // to the Cub then grows (0.25 s) and stays drawn while she holds priority over it
    @Test fun theEntryShowsOnlyAsThePlayLandsThenItsArrowGrows() {
        val (kit, show) = bobsSparkShown()
        val entry = "entry:${show.entry}"
        val targeted = ArrayList<Triple<String, Int, Long>>()
        StackAnimationProbe.targeting = { me, e, targets, millis -> if (me == kit.f.a) targeted += Triple(e, targets, millis) }
        assertTrue(entry in CombatStackRules.stack(kit.alice.play().board).cards, "the device has the Spark on the Stack")
        render(kit)
        // Step 1: flying in from Bob's hand; the Stack empty on her table
        compose.onNodeWithTag(PLAY_SHOW_WAITING_TAG).assertExists()
        compose.onNodeWithContentDescription("Stack, 0 entries").assertExists()
        advance(PLAY_SHOW_FLIGHT_MILLIS + 100L)
        // Step 2: held large in the middle with its line, still nothing on the Stack, no arrow
        compose.onNodeWithTag(PLAY_SHOW_TAG).assertIsDisplayed()
        compose.onNodeWithContentDescription("Stack, 0 entries").assertExists()
        compose.onNodeWithTag("stack-target-arrows").assertDoesNotExist()
        captureShot("$SCREENSHOTS/stack-animation/2-hold.png",
            "Bob cast Storm Spark at Alice's Ember Cub: it flew in from his hand and holds large in the middle of her board with \"Bob cast Storm Spark · deals 3 damage to Ember Cub\" under it, while her Stack at the right edge stays empty: its entry shows only once it lands there.")
        // Step 3: its flight to the Stack (the line gone), the Stack still empty
        advance(HistoryPlaybackPreferences.DEFAULT_SHOW_PLAYS_MILLIS + PLAY_SHOW_FLIGHT_MILLIS / 2L)
        compose.onNodeWithTag(PLAY_SHOW_TAG).assertExists()
        compose.onNodeWithText("cast Storm Spark", substring = true, useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("Stack, 0 entries").assertExists()
        captureShot("$SCREENSHOTS/stack-animation/3-to-stack.png",
            "Halfway through its quarter-second flight the Spark shrinks toward Alice's Stack at the right edge, speeding up, its line gone; the Stack is still empty, nothing else moves there.")
        // Landed: gone from the middle, its entry on the Stack in its place at once (no move of its own)
        compose.mainClock.advanceTimeUntil(1_000) { entry in kit.routes.stackAnimation.landed }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.onNodeWithTag(PLAY_SHOW_TAG).assertDoesNotExist()
        compose.onNodeWithContentDescription("Stack, 1 entries").assertExists()
        val landedAt = compose.onNode(hasContentDescription("Projection, Storm Spark", substring = true), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        // Step 4: its arrow grows to the Cub
        compose.mainClock.advanceTimeUntil(1_000) { kit.routes.stackAnimation.targeting != null }
        advance(StackSchedule.ARROW_GROW_MILLIS / 2)
        compose.onNodeWithTag("stack-target-arrows").assertExists().assert(hasContentDescription("Targets of the top Stack entry: Ember Cub"))
        assertEquals(listOf(Triple(entry, 1, 650L)), targeted, "one arrow, 0.25 s growing and 0.4 s read")
        captureShot("$SCREENSHOTS/stack-animation/4-arrows.png",
            "The Spark landed on Alice's Stack and is its top entry: its red arrow grows from the Stack to her Ember Cub, the Cub's highlight fading in as it arrives, before anyone passes.")
        assertEquals(landedAt, compose.onNode(hasContentDescription("Projection, Storm Spark", substring = true), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot,
            "the entry stayed where it appeared")
        compose.runOnIdle { assertTrue(kit.routes.stackAnimation.busy, "the board waits for the arrows") }
        advance(StackSchedule.ARROW_GROW_MILLIS + StackSchedule.ARROW_READ_MILLIS)
        compose.runOnIdle {
            assertTrue(!kit.routes.stackAnimation.busy, "step 4 done")
            assertEquals(entry, kit.routes.stackAnimation.arrowed, "its arrow stays drawn while she holds priority (STK-058)")
            assertNull(kit.alice.shown?.show, "the play shown is done on the device too")
        }
        compose.onNodeWithTag("stack-target-arrows").assertExists()
        assertEquals(kit.me, kit.state().priority.holder, "she holds priority over the Spark")
    }

    // STK-058, STK-128, HIS-230, TBL-125 (spec step 5): the Spark resolving takes it off her Stack; its arrow fades as its effect plays
    @Test fun theResolutionComesAfterTheArrows() {
        val (kit, _) = bobsSparkShown()
        val resolved = ArrayList<List<String>>()
        val targeted = ArrayList<String>()
        StackAnimationProbe.targeting = { me, e, _, _ -> if (me == kit.f.a) targeted += e }
        StackAnimationProbe.resolving = { me, entries -> if (me == kit.f.a) resolved += entries }
        render(kit)
        advance(PLAY_SHOW_FLIGHT_MILLIS + HistoryPlaybackPreferences.DEFAULT_SHOW_PLAYS_MILLIS + PLAY_SHOW_FLIGHT_MILLIS + 100L)
        advance(StackSchedule.targetingMillis(1, 1f) + 50)
        kit.alice.pass()
        kit.playOn("the Spark resolving on Alice's table") { kit.state().stack.isEmpty() }
        compose.runOnIdle { version++ }
        compose.mainClock.advanceTimeByFrame()
        advance(400)
        assertEquals(1, targeted.size, "its arrows once")
        assertEquals(1, resolved.size, "then its resolution: $resolved")
        assertTrue(kit.field("Ember Cub").isEmpty(), "the Cub is gone")
    }

    // HIS-112, HIS-136, WEB-017, STK-007 (spec step 3; owner playtest 2026-11-15: the Stack on the field, never in the chat pane): on the
    // desktop table the play shown flies into the Stack at the battlefields' right edge, left of the open chat dock, as on the
    // phone; the Stack shows the entry only as it lands
    @Config(qualifiers = "w1440dp-h900dp-land-mdpi")
    @Test fun onTheDesktopTableThePlayFliesIntoTheStackBesideTheChatDock() {
        val (kit, show) = bobsSparkShown()
        val entry = "entry:${show.entry}"
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme { NoRipples {
                CompositionLocalProvider(LocalAppLayout provides AppLayout.Desktop, LocalEngineRoutes provides kit.routes,
                    LocalDesktopFrame provides DesktopFrame()) {
                    androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
                        DesktopTopRow(kit.f.game, kit.alice.play(), kit.f.a, kit.toolkit, reviewing = false, navigation = {},
                            returnCurrent = null, changes = null, dockOpen = true, toggleDock = {}, chat = null, chatUnread = 0)
                        Box(Modifier.weight(1f).fillMaxSize()) {
                            StackAnimatedTable(kit, Modifier.fillMaxSize(), landscape = true) { play ->
                                TableRuntimeHooks(navigation = {}, undo = { kit.routes.undo() }, controlsConnected = true,
                                    waitingLine = kit.routes.waitingLine(play), desktopDock = { DesktopDock { } })
                            }
                        }
                    }
                }
            } }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        val dock = compose.onNodeWithTag("desktop-dock").fetchSemanticsNode().boundsInRoot
        val stack = compose.onNodeWithContentDescription("Stack, 0 entries").fetchSemanticsNode().boundsInRoot
        assertTrue(stack.right <= dock.left, "the Stack on the field, left of the chat dock: $stack, dock $dock")
        advance(PLAY_SHOW_FLIGHT_MILLIS + HistoryPlaybackPreferences.DEFAULT_SHOW_PLAYS_MILLIS + 100L + PLAY_SHOW_FLIGHT_MILLIS * 3L / 4L)
        compose.onNodeWithTag(PLAY_SHOW_TAG).assertExists()
        compose.onNodeWithContentDescription("Stack, 0 entries").assertExists()
        // Most of its flight done, it lies between the middle and the Stack
        val flying = compose.onNodeWithTag(PLAY_SHOW_TAG).fetchSemanticsNode().boundsInRoot
        assertTrue(flying.center.x > 560f && flying.center.x < dock.left, "flying toward the Stack beside the dock: $flying, Stack $stack")
        captureShot("$SCREENSHOTS/stack-animation/3-to-stack-desktop.png",
            "On Alice's desktop table Bob's Storm Spark, held in the middle, shrinks into the Stack at the battlefields' right edge, beside the open chat dock; the Stack still reads 0 entries: its entry shows there only as it lands.")
        compose.mainClock.advanceTimeUntil(1_000) { entry in kit.routes.stackAnimation.landed }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Stack, 1 entries").assertExists()
    }
}
