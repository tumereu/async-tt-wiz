package org.asyncttwiz.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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
import kotlin.test.assertTrue

/**
 * The opening's Keep / Bottom flow drawn on a real canvas (TRN-062, I-D49, I-D46 (b)): two devices on one shared history,
 * Alice's table rendered while she shakes an unplayable hand card, mulligans, keeps and puts a card on the bottom — every
 * frame of the shakes, the card flying to her library and the panel closing draws without unbalancing the canvas
 * (playtest crash 2026-11-03: "Underflow in restore - more restores than saves" on Keep).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class AutomatumKeepDrawTest {
    @get:Rule val compose = createComposeRule()

    private fun id(value: String) = java.util.UUID.nameUUIDFromBytes("keep-draw/$value".toByteArray()).toString()
    private val alice = id("alice")
    private val bob = id("bob")

    private val mountainFace = DefinitionFace("mountain", "Mountain", typeLine = "Basic Land — Mountain")
    private val mountain = CardDefinition("Mountain", listOf(mountainFace)).let { SubmittedDefinition(DeckJson.digest(it), it) }
    private val parsed = arcanum.api.CardDefinition(Layout.Normal,
        listOf(OracleParser.parse(OracleCard("Mountain", null, "Basic Land — Mountain", "", null, null))))

    @Before fun definitions() {
        AutomatumBoards.definitionSource = { submitted -> submitted.filter { it.digest == mountain.digest }
            .associate { it.digest to ParsedCard.Read(parsed, 0) } }
    }
    @After fun forget() { AutomatumBoards.reset(); AutomatumBoards.definitionSource = null }

    /** A new game as the server starts it: both players "Mulliganing" with their opening seven drawn (I-D49). */
    private fun newGame(starter: String = alice): PlayState {
        val cards = mutableListOf<PlayCard>()
        val containers = mutableListOf<PlayContainer>()
        for (player in listOf(alice, bob)) {
            val ids = List(20) { id("$player/card/$it") }
            val drawn = ids.take(7)
            val rest = ids - drawn.toSet()
            ids.forEach { cards += PlayCard(it, mountain.digest, player, id("$player/${if (it in drawn) "HAND" else "LIBRARY"}"), mountainFace.faceId) }
            for (zone in PlayZone.entries.filter { it != PlayZone.STACK }) containers += PlayContainer(id("$player/$zone"), zone, player,
                when (zone) { PlayZone.LIBRARY -> rest; PlayZone.HAND -> drawn; else -> emptyList() },
                layers = if (zone == PlayZone.LIBRARY) LibraryLayers.initial(id("$player/layer"), rest) else emptyList())
        }
        containers += PlayContainer(id("STACK"), PlayZone.STACK)
        val seed = RandomV1.encodeSeed(ByteArray(32) { it.toByte() })
        return PlayState(id("game"), id("play"), id("round"), id("start"), PlayRoot(id("root"), id("branch")), id("root"),
            BoardState(listOf(alice, bob).map { PlayPlayer(it, status = PlayerStatus.OPENING) }, cards.sortedBy { it.id },
                containers, TurnState(starter)),
            listOf(mountain), listOf(alice, bob).map { p -> InitialShuffle(p, seed, cards.filter { it.owner == p }.map { it.id }) },
            InitialStarter(StarterChoice(starter), starter))
    }

    // ---- The server: one shared history for both devices; Alice's screen shows its current state
    private lateinit var tree: GameplayTree
    private var requests = 0
    private val play: PlayState get() = tree.stateAt(tree.currentNodeId)!!
    private var shown by mutableStateOf<PlayState?>(null)
    private var view: android.view.View? = null
    private fun apply(actor: String, action: GameAction, request: String = id("request/${requests++}")): Boolean {
        val command = GameCommand(request, GameScope(play.gameId, playId = play.id), action, id("client/$actor"), "1")
        val change = AutomatumOnly.change(tree, actor, command)
        change.tree?.let { tree = it; shown = play }
        return change.tree != null
    }
    private fun device(viewer: String) = TableToolkitController(viewer, { play }, { true },
        { action, _ -> apply(viewer, GameAction.Play(action)) },
        playBatch = { actions, request -> apply(viewer, GameAction.PlayBatch(actions), request) },
        playTree = { tree }, automatedBatch = { batch, request -> apply(viewer, batch, request) })
    private fun quick(toolkit: TableToolkitController, name: String) =
        QuickActions.contextual(play, QuickContext(viewer = toolkit.viewer)).single { it.name == name }
    private fun hand(player: String) = play.board.containers.single { it.zone == PlayZone.HAND && it.accountId == player }.cards
    private fun library(player: String) = play.board.containers.single { it.zone == PlayZone.LIBRARY && it.accountId == player }

    /** Draws every frame of the next [millis] on a real canvas, as the phone's renderer does. */
    private fun drawFrames(millis: Long = 700) {
        var at = 0L
        while (at < millis) {
            compose.mainClock.advanceTimeBy(16)
            compose.runOnUiThread {
                val root = view!!.rootView
                val bitmap = android.graphics.Bitmap.createBitmap(root.width, root.height, android.graphics.Bitmap.Config.ARGB_8888)
                root.draw(android.graphics.Canvas(bitmap))
            }
            at += 16
        }
    }

    /** A tap on [card] in Alice's hand, as the table's gestures make it. */
    private fun tapHand(toolkit: TableToolkitController, card: String) {
        val item = TablePresentation.scene(game, play, alice, 412f, 892f, false, toolkit.layoutState.value).items
            .single { it.cardId == card && it.zone == PlayZone.HAND }
        compose.runOnIdle { toolkit.open(TableIntent.Actions(item.id, TableScope(card, listOf(card))), item) }
    }

    private val game get() = LiveTableFixture.game.copy(id = play.gameId, playId = play.id,
        inviter = AccountIdentity(alice, "Alice"), recipient = AccountIdentity(bob, "Bob"))

    /** Alice's table on her device, drawn frame by frame from here on. */
    private fun render(a: TableToolkitController) {
        val summary = game
        compose.mainClock.autoAdvance = false
        compose.setContent {
            view = androidx.compose.ui.platform.LocalView.current
            MaterialTheme {
                NoRipples {
                    Box(Modifier.fillMaxSize()) {
                        shown?.let { current ->
                            TableSurface(summary, current, alice, false, Modifier.fillMaxSize(), connected = true,
                                onIntent = a::handle, toolkit = a,
                                runtime = TableRuntimeHooks(navigation = {}, chat = {}, undo = { true }, redo = { true }, controlsConnected = true))
                        }
                    }
                }
            }
        }
        drawFrames()
    }

    private fun perform(toolkit: TableToolkitController, name: String) {
        compose.runOnIdle { assertTrue(toolkit.table.perform(quick(toolkit, name).intent), toolkit.error) }
        drawFrames()
    }

    // TRN-062, TRN-057 (I-D49), I-D46 (b): as in the playtest crash: Bob starts; Alice shakes a land she can't play yet,
    // takes her free mulligan, Bob keeps, Alice keeps seven and Bob's first turn begins; her table draws every frame of it
    @Test fun freeMulliganThenKeepStartsTheGameAndDraws() {
        tree = GameplayTree(newGame(starter = bob))
        shown = play
        val a = device(alice)
        val b = device(bob)
        render(a)
        tapHand(a, hand(alice).first())
        drawFrames()
        perform(a, QuickActions.MULLIGAN)
        tapHand(a, hand(alice)[2])
        drawFrames()
        perform(b, QuickActions.KEEP)
        perform(a, QuickActions.KEEP)
        drawFrames(1500)
        assertFalse(PlayerStatus.opening(play.board, alice))
        assertFalse(PlayerStatus.opening(play.board, bob))
        assertEquals(7, hand(alice).size)
    }

    // TRN-062 (I-D49, I-D48), I-D46 (b): Alice shakes a land she can't play yet, takes her free mulligan and one more,
    // keeps and puts one card on the bottom while Bob mulligans; her table draws every frame of it
    @Test fun keepWithABottomCardDraws() {
        tree = GameplayTree(newGame())
        shown = play
        val a = device(alice)
        val b = device(bob)
        render(a)
        tapHand(a, hand(alice).first())
        drawFrames()
        repeat(2) { perform(a, QuickActions.MULLIGAN) }
        perform(a, QuickActions.KEEP)
        assertTrue(a.keeping, "the Keep / Bottom screen opened")
        tapHand(a, hand(alice).last())
        drawFrames()
        perform(b, QuickActions.MULLIGAN)
        compose.onAllNodes(hasContentDescription("Mountain") and hasAnyAncestor(hasContentDescription("Keep area")))[0]
            .performTouchInput { down(center); advanceEventTime(60); up() }
        drawFrames()
        compose.onNodeWithText("OK").performClick()
        drawFrames(1500)
        assertFalse(a.keeping)
        assertEquals(6, hand(alice).size)
        assertEquals(20 - 6, library(alice).cards.size)
    }

    // TRN-062: the playtest crash ("Underflow in restore - more restores than saves" in the drawing after Keep): a
    // renderer that doesn't push a layer for a fading card must not let the table pop the saves of the nodes around
    // it. Every card fades and one shakes, on a canvas whose layers push nothing; the canvas is left as it was.
    @Test fun aFadeLayerTheRendererSkipsLeavesTheCanvasBalanced() {
        tree = GameplayTree(newGame())
        val scene = TablePresentation.scene(game, play, alice, 412f, 892f, false, TableLayoutState())
        val cards = scene.items.filter { it.kind == TableItemKind.CARD && it.visible.isNotEmpty() }
        assertTrue(cards.size >= 7, "the hand is drawn")
        val bitmap = android.graphics.Bitmap.createBitmap(412, 892, android.graphics.Bitmap.Config.ARGB_8888)
        val skipping = object : android.graphics.Canvas(bitmap) {
            override fun saveLayer(left: Float, top: Float, right: Float, bottom: Float, paint: android.graphics.Paint?,
                saveFlags: Int): Int = saveCount
            override fun saveLayer(left: Float, top: Float, right: Float, bottom: Float, paint: android.graphics.Paint?): Int =
                saveCount
        }
        val before = skipping.saveCount
        androidx.compose.ui.graphics.drawscope.CanvasDrawScope().draw(androidx.compose.ui.unit.Density(1f),
            androidx.compose.ui.unit.LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(skipping),
            androidx.compose.ui.geometry.Size(412f, 892f)) {
            drawTable(scene, alice, alice, TableGesturePreview(), alphas = cards.associate { it.id to .5f },
                cache = TableDrawCache(1f), shake = cards.first().id to 2f)
        }
        assertEquals(before, skipping.saveCount)
    }
}
