package org.asyncttwiz.android

import arcanum.api.CardDefinition
import arcanum.api.CardType
import arcanum.api.FaceDefinition
import arcanum.api.Layout
import arcanum.api.ManaCost
import arcanum.api.PrintedNumber
import arcanum.api.Subtype
import arcanum.api.Supertype
import arcanum.engine.Engine
import arcanum.engine.decision.Answer
import arcanum.engine.input.Input
import arcanum.engine.setup.DeckCard
import arcanum.engine.setup.Seat
import arcanum.engine.setup.StartOptions
import arcanum.engine.state.CardId
import arcanum.engine.state.GameState
import arcanum.engine.state.PlayerId
import org.asyncttwiz.automatum.ComputedBoard
import org.asyncttwiz.automatum.EngineProjection
import org.asyncttwiz.automatum.boundary.EngineLog
import org.asyncttwiz.automatum.boundary.EngineMessages
import org.asyncttwiz.automatum.boundary.EnginePlanner
import org.asyncttwiz.automatum.boundary.EngineRequest
import org.asyncttwiz.automatum.boundary.MessageEngineChannel
import org.asyncttwiz.protocol.BoardState
import org.asyncttwiz.protocol.TurnState
import org.asyncttwiz.protocol.game.EventBuilder
import org.asyncttwiz.protocol.game.GameModel
import org.asyncttwiz.protocol.game.LineRandom
import org.asyncttwiz.protocol.game.TableState
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The browser's table plays (docs/specs/web-client.md §1 item 3, §11 step 4; WEB-007): a board's hand plays and card
 * menus' plays asked of the engine's Web Worker ([EngineBoundary.remote]) arrive later as its answers, the same as worked
 * out in-process; nothing waits for good (a newer board, a lost worker); Android keeps working them out in-process.
 * The worker here is the engine side of the boundary ([EnginePlanner]) behind the page's message channel, answering
 * when [deliver] says.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WebEnginePlaysTest {
    private val alice = "alice"
    private val model = game()
    private val planner = EnginePlanner()
    private val posted = ArrayDeque<String>()
    private val channel = MessageEngineChannel({ posted += it }, EngineLog { _, _ -> })

    @After fun noWorker() = EngineBoundary.reset()

    /** The worker answers every message posted so far, in order. */
    private fun deliver() {
        while (posted.isNotEmpty()) channel.received(EngineMessages.encode(planner.handle(EngineMessages.decodeRequest(posted.removeFirst()))))
    }

    private fun values(state: GameState = model.engine, table: TableState? = model.table) = EngineBoardValues(alice, ComputedBoard.NONE,
        EngineProjection.Board(BoardState(emptyList(), emptyList(), emptyList(), TurnState(alice)), emptyList()), "game", state,
        model.view(), model.visible(PlayerId(alice)), EngineWorker.Direct, table)

    // WEB-007
    @Test fun theWorkersPlaysArriveLaterAndAreThoseWorkedOutInProcess() {
        val inProcess = values()
        val hand = inProcess.handPlays()
        val cards = inProcess.cardPlays()
        assertTrue(hand.byCard.isNotEmpty(), "Alice has a land to play")

        EngineBoundary.useRemote(channel)
        val board = values()
        var woken = 0
        val wake = { woken++; Unit }
        board.boardPlays()
        assertNull(board.handPlays, "nothing worked out on the page")
        assertNull(board.cardPlays)
        assertTrue(board.awaitingPlays(true, wake))
        assertTrue(board.awaitingPlays(false, wake))
        val sent = posted.map(EngineMessages::decodeRequest)
        assertEquals(1, sent.count { it is EngineRequest.Load }, "the board sent once, as a snapshot")
        assertEquals(1, sent.count { it is EngineRequest.HandPlaysOf })
        assertEquals(1, sent.count { it is EngineRequest.CardPlaysOf })

        deliver()
        assertEquals(hand.byCard, assertNotNull(board.handPlays).byCard)
        assertEquals(cards.byCard, assertNotNull(board.cardPlays).byCard)
        assertTrue(woken > 0, "what waited was woken")
        assertFalse(board.awaitingPlays(true, wake))
        assertFalse(board.awaitingPlays(false, wake))
        // Asked again: nothing more goes out
        board.askPlays()
        assertTrue(posted.isEmpty())
    }

    // WEB-007
    @Test fun aNewerBoardEndsTheWaitOfTheOneBefore() {
        EngineBoundary.useRemote(channel)
        val older = values()
        var olderWoken = 0
        older.askPlays()
        assertTrue(older.awaitingPlays(true) { olderWoken++ })
        val newer = values()
        newer.askPlays()
        assertEquals(1, olderWoken, "the older board's wait ended: its answers are stale now")
        assertFalse(older.awaitingPlays(true) {})
        deliver()
        assertNull(older.handPlays, "the older board's answer dropped")
        assertNotNull(newer.handPlays)
        assertNotNull(newer.cardPlays)
    }

    // WEB-007
    @Test fun aLostWorkerEndsTheWaitAndThePageWorksThePlaysOut() {
        EngineBoundary.useRemote(channel)
        val board = values()
        var woken = 0
        board.askPlays()
        assertTrue(board.awaitingPlays(true) { woken++ })
        EngineBoundary.lost("failed to load")
        assertEquals(1, woken)
        assertNull(EngineBoundary.remote)
        assertFalse(board.awaitingPlays(true) {})
        assertTrue(board.handPlays().byCard.isNotEmpty(), "worked out on the page")
        // A later board is worked out in-process at once
        val next = values()
        assertFalse(next.askPlays())
    }

    // WEB-007: Android (no worker) works the hand plays out with the board, in-process
    @Test fun withoutAWorkerThePlaysAreWorkedOutWithTheBoard() {
        val board = values()
        assertFalse(board.askPlays())
        board.boardPlays()
        assertNotNull(board.handPlays)
        assertFalse(board.awaitingPlays(true) {})
        assertTrue(posted.isEmpty())
    }

    /** Two 40-card decks of basic lands and vanilla creatures, both hands kept, on to Alice's first main phase. */
    private fun game(): GameModel {
        val bob = "bob"
        fun land(name: String) = CardDefinition(Layout.Normal, listOf(FaceDefinition(name, supertypes = listOf(Supertype.Basic),
            types = listOf(CardType.Land), subtypes = listOf(Subtype(name)))))
        fun creature(name: String, cost: String, size: Int) = CardDefinition(Layout.Normal, listOf(FaceDefinition(name,
            ManaCost.parse(cost), types = listOf(CardType.Creature), subtypes = listOf(Subtype("Bear")),
            power = PrintedNumber.Fixed(size), toughness = PrintedNumber.Fixed(size))))
        val deck = List(20) { land("Forest") } + List(20) { creature("Test Cub", "{1}{G}", 2) }
        val seats = listOf(alice, bob).map { p -> Seat(PlayerId(p), deck.mapIndexed { i, card -> DeckCard(CardId("$p-$i"), card) }) }
        val model = GameModel(Engine.newGame(seats), TableState(secret = LineRandom.secret(ByteArray(32) { 3 })))
        fun event(id: String, parent: String, author: String, input: (GameState) -> Input) =
            EventBuilder(model, id, parent, author).run { perform(input(model.engine)); answerRandoms(); build() }
        fun keep(p: String): (GameState) -> Input = { s ->
            val d = s.pending?.decision
            if (d != null && d.player == PlayerId(p)) Input.Answer(d.id, Answer.Option("Keep")) else Input.SetupAnswer(PlayerId(p), Answer.Option("Keep"))
        }
        event("start", "root", "server") {
            Input.StartGame(StartOptions(startingPlayer = PlayerId(alice), simultaneousMulligans = true, freeFirstMulligan = true))
        }
        event("keep-b", "start", bob, keep(bob))
        event("keep-a", "keep-b", alice, keep(alice))
        var parent = "keep-a"
        var passes = 0
        while (Engine.options(model.engine, PlayerId(alice)).none { it is Input.PlayLand }) {
            check(passes < 20) { "no land play after $passes passes" }
            val holder = requireNotNull(model.engine.priority.holder) { "nobody holds priority" }
            event("pass-$passes", parent, holder.value) { Input.PassPriority(holder) }
            parent = "pass-${passes++}"
        }
        return model
    }
}
