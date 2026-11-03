package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import arcanum.api.Layout
import arcanum.parser.OracleCard
import arcanum.parser.OracleParser
import org.asyncttwiz.automatum.PausePoints
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

/**
 * Automatum's pause points on two devices (integration spec I-D41): Alice casts Storm Spark at Bob, who could answer
 * with his own, so the cast's step ends at Bob's pause. Each device runs [AutomatumPauseDriver] as the table does; the
 * server takes their batches onto one shared history. Invented cards parsed from their oracle text as on a device: Storm
 * Spark (instant, 3 damage to any target) and Mountains.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class AutomatumPauseTest {
    @get:Rule val compose = createComposeRule()
    private val f = TableStatesFixture

    private class Invented(val face: DefinitionFace, val oracle: OracleCard) {
        val submitted = CardDefinition(face.name, listOf(face)).let { SubmittedDefinition(DeckJson.digest(it), it) }
        val definition = arcanum.api.CardDefinition(Layout.Normal, listOf(OracleParser.parse(oracle)))
    }
    private fun invented(id: String, name: String, cost: String?, type: String, text: String) = Invented(
        DefinitionFace(id, name, typeLine = type, manaCost = cost.orEmpty(), rulesText = text), OracleCard(name, cost, type, text, null, null))
    private val spark = invented("spark", "Storm Spark", "{R}", "Instant", "Storm Spark deals 3 damage to any target.")
    private val mountain = invented("mountain", "Mountain", null, "Basic Land — Mountain", "")
    private val all = listOf(spark, mountain)

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

    /** Alice's main phase with Automatum on: each player holds Storm Spark with untapped Mountains (Alice two, Bob one);
     * every other hand card is out of the game; the libraries in an unknown order, as every game's start leaves them
     * (pausereview #13: a branch reseeds them, HIS-042). [fullControl]: players with Full control on (SYS-071). */
    private fun board(fullControl: Set<String> = emptySet()): PlayState {
        val initial = f.initial.copy(board = f.initial.board.copy(containers = f.initial.board.containers.map {
            if (it.zone == PlayZone.LIBRARY && it.cards.size >= 2) it.copy(layers = LibraryLayers.initial(f.id("layer/${it.id}"), it.cards)) else it }))
        val aLands = initial.board.cards.filter { it.containerId == aField }.take(2).map { it.id }
        val bLands = initial.board.cards.filter { it.containerId == bField }.take(1).map { it.id }
        val kept = setOf(aSpark, bSpark)
        val dropped = initial.board.cards.filter { (it.containerId == aHand || it.containerId == bHand) && it.id !in kept }.map { it.id }.toSet()
        fun as_(card: PlayCard, what: Invented) = card.copy(definition = what.submitted.digest, faceId = what.face.faceId, corner = "", tapped = false)
        return initial.copy(definitions = initial.definitions + all.map { it.submitted },
            board = initial.board.copy(priority = MtgPriority(f.a),
                players = initial.board.players.map { it.copy(fullControl = it.accountId in fullControl) },
                cards = initial.board.cards.filter { it.id !in dropped }.map { card -> when (card.id) {
                    aSpark, bSpark -> as_(card, spark)
                    in aLands, in bLands -> as_(card, mountain)
                    else -> card
                } },
                containers = initial.board.containers.map { it.copy(cards = it.cards.filter { id -> id !in dropped }) }))
    }

    /** The shared history (the server's): every device's batches are accepted onto it. */
    private var tree: GameplayTree? = null
    private val sent = mutableListOf<Pair<GameAction.PlayBatch, String>>()

    /** The server takes [batch] from [actor], continuing from [from] (a branch when the game has gone on since). */
    private fun accepted(play: PlayState, batch: GameAction.PlayBatch, request: String, actor: String, from: String? = null): PlayState {
        val history = tree ?: GameplayTree(play)
        val command = GameCommand(request, GameScope(play.gameId, playId = play.id), batch, f.id("client"), "1",
            continuation = from?.let { Continuation(it, explicit = true) })
        val change = PlayBatches.change(history, actor, command)
        assertNull(change.reason, "the server takes the batch")
        val next = assertNotNull(change.tree)
        tree = next
        return assertNotNull(next.stateAt(next.currentNodeId))
    }
    /** The one batch a device sent since the last call, as the server takes it. */
    private fun takeSent(play: PlayState, actor: String, from: String? = null): PlayState {
        val (batch, request) = sent.single()
        sent.clear()
        return accepted(play, batch, request, actor, from)
    }

    /** Ordinary (manual) actions a device sent. */
    private val submitted = mutableListOf<PlayAction>()
    /** [viewer]'s device showing [play]; [from]: the point its next action continues from (held, as the app arms it). */
    private fun device(play: PlayState, viewer: String, from: String? = null) = TableToolkitController(viewer, { play }, { true },
        { action, _ -> submitted += action; true }, automatedBatch = { batch, request -> sent += batch to request; true }, playTree = { tree },
        continuation = { from?.let { Continuation(it, explicit = true) } })

    /** Alice casts her Storm Spark at Bob through Automatum, as the app sends it. */
    private fun aliceCasts(play: PlayState): PlayState {
        val alice = device(play, f.a)
        assertTrue(alice.cast.start(aSpark))
        val life = TablePresentation.scene(f.game, play, f.a, 412f, 892f, false).items.first { it.id == "life/${f.b}" }
        alice.open(TableIntent.Actions(life.id, null), life)
        // I-D47: Autotap (on) sends the cast once its last choice is made; only a cast still open waits for Cast
        if (alice.cast.open) assertTrue(alice.cast.cast(), alice.error)
        return takeSent(play, f.a)
    }
    private fun life(play: PlayState, player: String) = play.board.players.single { it.accountId == player }.life.toInt()

    /** Routes as the table's host gives them; [held] the point held. */
    private class Routes : PauseRoutes {
        var held: String? = null
        var released = 0
        override fun hold(node: String): Boolean { held = node; return true }
        override fun holding(node: String) = held == node
        override fun releaseLive() { released++ }
        override fun leaveHold() { held = null }
    }

    /** [viewer]'s device showing [play] live, running its pauses on the test clock. */
    private fun show(play: PlayState, viewer: String, online: Boolean = true, settings: PauseSettings = PauseSettings(),
        routes: PauseRoutes = Routes()): TableToolkitController {
        AutomatumBoards.compute(play, viewer)
        AutomatumBoards.sharedFlow(play, engineTags(tree, play.currentNodeId))
        val toolkit = device(play, viewer).also { it.pause.routes = routes }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme {
                NoRipples {
                    Box(Modifier.fillMaxSize()) {
                        TableSurface(f.game, play, viewer, false, Modifier.fillMaxSize(), connected = true,
                            onIntent = toolkit::handle, toolkit = toolkit,
                            runtime = TableRuntimeHooks(navigation = {}, chat = {}, undo = { true }, redo = { true }, controlsConnected = true))
                        AutomatumPauseDriver(toolkit, null, play, play.currentNodeId, liveShown = true, opponentOnline = online,
                            settings = settings)
                    }
                }
            }
        }
        frames()
        return toolkit
    }
    private fun frames() { repeat(3) { compose.mainClock.advanceTimeByFrame() } }

    // STK-036, STK-037 (I-D41): Bob's pause on his device: the respond panel with its time running out and Hold; the
    // step was Alice's, so his device sends nothing when it runs out (hers goes on); Hold keeps him there
    @Test fun yourPauseRunsOutWithHold() {
        val cast = aliceCasts(board())
        assertEquals(PausePoints.At(f.b, PausePoints.Point.Pause), PausePoints.at(cast.board))
        assertEquals(PauseRole.Yours(2_000, passes = false), LivePause.role(cast.board, f.b, f.a, true, PauseSettings()))
        val routes = Routes()
        val bob = show(cast, f.b, routes = routes)
        compose.mainClock.advanceTimeBy(800)
        compose.onNodeWithText("Alice cast Storm Spark").assertExists()
        compose.onNodeWithContentDescription("Pause time").assertExists()
        compose.onRoot().captureShot("$SCREENSHOTS/automatum/pause-respond-running.png",
            "Alice cast Storm Spark at Bob with Automatum on and Bob could answer with his own: it is his pause, so his respond panel shows a violet bar emptying over his 2 s response time, with Hold beside Respond; his step arrow reads Resolve.")
        compose.onNodeWithText("Hold").performClick()
        frames()
        assertEquals(cast.currentNodeId, routes.held)
        compose.onNodeWithText("Holding — take your time").assertExists()
        compose.onNodeWithContentDescription("Pause time").assertDoesNotExist()
        compose.onRoot().captureShot("$SCREENSHOTS/automatum/pause-held.png",
            "Bob tapped Hold during his pause: the bar is gone and the respond panel reads \"Holding — take your time\" with Respond (the step arrow resolves); he stays at that point as long as he likes.")
        compose.mainClock.advanceTimeBy(3_000)
        assertTrue(sent.isEmpty(), "Bob's device doesn't pass for him")
        assertEquals(0, routes.released, "the table stays held")
        assertNull(bob.error)
    }

    // STK-038 (I-D41): both online, "Wait": Alice's device waits for Bob's response time, showing it, then passes for him
    @Test fun bothOnlineTheActingDeviceWaitsThenGoesOn() {
        val play = board()
        val cast = aliceCasts(play)
        assertEquals(PauseRole.Theirs(f.b, 2_000), LivePause.role(cast.board, f.a, f.a, true, PauseSettings()))
        show(cast, f.a)
        compose.mainClock.advanceTimeBy(800)
        compose.onNodeWithText("Bob may respond to Storm Spark").assertExists()
        compose.onRoot().captureShot("$SCREENSHOTS/automatum/pause-waiting.png",
            "Alice cast Storm Spark at Bob, who is online and could answer: her device shows \"Bob may respond to Storm Spark\" on one line beside the spell's card, with a bar emptying over her 2 s response time while it waits for him; passing for him is her step arrow's Resolve.")
        compose.mainClock.advanceTimeBy(1_000)
        assertTrue(sent.isEmpty(), "still waiting")
        compose.mainClock.advanceTimeBy(300)
        frames()
        val after = takeSent(cast, f.a)
        assertEquals(life(play, f.b) - 3, life(after, f.b), "Alice's device passed for Bob: Storm Spark resolved")
        // The other device's history shows where its player's pause was passed for (STK-037)
        val pass = assertNotNull(tree).let { t -> t.nodes.values.single { it.parentId == cast.currentNodeId } }
        assertTrue(passedFor(pass, f.b))
    }

    private fun notWaitedFor(online: Boolean, settings: PauseSettings) {
        val play = board()
        val cast = aliceCasts(play)
        assertEquals(PauseRole.Theirs(f.b, 0), LivePause.role(cast.board, f.a, f.a, online, settings))
        show(cast, f.a, online = online, settings = settings)
        val after = takeSent(cast, f.a)
        assertEquals(life(play, f.b) - 3, life(after, f.b))
    }

    // STK-038 (I-D41): Bob isn't online (async play): Alice's device never waits, it passes for him at once
    @Test fun anOfflineOpponentIsNotWaitedFor() = notWaitedFor(online = false, PauseSettings())

    // STK-038 (I-D41): Alice's "When the opponent can react" is Proceed at once: no wait even with Bob online
    @Test fun proceedAtOnceDoesNotWait() = notWaitedFor(online = true, PauseSettings(proceedAtOnce = true))

    // STK-037, SYS-071 (I-D41): with Full control Alice keeps priority after her own spell: her own pause, which her own
    // device passes when it runs out; then it is Bob's pause
    @Test fun yourOwnPauseIsPassedByYourDevice() {
        val cast = aliceCasts(board(fullControl = setOf(f.a)))
        assertEquals(PausePoints.At(f.a, PausePoints.Point.Pause), PausePoints.at(cast.board))
        show(cast, f.a)
        compose.mainClock.advanceTimeBy(1_500)
        assertTrue(sent.isEmpty())
        compose.mainClock.advanceTimeBy(600)
        frames()
        val after = takeSent(cast, f.a)
        assertEquals(PausePoints.At(f.b, PausePoints.Point.Pause), PausePoints.at(after.board))
    }

    // STK-039 (I-D41): Bob, offline, didn't respond in time and Alice's device went on; catching up he holds that point
    // and casts his Storm Spark there: a new branch from his pause, with Alice's spell still on the Stack under his
    @Test fun aReactionAfterTheGameWentOnIsABranch() {
        val play = board()
        val cast = aliceCasts(play)
        val alice = device(cast, f.a)
        assertTrue(alice.flows.autoPass(f.b, cast.currentNodeId))
        val went = takeSent(cast, f.a)
        assertEquals(life(play, f.b) - 3, life(went, f.b))
        // Bob at his held pause (the board of that point), responding at Alice
        val bob = device(cast, f.b, from = cast.currentNodeId)
        AutomatumBoards.compute(cast, f.b)
        assertTrue(bob.cast.start(bSpark), bob.error)
        val life = TablePresentation.scene(f.game, cast, f.b, 412f, 892f, false).items.first { it.id == "life/${f.a}" }
        bob.open(TableIntent.Actions(life.id, null), life)
        // I-D47: Autotap (on) sends the cast once its last choice is made; only a cast still open waits for Cast
        if (bob.cast.open) assertTrue(bob.cast.cast(), bob.error)
        val branched = takeSent(cast, f.b, from = cast.currentNodeId)
        val history = assertNotNull(tree)
        assertEquals(2, history.nodes.values.count { it.parentId == cast.currentNodeId }, "two lines go on from Bob's pause")
        assertEquals(life(play, f.a) - 3, life(branched, f.a), "on Bob's branch his Storm Spark hit Alice")
        var cursor = history.currentNodeId
        while (cursor != cast.currentNodeId && cursor in history.nodes) cursor = history.nodes.getValue(cursor).parentId
        assertEquals(cast.currentNodeId, cursor, "the live line now goes on from Bob's pause")
    }

    // STK-037, SYS-071, TRN-063 (I-D41): a pause on an empty Stack (Bob, with Full control, in Alice's main phase after
    // her pass): the panel above the hand names the step, with the time running out and Hold; the step arrow passes
    @Test fun aPauseOnAnEmptyStack() {
        val play = board(fullControl = setOf(f.b))
        sent.clear()
        assertTrue(device(play, f.a).table.perform(TableActionIntent.Next(TableActionScope(play.gameId, play.id))))
        val passed = takeSent(play, f.a)
        assertEquals(PausePoints.At(f.b, PausePoints.Point.Pause), PausePoints.at(passed.board))
        show(passed, f.b)
        compose.mainClock.advanceTimeBy(800)
        compose.onNodeWithText("Precombat main: you may act").assertExists()
        compose.onNodeWithText("Hold").assertExists()
        // UI review round 2 #1 (TRN-063): the step arrow alone says Pass; the pause panel has none of its own
        compose.onNode(hasText("Pass") and hasAnyAncestor(hasContentDescription("Your pause"))).assertDoesNotExist()
        compose.onNodeWithContentDescription("Pass").assertExists()
        compose.onRoot().captureShot("$SCREENSHOTS/automatum/pause-empty-stack.png",
            "Bob has Full control and could cast Storm Spark, so Alice's pass in her main phase gives him a pause: docked full width above his hand \"Precombat main: you may act\" with a violet bar emptying over his response time, the eye at the bottom left and Hold at the right; his step arrow reads Pass.")
    }

    // SYS-071 (I-D41): the device's Full control goes into a game Automatum is on in when the game's value differs
    @Test fun fullControlGoesIntoTheGame() {
        val play = board()
        show(play, f.a, settings = PauseSettings(fullControl = true))
        assertEquals(listOf<PlayAction>(PlayAction.Mtg(MtgAction.SetFullControl(true))), submitted)
        assertTrue(sent.isEmpty(), "Alice's main phase is her stop: nothing is passed")
    }

    // HIS-093 (I-D41): Bob was offline: Alice's device passed for him and the game went on. Catching up, Auto stops on his
    // pause with no time limit, the respond panel shown; holding Auto there branches as usual (STK-039)
    @Test fun playbackStopsAtYourPause() {
        val play = board()
        val cast = aliceCasts(play)
        assertTrue(device(cast, f.a).flows.autoPass(f.b, cast.currentNodeId))
        takeSent(cast, f.a)
        val history = assertNotNull(tree)
        val playback = HistoryPlayback(HistoryTree.ingest(history, "v"))
        playback.openFrom(history.initial.root.id)
        playback.play()
        playback.next(automatic = true, viewer = f.b, autoPause = emptySet())
        assertEquals(cast.currentNodeId, playback.position.cursorId)
        assertTrue(playback.position.playing)
        AutomatumBoards.compute(cast, f.b)
        AutomatumBoards.sharedFlow(cast, engineTags(history, cast.currentNodeId))
        val bob = device(cast, f.b)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme {
                NoRipples {
                    TableSurface(f.game, cast, f.b, false, Modifier.fillMaxSize(), connected = true, onIntent = bob::handle, toolkit = bob,
                        runtime = TableRuntimeHooks(navigation = {}, chat = {}, undo = { true }, reviewing = true,
                            timelinePlayback = playback.status, controlsConnected = true,
                            bottomPanel = { controls -> HistoryPlaybackPanel(playback, history.currentNodeId, f.b, true, 1_000, {},
                                overlay = true, autoPause = emptySet(), board = cast.board, definitions = cast.definitions,
                                animationFinished = true, playControls = controls, onSettings = {}, onTree = {}) }))
                }
            }
        }
        frames()
        assertFalse(playback.position.playing, "Auto stopped at Bob's pause")
        compose.mainClock.advanceTimeBy(10_000)
        assertEquals(cast.currentNodeId, playback.position.cursorId, "no time limit")
        compose.onNodeWithText("Alice cast Storm Spark").assertExists()
        compose.onRoot().captureShot("$SCREENSHOTS/automatum/pause-playback-stop.png",
            "Bob, offline while Alice cast Storm Spark at him, catches up in timeline mode: Auto stopped at his pause (the respond panel shows Alice's spell) and waits there until he plays on or holds Auto to respond on a branch.")
    }
}
