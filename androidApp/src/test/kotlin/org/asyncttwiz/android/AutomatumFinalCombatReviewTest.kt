package org.asyncttwiz.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import arcanum.api.Layout
import arcanum.parser.OracleCard
import arcanum.parser.OracleParser
import org.asyncttwiz.automatum.PausePoints
import org.asyncttwiz.automatum.StepFlow
import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.ParsedCard
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Final adversarial review of combat (I-D29–I-D32, I-D38, I-D39) and pause points (I-D41) on the app (report
 * `arcanum-final-combat-review.md`, outside the repo): the pause overlays during combat. Each ignored test pins a problem
 * found and fails while it isn't fixed. Invented cards parsed from their oracle text as on a device.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class AutomatumFinalCombatReviewTest {
    @get:Rule val compose = createComposeRule()
    private val f = TableStatesFixture

    private class Invented(val face: DefinitionFace, oracle: OracleCard) {
        val submitted = CardDefinition(face.name, listOf(face)).let { SubmittedDefinition(DeckJson.digest(it), it) }
        val definition = arcanum.api.CardDefinition(Layout.Normal, listOf(OracleParser.parse(oracle)))
    }
    private fun invented(id: String, name: String, cost: String?, type: String, text: String, pt: String? = null) = Invented(
        DefinitionFace(id, name, typeLine = type, manaCost = cost.orEmpty(), rulesText = text, corner = pt.orEmpty()),
        OracleCard(name, cost, type, text, pt?.substringBefore("/"), pt?.substringAfter("/")))
    private val brute = invented("brute", "Ironhide Brute", "{3}{G}", "Creature — Beast", "Trample", "5/5")
    private val bear = invented("bear", "Stout Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ogre = invented("ogre", "Hill Ogre", "{2}{R}", "Creature — Ogre", "", "3/3")
    private val spark = invented("spark", "Storm Spark", "{R}", "Instant", "Storm Spark deals 3 damage to any target.")
    private val mountain = invented("mountain", "Mountain", null, "Basic Land — Mountain", "")
    private val all = listOf(brute, bear, ogre, spark, mountain)

    private val aHand = f.id("a/HAND")
    private val bHand = f.id("b/HAND")
    private val aField = f.id("a/BATTLEFIELD")
    private val bField = f.id("b/BATTLEFIELD")

    @Before fun definitions() {
        val byDigest = all.associate { it.submitted.digest to it.definition }
        AutomatumBoards.definitionSource = { submitted -> submitted.mapNotNull { s ->
            byDigest[s.digest]?.let { s.digest to ParsedCard.Read(it, 0) } }.toMap() }
    }
    @After fun forget() {
        kotlinx.coroutines.runBlocking(AutomatumBoards.boardWorker) {}
        AutomatumBoards.reset(); AutomatumBoards.definitionSource = null
    }

    /** Automatum on, turn 3 of Alice at [position]: the first cards of each Battlefield and hand become the given cards
     * (creatures there since turn 1); every other Battlefield and hand card is out of the game. */
    private fun board(position: TurnPosition, priority: MtgPriority?, aliceField: List<Invented>, bobField: List<Invented>,
        aliceHand: List<Invented> = emptyList(), bobHand: List<Invented> = emptyList()): PlayState {
        val initial = f.initial
        fun first(container: String, n: Int) = initial.board.containers.single { it.id == container }.cards.take(n)
        val roles = (first(aField, aliceField.size).zip(aliceField) + first(bField, bobField.size).zip(bobField) +
            first(aHand, aliceHand.size).zip(aliceHand) + first(bHand, bobHand.size).zip(bobHand)).toMap()
        val dropped = initial.board.cards.filter { it.containerId in setOf(aField, bField, aHand, bHand) && it.id !in roles }.map { it.id }.toSet()
        return initial.copy(definitions = initial.definitions + all.map { it.submitted },
            board = initial.board.copy(automatum = true, priority = priority,
                turn = TurnState(f.a, "3", position.phase, position.step),
                cards = initial.board.cards.filter { it.id !in dropped }.map { card -> roles[card.id]?.let { what ->
                    card.copy(definition = what.submitted.digest, faceId = what.face.faceId, corner = what.face.corner, tapped = false,
                        covered = card.containerId == aHand || card.containerId == bHand, enteredTurn = if (card.containerId in setOf(aField, bField)) "1" else "")
                } ?: card },
                containers = initial.board.containers.map { it.copy(cards = it.cards.filter { id -> id !in dropped }) }))
    }
    private fun card(play: PlayState, what: Invented, container: String? = null) =
        play.board.cards.first { it.definition == what.submitted.digest && (container == null || it.containerId == container) }.id

    // ---- The game as the server keeps it

    private var tree: GameplayTree? = null
    private val live = mutableStateOf<PlayState?>(null)
    private var requests = 0
    private val play get() = live.value!!

    private fun start(initial: PlayState) { tree = GameplayTree(initial); live.value = initial }
    private fun accept(actor: String, action: GameAction, request: String = f.id("request/${requests++}")): Boolean {
        val history = tree!!
        val command = GameCommand(request, GameScope(play.gameId, playId = play.id), action, f.id("client"), "1")
        val change = if (action is GameAction.Play) history.play(actor, command) else PlayBatches.change(history, actor, command)
        assertNull(change.reason, "the server takes $action")
        tree = change.tree
        live.value = tree!!.stateAt(tree!!.currentNodeId)
        return true
    }
    private fun toolkit(viewer: String) = TableToolkitController(viewer, { live.value }, { true },
        { action, _ -> accept(viewer, GameAction.Play(action)) },
        playBatch = { actions, request -> accept(viewer, GameAction.PlayBatch(actions), request) },
        playTree = { tree }, automatedBatch = { batch, request -> accept(viewer, batch, request) })

    private fun scene(viewer: String, landscape: Boolean = false) =
        if (landscape) TablePresentation.scene(f.game, play, viewer, 892f, 412f, true) else TablePresentation.scene(f.game, play, viewer, 412f, 892f, false)
    private fun tap(toolkit: TableToolkitController, item: TableItem) = toolkit.open(TableIntent.Actions(item.id, null), item, TableSelection())
    private fun tapCard(toolkit: TableToolkitController, card: String) = tap(toolkit,
        scene(toolkit.viewer).items.first { it.cardId == card && it.viewport != "combat" && it.zone == PlayZone.BATTLEFIELD })
    private fun tapCopy(toolkit: TableToolkitController, card: String) = tap(toolkit,
        scene(toolkit.viewer).items.first { it.viewport == "combat" && it.sourceId == card })
    private fun quick(toolkit: TableToolkitController) = automatumCombatQuickActions(play, toolkitQuickContext(play, TableSelection(), toolkit.viewer)
        .copy(automatumCombat = AutomatumBoards.combat(play) { engineTags(tree, play.currentNodeId) })
        .withCombatTarget(toolkit.combat, play, "Bob"), TableActionScope(play.gameId, play.id)).orEmpty()
    private fun perform(toolkit: TableToolkitController, name: String) = quick(toolkit).single { it.name == name }.let { action ->
        assertTrue(action.enabled, "$name: ${action.refused}")
        toolkit.perform(action.intent)
    }

    /** Where exploratory pictures go (not committed): the FINALREVIEW_SHOTS directory when set. */
    private fun snap(name: String) {
        val dir = System.getenv("FINALREVIEW_SHOTS") ?: return
        java.io.File(dir).mkdirs()
        compose.onRoot().captureRoboImage("$dir/$name.png",
            com.github.takahirom.roborazzi.RoborazziOptions(taskType = com.github.takahirom.roborazzi.RoborazziTaskType.Record))
    }

    /** [toolkit]'s table on the live game with its pause driver running on the test clock ([landscape]: the sidebar layout). */
    private fun show(toolkit: TableToolkitController, landscape: Boolean = false, online: Boolean = true) {
        AutomatumBoards.compute(play, toolkit.viewer)
        AutomatumBoards.combat(play) { engineTags(tree, play.currentNodeId) }
        AutomatumBoards.sharedFlow(play, engineTags(tree, play.currentNodeId))
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme {
                NoRipples {
                    Box(Modifier.fillMaxSize()) {
                        TableSurface(f.game, live.value!!, toolkit.viewer, landscape, Modifier.fillMaxSize(), connected = true,
                            onIntent = toolkit::handle, toolkit = toolkit,
                            runtime = TableRuntimeHooks(navigation = {}, chat = {}, undo = { true }, redo = { true }, controlsConnected = true))
                        AutomatumPauseDriver(toolkit, null, live.value!!, live.value!!.currentNodeId, liveShown = true,
                            opponentOnline = online, settings = PauseSettings(responseMillis = 3_000))
                    }
                }
            }
        }
        repeat(3) { compose.mainClock.advanceTimeByFrame() }
    }

    /** Alice's Ironhide Brute and Stout Bear attack; Bob blocks the Brute with Hill Ogre; Alice (Storm Spark and a Mountain)
     * holds priority at her pause in the declare blockers step (I-D41: the blocks are a pause for both players). */
    private fun blocked(): TableToolkitController {
        start(board(TurnPosition.DECLARE_ATTACKERS, null, aliceField = listOf(brute, bear, mountain), bobField = listOf(ogre),
            aliceHand = listOf(spark), bobHand = listOf(spark)))
        val alice = toolkit(f.a)
        val (bruteCard, bearCard) = listOf(card(play, brute), card(play, bear))
        tapCard(alice, bruteCard); tapCard(alice, bearCard)
        assertTrue(perform(alice, "Commit attack (2)"), alice.error)
        // Bob's pause on the attack (his Storm Spark): he lets it go
        if (TurnPosition.of(play.board.turn) == TurnPosition.DECLARE_ATTACKERS) assertTrue(toolkit(f.b).flows.step(StepFlow.Until.Pass), "pass")
        assertEquals(TurnPosition.DECLARE_BLOCKERS, TurnPosition.of(play.board.turn))
        val bob = toolkit(f.b)
        tapCard(bob, card(play, ogre)); tapCopy(bob, bruteCard)
        assertTrue(perform(bob, "Commit blocks"), bob.error)
        assertEquals(PausePoints.At(f.a, PausePoints.Point.Pause), PausePoints.at(play.board), "Alice's pause on the blocks")
        return toolkit(f.a)
    }

    private fun bounds(description: String): List<Rect> =
        compose.onAllNodesWithContentDescription(description, substring = true).fetchSemanticsNodes().map { it.boundsInRoot }

    // finalreview-combat #2 — I-D41, CMB-029, STK-037: at a pause in combat (the blocks), the thing to react to is the
    // combat area's damage preview, but the pause bar ("Declare blockers: you may act", Hold, Pass) is placed by the Stack
    // (CastPanelPlacement: 300 dp wide, left of the Stack, centred on it) — over the middle of the table where the combat
    // panel sits, covering the preview's badges for the whole response time. Expected: the pause bar leaves the combat
    // panel's preview visible (e.g. docked above the hand or beside the panel)
    @Ignore("finalreview-combat #2: the pause bar covers the combat area's damage preview")
    @Test fun thePauseOnTheBlocksLeavesThePreviewVisible() {
        val alice = blocked()
        show(alice)
        compose.mainClock.advanceTimeBy(500)
        snap("pause-on-blocks-portrait")
        val pause = bounds("Your pause")
        assertEquals(1, pause.size, "Alice's pause bar is shown")
        val preview = bounds("Would take") + bounds("Header would go from") + bounds("Hide the combat area")
        assertTrue(preview.isNotEmpty(), "the preview is shown")
        val covered = preview.filter { it.overlaps(pause.single()) }
        assertTrue(covered.isEmpty(), "the pause bar ${pause.single()} covers the preview at $covered")
    }

    // In landscape the bar sits over the sidebar (life squares), clear of the combat area: a check that holds
    @Test @Config(qualifiers = "w892dp-h412dp-land-mdpi")
    fun thePauseOnTheBlocksLeavesThePreviewVisibleLandscape() {
        val alice = blocked()
        show(alice, landscape = true)
        compose.mainClock.advanceTimeBy(500)
        snap("pause-on-blocks-landscape")
        val pause = bounds("Your pause")
        assertEquals(1, pause.size, "Alice's pause bar is shown")
        val preview = bounds("Would take") + bounds("Header would go from") + bounds("Hide the combat area")
        val covered = preview.filter { it.overlaps(pause.single()) }
        assertTrue(covered.isEmpty(), "the pause bar ${pause.single()} covers the preview at $covered")
    }
}
