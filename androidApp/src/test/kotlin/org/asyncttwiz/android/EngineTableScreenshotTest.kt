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
 * I-D56 steps C–D (§14): the table of a game on engine state: the private toggles and the unconfirmed mark, the
 * wait's faint words with Assume in the step arrow's place, the catch-up screen, the rewind dialog and the incoming undo request with the board
 * they go back to, the strip and timeline mode on engine events, and the other player's shared attack staging (the
 * combat band itself: [CombatBandTest]).
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
        routes.opponentName = names.entries.first { it.key != viewer }.value
        val toolkit = TableToolkitController(viewer, { game.play() }, { routes.canAct() }, { action, _ -> routes.local(listOf(action)) },
            playBatch = { actions, _ -> routes.local(actions) }, engine = routes)
        compose.setContent {
            MaterialTheme {
                NoRipples {
                    CompositionLocalProvider(LocalEngineRoutes provides routes) {
                        Box(Modifier.fillMaxSize()) {
                            val reviewed = routes.review?.let { game.reviewPlay(it) }
                            val events = game.timeline.events(game, names)
                            val status = game.shown?.status
                            val playing = status is GameDevice.Status.Playing
                            // HIS-094, HIS-095: catch-up playback looks like live play, with the centre text at its paused start
                            // and at a stop its pause made
                            val watch = status == GameDevice.Status.CatchUp || (status as? GameDevice.Status.Stopped)?.paused == true
                            TableSurface(summary, reviewed ?: game.play(), viewer, false, Modifier.fillMaxSize(), connected = reviewed == null,
                                onIntent = toolkit::handle, toolkit = toolkit,
                                runtime = TableRuntimeHooks(navigation = {}, chat = {}, undo = { routes.undo() },
                                    undoHold = { routes.holdUndo() }, controlsConnected = reviewed == null, opponentPresence = presence,
                                    reviewing = reviewed != null, liveView = reviewed == null,
                                    timelinePlayback = if (reviewed != null) TimelinePlaybackStatus.PAUSED else null,
                                    waitingLine = routes.waitingLine(game.play()),
                                    returnCurrent = if (reviewed != null) ({ routes.leaveReview() }) else null,
                                    liveTimeline = if (events.isEmpty()) null else ({ EngineStrip(events, viewer, false) { routes.enterReview(events.last().id) } }),
                                    bottomPanel = if (reviewed != null) ({ _ -> EngineReviewPanel(routes, events, viewer, false, 1_000) }) else null,
                                    centreAction = if (reviewed == null && watch)
                                        TableCentreAction("${routes.opponentName} has made moves · Tap to watch") { game.startPlayback() } else null,
                                    boardTap = if (reviewed == null && playing) ({ game.pause() }) else null))
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        return routes
    }

    /** Alice kept after Bob's keep while he is away: his passes are assumed up to her first main phase. */
    private fun aliceAtMain(): EngineGame = EngineGame(f.game.id, f.a, f.base, f.snapshot(otherKept = true)).apply {
        tick(1_000); keep(emptyList()); tick(2_000)
    }

    private fun hand(game: EngineGame, name: String): ObjectId = game.device.model.engine.let { s ->
        s.player(PlayerId(f.a)).hand.first { s.obj(it)?.definition?.faces?.first()?.name == name }
    }

    // TRN-059, TRN-068, TRN-016: the private toggles, and the unconfirmed mark at the main button's corner (no count); the
    // main button is twice as wide as a quick action
    @Test fun assumedAnswersAndPrivateToggles() {
        val game = aliceAtMain()
        val routes = EngineTableRoutes(f.a, { game }, {}, { game.play() }).apply { opponentName = "Bob" }
        render(game, f.a, routes = routes)
        compose.onNodeWithTag("engine-unconfirmed").assertExists()
        compose.onNodeWithContentDescription("Bob hasn't seen your last moves yet", substring = true).assertExists()
        // HIS-001: the strip shows the events up to her local head (her keep, Bob's assumed keep and passes)
        compose.onNodeWithContentDescription("Event strip").assertExists()
        compose.onNodeWithContentDescription("Timeline mode").assertExists()
        compose.onNodeWithContentDescription("Response mode", substring = true).assertExists()
        compose.onNodeWithContentDescription("Always assume resolves").assertExists()
        compose.onRoot().captureShot("$SCREENSHOTS/engine/assumed-main.png",
            "Alice kept while Bob was away and is in her first main phase: a small violet dot at the corner of the main button (To combat, twice as wide as a quick action) says Bob hasn't seen her last moves yet (no count, nothing over the board), the strip above the quick actions shows the events so far in each player's colour, and the quick actions are her own Response mode (Auto-skip), Always assume resolves and Autotap mana.")
    }

    // TRN-066, TRN-070, TRN-062, TRN-027, I-D56 §4, §14: after Alice's keep the game waits for Bob's; faint words over the
    // battlefield say so (nothing covers the board) and nothing stands in the step arrow's place: his keep is never assumed
    @Test fun waitingBarWaitsForTheOtherKeep() {
        val game = EngineGame(f.game.id, f.a, f.base, f.snapshot())
        game.tick(1_000)
        assertIs<GameDevice.Acted.Sent>(game.keep(emptyList()))
        game.tick(2_000)
        assertEquals(GameDevice.Status.Waiting(GameDevice.Wait.Keep), game.device.status)
        render(game, f.a, ReceivedPresence(OpponentPresence(PresenceState.ONLINE, idleSeconds = 0), monotonicMillis()))
        compose.onNodeWithText("Bob is deciding on a mulligan").assertExists()
        compose.onNodeWithTag("engine-bar").assertDoesNotExist()
        compose.onNodeWithTag("engine-wait-action").assertDoesNotExist()
        compose.onRoot().captureShot("$SCREENSHOTS/engine/waiting-bar.png",
            "Alice kept while Bob is online: faint words over the battlefield say Bob is deciding on a mulligan, nothing covers the board or her hand, and the main button's place is empty (his keep is never assumed).")
    }

    // HIS-033, HIS-094 (§29.1, owner 2026-11-05): coming back after the opening, Bob's playback of what Alice did starts
    // paused: no dialog, his table as live play with the centre text "Alice has made moves · Tap to watch" (no Play/Pause,
    // no timeline look); a tap on it starts playback
    @Test fun catchUpStartsPaused() {
        val sim = EngineAppSim(f)
        val alice = sim.load(f.a)
        val bobAway = sim.load(f.b)
        assertIs<GameDevice.Acted.Sent>(bobAway.keep(emptyList()))
        sim.flush(f.b)
        repeat(6) {
            sim.deliver(f.a)
            alice.playToStop()
            (alice.device.status as? GameDevice.Status.Stopped)?.let { alice.act(it.assumed) }
            if (alice.device.status == GameDevice.Status.YourMove && alice.device.model.engine.turn.number == 0) alice.keep(emptyList())
            alice.tick(sim.now)
            sim.flush(f.a)
            sim.now += 1_000
        }
        val land = Engine.options(alice.device.model.engine, PlayerId(f.a)).filterIsInstance<Input.PlayLand>().first()
        assertIs<GameDevice.Acted.Sent>(alice.send(listOf(land)))
        sim.flush(f.a)
        val bob = sim.load(f.b)
        // Alice's keep, still in the opening, shows at once; past it playback waits
        assertIs<GameDevice.Status.Playing>(bob.device.status)
        bob.step()
        assertEquals(GameDevice.Status.CatchUp, bob.device.status)
        render(bob, f.b)
        compose.onNodeWithText("Catching up").assertDoesNotExist()
        compose.onNodeWithText("Timeline mode").assertDoesNotExist()
        compose.onNodeWithTag("playback-toggle").assertDoesNotExist()
        compose.onNodeWithContentDescription("Alice has made moves · Tap to watch").assertExists()
        compose.onNodeWithContentDescription("Full control").assertExists()
        compose.onNodeWithContentDescription("Always assume resolves").assertDoesNotExist()
        captureShot("$SCREENSHOTS/engine/catch-up.png",
            "Bob comes back after Alice played on past the opening: no dialog and no timeline look; his table shows where he left it, as in live play, with \"Alice has made moves · Tap to watch\" in the middle of the board, and his playback quick actions Full control and Autotap mana; nothing plays until he taps the text.")
        compose.onNodeWithTag("centre-action").performClick()
        compose.runOnIdle {
            assertTrue(bob.device.status.let { it is GameDevice.Status.Playing || it is GameDevice.Status.Stopped }, "the tap started it")
        }
    }

    // HIS-021, CMB-007 (§29.6): Alice attacked while Bob was away; his playback shows the attack in the combat band
    // between the battlefields, as in live combat
    @Test fun anAttackPlaysBackInTheCombatArea() {
        val scenario = EngineCombatScenario()
        scenario.until(scenario::bothHaveCubs)
        val alice = scenario.alice
        val attacker = scenario.creatures(alice.device.model.engine, scenario.me, ready = true).first()
        assertIs<GameDevice.Acted.Sent>(alice.send(listOf(Input.DeclareAttackers(scenario.me,
            listOf(arcanum.engine.input.Attack(attacker, TargetRef.Player(scenario.other)))))))
        alice.tick(scenario.sim.now)
        if ((alice.device.status as? GameDevice.Status.Waiting)?.offer == GameDevice.Offer.AssumeBlockers) alice.assume()
        scenario.sim.flush(scenario.f.a)
        val bob = scenario.sim.load(scenario.f.b)
        bob.startPlayback()
        var guard = 0
        while (bob.device.status is GameDevice.Status.Playing && guard++ < 100) {
            val shown = bob.step()
            if (shown?.engine?.steps.orEmpty().any { (it.input as? Input.DeclareAttackers)?.attacks?.isNotEmpty() == true }) break
        }
        assertTrue(bob.play().board.combat?.pairs.orEmpty().isNotEmpty(), "the attack shown")
        render(bob, f.b, summary = scenario.f.game)
        captureShot("$SCREENSHOTS/engine/playback-attack.png",
            "Bob's playback of Alice's attack while he was away, stopped at his blocks: her Ember Cub attacks him in the combat band between the battlefields, as in live combat, with No blocks on the main button.")
    }

    /** Bob coming back after [aliceAtMain]: his device holds her events past his local head. */
    private fun bobCatchingUp(): EngineGame {
        val alice = aliceAtMain()
        val events = alice.device.session.pending
        return EngineGame(f.game.id, f.b, f.base, f.behind(events, alice.device.model))
    }

    /** Plays [game] back until it stops. */
    private fun playToStop(game: EngineGame) {
        var guard = 0
        while (game.device.status is GameDevice.Status.Playing && guard++ < 100) game.step()
    }

    /** Alice at her main phase in [mode] casts Storm Spark at Bob ([alwaysAssume]: "Always assume resolves"). */
    private fun aliceCastsSpark(mode: org.asyncttwiz.protocol.game.ResponseMode, alwaysAssume: Boolean): EngineGame {
        val game = EngineGame(f.game.id, f.a, f.base, f.snapshot(otherKept = true)).apply { tick(1_000); keep(emptyList()); tick(2_000) }
        game.settings(game.shown!!.settings.copy(mode = mode, alwaysAssumeResolves = alwaysAssume))
        val me = PlayerId(f.a)
        game.send(listOf(Input.PlayLand(me, hand(game, "Mountain"))))
        game.tick(3_000)
        val copy = game.device.model.engine.deepCopy()
        val inputs = mutableListOf<Input>(Input.Cast(me, hand(game, "Storm Spark")))
        var next: Input = inputs.first()
        while (true) {
            Engine.perform(copy, next).changes.forEach(copy::apply)
            val d = copy.pending?.decision ?: break
            if (d is Decision.Random || Engine.decider(copy, d.player) != me) break
            next = if (d is Decision.ManaPayment) Input.Answer(d.id, Answer.ManaPayment(d.suggestion))
                else Engine.options(copy, me).filterIsInstance<Input.Answer>().first { it.decision == d.id }
            inputs += next
        }
        assertIs<GameDevice.Acted.Sent>(game.send(inputs))
        game.tick(4_000)
        return game
    }

    // TRN-062 (owner playtest 2026-11-04 #3): the opening decision is only the quick actions Mulligan and Keep: nothing
    // covers the board
    @Test fun theOpeningHasOnlyTheQuickActions() {
        val game = EngineGame(f.game.id, f.a, f.base, f.snapshot())
        game.tick(1_000)
        render(game, f.a)
        compose.onNodeWithText("Choose one").assertDoesNotExist()
        compose.onNodeWithContentDescription("Mulligan").assertExists()
        compose.onNodeWithContentDescription("Keep").assertExists()
        captureShot("$SCREENSHOTS/engine/opening.png",
            "A new game's opening: Alice's seven cards are in her hand and her quick actions are Mulligan and Keep; nothing covers the board or asks her anything else.")
    }

    // STK-029, STK-027, TRN-063 (owner 2026-11-05): in Full control Alice keeps priority after casting Storm Spark: it
    // shows on the Stack and Resolve is the main button; nothing sits under the Stack and no panel says she cast it
    @Test fun resolveIsTheMainButton() {
        val game = aliceCastsSpark(org.asyncttwiz.protocol.game.ResponseMode.FullControl, alwaysAssume = true)
        assertEquals(GameDevice.Status.YourMove, game.device.status)
        render(game, f.a)
        compose.onNodeWithTag("main-button").assert(hasContentDescription("Resolve"))
        compose.onNodeWithTag("stack-resolve").assertDoesNotExist()
        compose.onNodeWithText("You cast Storm Spark").assertDoesNotExist()
        compose.onNodeWithContentDescription("Respond prompt").assertDoesNotExist()
        captureShot("$SCREENSHOTS/engine/stack-resolve.png",
            "In Full control Alice cast Storm Spark at Bob and kept priority: the spell is on the Stack, nothing covers the board or sits under the Stack, and the main button in the bottom-right corner, twice as wide as a quick action, reads Resolve.")
        compose.onNodeWithTag("main-button").performClick()
        compose.runOnIdle { assertTrue(game.device.model.engine.stack.isEmpty(), "she passed; Bob's pass was assumed and it resolved") }
    }

    // STK-028, TRN-070, HIS-096 (owner 2026-11-05): with "Always assume resolves" off, after Alice's Storm Spark the game
    // waits for Bob (away or online); "Assume resolves" is the main button and assumes he lets it resolve
    @Test fun assumeIsTheMainButton() {
        val game = aliceCastsSpark(org.asyncttwiz.protocol.game.ResponseMode.Normal, alwaysAssume = false)
        val waiting = assertIs<GameDevice.Status.Waiting>(game.device.status)
        assertEquals(GameDevice.Offer.AssumeResolves, waiting.offer)
        render(game, f.a, ReceivedPresence(OpponentPresence(PresenceState.OFFLINE, idleSeconds = 0), monotonicMillis()))
        compose.onNodeWithTag("engine-wait-action").assert(hasContentDescription("Assume resolves"))
        compose.onNodeWithTag("stack-assume").assertDoesNotExist()
        captureShot("$SCREENSHOTS/engine/stack-assume.png",
            "Alice cast Storm Spark while Bob is away and Always assume resolves is off: faint words say Bob may respond to it, and the main button reads Assume resolves, where tapping it goes on as if Bob let it resolve.")
        compose.onNodeWithTag("engine-wait-action").performClick()
        compose.runOnIdle { assertTrue(game.device.model.engine.stack.isEmpty(), "Storm Spark resolved on the assumed pass") }
    }

    // STK-046 (owner playtest 2026-11-04): in Full control Alice keeps priority after her Ember Herald resolved: its enters
    // trigger is on the Stack as an ability token (the Herald's art, the trigger marker, its name and text), not as the
    // Herald card; the Stack list shows it as "Ember Herald's ability" with its text
    @Test fun anAbilityOnTheStackIsDrawnAsAnAbility() {
        val (game, h) = heraldTriggerOnStack()
        render(game, h.a)
        compose.onNodeWithTag("main-button").assert(hasContentDescription("Resolve"))
        compose.onNodeWithContentDescription("Triggered ability, Ember Herald — When this creature enters", substring = true).assertExists()
        captureShot("$SCREENSHOTS/engine/stack-ability.png",
            "In Full control Alice's Ember Herald resolved and its enters trigger is on the Stack: it is drawn as a small dark ability token with the Herald's art, a \"!\" trigger marker, the Herald's name and the trigger's text, not as the Herald card, and the main button reads Resolve.")
        compose.onNodeWithContentDescription("Stack, 1 entries").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Top · Ember Herald's ability").assertExists()
        captureShot("$SCREENSHOTS/engine/stack-ability-list.png",
            "Alice tapped the Stack holding her Ember Herald's enters trigger: the list shows it as the ability token, \"Top · Ember Herald's ability\" and the trigger's text.")
    }

    // STK-050, TBL-104: holding an ability's entry on the Stack shows a card-sized tile with the source's name and the
    // ability's text, then the source card's picture, and nothing else
    @Test fun holdingAnAbilityOnTheStackShowsItsTileThenItsSourceCard() {
        val (game, h) = heraldTriggerOnStack()
        render(game, h.a)
        compose.onNodeWithContentDescription("Stack, 1 entries").performClick()
        compose.waitForIdle()
        val row = compose.onNodeWithText("Top · Ember Herald's ability")
        compose.mainClock.autoAdvance = false
        row.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(600)
        val tile = compose.onNodeWithContentDescription("Triggered ability of Ember Herald").assertIsDisplayed().getBoundsInRoot()
        compose.onNode(hasText("When this creature enters", substring = true) and
            hasAnyAncestor(hasContentDescription("Triggered ability of Ember Herald"))).assertExists()
        val source = compose.onNodeWithContentDescription("Card image: Ember Herald").assertExists().getBoundsInRoot()
        assertTrue(tile.bottom <= source.top, "The tile first, then the source card")
        assertEquals(tile.right - tile.left, source.right - source.left)
        compose.waitForIdle(); compose.mainClock.advanceTimeBy(100)
        captureShot("$SCREENSHOTS/engine/stack-ability-inspect.png",
            "Alice holds her Ember Herald's enters trigger in the Stack list: Inspect shows a dark card-sized tile with the trigger marker, \"Triggered ability\", the Herald's name and the trigger's text, then the Herald's card image below it.")
        row.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.onNodeWithContentDescription("Card inspection content").assertDoesNotExist()
    }

    /** Alice in Full control with her Ember Herald's enters trigger on the Stack, holding priority. */
    private fun heraldTriggerOnStack(): Pair<EngineGame, EngineGameFixture> {
        val h = EngineGameFixture(heralds = true)
        val me = PlayerId(h.a)
        val game = EngineGame(h.game.id, h.a, h.base, h.snapshot(otherKept = true)).apply { tick(1_000); keep(emptyList()); tick(2_000) }
        game.settings(game.shown!!.settings.copy(mode = org.asyncttwiz.protocol.game.ResponseMode.FullControl, alwaysAssumeResolves = true))
        fun named(name: String) = game.device.model.engine.let { s -> s.player(me).hand.first { s.obj(it)?.definition?.faces?.first()?.name == name } }
        game.send(listOf(Input.PlayLand(me, named("Mountain"))))
        game.tick(3_000)
        val s = game.device.model.engine
        val cast = Engine.options(s, me).filterIsInstance<Input.Cast>().first { s.obj(it.card)?.definition?.faces?.first()?.name == "Ember Herald" }
        assertIs<GameDevice.Acted.Sent>(game.send(EngineCombatScenario(h).cast(s, me, cast)))
        game.tick(4_000)
        // Resolve the Herald (Bob's pass assumed): its trigger goes on the Stack and Alice keeps priority in Full control
        game.act(Input.PassPriority(me))
        game.tick(5_000)
        assertEquals(GameDevice.Status.YourMove, game.device.status)
        val top = CombatStackRules.stack(game.play().board).cards.single()
        assertEquals(MtgStackKind.Triggered, game.play().board.stackEffects.single { it.id == top }.item?.kind)
        return game to h
    }

    // HIS-095, HIS-091, HIS-105 (owner playtest 2026-11-04 #1, #2): Bob's playback (its Full control on) stops at a pass
    // Alice assumed for him in her upkeep: his table looks like any point of his, with no words saying it was assumed
    @Test fun aStopAtAnAssumptionLooksLikeYourOwnPoint() {
        val bob = bobCatchingUp()
        bob.settings(org.asyncttwiz.protocol.game.PlaySettings(playbackFullControl = true))
        bob.startPlayback()
        playToStop(bob)
        val stop = assertIs<GameDevice.Status.Stopped>(bob.device.status)
        assertIs<Input.PassPriority>(stop.assumed)
        render(bob, f.b)
        compose.onNodeWithText("assumed", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Your decision", substring = true).assertDoesNotExist()
        compose.onNodeWithTag("engine-bar").assertDoesNotExist()
        captureShot("$SCREENSHOTS/engine/decision-stop.png",
            "Bob's playback (Full control on) stopped at Alice's upkeep, where she had assumed his pass: his table is live there with his main button, as at any point of his; nothing says what Alice assumed.")
    }

    // HIS-095 (owner 2026-11-05): Bob tapped the table while playback ran: it went on to Alice's upkeep, where Full control
    // would have stopped for him, and stopped there, live, with the centre text that resumes it
    @Test fun pausedPlaybackStopsWhereFullControlWould() {
        val bob = bobCatchingUp()
        bob.startPlayback()
        bob.pause()
        playToStop(bob)
        assertTrue((bob.device.status as GameDevice.Status.Stopped).paused)
        render(bob, f.b)
        compose.onNodeWithTag("playback-toggle").assertDoesNotExist()
        compose.onNodeWithContentDescription("Event strip").assertExists()
        compose.onNodeWithTag("main-button").assertExists()
        compose.onNodeWithContentDescription("Alice has made moves · Tap to watch").assertExists()
        captureShot("$SCREENSHOTS/engine/playback-paused.png",
            "Bob tapped the table during Alice's playback: it went on to her upkeep, where he could have acted, and stopped there; his table is live with the strip and his main button, and \"Alice has made moves · Tap to watch\" in the middle resumes it.")
        compose.onNodeWithTag("centre-action").performClick()
        compose.runOnIdle { assertTrue(bob.device.status.let { it !is GameDevice.Status.Stopped || !it.paused }, "the tap resumed it") }
    }

    // HIS-083, HIS-084 (I-D56 §10): holding Undo opens the rewind dialog; going back past what Bob saw asks him
    @Test fun rewindDialogPicksAnEarlierPoint() {
        val game = aliceAtMain()
        val pending = game.device.session.pending
        pending.forEachIndexed { n, event -> game.revision(f.revision(event, n + 2, mapOf(f.a to event.id, f.b to f.keep.id))) }
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

    // CMB-043 (I-D54 §8): Alice's attack staging is shared: Bob's table shows her staged Cub in the lane while she decides
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
