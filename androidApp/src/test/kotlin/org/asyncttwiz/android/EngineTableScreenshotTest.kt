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
                            val catchUp = status == GameDevice.Status.CatchUp
                            val playback = playing || catchUp || (status as? GameDevice.Status.Stopped)?.paused == true
                            TableSurface(summary, reviewed ?: game.play(), viewer, false, Modifier.fillMaxSize(), connected = reviewed == null,
                                onIntent = toolkit::handle, toolkit = toolkit,
                                runtime = TableRuntimeHooks(navigation = {}, chat = {}, undo = { routes.undo() },
                                    undoHold = { routes.holdUndo() }, controlsConnected = reviewed == null, opponentPresence = presence,
                                    reviewing = reviewed != null || playing || catchUp, liveView = reviewed == null && !playing && !catchUp,
                                    timelinePlayback = if (reviewed != null) TimelinePlaybackStatus.PAUSED else if (playing) TimelinePlaybackStatus.PLAYING else null,
                                    waitingLine = routes.waitingLine(game.play()),
                                    returnCurrent = if (reviewed != null) ({ routes.leaveReview() }) else null,
                                    liveTimeline = if (events.isEmpty()) null else ({ EngineStrip(events, viewer, false) { routes.enterReview(events.last().id) } }),
                                    bottomPanel = if (reviewed != null) ({ _ -> EngineReviewPanel(routes, events, viewer, false, 1_000) })
                                        else if (playback) ({ _ -> EnginePlaybackPanel(routes, events, viewer, false) }) else null))
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

    // TRN-059, TRN-068: the private toggles, and the unconfirmed mark at the step arrow's corner (no count)
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
            "Alice kept while Bob was away and is in her first main phase: a small violet dot at the step arrow's corner says Bob hasn't seen her last moves yet (no count, nothing over the board), the strip above the quick actions shows the events so far in each player's colour, and the quick actions are her own Response mode (Auto-skip), Always assume resolves and Autotap mana.")
    }

    // TRN-066, TRN-070, TRN-062, I-D56 §4, §14: Bob is online, so after Alice's keep the game waits for his; faint words over the
    // battlefield say so (nothing covers the board) and Assume keep takes the step arrow's place while he must act
    @Test fun waitingBarOffersAssumeKeep() {
        val game = EngineGame(f.game.id, f.a, f.base, f.snapshot())
        game.tick(live = true, now = 1_000)
        assertIs<GameDevice.Acted.Sent>(game.keep(emptyList()))
        game.tick(live = true, now = 2_000)
        val waiting = assertIs<GameDevice.Status.Waiting>(game.device.status)
        assertEquals(GameDevice.Offer.AssumeKeep, waiting.offer)
        render(game, f.a, ReceivedPresence(OpponentPresence(PresenceState.ONLINE, idleSeconds = 0), monotonicMillis()))
        compose.onNodeWithText("Bob is deciding on a mulligan").assertExists()
        compose.onNodeWithTag("engine-bar").assertDoesNotExist()
        compose.onNodeWithTag("engine-wait-action").assertExists()
        compose.onRoot().captureShot("$SCREENSHOTS/engine/waiting-bar.png",
            "Alice kept while Bob is online: faint words over the battlefield say Bob is deciding on a mulligan, nothing covers the board or her hand, and Assume keep stands where the step arrow would be.")
        // One tap assumes it: the game goes on to Alice's first main phase
        compose.onNodeWithTag("engine-wait-action").performClick()
        compose.runOnIdle { assertEquals(GameDevice.Status.YourMove, game.device.status) }
    }

    // HIS-033, HIS-094 (§29.1): coming back after the opening, Bob's playback of what Alice did starts paused: no dialog,
    // the strip and Play at the bottom; a tap on Play starts it
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
            alice.tick(false, sim.now)
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
        compose.onNodeWithTag("playback-toggle").assertExists()
        compose.onNodeWithTag("playback-toggle").assert(SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Play"))
        captureShot("$SCREENSHOTS/engine/catch-up.png",
            "Bob comes back after Alice played on past the opening: no dialog; his table shows where he left it, dimmed as in playback with \"Paused\" in the middle, and Play at the bottom; nothing plays until he taps it.")
        compose.onNodeWithTag("playback-toggle").performClick()
        compose.runOnIdle {
            assertTrue(bob.device.status.let { it is GameDevice.Status.Playing || it is GameDevice.Status.Stopped }, "Play started it")
        }
    }

    // HIS-021, CMB-007 (§29.6): Alice attacked while Bob was away; his playback shows the attack in the combat area, the
    // panel in the middle of the table, as in live combat
    @Test fun anAttackPlaysBackInTheCombatArea() {
        val scenario = EngineCombatScenario()
        scenario.until(scenario::bothHaveCubs)
        val alice = scenario.alice
        val attacker = scenario.creatures(alice.device.model.engine, scenario.me, ready = true).first()
        assertIs<GameDevice.Acted.Sent>(alice.send(listOf(Input.DeclareAttackers(scenario.me,
            listOf(arcanum.engine.input.Attack(attacker, TargetRef.Player(scenario.other)))))))
        alice.tick(false, scenario.sim.now)
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
            "Bob's playback of Alice's attack while he was away, stopped at his blocks: her Ember Cub attacks him in the combat area, the white panel in the middle of the table, as in live combat, with No blocks in the step arrow's place.")
    }

    /** Bob coming back after [aliceAtMain]: his device holds her events past his local head. */
    private fun bobCatchingUp(): EngineGame {
        val alice = aliceAtMain()
        val events = alice.device.session.pending
        val snapshot = EngineSnapshot(f.codec.encodeDefinitions(), f.snapshot().rootId, events.last().id,
            f.codec.encodeModel(alice.device.model), localHeads = mapOf(f.a to events.last().id, f.b to f.start.id),
            baseId = f.start.id, base = f.snapshot().state, events = events.map(f.codec::encodeEvent))
        return EngineGame(f.game.id, f.b, f.base, snapshot)
    }

    /** Plays [game] back until it stops. */
    private fun playToStop(game: EngineGame) {
        var guard = 0
        while (game.device.status is GameDevice.Status.Playing && guard++ < 100) game.step()
    }

    /** Alice at her main phase in [mode] casts Storm Spark at Bob ([live]: Bob is online). */
    private fun aliceCastsSpark(mode: org.asyncttwiz.protocol.game.ResponseMode, live: Boolean): EngineGame {
        val game = EngineGame(f.game.id, f.a, f.base, f.snapshot()).apply { tick(live, 1_000); keep(emptyList()); tick(live, 2_000) }
        if (live) { game.assume(); game.tick(live, 2_500) }
        game.settings(game.shown!!.settings.copy(mode = mode))
        val me = PlayerId(f.a)
        game.send(listOf(Input.PlayLand(me, hand(game, "Mountain"))))
        game.tick(live, 3_000)
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
        game.tick(live, 4_000)
        return game
    }

    // TRN-062 (owner playtest 2026-11-04 #3): the opening decision is only the quick actions Mulligan and Keep: nothing
    // covers the board
    @Test fun theOpeningHasOnlyTheQuickActions() {
        val game = EngineGame(f.game.id, f.a, f.base, f.snapshot())
        game.tick(false, 1_000)
        render(game, f.a)
        compose.onNodeWithText("Choose one").assertDoesNotExist()
        compose.onNodeWithContentDescription("Mulligan").assertExists()
        compose.onNodeWithContentDescription("Keep").assertExists()
        captureShot("$SCREENSHOTS/engine/opening.png",
            "A new game's opening: Alice's seven cards are in her hand and her quick actions are Mulligan and Keep; nothing covers the board or asks her anything else.")
    }

    // STK-029, STK-027 (owner playtest 2026-11-04 #5, #6): in Full control Alice keeps priority after casting Storm Spark:
    // it shows on the Stack with Resolve directly under it; no panel says she cast it, and the step arrow's place is empty
    @Test fun resolveSitsUnderTheStack() {
        val game = aliceCastsSpark(org.asyncttwiz.protocol.game.ResponseMode.FullControl, live = false)
        assertEquals(GameDevice.Status.YourMove, game.device.status)
        render(game, f.a)
        compose.onNodeWithTag("stack-resolve").assertExists()
        compose.onNodeWithText("You cast Storm Spark").assertDoesNotExist()
        compose.onNodeWithContentDescription("Respond prompt").assertDoesNotExist()
        captureShot("$SCREENSHOTS/engine/stack-resolve.png",
            "In Full control Alice cast Storm Spark at Bob and kept priority: the spell is on the Stack with Resolve directly under it, nothing covers the board and the step arrow's place is empty.")
        compose.onNodeWithTag("stack-resolve").performClick()
        compose.runOnIdle { assertTrue(game.device.model.engine.stack.isEmpty(), "she passed; Bob's pass was assumed and it resolved") }
    }

    // STK-028 (owner playtest 2026-11-04 #6): Bob is online, so after Alice's Storm Spark the game waits for him; Assume
    // sits under the Stack (not in the step arrow's place) and assumes he lets it resolve
    @Test fun assumeSitsUnderTheStack() {
        val game = aliceCastsSpark(org.asyncttwiz.protocol.game.ResponseMode.Normal, live = true)
        val waiting = assertIs<GameDevice.Status.Waiting>(game.device.status)
        assertEquals(GameDevice.Offer.AssumeResolves, waiting.offer)
        render(game, f.a, ReceivedPresence(OpponentPresence(PresenceState.ONLINE, idleSeconds = 0), monotonicMillis()))
        compose.onNodeWithTag("stack-assume").assertExists()
        compose.onNodeWithTag("engine-wait-action").assertDoesNotExist()
        captureShot("$SCREENSHOTS/engine/stack-assume.png",
            "Alice cast Storm Spark while Bob is online: faint words say Bob may respond to it, and Assume sits directly under the Stack, where tapping it goes on as if Bob let it resolve.")
        compose.onNodeWithTag("stack-assume").performClick()
        compose.runOnIdle { assertTrue(game.device.model.engine.stack.isEmpty(), "Storm Spark resolved on the assumed pass") }
    }

    // STK-046 (owner playtest 2026-11-04): in Full control Alice keeps priority after her Ember Herald resolved: its enters
    // trigger is on the Stack as an ability token (the Herald's art, the trigger marker, its name and text), not as the
    // Herald card; the Stack list shows it as "Ember Herald's ability" with its text
    @Test fun anAbilityOnTheStackIsDrawnAsAnAbility() {
        val (game, h) = heraldTriggerOnStack()
        render(game, h.a)
        compose.onNodeWithTag("stack-resolve").assertExists()
        compose.onNodeWithContentDescription("Triggered ability, Ember Herald — When this creature enters", substring = true).assertExists()
        captureShot("$SCREENSHOTS/engine/stack-ability.png",
            "In Full control Alice's Ember Herald resolved and its enters trigger is on the Stack: it is drawn as a small dark ability token with the Herald's art, a \"!\" trigger marker, the Herald's name and the trigger's text, not as the Herald card, with Resolve under it.")
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
        val game = EngineGame(h.game.id, h.a, h.base, h.snapshot()).apply { tick(false, 1_000); keep(emptyList()); tick(false, 2_000) }
        game.settings(game.shown!!.settings.copy(mode = org.asyncttwiz.protocol.game.ResponseMode.FullControl))
        fun named(name: String) = game.device.model.engine.let { s -> s.player(me).hand.first { s.obj(it)?.definition?.faces?.first()?.name == name } }
        game.send(listOf(Input.PlayLand(me, named("Mountain"))))
        game.tick(false, 3_000)
        val s = game.device.model.engine
        val cast = Engine.options(s, me).filterIsInstance<Input.Cast>().first { s.obj(it.card)?.definition?.faces?.first()?.name == "Ember Herald" }
        assertIs<GameDevice.Acted.Sent>(game.send(EngineCombatScenario(h).cast(s, me, cast)))
        game.tick(false, 4_000)
        // Resolve the Herald (Bob's pass assumed): its trigger goes on the Stack and Alice keeps priority in Full control
        game.act(Input.PassPriority(me))
        game.tick(false, 5_000)
        assertEquals(GameDevice.Status.YourMove, game.device.status)
        val top = CombatStackRules.stack(game.play().board).cards.single()
        assertEquals(MtgStackKind.Triggered, game.play().board.stackEffects.single { it.id == top }.item?.kind)
        return game to h
    }

    // HIS-095, HIS-091 (owner playtest 2026-11-04 #1, #2): Bob's playback stops at the keep Alice assumed for him: his
    // table looks like any opening of his, with no words saying it was assumed
    @Test fun aStopAtAnAssumptionLooksLikeYourOwnPoint() {
        val bob = bobCatchingUp()
        bob.startPlayback()
        playToStop(bob)
        assertIs<GameDevice.Status.Stopped>(bob.device.status)
        render(bob, f.b)
        compose.onNodeWithText("assumed", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Your decision", substring = true).assertDoesNotExist()
        compose.onNodeWithTag("engine-bar").assertDoesNotExist()
        compose.onNodeWithContentDescription("Keep").assertExists()
        captureShot("$SCREENSHOTS/engine/decision-stop.png",
            "Bob's playback stopped at his own opening, which Alice had played on from: his hand and the quick actions Mulligan and Keep, as in any opening; nothing says what Alice assumed.")
    }

    // HIS-103 (§27): Bob paused playback: it went on to Alice's upkeep, where Full control would have stopped for him, and
    // stopped there with the strip and Play at the bottom; holding Play opens the response mode dialog
    @Test fun pausedPlaybackStopsWhereFullControlWould() {
        val bob = bobCatchingUp()
        bob.startPlayback()
        playToStop(bob)
        bob.act((bob.device.status as GameDevice.Status.Stopped).assumed)
        bob.pause(true)
        playToStop(bob)
        assertTrue((bob.device.status as GameDevice.Status.Stopped).paused)
        render(bob, f.b)
        compose.onNodeWithTag("playback-toggle").assertExists()
        compose.onNodeWithContentDescription("Event strip").assertExists()
        captureShot("$SCREENSHOTS/engine/playback-paused.png",
            "Bob paused Alice's playback: it went on to her upkeep, where he could have acted, and stopped there; the event strip shows what he has seen and the button where Auto was reads Play, with holding it for the response mode.")
        compose.onNodeWithTag("playback-toggle").performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithText("Where playback and your device stop for you.").assertExists()
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

    // CMB-040, CMB-049 (I-D56 §4, §14): the Assume blockers screen while Bob is away: Alice edits the blocks before assuming them
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
