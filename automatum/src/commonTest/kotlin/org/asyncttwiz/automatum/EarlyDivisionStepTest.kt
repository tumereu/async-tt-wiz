@file:OptIn(arcanum.engine.state.ArcanumTestSetup::class)

package org.asyncttwiz.automatum

import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.decision.Share
import arcanum.engine.input.Input
import arcanum.engine.state.GameState
import arcanum.engine.state.TargetRef
import org.asyncttwiz.protocol.EngineRecordKind
import org.asyncttwiz.protocol.MtgRef
import org.asyncttwiz.protocol.MtgAction
import org.asyncttwiz.protocol.PlayAction
import org.asyncttwiz.protocol.game.EarlyAnswer
import org.asyncttwiz.protocol.game.GameModel
import org.asyncttwiz.protocol.game.TableChange
import org.asyncttwiz.protocol.game.TableState
import org.asyncttwiz.protocol.game.WhoActs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * STK-034 (review 519b7400ec): an early division of combat damage answers only the combat damage step it was given in. With
 * first strike a combat has two combat damage steps (CR 510.4) and each creature dealing damage in both divides it anew in each
 * (CR 510.1c), so one given in the first-strike step was wrongly applied again, without asking, in the regular one.
 */
class EarlyDivisionStepTest {
    init {
        Cards.invent("Twinfang Duelist", "{2}{R}{R}", "Creature — Human Warrior", "Double strike", "3/3")
        Cards.invent("Ridge Brawler", "{2}{R}{R}", "Creature — Human Warrior", "Double strike", "3/3")
        Cards.invent("Bulwark Ox", "{3}{W}", "Creature — Ox", "", "4/4")
    }
    private val table = EngineTable().apply { turn = 3; step = TurnStep.DeclareAttackers; priority = null }
    private val alice get() = table.alice
    private val bob get() = table.bob

    private fun run(state: GameState, board: org.asyncttwiz.protocol.BoardState = table.board(state)) =
        EngineRun.of(state, board, "game", table::seed)

    private fun passUntil(start: GameState, done: (GameState) -> Boolean): GameState {
        var state = start
        repeat(60) {
            if (done(state)) return state
            val holder = assertNotNull(state.priority.holder, "nobody holds priority at ${state.turn.step}")
            state = run(state).also { it.perform(Input.PassPriority(holder)) }.working
        }
        error("never reached")
    }

    /** Passes, and answers other divisions with their suggestion, until [card]'s division is open. */
    private fun toDivision(start: GameState, card: String): GameState {
        var state = start
        repeat(60) {
            if (dividing(state, card) != null) return state
            val open = state.pending?.decision as? Decision.Divide
            state = run(state).also {
                if (open != null) it.perform(Input.Answer(open.id, Answer.Division(open.suggested)))
                else it.perform(Input.PassPriority(assertNotNull(state.priority.holder, "nobody holds priority at ${state.turn.step}")))
            }.working
        }
        error("never reached")
    }

    private fun dividing(state: GameState, card: String): Decision.Divide? =
        (state.pending?.decision as? Decision.Divide)?.takeIf { it.source == table.obj(card) }

    /** Alice's two double strikers, each blocked by two of Bob's 4/4 Oxen, at the first-strike step's first division. */
    private fun firstStrikeStep(): Triple<GameState, Pair<String, String>, List<String>> {
        val (duelist, brawler) = table.put(alice, Zone.Battlefield, "Twinfang Duelist", "Ridge Brawler")
        val oxen = table.put(bob, Zone.Battlefield, "Bulwark Ox", "Bulwark Ox", "Bulwark Ox", "Bulwark Ox")
        val declared = run(table.state()).also { CombatFlow.declareAttackers(it, listOf(LaneAttack(duelist), LaneAttack(brawler))) }.working
        val blocking = passUntil(declared) { it.turn.step == TurnStep.DeclareBlockers && it.priority.holder == null }
        val blocked = run(blocking).also {
            CombatFlow.declareBlockers(it, listOf(LaneBlock(oxen[0], duelist), LaneBlock(oxen[1], duelist), LaneBlock(oxen[2], brawler),
                LaneBlock(oxen[3], brawler)))
        }.working
        val first = passUntil(blocked) { it.pending != null }
        assertEquals(TurnStep.CombatDamage, first.turn.step)
        assertTrue(first.combat?.firstStrikeStep == true, "the first-strike combat damage step")
        return Triple(first, duelist to brawler, oxen)
    }

    // STK-034, CR 510.4: the combat damage steps of a turn are counted, so an early answer names the one it was given in
    @Test fun theCombatDamageStepsOfATurnAreCounted() {
        val (first, _, _) = firstStrikeStep()
        assertEquals(1, EarlyAnswer.damageStep(first))
        assertNull(EarlyAnswer.damageStep(table.state()), "outside a combat damage step")
    }

    // STK-034, CR 510.4, CR 510.1c: the Brawler's division given early while the Duelist's is open (in its rows, as the table's
    // controls give it) answers the Brawler's division in the first-strike step; in the regular step both divide again and
    // neither early answer is applied: the Brawler is asked again, and the early answer shows no more
    @Test fun anEarlyDivisionAnswersOnlyTheDamageStepItWasGivenIn() {
        val (first, attackers, oxen) = firstStrikeStep()
        val (duelist, brawler) = attackers
        val open = assertNotNull(dividing(first, duelist) ?: dividing(first, brawler), "a division open: ${first.pending?.decision}")
        val other = if (open.source == table.obj(duelist)) brawler else duelist
        val otherOxen = if (other == brawler) oxen.subList(2, 4) else oxen.subList(0, 2)
        // Given early on the table (STK-034): all three to the first Ox it fights
        val early = PreAnswer.Division(alice, MtgRef.Card(other, 0), listOf(PreShare(MtgRef.Card(otherOxen[0], 0), 3),
            PreShare(MtgRef.Card(otherOxen[1], 0), 0)), first.turn.number, firstStrike = true)
        val record = assertNotNull((PreAnswers.set(table.board(first), early) as PlayAction.Mtg).action as? MtgAction.SetRecord).after!!
        val staged = table.board(first).let { it.copy(engineRecords = it.engineRecords + record) }
        val changes = EngineDrafts.changes(GameModel(first, TableState()), staged, alice)
        val given = assertIs<TableChange.EarlyAnswered>(changes.single()).after!!
        assertEquals(1, given.damageStep, "given in the turn's first combat damage step")
        val tableState = TableState(earlyAnswers = mapOf(given.key to given))
        // The table shows it in the first-strike step, as the first-strike step's
        val shown = EngineDrafts.records(first, tableState).filter { it.kind == EngineRecordKind.PreAnswer }
            .map { PreAnswers.decode(it) as PreAnswer.Division }
        assertEquals(listOf(true), shown.map { it.firstStrike })

        // The open division answered by Alice: the other creature's division opens and its early answer answers it
        val answered = run(first).also { it.perform(Input.Answer(open.id, Answer.Division(open.suggested))) }.working
        val otherDivision = assertNotNull(dividing(answered, other), "the other division opens: ${answered.pending?.decision}")
        val automatic = assertNotNull(WhoActs.automaticAnswer(GameModel(answered, tableState), otherDivision), "the early answer applies")
        assertEquals(Answer.Division(listOf(Share(TargetRef.Object(table.obj(otherOxen[0])), 3), Share(TargetRef.Object(table.obj(otherOxen[1])), 0))),
            automatic.answer)

        // The regular combat damage step: both double strikers divide again (CR 510.4), the early answer no longer answers
        val regular = toDivision(run(answered).also { it.perform(automatic) }.working, other)
        assertEquals(TurnStep.CombatDamage, regular.turn.step)
        assertTrue(regular.combat?.firstStrikeStep != true, "the regular combat damage step")
        assertEquals(2, EarlyAnswer.damageStep(regular))
        assertNull(WhoActs.automaticAnswer(GameModel(regular, tableState), dividing(regular, other)!!), "asked again, not answered early")
        assertTrue(EngineDrafts.records(regular, tableState).none { it.kind == EngineRecordKind.PreAnswer }, "the early answer shows no more")
        // Nor is the used one withdrawn by a staging there: it is simply over
        assertTrue(EngineDrafts.changes(GameModel(regular, tableState), table.board(regular), alice).isEmpty())
    }
}
