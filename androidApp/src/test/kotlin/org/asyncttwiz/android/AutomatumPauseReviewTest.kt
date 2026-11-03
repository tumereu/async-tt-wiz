package org.asyncttwiz.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import arcanum.api.Layout
import arcanum.parser.OracleCard
import arcanum.parser.OracleParser
import org.asyncttwiz.automatum.PausePoints
import org.asyncttwiz.automatum.StepFlow
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
 * Adversarial review of Automatum's pause points (integration spec I-D41, "pausereview"): two devices on one shared
 * history, each running [AutomatumPauseDriver] as the table does. Each "pausereview #N" test was a finding of the review
 * (~/projects/atw-cache/arcanum-pause-review.md), now fixed. Invented cards parsed from their oracle
 * text: Storm Spark (instant, 3 damage to any target), Mind Prick (sorcery, target player discards a card), basic lands.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-port-mdpi")
class AutomatumPauseReviewTest {
    @get:Rule val compose = createComposeRule()
    private val f = TableStatesFixture

    private class Invented(val face: DefinitionFace, val oracle: OracleCard) {
        val submitted = CardDefinition(face.name, listOf(face)).let { SubmittedDefinition(DeckJson.digest(it), it) }
        val definition = arcanum.api.CardDefinition(Layout.Normal, listOf(OracleParser.parse(oracle)))
    }
    private fun invented(id: String, name: String, cost: String?, type: String, text: String) = Invented(
        DefinitionFace(id, name, typeLine = type, manaCost = cost.orEmpty(), rulesText = text), OracleCard(name, cost, type, text, null, null))
    private val spark = invented("spark", "Storm Spark", "{R}", "Instant", "Storm Spark deals 3 damage to any target.")
    private val prick = invented("prick", "Mind Prick", "{B}", "Sorcery", "Target player discards a card.")
    private val mountain = invented("mountain", "Mountain", null, "Basic Land — Mountain", "")
    private val swamp = invented("swamp", "Swamp", null, "Basic Land — Swamp", "")
    private val all = listOf(spark, prick, mountain, swamp)

    private val aHand = f.id("a/HAND")
    private val bHand = f.id("b/HAND")
    private val aField = f.id("a/BATTLEFIELD")
    private val bField = f.id("b/BATTLEFIELD")
    private fun handCards(hand: String) = f.initial.board.containers.single { it.id == hand }.cards

    @Before fun definitions() {
        val byDigest = all.associate { it.submitted.digest to it.definition }
        AutomatumBoards.definitionSource = { submitted -> submitted.mapNotNull { s ->
            byDigest[s.digest]?.let { s.digest to ParsedCard.Read(it, 0) } }.toMap() }
    }
    @After fun forget() { AutomatumBoards.reset(); AutomatumBoards.definitionSource = null; DeviceRandom.seeded(null) }

    /** Alice's main phase with Automatum on: the first hand cards of each player become [aliceHand] / [bobHand], the
     * first lands [aliceLands] / [bobLands] (untapped); every other hand card is out of the game. */
    private fun board(aliceHand: List<Invented>, bobHand: List<Invented>, aliceLands: List<Invented>, bobLands: List<Invented>,
        fullControl: Set<String> = emptySet(), shuffled: Boolean = true): PlayState {
        // [shuffled]: libraries in an unknown order, as every game's start leaves them (one unordered layer each); on by
        // default, so a branch's reseed (HIS-042) can't hide from these tests again (#13)
        val initial = if (!shuffled) f.initial else f.initial.copy(board = f.initial.board.copy(containers = f.initial.board.containers.map {
            if (it.zone == PlayZone.LIBRARY && it.cards.size >= 2) it.copy(layers = LibraryLayers.initial(f.id("layer/${it.id}"), it.cards)) else it }))
        val aCards = handCards(aHand).take(aliceHand.size).zip(aliceHand).toMap()
        val bCards = handCards(bHand).take(bobHand.size).zip(bobHand).toMap()
        check(aCards.size == aliceHand.size && bCards.size == bobHand.size) { "the fixture's hands are too small" }
        val aLand = initial.board.cards.filter { it.containerId == aField }.take(aliceLands.size).map { it.id }.zip(aliceLands).toMap()
        val bLand = initial.board.cards.filter { it.containerId == bField }.take(bobLands.size).map { it.id }.zip(bobLands).toMap()
        val kept = aCards.keys + bCards.keys
        val dropped = initial.board.cards.filter { (it.containerId == aHand || it.containerId == bHand) && it.id !in kept }.map { it.id }.toSet()
        val assigned = aCards + bCards + aLand + bLand
        return initial.copy(definitions = initial.definitions + all.map { it.submitted },
            board = initial.board.copy(priority = MtgPriority(f.a),
                players = initial.board.players.map { it.copy(fullControl = it.accountId in fullControl) },
                cards = initial.board.cards.filter { it.id !in dropped }.map { card -> assigned[card.id]?.let { what ->
                    card.copy(definition = what.submitted.digest, faceId = what.face.faceId, corner = "", tapped = false) } ?: card },
                containers = initial.board.containers.map { it.copy(cards = it.cards.filter { id -> id !in dropped }) }))
    }
    private fun sparkBoard(fullControl: Set<String> = emptySet()) =
        board(listOf(spark), listOf(spark), listOf(mountain, mountain), listOf(mountain), fullControl)
    private fun card(play: PlayState, hand: String, index: Int = 0) = play.board.containers.single { it.id == hand }.cards[index]

    // ---- The server: every device's batches go onto one shared history

    private var tree: GameplayTree? = null
    private val sent = mutableListOf<Pair<GameAction.PlayBatch, String>>()
    private fun command(play: PlayState, request: String, action: GameAction, from: String?) = GameCommand(request,
        GameScope(play.gameId, playId = play.id), action, f.id("client"), "1", continuation = from?.let { Continuation(it, explicit = true) })
    /** The server's answer to [batch] from [actor] (continuing from [from]: a branch when the game has gone on). */
    private fun serve(play: PlayState, batch: GameAction.PlayBatch, request: String, actor: String, from: String? = null): GameplayTreeChange {
        val history = tree ?: GameplayTree(play)
        // As the server: its gate, then the shared reducer (pivot review #11)
        val change = AutomatumOnly.change(history, actor, command(play, request, batch, from))
        change.tree?.let { tree = it }
        return change
    }
    private fun accepted(play: PlayState, batch: GameAction.PlayBatch, request: String, actor: String, from: String? = null): PlayState {
        val change = serve(play, batch, request, actor, from)
        assertNull(change.reason, "the server takes the batch")
        return assertNotNull(tree!!.stateAt(tree!!.currentNodeId))
    }
    private fun takeSent(play: PlayState, actor: String, from: String? = null): PlayState {
        val (batch, request) = sent.single()
        sent.clear()
        return accepted(play, batch, request, actor, from)
    }

    private val submitted = mutableListOf<Pair<String, PlayAction>>()
    /** [viewer]'s device showing [state]; [from]: the point its next action continues from (held, as the app arms it). */
    private fun device(state: () -> PlayState, viewer: String, tag: String = viewer, from: () -> String? = { null }) =
        TableToolkitController(viewer, state, { true }, { action, _ -> submitted += tag to action; true },
            automatedBatch = { batch, request -> sent += batch to request; true }, playTree = { tree },
            continuation = { from()?.let { Continuation(it, explicit = true) } })
    private fun device(play: PlayState, viewer: String, from: String? = null) = device({ play }, viewer, from = { from })

    /** [viewer] casts their hand card [cardId] at [target]'s life square through Automatum, as the app does; sends it. */
    private fun castAt(play: PlayState, viewer: String, cardId: String, target: String, from: String? = null): PlayState {
        val toolkit = device(play, viewer)
        AutomatumBoards.compute(play, viewer)
        assertTrue(toolkit.cast.start(cardId), toolkit.error)
        targetLife(toolkit, play, viewer, target)
        // I-D47: Autotap (on) sends the cast once its last choice is made; only a cast still open waits for Cast
        if (toolkit.cast.open) assertTrue(toolkit.cast.cast(), toolkit.error)
        return takeSent(play, viewer, from)
    }
    private fun targetLife(toolkit: TableToolkitController, play: PlayState, viewer: String, target: String) {
        val life = TablePresentation.scene(f.game, play, viewer, 412f, 892f, false).items.first { it.id == "life/$target" }
        toolkit.open(TableIntent.Actions(life.id, null), life)
    }
    private fun life(play: PlayState, player: String) = play.board.players.single { it.accountId == player }.life.toInt()

    private class Routes : PauseRoutes {
        var held: String? = null
        override fun hold(node: String): Boolean { held = node; return true }
        override fun holding(node: String) = held == node
        override fun releaseLive() {}
        override fun leaveHold() { held = null }
    }

    /** The board each shown device sees (switchable: the driver follows it). */
    private val shown = mutableStateOf<PlayState?>(null)

    /** [viewer]'s device showing [play] live (switchable through [shown]), running its pauses on the test clock. */
    private fun show(play: PlayState, viewer: String, online: Boolean = true, settings: PauseSettings = PauseSettings(),
        routes: PauseRoutes = Routes()): TableToolkitController {
        shown.value = play
        AutomatumBoards.compute(play, viewer)
        AutomatumBoards.sharedFlow(play, engineTags(tree, play.currentNodeId))
        val toolkit = device({ shown.value!! }, viewer).also { it.pause.routes = routes }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme {
                NoRipples {
                    val now = shown.value!!
                    Box(Modifier.fillMaxSize()) {
                        TableSurface(f.game, now, viewer, false, Modifier.fillMaxSize(), connected = true,
                            onIntent = toolkit::handle, toolkit = toolkit,
                            runtime = TableRuntimeHooks(navigation = {}, chat = {}, undo = { true }, controlsConnected = true))
                        AutomatumPauseDriver(toolkit, null, now, now.currentNodeId, liveShown = true, opponentOnline = online,
                            settings = settings)
                    }
                }
            }
        }
        frames()
        return toolkit
    }
    private fun frames() { repeat(3) { compose.mainClock.advanceTimeByFrame() } }
    /** The shown device now shows [play] (the live game moved). */
    private fun showNow(play: PlayState) {
        shown.value = play
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        frames()
    }

    // pausereview #1 (STK-039, I-D41: a reaction after the acting player moved on uses branching): Bob starts casting his
    // Storm Spark from his hand during his pause (double-tap or the hand menu's Cast, not Respond). Beginning a cast or
    // ability at one's own pause holds it, so it continues from there instead of being dropped when Alice's device passes.
    @Test fun aCastBegunDuringYourPauseHoldsIt() {
        val cast = castAt(sparkBoard(), f.a, card(sparkBoard(), aHand), f.b)
        val routes = Routes()
        val bob = show(cast, f.b, routes = routes)
        compose.mainClock.advanceTimeBy(500)
        assertTrue(bob.cast.start(card(cast, bHand)), bob.error)
        compose.mainClock.advanceTimeBy(2_000)
        frames()
        assertEquals(cast.currentNodeId, routes.held, "the pause is held while Bob casts, so his cast continues from it")
    }

    // pausereview #2 (STK-037/STK-039): Bob's response time is 1 s, Alice's 3 s. His bar has run out but the game is still
    // at his pause (Alice's device still waits); he taps Respond: it holds, the bar running or not, so Alice's device
    // doesn't pass for him while he picks targets.
    @Test fun respondAfterYourBarRanOutStillHolds() {
        val cast = castAt(sparkBoard(), f.a, card(sparkBoard(), aHand), f.b)
        val routes = Routes()
        show(cast, f.b, settings = PauseSettings(responseMillis = 1_000), routes = routes)
        compose.mainClock.advanceTimeBy(1_500)
        frames()
        compose.onNodeWithText("Respond").performClick()
        frames()
        assertEquals(cast.currentNodeId, routes.held)
    }

    // pausereview #1/#2 (STK-037, STK-039): Alice's device went on at once (Proceed at once); Bob's table still shows his
    // pause for his response time while his device acts on the live game. Respond holds the pause shown, not the live
    // game, and his Storm Spark from there is a branch.
    @Test fun respondOnTheTableHeldAtYourPauseBranches() {
        val cast = castAt(sparkBoard(), f.a, card(sparkBoard(), aHand), f.b)
        AutomatumBoards.compute(cast, f.a)
        assertTrue(device(cast, f.a).flows.autoPass(f.b, cast.currentNodeId))
        val went = takeSent(cast, f.a)
        val routes = Routes()
        // As the app's toolkit: the live game, or the held point's board once held (TableScreen's tableToolkit)
        val bob = device({ if (routes.held != null) cast else went }, f.b, from = { routes.held }).also { it.pause.routes = routes }
        AutomatumBoards.compute(cast, f.b)
        AutomatumBoards.sharedFlow(cast, engineTags(tree, cast.currentNodeId))
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme {
                NoRipples {
                    Box(Modifier.fillMaxSize()) {
                        TableSurface(f.game, cast, f.b, false, Modifier.fillMaxSize(), connected = true,
                            onIntent = bob::handle, toolkit = bob,
                            runtime = TableRuntimeHooks(navigation = {}, chat = {}, undo = { true }, controlsConnected = true))
                        AutomatumPauseDriver(bob, null, cast, went.currentNodeId, liveShown = true, opponentOnline = true,
                            settings = PauseSettings())
                    }
                }
            }
        }
        frames()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("Respond").performClick()
        frames()
        assertEquals(cast.currentNodeId, routes.held, "the pause shown is held")
        assertTrue(bob.cast.start(card(cast, bHand)), bob.error)
        targetLife(bob, cast, f.b, f.a)
        // I-D47: Autotap (on) sends the cast once its last choice is made; only a cast still open waits for Cast
        if (bob.cast.open) assertTrue(bob.cast.cast(), bob.error)
        takeSent(cast, f.b, from = cast.currentNodeId)
        assertEquals(2, tree!!.nodes.values.count { it.parentId == cast.currentNodeId }, "a branch from Bob's pause")
    }

    // pausereview #3 (STK-037, SYS-071): with Full control Alice keeps priority after her Storm Spark (her own pause) and
    // starts casting her second one. Her own device doesn't pass for her while her cast panel is open (the cast holds her
    // pause, and no automatic pass runs under an open cast), so her first spell doesn't resolve under the panel.
    @Test fun yourDeviceDoesNotPassForYouWhileYouCast() {
        val start = board(listOf(spark, spark), listOf(spark), listOf(mountain, mountain), listOf(mountain), fullControl = setOf(f.a))
        val cast = castAt(start, f.a, card(start, aHand, 0), f.b)
        assertEquals(PausePoints.At(f.a, PausePoints.Point.Pause), PausePoints.at(cast.board))
        val alice = show(cast, f.a)
        compose.mainClock.advanceTimeBy(500)
        assertTrue(alice.cast.start(card(cast, aHand, 1)), alice.error)
        compose.mainClock.advanceTimeBy(2_000)
        frames()
        assertTrue(sent.isEmpty(), "nothing is passed for Alice while she casts")
    }

    // pausereview #4 (SYS-004, STK-038): Bob is offline, so Alice's device passed for him at once and Storm Spark resolved.
    // Alice takes that back with Undo (her own step): the board is again Bob's pause made by her cast. A pause the device
    // already passed and that Undo brought back isn't passed again by itself, so she can undo back before the resolution.
    @Test fun undoOfTheAutomaticPassIsNotRedone() {
        val cast = castAt(sparkBoard(), f.a, card(sparkBoard(), aHand), f.b)
        show(cast, f.a, online = false)
        val resolved = takeSent(cast, f.a)
        assertEquals(life(cast, f.b) - 3, life(resolved, f.b))
        showNow(resolved)
        assertTrue(sent.isEmpty(), "Alice's main phase is her stop")
        val undo = HistoryContinuation.change(tree!!, f.a, command(cast, f.id("undo"), GameAction.Undo, null))
        assertNull(undo.reason, "Alice may undo her own step")
        tree = undo.tree
        val back = assertNotNull(tree!!.stateAt(tree!!.currentNodeId))
        assertEquals(cast.currentNodeId, back.currentNodeId, "back at Bob's pause")
        showNow(back)
        compose.mainClock.advanceTimeBy(3_000)
        assertTrue(sent.isEmpty(), "the undone pass isn't sent again by itself")
    }

    // pausereview #5 (SYS-071, LOB-113): Alice uses two devices (Full control on in one, off in the other, the setting being
    // per device). A device sends its value only after it was changed on it and otherwise follows the game's, so with both
    // open SetFullControl isn't sent back and forth: at most once per device and setting change.
    @Test fun twoDevicesOfOnePlayerDoNotFightOverFullControl() {
        var game = sparkBoard()
        val state = mutableStateOf(game)
        val phone = device({ state.value }, f.a, "phone")
        val tablet = device({ state.value }, f.a, "tablet")
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val now = state.value
            AutomatumPauseDriver(phone, null, now, now.currentNodeId, liveShown = true, opponentOnline = true,
                settings = PauseSettings(fullControl = true))
            AutomatumPauseDriver(tablet, null, now, now.currentNodeId, liveShown = true, opponentOnline = true,
                settings = PauseSettings(fullControl = false))
        }
        repeat(8) { round ->
            frames()
            val change = submitted.lastOrNull()?.second as? PlayAction.Mtg ?: return@repeat
            val on = (change.action as MtgAction.SetFullControl).on
            if (game.board.players.single { it.accountId == f.a }.fullControl == on) return@repeat
            game = game.copy(currentNodeId = f.id("full-control/$round"), board = game.board.copy(players = game.board.players.map {
                if (it.accountId == f.a) it.copy(fullControl = on) else it }))
            state.value = game
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
            compose.mainClock.advanceTimeBy(100)
        }
        assertTrue(submitted.size <= 2, "SetFullControl sent ${submitted.size} times: ${submitted.map { it.first }}")
    }

    // pausereview #6 (HIS-093, HIS auto-pause): catching up, Auto pauses after Alice's cast (her Stack entry: the default
    // "Added to Stack" auto-pause) and that point is also Bob's pause. Bob presses play: a point Auto already paused at
    // isn't a pause stop again (one stop for one pause).
    @Test fun anAutoPauseAndAPauseStopStopOnce() {
        fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"
        fun node(n: Int, parent: Int, key: String, author: Int = 70, action: PlayAction = PlayAction.SetLife(id(70), "$n")) =
            GameplayMutation(id(n), id(parent), id(50), id(n + 100), id(author), null, action, GameplayTransition(),
                grouping = HistorySemantic(HistorySignificance.CROSS_ZONE_MOVE, HistoryIcon.FLAG, key))
        val before = node(1, 0, "a1")
        // Alice's step that ends at Bob's pause: here one passing Bob the move, which the default auto-pauses stop at
        val step = node(2, 1, "b2", action = PlayAction.PassAttention).copy(transition = GameplayTransition(attention = AttentionChange(id(70), id(71))))
        val pass = node(3, 2, "c3", action = PlayAction.Mtg(MtgAction.SetPriority(MtgPriority(id(71), 1), MtgPriority(id(70)))))
        val after = node(4, 3, "d4")
        val nodes = listOf(before, step, pass, after)
        val player = HistoryPlayback(HistoryTree(id(0), nodes, id(4), "v4", emptyMap()))
        player.openFrom(id(1)); player.play()
        player.next(automatic = true, viewer = id(71), autoPause = PlaybackAutoPause.defaults)
        assertEquals(id(2), player.position.cursorId)
        assertTrue(player.pauseAfterAnimation(), "the move's auto-pause")
        player.play()
        assertFalse(player.stopAtPause(id(71), pause = true), "already stopped at this point once")
    }

    // pausereview #6 (HIS-093): Auto stopped at Bob's pause; a ping right after it leaves the same pause shown, which is
    // no second stop; the next pause is one again
    @Test fun aPingAfterAPauseIsNoSecondStop() {
        fun id(n: Int) = "00000000-0000-4000-8000-${n.toString().padStart(12, '0')}"
        fun node(n: Int, parent: Int, author: Int = 70) = GameplayMutation(id(n), id(parent), id(50), id(n + 100), id(author), null,
            PlayAction.SetLife(id(70), "$n"), GameplayTransition(),
            grouping = HistorySemantic(HistorySignificance.CROSS_ZONE_MOVE, HistoryIcon.FLAG, "k$n"))
        val player = HistoryPlayback(HistoryTree(id(0), listOf(node(1, 0), node(2, 1), node(3, 2), node(4, 3), node(5, 4)), id(5), "v5", emptyMap()))
        player.openFrom(id(1)); player.play()
        player.next(automatic = true, viewer = id(71), autoPause = emptySet())
        assertTrue(player.stopAtPause(id(71), pause = true, key = "spark"), "Bob's pause")
        player.play()
        player.next(automatic = true, viewer = id(71), autoPause = emptySet())
        assertFalse(player.stopAtPause(id(71), pause = true, key = "spark"), "the ping after it shows the same pause")
        player.next(automatic = true, viewer = id(71), autoPause = emptySet())
        assertTrue(player.stopAtPause(id(71), pause = true, key = "bolt"), "another pause")
    }

    // pausereview #12 (HIS-091): the pass Alice's device made for Bob after his pause reads with both names
    @Test fun aPassForTheOtherPlayerNamesBoth() {
        val pass = GameplayMutation(f.id("pass"), f.id("cast"), f.id("branch"), f.id("request"), f.a, null,
            PlayAction.Mtg(MtgAction.SetPriority(MtgPriority(f.b, 1), MtgPriority(f.a))), GameplayTransition())
        val names = mapOf(f.a to "alice", f.b to "bob")
        assertEquals("alice passed for bob", historyText(pass, usernames = names).description)
        assertEquals("Priority → alice", historyText(pass.copy(author = f.b), usernames = names).description, "his own pass")
    }

    // pausereview #7 (STK-039, SYS-062; I-D41 decision A): Alice's Mind Prick at Bob is his pause; offline, Alice's device
    // passes for him and the spell resolves: Bob must choose a card to discard (an open Automatum choice). Catching up,
    // Bob holds his pause and casts Storm Spark there: a branch, leaving the live line's open choice behind with it.
    @Test fun aLateReactionBranchesWhileTheLiveGameHasAnOpenChoice() {
        val start = board(listOf(prick), listOf(spark, mountain), listOf(swamp), listOf(mountain))
        val cast = castAt(start, f.a, card(start, aHand), f.b)
        assertEquals(PausePoints.At(f.b, PausePoints.Point.Pause), PausePoints.at(cast.board))
        AutomatumBoards.compute(cast, f.a)
        assertTrue(device(cast, f.a).flows.autoPass(f.b, cast.currentNodeId))
        val went = takeSent(cast, f.a)
        assertTrue(Automatum.flowOpen(went.board), "Bob's discard is open on the live game")
        // Bob at his held pause (its board), responding at Alice: a branch from there
        val bob = device(cast, f.b, from = cast.currentNodeId)
        AutomatumBoards.compute(cast, f.b)
        assertTrue(bob.cast.start(card(cast, bHand, 0)), bob.error)
        targetLife(bob, cast, f.b, f.a)
        // I-D47: Autotap (on) sends the cast once its last choice is made; only a cast still open waits for Cast
        if (bob.cast.open) assertTrue(bob.cast.cast(), bob.error)
        val (batch, request) = sent.single()
        val change = serve(cast, batch, request, f.b, from = cast.currentNodeId)
        assertNull(change.reason, "the late reaction is a branch")
        val branched = assertNotNull(tree!!.stateAt(tree!!.currentNodeId))
        assertFalse(Automatum.flowOpen(branched.board), "the discard stayed on the line Bob left")
    }

    // pausereview #11 (STK-037 "If you do nothing, the game goes on when the bar has emptied"): Alice cast Storm Spark at
    // Bob and left (her app closed, no green dot): her device can't pass for him. Bob, catching up live, sees his pause
    // and its bar run out: his own device passes for him, as the other player isn't online.
    @Test fun yourPauseGoesOnWhenTheOtherPlayerIsAway() {
        val cast = castAt(sparkBoard(), f.a, card(sparkBoard(), aHand), f.b)
        show(cast, f.b, online = false)
        compose.mainClock.advanceTimeBy(2_500)
        frames()
        assertTrue(sent.isNotEmpty(), "Bob's device passes for him when the bar has emptied")
    }

    // pausereview (STK-039): Bob held his pause on Alice's Storm Spark; her device passed for him and she went on with
    // End turn (the turn passes to Bob, who draws). Bob's Storm Spark from the held pause is a branch from there.
    @Test fun aHeldReactionBranchesAfterTheTurnWentOn() = heldReactionAfterTheTurnWentOn(landFirst = false, shuffled = false)

    // pausereview (STK-039): the same, with Alice playing a Mountain before End turn
    @Test fun aHeldReactionBranchesAfterALandAndTheTurnWentOn() = heldReactionAfterTheTurnWentOn(landFirst = true, shuffled = false)

    // pausereview #10 (STK-039, I-D41; HIS-037 continuations, HIS-042 reseed; SYS-010 #4 "an automated batch only to the
    // board it was planned on"): the same game with the libraries in an unknown order, as every real game has them. A
    // branch reseeds the unknown library order, so Bob's device plans his cast on the branch's start (the held board
    // reseeded as the server will), and the server takes it
    @Test fun aHeldReactionBranchesWithShuffledLibraries() = heldReactionAfterTheTurnWentOn(landFirst = false, shuffled = true)

    // pausereview #10 (HIS-042, SYS-010 #4): a held reaction planned on the held board before its device learned the
    // game had gone on (sent within the network's delay) still applies as the branch
    @Test fun aHeldReactionPlannedBeforeTheBranchStillApplies() =
        heldReactionAfterTheTurnWentOn(landFirst = false, shuffled = true, knowsBranch = false)

    private fun heldReactionAfterTheTurnWentOn(landFirst: Boolean, shuffled: Boolean, knowsBranch: Boolean = true) {
        val start = board(listOf(spark, mountain), listOf(spark), listOf(mountain, mountain), listOf(mountain), shuffled = shuffled)
        val cast = castAt(start, f.a, card(start, aHand, 0), f.b)
        AutomatumBoards.compute(cast, f.a)
        assertTrue(device(cast, f.a).flows.autoPass(f.b, cast.currentNodeId))
        var resolved = takeSent(cast, f.a)
        if (landFirst) {
            AutomatumBoards.compute(resolved, f.a)
            val alice = device(resolved, f.a)
            val land = resolved.board.containers.single { it.id == aHand }.cards
                .single { id -> resolved.board.cards.single { it.id == id }.definition == mountain.submitted.digest }
            assertTrue(alice.cast.start(land), alice.error)
            if (alice.cast.open) assertTrue(alice.cast.cast(), alice.error)
            resolved = takeSent(resolved, f.a)
        }
        AutomatumBoards.compute(resolved, f.a)
        assertTrue(device(resolved, f.a).flows.step(StepFlow.Until.EndOfTurn))
        var went = takeSent(resolved, f.a)
        // Bob's pause in Alice's end step (I-D41 decision B): her device passes for him, away
        if (PausePoints.at(went.board) == PausePoints.At(f.b, PausePoints.Point.Pause)) {
            AutomatumBoards.compute(went, f.a)
            assertTrue(device(went, f.a).flows.autoPass(f.b, went.currentNodeId))
            went = takeSent(went, f.a)
        }
        assertEquals(f.b, went.board.turn.accountId, "Bob's turn (${went.board.turn})")
        val bob = device(cast, f.b, from = cast.currentNodeId.takeIf { knowsBranch })
        AutomatumBoards.compute(cast, f.b)
        assertTrue(bob.cast.start(card(cast, bHand, 0)), bob.error)
        targetLife(bob, cast, f.b, f.a)
        // I-D47: Autotap (on) sends the cast once its last choice is made; only a cast still open waits for Cast
        if (bob.cast.open) assertTrue(bob.cast.cast(), bob.error)
        val (batch, request) = sent.single()
        val planned = assertNotNull(tree).let { if (knowsBranch && shuffled) it.branchStart(cast, f.b)!! else cast }
        if (shuffled) assertTrue(planned.board != cast.board == knowsBranch, "the branch's start reseeds the libraries")
        assertEquals(MtgFacts.digest(planned.board), batch.tags.first()?.board, "planned on the branch's start")
        val change = serve(cast, batch, request, f.b, from = cast.currentNodeId)
        assertNull(change.reason, "the held reaction is a branch")
        assertEquals(life(cast, f.a) - 3, life(tree!!.stateAt(tree!!.currentNodeId)!!, f.a), "Bob's Storm Spark resolved at Alice")
    }


    // ---- Seeded two-device games through the app's controllers

    /** What a simulated player does next, at [at] (sim time), about the board [node]. */
    private sealed class Plan(val at: Long, val node: String) {
        class Think(at: Long, node: String) : Plan(at, node)
        class React(at: Long, node: String, val respond: Boolean) : Plan(at, node)
        class Hold(at: Long, node: String) : Plan(at, node)
        class Release(at: Long, node: String) : Plan(at, node)
    }
    private class Reaction(val node: String, val path: String, val doneAt: Long)

    /** One player's device in a simulated game: its view (the live game, or the point it holds), its pause driver's
     * settings, whether its player is online (the app open on the table) and what its player is doing. */
    private inner class Device(val player: String, val settings: PauseSettings) {
        val name = if (player == f.a) "alice" else "bob"
        var online = true
        var held: String? = null
        var view: PlayState = tree!!.stateAt(tree!!.currentNodeId)!!
        /** The live game as this device last received it. */
        var live: PlayState = view
        val queue = mutableListOf<Triple<GameAction.PlayBatch, String, String?>>()
        // As the app's toolkit (TableScreen's tableToolkit): the live game, or the held point's board once held. These games
        // keep a cast open (its mana section, I-D47 Autotap off) while the table goes on, then Cast it
        val toolkit = TableToolkitController(player, { if (held != null) view else live }, { true }, { _, _ -> true },
            automatedBatch = { batch, request -> queue += Triple(batch, request, held); true }, playTree = { tree },
            continuation = { held?.let { Continuation(it, explicit = true) } }).also { it.cast.autotap = false }
        val routes = object : PauseRoutes {
            override fun hold(node: String): Boolean { held = node; view = tree!!.stateAt(node)!!; return true }
            override fun holding(node: String) = held == node
            override fun releaseLive() {}
            override fun leaveHold() { held = null; view = tree!!.stateAt(tree!!.currentNodeId)!! }
        }
        init { toolkit.pause.routes = routes }
        var plan: Plan? = null
        var casting: Reaction? = null
        var lastPause: String? = null
        var lastStop: String? = null
    }

    private class SimStats {
        val reactions = mutableMapOf<String, Int>()
        val lost = mutableListOf<String>()
        val refused = mutableListOf<String>()
        val stalls = mutableListOf<String>()
        val confused = mutableListOf<String>()
        val stops = mutableMapOf<String, Int>()
        var events = 0
        var turns = 0
        var end = "ticks"
        override fun toString() = "events $events, turn $turns, end $end, stops $stops, reactions $reactions, lost ${lost.size} ${lost.take(6)}, " +
            "refused ${refused.size} ${refused.take(6)}, stalls ${stalls.size} ${stalls.take(4)}, confused ${confused.size} ${confused.take(4)}"
    }

    /** The sim's start: both players with two Mountains, two Storm Sparks and a Mountain in hand, libraries of Storm Sparks
     * and Mountains; every other card out of the game. */
    private fun simBoard(random: kotlin.random.Random): PlayState {
        val initial = f.initial
        val keep = mutableMapOf<String, Invented>()
        for ((hand, field) in listOf(aHand to aField, bHand to bField)) {
            handCards(hand).take(3).zip(listOf(spark, spark, mountain)).forEach { (id, what) -> keep[id] = what }
            initial.board.cards.filter { it.containerId == field }.take(2).forEach { keep[it.id] = mountain }
        }
        for (library in initial.board.containers.filter { it.zone == PlayZone.LIBRARY }) for (id in library.cards)
            keep[id] = if (random.nextBoolean()) spark else mountain
        return initial.copy(definitions = initial.definitions + all.map { it.submitted },
            board = initial.board.copy(priority = MtgPriority(f.a),
                cards = initial.board.cards.filter { it.id in keep }.map { card -> keep.getValue(card.id).let { what ->
                    card.copy(owner = initial.board.containers.single { c -> c.id == card.containerId }.accountId ?: card.owner, definition = what.submitted.digest, faceId = what.face.faceId, corner = "", tapped = false) } },
                containers = initial.board.containers.map { it.copy(cards = it.cards.filter { id -> id in keep }) }.map {
                    // Libraries in an unknown order, as a game starts
                    if (it.zone == PlayZone.LIBRARY && it.cards.size >= 2) it.copy(layers = LibraryLayers.initial(f.id("layer/${it.id}"), it.cards)) else it }))
    }

    /**
     * A seeded game on two devices: each runs [AutomatumPauseDriver] on its view; players go online and offline, act at
     * their stops (a land, Storm Spark at the other player, Next step or End turn), and at their pauses react (Respond,
     * which holds while the bar runs, or a cast begun from the hand), hold and think, or let it go. The server takes each
     * device's batches onto one history in order. Counts what went wrong.
     */
    private fun simulate(seed: Long, ticks: Int): SimStats {
        val random = kotlin.random.Random(seed)
        // The devices' request ids and seeds from the game's seed: the same game on every run
        DeviceRandom.seeded(seed)
        val start = simBoard(random)
        tree = GameplayTree(start)
        val stats = SimStats()
        fun settings() = PauseSettings(responseMillis = listOf(1_000L, 1_500L, 2_000L, 3_000L).random(random),
            proceedAtOnce = random.nextInt(4) == 0, fullControl = random.nextInt(3) == 0)
        val devices = listOf(Device(f.a, settings()), Device(f.b, settings()))
        val head = mutableStateOf(start)
        val tick = mutableStateOf(0)
        fun other(d: Device) = devices.single { it !== d }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            tick.value
            val live = head.value
            for (d in devices) if (d.online)
                androidx.compose.runtime.key(d.player) {
                    AutomatumPauseDriver(d.toolkit, null, d.view, live.currentNodeId, liveShown = d.held == null,
                        opponentOnline = other(d).online, settings = d.settings)
                }
        }
        var now = 0L
        var changedAt = 0L
        // When a player last went online or offline: a pause their device was to pass waits for them meanwhile
        var flippedAt = 0L
        val reactionRequests = mutableMapOf<String, String>()
        fun liveState() = tree!!.stateAt(tree!!.currentNodeId)!!
        fun refresh() {
            val live = liveState()
            // By the board: an Undo of an earlier event replays the later ones, changing the board at the same newest event
            if (live.board !== head.value.board) { changedAt = now; stats.events++ }
            head.value = live
            for (d in devices) {
                if (d.live.board !== live.board) {
                    d.live = live
                    if (d.held == null && d.toolkit.cast.open) d.toolkit.cast.follow()
                }
                val before = d.view
                // The live table stays on the viewer's own pause while its bar runs (STK-037: HistoryPlayback.holdPause)
                if (d.held == null) d.view = d.toolkit.pause.shown?.takeIf { it.running && it.role is PauseRole.Yours &&
                    it.node == d.view.currentNodeId && tree!!.stateAt(it.node) != null }?.let { d.view } ?: live
                else if (tree!!.stateAt(d.held!!) == null) { d.held = null; d.view = live }
                if (d.view !== before) {
                    AutomatumBoards.compute(d.view, d.player)
                    if (d.toolkit.cast.open) d.toolkit.cast.follow()
                }
            }
            stats.turns = live.board.turn.number.toInt()
            tick.value++
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        }
        /** Why a batch [d] planned on its held point [from] isn't taken there: the events since, the source card. */
        fun diagnose(d: Device, batch: GameAction.PlayBatch, from: String): String {
            val history = tree!!
            val since = generateSequence(history.currentNodeId) { c -> history.nodes[c]?.parentId?.takeIf { c != from } }
                .takeWhile { it != from }.mapNotNull { history.nodes[it] }.toList().asReversed()
            val held = history.stateAt(from)!!
            val add = (batch.actions.firstOrNull() as? PlayAction.Stack)?.action as? StackAction.AddEffect
            val card = held.board.cards.singleOrNull { it.id == add?.sourceId }
            val zone = held.board.containers.singleOrNull { it.id == card?.containerId }
            return "; ${since.size} events since: ${since.filter { it.tag?.folded != true }.joinToString(" ") {
                (if (it.author == f.a) "A:" else "B:") + ((it.action as? PlayAction.Mtg)?.action?.let { a -> a::class.simpleName } ?: it.action::class.simpleName) }}" +
                "; source in ${zone?.zone} of ${if (zone?.accountId == d.player) "the caster" else zone?.accountId}, owner the caster ${card?.owner == d.player}" +
                "; planned on the held board ${d.view.board == held.board}, its digest ${batch.tags.firstOrNull()?.board == MtgFacts.digest(held.board)}" +
                "; unordered library layers that a fork reseeds: ${held.board.containers.filter { it.zone == PlayZone.LIBRARY }.sumOf { library ->
                    LibraryLayers.segments(library).count { !it.layer.ordered && it.cards.size >= 2 } }}"
        }
        fun serveQueues() {
            for (d in devices) while (d.queue.isNotEmpty()) {
                val (batch, request, from) = d.queue.removeAt(0)
                val change = serve(liveState(), batch, request, d.player, from)
                val reaction = reactionRequests.remove(request)
                if (change.reason != null) {
                    stats.refused += "${change.reason} ${d.name}${if (from != null) " (held)" else ""}${reaction?.let { " reaction" } ?: ""}"
                    if (reaction != null) stats.lost += "$reaction: refused (${change.reason}${from?.let { diagnose(d, batch, it) } ?: ""})"
                }
                if (from != null) d.routes.leaveHold()
                refresh()
            }
            refresh()
        }
        fun hand(d: Device, what: Invented) = d.view.board.containers.single { it.id == (if (d.player == f.a) aHand else bHand) }.cards
            .filter { id -> d.view.board.cards.single { it.id == id }.definition == what.submitted.digest }
        /** Begins casting a Storm Spark at the other player; false when none can be cast. */
        fun beginSpark(d: Device): Boolean {
            AutomatumBoards.compute(d.view, d.player)
            for (card in hand(d, spark)) {
                if (!d.toolkit.cast.start(card)) continue
                if (d.toolkit.cast.targeting) targetLife(d.toolkit, d.view, d.player, other(d).player)
                if (d.toolkit.cast.open) return true
            }
            return false
        }
        fun finishCast(d: Device, path: String?) {
            if (!d.toolkit.cast.open) { path?.let { stats.lost += "$it: dropped while casting (${d.toolkit.error})" }; return }
            val before = d.queue.size
            if (!d.toolkit.cast.cast() || d.queue.size == before) {
                path?.let { stats.lost += "$it: not sent (${d.toolkit.error})" }
                d.toolkit.cast.cancel(); return
            }
            path?.let { reactionRequests[d.queue.last().second] = it }
        }
        refresh()
        repeat(ticks) {
            now += 100
            compose.mainClock.advanceTimeBy(100)
            serveQueues()
            val live = head.value
            if (live.board.players.any { (it.life.toIntOrNull() ?: 1) <= 0 }) { stats.end = "a player lost"; return stats }
            if (Automatum.flowOpen(live.board)) { stats.end = "a choice opened"; return stats }
            for (d in devices) {
                if (random.nextInt(150) == 0) { d.online = !d.online; flippedAt = now; tick.value++ }
                if (!d.online) continue
                d.casting?.let { r -> if (now >= r.doneAt) { d.casting = null; finishCast(d, r.path) } }
                if (d.casting != null) continue
                val plan = d.plan
                if (plan != null) {
                    if (now < plan.at) continue
                    d.plan = null
                    val running = d.toolkit.pause.running(plan.node) != null
                    val there = head.value.currentNodeId == plan.node || d.held == plan.node || d.view.currentNodeId == plan.node
                    if (plan is Plan.React && !there) stats.reactions.merge("the game went on before it was begun", 1, Int::plus)
                    when (plan) {
                        is Plan.Think -> if (d.view.currentNodeId == plan.node) {
                            val land = hand(d, mountain).firstOrNull()
                            val queued = d.queue.size
                            if (land != null && random.nextBoolean()) {
                                AutomatumBoards.compute(d.view, d.player)
                                if (d.toolkit.cast.start(land) && d.toolkit.cast.open) finishCast(d, null)
                                stats.stops.merge(if (d.queue.size > queued) "land" else "land refused: ${d.toolkit.error}", 1, Int::plus)
                            } else if (random.nextInt(10) < 3 && beginSpark(d)) {
                                finishCast(d, null)
                                stats.stops.merge(if (d.queue.size > queued) "spark" else "spark refused: ${d.toolkit.error}", 1, Int::plus)
                            } else {
                                if (!d.toolkit.flows.step(if (random.nextInt(3) == 0) StepFlow.Until.EndOfTurn else StepFlow.Until.Pass) &&
                                    d.toolkit.flows.acting != null) stats.confused += "${d.name}'s Next step at a stop would act for the other player"
                                stats.stops.merge(if (d.queue.size > queued) "step" else "step refused: ${d.toolkit.error}", 1, Int::plus)
                            }
                        }
                        is Plan.React -> if (there) {
                            val path = if (plan.respond) (if (running) "Respond during the bar" else "Respond after the bar") else "cast from the hand"
                            // Respond holds the pause as the app's Respond does (#2); a cast begun from the hand too (#1)
                            if (plan.respond) d.toolkit.flows.respond(d.view)
                            if (!beginSpark(d)) stats.reactions.merge("$path, nothing castable", 1, Int::plus)
                            else { stats.reactions.merge(path, 1, Int::plus); d.casting = Reaction(plan.node, path, now + random.nextLong(800, 3_000)) }
                        }
                        is Plan.Hold -> if (there) {
                            d.toolkit.pause.hold(plan.node)
                            d.plan = Plan.Release(now + random.nextLong(1_000, 6_000), plan.node)
                        }
                        is Plan.Release -> if (d.held == plan.node && !d.toolkit.pause.passBehind(plan.node)) d.toolkit.flows.pass(d.player)
                    }
                    continue
                }
                val at = PausePoints.at(d.view.board)?.takeIf { it.holder == d.player } ?: continue
                val node = d.view.currentNodeId
                when (at.point) {
                    PausePoints.Point.Stop -> if (d.lastStop != node || now - changedAt > 3_000) {
                        d.lastStop = node
                        d.plan = Plan.Think(now + random.nextLong(300, 1_500), node)
                    }
                    PausePoints.Point.Pause -> if (d.lastPause != node) {
                        d.lastPause = node
                        val roll = random.nextInt(10)
                        val delay = random.nextLong(200, 4_000)
                        if (roll < 5) d.plan = Plan.React(now + delay, node, respond = random.nextBoolean())
                        else if (roll < 6) d.plan = Plan.Hold(now + delay, node)
                    } else if (d.held == null && now - maxOf(changedAt, flippedAt) > 10_000) {
                        // Left at a pause the game doesn't leave: the player lets it go by hand
                        stats.stalls += "turn ${d.view.board.turn.number} ${TurnPosition.of(d.view.board.turn)} ${d.name} (by " +
                            "${if (tree!!.nodes[node]?.author == f.a) "alice" else "bob"}; live ${head.value.currentNodeId == node}; " +
                            "now $now changed $changedAt clock ${compose.mainClock.currentTime}; " +
                            devices.joinToString { "${it.name} online ${it.online} held ${it.held != null} shown ${it.toolkit.pause.shown?.let { s -> "${s.role} here ${s.node == node} running ${s.running} at ${s.startedAt}" }} " +
                                "passed ${it.toolkit.pause.passedAt(node)} casting ${it.toolkit.cast.open}" } + ")"
                        if (!d.toolkit.pause.passBehind(node)) d.toolkit.flows.pass(d.player)
                        changedAt = now
                    }
                }
            }
        }
        return stats
    }

    // pausereview (I-D41): seeded two-device games: the games go on, no player's Next step at their own stop acts for the
    // other player, nobody is left at a pause nobody passes, and the server refuses no batch from a held point (#10)
    @Test fun twoDeviceGamesHoldTogether() {
        val stats = simulate(7L, ticks = 3_000)
        println("SIM seed 7: $stats")
        assertTrue(stats.events > 20, "the game moved ($stats)")
        assertTrue(stats.confused.isEmpty(), "$stats")
        assertTrue(stats.stalls.isEmpty(), "$stats")
        assertTrue(stats.refused.none { it.contains("(held)") }, "no held batch is refused (#10): $stats")
    }

    // pausereview #1/#2/#10 (I-D41: a reaction after the acting player moved on uses branching): in such games no reaction
    // is lost, however it was begun
    @Test fun twoDeviceGamesLoseNoReaction() {
        val stats = simulate(8L, ticks = 3_000)
        println("SIM seed 8: $stats")
        assertTrue(stats.lost.isEmpty(), "${stats.lost.size} reactions lost: $stats")
    }
}
