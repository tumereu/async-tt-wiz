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
 * The opening with Automatum on (TRN-027–TRN-029): a new game's mulligans stay ATW's, the host's setup before the
 * engine's game begins, and once both players are Ready the first turn plays through Automatum (CR 103.5, 103.8).
 * Two devices, one per player, on one shared history as the server keeps it.
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

    /** A new game as the server starts it: 12-card libraries in an unknown order, empty hands, both players
     * "Mulliganing", Alice starting in her first untap step. */
    private fun newGame(): PlayState {
        val cards = mutableListOf<PlayCard>()
        val containers = mutableListOf<PlayContainer>()
        for (player in listOf(alice, bob)) {
            val ids = List(12) { id("$player/card/$it") }
            ids.forEach { cards += PlayCard(it, mountain.digest, player, id("$player/LIBRARY"), mountainFace.faceId) }
            for (zone in PlayZone.entries.filter { it != PlayZone.STACK }) containers += PlayContainer(id("$player/$zone"), zone, player,
                if (zone == PlayZone.LIBRARY) ids else emptyList(),
                layers = if (zone == PlayZone.LIBRARY) LibraryLayers.initial(id("$player/layer"), ids) else emptyList())
        }
        containers += PlayContainer(id("STACK"), PlayZone.STACK)
        val seed = RandomV1.encodeSeed(ByteArray(32) { it.toByte() })
        return PlayState(id("game"), id("play"), id("round"), id("start"), PlayRoot(id("root"), id("branch")), id("root"),
            BoardState(listOf(alice, bob).map { PlayPlayer(it, status = PlayerStatus.OPENING) }, cards.sortedBy { it.id },
                containers, TurnState(alice)),
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
    /** "Mull N" with 7 in its dialog (TRN-027, ZON-028), as the player taps it. */
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
    private fun next(toolkit: TableToolkitController): Boolean =
        toolkit.table.perform(TableActionIntent.Next(TableActionScope(play.gameId, play.id)))
    /** [toolkit]'s player plays their first hand card, a Mountain, through Automatum (ZON-004). */
    private fun playLand(toolkit: TableToolkitController): String {
        val card = hand(toolkit.viewer).first()
        assertTrue(toolkit.cast.start(card), toolkit.error)
        if (toolkit.cast.open) assertTrue(toolkit.cast.cast(), toolkit.error)
        return card
    }

    // TRN-027, TRN-028, TRN-029, ZON-027 (CR 103.5, 103.8, 103.8a): Automatum switched on as the new game begins; Alice takes
    // a mulligan to six the London way (a new seven, then one of them on the bottom of her library), Bob with Mull −1; the
    // game doesn't begin until both are Ready; then Alice's step button begins her first turn through Automatum: no draw,
    // her main phase, where she plays a land
    @Test fun bothMulliganWithAutomatumOnThenTheFirstTurnPlays() {
        tree = GameplayTree(newGame())
        val a = device(alice)
        val b = device(bob)
        assertTrue(a.setAutomatum(true), a.error)
        assertTrue(play.board.automatum)

        mullSeven(a)
        assertEquals(7, hand(alice).size)
        mullSeven(a)
        val bottom = hand(alice).first()
        val source = play.board.cards.single { it.id == bottom }
        assertTrue(a.submit(PlayAction.MoveCard(bottom, MoveSource(source.containerId),
            MoveDestination(library(alice).id, MoveInsertion.BOTTOM))), a.error)
        assertEquals(6, hand(alice).size)
        assertEquals(bottom, library(alice).cards.last(), "the chosen card is at the bottom of her library")
        // Drawing by hand is still no opening action
        val top = library(alice).cards.first()
        assertFalse(a.submit(PlayAction.MoveCard(top, MoveSource(library(alice).id), MoveDestination(handId(alice), MoveInsertion.TOP))))
        assertEquals(AutomatumDrags.MANUAL, a.error)

        mullSeven(b)
        val mullOne = quick(b, "Mull −1")
        assertTrue(mullOne.enabled, "${mullOne.refused}")
        assertTrue(b.table.perform(mullOne.intent), b.error)
        assertEquals(6, hand(bob).size)
        // Bob mulligans only his own hand
        assertFalse(b.submit(PlayAction.Mulligan(library(alice).id, handId(alice), hand(alice), 7, DeviceRandom.seed())))
        assertEquals(AutomatumDrags.MANUAL, b.error)
        // The game doesn't begin while either player is still mulliganing
        assertFalse(next(a))
        assertEquals(SharedFlowController.OPENING, a.error)
        ready(a)
        assertFalse(next(a))
        assertEquals(SharedFlowController.OPENING, a.error)
        ready(b)
        assertFalse(PlayerStatus.anyOpening(play.board))
        // After Ready a player's mulligans are over (CR 103.5)
        assertFalse(b.submit(PlayAction.Mulligan(library(bob).id, handId(bob), hand(bob), 5, DeviceRandom.seed())))

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

    // TRN-027, TRN-029 (I-D28): the opening played with Automatum off, Automatum switched on once both are Ready: the
    // first turn begins from its untap step through Automatum all the same
    @Test fun switchedOnAfterTheOpeningTheFirstTurnBegins() {
        tree = GameplayTree(newGame())
        val a = device(alice)
        val b = device(bob)
        mullSeven(a)
        mullSeven(b)
        ready(a)
        ready(b)
        assertTrue(b.setAutomatum(true), b.error)
        assertTrue(next(a), a.error)
        assertEquals(TurnPosition.PRECOMBAT_MAIN, TurnPosition.of(play.board.turn))
        assertEquals(7, hand(alice).size)
        playLand(a)
        assertEquals(6, hand(alice).size)
    }
}
