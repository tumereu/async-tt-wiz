package arcanum.engine.combat

import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.Engine
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Attack
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.state.TargetRef
import arcanum.parser.OracleCard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Battles (CR 310): protectors, battles as attack targets (CR 506.2, 508.1b), blocking for them, defense and Sieges. */
class BattleTest {
    private val siege = Cards.inventFaces("Transform",
        OracleCard("Siege of the Ember Hollow", "{2}{R}", "Battle — Siege", "", defense = "3"),
        OracleCard("Ember Hollow Warden", null, "Creature — Elemental", "", power = "4", toughness = "4", colorIndicator = "R"),
    )
    private val ox = Cards.invent("Battle Plain Ox", "{2}{G}", "Creature — Ox", "", "3/3")
    private val bear = Cards.invent("Battle Plain Bear", "{1}{G}", "Creature — Bear", "", "2/2")

    private fun Scenario.libraries() {
        players.forEach { it.library("Island", "Island", "Island") }
    }

    private val defense = listOf(CounterKind.Defense to 3)

    private fun Scenario.protector() = state.obj(card(siege))?.protector

    private fun Scenario.attackSiege(vararg attackers: String) =
        perform(Input.DeclareAttackers(alice.id, attackers.map { Attack(card(it), TargetRef.Object(card(siege))) }))

    // CR 310.12a: as a Siege enters, its controller chooses its protector among their opponents (the only one: no question);
    // CR 310.4b: it enters with its printed defense
    @Test
    fun siegeEntersProtectedByAnOpponent() = scenario {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(siege)
        alice.casts(siege)
        allPass()
        expect { siege.counters(CounterKind.Defense) shouldBe 3 }
        assertEquals(bob.id, protector())
    }

    // CR 310.9a, 310.12a: with several opponents, the controller chooses which protects it
    @Test
    fun protectorChosenAmongOpponents() = scenario(players = 3) {
        alice.battlefield("Mountain", "Mountain", "Mountain")
        alice.hand(siege)
        alice.casts(siege)
        allPass()
        val choice = assertIs<Decision.ChoosePlayer>(pending)
        assertEquals(setOf(bob.id, carol.id), choice.options.toSet())
        answer(Answer.Player(carol.id))
        assertEquals(carol.id, protector())
    }

    // CR 704.5x: a battle put onto the battlefield without a protector gets one as the state-based actions are checked
    @Test
    fun protectorByStateBasedAction() = scenario {
        alice.battlefield(siege, counters = defense)
        settle()
        assertEquals(bob.id, protector())
    }

    // CR 310.9b, 508.1b: a Siege can be attacked by its own controller, never by its protector
    @Test
    fun attackTargets() = scenario {
        libraries()
        alice.battlefield(siege, counters = defense)
        alice.battlefield(ox)
        bob.battlefield(bear)
        advanceTo(TurnStep.DeclareAttackers)
        val targets = Engine.options(state, alice.id).filterIsInstance<Input.DeclareAttackers>().flatMap { d -> d.attacks.map { it.target } }
        assertTrue(TargetRef.Object(card(siege)) in targets)
        advanceTo(TurnStep.DeclareAttackers, turn = 2)
        val bobs = Engine.options(state, bob.id).filterIsInstance<Input.DeclareAttackers>().flatMap { d -> d.attacks.map { it.target } }
        assertFalse(TargetRef.Object(card(siege)) in bobs)
        assertFailsWith<IllegalArgumentException> {
            perform(Input.DeclareAttackers(bob.id, listOf(Attack(card(bear), TargetRef.Object(card(siege))))))
        }
    }

    // CR 310.9c, d: the protector is the defending player and blocks for it; CR 310.6: damage removes defense counters
    @Test
    fun protectorBlocksAndDamageRemovesDefense() = scenario {
        alice.battlefield(siege, counters = defense)
        alice.battlefield(ox, bear)
        bob.battlefield("Umbral Elves")
        advanceTo(TurnStep.DeclareAttackers)
        attackSiege(ox, bear)
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks("Umbral Elves" to bear)
        advanceTo(TurnStep.CombatDamage)
        expect {
            siege.counters(CounterKind.Defense) shouldBe 0
            "Umbral Elves".zone shouldBe Zone.Graveyard
            bob.life shouldBe 20
        }
    }

    // CR 310.12b, 704.5v: the last defense counter removed, the Siege is exiled and may be cast transformed without paying
    // its mana cost (it waits for its trigger instead of going to the graveyard)
    @Test
    fun defeatedSiegeIsCastTransformed() = scenario {
        alice.battlefield(siege, counters = defense)
        alice.battlefield(ox)
        advanceTo(TurnStep.DeclareAttackers)
        attackSiege(ox)
        advanceTo(TurnStep.CombatDamage)
        expect { stack.size shouldBe 1 }
        allPass()
        answer(Answer.YesNo(true))
        allPass()
        expect {
            "Ember Hollow Warden".zone shouldBe Zone.Battlefield
            "Ember Hollow Warden".power shouldBe 4
            "Ember Hollow Warden".controller shouldBe alice.id
        }
    }

    // CR 704.5y: a protector who left the game can't be one: the controller's other opponent becomes it
    @Test
    fun protectorLeaves() = scenario(players = 3) {
        alice.battlefield(siege, counters = defense)
        settle()
        if (pending is Decision.ChoosePlayer) answer(Answer.Player(bob.id))
        assertEquals(bob.id, protector())
        perform(Input.Concede(bob.id))
        settle()
        assertEquals(carol.id, protector())
    }
}
