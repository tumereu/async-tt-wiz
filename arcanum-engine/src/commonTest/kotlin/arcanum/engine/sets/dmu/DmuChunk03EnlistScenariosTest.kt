package arcanum.engine.sets.dmu

import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.input.Attack
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.sos.may
import arcanum.engine.state.TargetRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The DMU enlist cards of chunk 03 that give the engine something new, played: the invented names carry the oracle text of Keldon
 * Flamesage and Guardian of New Benalia.
 */
class DmuChunk03EnlistScenariosTest {
    private val ox = Cards.invent("Enlist Test Ox", "{2}{G}", "Creature — Ox", "", "3/3")
    private val cheap = Cards.invent("Enlist Test Spark", "{R}", "Instant", "Enlist Test Spark deals 2 damage to each opponent.")
    private val dear = Cards.invent("Enlist Test Flare", "{2}{R}", "Sorcery", "Enlist Test Flare deals 3 damage to each opponent.")
    private val bear = Cards.invent("Enlist Test Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val fillers = (1..3).map { Cards.invent("Enlist Test Filler $it", "{U}", "Instant", "Draw a card.") }

    private val sage = Cards.invent("Torchwave Test Sage", "{2}{R}", "Creature — Human Shaman", """
        Enlist (As this creature attacks, you may tap a nonattacking creature you control without summoning sickness. When you do, add its power to this creature's until end of turn.)
        Whenever this creature attacks, look at the top X cards of your library, where X is this creature's power. You may exile an instant or sorcery card with mana value X or less from among them. Put the rest on the bottom of your library in a random order. You may cast the exiled card without paying its mana cost.
    """, "2/3")
    private val guardian = Cards.invent("Brindle Test Marshal", "{1}{W}", "Creature — Human Soldier", """
        Enlist (As this creature attacks, you may tap a nonattacking creature you control without summoning sickness. When you do, add its power to this creature's until end of turn.)
        Whenever this creature enlists a creature, scry 2.
        Discard a card: This creature gains indestructible until end of turn. Tap it.
    """, "2/2")

    private fun Scenario.attackEnlisting(attacker: String, vararg enlisted: String) =
        perform(Input.DeclareAttackers(alice.id, listOf(Attack(card(attacker), TargetRef.Player(bob.id), enlisted.map { card(it) }))))

    // CR 702.154a, 401.4, 608.2g: X is the power as the trigger resolves (2); the mana value 3 sorcery is not offered, the cheap instant is
    // exiled and cast for free (CR 118.9), the rest go to the bottom (CR 401.4)
    @Test
    fun flamesageExilesAndCastsACheapSpell() = scenario {
        alice.battlefield(sage)
        alice.library(dear, cheap, bear, *fillers.toTypedArray())
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(sage)
        resolveAll()
        val choice = pending as Decision.ChooseObjects
        assertEquals(1, choice.options.size, "only the mana value 1 instant of the top two cards qualifies: ${choice.options}")
        alice.chooses(cheap)
        resolveAll()
        if (pending is Decision.May) may(true)
        resolveAll()
        expect {
            bob.life shouldBe 18
            alice.library.size shouldBe 5
            alice.library.names.last() shouldBe dear
        }
    }

    // CR 702.154a: the creature enlisted adds its power (3) first, so X is 5 and the mana value 3 sorcery qualifies
    @Test
    fun flamesageSeesTheEnlistedPower() = scenario {
        alice.battlefield(sage, ox)
        alice.library(dear, bear, *fillers.toTypedArray())
        advanceTo(TurnStep.DeclareAttackers)
        attackEnlisting(sage, ox)
        // CR 603.3b: the attack trigger goes on the Stack first, the enlist trigger (+X/+0) on top of it resolves first
        (pending as Decision.OrderTriggers).let { d -> answer(Answer.Order(d.triggers.map { it.index })) }
        resolveAll()
        if (pending is Decision.ChooseObjects) alice.chooses(dear)
        resolveAll()
        if (pending is Decision.May) may(true)
        resolveAll()
        expect { bob.life shouldBe 17 }
    }

    // CR 702.154c: the trigger sees the creature enlisted; scry 2 happens only when the attacker enlisted
    @Test
    fun guardianScriesWhenItEnlists() = scenario {
        alice.battlefield(guardian, ox)
        alice.library(bear, *fillers.toTypedArray())
        advanceTo(TurnStep.DeclareAttackers)
        attackEnlisting(guardian, ox)
        resolveAll()
        val scry = pending as Decision.ChooseObjects
        assertEquals(2, scry.options.size, "scry 2 looks at the top two cards: ${scry.options}")
    }

    @Test
    fun guardianDoesNotScryWithoutEnlisting() = scenario {
        alice.battlefield(guardian, ox)
        alice.library(bear, *fillers.toTypedArray())
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(guardian)
        resolveAll()
        assertNull(pending, "no scry without enlisting")
    }
}
