package arcanum.engine.scenario

import arcanum.api.Affected
import arcanum.api.CardType
import arcanum.api.Mechanic
import arcanum.api.ObjectFilter
import arcanum.api.Objects
import arcanum.api.Value
import arcanum.api.Zone
import arcanum.engine.decision.Decision
import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The harness finds what a test means, or fails: names in several zones, names given twice, effects set up by hand. */
class HarnessTest {
    /** A name in several zones is ambiguous unless the zone or index is named. */
    @Test
    fun aNameInSeveralZonesMustBeNamed() = scenario {
        alice.hand("Storm Spark")
        alice.graveyard("Storm Spark")
        assertFails { card("Storm Spark") }
        assertTrue(state.objOrThrow(card("Storm Spark", zone = Zone.Graveyard)).zone == Zone.Graveyard)
        assertTrue(state.objOrThrow(card("Storm Spark", index = 0)).zone == Zone.Hand)
    }

    /** Casting by name takes the card from the caster's hand, not an earlier one in the graveyard. */
    // CR 601.2a
    @Test
    fun castsFromTheHand() = scenario {
        alice.battlefield("Mountain")
        alice.graveyard("Storm Spark")
        alice.hand("Storm Spark")
        alice.casts("Storm Spark", target = "bob")
        expect {
            stack.names shouldBe listOf("Storm Spark")
            alice.graveyard has "Storm Spark"
            alice.hand.size shouldBe 0
        }
    }

    /** A name given twice to a choice means two objects of that name. */
    // CR 701.9a
    @Test
    fun aRepeatedNameChoosesTwoObjects() = scenario {
        alice.battlefield("Mountain")
        alice.hand("Faithless Rummage", "Island", "Island", "Forest")
        alice.library("Swamp", "Swamp")
        alice.casts("Faithless Rummage")
        allPass()
        decision<Decision.ChooseObjects>()
        alice.chooses("Island", "Island")
        expect {
            alice.graveyard.names.count { it == "Island" } shouldBe 2
            alice.hand has "Forest"
        }
    }

    /** An effect on "creatures you control" with a layer mechanic locks its set: a creature arriving later isn't pumped. */
    // CR 611.2c
    @Test
    fun anEffectOnASetLocksIt() = scenario {
        alice.battlefield("Elvish Seer")
        alice.hand("Umbral Elves")
        effect(alice, Mechanic.ModifyPowerToughness(Value.Fixed(1), Value.Fixed(1)),
            affects = Affected.Objects(Objects.Each(ObjectFilter.Type(CardType.Creature))))
        move("Umbral Elves", Zone.Battlefield)
        expect {
            "Elvish Seer".power shouldBe 2
            "Umbral Elves".power shouldBe 1
        }
    }

    /** An effect on named cards with a rule that isn't a layer mechanic applies to those cards only, not as a game rule. */
    // CR 611.2c
    @Test
    fun anEffectOnCardsAppliesToThem() = scenario {
        bob.battlefield("Elvish Seer", "Umbral Elves")
        effect(alice, Mechanic.CantBlock(ObjectFilter.Any), on = listOf("Elvish Seer"))
        val view = view()
        assertTrue(view[card("Elvish Seer")]!!.mechanics.any { it.mechanic is Mechanic.CantBlock })
        assertFalse(view[card("Umbral Elves")]!!.mechanics.any { it.mechanic is Mechanic.CantBlock })
        assertTrue(view.rules.none { it.mechanic is Mechanic.CantBlock })
    }
}
