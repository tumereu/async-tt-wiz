package arcanum.engine.combat

import arcanum.api.TurnStep
import arcanum.engine.cards.Cards
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.decision.Share
import arcanum.engine.input.Attack
import arcanum.engine.input.Input
import arcanum.engine.scenario.Scenario
import arcanum.engine.scenario.scenario
import arcanum.engine.state.TargetRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Banding (CR 702.22): attacking bands, blocking a band, and who assigns combat damage. */
class BandingTest {
    private val knight = Cards.invent("Band Shield Knight", "{1}{W}", "Creature — Human Knight", "Banding", "2/2")
    private val bear = Cards.invent("Band Plain Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val ox = Cards.invent("Band Plain Ox", "{2}{G}", "Creature — Ox", "", "3/3")
    private val ogre = Cards.invent("Band Big Ogre", "{4}{R}", "Creature — Ogre", "", "5/5")

    private fun Scenario.band(vararg names: String) = alice.id.let { me ->
        perform(Input.DeclareAttackers(me, names.map { Attack(card(it), TargetRef.Player(bob.id), band = 1) }))
    }

    // CR 702.22c, h, k: a band of a creature with banding and one without; a creature blocking one blocks both, and the active
    // player assigns its combat damage among them
    @Test
    fun blockingABandBlocksAllOfItAndTheAttackerAssigns() = scenario {
        alice.battlefield(knight, bear)
        bob.battlefield(ogre)
        advanceTo(TurnStep.DeclareAttackers)
        band(knight, bear)
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(ogre to knight)
        assertEquals(setOf(card(knight), card(bear)), state.combat!!.blockers.single().attackers.toSet())
        advanceTo(TurnStep.CombatDamage)
        var guard = 4
        while (pending is Decision.Divide && guard-- > 0) {
            val d = pending as Decision.Divide
            if (d.source == card(ogre)) {
                assertEquals(alice.id, d.player)
                answer(Answer.Division(listOf(Share(TargetRef.Object(card(knight)), 0), Share(TargetRef.Object(card(bear)), 5))))
            } else answer(Answer.Division(d.suggested))
        }
        advanceTo(TurnStep.EndOfCombat)
        expect {
            bob.life shouldBe 20
            alice.battlefield has knight
            alice.graveyard has bear
        }
    }

    // CR 702.22j: an attacker blocked by a creature with banding has its damage assigned by the defending player
    @Test
    fun theDefenderAssignsAgainstABandingBlocker() = scenario {
        alice.battlefield(ogre)
        bob.battlefield(knight, ox)
        advanceTo(TurnStep.DeclareAttackers)
        alice.attacks(ogre)
        advanceTo(TurnStep.DeclareBlockers)
        bob.blocks(knight to ogre, ox to ogre)
        advanceTo(TurnStep.CombatDamage)
        val d = pending
        assertTrue(d is Decision.Divide && d.source == card(ogre) && d.player == bob.id, "bob assigns the Ogre's damage: $d")
        answer(Answer.Division(listOf(Share(TargetRef.Object(card(knight)), 1), Share(TargetRef.Object(card(ox)), 4))))
        advanceTo(TurnStep.EndOfCombat)
        expect {
            bob.battlefield has knight
            bob.graveyard has ox
        }
    }

    // CR 702.22c: at most one creature without banding in a band
    @Test
    fun twoWithoutBandingCantBand() = scenario {
        alice.battlefield(bear, ox)
        advanceTo(TurnStep.DeclareAttackers)
        assertFailsWith<IllegalArgumentException> { band(bear, ox) }
    }
}
