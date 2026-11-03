package org.asyncttwiz.automatum

import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.state.PlayerId
import arcanum.engine.state.TargetRef
import org.asyncttwiz.automatum.PausePoints.At
import org.asyncttwiz.automatum.PausePoints.Point
import org.asyncttwiz.protocol.MtgPriority
import org.asyncttwiz.protocol.PlayZone
import org.asyncttwiz.protocol.TurnPosition
import org.asyncttwiz.protocol.TurnState
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pause points (integration spec I-D41, review item B): the host passes for a player who could act wherever they have
 * neither a stop (their own main phase) nor a pause (something they could react to), unless their "Full control" is
 * on. Every call is checked by [RoundTrip].
 */
class PausePointsTest {
    init { CombatCards.ensured }
    private val table = AtwTable("pauses")
    private val alice get() = table.alice
    private val bob get() = table.bob
    private val random = Random(5)
    private fun seed() = org.asyncttwiz.protocol.RandomV1.encodeSeed(random.nextBytes(32))

    private fun at(position: TurnPosition, number: Int, player: String = alice) {
        table.turn = TurnState(player, "$number", position.phase, position.step)
        table.priority = MtgPriority(player)
    }
    private fun game() = RoundTrip(table.state(), table.definitions)
    private fun position(rt: RoundTrip) = TurnPosition.of(rt.state.board.turn)
    private fun life(rt: RoundTrip, player: String) = rt.state.board.players.single { it.accountId == player }.life
    private fun log(rt: RoundTrip) = EngineLog.from(rt.tags, rt.state, table.definitions)
    private fun player(id: String) = TargetRef.Player(PlayerId(id))

    /** [player] casts [card] at [target] with the suggested mana, as the app sends it ([CastFlow.sent]). */
    private fun cast(rt: RoundTrip, player: String, card: String, target: TargetRef) {
        val plays = HandPlays.of(rt.state, table.definitions, player, log(rt))
        var flow = CastFlow.start(rt.state, table.definitions, player, log(rt), plays.of(card).first(), CastFlow.Seeds(::seed))
        flow = flow.answer(Answer.Targets(listOf(target)))
        flow = flow.answer(Answer.ManaPayment((flow.pending as Decision.ManaPayment).suggestion))
        rt.apply(flow.sent(), player)
    }

    /** Both players hold Storm Spark with an untapped Mountain: either could act in every step. */
    private fun armed() {
        table.put(alice, PlayZone.BATTLEFIELD, "Mountain")
        table.put(bob, PlayZone.BATTLEFIELD, "Mountain")
        table.put(alice, PlayZone.HAND, "Storm Spark")
        table.put(bob, PlayZone.HAND, "Storm Spark")
        table.put(alice, PlayZone.LIBRARY, "Island", "Island", "Island")
        table.put(bob, PlayZone.LIBRARY, "Island", "Island", "Island")
    }

    // STK-036, SYS-071 (I-D41): the rule itself, read from nothing hidden
    @Test fun theRule() {
        val main = TurnPosition.PRECOMBAT_MAIN
        // Something on the Stack: another player's is a pause, your own only with Full control
        assertEquals(Point.Pause, PausePoints.of(bob, alice, main, alice, attacking = false, fullControl = false))
        assertNull(PausePoints.of(alice, alice, main, alice, attacking = false, fullControl = false))
        assertEquals(Point.Pause, PausePoints.of(alice, alice, main, alice, attacking = false, fullControl = true))
        // An empty Stack: your own main phases are stops, the other player's are nothing to you
        assertEquals(Point.Stop, PausePoints.of(alice, alice, main, null, attacking = false, fullControl = false))
        assertEquals(Point.Stop, PausePoints.of(alice, alice, TurnPosition.POSTCOMBAT_MAIN, null, attacking = false, fullControl = true))
        assertNull(PausePoints.of(bob, alice, main, null, attacking = false, fullControl = false))
        // Combat: the defending player answers an attack, both players the blocks; nothing when nobody attacks
        assertEquals(Point.Pause, PausePoints.of(bob, alice, TurnPosition.DECLARE_ATTACKERS, null, attacking = true, fullControl = false))
        assertNull(PausePoints.of(alice, alice, TurnPosition.DECLARE_ATTACKERS, null, attacking = true, fullControl = false))
        assertEquals(Point.Pause, PausePoints.of(alice, alice, TurnPosition.DECLARE_BLOCKERS, null, attacking = true, fullControl = false))
        assertEquals(Point.Pause, PausePoints.of(bob, alice, TurnPosition.DECLARE_BLOCKERS, null, attacking = true, fullControl = false))
        assertNull(PausePoints.of(bob, alice, TurnPosition.DECLARE_ATTACKERS, null, attacking = false, fullControl = false))
        // Finalreview-combat #7: one pause for one attack: none on the empty Stack once the attacking player's trigger or
        // spell of the step was answered (unless Full control)
        assertNull(PausePoints.of(bob, alice, TurnPosition.DECLARE_ATTACKERS, null, attacking = true, fullControl = false, answered = true))
        assertEquals(Point.Pause, PausePoints.of(bob, alice, TurnPosition.DECLARE_ATTACKERS, null, attacking = true, fullControl = true, answered = true))
        assertEquals(Point.Pause, PausePoints.of(bob, alice, TurnPosition.DECLARE_ATTACKERS, alice, attacking = true, fullControl = false, answered = true))
        // The other player's end step (I-D41 decision B, pausereview #8); the active player's own end step isn't one
        assertEquals(Point.Pause, PausePoints.of(bob, alice, TurnPosition.END_STEP, null, attacking = false, fullControl = false))
        assertNull(PausePoints.of(alice, alice, TurnPosition.END_STEP, null, attacking = false, fullControl = false))
        // Every other step: a pause only with Full control
        for (step in listOf(TurnPosition.UPKEEP, TurnPosition.DRAW, TurnPosition.BEGIN_COMBAT, TurnPosition.COMBAT_DAMAGE,
            TurnPosition.END_COMBAT, TurnPosition.END_STEP)) for (holder in listOf(alice, bob)) {
            if (step != TurnPosition.END_STEP || holder == alice)
                assertNull(PausePoints.of(holder, alice, step, null, attacking = false, fullControl = false), "$step")
            assertEquals(Point.Pause, PausePoints.of(holder, alice, step, null, attacking = false, fullControl = true), "$step")
        }
    }

    // STK-036, review item B: End turn while Bob could cast Storm Spark in every step: Automatum passes for both players
    // through the rest of her turn up to her end step, his pause (I-D41 decision B); his pass there goes on through his
    // upkeep and draw up to his main phase, his stop: two steps instead of eight
    @Test fun endTurnRunsToTheOtherPlayersMainPhase() {
        at(TurnPosition.PRECOMBAT_MAIN, 3)
        armed()
        val rt = game()
        rt.run(alice) { StepFlow.pass(it, alice, StepFlow.Until.EndOfTurn) }
        assertEquals(TurnPosition.END_STEP, position(rt))
        assertEquals(At(bob, Point.Pause), PausePoints.at(rt.state.board))
        rt.run(bob) { StepFlow.pass(it, bob) }
        assertEquals(2, rt.batches.size)
        assertEquals(TurnState(bob, "4", TurnPosition.PRECOMBAT_MAIN.phase, TurnPosition.PRECOMBAT_MAIN.step), rt.state.board.turn)
        assertEquals(bob, rt.state.board.priority?.holder)
        assertEquals(At(bob, Point.Stop), PausePoints.at(rt.state.board))
    }

    // SYS-071 (I-D41): with Full control Bob is stopped for in each step in which he could act, as before pause points
    @Test fun fullControlStopsInEveryStep() {
        at(TurnPosition.PRECOMBAT_MAIN, 3)
        armed()
        table.fullControl += bob
        val rt = game()
        rt.run(alice) { StepFlow.pass(it, alice) }
        assertEquals(TurnPosition.PRECOMBAT_MAIN, position(rt))
        assertEquals(MtgPriority(bob, 1), rt.state.board.priority)
        assertEquals(At(bob, Point.Pause), PausePoints.at(rt.state.board))
    }

    // STK-036: Next step in Alice's main phase goes on to her attack; nobody is stopped for on the way
    @Test fun nextStepGoesOnToTheAttack() {
        at(TurnPosition.PRECOMBAT_MAIN, 3)
        armed()
        table.put(alice, PlayZone.BATTLEFIELD, "Stout Bear")
        val rt = game()
        rt.run(alice) { StepFlow.pass(it, alice) }
        assertEquals(TurnPosition.DECLARE_ATTACKERS, position(rt))
        assertNull(rt.state.board.priority?.holder, "her attack is awaited (CR 508.1)")
        assertNull(PausePoints.at(rt.state.board))
    }

    // STK-036, CR 117.3c: Alice's Storm Spark at Bob, who could answer with his own: the cast's step ends with Bob holding
    // priority, a pause; his pass resolves it and Alice (who can still play a land) holds priority in her own main phase
    // again, a stop
    @Test fun anOpponentsSpellIsAPause() {
        at(TurnPosition.PRECOMBAT_MAIN, 3)
        armed()
        table.put(alice, PlayZone.HAND, "Mountain")
        val rt = game()
        val spark = rt.state.board.containers.single { it.zone == PlayZone.HAND && it.accountId == alice }.cards
            .single { table.names[it] == "Storm Spark" }
        cast(rt, alice, spark, player(bob))
        assertEquals(MtgPriority(bob, 1), rt.state.board.priority)
        assertEquals(At(bob, Point.Pause), PausePoints.at(rt.state.board))
        rt.run(bob) { StepFlow.pass(it, bob) }
        assertEquals("17", life(rt, bob))
        assertEquals(At(alice, Point.Stop), PausePoints.at(rt.state.board))
    }

    // STK-036, SYS-071: with Full control the caster keeps priority after her own spell (she could cast another on it)
    @Test fun fullControlKeepsPriorityAfterYourOwnSpell() {
        at(TurnPosition.PRECOMBAT_MAIN, 3)
        armed()
        table.fullControl += alice
        val rt = game()
        val spark = rt.state.board.containers.single { it.zone == PlayZone.HAND && it.accountId == alice }.cards
            .single { table.names[it] == "Storm Spark" }
        cast(rt, alice, spark, player(bob))
        assertEquals(MtgPriority(alice, 0), rt.state.board.priority)
        assertEquals(At(alice, Point.Pause), PausePoints.at(rt.state.board))
        rt.run(alice) { StepFlow.pass(it, alice) }
        assertEquals(At(bob, Point.Pause), PausePoints.at(rt.state.board), "then Bob's pause, as without Full control")
    }

    // STK-036: Alice's attack is a pause for Bob, the defender; his blocks are a pause for Alice, then for Bob
    @Test fun anAttackAndBlocksArePauses() {
        at(TurnPosition.PRECOMBAT_MAIN, 3)
        armed()
        val (bear) = table.put(alice, PlayZone.BATTLEFIELD, "Stout Bear")
        val (ogre) = table.put(bob, PlayZone.BATTLEFIELD, "Hill Ogre")
        val rt = game()
        rt.run(alice) { StepFlow.pass(it, alice) }
        rt.run(alice) { CombatFlow.declareAttackers(it, listOf(LaneAttack(bear))) }
        assertEquals(TurnPosition.DECLARE_ATTACKERS, position(rt))
        assertEquals(At(bob, Point.Pause), PausePoints.at(rt.state.board), "Alice's own priority after attacking was passed for")
        rt.run(bob) { StepFlow.pass(it, bob) }
        assertNull(rt.state.board.priority?.holder, "his blocks are awaited")
        rt.run(bob) { CombatFlow.declareBlockers(it, listOf(LaneBlock(ogre, bear))) }
        assertEquals(TurnPosition.DECLARE_BLOCKERS, position(rt))
        assertEquals(At(alice, Point.Pause), PausePoints.at(rt.state.board))
        rt.run(alice) { StepFlow.pass(it, alice) }
        assertEquals(At(bob, Point.Pause), PausePoints.at(rt.state.board))
    }

    private fun raidcallLizard() = Cards.invent("Raidcall Lizard", "{1}{R}", "Creature — Lizard Assassin",
        "Whenever this creature attacks, defending player loses 1 life and you gain 1 life.", "1/3")

    // STK-036 (finalreview-combat #7): one pause for one attack: Alice's attack trigger on the Stack is Bob's pause (the
    // respond panel); once it resolved he isn't paused again on the empty Stack, and his blocks are awaited
    @Test fun anAttackTriggerIsTheDefendersOnePause() {
        at(TurnPosition.PRECOMBAT_MAIN, 3)
        armed()
        val (lizard) = table.put(alice, PlayZone.BATTLEFIELD, raidcallLizard())
        table.put(bob, PlayZone.BATTLEFIELD, "Hill Ogre")
        val rt = game()
        rt.run(alice) { StepFlow.pass(it, alice) }
        rt.run(alice) { CombatFlow.declareAttackers(it, listOf(LaneAttack(lizard))) }
        assertEquals(TurnPosition.DECLARE_ATTACKERS, position(rt))
        assertEquals(At(bob, Point.Pause), PausePoints.at(rt.state.board), "the trigger on the Stack")
        rt.run(bob) { StepFlow.pass(it, bob) }
        assertEquals("19", life(rt, bob))
        assertNull(rt.state.board.priority?.holder, "no second pause on the empty Stack: his blocks are awaited")
    }

    // STK-036, SYS-071 (finalreview-combat #7): with Full control Bob still gets the empty Stack after the trigger as a
    // pause; the board says the attack was answered, so without Full control it is none (the host's rule, read from ATW)
    @Test fun anAnsweredAttackIsReadFromTheBoard() {
        at(TurnPosition.PRECOMBAT_MAIN, 3)
        armed()
        table.fullControl += bob
        val (lizard) = table.put(alice, PlayZone.BATTLEFIELD, raidcallLizard())
        val rt = game()
        rt.run(alice) { StepFlow.pass(it, alice) }
        // His Full control pauses on the way to the attack (her main phase, beginning of combat)
        while (rt.state.board.priority?.holder == bob) rt.run(bob) { StepFlow.pass(it, bob) }
        rt.run(alice) { CombatFlow.declareAttackers(it, listOf(LaneAttack(lizard))) }
        rt.run(bob) { StepFlow.pass(it, bob) }
        assertEquals("19", life(rt, bob))
        assertEquals(TurnPosition.DECLARE_ATTACKERS, position(rt))
        assertEquals(At(bob, Point.Pause), PausePoints.at(rt.state.board))
        val board = rt.state.board.copy(players = rt.state.board.players.map { it.copy(fullControl = false) })
        assertNull(PausePoints.at(board))
    }
}
