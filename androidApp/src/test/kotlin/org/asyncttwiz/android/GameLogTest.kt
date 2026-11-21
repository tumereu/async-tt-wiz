package org.asyncttwiz.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The game log (HIS-239–HIS-242): the timeline's events as lines of text, from Table actions. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class GameLogTest {
    @get:Rule val compose = createComposeRule()

    @After fun forget() = AutomatumBoards.reset()

    private val names = mapOf(TableStatesFixture.a to "Alice", TableStatesFixture.b to "Bob")

    private fun drain(f: EngineLobbyFixture) {
        repeat(4) { compose.runOnIdle { f.worker.drain() }; compose.waitForIdle() }
    }

    // HIS-239, HIS-241, HIS-004, TRN-010: Table actions → Game log lists the timeline's events, oldest at the top; tapping
    // Bob's keep closes the log and opens timeline mode there (not at the newest event: Auto, not Exit timeline, HIS-015)
    @Test fun theLogListsTheTimelineAndALineOpensTimelineModeThere() {
        val f = EngineLobbyFixture()
        try {
            f.connect(f.head(otherKept = true))
            f.keep()
            compose.setContent { MaterialTheme { TableScreen(f.lobby, f.me, {}, false) } }
            drain(f)
            compose.onNodeWithContentDescription("Table actions").performClick()
            compose.onNodeWithText("Game log").assertIsEnabled().performClick()
            drain(f)
            compose.onNodeWithTag("game-log").assertIsDisplayed()
            compose.onNodeWithText("Table actions").assertDoesNotExist()
            // Top to bottom as drawn (the list lays the newest out first, at the bottom)
            val lines = compose.onAllNodesWithTag("game-log-line").fetchSemanticsNodes().sortedBy { it.boundsInRoot.top }
            val texts = lines.map { it.config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription].single() }
            val timeline = compose.runOnIdle { f.lobby.engineGame()!!.let { it.timeline.events(it, names) }.map { it.description } }
            assertEquals(timeline, texts, "the log's lines are the timeline's events, oldest first")
            assertTrue("Bob kept" in texts && "You kept" in texts, "both keeps: $texts")
            val bob = lines[texts.indexOf("Bob kept")].boundsInRoot
            val mine = lines[texts.indexOf("You kept")].boundsInRoot
            assertTrue(bob.bottom <= mine.top + 1f, "the newer line below the older: $bob, $mine")
            compose.onNodeWithContentDescription("Bob kept").performClick()
            drain(f)
            compose.onNodeWithTag("game-log").assertDoesNotExist()
            compose.onNodeWithContentDescription("Return to current").assertExists()
            compose.onNodeWithContentDescription("Auto advance").assertExists()
            compose.onNodeWithContentDescription("Exit timeline").assertDoesNotExist()
        } finally { f.close() }
    }

    // HIS-240, HIS-242, HIS-239: a few turns of Mountains and Ember Cubs: each name and card in its player's colour, the turn
    // and step labels between the lines in the colour of whose turn it is
    @Test fun theLogInThePlayersColours() {
        val scenario = EngineCombatScenario()
        scenario.until(scenario::bothHaveCubs)
        val alice = scenario.alice
        val lines = alice.timeline.log(alice, names)
        val texts = lines.map { it.pieces.plain() }
        assertTrue(texts.any { it == "Bob played Mountain" } && texts.any { it == "You cast Ember Cub" }, "$texts")
        val bobs = lines.first { it.pieces.plain() == "Bob played Mountain" }.pieces
        assertEquals(listOf(StackTextPiece("Bob", scenario.f.b), StackTextPiece(" played "), StackTextPiece("Mountain", scenario.f.b)), bobs)
        val mine = lines.first { it.pieces.plain() == "You cast Ember Cub" }.pieces
        assertEquals(listOf(StackTextPiece("You", scenario.f.a), StackTextPiece(" cast "), StackTextPiece("Ember Cub", scenario.f.a)), mine)
        compose.setContent { MaterialTheme { NoRipples { GameLogPanel(lines, scenario.f.a, {}, {}) } } }
        compose.waitForIdle()
        compose.onAllNodesWithTag("game-log-separator").onFirst().assertExists()
        compose.onAllNodesWithTag("game-log-line").onLast().assertIsDisplayed()
        // HIS-239 (owner 2026-11-21, E11): tight rows, a one-line row about 24 dp high
        val heights = compose.onAllNodesWithTag("game-log-line").fetchSemanticsNodes().map { it.size.height / it.layoutInfo.density.density }
        assertTrue(heights.min() in 23f..25f, "one-line rows about 24 dp: $heights")
        captureShot("$SCREENSHOTS/history/game-log.png",
            "Alice opened Table actions → Game log after a few turns of Mountains and Ember Cubs: one line per event with its icon, the newest at the bottom, her own name and cards in green and Bob's in red, and the turn and step labels between the lines.")
    }

    /**
     * Alice and Bob each turn play a Mountain and cast a Storm Spark at the other's Ember Cub (at the other player while
     * there is none) or else an Ember Cub; Alice attacks Bob with her ready Cubs. Until Alice's log holds a Spark of each at
     * a Cub and her attack.
     */
    private fun playWithTargets(): Pair<EngineCombatScenario, List<GameLogLine>> {
        val scenario = EngineCombatScenario()
        val alice = scenario.alice
        fun done(texts: List<String>) = texts.any { it == "You cast Storm Spark targeting Bob's Ember Cub" } &&
            texts.any { it == "Bob cast Storm Spark targeting your Ember Cub" } && texts.any { it.startsWith("You attacked Bob with ") }
        alice.keep(); scenario.bob.keep()
        repeat(600) {
            scenario.settle()
            val lines = alice.timeline.log(alice, names)
            if (done(lines.map { it.pieces.plain() })) return scenario to lines
            for (game in listOf(alice, scenario.bob)) {
                if (game.device.status != org.asyncttwiz.protocol.game.GameDevice.Status.YourMove &&
                    game.device.status !is org.asyncttwiz.protocol.game.GameDevice.Status.Stopped) continue
                val s = game.device.model.engine
                val me = game.device.me
                val opponent = s.players.first { it.id != me }.id
                val options = arcanum.engine.Engine.options(s, me, org.asyncttwiz.protocol.game.EnginePreferences.of(s))
                val main = s.turn.active == me && s.stack.isEmpty() && s.priority.holder == me &&
                    s.turn.step in setOf(arcanum.api.TurnStep.PrecombatMain, arcanum.api.TurnStep.PostcombatMain)
                when {
                    options.any { it is arcanum.engine.input.Input.DeclareAttackers } -> game.send(listOf(arcanum.engine.input.Input.DeclareAttackers(me,
                        if (me == scenario.me) scenario.creatures(s, me, ready = true).map { arcanum.engine.input.Attack(it, arcanum.engine.state.TargetRef.Player(opponent)) }
                        else emptyList())))
                    options.any { it is arcanum.engine.input.Input.DeclareBlockers } -> game.send(listOf(arcanum.engine.input.Input.DeclareBlockers(me, emptyList())))
                    main -> {
                        val hand = s.player(me).hand
                        val untapped = scenario.cards(s, me, s.battlefield, "Mountain").any { s.obj(it)?.tapped == false }
                        val spark = scenario.cards(s, me, hand, "Storm Spark").firstOrNull()
                        val cub = scenario.cards(s, me, hand, "Ember Cub").firstOrNull()
                        val land = scenario.cards(s, me, hand, "Mountain").firstOrNull()?.takeIf { (s.turn.landsPlayed[me] ?: 0) == 0 }
                        when {
                            land != null -> game.send(listOf(arcanum.engine.input.Input.PlayLand(me, land)))
                            untapped && spark != null -> {
                                val target = scenario.creatures(s, opponent, ready = false).firstOrNull()
                                    ?.let { arcanum.engine.state.TargetRef.Object(it) } ?: arcanum.engine.state.TargetRef.Player(opponent)
                                game.send(scenario.cast(s, me, arcanum.engine.input.Input.Cast(me, spark), target))
                            }
                            untapped && cub != null -> game.send(scenario.cast(s, me, arcanum.engine.input.Input.Cast(me, cub)))
                            else -> game.pass()
                        }
                    }
                    s.priority.holder == me -> game.pass()
                    else -> {}
                }
            }
        }
        kotlin.test.fail("never got the targeted lines: ${alice.timeline.log(alice, names).map { it.pieces.plain() }}")
    }

    // HIS-243, HIS-245, HIS-240 (owner 2026-11-21: event texts name their targets): Alice's log names what each Storm Spark targeted and
    // whom her attack attacked, each target in its player's colour
    @Test fun theLogNamesTargets() {
        val (scenario, lines) = playWithTargets()
        val mine = lines.last { it.pieces.plain() == "You cast Storm Spark targeting Bob's Ember Cub" }.pieces
        assertEquals(listOf(StackTextPiece("You", scenario.f.a), StackTextPiece(" cast "), StackTextPiece("Storm Spark", scenario.f.a),
            StackTextPiece(" targeting "), StackTextPiece("Bob's Ember Cub", scenario.f.b)), mine)
        val bobs = lines.last { it.pieces.plain() == "Bob cast Storm Spark targeting your Ember Cub" }.pieces
        assertEquals(listOf(StackTextPiece("Bob", scenario.f.b), StackTextPiece(" cast "), StackTextPiece("Storm Spark", scenario.f.b),
            StackTextPiece(" targeting "), StackTextPiece("your Ember Cub", scenario.f.a)), bobs)
        val attack = lines.last { it.pieces.plain().startsWith("You attacked Bob with ") }.pieces
        assertEquals(StackTextPiece("Bob", scenario.f.b), attack[2], "$attack")
        // Bob's log of the same game: the same events from his side
        val bob = scenario.bob
        val his = bob.timeline.log(bob, names).map { it.pieces.plain() }
        assertTrue(his.any { it == "You cast Storm Spark targeting Alice's Ember Cub" } &&
            his.any { it == "Alice cast Storm Spark targeting your Ember Cub" } && his.any { it.startsWith("Alice attacked you with ") }, "$his")
        compose.setContent { MaterialTheme { NoRipples { GameLogPanel(lines, scenario.f.a, {}, {}) } } }
        compose.waitForIdle()
        compose.onAllNodesWithTag("game-log-line").onLast().assertIsDisplayed()
        captureShot("$SCREENSHOTS/history/game-log-targets.png",
            "Alice opened the Game log after turns of Storm Sparks at each other's Ember Cubs and her attacks: each Spark's line names its target (\"targeting Bob's Ember Cub\" in his red, \"targeting your Ember Cub\" in her green) and her attack names Bob.")
    }

    // HIS-243, HIS-245, WEB-015: the desktop table's Game log, the same targeted lines
    @Config(qualifiers = "w1440dp-h900dp-land-mdpi")
    @Test fun theLogNamesTargetsOnTheDesktop() {
        val (scenario, lines) = playWithTargets()
        compose.setContent { MaterialTheme { NoRipples {
            androidx.compose.runtime.CompositionLocalProvider(LocalAppLayout provides AppLayout.Desktop) { GameLogPanel(lines, scenario.f.a, {}, {}) }
        } } }
        compose.waitForIdle()
        compose.onAllNodesWithTag("game-log-line").onLast().assertIsDisplayed()
        captureShot("$SCREENSHOTS/desktop/game-log-targets.png",
            "On the desktop table Alice opened the Game log after turns of Storm Sparks at each other's Ember Cubs and her attacks: the log sits in the middle of the window, each Spark's line naming its target in that player's colour and her attack naming Bob.")
    }
}
