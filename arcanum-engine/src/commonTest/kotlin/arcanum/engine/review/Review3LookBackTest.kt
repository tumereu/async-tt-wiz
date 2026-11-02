package arcanum.engine.review

import arcanum.api.Color
import arcanum.api.CounterKind
import arcanum.api.TurnStep
import arcanum.engine.decision.Answer
import arcanum.engine.decision.Decision
import arcanum.engine.scenario.scenario
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Review 3: triggered abilities of cards in graveyards and hands, and leaves-the-battlefield abilities that "look back
 * in time" (CR 603.10a): an ability that functions only from the graveyard doesn't see the event that put its card
 * there; a permanent that enters in the same event as another leaves doesn't see the other leave. Dredge's
 * requirement (CR 702.52b).
 */
class Review3LookBackTest {
    private val charge = CounterKind.Named("charge")

    /** Netherbound Turncoat dies at the same time as another of alice's creatures: its graveyard ability didn't exist
     * immediately before the event, so it doesn't trigger. */
    // CR 603.10a, 113.6
    @Test
    fun aGraveyardAbilityDoesntSeeItsOwnCardsDeath() = scenario {
        alice.battlefield("Netherbound Turncoat", "Tinkered Mite", "Swamp")
        bob.hand("Blazing Confluence")
        bob.castsWithoutCosts("Blazing Confluence", modes = listOf(0, 0, 0))
        resolve()
        expect {
            alice.graveyard has "Netherbound Turncoat"
            alice.graveyard has "Tinkered Mite"
            stack.isEmpty()
        }
        assertTrue(state.triggered.isEmpty())
        assertTrue(pending == null, "nothing to decide: $pending")
    }

    /** Stairway from the Depths destroyed together with a creature (Clockwork Charge, mana value 3): "if this card is in
     * your graveyard" – the card wasn't there before the event and its ability didn't function: no Zombie. */
    // CR 603.10a, 603.4
    @Test
    fun stairwayDestroyedWithACreatureMakesNoZombie() = scenario {
        alice.battlefield("Stairway from the Depths", "Elvish Hierophant")
        alice.battlefield("Clockwork Charge", counters = listOf(charge to 3))
        alice.activates("Clockwork Charge", ability = 1)
        allPass()
        expect {
            alice.graveyard has "Stairway from the Depths"
            alice.graveyard has "Elvish Hierophant"
            alice.battlefield.names.count { it == "Zombie Token" } shouldBe 0
        }
    }

    /** Stairway from the Depths already in the graveyard: a nontoken creature dying makes a Zombie. */
    // CR 603.4, 113.6
    @Test
    fun stairwayInTheGraveyardMakesAZombie() = scenario {
        alice.graveyard("Stairway from the Depths")
        alice.battlefield("Elvish Hierophant")
        alice.hand("Storm Spark")
        alice.castsWithoutCosts("Storm Spark", target = "Elvish Hierophant")
        resolve()
        resolve()
        expect { alice.battlefield.names.count { it == "Zombie Token" } shouldBe 1 }
    }

    /** Endless Return destroyed together with a creature: a leaves-the-battlefield ability looks back, so it sees the
     * creature put into the graveyard and returns it to its owner's hand. */
    // CR 603.10a
    @Test
    fun endlessReturnLooksBackAtItsOwnDestruction() = scenario {
        alice.battlefield("Endless Return", "Ossira, Ancestral Herald")
        alice.battlefield("Clockwork Charge", counters = listOf(charge to 4))
        alice.activates("Clockwork Charge", ability = 1)
        allPass()
        allPass()
        expect {
            alice.graveyard has "Endless Return"
            alice.hand has "Ossira, Ancestral Herald"
        }
    }

    /** Goblin Solderer: alice sacrifices Bile Fountain and returns Scrap Dredger at the same time. Scrap Dredger wasn't
     * on the battlefield before the event, so its "another artifact you control is put into a graveyard from the
     * battlefield" doesn't see Bile Fountain leave. */
    // CR 603.10a, 603.6a
    @Test
    fun aPermanentEnteringWithAnotherLeavingDoesntSeeItLeave() = scenario {
        alice.battlefield("Goblin Solderer", "Bile Fountain")
        alice.graveyard("Scrap Dredger", "Tinkered Mite")
        alice.library("Island", "Island")
        alice.activates("Goblin Solderer", targets = listOf("Bile Fountain", "Scrap Dredger"))
        allPass()
        while (state.stack.isNotEmpty() && pending == null) allPass()
        expect {
            alice.battlefield has "Scrap Dredger"
            alice.graveyard has "Bile Fountain"
            alice.graveyard has "Tinkered Mite"
        }
    }

    /** Creatures entering together see each other enter (CR 603.6a): Muster the Forebears returns Cinder Gatewarden and
     * Elvish Seer at once; the Gatewarden triggers for the Seer. */
    // CR 603.6a
    @Test
    fun creaturesEnteringTogetherSeeEachOther() = scenario {
        alice.graveyard("Cinder Gatewarden", "Elvish Seer")
        alice.hand("Muster the Forebears")
        alice.castsWithoutCosts("Muster the Forebears", x = 3)
        resolve()
        resolveStack()
        expect { bob.life shouldBe 19 }
    }

    /** Dredge needs at least as many cards in the library as its number: with two cards, Shadowsting can't be dredged
     * and alice just draws. */
    // CR 702.52b
    @Test
    fun dredgeNeedsEnoughCardsInTheLibrary() = scenario {
        turn(number = 3, step = TurnStep.Upkeep)
        alice.graveyard("Shadowsting")
        alice.library("Island", "Forest")
        allPass()
        assertTrue(pending == null, "dredge offered with two cards: $pending")
        expect {
            alice.hand has "Island"
            alice.graveyard has "Shadowsting"
        }
    }

    /** With three cards in the library, dredge 3 is offered; dredging mills three and returns Shadowsting. */
    // CR 702.52a
    @Test
    fun dredgeWithEnoughCards() = scenario {
        turn(number = 3, step = TurnStep.Upkeep)
        alice.graveyard("Shadowsting")
        alice.library("Island", "Forest", "Swamp")
        allPass()
        when (val d = pending) {
            is Decision.May -> yes()
            is Decision.ChooseReplacement -> answer(Answer.Replacement(d.options.first { it.text?.contains("redge") == true }.index))
            else -> error("dredge wasn't offered: $d")
        }
        expect {
            alice.hand has "Shadowsting"
            alice.graveyard.size shouldBe 3
        }
    }

    /** Chastening Flame's ability works from the graveyard: bob gains life from alice's Hearth of the Emberwillows and
     * alice may pay {R} to return the card. */
    // CR 113.6k, 603.2
    @Test
    fun aGraveyardTriggerFromAManaAbility() = scenario {
        alice.battlefield("Hearth of the Emberwillows", "Mountain")
        alice.graveyard("Chastening Flame")
        alice.activates("Hearth of the Emberwillows", ability = 1, color = Color.Red)
        expect { bob.life shouldBe 21 }
        allPass()
        assertTrue(pending is Decision.May, "Chastening Flame's trigger: $pending")
        yes()
        if (pending is Decision.ManaPayment) answer(Answer.ManaPayment(decision<Decision.ManaPayment>().suggestion))
        expect { alice.hand has "Chastening Flame" }
    }

    /** Gorehaunt's landfall works from the graveyard. */
    // CR 113.6k, 603.2
    @Test
    fun gorehauntReturnsOnLandfall() = scenario {
        alice.graveyard("Gorehaunt")
        alice.hand("Swamp")
        alice.plays("Swamp")
        allPass()
        yes()
        expect { alice.battlefield has "Gorehaunt" }
    }

    /** Netherbound Turncoat in the graveyard sees another creature die and may come back for {B}. */
    // CR 113.6k, 603.10a
    @Test
    fun turncoatInTheGraveyardSeesAnotherDie() = scenario {
        alice.graveyard("Netherbound Turncoat")
        alice.battlefield("Tinkered Mite", "Swamp")
        alice.hand("Storm Spark")
        alice.castsWithoutCosts("Storm Spark", target = "Tinkered Mite")
        resolve()
        assertEquals(1, state.stack.size)
    }
}
