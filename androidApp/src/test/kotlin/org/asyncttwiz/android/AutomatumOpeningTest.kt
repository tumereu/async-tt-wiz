package org.asyncttwiz.android

import arcanum.api.Layout
import arcanum.parser.OracleCard
import arcanum.parser.OracleParser
import org.asyncttwiz.protocol.*
import org.asyncttwiz.storage.ParsedCard
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The opening with Automatum on (TRN-062, I-D49): the game's rules (opening hand 7, the first mulligan free, London
 * mulligans) as ATW's events, the host's setup before the engine's game begins; once both players have kept the first
 * turn plays through Automatum (CR 103.5, 103.8). Two devices, one per player, on one shared history as the server keeps it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AutomatumOpeningTest {
    private fun id(value: String) = java.util.UUID.nameUUIDFromBytes("opening/$value".toByteArray()).toString()
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

    /** A new game as the server starts it: 12-card libraries in an unknown order, both players "Mulliganing", Alice
     * starting in her first untap step, each player's opening hand of seven drawn from the top of their shuffled library
     * (I-D49, as [StartRules.initialize] does). */
    private fun newGame(): PlayState {
        val cards = mutableListOf<PlayCard>()
        val containers = mutableListOf<PlayContainer>()
        for (player in listOf(alice, bob)) {
            val ids = List(12) { id("$player/card/$it") }
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
                containers, TurnState(alice), automatum = true),
            listOf(mountain), listOf(alice, bob).map { p -> InitialShuffle(p, seed, cards.filter { it.owner == p }.map { it.id }) },
            InitialStarter(StarterChoice(alice), alice))
    }

    // ---- The server: one shared history for both devices
    private lateinit var tree: GameplayTree
    private var requests = 0
    private val play: PlayState get() = tree.stateAt(tree.currentNodeId)!!
    private fun apply(actor: String, action: GameAction, request: String = id("request/${requests++}")): Boolean {
        val command = GameCommand(request, GameScope(play.gameId, playId = play.id), action, id("client/$actor"), "1")
        val change = HistoryContinuation.change(tree, actor, command)
        change.tree?.let { tree = it }
        return change.tree != null
    }
    private fun device(viewer: String) = TableToolkitController(viewer, { play }, { true },
        { action, _ -> apply(viewer, GameAction.Play(action)) },
        playBatch = { actions, request -> apply(viewer, GameAction.PlayBatch(actions), request) },
        playTree = { tree }, automatedBatch = { batch, request -> apply(viewer, batch, request) })

    private fun hand(player: String) = play.board.containers.single { it.zone == PlayZone.HAND && it.accountId == player }.cards
    private fun library(player: String) = play.board.containers.single { it.zone == PlayZone.LIBRARY && it.accountId == player }
    private fun handId(player: String) = play.board.containers.single { it.zone == PlayZone.HAND && it.accountId == player }.id
    private fun quick(toolkit: TableToolkitController, name: String) =
        QuickActions.contextual(play, QuickContext(viewer = toolkit.viewer), toolkit.table).single { it.name == name }
    private fun quickNames(toolkit: TableToolkitController) =
        QuickActions.contextual(play, QuickContext(viewer = toolkit.viewer), toolkit.table).map { it.name }
    private fun mulligans(player: String) = play.board.players.single { it.accountId == player }.mulligans
    private fun opening(player: String) = PlayerStatus.opening(play.board, player)
    /** "Mull N" with 7 in its dialog (TRN-027, ZON-028), as the player taps it (Automatum off). */
    private fun mullSeven(toolkit: TableToolkitController) {
        val mull = quick(toolkit, "Mull N")
        assertTrue(mull.enabled, "Mull N works during the opening: ${mull.refused}")
        assertTrue(toolkit.table.perform(mull.intent))
        val library = assertNotNull(toolkit.library, "the mulligan dialog opened")
        assertEquals(0, library.controller.mulligan(library.handIds, 7).refused, toolkit.error)
        toolkit.dismiss()
    }
    private fun ready(toolkit: TableToolkitController) {
        val ready = quick(toolkit, QuickActions.READY)
        assertTrue(ready.enabled)
        assertTrue(toolkit.table.perform(ready.intent), toolkit.error)
    }
    /** "Mulligan" (I-D49), as the player taps it. */
    private fun mulligan(toolkit: TableToolkitController) {
        val mulligan = quick(toolkit, QuickActions.MULLIGAN)
        assertTrue(mulligan.enabled, "${mulligan.refused}")
        assertTrue(toolkit.table.perform(mulligan.intent), toolkit.error)
    }
    /** "Keep" (I-D49), as the player taps it. */
    private fun keep(toolkit: TableToolkitController) = assertTrue(toolkit.table.perform(quick(toolkit, QuickActions.KEEP).intent), toolkit.error)
    private fun next(toolkit: TableToolkitController): Boolean =
        toolkit.table.perform(TableActionIntent.Next(TableActionScope(play.gameId, play.id)))
    /** [toolkit]'s player plays their first hand card, a Mountain, through Automatum (ZON-004). */
    private fun playLand(toolkit: TableToolkitController): String {
        val card = hand(toolkit.viewer).first()
        assertTrue(toolkit.cast.start(card), toolkit.error)
        if (toolkit.cast.open) assertTrue(toolkit.cast.cast(), toolkit.error)
        return card
    }

    // TRN-062, TRN-027, TRN-029 (I-D49, CR 103.5, 103.8): a game started with Automatum on begins with seven cards in each
    // hand; both players see Mulligan and Keep (no Mull N); both keep at once; the game doesn't begin until both have
    // kept; then Alice's step button begins her first turn through Automatum: no draw, her main phase, where she plays a land
    @Test fun bothKeepAndTheFirstTurnPlays() {
        tree = GameplayTree(newGame())
        val a = device(alice)
        val b = device(bob)
        assertEquals(7, hand(alice).size)
        assertEquals(7, hand(bob).size)
        assertEquals(listOf(QuickActions.MULLIGAN, QuickActions.KEEP), quickNames(b))
        assertEquals(listOf(QuickActions.MULLIGAN, QuickActions.KEEP), quickNames(a))
        assertFalse(next(a))
        assertEquals(SharedFlowController.OPENING, a.error)
        keep(b)
        assertFalse(a.keeping || b.keeping, "nothing owed: no Keep / Bottom screen")
        assertFalse(opening(bob))
        assertFalse(next(a))
        assertEquals(SharedFlowController.OPENING, a.error)
        keep(a)
        assertFalse(PlayerStatus.anyOpening(play.board))
        // After keeping a player's mulligans are over (CR 103.5)
        assertFalse(b.submit(PlayAction.Mulligan(library(bob).id, handId(bob), hand(bob), 7, DeviceRandom.seed())))

        val aliceHand = hand(alice)
        val bobHand = hand(bob)
        assertTrue(next(a), a.error)
        assertEquals(TurnState(alice, "1", TurnPosition.PRECOMBAT_MAIN.phase, TurnPosition.PRECOMBAT_MAIN.step), play.board.turn)
        assertEquals(alice, play.board.priority?.holder)
        assertEquals(aliceHand, hand(alice), "no draw on the starting player's first turn")
        assertEquals(bobHand, hand(bob))
        val land = playLand(a)
        assertTrue(play.board.containers.single { it.zone == PlayZone.BATTLEFIELD && it.accountId == alice }.cards.contains(land))
    }

    // TRN-062 (I-D49, CR 103.5): the first mulligan is free: a new seven, counted, and Keep sets Ready at once with seven
    @Test fun aFreeMulliganThenKeepSeven() {
        tree = GameplayTree(newGame())
        val a = device(alice)
        mulligan(a)
        assertEquals(1, mulligans(alice))
        assertEquals(7, hand(alice).size)
        assertEquals(5, library(alice).cards.size)
        assertEquals(12, (hand(alice) + library(alice).cards).toSet().size, "the hand went back and seven were drawn again")
        keep(a)
        assertFalse(a.keeping)
        assertFalse(opening(alice))
        assertEquals(7, hand(alice).size)
        assertTrue(opening(bob), "Bob decides on his own")
    }

    // TRN-062 (I-D49, I-D48, CR 103.5): a second mulligan owes one card: Keep opens the Keep / Bottom screen, which takes
    // exactly one card; it goes to the bottom of Alice's library and she is Ready with six. Bob mulligans meanwhile:
    // both decide at the same time
    @Test fun twoMulligansPutOneOnTheBottom() {
        tree = GameplayTree(newGame())
        val a = device(alice)
        val b = device(bob)
        mulligan(a)
        mulligan(a)
        assertEquals(2, mulligans(alice))
        assertEquals(1, AutomatumOpening.owed(play.board, alice))
        keep(a)
        assertTrue(a.keeping, "the Keep / Bottom screen opened")
        assertTrue(opening(alice), "not Ready before the bottom is chosen")
        mulligan(b)
        assertEquals(1, mulligans(bob))
        assertFalse(a.confirmKeep(emptyList()), "exactly the owed number")
        assertFalse(a.confirmKeep(hand(alice).take(2)))
        val bottom = hand(alice).last()
        assertTrue(a.confirmKeep(listOf(bottom)), a.error)
        assertFalse(a.keeping)
        assertFalse(opening(alice))
        assertEquals(6, hand(alice).size)
        assertEquals(bottom, library(alice).cards.last(), "the chosen card is at the bottom of her library")
        assertTrue(opening(bob))
        keep(b)
        assertFalse(b.keeping)
        assertEquals(7, hand(bob).size)
        assertTrue(next(a), a.error)
    }

    // TRN-062 (I-D49), SYS-038: the opening's steps are undone one at a time: a keep with its bottom card, then a mulligan
    // with its count, each restoring the hand it changed
    @Test fun undoWithinTheOpening() {
        tree = GameplayTree(newGame())
        val a = device(alice)
        val seven = hand(alice)
        mulligan(a)
        val second = hand(alice)
        mulligan(a)
        keep(a)
        assertTrue(a.confirmKeep(listOf(hand(alice).first())), a.error)
        assertFalse(opening(alice))

        assertTrue(apply(alice, GameAction.Undo))
        assertTrue(opening(alice))
        assertEquals(7, hand(alice).size)
        assertEquals(2, mulligans(alice))
        assertTrue(apply(alice, GameAction.Undo))
        assertEquals(second, hand(alice))
        assertEquals(1, mulligans(alice))
        assertTrue(apply(alice, GameAction.Undo))
        assertEquals(seven, hand(alice))
        assertEquals(0, mulligans(alice))
        assertEquals(listOf(QuickActions.MULLIGAN, QuickActions.KEEP), quickNames(a))
    }

    // TRN-062 (I-D49, CR 103.5): mulligans go on until the hand kept would have no cards; then Mulligan is greyed and says
    // why, and Keep puts all seven on the bottom
    @Test fun noMulliganOnceTheHandKeptWouldBeEmpty() {
        tree = GameplayTree(newGame())
        val a = device(alice)
        repeat(8) { mulligan(a) }
        val refused = quick(a, QuickActions.MULLIGAN)
        assertFalse(refused.enabled)
        assertEquals(QuickActions.NO_MORE_MULLIGANS, refused.refused)
        assertFalse(a.table.perform(refused.intent))
        assertEquals(7, AutomatumOpening.owed(play.board, alice))
        keep(a)
        assertTrue(a.confirmKeep(hand(alice)), a.error)
        assertTrue(hand(alice).isEmpty())
    }

}
