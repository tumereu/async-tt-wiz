package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import arcanum.api.Layout
import arcanum.engine.decision.Decision
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
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Automatum I2b (integration spec §3 I2, I-D3, I-D11, D11): priority, responding, resolution choices and triggers as
 * shared flows. Invented cards parsed from their oracle text as on a device: Storm Spark (instant, 3 damage to any
 * target), Fertile Passing ("Whenever a creature dies, that creature's controller may draw a card."), Peak Ascendancy
 * (two "whenever you cast a noncreature spell" triggers), Tinkered Mite (a 1/1) and Mountains. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class AutomatumResolveScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val f = TableStatesFixture

    private class Invented(val face: DefinitionFace, val oracle: OracleCard) {
        val submitted = CardDefinition(face.name, listOf(face)).let { SubmittedDefinition(DeckJson.digest(it), it) }
        val definition = arcanum.api.CardDefinition(Layout.Normal, listOf(OracleParser.parse(oracle)))
    }
    private fun invented(id: String, name: String, cost: String?, type: String, text: String, pt: String? = null) = Invented(
        DefinitionFace(id, name, typeLine = type, manaCost = cost.orEmpty(), rulesText = text, corner = pt.orEmpty()),
        OracleCard(name, cost, type, text, pt?.substringBefore("/"), pt?.substringAfter("/")))
    private val spark = invented("spark", "Storm Spark", "{R}", "Instant", "Storm Spark deals 3 damage to any target.")
    private val mountain = invented("mountain", "Mountain", null, "Basic Land — Mountain", "")
    private val passing = invented("passing", "Fertile Passing", "{2}{G}", "Enchantment",
        "Whenever a creature dies, that creature's controller may draw a card.")
    private val peak = invented("peak", "Peak Ascendancy", "{U}{R}{W}", "Enchantment",
        "Whenever you cast a noncreature spell, creatures you control get +1/+1 until end of turn. Untap those creatures.\n" +
            "Whenever you cast a noncreature spell, you may draw a card. If you do, discard a card.")
    private val mite = invented("mite", "Tinkered Mite", "{0}", "Artifact Creature — Construct", "", "1/1")
    private val all = listOf(spark, mountain, passing, peak, mite)

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

    /**
     * Alice's main phase with Automatum on: Alice holds Storm Spark and has two Mountains; Bob holds Storm Spark
     * ([bobSpark], else an empty hand) and has one Mountain. [aliceExtra] and [bobExtra] go onto the battlefields
     * (Alice's then her first two lands are those Mountains). Every other hand card is out of the game.
     */
    private fun board(bobSpark: Boolean = true, aliceExtra: List<Invented> = emptyList(), bobExtra: List<Invented> = emptyList()): PlayState {
        val initial = f.initial
        val aLands = initial.board.cards.filter { it.containerId == aField }.take(2 + aliceExtra.size).map { it.id }
        val bLands = initial.board.cards.filter { it.containerId == bField }.take(1 + bobExtra.size).map { it.id }
        val kept = setOf(aSpark) + if (bobSpark) setOf(bSpark) else emptySet()
        val dropped = initial.board.cards.filter { (it.containerId == aHand || it.containerId == bHand) && it.id !in kept }.map { it.id }.toSet()
        fun as_(card: PlayCard, what: Invented) = card.copy(definition = what.submitted.digest, faceId = what.face.faceId, corner = what.face.corner,
            tapped = false)
        return initial.copy(definitions = initial.definitions + all.map { it.submitted },
            board = initial.board.copy(priority = MtgPriority(f.a),
                cards = initial.board.cards.filter { it.id !in dropped }.map { card -> when (card.id) {
                    aSpark, bSpark -> as_(card, spark)
                    in aLands -> as_(card, aLands.indexOf(card.id).let { i -> if (i < 2) mountain else aliceExtra[i - 2] })
                    in bLands -> as_(card, bLands.indexOf(card.id).let { i -> if (i < 1) mountain else bobExtra[i - 1] })
                    else -> card
                } },
                containers = initial.board.containers.map { it.copy(cards = it.cards.filter { id -> id !in dropped }) }))
    }

    /** The game's history so far: batches are accepted onto it as the server does, and its tags are the engine's
     * event log (D33). */
    private var tree: GameplayTree? = null

    /** The board the server makes of [batch] sent by [actor] on the history so far (which starts at [play]). */
    private fun accepted(play: PlayState, batch: GameAction.PlayBatch, request: String, actor: String): PlayState {
        val history = tree?.takeIf { it.stateAt(it.currentNodeId)?.board == play.board } ?: GameplayTree(play)
        val command = GameCommand(request, GameScope(play.gameId, playId = play.id), batch, f.id("client"), "1")
        val change = PlayBatches.change(history, actor, command)
        assertNull(change.reason, "the server takes the batch")
        val next = assertNotNull(change.tree)
        tree = next
        return assertNotNull(next.stateAt(next.currentNodeId))
    }
    private fun tags(play: PlayState) = engineTags(tree?.takeIf { it.stateAt(it.currentNodeId)?.board == play.board }, play.currentNodeId)

    private fun item(play: PlayState, viewer: String, predicate: (TableItem) -> Boolean): TableItem =
        TablePresentation.scene(f.game, play, viewer, 412f, 892f, false).items.first(predicate)

    /** Alice casts her Storm Spark at [target] (a life square or card id) through Automatum, as the app sends it. */
    private fun aliceCasts(play: PlayState, target: String): PlayState {
        val sent = mutableListOf<Pair<GameAction.PlayBatch, String>>()
        val toolkit = TableToolkitController(f.a, { play }, { true }, { _, _ -> true }, automatedBatch = { b, r -> sent += b to r; true },
            playTree = { tree })
        assertTrue(toolkit.cast.start(aSpark))
        val tapped = if (target.startsWith("life/")) item(play, f.a) { it.id == target } else item(play, f.a) { it.cardId == target }
        toolkit.open(TableIntent.Actions(tapped.id, null), tapped, TableSelection())
        // I-D47: Autotap (on) sends the cast once its last choice is made; only a cast still open waits for Cast
        if (toolkit.cast.open) assertTrue(toolkit.cast.cast(), toolkit.error)
        val (batch, request) = sent.single()
        return accepted(play, batch, request, f.a)
    }

    private val sent = mutableListOf<Pair<GameAction.PlayBatch, String>>()
    private fun render(play: PlayState, viewer: String): TableToolkitController {
        AutomatumBoards.compute(play, viewer)
        AutomatumBoards.sharedFlow(play, tags(play))
        val history = tree
        val toolkit = TableToolkitController(viewer, { play }, { true }, { _, _ -> true },
            automatedBatch = { batch, request -> sent += batch to request; true }, playTree = { history })
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
        return toolkit
    }

    private fun life(play: PlayState, player: String) = play.board.players.single { it.accountId == player }.life.toInt()

    // STK-026, STK-027, STK-029, TRN-063, I-D3, CR 117.3d, 117.4: Bob could respond, so he holds priority and is asked;
    // the step arrow's "Resolve" passes and the spell resolves in that batch. Steps don't change while it waits.
    @Test fun respondPromptAndLetItResolve() {
        val cast = aliceCasts(board(), "life/${f.b}")
        assertEquals(MtgPriority(f.b, 1), cast.board.priority)
        assertEquals(1, cast.board.stackEffects.size)
        val toolkit = render(cast, f.b)
        compose.onNodeWithText("Alice cast Storm Spark").assertExists()
        // STK-027 (UI review #5): the spell is shown as its card, its target as "Target:" and a player's name
        compose.onNode(hasText("Target:") and hasAnyAncestor(hasContentDescription("Respond prompt"))).assertExists()
        compose.onNode(hasText("You") and hasAnyAncestor(hasContentDescription("Respond prompt"))).assertExists()
        compose.onRoot().captureShot("$SCREENSHOTS/automatum/respond-prompt.png",
            "Alice cast Storm Spark at Bob with Automatum on and Bob holds a Storm Spark he could answer with: docked above his hand Bob sees Storm Spark's card beside \"Alice cast Storm Spark\", \"Target: You\", the eye at the bottom left and Respond at the right, his step arrow reads Resolve, and the move is his (\"Your move\") since he must act.")
        compose.runOnIdle { assertFalse(toolkit.submit(PlayAction.NextStep)); assertEquals(SharedFlowController.STACK_WAITING, toolkit.error) }
        compose.onNodeWithText("Respond").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Respond — cast a card from your hand").assertExists()
        compose.onNodeWithText("Let it resolve").assertDoesNotExist()
        compose.onNodeWithContentDescription("Resolve").performClick()
        compose.waitForIdle()
        val (batch, request) = sent.single()
        val after = accepted(cast, batch, request, f.b)
        assertEquals(life(cast, f.b) - 3, life(after, f.b))
        assertTrue(after.board.stackEffects.isEmpty())
        // CR 117.3b: the active player gets priority after resolution; with nothing left to do she is passed for (TRN-057),
        // and Bob, who could still cast his Storm Spark, has no pause in her main phase (STK-036): Automatum goes on past it
        assertFalse(after.board.turn == cast.board.turn && after.board.priority?.holder == f.b)
    }

    // STK-026, I-D3, I-D18: with nothing Bob could respond with, the spell resolves in the cast's own batch
    @Test fun nothingToRespondWithResolvesAtOnce() {
        val play = board(bobSpark = false)
        val after = aliceCasts(play, "life/${f.b}")
        assertEquals(life(play, f.b) - 3, life(after, f.b))
        assertTrue(after.board.stackEffects.isEmpty())
        assertTrue(aSpark in after.board.containers.single { it.id == f.id("a/GRAVEYARD") }.cards)
    }

    // STK-028, TRN-061, D11: the player without priority sees who may respond; their step arrow's Resolve passes for them
    // after confirming
    @Test fun passForTheOtherPlayer() {
        val cast = aliceCasts(board(), "life/${f.b}")
        render(cast, f.a)
        compose.onNodeWithText("Bob may respond to Storm Spark").assertExists()
        compose.onNodeWithText("Pass for them").assertDoesNotExist()
        compose.onNodeWithContentDescription("Resolve").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Pass for Bob?").assertExists()
        compose.onNodeWithText("Pass for them").performClick()
        compose.waitForIdle()
        val (batch, request) = sent.single()
        assertEquals(life(cast, f.b) - 3, life(accepted(cast, batch, request, f.a), f.b))
    }

    /** Alice's Storm Spark kills Bob's Tinkered Mite under her Fertile Passing: the trigger resolves to Bob's "may". */
    private fun fertileDeath(): PlayState {
        val play = board(bobSpark = false, aliceExtra = listOf(passing), bobExtra = listOf(mite))
        val miteCard = play.board.cards.first { it.definition == mite.submitted.digest }.id
        val after = aliceCasts(play, miteCard)
        assertTrue(Automatum.flowOpen(after.board), "Bob's choice is kept in the game")
        return after
    }

    // STK-030, I-D11, I-D47, CR 603.3, 608.2d: the deciding player answers in the panel, titled with what the choice is and
    // where it comes from and showing the card it is about; its prompt is the card's sentence
    @Test fun resolutionChoicePanel() {
        val open = fertileDeath()
        val toolkit = render(open, f.b)
        compose.onNodeWithContentDescription("Automatum choice").assertExists()
        compose.onNodeWithText("You may · Fertile Passing").assertExists()
        compose.onNode(hasContentDescription("Tinkered Mite") and hasClickAction()).assertExists()
        compose.onRoot().captureShot("$SCREENSHOTS/automatum/resolve-choice.png",
            "Alice's Storm Spark killed Bob's Tinkered Mite under her Fertile Passing: docked above his hand Bob sees Fertile Passing's card beside \"You may · Fertile Passing\", \"About:\" with Tinkered Mite's card, the trigger's sentence \"that creature's controller may draw a card\", and Yes and No with icons; the move is his while he chooses.")
        // STK-032: life changes wait; passing the move only shows and goes on
        compose.runOnIdle {
            assertFalse(toolkit.submit(PlayAction.AdjustLife(f.b, "-1")))
            assertEquals(SharedFlowController.BUSY, toolkit.error)
            assertTrue(toolkit.submit(PlayAction.PassAttention))
        }
        compose.onNodeWithText("Yes").performClick()
        compose.waitForIdle()
        val (batch, request) = sent.single()
        val after = accepted(open, batch, request, f.b)
        assertFalse(Automatum.flowOpen(after.board))
        assertEquals(open.board.containers.single { it.id == bHand }.cards.size + 1, after.board.containers.single { it.id == bHand }.cards.size)
    }

    // STK-031, D11: the other player sees "Bob is choosing · <source>" and may choose for them after confirming
    @Test fun isChoosingAndChooseForThem() {
        val open = fertileDeath()
        val toolkit = render(open, f.a)
        compose.onNodeWithText("Bob is choosing · Fertile Passing").assertExists()
        compose.onRoot().captureShot("$SCREENSHOTS/automatum/resolve-is-choosing.png",
            "The same moment seen by Alice: a slim one-line bar above her hand shows Fertile Passing's card beside \"Bob is choosing · Fertile Passing\" with Choose for them; her step arrow reads Next, greyed, and the move is Bob's while he chooses (\"Waiting · Bob\").")
        compose.onNodeWithText("Choose for them").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Choose for Bob?").assertExists()
        compose.onAllNodesWithText("Choose for them")[1].performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Choosing for Bob").assertExists()
        compose.onNodeWithText("No").performClick()
        compose.waitForIdle()
        val (batch, request) = sent.single()
        val after = accepted(open, batch, request, f.a)
        assertFalse(Automatum.flowOpen(after.board))
        assertEquals(open.board.containers.single { it.id == bHand }.cards, after.board.containers.single { it.id == bHand }.cards)
        assertNull(toolkit.error)
    }

    // STK-030, I-D48, CR 603.3b: two different triggers of Alice's at once are ordered by her in the panel, each its
    // source's card with its own line, numbered as tapped
    @Test fun triggerOrdering() {
        val play = board(bobSpark = false, aliceExtra = listOf(peak, mite))
        val open = aliceCasts(play, "life/${f.b}")
        val flow = assertNotNull(AutomatumBoards.sharedFlow(open, tags(open)))
        assertIs<Decision.OrderTriggers>(flow.decision)
        render(open, f.a)
        compose.onNodeWithText("Order triggers", substring = true).assertExists()
        compose.onNodeWithText("may draw a card", substring = true).performClick()
        compose.waitForIdle()
        captureShot("$SCREENSHOTS/automatum/resolve-trigger-order.png",
            "Alice cast Storm Spark with Peak Ascendancy out: its two triggers wait docked above her hand as their source's cards, each with its own line under it; she tapped the draw trigger first, so it is ringed and numbered 1, and OK beside the eye stays greyed until both are numbered.")
        compose.onNodeWithText("creatures you control get +1/+1", substring = true).performClick()
        compose.onNodeWithText("OK").performClick()
        compose.waitForIdle()
        val (batch, request) = sent.single()
        val after = accepted(open, batch, request, f.a)
        assertTrue(after.board.stackEffects.isNotEmpty() || Automatum.flowOpen(after.board))
    }
}
