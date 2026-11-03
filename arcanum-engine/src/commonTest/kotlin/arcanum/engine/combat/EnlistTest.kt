package arcanum.engine.combat

import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.input.Attack
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.state.TargetRef
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** Enlist (CR 702.154): an optional cost to attack (CR 508.1g) and its linked "when you do" trigger. */
class EnlistTest {
    private val sergeant = Cards.invent("Enlist Drafting Sergeant", "{1}{W}", "Creature — Human Soldier",
        "Enlist (As this creature attacks, you may tap a nonattacking creature you control without summoning sickness. When you do, add its power to this creature's until end of turn.)", "2/2")
    private val ox = Cards.invent("Enlist Plain Ox", "{2}{G}", "Creature — Ox", "", "3/3")
    private val bear = Cards.invent("Enlist Plain Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val hasty = Cards.invent("Enlist Hasty Imp", "{R}", "Creature — Imp", "Haste", "1/1")

    private fun Scenario.attackEnlisting(attacker: String, vararg enlisted: String) =
        perform(Input.DeclareAttackers(alice.id, listOf(Attack(card(attacker), TargetRef.Player(bob.id), enlisted.map { card(it) }))))

    // CR 702.154a, 508.1g: the enlisted creature is tapped as the attacker attacks; when it is, the attacker gets +X/+0, X its power
    @Test
    fun enlistTapsAndAddsPower() = scenario {
        alice.battlefield(sergeant, ox)
        advanceTo(TurnStep.DeclareAttackers)
        attackEnlisting(sergeant, ox)
        expect { ox.tapped shouldBe true }
        allPass()
        expect { sergeant.power shouldBe 5 }
        advanceTo(TurnStep.EndOfCombat)
        expect { bob.life shouldBe 15 }
    }

    // CR 702.154a: enlisting is optional; attacking without it is an ordinary attack
    @Test
    fun withoutEnlisting() = scenario {
        alice.battlefield(sergeant, ox)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(sergeant)
        allPass()
        expect {
            ox.tapped shouldBe false
            sergeant.power shouldBe 2
        }
    }

    // CR 702.154a: not a creature chosen to attack; CR 702.154c: not itself; CR 702.154d: one creature per enlist ability
    @Test
    fun whoCanBeEnlisted() = scenario {
        alice.battlefield(sergeant, ox, bear)
        advanceTo(TurnStep.DeclareAttackers)
        assertFailsWith<IllegalArgumentException> {
            perform(Input.DeclareAttackers(alice.id, listOf(Attack(card(sergeant), TargetRef.Player(bob.id), listOf(card(ox))),
                Attack(card(ox), TargetRef.Player(bob.id)))))
        }
        assertFailsWith<IllegalArgumentException> { attackEnlisting(sergeant, sergeant) }
        assertFailsWith<IllegalArgumentException> { attackEnlisting(sergeant, ox, bear) }
        // A creature without enlist enlists nothing
        assertFailsWith<IllegalArgumentException> { attackEnlisting(bear, ox) }
        attackEnlisting(sergeant, bear)
        allPass()
        expect { sergeant.power shouldBe 4 }
    }

    // CR 702.154a: a creature that came under your control this turn can be enlisted only with haste
    @Test
    fun summoningSickOnlyWithHaste() = scenario {
        alice.battlefield(sergeant)
        alice.hand(ox, hasty)
        move(ox, arcanum.api.Zone.Battlefield)
        move(hasty, arcanum.api.Zone.Battlefield)
        advanceTo(TurnStep.DeclareAttackers)
        assertFailsWith<IllegalArgumentException> { attackEnlisting(sergeant, ox) }
        attackEnlisting(sergeant, hasty)
        allPass()
        expect { sergeant.power shouldBe 3 }
    }
}
