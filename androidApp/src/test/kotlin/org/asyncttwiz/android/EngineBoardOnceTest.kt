package org.asyncttwiz.android

import arcanum.engine.Engine
import arcanum.engine.input.Input
import arcanum.engine.state.PlayerId
import org.asyncttwiz.automatum.CardPlays
import org.asyncttwiz.automatum.ComputedBoard
import org.asyncttwiz.automatum.HandPlays
import org.asyncttwiz.protocol.game.GameDevice
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Engine-responsiveness R3: the board is built once, on the engine thread. An own action's early board is published again
 * with its whole outcome when nothing followed it (only the status fields new); a tick at an unchanged point builds and
 * searches nothing; every board published comes with its computed card values, equal to what [ComputedBoard] works out
 * for it, and the boards published after an action with the player's hand plays, equal to [HandPlays]; the card menus'
 * plays are worked out once per board, equal to [CardPlays]. The table ([AutomatumBoards]) reads them, working nothing
 * out itself. Counted, never timed.
 */
class EngineBoardOnceTest {
    private val f = EngineGameFixture(creatures = true)
    private val shown = mutableListOf<EngineGame.Shown>()

    @Before fun clean() = AutomatumBoards.reset()
    @After fun reset() { Engine.layersWorkedOut = null; AutomatumBoards.reset() }

    /** The engine thread inline, the main thread's work at once; with [early] an own action's board shows at its
     * performed point, as on the app's engine thread. */
    private class Inline(private val early: Boolean) : EngineWorker {
        override fun run(work: () -> Unit) = work()
        override fun <T> call(work: () -> T): T = work()
        override fun main(work: () -> Unit) = work()
        override fun early(work: (release: (() -> Unit)?) -> Unit) { if (early) work {} else super.early(work) }
    }

    /** Alice at her first main phase after her keep (Bob's keep assumed), every board published kept in [shown]. */
    private fun game(early: Boolean = true): EngineGame {
        var made: EngineGame? = null
        val game = EngineGame(f.game.id, f.a, f.base, f.snapshot(), worker = Inline(early), onShown = { made?.shown?.let { shown += it } })
        made = game
        game.tick(live = false, now = 1_000)
        assertIs<GameDevice.Acted.Sent>(game.keep(emptyList()))
        game.tick(live = false, now = 2_000)
        assertEquals(GameDevice.Status.YourMove, game.shown!!.status)
        return game
    }

    private fun option(game: EngineGame, kind: (Input) -> Boolean): Input =
        game.read { d -> Engine.options(d.model.engine, PlayerId(f.a)).first(kind) }

    /** The layer computations, boards built and the device's points worked out while [work] runs. */
    private fun counted(game: EngineGame, work: () -> Unit): Triple<Int, Int, Int> {
        var layers = 0
        val boards = game.boardsBuilt
        val points = game.read { it.pointsWorkedOut }
        Engine.layersWorkedOut = { layers++ }
        try { work() } finally { Engine.layersWorkedOut = null }
        return Triple(layers, game.boardsBuilt - boards, game.read { it.pointsWorkedOut } - points)
    }

    // R3 item 1 (SYS-025): a land played at Alice's main phase is shown at once (its early board); nothing follows it by
    // itself, so the board published with its whole outcome is that board again, with only its node and status new
    @Test fun anEarlyBoardIsPublishedAgainWhenNothingFollowedIt() {
        val game = game()
        val land = option(game) { it is Input.PlayLand }
        shown.clear()
        val (_, built, _) = counted(game) { assertEquals(GameDevice.Acted.Made, game.act(land)) }
        assertEquals(1, built, "one board built for the action")
        assertEquals(2, shown.size, "its early board, then its whole outcome")
        val (early, whole) = shown
        assertSame(early.board.board, whole.board.board, "the same board published again")
        assertEquals(game.read { it.session.tip }, whole.board.nodeId)
        assertSame(early.values, whole.values)
        assertNotNull(whole.values?.handPlays, "the hand plays come with the board after the early one")
    }

    // R3 item 2: ticks at an unchanged point (Alice's own main phase) build no board and search nothing
    @Test fun aTickAtAnUnchangedPointBuildsAndSearchesNothing() {
        val game = game()
        game.tick(live = false, now = 3_000)
        shown.clear()
        assertEquals(Triple(0, 0, 0), counted(game) { for (now in 4_000L..9_000L step 1_000) game.tick(live = false, now = now) })
        assertTrue(shown.isEmpty())
    }

    // R3 item 3: every board published (early boards, whole outcomes, the motion steps in between) comes with its computed
    // values, equal to what ComputedBoard works out for it; the boards after an action with the hand plays, equal to
    // HandPlays'; the card menus' plays equal to CardPlays', worked out once per board; the table reads them, working
    // nothing out itself
    @Test fun theValuesComeWithTheBoardAndEqualTheTablesOwn() {
        val game = game()
        shown.clear()
        // A land, a creature, a pass to the next turn and back: early boards, whole outcomes, steps
        game.act(option(game) { it is Input.PlayLand })
        assertIs<GameDevice.Acted>(game.act(option(game) { it is Input.Cast }))
        game.pass()
        game.endTurn()
        assertTrue(shown.size >= 4, "${shown.size} boards")
        val boards = shown.flatMap { s -> listOf(s.board) + s.transition?.steps.orEmpty() }.distinctBy { System.identityHashCode(it.board) }
        for (play in boards) {
            val values = assertNotNull(play.values?.takeIf { it.viewer == f.a }, "values with board ${play.nodeId}")
            assertComputed(computedOf(play), values.computed, play)
            assertSame(values.computed, AutomatumBoards.cached(play, f.a), "the table reads the engine thread's")
        }
        val made = EngineBoardValues.made
        for (s in shown) {
            val play = s.board
            val values = assertNotNull(s.values)
            val hand = values.handPlays ?: continue
            assertEquals(HandPlays.of(stateOf(play), f.a, null).byCard, hand.byCard, "hand plays on ${play.nodeId}")
            assertSame(hand, AutomatumBoards.handPlays(play, f.a) { emptyList() })
            val cards = AutomatumBoards.cardPlays(play, f.a) { emptyList() }
            assertEquals(cardPlaysOf(play).byCard, cards.byCard, "card plays on ${play.nodeId}")
            assertSame(cards, AutomatumBoards.cachedCardPlays(play, f.a), "once per board")
        }
        assertEquals(made, EngineBoardValues.made, "the table made no computed board of its own")
        assertTrue(shown.any { it.values?.handPlays?.byCard?.isNotEmpty() == true }, "some board had something to cast")
    }

    // R3 (measured, printed): one own action's boards built and layer computations, all on the engine thread now, next to
    // what was built before R3 (every board published projected anew) and what the table's second thread worked out of
    // the published boards before R3 (ComputedBoard and HandPlays rebuilt from each board)
    @Test fun anOwnActionsWorkIsCounted() {
        for ((what, action) in listOf<Pair<String, (EngineGame) -> Unit>>(
            "land" to { g -> g.act(option(g) { it is Input.PlayLand }) },
            "pass" to { g -> g.pass() })) {
            shown.clear()
            AutomatumBoards.reset()
            val game = game()
            shown.clear()
            val (layers, built, _) = counted(game) { action(game) }
            val boards = shown.flatMap { s -> listOf(s.board) + s.transition?.steps.orEmpty() }.distinctBy { System.identityHashCode(it.engine) }
            val publishedBefore = shown.size + shown.sumOf { it.transition?.steps?.size ?: 0 }
            val steps = shown.sumOf { it.transition?.steps?.size ?: 0 }
            var table = 0
            Engine.layersWorkedOut = { table++ }
            try {
                for (play in boards) { computedOf(play); HandPlays.of(stateOf(play), f.a, null) }
            } finally { Engine.layersWorkedOut = null }
            println("R3 $what: boards built before $publishedBefore, after ${built + steps} ($steps motion steps); " +
                "layer computations now $layers on the engine thread, 0 on the table's; the table's thread before: $table")
            assertTrue(built + steps <= publishedBefore)
        }
    }

    /** The engine state [play] shows (the render model's, engine-responsiveness R4a). */
    private fun stateOf(play: TableBoard) = assertNotNull(play.engine).state

    /** What ComputedBoard works out for [play] from its own state, as the table would. */
    private fun computedOf(play: TableBoard): ComputedBoard =
        ComputedBoard.of(play.board, play.definitions, emptyMap(), f.a, stateOf(play), Engine.view(stateOf(play)))

    /** What CardPlays works out for [play] from its own state, as the table would. */
    private fun cardPlaysOf(play: TableBoard): CardPlays {
        val state = stateOf(play)
        val view = Engine.view(state)
        return CardPlays.of(state, f.a, view, Engine.visible(state, PlayerId(f.a), view))
    }

    private fun assertComputed(expected: ComputedBoard, actual: ComputedBoard, play: TableBoard) {
        assertEquals(expected.cards, actual.cards, "computed cards on ${play.nodeId}")
        assertEquals(expected.unsupported, actual.unsupported)
        assertEquals(expected.manualAbilities, actual.manualAbilities)
        assertEquals(expected.manualCards, actual.manualCards)
        for (id in expected.cards.keys) assertEquals(expected.explain(id, f.a), actual.explain(id, f.a), "Inspect's lines of $id")
    }
}
