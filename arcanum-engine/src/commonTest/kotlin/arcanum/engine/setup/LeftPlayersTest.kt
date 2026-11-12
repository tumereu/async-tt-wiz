package arcanum.engine.setup

import arcanum.api.Cost
import arcanum.engine.action.Act
import arcanum.engine.action.Asker
import arcanum.engine.action.NeedDecision
import arcanum.engine.change.Change
import arcanum.engine.change.Recorder
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.state.PlayerId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/** Decisions of a player who left the game (CR 800.4f–h), as a flow asks them. */
class LeftPlayersTest {
    private val bear = arcanum.engine.cards.Cards.invent("Left Plain Bear", "{1}{G}", "Creature — Bear", "", "2/2")

    /** An act on the scenario's state after [left] left the game, with the [answers] given so far. */
    private fun Scenario.actAfter(left: PlayerId, answers: List<Answer> = emptyList()): Act {
        val s = state.deepCopy()
        s.apply(Change.Lost(left, null, arcanum.engine.setup.LossReason.Conceded))
        return Act(Recorder(s), Asker(answers, "test"))
    }

    private fun Act.asked(block: Act.() -> Unit): Decision = assertFailsWith<NeedDecision> { block() }.decision

    // CR 800.4f: a cost a player who left would choose whether to pay isn't paid, without asking
    @Test
    fun costNotPaid() = scenario(players = 3) {
        alice.battlefield(bear)
        val act = actAfter(bob.id)
        assertEquals(Answer.YesNo(false), act.ask { Decision.May(it, bob.id, card(bear), "Pay {2}?", Cost.mana(arcanum.api.ManaCost.parse("{2}")!!)) })
    }

    // CR 800.4g: another choice an object asks of them: the object's controller's other opponent makes it (the only one: no question)
    @Test
    fun anotherOpponentChooses() = scenario(players = 3) {
        alice.battlefield(bear)
        val act = actAfter(bob.id)
        val d = act.asked { ask { Decision.ChooseNumber(it, bob.id, card(bear), 0, 3) } }
        assertIs<Decision.ChooseNumber>(d)
        assertEquals(carol.id, d.player)
    }

    // CR 800.4g: with several other opponents, the object's controller chooses who makes the choice, then they make it
    @Test
    fun controllerChoosesWhoDecides() = scenario(players = 4) {
        alice.battlefield(bear)
        val first = actAfter(bob.id).asked { ask { Decision.ChooseNumber(it, bob.id, card(bear), 0, 3) } }
        assertIs<Decision.ChoosePlayer>(first)
        assertEquals(alice.id, first.player)
        assertEquals(setOf(carol.id, dave.id), first.options.toSet())
        val second = actAfter(bob.id, listOf(Answer.Player(dave.id))).asked { ask { Decision.ChooseNumber(it, bob.id, card(bear), 0, 3) } }
        assertEquals(dave.id, second.player)
        val answer = actAfter(bob.id, listOf(Answer.Player(dave.id), Answer.Number(2))).ask { Decision.ChooseNumber(it, bob.id, card(bear), 0, 3) }
        assertEquals(Answer.Number(2), answer)
    }

    // CR 800.4h: a choice the rules ask of them (no object asks it) is made by the next player in turn order
    @Test
    fun nextPlayerMakesARulesChoice() = scenario(players = 3) {
        val d = actAfter(bob.id).asked { ask { Decision.ChooseNumber(it, bob.id, null, 0, 3) } }
        assertEquals(carol.id, d.player)
    }
}
