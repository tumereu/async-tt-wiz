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
 * wait's band with Assume in the step arrow's place, the catch-up screen, the rewind dialog and the incoming undo request with the board
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
     * mode), the change flashes. */
    private fun render(game: EngineGame, viewer: String, presence: ReceivedPresence? = null,
        routes: EngineTableRoutes = EngineTableRoutes(viewer, { game }, {}, { game.play() }), summary: GameSummary = f.game,
        landscape: Boolean = false, onToolkit: (TableToolkitController) -> Unit = {}): EngineTableRoutes {
        routes.opponentName = names.entries.first { it.key != viewer }.value
        val toolkit = TableToolkitController(viewer, { game.play() }, { routes.canAct() }, { action, _ -> routes.local(listOf(action)) },
            playBatch = { actions, _ -> routes.local(actions) }, engine = routes)
        onToolkit(toolkit)
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
                            TableSurface(summary, reviewed ?: game.play(), viewer, landscape, Modifier.fillMaxSize(), connected = reviewed == null,
                                onIntent = toolkit::handle, toolkit = toolkit,
                                runtime = TableRuntimeHooks(navigation = {}, chat = {}, undo = { routes.undo() },
                                    undoHold = { routes.holdUndo() }, controlsConnected = reviewed == null, opponentPresence = presence,
                                    reviewing = reviewed != null, liveView = reviewed == null,
                                    timelinePlayback = if (reviewed != null) TimelinePlaybackStatus.PAUSED else null,
                                    waitingLine = routes.waitingLine(game.play()),
                                    returnCurrent = if (reviewed != null) ({ routes.leaveReview() }) else null,
                                    bottomPanel = if (reviewed != null) ({ _ -> EngineReviewPanel(routes, events, viewer, landscape, 1_000) }) else null,
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

    /** [aliceAtMain] with a Mountain played: her keep is no longer the newest event. */
    private fun aliceAfterALand(): EngineGame = aliceAtMain().apply {
        assertTrue(act(Input.PlayLand(PlayerId(f.a), hand(this, "Mountain"))) !is GameDevice.Acted.Refused)
        tick(3_000)
    }

    private fun hand(game: EngineGame, name: String): ObjectId = game.device.model.engine.let { s ->
        s.player(PlayerId(f.a)).hand.first { s.obj(it)?.definition?.faces?.first()?.name == name }
    }

    // TRN-059, TRN-068, TRN-016, TRN-017: the private toggles, and the unconfirmed mark at the main button's corner (no
    // count); the main button is 112 dp wide and the three toggles share the rest of the row equally
    @Test fun assumedAnswersAndPrivateToggles() {
        val game = aliceAtMain()
        val routes = EngineTableRoutes(f.a, { game }, {}, { game.play() }).apply { opponentName = "Bob" }
        render(game, f.a, routes = routes)
        compose.onNodeWithTag("engine-unconfirmed").assertExists()
        compose.onNodeWithContentDescription("Bob hasn't seen your last moves yet", substring = true).assertExists()
        // HIS-001: no event strip in play mode
        compose.onNodeWithContentDescription("Event strip").assertDoesNotExist()
        compose.onNodeWithContentDescription("Timeline mode").assertDoesNotExist()
        compose.onNodeWithContentDescription("Response mode", substring = true).assertExists()
        compose.onNodeWithContentDescription("Assume resolves setting").assertExists()
        // TRN-017 (owner playtest 2026-11-06): one width for the main button, the rest shared equally (mdpi: px = dp)
        assertEquals(112f, compose.onNodeWithContentDescription("To combat").fetchSemanticsNode().boundsInRoot.width, .5f)
        val cells = listOf("Response mode", "Assume resolves setting", "Autotap mana")
            .map { compose.onNodeWithContentDescription(it).fetchSemanticsNode().boundsInRoot.width }
        assertTrue(cells.all { kotlin.math.abs(it - cells.first()) <= 1.5f } && cells.first() > 48f, "$cells")
        compose.onRoot().captureShot("$SCREENSHOTS/engine/assumed-main.png",
            "Alice kept while Bob was away and is in her first main phase: a small violet dot at the corner of the main button (To combat, 112 dp wide) says Bob hasn't seen her last moves yet (no count, nothing over the board), no event strip, and the quick actions are her own Response mode (Quick), Assume resolves (Always, as the fixture sets it) and Autotap mana.")
    }

    /** Alice at her main phase with "Assume resolves" set to [choice]: the quick action shows [state] and is captured as [file]. */
    private fun assumeCell(choice: org.asyncttwiz.protocol.game.AssumeResolves, state: String, file: String, caption: String) {
        val game = aliceAtMain()
        game.settings(game.shown!!.settings.assuming(choice))
        val routes = EngineTableRoutes(f.a, { game }, {}, { game.play() }).apply { opponentName = "Bob" }
        render(game, f.a, routes = routes)
        val cell = compose.onNodeWithContentDescription("Assume resolves setting")
        cell.assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, state))
        cell.captureShot("$SCREENSHOTS/engine/$file.png", "Alice's Assume resolves quick action set to $state: $caption")
    }

    // TRN-059, HIS-096 (owner playtest 2026-11-05 evening, 2026-11-06): the Assume resolves quick action shows its choice
    // of Never, Normal, When they have no mana and Always, filled deep blue, violet or amber as the response mode that
    // keeps as much control
    @Test fun theAssumeQuickActionShowsNever() = assumeCell(org.asyncttwiz.protocol.game.AssumeResolves.Never, "Never",
        "assume-never", "filled deep blue with white words, it reads \"Assume: Never\": her device waits wherever Bob gets priority.")

    // TRN-059, HIS-096
    @Test fun theAssumeQuickActionShowsNormal() = assumeCell(org.asyncttwiz.protocol.game.AssumeResolves.Normal, "Normal",
        "assume-normal", "filled violet with white words, it reads \"Assume: Normal\": her device goes on where Normal mode would and waits at Bob's pauses.")

    // TRN-059, HIS-096
    @Test fun theAssumeQuickActionShowsNoMana() = assumeCell(org.asyncttwiz.protocol.game.AssumeResolves.WhenNoMana,
        "When they have no mana", "assume-no-mana",
        "filled amber with white words, it reads \"Assume: No mana\": her device also goes on at Bob's pauses while the table shows him no mana.")

    // TRN-059, HIS-096
    @Test fun theAssumeQuickActionShowsAlways() = assumeCell(org.asyncttwiz.protocol.game.AssumeResolves.Always, "Always",
        "assume-always", "filled red-orange with white words, it reads \"Assume: Always\": her device goes on wherever Bob gets priority after something she did.")

    /** Alice at her main phase in response mode [mode]: the quick action shows its name and is captured as [file]. */
    private fun modeCell(mode: org.asyncttwiz.protocol.game.ResponseMode, file: String, caption: String) {
        val game = aliceAtMain()
        game.settings(game.shown!!.settings.copy(mode = mode))
        val routes = EngineTableRoutes(f.a, { game }, {}, { game.play() }).apply { opponentName = "Bob" }
        render(game, f.a, routes = routes)
        val cell = compose.onNodeWithContentDescription("Response mode")
        cell.assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, mode.label()))
        cell.captureShot("$SCREENSHOTS/engine/$file.png", "Alice's response mode quick action set to ${mode.label()}: $caption")
    }

    // TRN-059 (owner playtest 2026-11-06): the response mode quick action reads Control, Normal or Quick, filled deep blue,
    // violet or amber by how much control she keeps
    @Test fun theModeQuickActionShowsControl() = modeCell(org.asyncttwiz.protocol.game.ResponseMode.FullControl, "mode-control",
        "filled deep blue with a white icon and \"Control\": her device stops wherever she gets priority.")

    // TRN-059
    @Test fun theModeQuickActionShowsNormal() = modeCell(org.asyncttwiz.protocol.game.ResponseMode.Normal, "mode-normal",
        "filled violet with a white icon and \"Normal\": her device stops at her stops and pauses.")

    // TRN-059
    @Test fun theModeQuickActionShowsQuick() = modeCell(org.asyncttwiz.protocol.game.ResponseMode.AutoSkip, "mode-quick",
        "filled amber with a white icon and \"Quick\": her device also goes on where she can't respond.")

    // TRN-063 (owner, 2026-11-06): an own action whose result arrives at once looks as before: no working sign, frame by
    // frame on the test clock
    @Test fun anInstantActionShowsNoWorkingSign() {
        val game = aliceAtMain()
        render(game, f.a)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("To combat").performClick()
        repeat(40) {
            compose.mainClock.advanceTimeBy(16)
            compose.onNodeWithTag("main-button-working").assertDoesNotExist()
        }
        assertTrue(game.shown!!.engine.turn.step != arcanum.api.TurnStep.PrecombatMain, "the action was made")
    }

    // TRN-063 (owner, 2026-11-06): an own action still being worked out 150 ms after the press shows the working sign on the
    // main button until its result arrives, at least 300 ms
    @Test fun aSlowActionShowsTheWorkingSign() {
        val game = aliceAtMain()
        // The main button's step is worked out off the engine game for a while (its result not there yet)
        val slow = androidx.compose.runtime.mutableStateOf(false)
        render(game, f.a, routes = EngineTableRoutes(f.a, { game }, {}, { game.play() }).apply { working = { slow.value } })
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { slow.value = true }; compose.waitForIdle()
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithTag("main-button-working").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithTag("main-button-working").assertExists()
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithTag("main-button").captureShot("$SCREENSHOTS/engine/main-button-working.png",
            "Alice's main button (To combat) while her action is still being worked out 150 ms after she tapped it: a soft " +
                "violet band sweeps across it and a thin violet line runs along its bottom, until the result shows.")
        // The result arrives: the sign has shown over 300 ms, so it goes at once
        compose.runOnIdle { slow.value = false }; compose.waitForIdle()
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("main-button-working").assertDoesNotExist()
        // A result that arrives just after the sign showed: it stays its least 300 ms
        compose.runOnIdle { slow.value = true }; compose.waitForIdle()
        compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithTag("main-button-working").assertExists()
        compose.runOnIdle { slow.value = false }; compose.waitForIdle()
        compose.mainClock.advanceTimeBy(150)
        compose.onNodeWithTag("main-button-working").assertExists()
        compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithTag("main-button-working").assertDoesNotExist()
    }

    // TRN-066, TRN-070, TRN-062, TRN-027, I-D56 §4, §14: after Alice's keep the game waits for Bob's; the band under his hand
    // says so and nothing stands in the step arrow's place: his keep is never assumed
    @Test fun waitingBarWaitsForTheOtherKeep() {
        val game = EngineGame(f.game.id, f.a, f.base, f.snapshot())
        game.tick(1_000)
        assertIs<GameDevice.Acted.Sent>(game.keep(emptyList()))
        game.tick(2_000)
        assertEquals(GameDevice.Status.Waiting(GameDevice.Wait.Keep), game.device.status)
        render(game, f.a, ReceivedPresence(OpponentPresence(PresenceState.ONLINE, idleSeconds = 0), monotonicMillis()))
        compose.onNodeWithText("Bob is deciding on a mulligan").assertExists()
        compose.onNodeWithTag("wait-band").assertExists()
        compose.onNodeWithTag("engine-bar").assertDoesNotExist()
        compose.onNodeWithTag("engine-wait-action").assertDoesNotExist()
        compose.onRoot().captureShot("$SCREENSHOTS/engine/waiting-bar.png",
            "Alice kept while Bob is online: a dark band under his hand says Bob is deciding on a mulligan, in small words, nothing covers her hand, and the main button's place is empty (his keep is never assumed).")
    }

    // TRN-066 (owner, 2026-11-09): in landscape the band lies under the other player's hand as well, and takes no touch: a tap
    // through it reaches the table
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun theWaitBandInLandscape() {
        val game = EngineGame(f.game.id, f.a, f.base, f.snapshot())
        game.tick(1_000)
        assertIs<GameDevice.Acted.Sent>(game.keep(emptyList()))
        game.tick(2_000)
        render(game, f.a, ReceivedPresence(OpponentPresence(PresenceState.ONLINE, idleSeconds = 0), monotonicMillis()))
        val band = compose.onNodeWithTag("wait-band").assertExists()
        assertTrue(androidx.compose.ui.semantics.SemanticsActions.OnClick !in band.fetchSemanticsNode().config, "no touch taken")
        compose.onRoot().captureShot("$SCREENSHOTS/engine/waiting-band-landscape.png",
            "In landscape, Alice kept while Bob is online: the same dark band under his hand says Bob is deciding on a mulligan.")
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
        compose.onNodeWithContentDescription("Response mode").assertExists()
        compose.onNodeWithContentDescription("Assume resolves setting").assertDoesNotExist()
        captureShot("$SCREENSHOTS/engine/catch-up.png",
            "Bob comes back after Alice played on past the opening: no dialog and no timeline look; his table shows where he left it, as in live play, with \"Alice has made moves · Tap to watch\" in the middle of the board, and his playback quick actions: the response mode cell as in live play (Quick, amber) and Autotap mana; nothing plays until he taps the text.")
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

    /** Alice at her main phase in [mode] casts Storm Spark at Bob ([alwaysAssume]: "Always assume resolves"). Bob is in
     * Normal (HIS-096: in Quick, with nothing to respond with, his own mode would pass and her phone would assume it). */
    private fun aliceCastsSpark(mode: org.asyncttwiz.protocol.game.ResponseMode, alwaysAssume: Boolean): EngineGame {
        val game = EngineGame(f.game.id, f.a, f.base, f.snapshot(otherKept = true)).apply { tick(1_000); keep(emptyList()); tick(2_000) }
        game.device.modes(mapOf(f.b to org.asyncttwiz.protocol.game.ResponseMode.Normal))
        game.settings(game.shown!!.settings.copy(mode = mode, assume = if (alwaysAssume) org.asyncttwiz.protocol.game.AssumeResolves.Always else org.asyncttwiz.protocol.game.AssumeResolves.Normal))
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
                else if (d is Decision.Targets) Input.Answer(d.id, Answer.Targets(listOf(arcanum.engine.state.TargetRef.Player(PlayerId(f.b)))))
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

    // STK-029, STK-027, TRN-063 (owner 2026-11-05): in the Control response mode Alice keeps priority after casting Storm Spark: it
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
            "In the Control response mode Alice cast Storm Spark at Bob and kept priority: the spell is on the Stack with a green arrow to Bob's life square, nothing covers the board or sits under the Stack, and the main button in the bottom-right corner, 112 dp wide, reads Resolve.")
        compose.onNodeWithTag("main-button").performClick()
        compose.runOnIdle { assertTrue(game.device.model.engine.stack.isEmpty(), "she passed; Bob's pass was assumed and it resolved") }
    }

    // ZON-093 (owner 2026-11-07, 2026-11-08): your own Library's menu has View deck; its panel lists your deck in type
    // groups at the Panels card size, All or Still in library (the seven cards in your opening hand left out), closed by
    // a borderless X
    @Test fun viewDeckListsYourDeck() {
        val game = EngineGame(f.game.id, f.a, f.base, f.snapshot())
        game.tick(1_000)
        var toolkit: TableToolkitController? = null
        render(game, f.a, onToolkit = { toolkit = it })
        val library = game.play().board.containers.single { it.zone == PlayZone.LIBRARY && it.accountId == f.a }
        compose.runOnIdle { requireNotNull(toolkit).openLibrary(library.id) }
        // past the panels' guard against the opening tap's tail
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithContentDescription("View deck").assertIsEnabled().performClick()
        compose.mainClock.advanceTimeBy(600)
        assertEquals("deck", toolkit?.panel)
        val deck = DeckView.deck(game.play(), f.a).sumOf { it.second }
        compose.onNodeWithText("All · $deck").assertExists()
        compose.onNodeWithText("Still in library · ${deck - 7}").performClick()
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithTag("deck-view-library").assertIsSelected()
        compose.onNode(hasContentDescription("Close") and hasClickAction()).assertExists()
        captureShot("$SCREENSHOTS/engine/view-deck.png",
            "At the opening Alice opened her own Library's menu and tapped View deck, then Still in library: her deck's cards without the seven in her hand, in type groups as in the deck views, at the Panels card size with copies counted on each card, and a borderless X at the top right that closes the panel.")
    }

    // STK-028, TRN-070, HIS-096 (owner 2026-11-05): with "Assume resolves" Normal, after Alice's Storm Spark the game
    // waits for Bob (away or online); "Assume resolves" is the main button and assumes he lets it resolve
    @Test fun assumeIsTheMainButton() {
        val game = aliceCastsSpark(org.asyncttwiz.protocol.game.ResponseMode.Normal, alwaysAssume = false)
        val waiting = assertIs<GameDevice.Status.Waiting>(game.device.status)
        assertEquals(GameDevice.Offer.AssumeResolves, waiting.offer)
        render(game, f.a, ReceivedPresence(OpponentPresence(PresenceState.OFFLINE, idleSeconds = 0), monotonicMillis()))
        compose.onNodeWithTag("engine-wait-action").assert(hasContentDescription("Assume resolves"))
        compose.onNodeWithTag("stack-assume").assertDoesNotExist()
        captureShot("$SCREENSHOTS/engine/stack-assume.png",
            "Alice cast Storm Spark at Bob while he is away and Assume resolves is Normal: a green arrow runs from the Stack to his life square, the band under his hand says Bob may respond to it, and the main button reads Assume resolves, where tapping it goes on as if Bob let it resolve.")
        compose.onNodeWithTag("engine-wait-action").performClick()
        compose.runOnIdle { assertTrue(game.device.model.engine.stack.isEmpty(), "Storm Spark resolved on the assumed pass") }
    }

    // TRN-067, TRN-070, TRN-071 (owner gallery review, 2026-11-06): Alice casts Ember Glance ("Look at target player's
    // hand.") at Bob; assuming his pass would show her his hidden hand, so the wait's Assume is greyed: a tap does nothing,
    // holding it says why
    @Test fun assumeIsGreyedWhereItWouldShowHiddenCards() {
        val glance = EngineGameFixture(aliceCards = listOf("Mountain", EngineGameFixture.Choices.GLANCE))
        val game = EngineGame(glance.game.id, glance.a, glance.base, glance.snapshot(otherKept = true)).apply { tick(1_000); keep(emptyList()); tick(2_000) }
        val me = PlayerId(glance.a)
        val card = game.device.model.engine.let { s -> s.player(me).hand.first { s.obj(it)?.definition?.faces?.first()?.name == EngineGameFixture.Choices.GLANCE } }
        val copy = game.device.model.engine.deepCopy()
        val inputs = mutableListOf<Input>(Input.Cast(me, card))
        var next: Input = inputs.first()
        while (true) {
            Engine.perform(copy, next).changes.forEach(copy::apply)
            val d = copy.pending?.decision ?: break
            if (d is Decision.Random || Engine.decider(copy, d.player) != me) break
            next = if (d is Decision.Targets) Input.Answer(d.id, Answer.Targets(listOf(arcanum.engine.state.TargetRef.Player(PlayerId(glance.b)))))
                else Engine.options(copy, me).filterIsInstance<Input.Answer>().first { it.decision == d.id }
            inputs += next
        }
        assertIs<GameDevice.Acted.Sent>(game.send(inputs))
        game.tick(4_000)
        val waiting = assertIs<GameDevice.Status.Waiting>(game.device.status)
        assertEquals(GameDevice.Wait.HiddenCards, waiting.reason)
        render(game, glance.a, ReceivedPresence(OpponentPresence(PresenceState.OFFLINE, idleSeconds = 0), monotonicMillis()))
        compose.onNodeWithTag("engine-wait-action").assert(hasContentDescription("Assume resolves")).assertIsNotEnabled()
        compose.onNodeWithTag("engine-wait-action").performClick()
        compose.runOnIdle { assertEquals(1, game.device.model.engine.stack.size, "nothing assumed: the Glance waits on the Stack") }
        compose.onNodeWithTag("engine-wait-action").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        compose.onNodeWithText("Assuming this would show you hidden cards or a random result: wait for Bob.").assertExists()
        captureShot("$SCREENSHOTS/engine/assume-greyed.png",
            "Alice cast Ember Glance at Bob, who is away (a green arrow runs from the Stack to his life square): assuming his pass would show her his hidden hand, so the band under his hand says she waits for Bob because going on would show hidden cards, and the main button's Assume resolves is greyed; held, it says why.")
        compose.onNodeWithTag("engine-wait-action").performTouchInput { up() }
    }

    // STK-046 (owner playtest 2026-11-04): in the Control response mode Alice keeps priority after her Ember Herald resolved: its enters
    // trigger is on the Stack as an ability token (the Herald's art and a "T" chip, no text), not as the Herald card; the
    // Stack list shows it as "Ember Herald's ability" with its text
    @Test fun anAbilityOnTheStackIsDrawnAsAnAbility() {
        val (game, h) = heraldTriggerOnStack()
        render(game, h.a)
        compose.onNodeWithTag("main-button").assert(hasContentDescription("Resolve"))
        compose.onNodeWithContentDescription("Triggered ability, Ember Herald — When this creature enters", substring = true).assertExists()
        captureShot("$SCREENSHOTS/engine/stack-ability.png",
            "In the Control response mode Alice's Ember Herald resolved and its enters trigger is on the Stack: it is drawn as a small dark ability token filled with the Herald's art and a \"T\" chip at its bottom, with no text, not as the Herald card, and the main button reads Resolve.")
        compose.onNodeWithContentDescription("Stack, 1 entries").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Top · Ember Herald's ability").assertExists()
        captureShot("$SCREENSHOTS/engine/stack-ability-list.png",
            "Alice tapped the Stack holding her Ember Herald's enters trigger: the list shows it as the ability token with its \"T\" chip, \"Top · Ember Herald's ability\", \"Triggered ability\" in violet and the trigger's text.")
    }

    // STK-050, TBL-104 (owner playtest 2026-11-06): holding an ability's entry on the Stack shows a dark rounded panel,
    // not card-shaped (shorter than a card of its width), with the source's art crop and under it the source's name and
    // the ability's text, and nothing else: no source card picture after it
    @Test fun holdingAnAbilityOnTheStackShowsOnlyItsTile() {
        val (game, h) = heraldTriggerOnStack()
        render(game, h.a)
        compose.onNodeWithContentDescription("Stack, 1 entries").performClick()
        compose.waitForIdle()
        val row = compose.onNodeWithText("Top · Ember Herald's ability")
        // A press right after a popup opens is ignored (CardRingInputGuard): hold once that has passed
        compose.mainClock.advanceTimeBy(400)
        compose.mainClock.autoAdvance = false
        row.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(600)
        val tile = compose.onNodeWithContentDescription("Triggered ability of Ember Herald").assertIsDisplayed().getBoundsInRoot()
        compose.onNode(hasText("When this creature enters", substring = true) and
            hasAnyAncestor(hasContentDescription("Triggered ability of Ember Herald"))).assertExists()
        compose.onNodeWithContentDescription("Art of Ember Herald").assertExists()
        compose.onNodeWithContentDescription("Card image: Ember Herald").assertDoesNotExist()
        assertTrue(tile.bottom - tile.top < (tile.right - tile.left) * 1.3f,
            "The panel is shorter than a card of its width: not card-shaped (${tile.right - tile.left} × ${tile.bottom - tile.top})")
        compose.waitForIdle(); compose.mainClock.advanceTimeBy(100)
        captureShot("$SCREENSHOTS/engine/stack-ability-inspect.png",
            "Alice holds her Ember Herald's enters trigger in the Stack list: Inspect shows only a dark rounded panel, not card-shaped, with the Herald's art crop on top and under it the \"T\" chip, \"Triggered ability\", the Herald's name and the trigger's text; no card image of the Herald.")
        row.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.onNodeWithContentDescription("Card inspection content").assertDoesNotExist()
    }

    /** Alice in Control with her Ember Herald's enters trigger on the Stack, holding priority. */
    private fun heraldTriggerOnStack(): Pair<EngineGame, EngineGameFixture> {
        val h = EngineGameFixture(heralds = true)
        val me = PlayerId(h.a)
        val game = EngineGame(h.game.id, h.a, h.base, h.snapshot(otherKept = true)).apply { tick(1_000); keep(emptyList()); tick(2_000) }
        game.settings(game.shown!!.settings.copy(mode = org.asyncttwiz.protocol.game.ResponseMode.FullControl, assume = org.asyncttwiz.protocol.game.AssumeResolves.Always))
        fun named(name: String) = game.device.model.engine.let { s -> s.player(me).hand.first { s.obj(it)?.definition?.faces?.first()?.name == name } }
        game.send(listOf(Input.PlayLand(me, named("Mountain"))))
        game.tick(3_000)
        val s = game.device.model.engine
        val cast = Engine.options(s, me).filterIsInstance<Input.Cast>().first { s.obj(it.card)?.definition?.faces?.first()?.name == "Ember Herald" }
        assertIs<GameDevice.Acted.Sent>(game.send(EngineCombatScenario(h).cast(s, me, cast)))
        game.tick(4_000)
        // Resolve the Herald (Bob's pass assumed): its trigger goes on the Stack and Alice keeps priority in Control
        game.act(Input.PassPriority(me))
        game.tick(5_000)
        assertEquals(GameDevice.Status.YourMove, game.device.status)
        val top = CombatStackRules.stack(game.play().board).cards.single()
        assertEquals(MtgStackKind.Triggered, game.play().board.stackEffects.single { it.id == top }.item?.kind)
        return game to h
    }

    // HIS-095, HIS-091, HIS-105 (owner playtest 2026-11-04 #1, #2): Bob's playback (his mode Control) stops at a pass
    // Alice assumed for him in her upkeep: his table looks like any point of his, with no words saying it was assumed
    @Test fun aStopAtAnAssumptionLooksLikeYourOwnPoint() {
        val bob = bobCatchingUp()
        bob.settings(org.asyncttwiz.protocol.game.PlaySettings(mode = org.asyncttwiz.protocol.game.ResponseMode.FullControl))
        bob.startPlayback()
        playToStop(bob)
        val stop = assertIs<GameDevice.Status.Stopped>(bob.device.status)
        assertIs<Input.PassPriority>(stop.assumed)
        render(bob, f.b)
        compose.onNodeWithText("assumed", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Your decision", substring = true).assertDoesNotExist()
        compose.onNodeWithTag("engine-bar").assertDoesNotExist()
        captureShot("$SCREENSHOTS/engine/decision-stop.png",
            "Bob's playback (his response mode Control) stopped at Alice's upkeep, where she had assumed his pass: his table is live there with his main button, as at any point of his; nothing says what Alice assumed.")
    }

    // HIS-095 (owner 2026-11-05): Bob tapped the table while playback ran: it went on to Alice's upkeep, where Control
    // would have stopped for him, and stopped there, live, with the centre text that resumes it
    @Test fun pausedPlaybackStopsWhereFullControlWould() {
        val bob = bobCatchingUp()
        bob.startPlayback()
        bob.pause()
        playToStop(bob)
        assertTrue((bob.device.status as GameDevice.Status.Stopped).paused)
        render(bob, f.b)
        compose.onNodeWithTag("playback-toggle").assertDoesNotExist()
        // HIS-001: playback has no event strip either
        compose.onNodeWithContentDescription("Event strip").assertDoesNotExist()
        compose.onNodeWithTag("main-button").assertExists()
        compose.onNodeWithContentDescription("Alice has made moves · Tap to watch").assertExists()
        captureShot("$SCREENSHOTS/engine/playback-paused.png",
            "Bob tapped the table during Alice's playback: it went on to her upkeep, where he could have acted, and stopped there; his table is live with his main button, and \"Alice has made moves · Tap to watch\" in the middle resumes it.")
        compose.onNodeWithTag("centre-action").performClick()
        compose.runOnIdle { assertTrue(bob.device.status.let { it !is GameDevice.Status.Stopped || !it.paused }, "the tap resumed it") }
    }

    /** Alice and Bob a few turns into the creatures game (a Mountain and an Ember Cub each turn), Alice declaring
     * attackers: her table, with [open] done on it. */
    private fun aliceFewTurnsIn(landscape: Boolean = false, open: (EngineTableRoutes) -> Unit): EngineGame {
        val scenario = EngineCombatScenario()
        scenario.until(scenario::bothHaveCubs)
        val alice = scenario.alice
        alice.notes.clear() // the scripted setup's own races (SYS-012 notes) aren't part of these pictures
        val routes = render(alice, f.a, summary = f.game, landscape = landscape)
        compose.runOnIdle { open(routes) }
        compose.waitForIdle()
        return alice
    }

    /** HIS-099: the Undo dialog held open on [game]: its points, and the one it opened at. */
    private fun undoDialogPoints(game: EngineGame): Pair<List<UndoPoint>, UndoPoint> {
        val points = undoPoints(game.shown!!, game.timeline.events(game, names))
        assertTrue(points.size >= 6, "several points: ${points.map { it.shown.description }}")
        return points to points.last { it.target == game.shown!!.undoTarget }
    }

    // HIS-083, HIS-099 (owner 2026-11-06): holding Undo opens the Undo dialog at the point before her latest action: the
    // board there on top, under it the timeline's strip of the points (both players' events, with the turn and step
    // lines) with the chosen one in the centre and its text; Bob has acted since, so Undo asks him
    private fun undoDialog(landscape: Boolean, file: String) {
        val game = aliceFewTurnsIn(landscape) { it.holdUndo() }
        val (points, chosen) = undoDialogPoints(game)
        assertTrue(points.any { it.shown.author == f.b } && points.any { it.shown.author == f.a }, "both players' events")
        compose.onNodeWithTag("undo-point").assertTextEquals(chosen.shown.description)
        compose.onNodeWithTag("undo-board").assertExists()
        compose.onNodeWithTag("undo-scroller").assertExists()
        assertTrue(compose.onAllNodesWithTag(TimelineSeparatorTag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty(), "turn and step lines")
        compose.onNodeWithText("Ask Bob").assertExists()
        captureShot("$SCREENSHOTS/engine/$file.png",
            "Alice, a few turns in, holds Undo: the Undo dialog opens at the point just before her latest action (\"${chosen.shown.description}\"), with the board there on top, under it the timeline's strip of both players' events with their turn and step lines and that point in the centre, its text, and Ask Bob with a message field, as Bob has acted since.")
    }

    // HIS-083, HIS-099
    @Test fun undoDialogShowsTheBoardAboveTheTimelineStrip() = undoDialog(false, "rewind-dialog")

    // HIS-083, HIS-099
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun undoDialogInLandscape() = undoDialog(true, "rewind-dialog-landscape")

    // HIS-099 (owner 2026-11-06): dragging the strip right chooses earlier points one event per step (snapping to them),
    // the text follows at once and the board once the choice rests; tapping an event chooses it
    @Test fun draggingTheUndoStripChoosesAnEarlierPoint() {
        val game = aliceFewTurnsIn { it.holdUndo() }
        val (points, chosen) = undoDialogPoints(game)
        val at = points.indexOf(chosen)
        assertTrue(at >= 2)
        compose.onNodeWithTag("undo-scroller").performTouchInput { swipe(center, center + androidx.compose.ui.geometry.Offset(56f, 0f), 300) }
        compose.waitForIdle()
        compose.onNodeWithTag("undo-point").assertTextEquals(points[at - 2].shown.description)
        // The main button waits for the new point's board, worked out once the choice rests
        compose.onNodeWithTag("rewind-confirm").assertIsNotEnabled()
        compose.mainClock.advanceTimeBy(UNDO_PREVIEW_DELAY_MS + 100)
        compose.waitForIdle()
        compose.onNodeWithTag("rewind-confirm").assertIsEnabled()
        // (chips are drawn in event order: the last chip with its text is the newest point's)
        compose.onAllNodes(hasContentDescription(points.last().shown.description) and hasClickAction(), useUnmergedTree = true).onLast().performClick()
        compose.onNodeWithTag("undo-point").assertTextEquals(points.last().shown.description)
    }

    // HIS-005 (owner 2026-11-06): timeline mode's strip shows a labelled line where the turn or step changed between two
    // events ("T3 · Main 1"), the newest change when several fell in between; a pass that only moved the game on is no event
    @Test fun timelineShowsTurnAndStepLines() {
        val game = aliceFewTurnsIn { r -> r.engine!!.timeline.events(r.engine!!, names).let { r.enterReview(it[it.size - 3].id) } }
        val events = game.timeline.events(game, names)
        val line = game.shown!!.line.associateBy { it.id }
        assertTrue(events.none { e -> line[e.id]!!.let { EngineEventText.pass(it) && EngineEventText.ownPress(it, game.me) == null &&
            it.engine?.changes.orEmpty().none { c -> c is arcanum.engine.change.Change.StackItemRemoved } } }, "no step-only pass")
        val separators = events.mapNotNull { it.separator }
        assertTrue(separators.any { it.newTurn && it.label.startsWith("T2 · ") }, "${separators.map { it.label }}")
        assertTrue(compose.onAllNodesWithTag(TimelineSeparatorTag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        captureShot("$SCREENSHOTS/engine/timeline-separators.png",
            "Alice, a few turns in, opened timeline mode and stepped back two events: the strip shows a labelled vertical line between events wherever the turn or step changed (\"T3 · Main 1\", a stronger line for a new turn, the label in the turn player's colour), and no branch lines.")
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
        val game = aliceAfterALand()
        val routes = render(game, f.a)
        val events = game.timeline.events(game, names)
        compose.runOnIdle { routes.enterReview(events.first { it.description == "You kept" }.id) }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Auto advance").assertExists()
        compose.onNodeWithContentDescription("Return to current").assertExists()
        captureShot("$SCREENSHOTS/engine/timeline-mode.png",
            "Alice tapped the history button at the end of the strip and stepped back to her keep: the bars turn grey, the board shows the opening as it was then, and the timeline panel has the strip with Previous, Auto and Next; nothing on it acts.")
    }

    // HIS-015, HIS-018 (owner, 2026-11-08): timeline mode opens at the newest event, where the Auto button reads "Exit
    // timeline"; moving back makes it Auto again, Next stays an arrow, and at the last event again a tap leaves timeline mode
    @Test fun exitTimelineAtTheLastEvent() {
        val game = aliceAfterALand()
        val routes = render(game, f.a)
        val events = game.timeline.events(game, names)
        compose.runOnIdle { routes.enterReview(events.last().id) }
        compose.waitForIdle()
        compose.onNodeWithContentDescription(EXIT_TIMELINE).assertExists()
        compose.onNodeWithContentDescription("Auto advance").assertDoesNotExist()
        compose.onNodeWithContentDescription("Next").assertExists()
        captureShot("$SCREENSHOTS/engine/timeline-exit.png",
            "Alice just opened timeline mode, at the newest event: the Auto button between Previous and Next reads Exit timeline with a close icon.")
        compose.onNodeWithContentDescription("Previous").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Auto advance").assertExists()
        compose.onNodeWithContentDescription(EXIT_TIMELINE).assertDoesNotExist()
        compose.onNodeWithContentDescription("Next").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription(EXIT_TIMELINE).performClick()
        compose.runOnIdle { kotlin.test.assertNull(routes.review, "back where she was") }
    }

    // HIS-015, TBL-009: in landscape too "Exit timeline" fits between Previous and Next, in the controls' place
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi") fun exitTimelineInLandscape() {
        val game = aliceAfterALand()
        val routes = render(game, f.a, landscape = true)
        val events = game.timeline.events(game, names)
        compose.runOnIdle { routes.enterReview(events.last().id) }
        compose.waitForIdle()
        compose.onNodeWithContentDescription(EXIT_TIMELINE).assertExists()
        captureShot("$SCREENSHOTS/engine/timeline-exit-landscape.png",
            "Landscape: Alice just opened timeline mode, at the newest event: Exit timeline sits between Previous and Next, in the bottom bar's right end beside the strip.")
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
