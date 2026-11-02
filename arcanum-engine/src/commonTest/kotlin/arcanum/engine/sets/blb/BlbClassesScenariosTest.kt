package arcanum.engine.sets.blb

import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** Bloomburrow's Classes (CR 716), played with invented cards of the parser's chunk 05. */
class BlbClassesScenariosTest {
    private val steward = Cards.invent("Steward's Knack", "{2}{W}", "Enchantment — Class", """
        (Gain the next level as a sorcery to add its ability.)
        Whenever one or more tokens you control enter, draw a card. This ability triggers only once each turn.
        {W}: Level 2
        When this Class becomes level 2, create a token that's a copy of target token you control.
        {3}{W}: Level 3
        Creature tokens you control get +2/+2.
    """)
    private val tavern = Cards.invent("Tavernkeeper's Knack", "{1}{G}", "Enchantment — Class", """
        (Gain the next level as a sorcery to add its ability.)
        At the beginning of combat on your turn, put a +1/+1 counter on target creature you control.
        {G}: Level 2
        Permanents you control with counters on them have ward {1}.
        {3}{G}: Level 3
        If you would put one or more counters on a permanent or player, put twice that many of each of those kinds of counters on that permanent or player instead.
    """)
    private val bunnies = Cards.invent("Bunny Call", "{2}{W}", "Sorcery", "Create three 1/1 white Rabbit creature tokens.")
    private val bear = Cards.invent("Mossback Cub", "{1}{G}", "Creature — Bear", "", "2/2")

    private val p1p1 = CounterKind.PowerToughness(1, 1)

    private fun Scenario.targets(vararg names: String) {
        if (pending is Decision.Targets) answer(Answer.Targets(names.map { target(it) }))
    }

    /** Levels go up one at a time, each adding its section's abilities; "becomes level 2" triggers as it does. */
    // CR 716.2a: "Activate only if this Class is level N-1"; CR 716.2d: a Class without a level is level 1
    @Test
    fun levelsInOrder() = scenario {
        alice.battlefield(steward, "Plains", "Plains", "Plains", "Plains", "Plains", "Plains", "Plains", "Plains")
        alice.hand(bunnies)
        alice.library("Island", "Island")

        alice.casts(bunnies)
        allPass()
        allPass()
        expect { alice.hand.names shouldBe listOf("Island") }
        // Level 3 isn't available at level 1
        assertFailsWith<IllegalArgumentException> { alice.activates(steward, ability = 1) }
        alice.activates(steward, ability = 0)
        allPass()
        // CR 716.2a: now level 2: its trigger copies a token
        targets("Rabbit Token")
        allPass()
        expect {
            alice.battlefield.names.count { it == "Rabbit Token" } shouldBe 4
            // the copy entering doesn't draw again this turn (CR 603.2h)
            alice.hand.names shouldBe listOf("Island")
            card("Rabbit Token").power shouldBe 1
        }
        alice.activates(steward, ability = 1)
        allPass()
        expect { card("Rabbit Token").power shouldBe 3 }
    }

    /** At level 3, counters its controller puts are doubled. */
    // CR 614.1a, 616.1: a replacement on counters put by you
    @Test
    fun countersDoubledAtLevelThree() = scenario {
        alice.battlefield(tavern, bear, "Forest", "Forest", "Forest", "Forest", "Forest")
        alice.activates(tavern, ability = 0)
        allPass()
        alice.activates(tavern, ability = 1)
        allPass()
        advanceTo(TurnStep.BeginningOfCombat)
        targets(bear)
        allPass()
        expect { card(bear).counters(p1p1) shouldBe 2 }
    }

    /** Below level 3, nothing is doubled. */
    @Test
    fun countersNotDoubledBelowLevelThree() = scenario {
        alice.battlefield(tavern, bear)
        advanceTo(TurnStep.BeginningOfCombat)
        targets(bear)
        allPass()
        expect { card(bear).counters(p1p1) shouldBe 1 }
    }
}
