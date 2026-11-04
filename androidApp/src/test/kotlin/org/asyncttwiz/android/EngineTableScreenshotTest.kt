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
 * I-D56 steps C–D (§14): the table of a game on engine state: the private toggles and the assumer's indicator, the
 * waiting bar with Assume resolves, the catch-up screen, the rewind dialog and the incoming undo request with the board
 * they go back to, the strip and timeline mode on engine events, the Assume blockers screen with edited blocks, and the
 * other player's shared attack staging.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class EngineTableScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val f = EngineGameFixture()

    @After fun forget() = AutomatumBoards.reset()

    private val names = mapOf(TableStatesFixture.a to "Alice", TableStatesFixture.b to "Bob")

    /** [game]'s table as [EngineTableScreen] draws it: the board (or the board at [routes]' review cursor in timeline
     * mode), the strip of the events shown, the change flashes. */
    private fun render(game: EngineGame, viewer: String, presence: ReceivedPresence? = null,
        routes: EngineTableRoutes = EngineTableRoutes(viewer, { game }, {}, { game.play() }), summary: GameSummary = f.game): EngineTableRoutes {
        val toolkit = TableToolkitController(viewer, { game.play() }, { routes.canAct() }, { action, _ -> routes.local(listOf(action)) },
            playBatch = { actions, _ -> routes.local(actions) }, engine = routes)
        compose.setContent {
            MaterialTheme {
                NoRipples {
                    CompositionLocalProvider(LocalEngineRoutes provides routes) {
                        Box(Modifier.fillMaxSize()) {
                            val reviewed = routes.review?.let { game.reviewPlay(it) }
                            val events = game.timeline.events(game, names)
                            TableSurface(summary, reviewed ?: game.play(), viewer, false, Modifier.fillMaxSize(), connected = reviewed == null,
                                onIntent = toolkit::handle, toolkit = toolkit,
                                runtime = TableRuntimeHooks(navigation = {}, chat = {}, undo = { routes.undo() },
                                    undoHold = { routes.holdUndo() }, controlsConnected = reviewed == null, opponentPresence = presence,
                                    reviewing = reviewed != null, liveView = reviewed == null,
                                    timelinePlayback = if (reviewed != null) TimelinePlaybackStatus.PAUSED else null,
                                    returnCurrent = if (reviewed != null) ({ routes.leaveReview() }) else null,
                                    liveTimeline = if (events.isEmpty()) null else ({ EngineStrip(events, viewer, false) { routes.enterReview(events.last().id) } }),
                                    bottomPanel = if (reviewed == null) null else ({ _ -> EngineReviewPanel(routes, events, viewer, false, 1_000) })))
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
        // HIS-001: the strip shows the events up to her local head (her keep, Bob's assumed keep and passes)
        compose.onNodeWithContentDescription("Event strip").assertExists()
        compose.onNodeWithContentDescription("Timeline mode").assertExists()
        compose.onNodeWithContentDescription("Response mode", substring = true).assertExists()
        compose.onNodeWithContentDescription("Always assume resolves").assertExists()
        compose.onRoot().captureShot("$SCREENSHOTS/engine/assumed-main.png",
            "Alice kept while Bob was away and is in her first main phase: a violet chip at the left says how many of Bob's answers are assumed and how many actions came since, the strip above the quick actions shows the events so far in each player's colour, and the quick actions are her own Response mode (Auto-skip), Always assume resolves and Autotap mana.")
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
        // HIS-099: the board at the chosen point
        compose.onNodeWithTag("undo-board").assertExists()
        captureShot("$SCREENSHOTS/engine/rewind-dialog.png",
            "Holding Undo opens the Undo dialog: the actions so far, newest first, to pick the point to go back to, and below them the board at that point; going back past what Bob has seen asks him first.")
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
        compose.onNodeWithTag("undo-board").assertExists()
        captureShot("$SCREENSHOTS/engine/undo-request.png",
            "Bob asks to undo: Alice sees his message, where the game would go back to with a small view of that board, and Accept or Deny; the game waits until she answers.")
    }

    // HIS-004, HIS-010, HIS-012 (I-D56 step D): timeline mode on engine events: the board at an earlier event, view-only
    @Test fun timelineModeShowsAnEarlierBoard() {
        val game = aliceAtMain()
        val routes = render(game, f.a)
        val events = game.timeline.events(game, names)
        compose.runOnIdle { routes.enterReview(events[1].id) }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Auto advance").assertExists()
        compose.onNodeWithContentDescription("Return to current").assertExists()
        captureShot("$SCREENSHOTS/engine/timeline-mode.png",
            "Alice tapped the history button at the end of the strip and stepped back to her keep: the bars turn grey, the board shows the opening as it was then, and the timeline panel has the strip with Previous, Auto and Next; nothing on it acts.")
    }

    // CMB-040 (I-D56 §4, §14): the Assume blockers screen while Bob is away: Alice edits the blocks before assuming them
    @Test fun assumeBlockersScreenEditsTheBlocks() {
        val scenario = EngineCombatScenario()
        scenario.until(scenario::bothHaveCubs)
        val alice = scenario.alice
        val s = alice.device.model.engine
        val attacker = scenario.creatures(s, scenario.me, ready = true).first()
        scenario.online[f.b] = false
        alice.tick(false, scenario.sim.now + 120_000)
        assertIs<GameDevice.Acted.Sent>(alice.send(listOf(arcanum.engine.input.Input.DeclareAttackers(scenario.me,
            listOf(arcanum.engine.input.Attack(attacker, TargetRef.Player(scenario.other)))))))
        alice.tick(false, scenario.sim.now + 121_000)
        val waiting = assertIs<GameDevice.Status.Waiting>(alice.device.status)
        assertTrue(waiting.blockersScreen)
        alice.notes.clear()
        render(alice, f.a)
        compose.onNodeWithText("Assume Bob's blockers").assertExists()
        val blocker = scenario.creatures(s, scenario.other, ready = false).first { s.obj(it)?.tapped == false } as ObjectId.Card
        compose.onNodeWithTag("assume-block-${blocker.card.value}-${(attacker as ObjectId.Card).card.value}").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("1 block").assertExists()
        captureShot("$SCREENSHOTS/engine/assume-blockers.png",
            "Bob is away when Alice attacks with her Ember Cub: the Assume blockers screen lists her attacker with Bob's untapped creatures beside it, and she tapped his Cub to assume it blocks (1 block) before pressing OK; Back would wait for him.")
        // OK assumes exactly those blocks
        compose.onNodeWithText("OK").performClick()
        compose.runOnIdle {
            val assumed = alice.device.session.pending.firstNotNullOf { it.assumed?.input as? arcanum.engine.input.Input.DeclareBlockers }
            assertEquals(listOf(arcanum.engine.input.Block(blocker, attacker)), assumed.blocks)
        }
    }

    // CMB-028 (I-D54 §8): Alice's attack staging is shared: Bob's table shows her staged Cub in the lane while she decides
    @Test fun theOtherPlayersStagingShows() {
        val scenario = EngineCombatScenario()
        scenario.until(scenario::bothHaveCubs)
        val alice = scenario.alice
        val attacker = scenario.creatures(alice.device.model.engine, scenario.me, ready = true).first()
        alice.table(listOf(org.asyncttwiz.protocol.game.TableChange.AttackDraft(null,
            listOf(arcanum.engine.input.Attack(attacker, TargetRef.Player(scenario.other))))))
        scenario.settle()
        scenario.bob.notes.clear() // the scripted setup's own races (SYS-012 notes) aren't part of this picture
        render(scenario.bob, f.b, summary = f.game)
        captureShot("$SCREENSHOTS/engine/shared-staging.png",
            "Alice is staging her attack: on Bob's table her Ember Cub already stands in the combat lane aimed at him while she decides, and the strip's newest event says she staged 1 attacker.")
    }
}
