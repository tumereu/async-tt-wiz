package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import arcanum.engine.Engine
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Input
import arcanum.engine.state.ObjectId
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TargetRef
import org.asyncttwiz.protocol.*
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
import kotlin.test.assertTrue

/**
 * I-D56 step C (§14): the table of a game on engine state: the private toggles and the assumer's indicator, the waiting
 * bar with Assume resolves, the catch-up screen, the rewind dialog and the incoming undo request.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class EngineTableScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val f = EngineGameFixture()

    @After fun forget() = AutomatumBoards.reset()

    private fun render(game: EngineGame, viewer: String, presence: ReceivedPresence? = null): EngineTableRoutes {
        val routes = EngineTableRoutes(viewer, { game }, {}, { game.play() })
        val toolkit = TableToolkitController(viewer, { game.play() }, { routes.canAct() }, { action, _ -> routes.local(listOf(action)) },
            playBatch = { actions, _ -> routes.local(actions) }, engine = routes)
        compose.setContent {
            MaterialTheme {
                NoRipples {
                    CompositionLocalProvider(LocalEngineRoutes provides routes) {
                        Box(Modifier.fillMaxSize()) {
                            TableSurface(f.game, game.play(), viewer, false, Modifier.fillMaxSize(), connected = true,
                                onIntent = toolkit::handle, toolkit = toolkit,
                                runtime = TableRuntimeHooks(navigation = {}, chat = {}, undo = { routes.undo() },
                                    undoHold = { routes.holdUndo() }, controlsConnected = true, opponentPresence = presence))
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        return routes
    }

    /** Alice kept while Bob is away: his keep and passes are assumed up to her first main phase. */
    private fun aliceAtMain(): EngineGame = EngineGame(f.game.id, f.a, f.base, f.snapshot()).apply {
        tick(false, 1_000); keep(emptyList()); tick(false, 2_000)
    }

    private fun hand(game: EngineGame, name: String): ObjectId = game.device.model.engine.let { s ->
        s.player(PlayerId(f.a)).hand.first { s.obj(it)?.definition?.faces?.first()?.name == name }
    }

    // TRN-059, I-D56 §14 (assumer's indicator): the private toggles and how many answers are assumed
    @Test fun assumedAnswersAndPrivateToggles() {
        val game = aliceAtMain()
        render(game, f.a)
        compose.onNodeWithTag("engine-indicator").assertExists()
        compose.onNodeWithContentDescription("Response mode", substring = true).assertExists()
        compose.onNodeWithContentDescription("Always assume resolves").assertExists()
        compose.onRoot().captureShot("$SCREENSHOTS/engine/assumed-main.png",
            "Alice kept while Bob was away and is in her first main phase: a violet chip at the top says how many of Bob's answers are assumed and how many actions came since, and the quick actions are her own Response mode (Auto-skip), Always assume resolves and Autotap mana.")
    }

    // STK-044, TRN-062, I-D56 §4, §14: Bob is online, so after Alice's keep the game waits for his; the waiting bar says
    // so with his presence and offers Assume keep, and the step arrow is gone while he must act
    @Test fun waitingBarOffersAssumeKeep() {
        val game = EngineGame(f.game.id, f.a, f.base, f.snapshot())
        game.tick(live = true, now = 1_000)
        assertIs<GameDevice.Acted.Sent>(game.keep(emptyList()))
        game.tick(live = true, now = 2_000)
        val waiting = assertIs<GameDevice.Status.Waiting>(game.device.status)
        assertEquals(GameDevice.Offer.AssumeKeep, waiting.offer)
        render(game, f.a, ReceivedPresence(OpponentPresence(PresenceState.ONLINE, idleSeconds = 0), monotonicMillis()))
        compose.onNodeWithText("Bob is deciding on a mulligan").assertExists()
        compose.onNodeWithText("Assume keep").assertExists()
        compose.onRoot().captureShot("$SCREENSHOTS/engine/waiting-bar.png",
            "Alice kept while Bob is online: above her hand the waiting bar says Bob is deciding on a mulligan, shows that he is online, and offers Assume keep; there is no step arrow while he must act.")
        // One tap assumes it: the game goes on to Alice's first main phase
        compose.onNodeWithText("Assume keep").performClick()
        compose.runOnIdle { assertEquals(GameDevice.Status.YourMove, game.device.status) }
    }

    // HIS-033 (I-D56 §7): coming back, Bob first sees the catch-up screen with his response mode
    @Test fun catchUpScreen() {
        val alice = aliceAtMain()
        val events = alice.device.session.pending
        val snapshot = EngineSnapshot(f.codec.encodeDefinitions(), f.snapshot().rootId, events.last().id,
            f.codec.encodeModel(alice.device.model), localHeads = mapOf(f.a to events.last().id, f.b to f.start.id),
            baseId = f.start.id, base = f.snapshot().state, events = events.map(f.codec::encodeEvent))
        val bob = EngineGame(f.game.id, f.b, f.base, snapshot)
        render(bob, f.b)
        compose.onNodeWithText("Catching up").assertExists()
        compose.onNodeWithTag("catch-up-play").assertExists()
        captureShot("$SCREENSHOTS/engine/catch-up.png",
            "Bob comes back after Alice played on: the Catching up screen explains that her actions play back and stop where he decides, with his response mode (Full control, Normal, Auto-skip) and Play.")
    }

    // HIS-083, HIS-084 (I-D56 §10): holding Undo opens the rewind dialog; going back past what Bob saw asks him
    @Test fun rewindDialogPicksAnEarlierPoint() {
        val game = aliceAtMain()
        val pending = game.device.session.pending
        pending.forEachIndexed { n, event -> game.revision(f.revision(event, n + 2, mapOf(f.a to event.id, f.b to f.start.id))) }
        val routes = render(game, f.a)
        compose.runOnIdle { routes.holdUndo() }
        compose.waitForIdle()
        compose.onNodeWithText("Go back to just before:").assertExists()
        compose.onNodeWithText("You kept").assertExists()
        captureShot("$SCREENSHOTS/engine/rewind-dialog.png",
            "Holding Undo opens the Undo dialog: the actions so far, newest first, to pick the point to go back to; going back past what Bob has seen asks him first.")
    }

    // SYS-025 (I-D56 §10): the other player's undo request: their message, Accept or Deny; the game waits meanwhile
    @Test fun incomingUndoRequest() {
        val game = aliceAtMain()
        val pending = game.device.session.pending
        pending.forEachIndexed { n, event -> game.revision(f.revision(event, n + 2, mapOf(f.a to event.id, f.b to event.id))) }
        val target = pending.first().id
        game.revision(GameRevision(f.game.id, "90", "91", DeviceRandom.uuid(), f.b, GameScope(f.game.id, playId = f.base.id), f.game,
            engine = EngineUpdate(localHeads = mapOf(f.a to pending.last().id, f.b to pending.last().id),
                rewindRequest = EngineRewindRequest(f.b, target, "I misclicked my keep, sorry!"))))
        render(game, f.a)
        compose.onNodeWithText("Bob asks to undo").assertExists()
        compose.onNodeWithTag("request-accept").assertExists()
        captureShot("$SCREENSHOTS/engine/undo-request.png",
            "Bob asks to undo: Alice sees his message, where the game would go back to, and Accept or Deny; the game waits until she answers.")
    }
}
