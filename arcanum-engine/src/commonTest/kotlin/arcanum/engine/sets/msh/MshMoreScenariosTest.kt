package arcanum.engine.sets.msh

import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** More Marvel Super Heroes cards in play: paying a number of times for that many modes, targets only with teamwork,
 * an extra turn without power-ups. */
class MshMoreScenariosTest {
    private val fletcher = Cards.invent("Fletcher, Master Marksman", "{1}{R}", "Legendary Creature — Human Archer Hero", """
        Reach, first strike
        Trick Arrows — Whenever Fletcher becomes tapped, you may pay {1} up to three times. When you do, choose up to that many —
        • Net — Target creature can't block this turn.
        • Explosive — Fletcher deals 2 damage to target player.
        • Boomerang — Discard a card, then draw a card.
    """, "2/2")
    private val compact = Cards.invent("Unholy Compact", "{2}{B}", "Sorcery", """
        Teamwork 2 (As an additional cost to cast this spell, you may tap any number of creatures you control with total power 2 or more.)
        Exile target creature with mana value 3 or less. If this spell was cast using teamwork, instead exile target creature and you gain 3 life.
    """)
    private val chronarch = Cards.invent("Chronarch the Conqueror", "{2}{U}{U}", "Legendary Creature — Human Villain", """
        Flying
        Power-up — {5}{U}{U}{U}: Put a +1/+1 counter on Chronarch. Take an extra turn after this one. During that turn, power-up abilities can't be activated. (Activate each power-up ability only once. Reduce the cost by his mana cost if he entered this turn.)
    """, "4/5")
    private val scrapper = Cards.invent("Plucky Scrapper", "{1}{W}", "Creature — Human Warrior Hero", """
        Lifelink
        Power-up — {4}{W}: Put two +1/+1 counters on this creature. (Activate each power-up ability only once. Reduce the cost by its mana cost if it entered this turn.)
    """, "2/1")
    private val bear = Cards.invent("Plaza Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val giant = Cards.invent("Plaza Giant", "{3}{R}", "Creature — Giant", "", "4/4")
    private val p1p1 = CounterKind.PowerToughness(1, 1)

    private fun Scenario.resolveAll() {
        var guard = 40
        while (state.stack.isNotEmpty() && pending == null && guard-- > 0) allPass()
    }

    /** Paid twice: two of the three modes at most. */
    // CR 603.12, 700.2
    @Test
    fun payingTwiceAllowsTwoModes() = scenario {
        alice.battlefield(fletcher, "Mountain", "Mountain")
        bob.battlefield(bear)
        alice.library(bear)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(fletcher)
        allPass()
        alice.answers(Answer.Number(2))
        alice.answers(Answer.YesNo(true))
        if (pending is Decision.ManaPayment) answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        resolveAll()
        assertEquals(2, decision<Decision.Modes>().max)
        assertFailsWith<IllegalArgumentException> { alice.answers(Answer.Modes(listOf(0, 1, 2))) }
        alice.answers(Answer.Modes(listOf(0, 1)))
        alice.answers(Answer.Targets(listOf(target(bear))))
        if (pending is Decision.Targets) alice.answers(Answer.Targets(listOf(target("bob"))))
        resolveAll()
        assertEquals(18, bob.life)
    }

    /** Without teamwork only a creature with mana value 3 or less can be the target. */
    // CR 702.194c
    @Test
    fun withoutTeamworkTheSmallTarget() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", bear)
        alice.hand(compact)
        bob.battlefield(giant)
        assertFailsWith<IllegalArgumentException> { alice.casts(compact, target = giant, kicked = 0) }
    }

    /** With teamwork any creature, and 3 life. */
    // CR 702.194c
    @Test
    fun withTeamworkAnyTargetAndLife() = scenario {
        alice.battlefield("Swamp", "Swamp", "Swamp", bear)
        alice.hand(compact)
        bob.battlefield(giant)
        alice.casts(compact, target = giant, kicked = 1, choosing = listOf(bear))
        resolveAll()
        expect { giant.zone shouldBe Zone.Exile }
        assertEquals(23, alice.life)
    }

    /** The extra turn comes next, and in it power-up abilities can't be activated. */
    // CR 500.7, 702.193a
    @Test
    fun extraTurnWithoutPowerUps() = scenario {
        alice.battlefield(scrapper)
        alice.battlefield("Island", "Island", "Island", "Island", "Island", "Island", "Island", "Island", "Island", "Island")
        alice.hand(chronarch)
        alice.library(bear, bear, bear)
        bob.library(bear, bear)
        alice.casts(chronarch)
        resolveAll()
        alice.activates(chronarch)
        resolveAll()
        expect { chronarch.counters(p1p1) shouldBe 1 }
        advanceTo(TurnStep.PrecombatMain, turn = 2)
        assertEquals(alice.id, state.turn.active)
        assertFailsWith<IllegalArgumentException> { alice.activates(scrapper) }
    }
}
