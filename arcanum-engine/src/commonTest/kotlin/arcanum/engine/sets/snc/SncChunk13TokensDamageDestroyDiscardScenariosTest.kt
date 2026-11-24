package arcanum.engine.sets.snc

import arcanum.engine.decision.Answer
import arcanum.api.TurnStep
import arcanum.api.Zone
import arcanum.engine.cards.Cards
import arcanum.engine.scenario.scenario
import arcanum.engine.sets.blb.resolveAll
import arcanum.engine.sets.blb.targets
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** Streets of New Capenna chunk 13 in play: destroying only an attacking creature, and a target opponent's discard with
 * three players. */
class SncChunk13TokensDamageDestroyDiscardScenariosTest {
    private val shot = Cards.invent("Snc13 Wound Shot", "{2}{W}", "Instant", "Destroy target attacking creature.")
    private val ogre = Cards.invent("Snc13 Lumbering Ogre", "{3}", "Creature — Ogre", "", "3/3")
    private val bear = Cards.invent("Snc13 Idle Bear", "{1}{G}", "Creature — Bear", "", "2/2")
    private val clerk = Cards.invent("Snc13 Cruel Court Clerk", "{1}{B}", "Creature — Human Advisor",
        "When this creature enters, target opponent discards a card.", "1/1")
    private val spare = Cards.invent("Snc13 Spare Ticket", "{9}", "Artifact", "")
    private val spareTwo = Cards.invent("Snc13 Second Ticket", "{9}", "Artifact", "")

    // CR 506.4, 701.8a: destroys the attacking creature, leaving the other one
    @Test
    fun woundShotDestroysOnlyAnAttackingCreature() = scenario {
        turn(number = 2, active = bob, step = TurnStep.DeclareAttackers, priority = bob)
        bob.battlefield(ogre, bear)
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand(shot)
        bob.attacks(ogre)
        bob.passes()
        alice.casts(shot, target = ogre)
        resolveAll()
        expect {
            ogre.zone shouldBe Zone.Graveyard
            bear.zone shouldBe Zone.Battlefield
        }
    }

    // CR 115.1, 506.4: a creature that isn't attacking is not a legal target
    @Test
    fun woundShotCannotTargetANonAttackingCreature() = scenario {
        turn(number = 2, active = bob, step = TurnStep.DeclareAttackers, priority = bob)
        bob.battlefield(ogre, bear)
        alice.battlefield("Plains", "Plains", "Plains")
        alice.hand(shot)
        bob.attacks(ogre)
        bob.passes()
        assertFailsWith<Throwable> { alice.casts(shot, target = bear) }
    }

    // CR 701.9a, 115.1: only the targeted opponent discards, and chooses the card; the other opponent keeps their hand
    @Test
    fun clerkMakesOnlyTheTargetedOpponentDiscard() = scenario(players = 3) {
        alice.hand(clerk)
        alice.battlefield("Swamp", "Swamp")
        bob.hand(spare, spareTwo)
        carol.hand(spare)
        alice.casts(clerk)
        resolveAll()
        targets("bob")
        resolveAll()
        answer(Answer.Objects(listOf(card(spareTwo))))
        expect {
            bob.graveyard has spareTwo
            bob.hand has spare
            carol.hand has spare
            carol.graveyard.isEmpty()
        }
    }
}
