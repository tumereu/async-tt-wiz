package org.asyncttwiz.automatum

import arcanum.api.CardDefinition
import arcanum.api.Layout
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.LoopStop
import arcanum.engine.setup.GameResult
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TargetRef
import arcanum.parser.OracleCard
import arcanum.parser.OracleParser
import org.asyncttwiz.protocol.MtgPriority
import org.asyncttwiz.protocol.PlayZone
import org.asyncttwiz.protocol.TurnPosition
import org.asyncttwiz.protocol.TurnState
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Adversarial review of the Automatum-always-on pivot (I-D52; report `arcanum-pivot-review.md`, outside the repo): the end
 * of a game by the rules, and which cards count as supported (I-D51). Each ignored "pivotreview #N" test pins a problem
 * found and fails while it isn't fixed.
 */
class PivotReviewTest {
    private val table = AtwTable("pivotreview")
    private val alice get() = table.alice
    private val bob get() = table.bob

    // pivotreview #1 — SYS-055, TRN-064, CR 104.2a, 104.3b, 704.5a (I-D52: every game is played by the rules): Alice's
    // Storm Spark takes Bob from 3 life to 0; the engine says Alice won, but the game doesn't end and nobody is told: the
    // play has no result or suggestion, and the engine gives nobody anything to do (no priority holder, no declaration),
    // so the step arrow (read from ATW's board) still offers a step that Automatum refuses. The players must find
    // "Suggest game end" (or Concede) themselves. Expected: the engine's result ends the game the way an accepted result
    // does, or at least shows as a result suggestion both players see (see the report's owner question).
    @Ignore("pivotreview #1: a game the rules ended goes on")
    @Test fun aPlayerAtZeroLifeEndsTheGame() {
        table.put(alice, PlayZone.BATTLEFIELD, "Mountain")
        table.put(alice, PlayZone.HAND, "Storm Spark")
        table.put(alice, PlayZone.LIBRARY, "Mountain")
        table.put(bob, PlayZone.LIBRARY, "Island")
        val state = table.state()
        val rt = RoundTrip(state.copy(board = state.board.copy(players = state.board.players.map {
            if (it.accountId == bob) it.copy(life = "3") else it })), table.definitions)
        rt.cast(alice, "Storm Spark"); rt.target(TargetRef.Player(PlayerId(bob))); rt.settle()
        rt.resolveAll()
        assertEquals("0", rt.state.board.players.single { it.accountId == bob }.life)
        assertEquals(GameResult.Won(PlayerId(alice)), Engine.result(rt.engine()))
        // Automatum has nothing left for anyone to do
        val flow = SharedFlow(rt.state, rt.definitions, EngineLog.from(rt.tags, rt.state, rt.definitions), rt::seed)
        assertNull(flow.holder); assertNull(flow.declaring); assertNull(flow.decision)
        assertNotNull(rt.state.board.resultSuggestion, "the rules' result reaches the game's result")
    }

    // pivotreview #1 — TRN-060, CR 104.4b (I-D52: loop protection is the engine's Continue / Draw choice): choosing Draw
    // makes the engine's game a draw, but ATW's play goes on as before, with no result and nothing to do
    @Ignore("pivotreview #1: a game the rules ended goes on")
    @Test fun aDrawChosenOnALoopEndsTheGame() {
        val revenant = Cards.invent("Restless Revenant", "{1}{B}", "Creature — Spirit",
            "When this creature enters, sacrifice a creature.\nWhen this creature dies, return it to the battlefield under its owner's control.",
            "1/1")
        table.turn = TurnState(alice, "3", TurnPosition.PRECOMBAT_MAIN.phase, TurnPosition.PRECOMBAT_MAIN.step)
        table.priority = MtgPriority(alice)
        table.put(alice, PlayZone.BATTLEFIELD, "Swamp", "Swamp")
        table.put(alice, PlayZone.HAND, revenant)
        table.put(alice, PlayZone.LIBRARY, "Island")
        table.put(bob, PlayZone.LIBRARY, "Island")
        val rt = RoundTrip(table.state(), table.definitions)
        rt.cast(alice, revenant)
        rt.settle()
        rt.run(alice) { StepFlow.pass(it, alice) }
        rt.run(bob) { StepFlow.answer(it, Answer.Option(LoopStop.DRAW)) }
        assertEquals(GameResult.Draw, Engine.result(rt.engine()))
        assertNotNull(rt.state.board.resultSuggestion, "the draw reaches the game's result")
    }

    // pivotreview #6 — I-D51, LOB-115: a card is unsupported only when Automatum leaves part of it to the players. The
    // engine carries out "skips their next turn" itself (`Effect.SkipNextTurn`, a skipped turn in the turn record), but
    // `AutomatumSupport` still lists `skip-next-turn` among the effects done by hand, so a card whose every word Automatum
    // plays (this SOS wording) is marked unsupported and blocks its deck, cube or set (I-D52 hard block)
    @Ignore("pivotreview #6: a card Automatum plays fully is marked unsupported")
    @Test fun aSkippedTurnIsSupported() {
        val definition = CardDefinition(Layout.Normal, listOf(OracleParser.parse(OracleCard("Coinstorm Hex", "{3}{U}{U}", "Sorcery",
            "Flip five coins. Target opponent skips their next X turns, where X is the number of coins that came up heads.", null, null))))
        assertTrue(definition.faces.single().abilities.none { it is arcanum.api.Ability.Unparsed }, "the parser reads it: $definition")
        assertEquals(emptyList(), AutomatumSupport.handParts(definition))
    }
}
