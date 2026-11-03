package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import arcanum.api.Layout
import arcanum.parser.OracleCard
import arcanum.parser.OracleParser
import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.ParsedCard
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Automatum I3a (integration spec §3 I3, I-D31, I-D34, I-D35): the step button, the step arrow and the phase dialog move
 * the turn through Automatum; the move follows who must act; each Automatum step is one history event. Invented cards
 * parsed from their oracle text as on a device: Storm Spark (instant, 3 damage to any target) and Mountains. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class AutomatumStepsScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val f = TableStatesFixture

    private class Invented(val face: DefinitionFace, val oracle: OracleCard, byHand: Boolean = false) {
        val submitted = CardDefinition(face.name, listOf(face)).let { SubmittedDefinition(DeckJson.digest(it), it) }
        val definition = arcanum.api.CardDefinition(Layout.Normal, listOf(OracleParser.parse(oracle).let { parsed ->
            // Something to do by hand (D61), as the engine's own manual steps are
            if (!byHand) parsed else parsed.copy(abilities = listOf(arcanum.api.Ability.Spell(
                arcanum.api.Instructions(steps = listOf(arcanum.api.Step.Manual(oracle.text.orEmpty()))), text = oracle.text)))
        }))
    }
    private fun invented(id: String, name: String, cost: String?, type: String, text: String) = Invented(
        DefinitionFace(id, name, typeLine = type, manaCost = cost.orEmpty(), rulesText = text), OracleCard(name, cost, type, text, null, null))
    private val spark = invented("spark", "Storm Spark", "{R}", "Instant", "Storm Spark deals 3 damage to any target.")
    private val mountain = invented("mountain", "Mountain", null, "Basic Land — Mountain", "")
    /** An instant whose text Automatum can't read: it is done by hand (D61). */
    private val odd = "Rearrange the permanents on the battlefield however you like.".let { text -> Invented(
        DefinitionFace("odd", "Odd Rearrangement", typeLine = "Instant", manaCost = "{R}", rulesText = text),
        OracleCard("Odd Rearrangement", "{R}", "Instant", text, null, null), byHand = true) }
    private val all = listOf(spark, mountain, odd)

    private val aHand = f.id("a/HAND")
    private val bHand = f.id("b/HAND")
    private val aField = f.id("a/BATTLEFIELD")
    private val bField = f.id("b/BATTLEFIELD")
    private val aSpark get() = f.initial.board.containers.single { it.id == aHand }.cards[0]
    private val bSpark get() = f.initial.board.containers.single { it.id == bHand }.cards[0]

    @Before fun definitions() {
        val byDigest = all.associate { it.submitted.digest to it.definition }
        AutomatumBoards.definitionSource = { submitted -> submitted.mapNotNull { s ->
            byDigest[s.digest]?.let { s.digest to ParsedCard.Read(it, 0) } }.toMap() }
    }
    @After fun forget() { AutomatumBoards.reset(); AutomatumBoards.definitionSource = null }

    /** Alice's main phase (turn 4) with Automatum on: Alice holds Storm Spark and has two Mountains; Bob holds Storm Spark
     * ([bobSpark], else nothing) and has one Mountain; every other hand card is out of the game. [fullControl]: the players
     * with Full control on (SYS-071), stopped for in every step in which they could act. */
    private fun board(bobSpark: Boolean = true, fullControl: Set<String> = emptySet()): PlayState {
        val initial = f.initial
        val aLands = initial.board.cards.filter { it.containerId == aField }.take(2).map { it.id }
        val bLands = initial.board.cards.filter { it.containerId == bField }.take(1).map { it.id }
        val kept = setOf(aSpark) + if (bobSpark) setOf(bSpark) else emptySet()
        val dropped = initial.board.cards.filter { (it.containerId == aHand || it.containerId == bHand) && it.id !in kept }.map { it.id }.toSet()
        fun as_(card: PlayCard, what: Invented) = card.copy(definition = what.submitted.digest, faceId = what.face.faceId, corner = "", tapped = false)
        return initial.copy(definitions = initial.definitions + all.map { it.submitted },
            board = initial.board.copy(automatum = true, priority = MtgPriority(f.a),
                players = initial.board.players.map { it.copy(fullControl = it.accountId in fullControl) },
                cards = initial.board.cards.filter { it.id !in dropped }.map { card -> when (card.id) {
                    aSpark, bSpark -> as_(card, spark)
                    in aLands, in bLands -> as_(card, mountain)
                    else -> card
                } },
                containers = initial.board.containers.map { it.copy(cards = it.cards.filter { id -> id !in dropped }) }))
    }

    /** The game's history so far, as the server accepts the batches (its tags are the engine's event log, D33). */
    private var tree: GameplayTree? = null
    private var batches = 0

    private fun accepted(play: PlayState, batch: GameAction.PlayBatch, request: String, actor: String): PlayState {
        val history = tree?.takeIf { it.stateAt(it.currentNodeId)?.board == play.board } ?: GameplayTree(play)
        val command = GameCommand(request, GameScope(play.gameId, playId = play.id), batch, f.id("client"), "1")
        val change = PlayBatches.change(history, actor, command)
        assertNull(change.reason, "the server takes the batch")
        val next = assertNotNull(change.tree)
        tree = next
        batches++
        return assertNotNull(next.stateAt(next.currentNodeId))
    }

    private val sent = mutableListOf<Pair<GameAction.PlayBatch, String>>()
    private fun toolkit(play: PlayState, viewer: String) = TableToolkitController(viewer, { play }, { true }, { _, _ -> true },
        automatedBatch = { batch, request -> sent += batch to request; true }, playTree = { tree })

    /** [viewer] presses [intent] (the step arrow or the phase dialog) on [play]; the batch the server accepts. */
    private fun press(play: PlayState, viewer: String, intent: (TableActionScope) -> TableActionIntent): PlayState {
        sent.clear()
        val toolkit = toolkit(play, viewer)
        assertTrue(toolkit.table.perform(intent(TableActionScope(play.gameId, play.id))), toolkit.error)
        val (batch, request) = sent.single()
        return accepted(play, batch, request, viewer)
    }

    private fun render(play: PlayState, viewer: String, toolkit: TableToolkitController = toolkit(play, viewer)) {
        AutomatumBoards.compute(play, viewer)
        compose.setContent {
            MaterialTheme {
                NoRipples {
                    Box(Modifier.fillMaxSize()) {
                        TableSurface(f.game, play, viewer, false, Modifier.fillMaxSize(), connected = true,
                            onIntent = toolkit::handle, toolkit = toolkit,
                            runtime = TableRuntimeHooks(navigation = {}, chat = {}, undo = { true }, redo = { true }, controlsConnected = true))
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun position(play: PlayState) = TurnPosition.of(play.board.turn)

    // TRN-057, TRN-058, SYS-067, CR 117.3d: Next step is Alice's priority pass; Bob could answer with Storm Spark and has
    // Full control (SYS-071), so Automatum stops there: he holds priority in her main phase, and the move with it
    @Test fun nextStepPassesAndTheMoveFollowsPriority() {
        val after = press(board(fullControl = setOf(f.a, f.b)), f.a) { TableActionIntent.Next(it) }
        assertEquals(TurnPosition.PRECOMBAT_MAIN, position(after))
        assertEquals(MtgPriority(f.b, 1), after.board.priority)
        assertEquals(f.b, after.board.attentionHolder)
        render(after, f.b)
        compose.onNodeWithText("Your move").assertExists()
        compose.onRoot().captureShot("$SCREENSHOTS/automatum/step-move-follows-priority.png",
            "Alice pressed Next step in her main phase with Automatum on; Bob holds a Storm Spark he could cast and has Full control, so Automatum stopped with Bob holding priority still in Main 1, and the move followed: Bob sees \"Your move\" and his colour on the bars.")
        // Bob passes too: the main phase ends and Alice, who could still cast her Storm Spark, acts at Beginning of combat
        val next = press(after, f.b) { TableActionIntent.Next(it) }
        assertEquals(TurnPosition.BEGIN_COMBAT, position(next))
        assertEquals(MtgPriority(f.a), next.board.priority)
        assertEquals(f.a, next.board.attentionHolder)
    }

    // HIS-092 (I-D35), TRN-057: a cast nobody can answer resolves, and the turn goes on by itself to Alice's attack
    // declaration; then her End turn runs into Bob's turn. Each Automatum step is one event on the timeline
    @Test fun eachAutomatumStepIsOneTimelineEvent() {
        val play = board(bobSpark = false)
        val toolkit = toolkit(play, f.a)
        assertTrue(toolkit.cast.start(aSpark))
        val life = TablePresentation.scene(f.game, play, f.a, 412f, 892f, false).items.first { it.id == "life/${f.b}" }
        toolkit.open(TableIntent.Actions(life.id, null), life)
        // I-D47: Autotap (on) sends the cast once its last choice is made; only a cast still open waits for Cast
        if (toolkit.cast.open) assertTrue(toolkit.cast.cast(), toolkit.error)
        val (batch, request) = sent.single()
        val cast = accepted(play, batch, request, f.a)
        fun life(p: PlayState) = p.board.players.single { it.accountId == f.b }.life.toInt()
        assertEquals(life(play) - 3, life(cast))
        assertEquals(TurnPosition.DECLARE_ATTACKERS, position(cast), "on to Alice's attack declaration")
        assertEquals(f.a, cast.board.attentionHolder)
        val ended = press(cast, f.a) { TableActionIntent.End(it) }
        assertEquals(f.b, ended.board.turn.accountId)
        val history = assertNotNull(tree)
        val events = HistoryTimelineLayout.events(HistoryTree(history.initial.root.id, emptyList(), history.currentNodeId, "v",
            source = history), history.currentNodeId)
        assertEquals(1 + batches, events.size, "the start, then one event per Automatum step: ${events.map { it.description }}")
        compose.setContent { MaterialTheme { Box(Modifier.fillMaxWidth().height(96.dp)) {
            HistoryTimeline(events, history.currentNodeId, f.a, {}, {}, readOnly = true)
        } } }
        compose.waitForIdle()
        compose.onRoot().captureShot("$SCREENSHOTS/automatum/step-timeline-events.png",
            "The timeline after Alice cast Storm Spark at Bob (it resolved and the turn went on by itself to her attack declaration) and then pressed End turn (into Bob's turn): the game's start and one event for each of the two Automatum steps; everything Automatum did on the way is folded into them.")
    }

    // TRN-056, TRN-008: in a combat's first-strike damage step the phase dialog shows that step's square between
    // Blockers and Damage, current; TRN-007: the step button shows "1st strike"
    @Test fun phaseDialogShowsTheFirstStrikeStep() {
        val play = TableToolFixture.play(TurnPosition.FIRST_STRIKE_DAMAGE, "5").let { it.copy(board = it.board.copy(automatum = true)) }
        val controller = TableActionController({ play }, { true }, RecordingRoutes())
        compose.setContent { MaterialTheme {
            Column {
                TurnIndicator(play.board.turn, "Alice", Color(0xff2e7d5b), {})
                PhaseStepPicker(play, play.board.turn.accountId, Color(0xff2e7d5b), controller, {},
                    names = mapOf(play.board.turn.accountId to "Alice"))
            }
        } }
        compose.onNodeWithText("Combat · First-strike damage").assertIsDisplayed()
        compose.onAllNodes(hasContentDescription("First-strike damage") and isSelected()).assertCountEquals(1)
        compose.onAllNodesWithText("1st strike", useUnmergedTree = true).assertCountEquals(2)
        captureShot("$SCREENSHOTS/automatum/phase-dialog-first-strike.png",
            "With Automatum on, in the first-strike combat damage step: the step button shows a bolt and \"1st strike\", and the phase dialog shows that square between Blockers and Damage, outlined as the current step.")
    }


    // TRN-057, I-D28: with Automatum on the turn moves on only through Automatum: an earlier or the same step, a step
    // passed into by a quick action and a step change made any other way are refused; STK-029 while the Stack holds anything
    @Test fun manualStepChangesAreRefused() {
        val play = board(bobSpark = false)
        val toolkit = toolkit(play, f.a)
        val scope = TableActionScope(play.gameId, play.id)
        assertFalse(toolkit.table.perform(TableActionIntent.Choose(scope, TurnPosition.UPKEEP)))
        assertEquals(SharedFlowController.STEPS_FORWARD, toolkit.error)
        assertFalse(toolkit.table.perform(TableActionIntent.Choose(scope, TurnPosition.PRECOMBAT_MAIN)))
        assertFalse(toolkit.table.perform(TableActionIntent.Choose(scope, TurnPosition.POSTCOMBAT_MAIN, pass = true)))
        assertFalse(toolkit.submit(PlayAction.NextStep))
        assertEquals(SharedFlowController.STEPS_FORWARD, toolkit.error)
        assertFalse(toolkit.submit(PlayAction.EndTurn))
        assertTrue(sent.isEmpty())
        // A later step goes through Automatum: Alice is passed for through combat, attacking with nothing, and acts there
        val endOfCombat = press(play, f.a) { TableActionIntent.Choose(it, TurnPosition.END_COMBAT) }
        assertEquals(TurnPosition.END_COMBAT, position(endOfCombat))
        assertEquals(MtgPriority(f.a), endOfCombat.board.priority)
        // STK-029: with something on the Stack the step button waits
        val stacked = play.copy(board = play.board.copy(stackEffects = listOf(StackEffect(f.id("entry"), aSpark)),
            containers = play.board.containers.map { if (it.zone == PlayZone.STACK) it.copy(cards = listOf(f.id("entry"))) else it }))
        val waiting = toolkit(stacked, f.a)
        assertFalse(waiting.table.perform(TableActionIntent.Next(scope)))
        assertEquals(SharedFlowController.STACK_WAITING, waiting.error)
    }

    // TRN-061, TRN-004, I-D40: Bob presses Next step while Alice holds priority in her main phase: he is asked first and
    // nothing is sent until he confirms; Cancel sends nothing; confirmed, it is Alice's pass as if she had pressed it
    @Test fun steppingForTheOtherPlayerAsksFirst() {
        val play = board(fullControl = setOf(f.b))
        val scope = TableActionScope(play.gameId, play.id)
        sent.clear()
        val bob = toolkit(play, f.b)
        assertFalse(bob.table.perform(TableActionIntent.Next(scope)))
        assertTrue(sent.isEmpty())
        val asked = assertNotNull(bob.flows.acting)
        assertEquals(f.a, asked.player)
        assertEquals(ActingFor.Kind.Pass(priority = true), asked.kind)
        bob.flows.cancelActing()
        assertNull(bob.flows.acting)
        assertTrue(sent.isEmpty())
        assertFalse(bob.table.perform(TableActionIntent.Next(scope)))
        render(play, f.b, bob)
        compose.onNodeWithText("Pass for Alice?").assertIsDisplayed()
        compose.onNodeWithText("Alice has priority and may still want to act. Passing for them lets the game go on.").assertIsDisplayed()
        captureShot("$SCREENSHOTS/automatum/step-for-other-asks.png",
            "With Automatum on, Bob pressed Next step in Alice's main phase while she holds priority: before anything is sent he is asked \"Pass for Alice?\", with Pass for them and Cancel.")
        compose.onNode(hasText("Pass for them") and hasAnyAncestor(isDialog())).performClick()
        compose.waitForIdle()
        assertNull(bob.flows.acting)
        val (batch, request) = sent.single()
        val after = accepted(play, batch, request, f.b)
        assertEquals(TurnPosition.PRECOMBAT_MAIN, position(after))
        assertEquals(MtgPriority(f.b, 1), after.board.priority, "Alice passed; Bob, who could cast Storm Spark with Full control, holds priority")
    }

    // TRN-061, I-D40: End turn and a later step for the other player say what they do for them, as do declarations; a
    // confirmation given after the table changed sends nothing
    @Test fun actingForTheOtherPlayerSaysWhatItDoes() {
        val play = board()
        var live = play
        sent.clear()
        val bob = TableToolkitController(f.b, { live }, { true }, { _, _ -> true },
            automatedBatch = { batch, request -> sent += batch to request; true }, playTree = { tree })
        assertFalse(bob.table.perform(TableActionIntent.End(TableActionScope(play.gameId, play.id))))
        val asked = assertNotNull(bob.flows.acting)
        assertEquals(org.asyncttwiz.automatum.StepFlow.Until.EndOfTurn, asked.until)
        assertEquals(ActingForText("End the turn for Alice?",
            "Alice has priority and may still want to act. Automatum passes for them until the turn ends, attacking with nothing.",
            "End turn for them"), actingForText(asked, "Alice"))
        assertEquals("Alice has priority and may still want to act. Automatum passes for them on to Postcombat main, attacking with nothing.",
            actingForText(ActingFor(play.board, f.a, org.asyncttwiz.automatum.StepFlow.Until.Step(TurnPosition.POSTCOMBAT_MAIN),
                ActingFor.Kind.Pass(priority = true)), "Alice").text)
        assertEquals(ActingForText("Declare attackers for Alice?",
            "Alice declares attackers now. This declares for them what the combat lanes hold: 2 attackers.", "Declare for them"),
            actingForText(ActingFor(play.board, f.a, org.asyncttwiz.automatum.StepFlow.Until.Pass, ActingFor.Kind.DeclareAttackers(2)), "Alice"))
        assertEquals("Bob declares blockers now. This declares for them what the combat lanes hold: no blocks.",
            actingForText(ActingFor(play.board, f.b, org.asyncttwiz.automatum.StepFlow.Until.Pass, ActingFor.Kind.DeclareBlockers(0)), "Bob").text)
        // Alice passed meanwhile: Bob's confirmation sends nothing
        live = play.copy(board = play.board.copy(priority = MtgPriority(f.b, 1)))
        assertFalse(bob.flows.confirmActing())
        assertEquals("The table changed: try again.", bob.error)
        assertTrue(sent.isEmpty())
    }
}
