package org.asyncttwiz.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The other player's open choice on the two devices of the simulated server ([EngineTableKit]): the waiting player's
 * event text says it (in the wait's words, TRN-066) without fading, also with Event text off, and what they try meanwhile
 * shakes and flashes it; once the choice is made the event text goes back to its events. Each table is drawn as
 * [EngineTableScreen] draws it (the table and the event text over its top row).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class EngineChoiceWaitTest {
    @get:Rule val compose = createComposeRule()
    private val kit = EngineTableKit()
    private val f = kit.f
    private var version by mutableIntStateOf(0)

    @After fun forget() = AutomatumBoards.reset()

    /** [viewer]'s table with the event text over it; Event text is switched off unless [eventTextOn] (LOB-081). */
    private fun render(viewer: String, eventTextOn: Boolean = false): EventTextFeed {
        val game = if (viewer == f.a) kit.alice else kit.bob
        val routes = if (viewer == f.a) kit.routes else kit.bobRoutes
        val toolkit = if (viewer == f.a) kit.toolkit else kit.bobToolkit
        val feed = EventTextFeed({ it.run() }, { it() }).apply { enabled = eventTextOn }
        compose.setContent {
            MaterialTheme {
                NoRipples {
                    CompositionLocalProvider(LocalEngineRoutes provides routes, LocalRefusedTap provides toolkit::refusedTap) {
                        Box(Modifier.fillMaxSize()) {
                            version
                            val play = game.play()
                            TableSurface(f.game, play, viewer, false, Modifier.fillMaxSize(), connected = true,
                                onIntent = toolkit::handle, toolkit = toolkit,
                                runtime = TableRuntimeHooks(navigation = {}, chat = {}, undo = { routes.undo() },
                                    controlsConnected = true, waitingLine = routes.waitingLine(play)))
                            EventTextOverlay(feed, 1_000, Modifier.align(Alignment.TopCenter), status = routes.choosingText(play),
                                flashes = toolkit.choiceFlashes)
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        return feed
    }

    private fun redraw() { version++; compose.waitForIdle() }

    // HIS-075, TRN-066, TBL-118, LOB-081 (owner, 2026-11-05, 2026-11-09): Alice kept while Bob (online) is still deciding on a
    // mulligan: her event text says so in the wait's words, with Event text off too, and fades after a chip's time while the
    // wait band under his hand goes on saying it; her double-tap, drag and tap of a hand card are refused: the card shakes
    // and the event text flashes again, without a message; once Bob keeps, the event text is the events' again
    @Test fun theOtherPlayersMulliganShowsInTheEventTextAndRefusalsFlashIt() {
        val toolkit = kit.toolkit
        // Bob is seen online before Alice keeps
        kit.settle(bobActs = false)
        assertTrue(toolkit.opening(TableActionIntent.Keep(kit.scope)), "Alice keeps")
        kit.settle(bobActs = false)
        val waiting = assertIs<GameDevice.Status.Waiting>(kit.alice.device.status)
        assertEquals(GameDevice.Wait.Keep, waiting.reason)
        render(f.a)
        compose.onNodeWithTag("event-text").assertContentDescriptionEquals("Bob is deciding on a mulligan")
        compose.onNodeWithTag("wait-band").assertExists()
        compose.mainClock.advanceTimeBy(EVENT_TEXT_MIN_MILLIS * 5)
        compose.onNodeWithTag("event-text").assertDoesNotExist()
        compose.onNodeWithTag("wait-band").assertExists()
        compose.onNodeWithText("Bob is deciding on a mulligan").assertExists()

        val card = kit.atw(kit.hand("Mountain").first())
        kit.gestures().let { (gestures, _) -> kit.doubleTap(gestures, kit.item(card, PlayZone.HAND).bounds.center) }
        assertEquals(card, toolkit.shaking?.id, "the double-tapped card shakes")
        var flashes = toolkit.choiceFlashes
        assertTrue(flashes > 0, "the event text flashes")
        redraw()
        compose.onNodeWithTag("event-text").assertContentDescriptionEquals("Bob is deciding on a mulligan")
        kit.gestures().let { (gestures, _) ->
            kit.drag(gestures, kit.item(card, PlayZone.HAND).bounds.center, kit.scene().fields.last().bounds.center)
        }
        assertTrue(toolkit.choiceFlashes > flashes, "a drag onto the Battlefield too")
        flashes = toolkit.choiceFlashes
        kit.tap(kit.item(card, PlayZone.HAND))
        assertNull(toolkit.hand.raised, "a tap raises nothing")
        assertTrue(toolkit.choiceFlashes > flashes, "a tap too")
        assertNull(toolkit.error, "no message")
        assertEquals(card, toolkit.shaking?.id)
        kit.settle(bobActs = false)
        assertTrue(card in kit.hand("Mountain").map(kit::atw), "nothing was played")

        kit.bob.keep(emptyList())
        kit.settle()
        assertNull(kit.routes.choosingText(kit.alice.play()), "the choice is made")
        redraw()
        compose.mainClock.advanceTimeBy(EVENT_TEXT_FADE_OUT_MILLIS * 2L)
        compose.onNodeWithContentDescription("Bob is deciding on a mulligan").assertDoesNotExist()
        // Nothing refused now: nothing flashes
        flashes = toolkit.choiceFlashes
        kit.playOn("Alice's Main 1") { kit.aliceMain() }
        kit.tap(kit.item(kit.atw(kit.hand("Mountain").first()), PlayZone.HAND))
        assertEquals(flashes, toolkit.choiceFlashes)
    }

    // HIS-075, STK-032, TRN-066: Alice discards to hand size (an Automatum choice of hers): Bob's event text says "Alice is
    // choosing" while his faint words over the battlefield stay; her own table says nothing of it
    @Test fun theOtherPlayersChoiceShowsInTheEventText() {
        kit.toAliceDiscard()
        kit.settle()
        render(f.b, eventTextOn = true)
        compose.onNodeWithTag("event-text").assertContentDescriptionEquals("Alice is choosing")
        compose.onRoot().captureShot("$SCREENSHOTS/engine/event-text-choosing.png",
            "Alice discards to hand size in her cleanup step: on Bob's table the event text over the top row says Alice is choosing, and stays while she chooses; the faint words over the battlefield say the same.")
        assertNull(kit.routes.choosingText(kit.alice.play()), "Alice's own table doesn't say she is choosing")
    }
}
